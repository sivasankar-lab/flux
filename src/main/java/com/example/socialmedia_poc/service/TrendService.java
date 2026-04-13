package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.PostTrend;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.example.socialmedia_poc.repository.PostTrendRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages trend computation for posts.
 * Snapshots engagement metrics daily and computes trend scores.
 */
@Service
public class TrendService {

    private static final Logger log = LoggerFactory.getLogger(TrendService.class);

    private final PostTrendRepository trendRepository;
    private final PoolPostRepository poolPostRepository;

    public TrendService(PostTrendRepository trendRepository,
                        PoolPostRepository poolPostRepository) {
        this.trendRepository = trendRepository;
        this.poolPostRepository = poolPostRepository;
    }

    /**
     * Snapshot current engagement metrics into post_trends table.
     * Runs every hour. Also callable manually.
     */
    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void computeTrends() {
        log.info("[Trends] Computing trend snapshots...");
        LocalDate today = LocalDate.now();
        List<PoolPost> allPosts = poolPostRepository.findAll();

        for (PoolPost post : allPosts) {
            if (post.getViewCount() == 0 && post.getLikeCount() == 0) continue;

            Optional<PostTrend> existing = trendRepository.findByPostIdAndTrendDate(post.getPostId(), today);
            PostTrend trend = existing.orElseGet(PostTrend::new);

            trend.setPostId(post.getPostId());
            trend.setTrendDate(today);
            trend.setCategory(post.getCategory());
            trend.setLikeCount(post.getLikeCount());
            trend.setViewCount(post.getViewCount());
            trend.setEngagementScore(post.getEngagementScore());

            // Trend score = weighted recent engagement velocity
            // Compare to yesterday's snapshot to compute growth
            double trendScore = computeTrendScore(post, today);
            trend.setTrendScore(trendScore);

            // Featured = high engagement + good like ratio
            boolean featured = isFeatured(post);
            trend.setFeatured(featured);

            trendRepository.save(trend);
        }

        // Cleanup old data (keep 30 days)
        trendRepository.deleteByTrendDateBefore(today.minusDays(30));
        log.info("[Trends] Computed trends for {} posts", allPosts.size());
    }

    private double computeTrendScore(PoolPost post, LocalDate today) {
        // Get yesterday's trend data for comparison
        Optional<PostTrend> yesterday = trendRepository.findByPostIdAndTrendDate(
                post.getPostId(), today.minusDays(1));

        int prevLikes = yesterday.map(PostTrend::getLikeCount).orElse(0);
        int prevViews = yesterday.map(PostTrend::getViewCount).orElse(0);

        int likeDelta = post.getLikeCount() - prevLikes;
        int viewDelta = post.getViewCount() - prevViews;

        // Trend score: likes are 5x more valuable, plus view growth
        // Recency boost: newer posts get a multiplier
        double rawScore = (likeDelta * 5.0) + (viewDelta * 1.0) + post.getEngagementScore();

        // Apply recency decay (posts older than 7 days get diminishing score)
        if (post.getCreatedAt() != null) {
            long ageHours = java.time.Duration.between(post.getCreatedAt(), java.time.Instant.now()).toHours();
            double recencyMultiplier = Math.max(0.1, 1.0 - (ageHours / (7.0 * 24.0)));
            rawScore *= recencyMultiplier;
        }

        return Math.max(0, rawScore);
    }

    private boolean isFeatured(PoolPost post) {
        // Featured = engagement score > 2.0 AND at least 2 likes AND like ratio > 30%
        int total = post.getViewCount() + post.getLikeCount() + post.getLongReadCount();
        if (total == 0) return false;
        double likeRatio = (double) post.getLikeCount() / total;
        return post.getEngagementScore() > 2.0 && post.getLikeCount() >= 2 && likeRatio > 0.3;
    }

    /** Get trending posts (highest trend score today) */
    public List<PoolPost> getTrendingPosts(int limit) {
        LocalDate today = LocalDate.now();
        List<PostTrend> trends = trendRepository.findByTrendDateOrderByTrendScoreDesc(today);

        if (trends.isEmpty()) {
            // Fallback: use engagement_score from pool directly
            return poolPostRepository.findAll().stream()
                    .filter(p -> p.getViewCount() > 0 || p.getLikeCount() > 0)
                    .sorted(Comparator.comparingDouble(PoolPost::getEngagementScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        Set<String> postIds = trends.stream()
                .limit(limit)
                .map(PostTrend::getPostId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return resolvePostsInOrder(postIds);
    }

    /** Get featured posts */
    public List<PoolPost> getFeaturedPosts(int limit) {
        LocalDate today = LocalDate.now();
        List<PostTrend> featured = trendRepository.findByTrendDateAndFeaturedTrueOrderByEngagementScoreDesc(today);

        if (featured.isEmpty()) {
            // Fallback: top engagement posts
            return poolPostRepository.findAll().stream()
                    .filter(p -> p.getEngagementScore() > 1.0)
                    .sorted(Comparator.comparingDouble(PoolPost::getEngagementScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        Set<String> postIds = featured.stream()
                .limit(limit)
                .map(PostTrend::getPostId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return resolvePostsInOrder(postIds);
    }

    /** Get most-liked posts (all time from pool) */
    public List<PoolPost> getMostLikedPosts(int limit) {
        return poolPostRepository.findAll().stream()
                .filter(p -> p.getLikeCount() > 0)
                .sorted(Comparator.comparingInt(PoolPost::getLikeCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** Resolve PoolPost entities from IDs, preserving order */
    private List<PoolPost> resolvePostsInOrder(Set<String> postIds) {
        Map<String, PoolPost> postMap = new HashMap<>();
        for (String id : postIds) {
            poolPostRepository.findById(id).ifPresent(p -> postMap.put(id, p));
        }
        return postIds.stream()
                .filter(postMap::containsKey)
                .map(postMap::get)
                .collect(Collectors.toList());
    }
}
