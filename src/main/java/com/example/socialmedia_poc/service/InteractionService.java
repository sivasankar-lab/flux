package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Interaction;
import com.example.socialmedia_poc.repository.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InteractionService {

    private final InteractionRepository interactionRepository;

    public InteractionService(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
    }

    @Transactional
    public void recordInteraction(Interaction interaction) {
        if (interaction.getTimestamp() == null) {
            interaction.setTimestamp(java.time.Instant.now());
        }
        interactionRepository.save(interaction);
    }

    public List<Interaction> loadInteractions(String userId) {
        return interactionRepository.findByUserIdOrderByTimestampAsc(userId);
    }

    public List<Interaction> getUserInteractions(String userId) {
        return loadInteractions(userId);
    }

    public UserPreference analyzeUserPreference(String userId) {
        List<Interaction> userInteractions = getUserInteractions(userId);

        if (userInteractions.isEmpty()) {
            return new UserPreference();
        }

        Map<String, Integer> categoryScores = new HashMap<>();
        Map<String, Long> categoryDwellTime = new HashMap<>();
        int totalLikes = 0;
        int totalLongReads = 0;

        for (Interaction interaction : userInteractions) {
            String category = interaction.getCategory();
            if (category != null) {
                categoryScores.put(category, categoryScores.getOrDefault(category, 0) + 1);

                if (interaction.getDwellTimeMs() != null) {
                    categoryDwellTime.put(category,
                            categoryDwellTime.getOrDefault(category, 0L) + interaction.getDwellTimeMs());
                }
            }

            if (interaction.getInteractionType() == Interaction.InteractionType.LIKE) {
                totalLikes++;
            }
            if (interaction.getInteractionType() == Interaction.InteractionType.LONG_READ) {
                totalLongReads++;
            }
        }

        UserPreference preference = new UserPreference();
        preference.setPreferredCategories(
                categoryScores.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(3)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList())
        );

        // Determine preferred pacing based on dwell time
        long avgDwellTime = categoryDwellTime.values().stream()
                .mapToLong(Long::longValue)
                .sum() / Math.max(categoryDwellTime.size(), 1);

        if (avgDwellTime > 8000) {
            preference.setPreferredPacing("Slow");
            preference.setPreferredDepth("deep");
        } else if (avgDwellTime > 4000) {
            preference.setPreferredPacing("Balanced");
            preference.setPreferredDepth("moderate");
        } else {
            preference.setPreferredPacing("Fast");
            preference.setPreferredDepth("shallow");
        }

        preference.setEngagementLevel(totalLikes > 5 || totalLongReads > 3 ? "high" : "medium");

        return preference;
    }

    // Load all interactions across all users (for stats)
    public List<Interaction> loadAllInteractions() {
        return interactionRepository.findAll();
    }

    public static class UserPreference {
        private List<String> preferredCategories = new ArrayList<>();
        private String preferredPacing = "Balanced";
        private String preferredDepth = "moderate";
        private String engagementLevel = "medium";

        public List<String> getPreferredCategories() { return preferredCategories; }
        public void setPreferredCategories(List<String> preferredCategories) { this.preferredCategories = preferredCategories; }

        public String getPreferredPacing() { return preferredPacing; }
        public void setPreferredPacing(String preferredPacing) { this.preferredPacing = preferredPacing; }

        public String getPreferredDepth() { return preferredDepth; }
        public void setPreferredDepth(String preferredDepth) { this.preferredDepth = preferredDepth; }

        public String getEngagementLevel() { return engagementLevel; }
        public void setEngagementLevel(String engagementLevel) { this.engagementLevel = engagementLevel; }
    }
}
