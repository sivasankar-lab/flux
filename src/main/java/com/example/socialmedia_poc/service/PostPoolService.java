package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.*;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the shared post pool using PostgreSQL via JPA:
 * - Loads initial seeds from .st files on startup (if pool table is empty)
 * - Provides recommendation engine for wall generation
 * - Supports cross-user reuse
 * - Tracks engagement metrics per post
 */
@Service
public class PostPoolService {

    private static final Logger log = LoggerFactory.getLogger(PostPoolService.class);

    private final PoolPostRepository poolPostRepository;
    private final ObjectMapper mapper;

    public PostPoolService(PoolPostRepository poolPostRepository) {
        this.poolPostRepository = poolPostRepository;
        this.mapper = new ObjectMapper();
    }

    @PostConstruct
    @Transactional
    public void initPool() {
        try {
            long count = poolPostRepository.count();
            if (count > 0) {
                log.info("[Pool] Already initialised with {} posts", count);
                return;
            }

            List<PoolPost> seeded = loadAllSeedFiles();
            if (!seeded.isEmpty()) {
                poolPostRepository.saveAll(seeded);
                log.info("[Pool] Initialised pool with {} seed posts", seeded.size());
            }
        } catch (Exception e) {
            log.error("[Pool] Failed to initialise pool: {}", e.getMessage());
        }
    }

    public List<PoolPost> recommend(InterestProfile profile, Set<String> seenPostIds, int count) {
        List<PoolPost> pool;
        if (seenPostIds != null && !seenPostIds.isEmpty()) {
            pool = poolPostRepository.findByPostIdNotIn(seenPostIds);
        } else {
            pool = poolPostRepository.findAll();
        }

        boolean coldStart = profile.getTotalInteractions() == 0;

        if (coldStart) {
            return deduplicateByContent(coldStartRecommendation(pool, count));
        }

        List<ScoredPost> scored = pool.stream()
                .filter(p -> p.getContent() != null && !p.getContent().isEmpty())
                .map(p -> new ScoredPost(p, scorePost(p, profile)))
                .sorted(Comparator.comparingDouble(ScoredPost::getScore).reversed())
                .collect(Collectors.toList());

        List<PoolPost> result = new ArrayList<>();
        Set<String> seenContentHashes = new HashSet<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        int maxPerCategory = Math.max(2, (int) Math.ceil(count * 0.4));

        for (ScoredPost sp : scored) {
            if (result.size() >= count) break;
            String hash = contentHash(sp.post.getContent());
            if (seenContentHashes.contains(hash)) continue; // skip duplicate content
            String cat = sp.post.getCategory();
            int current = categoryCount.getOrDefault(cat, 0);
            if (current < maxPerCategory) {
                result.add(sp.post);
                seenContentHashes.add(hash);
                categoryCount.put(cat, current + 1);
            }
        }

        if (result.size() < count) {
            for (ScoredPost sp : scored) {
                if (result.size() >= count) break;
                String hash = contentHash(sp.post.getContent());
                if (!seenContentHashes.contains(hash)) {
                    result.add(sp.post);
                    seenContentHashes.add(hash);
                }
            }
        }

        return result;
    }

