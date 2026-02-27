package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluates trigger conditions after each interaction.
 *
 * When a trigger fires, generation is ENQUEUED to the AsyncContentGeneratorService
 * (runs in a background thread). The trigger result carries the type + message
 * but NO inline posts — new content will appear in the pool for subsequent requests.
 *
 * Triggers:
 *  - DEEP_INTEREST   : 3+ likes in same category this session
 *  - POOL_EXHAUSTION : <5 unseen pool posts match top interests
 *  - ENGAGEMENT_DROP : 3 consecutive skips
 *  - NONE            : no trigger fired
 */
@Service
public class GenerationTriggerService {

    private final PostPoolService poolService;
    private final AsyncContentGeneratorService asyncContentGenerator;
    private final InteractionService interactionService;

    public GenerationTriggerService(PostPoolService poolService,
                                    AsyncContentGeneratorService asyncContentGenerator,
                                    InteractionService interactionService) {
        this.poolService = poolService;
        this.asyncContentGenerator = asyncContentGenerator;
        this.interactionService = interactionService;
    }

    // ──────────────────────────────────────────────
    // Trigger evaluation
    // ──────────────────────────────────────────────

    /**
     * Evaluate all server-side triggers given the latest interaction
     * and the user's current interest profile.
     *
     * Returns a TriggerResult describing what (if anything) should happen.
     */
    public TriggerResult evaluate(String userId, Interaction latestInteraction,
                                   InterestProfile profile, Set<String> seenPostIds) throws IOException {
        // 1. ENGAGEMENT_DROP: 3 consecutive skips
        if (profile.getConsecutiveSkips() >= 3) {
            return handleEngagementDrop(userId, profile, seenPostIds);
        }

        // 2. DEEP_INTEREST: 3+ likes in same category recently
        String deepCategory = detectDeepInterest(userId);
        if (deepCategory != null) {
            return handleDeepInterest(userId, deepCategory, seenPostIds);
        }

        // 3. POOL_EXHAUSTION: <5 unseen posts matching top interests in pool
        if (isPoolExhausted(profile, seenPostIds)) {
            return handlePoolExhaustion(userId, profile, seenPostIds);
        }

        return TriggerResult.none();
    }

    // ──────────────────────────────────────────────
    // Trigger handlers (async enqueue — no blocking LLM calls)
    // ──────────────────────────────────────────────

    private TriggerResult handleDeepInterest(String userId, String category, Set<String> seenPostIds) {
        // Enqueue 3 posts in the deep-interest category (background thread)
        asyncContentGenerator.enqueue(userId, category, 3, "DEEP_INTEREST");
        return new TriggerResult(TriggerType.DEEP_INTEREST, Collections.emptyList(),
                "You seem to love " + category + "! Generating more for you...");
    }

    private TriggerResult handleEngagementDrop(String userId, InterestProfile profile, Set<String> seenPostIds) {
        String underservedCategory = findUnderservedCategory(profile);
        if (underservedCategory == null) {
            return TriggerResult.none();
        }
        asyncContentGenerator.enqueue(userId, underservedCategory, 3, "ENGAGEMENT_DROP");
        return new TriggerResult(TriggerType.ENGAGEMENT_DROP, Collections.emptyList(),
                "Switching things up – fresh " + underservedCategory + " content incoming!");
    }

    private TriggerResult handlePoolExhaustion(String userId, InterestProfile profile, Set<String> seenPostIds) {
        List<String> topCategories = profile.getCategoryScores().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topCategories.isEmpty()) return TriggerResult.none();

        Map<String, Integer> bulk = new LinkedHashMap<>();
        for (String cat : topCategories) {
            bulk.put(cat, 3);
        }
        asyncContentGenerator.enqueueBulk(userId, bulk, "POOL_EXHAUSTION");

        return new TriggerResult(TriggerType.POOL_EXHAUSTION, Collections.emptyList(),
                "Fresh content being prepared for your interests!");
    }

    // ──────────────────────────────────────────────
    // Detection helpers
    // ──────────────────────────────────────────────

    /**
     * Detect "deep interest": 3+ likes in the same category in recent interactions (last 20).
     */
    private String detectDeepInterest(String userId) throws IOException {
        List<Interaction> interactions = interactionService.loadInteractions(userId);
        if (interactions.size() < 3) return null;

        // Look at last 20 interactions
        int startIdx = Math.max(0, interactions.size() - 20);
        List<Interaction> recent = interactions.subList(startIdx, interactions.size());

        Map<String, Integer> likeCounts = new HashMap<>();
        for (Interaction i : recent) {
            if (i.getInteractionType() == Interaction.InteractionType.LIKE && i.getCategory() != null) {
                likeCounts.merge(i.getCategory(), 1, Integer::sum);
            }
        }

        return likeCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Check if <5 unseen pool posts match the user's top interests.
     */
    private boolean isPoolExhausted(InterestProfile profile, Set<String> seenPostIds) throws IOException {
        List<String> topCategories = profile.getCategoryScores().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topCategories.isEmpty()) return false;

        List<PoolPost> pool = poolService.loadPool();
        long unseenMatches = pool.stream()
                .filter(p -> !seenPostIds.contains(p.getPostId()))
                .filter(p -> topCategories.contains(p.getCategory()))
                .count();

        return unseenMatches < 5;
    }

    /**
     * Find highest-scoring category that the user hasn't been skipping lately.
     */
    private String findUnderservedCategory(InterestProfile profile) {
        Map<String, Integer> skips = profile.getCategorySkips();
        Map<String, Double> scores = profile.getCategoryScores();

        // Categories the user has high interest but hasn't been getting
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .filter(e -> skips.getOrDefault(e.getKey(), 0) < 3) // not heavily skipped overall
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(scores.keySet().stream().findFirst().orElse(null));
    }

    // ──────────────────────────────────────────────
    // Trigger types and result DTO
    // ──────────────────────────────────────────────

    public enum TriggerType {
        NONE, DEEP_INTEREST, POOL_EXHAUSTION, ENGAGEMENT_DROP, SCROLL_END
    }

    public static class TriggerResult {
        @JsonProperty("trigger")
        private TriggerType trigger;

        @JsonProperty("posts")
        private List<WallPost> posts;

        @JsonProperty("message")
        private String message;

        public TriggerResult() {}

        public TriggerResult(TriggerType trigger, List<WallPost> posts, String message) {
            this.trigger = trigger;
            this.posts = posts;
            this.message = message;
        }

        public static TriggerResult none() {
            return new TriggerResult(TriggerType.NONE, Collections.emptyList(), null);
        }

        public TriggerType getTrigger() { return trigger; }
        public void setTrigger(TriggerType trigger) { this.trigger = trigger; }

        public List<WallPost> getPosts() { return posts; }
        public void setPosts(List<WallPost> posts) { this.posts = posts; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public boolean hasTrigger() { return trigger != TriggerType.NONE; }
    }
}
