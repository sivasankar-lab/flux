package com.example.socialmedia_poc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class OllamaService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private final WebClient webClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaService(WebClient.Builder webClientBuilder,
                         @Value("${ollama.baseurl}") String ollamaBaseUrl,
                         @Value("${ollama.model}") String model) {
        this.webClient = webClientBuilder.baseUrl(ollamaBaseUrl).build();
        this.model = model;
    }

    public Mono<String> generate(String model, String prompt) {
        return this.webClient.post()
                .uri("/api/generate")
                .bodyValue(Map.of("model", model, "prompt", prompt, "stream", false))
                .retrieve()
                .bodyToMono(String.class);
    }

    @Override
    public String generateContent(String prompt) throws Exception {
        return generateContent(null, prompt);
    }

    @Override
    public String generateContent(String systemMessage, String prompt) throws Exception {
        // Ollama /api/generate doesn't support system messages natively,
        // so prepend it to the prompt
        String fullPrompt = prompt;
        if (systemMessage != null && !systemMessage.isEmpty()) {
            fullPrompt = systemMessage + "\n\n" + prompt;
        }

        String jsonResponse = generate(model, fullPrompt).block();
        Map<String, Object> responseMap = mapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
        String content = (String) responseMap.get("response");
        log.debug("[Ollama] Generated {} chars", content != null ? content.length() : 0);
        return content != null ? content : "";
    }

    @Override
    public String getProviderName() {
        return "Ollama (local) - " + model;
    }
}
