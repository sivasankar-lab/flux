package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.Interaction;
import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.example.socialmedia_poc.service.InteractionService;
import com.example.socialmedia_poc.service.LLMService;
import com.example.socialmedia_poc.service.PostPoolService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/deepdive")
public class DeepDiveController {

    private static final Logger log = LoggerFactory.getLogger(DeepDiveController.class);

    private final PoolPostRepository poolPostRepository;
    private final LLMService llmService;
    private final InteractionService interactionService;
    private final PostPoolService postPoolService;

    public DeepDiveController(PoolPostRepository poolPostRepository,
                              @Qualifier("activeLLMService") LLMService llmService,
                              InteractionService interactionService,
                              PostPoolService postPoolService) {
        this.poolPostRepository = poolPostRepository;
        this.llmService = llmService;
        this.interactionService = interactionService;
        this.postPoolService = postPoolService;
    }

    @PostMapping("/{postId}")
    public ResponseEntity<?> deepDive(@PathVariable String postId,
                                      @RequestBody Map<String, String> body) {
        try {
            String userId = body.get("user_id");
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "user_id is required"));
            }

            Optional<PoolPost> optPost = poolPostRepository.findById(postId);
            if (optPost.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            PoolPost post = optPost.get();
            String postContent = post.getContent();
            String category = post.getCategory() != null ? post.getCategory() : "General";

            // Record DEEP_DIVE interaction
            Interaction interaction = new Interaction();
            interaction.setUserId(userId);
            interaction.setSeedId(postId);
            interaction.setInteractionType(Interaction.InteractionType.DEEP_DIVE);
            interaction.setCategory(category);
            interactionService.recordInteraction(interaction);
            postPoolService.recordEngagement(postId, Interaction.InteractionType.DEEP_DIVE, null);

            // Build deep dive prompt
            String systemMessage = DEEP_DIVE_SYSTEM;
            String prompt = buildDeepDivePrompt(postContent, category);

            log.info("[DeepDive] Generating for post '{}' (category: {}) via {}", postId, category, llmService.getProviderName());
            String rawResponse = llmService.generateContent(systemMessage, prompt);

            // Parse structured JSON from LLM response
            Map<String, Object> result = parseDeepDiveResponse(rawResponse, postContent, category);
            result.put("post_id", postId);
            result.put("original_content", postContent);
            result.put("category", category);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[DeepDive] Failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Deep dive generation failed", "details", e.getMessage()));
        }
    }

    // ── System Message ──

    private static final String DEEP_DIVE_SYSTEM =
            "You are Flux Deep Dive — an expert research assistant that expands short social media posts " +
            "into rich, well-researched articles. " +
            "You must respond ONLY with valid JSON (no markdown fences, no explanation). " +
            "JSON schema: {\"title\": \"string\", \"content\": \"string (300-500 words, markdown OK)\", " +
            "\"key_points\": [\"string\", ...], \"sources\": [{\"title\": \"string\", \"url\": \"string\", \"description\": \"string\"}, ...]} " +
            "Rules: " +
            "- The content must expand on the original post with real facts, data, and context. " +
            "- Include 3-5 key points as a concise summary list. " +
            "- Provide 2-4 real, verifiable source URLs (Wikipedia, reputable news, academic sources). " +
            "- Never fabricate URLs. Use well-known domains you are confident exist. " +
            "- Write in a clear, engaging, human tone. No AI slop. No filler phrases. " +
            "- Do NOT include <think> tags, reasoning blocks, or anything outside the JSON.";

    private String buildDeepDivePrompt(String postContent, String category) {
        return "Expand this short social media post into a deep-dive article.\n\n" +
               "ORIGINAL POST:\n\"" + postContent + "\"\n\n" +
               "CATEGORY: " + category + "\n\n" +
               "Generate a comprehensive deep dive with real facts, data, and verifiable reference links. " +
               "Return ONLY the JSON object. No wrapping, no markdown fences.";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDeepDiveResponse(String raw, String originalContent, String category) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Try to extract JSON from response (handle cases where LLM wraps in markdown)
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            // Remove any <think> blocks
            json = json.replaceAll("(?s)<think>.*?</think>", "").trim();

            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[DeepDive] Failed to parse JSON response, building fallback. Error: {}", e.getMessage());
            // Fallback: wrap raw text as content
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "Deep Dive: " + category);
            fallback.put("content", raw.replaceAll("(?s)<think>.*?</think>", "").replaceAll("<[^>]+>", "").trim());
            fallback.put("key_points", List.of("Expanded analysis of the original post"));
            fallback.put("sources", List.of());
            return fallback;
        }
    }
}
