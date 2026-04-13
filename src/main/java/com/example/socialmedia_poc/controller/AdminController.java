package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.config.ApiKeyStore;
import com.example.socialmedia_poc.model.MigrationLog;
import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.User;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.example.socialmedia_poc.repository.UserRepository;
import com.example.socialmedia_poc.repository.WallPostRepository;
import com.example.socialmedia_poc.service.MigrationService;
import com.example.socialmedia_poc.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin-only endpoints for platform management.
 * Requires ROLE_ADMIN authority (enforced via SecurityConfig).
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PoolPostRepository poolPostRepository;
    private final WallPostRepository wallPostRepository;
    private final ApiKeyStore apiKeyStore;
    private final MigrationService migrationService;
    private final String llmProvider;

    public AdminController(UserService userService,
                           UserRepository userRepository,
                           PoolPostRepository poolPostRepository,
                           WallPostRepository wallPostRepository,
                           ApiKeyStore apiKeyStore,
                           MigrationService migrationService,
                           @Value("${llm.provider:huggingface}") String llmProvider) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.poolPostRepository = poolPostRepository;
        this.wallPostRepository = wallPostRepository;
        this.apiKeyStore = apiKeyStore;
        this.migrationService = migrationService;
        this.llmProvider = llmProvider;
    }

    // ── Dashboard stats ──

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        long totalUsers = userRepository.count();
        long totalPoolPosts = poolPostRepository.count();
        long totalWallPosts = wallPostRepository.count();
        long seedPosts = poolPostRepository.countBySource(PoolPost.PostSource.SEED);
        long llmPosts = poolPostRepository.countBySource(PoolPost.PostSource.GENERATED);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_users", totalUsers);
        stats.put("total_pool_posts", totalPoolPosts);
        stats.put("total_wall_posts", totalWallPosts);
        stats.put("seed_posts", seedPosts);
        stats.put("llm_posts", llmPosts);

        return ResponseEntity.ok(Map.of("status", "success", "stats", stats));
    }

    // ── User management ──

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userService.getAllUsers();
        users.forEach(u -> u.setSessionToken(null)); // hide tokens

        List<Map<String, Object>> userList = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("user_id", u.getUserId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("display_name", u.getDisplayName());
            m.put("role", u.getRole());
            m.put("auth_provider", u.getAuthProvider());
            m.put("created_at", u.getCreatedAt());
            m.put("last_login", u.getLastLogin());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("status", "success", "count", users.size(), "users", userList));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable String userId, @RequestBody Map<String, String> request) {
        try {
            String role = request.get("role");
            if (role == null || (!role.equals("USER") && !role.equals("ADMIN"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Role must be USER or ADMIN"
                ));
            }
            User user = userService.updateUserRole(userId, role);
            user.setSessionToken(null);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Role updated", "user_id", user.getUserId(), "role", user.getRole()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // ── Pool post / seed management ──

    @GetMapping("/pool-posts")
    public ResponseEntity<?> getPoolPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<PoolPost> all = poolPostRepository.findAll();
        int total = all.size();
        int start = Math.min(page * size, total);
        int end = Math.min(start + size, total);
        List<PoolPost> paged = all.subList(start, end);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "total", total,
            "page", page,
            "size", paged.size(),
            "posts", paged
        ));
    }

    @DeleteMapping("/pool-posts/{postId}")
    public ResponseEntity<?> deletePoolPost(@PathVariable String postId) {
        if (!poolPostRepository.existsById(postId)) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Post not found"));
        }
        poolPostRepository.deleteById(postId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Post deleted"));
    }

    @PutMapping("/pool-posts/{postId}/moderate")
    public ResponseEntity<?> moderatePoolPost(@PathVariable String postId, @RequestBody Map<String, Object> request) {
        Optional<PoolPost> opt = poolPostRepository.findById(postId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Post not found"));
        }

        PoolPost post = opt.get();
        // Allow updating content for moderation
        if (request.containsKey("content")) {
            post.setContent((String) request.get("content"));
        }
        poolPostRepository.save(post);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Post moderated", "post_id", postId));
    }

    @GetMapping("/pool-posts/categories")
    public ResponseEntity<?> getCategories() {
        List<PoolPost> all = poolPostRepository.findAll();
        Map<String, Long> categoryCounts = all.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategory() != null ? p.getCategory() : "uncategorized",
                        Collectors.counting()));

        return ResponseEntity.ok(Map.of("status", "success", "categories", categoryCounts));
    }

    // ── Settings (API keys) ──

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("huggingface_key", ApiKeyStore.mask(apiKeyStore.getHuggingFaceApiKey()));
        settings.put("huggingface_key_set", !apiKeyStore.getHuggingFaceApiKey().isBlank());
        settings.put("grok_key", ApiKeyStore.mask(apiKeyStore.getGrokApiKey()));
        settings.put("grok_key_set", !apiKeyStore.getGrokApiKey().isBlank());
        settings.put("llm_provider", llmProvider);
        settings.put("pool_generation_enabled", apiKeyStore.isPoolGenerationEnabled());
        return ResponseEntity.ok(Map.of("status", "success", "settings", settings));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, String> request) {
        List<String> updated = new ArrayList<>();

        if (request.containsKey("huggingface_key")) {
            String key = request.get("huggingface_key");
            if (key != null && !key.isBlank()) {
                apiKeyStore.setHuggingFaceApiKey(key);
                updated.add("huggingface_key");
            }
        }

        if (request.containsKey("grok_key")) {
            String key = request.get("grok_key");
            if (key != null && !key.isBlank()) {
                apiKeyStore.setGrokApiKey(key);
                updated.add("grok_key");
            }
        }

        if (updated.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No valid keys provided"));
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "Settings updated", "updated", updated));
    }

    @PutMapping("/settings/pool-generation")
    public ResponseEntity<?> togglePoolGeneration(@RequestBody Map<String, Object> request) {
        Object enabledObj = request.get("enabled");
        if (enabledObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "'enabled' field required"));
        }
        boolean enabled = Boolean.parseBoolean(enabledObj.toString());
        apiKeyStore.setPoolGenerationEnabled(enabled);
        return ResponseEntity.ok(Map.of("status", "success", "pool_generation_enabled", enabled));
    }

    // ── Data Migrations ──

    @GetMapping("/migrations")
    public ResponseEntity<?> listMigrations() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "migrations", migrationService.listMigrations()
        ));
    }

    @PostMapping("/migrations/run")
    public ResponseEntity<?> runMigration(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "'name' is required. Use GET /v1/admin/migrations to see available migrations."
            ));
        }
        boolean dryRun = Boolean.parseBoolean(String.valueOf(request.getOrDefault("dry_run", "false")));
        String triggeredBy = (String) request.getOrDefault("triggered_by", "admin_api");

        try {
            MigrationLog result = migrationService.runMigration(name, dryRun, triggeredBy);
            return ResponseEntity.ok(Map.of("status", "success", "migration", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Migration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/migrations/logs")
    public ResponseEntity<?> getMigrationLogs(@RequestParam(required = false) String name) {
        List<MigrationLog> logs;
        if (name != null && !name.isBlank()) {
            logs = migrationService.getLogsForMigration(name);
        } else {
            logs = migrationService.getLogs();
        }
        return ResponseEntity.ok(Map.of("status", "success", "count", logs.size(), "logs", logs));
    }
}
