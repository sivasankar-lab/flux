package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.config.ApiKeyStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

/**
 * Hugging Face Inference API (OpenAI-compatible chat completions).
 * Uses https://router.huggingface.co/v1/chat/completions
 */
@Service
public class HuggingFaceService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceService.class);

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiKeyStore apiKeyStore;

    public HuggingFaceService(WebClient.Builder webClientBuilder,
                              ApiKeyStore apiKeyStore,
                              @Value("${huggingface.model:meta-llama/Llama-3.3-70B-Instruct}") String model,
                              @Value("${huggingface.baseurl:https://router.huggingface.co}") String baseUrl) {
        this.model = model;
        this.apiKeyStore = apiKeyStore;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String generateContent(String prompt) throws Exception {
        return generateContent(null, prompt);
    }

    @Override
    public String generateContent(String systemMessage, String prompt) throws Exception {
        return generateContent(systemMessage, prompt, 300);
    }

    @Override
    public String generateContent(String systemMessage, String prompt, int maxTokens) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();

        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemMessage));
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.8);
        requestBody.put("stream", false);

        log.info("[HuggingFace] Calling model: {}", model);

        String response;
        try {
            response = webClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyStore.getHuggingFaceApiKey())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(90)) // HF free tier can be slow
                    .block();
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("[HuggingFace] 401 Unauthorized — API key is invalid or expired. Update it in the admin panel.");
            throw new RuntimeException("HuggingFace API key is invalid or expired (401 Unauthorized)");
        } catch (WebClientResponseException.Forbidden e) {
            log.error("[HuggingFace] 403 Forbidden — API key lacks required permissions.");
            throw new RuntimeException("HuggingFace API key lacks permissions (403 Forbidden)");
        } catch (WebClientResponseException e) {
            log.error("[HuggingFace] HTTP {} from API: {}", e.getStatusCode().value(), e.getMessage());
            throw e;
        }

        // Parse OpenAI-compatible response
        Map<String, Object> responseMap = mapper.readValue(response, new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            log.warn("[HuggingFace] No choices in response");
            return "";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");

        log.debug("[HuggingFace] Generated {} chars", content != null ? content.length() : 0);
        return content != null ? content : "";
    }

    @Override
    public String getProviderName() {
        return "HuggingFace - " + model;
    }
}
