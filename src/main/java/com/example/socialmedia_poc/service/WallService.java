package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages per-user wall.json.
 *
 * - Initial 10 posts: recommended from the shared pool (scored against interest profile)
 * - Next batches: pulled from pool ONLY (no sync LLM calls)
 * - If pool is insufficient, a background generation task is enqueued
 */
@Service
public class WallService {

    private final ObjectMapper mapper;
    private final UserService userService;
    private final PostPoolService poolService;
    private final InterestProfileService profileService;
    private final AsyncContentGeneratorService asyncContentGenerator;

    public WallService(UserService userService,
                       PostPoolService poolService,
                       InterestProfileService profileService,
                       AsyncContentGeneratorService asyncContentGenerator) {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.userService = userService;
        this.poolService = poolService;
        this.profileService = profileService;
        this.asyncContentGenerator = asyncContentGenerator;
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * Get the user's wall. Auto-initialises with recommended posts if empty.
     */
    public List<WallPost> getWall(String userId) throws IOException {
        List<WallPost> wall = loadWall(userId);
        if (wall.isEmpty()) {
            return initializeWall(userId);
        }
        return wall;
    }

    /**
     * Initialise a new user's wall with 10 recommended posts from the pool.
     */
    public List<WallPost> initializeWall(String userId) throws IOException {
        List<WallPost> wall = loadWall(userId);
        if (!wall.isEmpty()) return wall;

        InterestProfile profile = profileService.getProfile(userId);
        Set<String> seenPostIds = Collections.emptySet();

        // Recommend 10 from pool
        List<PoolPost> recommended = poolService.recommend(profile, seenPostIds, 10);

        List<WallPost> initialPosts = recommended.stream()
                .map(p -> p.toWallPost(1))
                .collect(Collectors.toList());

        saveWall(userId, initialPosts);
        return initialPosts;
    }

    /**
     * Get next batch of posts for the wall.
     * Strategy: pull from pool ONLY (instant). If pool is insufficient,
     * enqueue a background generation task — user gets whatever is available now.
     */
    public List<WallPost> generateNextBatch(String userId) throws IOException {
        List<WallPost> currentWall = loadWall(userId);

        int nextBatch = currentWall.stream()
                .mapToInt(WallPost::getBatch)
                .max()
                .orElse(0) + 1;

        InterestProfile profile = profileService.getProfile(userId);
        Set<String> seenPostIds = currentWall.stream()
                .map(WallPost::getPostId)
                .collect(Collectors.toSet());

        // Pull from pool only — no sync LLM calls
        List<PoolPost> fromPool = poolService.recommend(profile, seenPostIds, 10);

        List<WallPost> newPosts = fromPool.stream()
                .map(pp -> pp.toWallPost(nextBatch))
                .collect(Collectors.toList());

        // If pool didn't have enough, enqueue background generation
        if (newPosts.size() < 10) {
            int deficit = 10 - newPosts.size();
            String topCategory = profile.getCategoryScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("General");
            asyncContentGenerator.enqueue(userId, topCategory, deficit, "WALL_DEFICIT");
        }

        if (!newPosts.isEmpty()) {
            currentWall.addAll(newPosts);
            saveWall(userId, currentWall);
        }

        return newPosts;
    }

    /**
     * Add trigger-generated posts to the wall.
     */
    public void addTriggerPosts(String userId, List<WallPost> triggerPosts) throws IOException {
        if (triggerPosts == null || triggerPosts.isEmpty()) return;

        List<WallPost> wall = loadWall(userId);

        int nextBatch = wall.stream()
                .mapToInt(WallPost::getBatch)
                .max()
                .orElse(0) + 1;

        // Set batch numbers
        for (WallPost p : triggerPosts) {
            p.setBatch(nextBatch);
        }

        wall.addAll(triggerPosts);
        saveWall(userId, wall);
    }

    /** Get wall posts for a specific batch. */
    public List<WallPost> getWallBatch(String userId, int batch) throws IOException {
        return loadWall(userId).stream()
                .filter(p -> p.getBatch() == batch)
                .collect(Collectors.toList());
    }

    /** Wall statistics. */
    public Map<String, Object> getWallStats(String userId) throws IOException {
        List<WallPost> wall = loadWall(userId);
        int totalPosts = wall.size();
        int totalBatches = wall.stream().mapToInt(WallPost::getBatch).max().orElse(0);
        long seedPosts = wall.stream().filter(p -> p.getSource() == WallPost.PostSource.SEED).count();
        long generatedPosts = wall.stream().filter(p -> p.getSource() == WallPost.PostSource.GENERATED).count();

        Map<String, Long> categoryBreakdown = wall.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(WallPost::getCategory, Collectors.counting()));

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_posts", totalPosts);
        stats.put("total_batches", totalBatches);
        stats.put("seed_posts", seedPosts);
        stats.put("generated_posts", generatedPosts);
        stats.put("categories", categoryBreakdown);
        return stats;
    }

    /** Reset the user's wall. */
    public List<WallPost> resetWall(String userId) throws IOException {
        saveWall(userId, new ArrayList<>());
        return initializeWall(userId);
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private List<WallPost> loadWall(String userId) throws IOException {
        Path wallFile = userService.getUserWallFile(userId);
        if (!Files.exists(wallFile)) {
            Files.createDirectories(wallFile.getParent());
            Files.writeString(wallFile, "[]");
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(wallFile.toFile(), new TypeReference<List<WallPost>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void saveWall(String userId, List<WallPost> wall) throws IOException {
        Path wallFile = userService.getUserWallFile(userId);
        Files.createDirectories(wallFile.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(wallFile.toFile(), wall);
    }
}