    /** Content-level dedup: normalize + first 100 chars as fingerprint */
    private String contentHash(String content) {
        if (content == null) return "";
        String normalized = content.trim().toLowerCase().replaceAll("\\s+", " ");
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private List<PoolPost> deduplicateByContent(List<PoolPost> posts) {
        Set<String> seen = new HashSet<>();
        return posts.stream().filter(p -> {
            String hash = contentHash(p.getContent());
            return seen.add(hash);
        }).collect(Collectors.toList());
    }

    private List<PoolPost> coldStartRecommendation(List<PoolPost> pool, int count) {
        Map<String, List<PoolPost>> byCategory = pool.stream()
                .filter(p -> p.getContent() != null && !p.getContent().isEmpty())
                .collect(Collectors.groupingBy(p -> p.getCategory() != null ? p.getCategory() : "General"));

        List<PoolPost> result = new ArrayList<>();
        List<String> categories = new ArrayList<>(byCategory.keySet());
        Collections.shuffle(categories);

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

    private double scorePost(PoolPost post, InterestProfile profile) {
        double categoryMatch = profile.getCategoryScores()
                .getOrDefault(post.getCategory(), 0.1);
        double engagement = Math.min(post.getEngagementScore() / 3.0, 1.0);
        double ageHours = Duration.between(post.getCreatedAt(), Instant.now()).toHours();
        double freshness = Math.max(0.0, 1.0 - (ageHours / (7 * 24)));
        double novelty = 0.5;
        if (post.getSource() == PoolPost.PostSource.GENERATED && post.getGeneratedForInterest() != null) {
            double interestScore = profile.getCategoryScores()
                    .getOrDefault(post.getGeneratedForInterest(), 0.0);
            novelty = 0.5 + (interestScore * 0.5);
        }
        return (categoryMatch * 0.4) + (engagement * 0.3) + (freshness * 0.2) + (novelty * 0.1);
    }

    @Transactional
    public void addToPool(List<PoolPost> newPosts) {
        for (PoolPost p : newPosts) {
            if (!poolPostRepository.existsById(p.getPostId())) {
                poolPostRepository.save(p);
            }
        }
    }

    @Transactional
    public void recordEngagement(String postId, Interaction.InteractionType type, Long dwellMs) {
        poolPostRepository.findById(postId).ifPresent(p -> {
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
            poolPostRepository.save(p);
        });
    }

    public List<PoolPost> loadPool() {
        return poolPostRepository.findAll();
    }

    public Map<String, Object> getPoolStats() {
        List<PoolPost> pool = poolPostRepository.findAll();
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

    private List<PoolPost> loadAllSeedFiles() throws IOException {
        // Read the seed index file from the classpath (works inside a JAR)
        InputStream indexStream = getClass().getResourceAsStream("/seeds/seed-index.txt");
        if (indexStream == null) {
            log.warn("[Pool] No /seeds/seed-index.txt found on classpath");
            return Collections.emptyList();
        }

        List<String> seedFileNames;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
            seedFileNames = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && line.endsWith(".st"))
                    .collect(Collectors.toList());
        }

        if (seedFileNames.isEmpty()) {
            log.warn("[Pool] seed-index.txt is empty");
            return Collections.emptyList();
        }

        InputStream metaStream = getClass().getResourceAsStream("/meta-configs.json");
        if (metaStream == null) {
            log.warn("[Pool] No /meta-configs.json found on classpath");
            return Collections.emptyList();
        }
        List<Meta> metas = mapper.readValue(metaStream, new TypeReference<List<Meta>>() {});
        Map<String, Meta> metaMap = metas.stream()
                .collect(Collectors.toMap(Meta::getCategory, m -> m, (a, b) -> a));

        List<PoolPost> posts = new ArrayList<>();
        for (String fileName : seedFileNames) {
            try {
                InputStream seedStream = getClass().getResourceAsStream("/seeds/" + fileName);
                if (seedStream == null) {
                    log.warn("[Pool] Seed file not found on classpath: /seeds/{}", fileName);
                    continue;
                }
                String content = new String(seedStream.readAllBytes(), StandardCharsets.UTF_8);
                if (content.trim().isEmpty()) {
                    continue;
                }
                String category = extractCategoryFromFileName(fileName);
                Meta meta = metaMap.get(category);

                SeedWithMeta seed = new SeedWithMeta();
                seed.setSeedId(UUID.randomUUID().toString());
                seed.setContent(content);
                seed.setCategory(category);
                if (meta != null) seed.setMetaConfig(meta.getMetaConfig());

                posts.add(PoolPost.fromSeedFile(seed));
            } catch (Exception e) {
                log.warn("[Pool] Failed to read seed file: {}", fileName, e);
            }
        }

        log.info("[Pool] Loaded {} seed files from classpath", posts.size());
        return posts;
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
