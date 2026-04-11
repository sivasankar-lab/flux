package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Interaction;
import com.example.socialmedia_poc.model.InterestProfile;
import com.example.socialmedia_poc.repository.InterestProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds and maintains per-user interest profiles from interaction data.
 * Profile is recalculated every 5 new interactions.
 */
@Service
public class InterestProfileService {

    private static final int RECALC_INTERVAL = 5;
    /** Recency weight: interactions within the last N hours are weighted 2x. */
    private static final long RECENCY_HOURS = 24;

    private final InterestProfileRepository profileRepository;
    private final InteractionService interactionService;

    public InterestProfileService(InterestProfileRepository profileRepository,
                                  InteractionService interactionService) {
        this.profileRepository = profileRepository;
        this.interactionService = interactionService;
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /** Load or create the user's interest profile. */
    public InterestProfile getProfile(String userId) {
        return profileRepository.findById(userId)
                .orElseGet(() -> rebuildProfile(userId));
    }

    /**
     * Called after every interaction. Checks whether a full recalculation is due
     * (every 5 interactions) and does incremental tracking regardless.
     *
     * Returns the updated profile.
     */
    @Transactional
    public InterestProfile onInteraction(String userId, Interaction interaction) {
        InterestProfile profile = getProfile(userId);

        // Incremental update for consecutive-skip tracking
        if (interaction.getInteractionType() == Interaction.InteractionType.SKIP) {
            profile.setConsecutiveSkips(profile.getConsecutiveSkips() + 1);
        } else {
            profile.setConsecutiveSkips(0);
        }

        profile.setTotalInteractions(profile.getTotalInteractions() + 1);

        // Check if full recalculation is needed
        int sinceLastCalc = profile.getTotalInteractions() - profile.getInteractionCountAtLastUpdate();
        if (sinceLastCalc >= RECALC_INTERVAL) {
            profile = rebuildProfile(userId);
        } else {
            profileRepository.save(profile);
        }

        return profile;
    }

    /**
     * Full rebuild of the profile from all interactions.
     */
    @Transactional
    public InterestProfile rebuildProfile(String userId) {
        List<Interaction> interactions = interactionService.loadInteractions(userId);
        InterestProfile profile = profileRepository.findById(userId)
                .orElse(new InterestProfile(userId));

        if (interactions.isEmpty()) {
            profileRepository.save(profile);
            return profile;
        }

        Instant now = Instant.now();
        Instant recencyThreshold = now.minusSeconds(RECENCY_HOURS * 3600);

        Map<String, Double> weightedScores = new HashMap<>();
        Map<String, Integer> categoryLikes = new HashMap<>();
        Map<String, Integer> categorySkips = new HashMap<>();
        Map<String, Long> categoryDwellMs = new HashMap<>();
        Map<String, Integer> categoryInteractionCount = new HashMap<>();
        int totalLikes = 0;
        int totalSkips = 0;
        long totalDwellMs = 0;
        int consecutiveSkips = 0;

        for (Interaction i : interactions) {
            String category = i.getCategory();
            if (category == null) continue;

            // Recency weight: recent interactions count 2x
            double recencyWeight = (i.getTimestamp() != null && i.getTimestamp().isAfter(recencyThreshold)) ? 2.0 : 1.0;

            // Interaction type weight
            double typeWeight;
            switch (i.getInteractionType()) {
                case LIKE:
                    typeWeight = 3.0;
                    totalLikes++;
                    categoryLikes.merge(category, 1, Integer::sum);
                    consecutiveSkips = 0;
                    break;
                case LONG_READ:
                    typeWeight = 2.0;
                    consecutiveSkips = 0;
                    break;
                case VIEW:
                    typeWeight = 1.0;
                    consecutiveSkips = 0;
                    break;
                case BOOKMARK:
                    typeWeight = 2.5;
                    consecutiveSkips = 0;
                    break;
                case SKIP:
                    typeWeight = -1.0;
                    totalSkips++;
                    categorySkips.merge(category, 1, Integer::sum);
                    consecutiveSkips++;
                    break;
                default:
                    typeWeight = 0.5;
                    consecutiveSkips = 0;
            }

            double weight = typeWeight * recencyWeight;
            weightedScores.merge(category, weight, Double::sum);
            categoryInteractionCount.merge(category, 1, Integer::sum);

            if (i.getDwellTimeMs() != null && i.getDwellTimeMs() > 0) {
                categoryDwellMs.merge(category, i.getDwellTimeMs(), Long::sum);
                totalDwellMs += i.getDwellTimeMs();
            }
        }

        // Normalise category scores to 0.0 – 1.0
        double maxScore = weightedScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (maxScore <= 0) maxScore = 1.0;
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> e : weightedScores.entrySet()) {
            normalized.put(e.getKey(), Math.max(0.0, Math.min(1.0, e.getValue() / maxScore)));
        }

        // Pacing preference
        long avgDwellMs = interactions.size() > 0 ? totalDwellMs / interactions.size() : 4000;
        String pacing;
        String contentLength;
        if (avgDwellMs > 8000) {
            pacing = "slow";
            contentLength = "long";
        } else if (avgDwellMs > 4000) {
            pacing = "moderate";
            contentLength = "medium";
        } else {
            pacing = "fast";
            contentLength = "short";
        }

        profile.setCategoryScores(normalized);
        profile.setCategoryLikes(categoryLikes);
        profile.setCategorySkips(categorySkips);
        profile.setCategoryDwellMs(categoryDwellMs);
        profile.setCategoryInteractionCount(categoryInteractionCount);
        profile.setPreferredPacing(pacing);
        profile.setContentLengthPref(contentLength);
        profile.setTotalInteractions(interactions.size());
        profile.setTotalLikes(totalLikes);
        profile.setTotalSkips(totalSkips);
        profile.setConsecutiveSkips(consecutiveSkips);
        profile.setAvgSessionDepth(interactions.size()); // simplified
        profile.setLastUpdated(Instant.now());
        profile.setInteractionCountAtLastUpdate(interactions.size());

        profileRepository.save(profile);
        return profile;
    }

