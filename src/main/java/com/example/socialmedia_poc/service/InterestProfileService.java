package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Interaction;
import com.example.socialmedia_poc.model.InterestProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final ObjectMapper mapper;
    private final UserService userService;
    private final InteractionService interactionService;

    public InterestProfileService(UserService userService, InteractionService interactionService) {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.userService = userService;
        this.interactionService = interactionService;
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /** Load or create the user's interest profile. */
    public InterestProfile getProfile(String userId) throws IOException {
        Path path = profilePath(userId);
        if (Files.exists(path)) {
            try {
                return mapper.readValue(path.toFile(), InterestProfile.class);
            } catch (IOException e) {
                // Corrupted file, rebuild
            }
        }
        return rebuildProfile(userId);
    }

    /**
     * Called after every interaction. Checks whether a full recalculation is due
     * (every 5 interactions) and does incremental tracking regardless.
     *
     * Returns the updated profile.
     */
    public InterestProfile onInteraction(String userId, Interaction interaction) throws IOException {
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
            saveProfile(userId, profile);
        }

        return profile;
    }

    /**
     * Full rebuild of the profile from all interactions.
     */
    public InterestProfile rebuildProfile(String userId) throws IOException {
        List<Interaction> interactions = interactionService.loadInteractions(userId);
        InterestProfile profile = new InterestProfile(userId);

        if (interactions.isEmpty()) {
            saveProfile(userId, profile);
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

        saveProfile(userId, profile);
        return profile;
    }

    /**
     * Find users with similar interest profiles (category score overlap > threshold).
     * Returns user IDs sorted by similarity (highest first).
     */
    public List<String> findSimilarUsers(String userId, double threshold) throws IOException {
        InterestProfile target = getProfile(userId);
        Path userDataDir = userService.getUserDataDirectory(userId).getParent();

        if (!Files.exists(userDataDir)) return Collections.emptyList();

        List<Map.Entry<String, Double>> similarities = new ArrayList<>();

        try (var stream = Files.list(userDataDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                String otherUserId = dir.getFileName().toString();
                if (otherUserId.equals(userId)) return;

                Path profileFile = dir.resolve("interest-profile.json");
                if (!Files.exists(profileFile)) return;

                try {
                    InterestProfile other = mapper.readValue(profileFile.toFile(), InterestProfile.class);
                    double similarity = computeSimilarity(target, other);
                    if (similarity >= threshold) {
                        similarities.add(new AbstractMap.SimpleEntry<>(otherUserId, similarity));
                    }
                } catch (IOException ignored) {}
            });
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
    // Persistence
    // ──────────────────────────────────────────────

    private Path profilePath(String userId) {
        return userService.getUserDataDirectory(userId).resolve("interest-profile.json");
    }

    private void saveProfile(String userId, InterestProfile profile) throws IOException {
        Path path = profilePath(userId);
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), profile);
    }
}
