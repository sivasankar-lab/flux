package com.example.socialmedia_poc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

    public HuggingFaceService(WebClient.Builder webClientBuilder,
                              @Value("${huggingface.api-key:}") String apiKey,
                              @Value("${huggingface.model:zai-org/GLM-5:novita}") String model,
                              @Value("${huggingface.baseurl:https://router.huggingface.co}") String baseUrl) {
        this.model = model;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Override
    public String generateContent(String prompt) throws Exception {
        return generateContent(null, prompt);
    }

    @Override
    public String generateContent(String systemMessage, String prompt) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();

        if (systemMessage != null && !systemMessage.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemMessage));
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 300);
        requestBody.put("temperature", 0.8);
        requestBody.put("stream", false);

        log.info("[HuggingFace] Calling model: {}", model);

        String response = webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(90)) // HF free tier can be slow
                .block();

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
