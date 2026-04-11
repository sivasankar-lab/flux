package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.Interaction;
import com.example.socialmedia_poc.model.InterestProfile;
import com.example.socialmedia_poc.model.WallPost;
import com.example.socialmedia_poc.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/interactions")
public class InteractionController {

    private final InteractionService interactionService;
    private final InterestProfileService profileService;
    private final GenerationTriggerService triggerService;
    private final PostPoolService poolService;
    private final WallService wallService;
    private final AsyncContentGeneratorService asyncContentGenerator;

    public InteractionController(InteractionService interactionService,
                                 InterestProfileService profileService,
                                 GenerationTriggerService triggerService,
                                 PostPoolService poolService,
                                 WallService wallService,
                                 AsyncContentGeneratorService asyncContentGenerator) {
        this.interactionService = interactionService;
        this.profileService = profileService;
        this.triggerService = triggerService;
        this.poolService = poolService;
        this.wallService = wallService;
        this.asyncContentGenerator = asyncContentGenerator;
    }

    @PostMapping("/record")
    public ResponseEntity<?> recordInteraction(@RequestBody Interaction interaction) {
        try {
            interactionService.recordInteraction(interaction);

            poolService.recordEngagement(
                    interaction.getSeedId(),
                    interaction.getInteractionType(),
                    interaction.getDwellTimeMs()
            );

            String userId = interaction.getUserId();
            InterestProfile profile = profileService.onInteraction(userId, interaction);

            Set<String> seenPostIds = wallService.getWall(userId).stream()
                    .map(WallPost::getPostId)
                    .collect(Collectors.toSet());

            GenerationTriggerService.TriggerResult trigger =
                    triggerService.evaluate(userId, interaction, profile, seenPostIds);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Interaction recorded");

            if (trigger.hasTrigger()) {
                response.put("trigger", trigger.getTrigger().name());
                response.put("trigger_message", trigger.getMessage());
            } else {
                response.put("trigger", "NONE");
            }

            response.put("queue_depth", asyncContentGenerator.getQueueSize());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to record interaction: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Interaction>> getUserInteractions(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(interactionService.getUserInteractions(userId));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/user/{userId}/preferences")
    public ResponseEntity<?> getUserPreferences(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(interactionService.analyzeUserPreference(userId));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/user/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(profileService.getProfile(userId));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/user/{userId}/generate-next")
    public ResponseEntity<?> generateNextSeeds(@PathVariable String userId) {
        try {
            InterestProfile profile = profileService.getProfile(userId);
            String topCategory = profile.getCategoryScores().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("General");

            asyncContentGenerator.enqueue(userId, topCategory, 5, "USER_REQUEST");

            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "queued");
            resp.put("message", "Generation queued for " + topCategory + " - content will appear shortly");
            resp.put("queue_depth", asyncContentGenerator.getQueueSize());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to enqueue generation: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/pool/stats")
    public ResponseEntity<?> getPoolStats() {
        try {
            Map<String, Object> stats = poolService.getPoolStats();
            stats.put("generation_queue_depth", asyncContentGenerator.getQueueSize());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            List<Interaction> allInteractions = interactionService.loadAllInteractions();
            long totalInteractions = allInteractions.size();
            long uniqueUsers = allInteractions.stream()
                    .map(Interaction::getUserId).distinct().count();

            Map<Interaction.InteractionType, Long> typeBreakdown = allInteractions.stream()
                    .collect(Collectors.groupingBy(Interaction::getInteractionType, Collectors.counting()));

            Map<String, Object> stats = new HashMap<>();
            stats.put("total_interactions", totalInteractions);
            stats.put("unique_users", uniqueUsers);
            stats.put("interaction_types", typeBreakdown);
            stats.put("generation_queue_depth", asyncContentGenerator.getQueueSize());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
