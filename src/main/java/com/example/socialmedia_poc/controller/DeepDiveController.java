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
            String rawResponse = llmService.generateContent(systemMessage, prompt, 2000);

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
            log.warn("[DeepDive] Failed to parse JSON, attempting field extraction. Error: {}", e.getMessage());
            // Try to extract individual fields from truncated JSON
            return extractFieldsFromPartialJson(raw, category);
        }
    }

    private Map<String, Object> extractFieldsFromPartialJson(String raw, String category) {
        Map<String, Object> result = new LinkedHashMap<>();
        String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "").replaceAll("^```(?:json)?\\s*", "").trim();

        // Try to extract title
        String title = extractJsonStringField(cleaned, "title");
        result.put("title", title != null ? title : "Deep Dive: " + category);

        // Try to extract content
        String content = extractJsonStringField(cleaned, "content");
        if (content != null) {
            // Clean up trailing incomplete sentence if truncated
            if (!content.endsWith(".") && !content.endsWith("!") && !content.endsWith("?")) {
                int lastSentEnd = Math.max(content.lastIndexOf("."), Math.max(content.lastIndexOf("!"), content.lastIndexOf("?")));
                if (lastSentEnd > content.length() / 2) {
                    content = content.substring(0, lastSentEnd + 1);
                }
            }
            result.put("content", content);
        } else {
            // Final fallback: strip JSON syntax and show raw text
            String fallbackText = cleaned
                    .replaceAll("\\{\\s*\"[^\"]+\"\\s*:\\s*\"", "")
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\\\n", "\n")
                    .trim();
            result.put("content", fallbackText);
        }

        // Try to extract key_points
        try {
            int kpStart = cleaned.indexOf("\"key_points\"");
            if (kpStart > 0) {
                int arrStart = cleaned.indexOf("[", kpStart);
                int arrEnd = cleaned.indexOf("]", arrStart);
                if (arrStart > 0 && arrEnd > arrStart) {
                    String arrStr = cleaned.substring(arrStart, arrEnd + 1);
                    ObjectMapper mapper = new ObjectMapper();
                    result.put("key_points", mapper.readValue(arrStr, List.class));
                }
            }
        } catch (Exception ignored) {}
        result.putIfAbsent("key_points", List.of("Expanded analysis of the original post"));

        // Try to extract sources
        try {
            int srcStart = cleaned.indexOf("\"sources\"");
            if (srcStart > 0) {
                int arrStart = cleaned.indexOf("[", srcStart);
                int arrEnd = cleaned.lastIndexOf("]");
                if (arrStart > 0 && arrEnd > arrStart) {
                    String arrStr = cleaned.substring(arrStart, arrEnd + 1);
                    ObjectMapper mapper = new ObjectMapper();
                    result.put("sources", mapper.readValue(arrStr, List.class));
                }
            }
        } catch (Exception ignored) {}
        result.putIfAbsent("sources", List.of());

        return result;
    }

    /** Extract a string field value from possibly-truncated JSON. */
    private String extractJsonStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;

        int valueStart = m.end();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append('\n');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        // Reached end without closing quote — truncated. Return what we have.
        return sb.length() > 0 ? sb.toString() : null;
    }
}
