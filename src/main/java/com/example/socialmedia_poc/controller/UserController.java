package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.User;
import com.example.socialmedia_poc.service.GoogleTokenVerifierService;
import com.example.socialmedia_poc.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    // Validation constants
    private static final int USERNAME_MAX = 50;
    private static final int EMAIL_MAX = 254;
    private static final int DISPLAY_NAME_MAX = 100;
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 128;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private final UserService userService;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    public UserController(UserService userService, GoogleTokenVerifierService googleTokenVerifierService) {
        this.userService = userService;
        this.googleTokenVerifierService = googleTokenVerifierService;
    }

    // ── Helper: build a safe user map for responses (never leak sensitive fields) ──
    private Map<String, Object> safeUser(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", user.getUserId());
        m.put("username", user.getUsername());
        m.put("email", user.getEmail());
        m.put("display_name", user.getDisplayName());
        m.put("role", user.getRole());
        m.put("auth_provider", user.getAuthProvider());
        m.put("profile_picture_url", user.getProfilePictureUrl());
        m.put("onboarded", user.isOnboarded());
        m.put("interests", user.getInterests());
        m.put("created_at", user.getCreatedAt());
        m.put("last_login", user.getLastLogin());
        // session_token is returned ONLY here so the client can store it
        m.put("session_token", user.getSessionToken());
        return m;
    }

    // Same as safeUser but WITHOUT session_token (for profile views etc.)
    private Map<String, Object> publicUser(User user) {
        Map<String, Object> m = safeUser(user);
        m.remove("session_token");
        m.remove("email"); // don't expose other users' emails
        return m;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String email = request.get("email");
            String displayName = request.get("display_name");
            String password = request.get("password");

            // ── Validation ──
            if (username == null || username.trim().isEmpty()) {
                return badRequest("Username is required");
            }
            username = username.trim();
            if (username.length() > USERNAME_MAX) {
                return badRequest("Username must be " + USERNAME_MAX + " characters or fewer");
            }
            if (!USERNAME_PATTERN.matcher(username).matches()) {
                return badRequest("Username may only contain letters, numbers, underscores, dots, and hyphens");
            }

            if (email == null || email.trim().isEmpty()) {
                return badRequest("Email is required");
            }
            email = email.trim();
            if (email.length() > EMAIL_MAX || !EMAIL_PATTERN.matcher(email).matches()) {
                return badRequest("Please provide a valid email address");
            }

            if (password == null || password.isEmpty()) {
                return badRequest("Password is required");
            }
            if (password.length() < PASSWORD_MIN) {
                return badRequest("Password must be at least " + PASSWORD_MIN + " characters");
            }
            if (password.length() > PASSWORD_MAX) {
                return badRequest("Password must be " + PASSWORD_MAX + " characters or fewer");
            }

            if (displayName != null && displayName.length() > DISPLAY_NAME_MAX) {
                return badRequest("Display name must be " + DISPLAY_NAME_MAX + " characters or fewer");
            }

            User user = userService.createUser(username, email,
                    displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : username, password);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "User registered successfully",
                "user", safeUser(user)
            ));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[UserController] Registration failed", e);
            return serverError("Registration failed. Please try again.");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            if (username == null || username.trim().isEmpty()) {
                return badRequest("Username is required");
            }

            if (password == null || password.isEmpty()) {
                return badRequest("Password is required");
            }

            User user = userService.loginUser(username.trim(), password);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Login successful",
                "user", safeUser(user)
            ));
        } catch (IllegalArgumentException e) {
            // Don't distinguish between "user not found" and "wrong password"
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Invalid credentials"
            ));
        } catch (Exception e) {
            log.error("[UserController] Login failed", e);
            return serverError("Login failed. Please try again.");
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        try {
            String idToken = request.get("id_token");
            if (idToken == null || idToken.trim().isEmpty()) {
                return badRequest("Google ID token is required");
            }

            if (!googleTokenVerifierService.isConfigured()) {
                return ResponseEntity.status(501).body(Map.of(
                    "status", "error",
                    "message", "Google Sign-In is not configured on this server"
                ));
            }

            GoogleTokenVerifierService.GoogleUserInfo googleUser = googleTokenVerifierService.verifyIdToken(idToken);
            if (googleUser == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Invalid Google token"
                ));
            }

            User user = userService.loginOrCreateGoogleUser(
                    googleUser.getGoogleId(),
                    googleUser.getEmail(),
                    googleUser.getName(),
                    googleUser.getPictureUrl()
            );

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Google login successful",
                "user", safeUser(user)
            ));
        } catch (Exception e) {
            log.error("[UserController] Google login failed", e);
            return serverError("Google login failed. Please try again.");
        }
    }

    /**
     * Session validation — changed from GET /{token} to POST to keep tokens out of URLs/logs.
     * Also supports the legacy GET for backward compatibility (both routes).
     */
    @PostMapping("/session/validate")
    public ResponseEntity<?> validateSessionPost(@RequestBody Map<String, String> request) {
        return doValidateSession(request.get("session_token"));
    }

    /** @deprecated Use POST /session/validate instead. Kept for backward compat. */
    @GetMapping("/session/{sessionToken}")
    public ResponseEntity<?> validateSessionGet(@PathVariable String sessionToken) {
        return doValidateSession(sessionToken);
    }

    private ResponseEntity<?> doValidateSession(String sessionToken) {
        try {
            if (sessionToken == null || sessionToken.isBlank()) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Session token is required"
                ));
            }

            User user = userService.validateSession(sessionToken);

            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Invalid or expired session"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "user", safeUser(user)
            ));
        } catch (Exception e) {
            log.error("[UserController] Session validation failed", e);
            return serverError("Session validation failed");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        try {
            String sessionToken = request.get("session_token");

            if (sessionToken == null || sessionToken.trim().isEmpty()) {
                return badRequest("Session token is required");
            }

            userService.logoutUser(sessionToken);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Logout successful"
            ));
        } catch (Exception e) {
            log.error("[UserController] Logout failed", e);
            return serverError("Logout failed");
        }
    }

    /**
     * Get user profile — IDOR fix: authenticated users can only view their own profile.
     * Admins can view any profile.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User)) {
                return ResponseEntity.status(401).body(Map.of("status", "error", "message", "Not authenticated"));
            }
            User caller = (User) auth.getPrincipal();

            // Non-admins can only view their own profile
            if (!"ADMIN".equals(caller.getRole()) && !caller.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of(
                    "status", "error",
                    "message", "Access denied"
                ));
            }

            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "status", "error",
                    "message", "User not found"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "user", publicUser(user)
            ));
        } catch (Exception e) {
            log.error("[UserController] Fetch profile failed", e);
            return serverError("Failed to fetch user profile");
        }
    }

    /**
     * Get all users — restricted to ADMIN role.
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User)) {
                return ResponseEntity.status(401).body(Map.of("status", "error", "message", "Not authenticated"));
            }
            User caller = (User) auth.getPrincipal();
            if (!"ADMIN".equals(caller.getRole())) {
                return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Admin access required"));
            }

            List<User> users = userService.getAllUsers();
            List<Map<String, Object>> safeList = users.stream()
                    .map(this::publicUser)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", users.size(),
                "users", safeList
            ));
        } catch (Exception e) {
            log.error("[UserController] Fetch all users failed", e);
            return serverError("Failed to fetch users");
        }
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/interests")
    public ResponseEntity<?> saveInterests(@RequestBody Map<String, Object> request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User)) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Not authenticated"
                ));
            }
            User authenticatedUser = (User) auth.getPrincipal();
            String userId = authenticatedUser.getUserId();

            Object interestsObj = request.get("interests");
            if (interestsObj == null || !(interestsObj instanceof List)) {
                return badRequest("interests array is required");
            }
            List<String> interests = (List<String>) interestsObj;

            boolean skip = Boolean.TRUE.equals(request.get("skip"));
            if (!skip && interests.size() < 3) {
                return badRequest("Please select at least 3 interests");
            }

            User user = userService.saveInterests(userId, interests);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Interests saved successfully",
                "user", safeUser(user)
            ));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[UserController] Save interests failed", e);
            return serverError("Failed to save interests");
        }
    }

    // ── Generic error helpers (never leak internal details) ──

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message));
    }

    private ResponseEntity<?> serverError(String message) {
        return ResponseEntity.status(500).body(Map.of("status", "error", "message", message));
    }
}
