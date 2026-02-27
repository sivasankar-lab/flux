package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the shared post pool:
 * - Loads initial 100 seeds on startup
 * - Scores posts against user interest profiles
 * - Recommends top-N posts for a user
 * - Adds generated posts back to pool for cross-user reuse
 * - Tracks engagement metrics per post
 */
@Service
public class PostPoolService {

    private static final Path POOL_FILE = Paths.get("src/main/resources/user-data/pool.json");
    private static final Path SEEDS_DIR = Paths.get("src/main/resources/seeds");

    private final ObjectMapper mapper;

    public PostPoolService() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ──────────────────────────────────────────────
    // Startup: seed the pool from .st files if empty
    // ──────────────────────────────────────────────

    @PostConstruct
    public void initPool() {
        try {
            List<PoolPost> pool = loadPool();
            if (!pool.isEmpty()) {
                System.out.println("[Pool] Already initialised with " + pool.size() + " posts");
                return;
            }

            List<PoolPost> seeded = loadAllSeedFiles();
            savePool(seeded);
            System.out.println("[Pool] Initialised pool with " + seeded.size() + " seed posts");
        } catch (IOException e) {
            System.err.println("[Pool] Failed to initialise pool: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Recommendation engine
    // ──────────────────────────────────────────────

    /**
     * Recommend top N posts from the pool for a user.
     * Excludes posts already on the user's wall.
     */
    public List<PoolPost> recommend(InterestProfile profile, Set<String> seenPostIds, int count) throws IOException {
        List<PoolPost> pool = loadPool();

        boolean coldStart = profile.getTotalInteractions() == 0;

        if (coldStart) {
            return coldStartRecommendation(pool, seenPostIds, count);
        }

        // Score every unseen post
        List<ScoredPost> scored = pool.stream()
                .filter(p -> !seenPostIds.contains(p.getPostId()))
                .filter(p -> p.getContent() != null && !p.getContent().isEmpty())
                .map(p -> new ScoredPost(p, scorePost(p, profile)))
                .sorted(Comparator.comparingDouble(ScoredPost::getScore).reversed())
                .collect(Collectors.toList());

        // Ensure diversity: no more than 40% from a single category in the result
        List<PoolPost> result = new ArrayList<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        int maxPerCategory = Math.max(2, (int) Math.ceil(count * 0.4));

        for (ScoredPost sp : scored) {
            if (result.size() >= count) break;
            String cat = sp.post.getCategory();
            int current = categoryCount.getOrDefault(cat, 0);
            if (current < maxPerCategory) {
                result.add(sp.post);
                categoryCount.put(cat, current + 1);
            }
        }

        // Fill remaining slots if diversity filter was too strict
        if (result.size() < count) {
            for (ScoredPost sp : scored) {
                if (result.size() >= count) break;
                if (!result.contains(sp.post)) {
                    result.add(sp.post);
                }
            }
        }

        return result;
    }

    /**
     * Cold-start: pick 1 post per category (diverse sampling).
     */
    private List<PoolPost> coldStartRecommendation(List<PoolPost> pool, Set<String> seenPostIds, int count) {
        Map<String, List<PoolPost>> byCategory = pool.stream()
                .filter(p -> !seenPostIds.contains(p.getPostId()))
                .filter(p -> p.getContent() != null && !p.getContent().isEmpty())
                .collect(Collectors.groupingBy(p -> p.getCategory() != null ? p.getCategory() : "General"));

        List<PoolPost> result = new ArrayList<>();
        List<String> categories = new ArrayList<>(byCategory.keySet());
        Collections.shuffle(categories);

        // Round-robin across categories
        int idx = 0;
        while (result.size() < count && idx < categories.size() * 3) {
            String cat = categories.get(idx % categories.size());
            List<PoolPost> catPosts = byCategory.get(cat);
            if (catPosts != null && !catPosts.isEmpty()) {
                PoolPost pick = catPosts.remove(new Random().nextInt(catPosts.size()));
                result.add(pick);
            }
            idx++;
        }

        return result;
    }

    /**
     * Score a single post against an interest profile.
     *
     * score = (category_match × 0.4) + (engagement × 0.3) + (freshness × 0.2) + (novelty × 0.1)
     */
    private double scorePost(PoolPost post, InterestProfile profile) {
        // Category match: how interested is the user in this post's category?
        double categoryMatch = profile.getCategoryScores()
                .getOrDefault(post.getCategory(), 0.1);

        // Engagement score (already 0-3 range, normalise to 0-1)
        double engagement = Math.min(post.getEngagementScore() / 3.0, 1.0);

        // Freshness: newer posts get a boost (decay over 7 days)
        double ageHours = Duration.between(post.getCreatedAt(), Instant.now()).toHours();
        double freshness = Math.max(0.0, 1.0 - (ageHours / (7 * 24)));

        // Novelty: generated posts with matching interest get a small boost
        double novelty = 0.5;
        if (post.getSource() == PoolPost.PostSource.GENERATED && post.getGeneratedForInterest() != null) {
            double interestScore = profile.getCategoryScores()
                    .getOrDefault(post.getGeneratedForInterest(), 0.0);
            novelty = 0.5 + (interestScore * 0.5); // 0.5 – 1.0
        }

        return (categoryMatch * 0.4) + (engagement * 0.3) + (freshness * 0.2) + (novelty * 0.1);
    }

    // ──────────────────────────────────────────────
    // Pool mutations
    // ──────────────────────────────────────────────

    /** Add generated posts to the pool for cross-user reuse. */
    public void addToPool(List<PoolPost> newPosts) throws IOException {
        List<PoolPost> pool = loadPool();
        Set<String> existingIds = pool.stream()
                .map(PoolPost::getPostId)
                .collect(Collectors.toSet());

        for (PoolPost p : newPosts) {
            if (!existingIds.contains(p.getPostId())) {
                pool.add(p);
            }
        }
        savePool(pool);
    }

    /** Update engagement metrics for a post after an interaction. */
    public void recordEngagement(String postId, Interaction.InteractionType type, Long dwellMs) throws IOException {
        List<PoolPost> pool = loadPool();
        boolean updated = false;

        for (PoolPost p : pool) {
            if (postId.equals(p.getPostId())) {
                switch (type) {
                    case VIEW:
                        p.setViewCount(p.getViewCount() + 1);
                        break;
                    case LIKE:
                        p.setLikeCount(p.getLikeCount() + 1);
                        break;
                    case LONG_READ:
                        p.setLongReadCount(p.getLongReadCount() + 1);
                        break;
                    case SKIP:
                        p.setSkipCount(p.getSkipCount() + 1);
                        break;
                    default:
                        break;
                }
                if (dwellMs != null && dwellMs > 0) {
                    int interactions = p.getViewCount() + p.getLongReadCount();
                    p.setTotalDwellMs(p.getTotalDwellMs() + dwellMs);
                    p.setAvgDwellMs(interactions > 0 ? p.getTotalDwellMs() / interactions : 0);
                }
                p.recalculateEngagement();
                updated = true;
                break;
            }
        }

        if (updated) {
            savePool(pool);
        }
    }

    /** Get the full pool (read-only access). */
    public List<PoolPost> loadPool() throws IOException {
        if (!Files.exists(POOL_FILE)) {
            Files.createDirectories(POOL_FILE.getParent());
            Files.writeString(POOL_FILE, "[]");
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(POOL_FILE.toFile(), new TypeReference<List<PoolPost>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** Get pool stats. */
    public Map<String, Object> getPoolStats() throws IOException {
        List<PoolPost> pool = loadPool();
        long seeds = pool.stream().filter(p -> p.getSource() == PoolPost.PostSource.SEED).count();
        long generated = pool.stream().filter(p -> p.getSource() == PoolPost.PostSource.GENERATED).count();
        Map<String, Long> byCategory = pool.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(PoolPost::getCategory, Collectors.counting()));

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_posts", pool.size());
        stats.put("seed_posts", seeds);
        stats.put("generated_posts", generated);
        stats.put("categories", byCategory);
        return stats;
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private void savePool(List<PoolPost> pool) throws IOException {
        Files.createDirectories(POOL_FILE.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(POOL_FILE.toFile(), pool);
    }

    /** Load all .st seed files and convert to PoolPosts. */
    private List<PoolPost> loadAllSeedFiles() throws IOException {
        if (!Files.exists(SEEDS_DIR)) {
            return Collections.emptyList();
        }

        // Load meta configs for category info
        InputStream inputStream = TypeReference.class.getResourceAsStream("/meta-configs.json");
        List<Meta> metas = mapper.readValue(inputStream, new TypeReference<List<Meta>>() {});
        Map<String, Meta> metaMap = metas.stream()
                .collect(Collectors.toMap(Meta::getCategory, m -> m, (a, b) -> a));

        try (Stream<Path> paths = Files.walk(SEEDS_DIR)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".st"))
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();
                            String category = extractCategoryFromFileName(fileName);
                            Meta meta = metaMap.get(category);

                            SeedWithMeta seed = new SeedWithMeta();
                            seed.setSeedId(UUID.randomUUID().toString());
                            seed.setContent(content);
                            seed.setCategory(category);
                            if (meta != null) seed.setMetaConfig(meta.getMetaConfig());

                            return PoolPost.fromSeedFile(seed);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private String extractCategoryFromFileName(String fileName) {
        String withoutExt = fileName.replaceAll("-\\d+\\.st$", "");
        return withoutExt
                .replace("___", " & ")
                .replace("__", " (")
                .replace("_-", ")")
                .replace("_", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // Helper for scoring
    private static class ScoredPost {
        final PoolPost post;
        final double score;
        ScoredPost(PoolPost post, double score) {
            this.post = post;
            this.score = score;
        }
        double getScore() { return score; }
    }
}
