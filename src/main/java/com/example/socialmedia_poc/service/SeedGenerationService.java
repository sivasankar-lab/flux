package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Meta;
import com.example.socialmedia_poc.model.MetaConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class SeedGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SeedGenerationService.class);

    private static final String BASE_SYSTEM_MESSAGE =
            "You are Flux, a generative social media platform that creates highly engaging, " +
            "culturally relevant short-form content. " +
            "NEVER include <think> tags, reasoning, or explanations. Output ONLY the post content.";

    private static String buildSystemMessage(MetaConfig config) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_MESSAGE);
        if (config.getLanguage() != null && !config.getLanguage().isEmpty()) {
            sb.append(" Write this post in: ").append(String.join(" or ", config.getLanguage())).append(".");
            if (config.getLanguage().contains("Tamil")) {
                sb.append(" Use Tamil script (தமிழ்) naturally. Tanglish (Tamil written in English letters) is also acceptable.");
            }
            if (config.getLanguage().contains("Tanglish")) {
                sb.append(" Tanglish = Tamil words written in English letters, e.g. 'Vera level mass da!'");
            }
        }
        return sb.toString();
    }

    private final LLMService llmService;

    public SeedGenerationService(@Qualifier("activeLLMService") LLMService llmService) {
        this.llmService = llmService;
    }

    public void generateSeeds() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = TypeReference.class.getResourceAsStream("/meta-configs.json");
        List<Meta> metas = mapper.readValue(inputStream, new TypeReference<List<Meta>>(){});

        Path seedsDir = Paths.get("src/main/resources/seeds");
        if (!Files.exists(seedsDir)) {
            Files.createDirectories(seedsDir);
        }

        for (Meta meta : metas) {
            for (int i = 0; i < 10; i++) {
                String prompt = constructPrompt(meta);
                try {
                    String systemMsg = buildSystemMessage(meta.getMetaConfig());
                    String seedContent = llmService.generateContent(systemMsg, prompt);
                    seedContent = cleanContent(seedContent);

                    String category = meta.getCategory().replaceAll("[^a-zA-Z0-9.-]", "_");
                    String fileName = category + "-" + i + ".st";
                    Path filePath = seedsDir.resolve(fileName);
                    Files.write(filePath, seedContent.getBytes());
                    log.debug("[SeedGen] Wrote {}", fileName);
                } catch (Exception e) {
                    log.error("[SeedGen] Failed seed {} for {}: {}", i, meta.getCategory(), e.getMessage());
                }
            }
        }
    }

    private String constructPrompt(Meta meta) {
        return "Generate a seed post for the category '" + meta.getCategory() + "'. " +
                "Respond with EXACTLY 50 words or less. Only provide the final post content. " +
                "The post should have an intensity between " + meta.getMetaConfig().getIntensityRange().get(0) + " and " + meta.getMetaConfig().getIntensityRange().get(1) + ", " +
                "a " + meta.getMetaConfig().getPacing() + " pacing, " +
                "and trigger feelings of " + String.join(", ", meta.getMetaConfig().getTriggers()) + ". " +
                "Use words like " + String.join(", ", meta.getMetaConfig().getVocabularyWeight().keySet()) + ".";
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