    /**
     * Find users with similar interest profiles (category score overlap > threshold).
     * Returns user IDs sorted by similarity (highest first).
     */
    public List<String> findSimilarUsers(String userId, double threshold) {
        InterestProfile target = getProfile(userId);
        List<InterestProfile> allProfiles = profileRepository.findAll();

        List<Map.Entry<String, Double>> similarities = new ArrayList<>();
        for (InterestProfile other : allProfiles) {
            if (userId.equals(other.getUserId())) continue;
            double similarity = computeSimilarity(target, other);
            if (similarity >= threshold) {
                similarities.add(new AbstractMap.SimpleEntry<>(other.getUserId(), similarity));
            }
        }

        return similarities.stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** Cosine-like similarity between two profiles based on category scores. */
    private double computeSimilarity(InterestProfile a, InterestProfile b) {
        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(a.getCategoryScores().keySet());
        allCategories.addAll(b.getCategoryScores().keySet());

        if (allCategories.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (String cat : allCategories) {
            double scoreA = a.getCategoryScores().getOrDefault(cat, 0.0);
            double scoreB = b.getCategoryScores().getOrDefault(cat, 0.0);
            dotProduct += scoreA * scoreB;
            normA += scoreA * scoreA;
            normB += scoreB * scoreB;
        }

        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom > 0 ? dotProduct / denom : 0.0;
    }

    // ──────────────────────────────────────────────
    // Topic → Category mapping
    // ──────────────────────────────────────────────

    /** Maps selected interest topics back to parent categories and pre-seeds the profile. */
    @Transactional
    public InterestProfile initializeFromInterests(String userId, List<String> selectedTopics) {
        InterestProfile profile = profileRepository.findById(userId)
                .orElse(new InterestProfile(userId));

        // Map: topic → parent category
        Map<String, String> topicToCategory = buildTopicToCategoryMap();

        // Count how many topics the user selected per category
        Map<String, Integer> categoryHits = new HashMap<>();
        Map<String, Integer> categoryTotal = new HashMap<>();
        for (Map.Entry<String, String> entry : topicToCategory.entrySet()) {
            categoryTotal.merge(entry.getValue(), 1, Integer::sum);
        }
        for (String topic : selectedTopics) {
            String cat = topicToCategory.get(topic);
            if (cat != null) {
                categoryHits.merge(cat, 1, Integer::sum);
            }
        }

        // Compute initial category scores: selected categories get 0.5-1.0 proportional to
        // how many topics were picked within them; unselected categories get 0.1
        Map<String, Double> scores = new HashMap<>();
        for (String cat : categoryTotal.keySet()) {
            int hits = categoryHits.getOrDefault(cat, 0);
            if (hits > 0) {
                int total = categoryTotal.get(cat);
                // Scale from 0.5 (1 topic) to 1.0 (all topics)
                double ratio = (double) hits / total;
                scores.put(cat, 0.5 + 0.5 * ratio);
            } else {
                scores.put(cat, 0.1);
            }
        }

        profile.setCategoryScores(scores);
        profile.setLastUpdated(Instant.now());
        profileRepository.save(profile);
        return profile;
    }

    /** Build topic→category mapping from the interest-topics.json resource. */
    private Map<String, String> buildTopicToCategoryMap() {
        Map<String, String> map = new HashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.io.InputStream is = getClass().getResourceAsStream("/interest-topics.json");
            if (is == null) return map;
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(is);
            com.fasterxml.jackson.databind.JsonNode categories = root.get("categories");
            if (categories != null && categories.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode cat : categories) {
                    String catName = cat.get("name").asText();
                    com.fasterxml.jackson.databind.JsonNode topics = cat.get("topics");
                    if (topics != null && topics.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode topic : topics) {
                            map.put(topic.asText(), catName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // fallback: return empty map
        }
        return map;
    }
}
