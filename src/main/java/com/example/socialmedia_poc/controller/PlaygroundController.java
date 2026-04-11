package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.config.ApiKeyStore;
import com.example.socialmedia_poc.model.Meta;
import com.example.socialmedia_poc.service.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;

/**
 * Test / playground endpoint for fine-tuning LLM content generation.
 * Lets you tweak system message, prompt, temperature, max_tokens, etc.
 * and see both raw and cleaned output.
 */
@RestController
@RequestMapping("/v1/playground")
public class PlaygroundController {

    private static final Logger log = LoggerFactory.getLogger(PlaygroundController.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final LLMService llmService;
    private final ApiKeyStore apiKeyStore;
    private final String huggingFaceModel;
    private final String huggingFaceBaseUrl;

    public PlaygroundController(
            @Qualifier("activeLLMService") LLMService llmService,
            ApiKeyStore apiKeyStore,
            @Value("${huggingface.model:meta-llama/Llama-3.3-70B-Instruct}") String huggingFaceModel,
            @Value("${huggingface.baseurl:https://router.huggingface.co}") String huggingFaceBaseUrl) {
        this.llmService = llmService;
        this.apiKeyStore = apiKeyStore;
        this.huggingFaceModel = huggingFaceModel;
        this.huggingFaceBaseUrl = huggingFaceBaseUrl;
    }

    /** GET meta-configs so the frontend can populate category dropdowns. */
    @GetMapping("/meta-configs")
    public List<Meta> getMetaConfigs() throws Exception {
        InputStream is = getClass().getResourceAsStream("/meta-configs.json");
        return mapper.readValue(is, new TypeReference<List<Meta>>() {});
    }

    /** GET active provider info. */
    @GetMapping("/provider")
    public Map<String, Object> getProvider() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("provider", llmService.getProviderName());
        info.put("model", huggingFaceModel);
        return info;
    }

    /**
     * POST generate content with full control over parameters.
     * Body: { system_message, prompt, temperature, max_tokens, top_p }
     * Returns: { raw, cleaned, elapsed_ms, model, parameters }
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> request) {
        String systemMessage = (String) request.getOrDefault("system_message", "");
        String prompt = (String) request.getOrDefault("prompt", "");
        double temperature = ((Number) request.getOrDefault("temperature", 0.8)).doubleValue();
        int maxTokens = ((Number) request.getOrDefault("max_tokens", 300)).intValue();
        double topP = ((Number) request.getOrDefault("top_p", 0.9)).doubleValue();
        String model = (String) request.getOrDefault("model", huggingFaceModel);

        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            // Build custom request to HuggingFace with tunable params
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemMessage != null && !systemMessage.isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemMessage));
            }
            messages.add(Map.of("role", "user", "content", prompt));

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            requestBody.put("top_p", topP);
            requestBody.put("stream", false);

            WebClient client = WebClient.builder()
                    .baseUrl(huggingFaceBaseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyStore.getHuggingFaceApiKey())
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                    .build();

            String response = client.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            long elapsed = System.currentTimeMillis() - start;

            // Parse response
            Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            String rawContent = "";
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                rawContent = (String) message.getOrDefault("content", "");
            }

            String cleaned = cleanContent(rawContent);

            result.put("status", "success");
            result.put("raw", rawContent);
            result.put("cleaned", cleaned);
            result.put("elapsed_ms", elapsed);
            result.put("model", model);
            result.put("parameters", Map.of(
                "temperature", temperature,
                "max_tokens", maxTokens,
                "top_p", topP
            ));
            result.put("usage", responseMap.get("usage"));

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[Playground] Generation failed: {}", e.getMessage());
            result.put("status", "error");
            result.put("error", e.getMessage());
            result.put("elapsed_ms", elapsed);
        }

        return result;
    }

    private String cleanContent(String content) {
        if (content == null) return "";
        content = content.replaceAll("(?s)<think>.*?</think>", "");
        content = content.replaceAll("<[^>]+>", "");
        content = content.trim();
        String[] words = content.split("\\s+");
        if (words.length > 55) {
            content = String.join(" ", java.util.Arrays.copyOfRange(words, 0, 50)) + "...";
        }
        return content;
    }
}
