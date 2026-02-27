package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Interaction;
import com.example.socialmedia_poc.model.Meta;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InteractionService {

    private final ObjectMapper mapper;
    private final UserService userService;

    public InteractionService(UserService userService) {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.userService = userService;
    }

    public void recordInteraction(Interaction interaction) throws IOException {
        String userId = interaction.getUserId();
        List<Interaction> interactions = loadInteractions(userId);
        interactions.add(interaction);
        saveInteractions(userId, interactions);
    }

    public List<Interaction> loadInteractions(String userId) throws IOException {
        Path interactionsPath = userService.getUserInteractionsFile(userId);
        
        if (!Files.exists(interactionsPath)) {
            // Create user directory and file if it doesn't exist
            Files.createDirectories(interactionsPath.getParent());
            Files.writeString(interactionsPath, "[]");
            return new ArrayList<>();
        }
        
        try {
            return mapper.readValue(interactionsPath.toFile(), new TypeReference<List<Interaction>>(){});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public List<Interaction> getUserInteractions(String userId) throws IOException {
        return loadInteractions(userId);
    }

    private void saveInteractions(String userId, List<Interaction> interactions) throws IOException {
        Path interactionsPath = userService.getUserInteractionsFile(userId);
        
        if (!Files.exists(interactionsPath.getParent())) {
            Files.createDirectories(interactionsPath.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(interactionsPath.toFile(), interactions);
    }

    public UserPreference analyzeUserPreference(String userId) throws IOException {
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

    public List<SeedWithMeta> loadNextSeeds(String userId) throws IOException {
        Path nextSeedsPath = userService.getUserNextSeedsFile(userId);
        
        if (!Files.exists(nextSeedsPath)) {
            return new ArrayList<>();
        }
        
        try {
            return mapper.readValue(nextSeedsPath.toFile(), new TypeReference<List<SeedWithMeta>>(){});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveNextSeeds(String userId, List<SeedWithMeta> seeds) throws IOException {
        Path nextSeedsPath = userService.getUserNextSeedsFile(userId);
        
        if (!Files.exists(nextSeedsPath.getParent())) {
            Files.createDirectories(nextSeedsPath.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(nextSeedsPath.toFile(), seeds);
    }
    
    // Load all interactions across all users (for stats)
    public List<Interaction> loadAllInteractions() throws IOException {
        List<Interaction> allInteractions = new ArrayList<>();
        Path userDataDir = Paths.get("src/main/resources/user-data");
        
        if (!Files.exists(userDataDir)) {
            return allInteractions;
        }
        
        try (var stream = Files.list(userDataDir)) {
            stream.filter(Files::isDirectory).forEach(userDir -> {
                Path interactionsFile = userDir.resolve("interactions.json");
                if (Files.exists(interactionsFile)) {
                    try {
                        List<Interaction> userInteractions = mapper.readValue(
                            interactionsFile.toFile(), 
                            new TypeReference<List<Interaction>>(){}
                        );
                        allInteractions.addAll(userInteractions);
                    } catch (IOException e) {
                        // Skip this user's interactions if there's an error
                    }
                }
            });
        }
        
        return allInteractions;
    }

    public static class UserPreference {
        private List<String> preferredCategories = new ArrayList<>();
        private String preferredPacing = "Balanced";
        private String preferredDepth = "moderate";
        private String engagementLevel = "medium";

        public List<String> getPreferredCategories() {
            return preferredCategories;
        }

        public void setPreferredCategories(List<String> preferredCategories) {
            this.preferredCategories = preferredCategories;
        }

        public String getPreferredPacing() {
            return preferredPacing;
        }

        public void setPreferredPacing(String preferredPacing) {
            this.preferredPacing = preferredPacing;
        }

        public String getPreferredDepth() {
            return preferredDepth;
        }

        public void setPreferredDepth(String preferredDepth) {
            this.preferredDepth = preferredDepth;
        }

        public String getEngagementLevel() {
            return engagementLevel;
        }

        public void setEngagementLevel(String engagementLevel) {
            this.engagementLevel = engagementLevel;
        }
    }
}
