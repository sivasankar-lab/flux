package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.*;
import com.example.socialmedia_poc.repository.WallPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages per-user wall using PostgreSQL via JPA.
 */
@Service
public class WallService {

    private final WallPostRepository wallPostRepository;
    private final PostPoolService poolService;
    private final InterestProfileService profileService;
    private final AsyncContentGeneratorService asyncContentGenerator;

    public WallService(WallPostRepository wallPostRepository,
                       PostPoolService poolService,
                       InterestProfileService profileService,
                       AsyncContentGeneratorService asyncContentGenerator) {
        this.wallPostRepository = wallPostRepository;
        this.poolService = poolService;
        this.profileService = profileService;
        this.asyncContentGenerator = asyncContentGenerator;
    }

    public List<WallPost> getWall(String userId) {
        List<WallPost> wall = wallPostRepository.findByUserIdOrderByBatchAscIdAsc(userId);
        if (wall.isEmpty()) {
            return initializeWall(userId);
        }
        return wall;
    }

    @Transactional
    public List<WallPost> initializeWall(String userId) {
        List<WallPost> existing = wallPostRepository.findByUserIdOrderByBatchAscIdAsc(userId);
        if (!existing.isEmpty()) return existing;

        // Ensure pool has content (re-seed from .st files if emptied)
        poolService.reseedIfEmpty();

        InterestProfile profile = profileService.getProfile(userId);
        Set<String> seenPostIds = Collections.emptySet();

        List<PoolPost> recommended = poolService.recommend(profile, seenPostIds, 10);

        // If pool is still empty after re-seed, trigger async LLM generation
        if (recommended.isEmpty()) {
            String topCategory = profile.getCategoryScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("General");
            asyncContentGenerator.enqueue(userId, topCategory, 10, "EMPTY_POOL_INIT");
            return Collections.emptyList();
        }

        List<WallPost> initialPosts = recommended.stream()
                .map(p -> p.toWallPost(1, userId))
                .collect(Collectors.toList());

        return wallPostRepository.saveAll(initialPosts);
    }

    @Transactional
    public List<WallPost> generateNextBatch(String userId) {
        int nextBatch = wallPostRepository.findMaxBatchByUserId(userId).orElse(0) + 1;

        InterestProfile profile = profileService.getProfile(userId);
        Set<String> seenPostIds = wallPostRepository.findPostIdsByUserId(userId);

        List<PoolPost> fromPool = poolService.recommend(profile, seenPostIds, 10);

        // Content-level dedup against existing wall posts
        Set<String> existingContentHashes = wallPostRepository.findByUserIdOrderByBatchAscIdAsc(userId).stream()
                .map(wp -> contentHash(wp.getContent()))
                .collect(Collectors.toSet());

        List<WallPost> newPosts = fromPool.stream()
                .filter(pp -> !existingContentHashes.contains(contentHash(pp.getContent())))
                .map(pp -> pp.toWallPost(nextBatch, userId))
                .collect(Collectors.toList());

        if (newPosts.size() < 5) {
            int deficit = 10 - newPosts.size();
            String topCategory = profile.getCategoryScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("General");
            asyncContentGenerator.enqueue(userId, topCategory, deficit, "WALL_DEFICIT");
        }

        if (!newPosts.isEmpty()) {
            wallPostRepository.saveAll(newPosts);
        }

        return newPosts;
    }

    private String contentHash(String content) {
        if (content == null) return "";
        String normalized = content.trim().toLowerCase().replaceAll("\\s+", " ");
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    @Transactional
    public void addTriggerPosts(String userId, List<WallPost> triggerPosts) {
        if (triggerPosts == null || triggerPosts.isEmpty()) return;

        int nextBatch = wallPostRepository.findMaxBatchByUserId(userId).orElse(0) + 1;

        for (WallPost p : triggerPosts) {
            p.setBatch(nextBatch);
            p.setUserId(userId);
        }

        wallPostRepository.saveAll(triggerPosts);
    }

    public List<WallPost> getWallBatch(String userId, int batch) {
        return wallPostRepository.findByUserIdAndBatch(userId, batch);
    }

    public Map<String, Object> getWallStats(String userId) {
        List<WallPost> wall = wallPostRepository.findByUserIdOrderByBatchAscIdAsc(userId);
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

    @Transactional
    public List<WallPost> resetWall(String userId) {
        wallPostRepository.deleteByUserId(userId);
        return initializeWall(userId);
    }
}
