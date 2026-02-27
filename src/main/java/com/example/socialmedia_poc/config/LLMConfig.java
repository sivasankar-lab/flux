package com.example.socialmedia_poc.config;

import com.example.socialmedia_poc.service.GrokCloudService;
import com.example.socialmedia_poc.service.HuggingFaceService;
import com.example.socialmedia_poc.service.LLMService;
import com.example.socialmedia_poc.service.OllamaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Routes LLM calls to the correct provider based on llm.provider property.
 *   huggingface → HuggingFaceService (default)
 *   grok        → GrokCloudService
 *   ollama      → OllamaService
 */
@Configuration
public class LLMConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    @Bean
    @Primary
    public LLMService activeLLMService(
            @Value("${llm.provider:huggingface}") String provider,
            GrokCloudService grokCloudService,
            OllamaService ollamaService,
            HuggingFaceService huggingFaceService) {

        LLMService selected;
        if ("ollama".equalsIgnoreCase(provider)) {
            selected = ollamaService;
        } else if ("grok".equalsIgnoreCase(provider)) {
            selected = grokCloudService;
        } else {
            selected = huggingFaceService;
        }
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  Active LLM: {}", selected.getProviderName());
        log.info("╚══════════════════════════════════════════════╝");
        return selected;
    }
}
