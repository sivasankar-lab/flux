package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Meta;
import com.example.socialmedia_poc.model.MetaConfig;
import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SeedGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SeedGenerationService.class);

    private static final String BASE_SYSTEM_MESSAGE =
            "You are Flux, a generative social media platform that creates engaging short-form content. " +
            "Write like a sharp, knowledgeable human — NOT like an AI assistant. " +
            "NEVER include <think> tags, reasoning, or explanations. " +
            "FORMAT: First line must be a catchy headline (like a news headline, 5-12 words, no quotes). " +
            "Then a blank line, then the post body. Example:\n" +
            "The Ancient City That Vanished Overnight\n\nBody text here...\n\n" +
            "No AI slop. Be specific, use real facts, names, and numbers.";

    private static String buildSystemMessage(MetaConfig config) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_MESSAGE);
        if (config.getLanguage() != null && !config.getLanguage().isEmpty()) {
            sb.append(" Write this post in: ").append(String.join(" or ", config.getLanguage())).append(".");
        }
        return sb.toString();
    }

    private static final String[] ANGLE_VARIATIONS = {
            "Focus on a surprising historical or scientific fact most people don't know.",
            "Start with a counter-intuitive observation that challenges a common belief.",
            "Tell a micro-story — a specific moment that reveals a larger truth.",
            "Use a vivid analogy or metaphor to explain something familiar in a new way.",
            "Present a sharp contrast or paradox that makes the reader rethink.",
            "Zoom in on one specific detail that reveals the bigger picture.",
            "Connect two seemingly unrelated ideas in an unexpected way.",
            "Ask a provocative question and immediately offer a surprising answer.",
            "Describe a real scenario or experiment and its unexpected outcome.",
            "Drop a specific number or statistic that reframes how we see this topic."
    };

    private final LLMService llmService;
    private final PoolPostRepository poolPostRepository;

    public SeedGenerationService(@Qualifier("activeLLMService") LLMService llmService,
                                 PoolPostRepository poolPostRepository) {
        this.llmService = llmService;
        this.poolPostRepository = poolPostRepository;
    }

    @Transactional
    public void generateSeeds() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = getClass().getResourceAsStream("/meta-configs.json");
        List<Meta> metas = mapper.readValue(inputStream, new TypeReference<List<Meta>>(){});

        for (Meta meta : metas) {
            for (int i = 0; i < 10; i++) {
                String prompt = constructPrompt(meta) + "\n\nANGLE: " + ANGLE_VARIATIONS[i % ANGLE_VARIATIONS.length];
                try {
                    String systemMsg = buildSystemMessage(meta.getMetaConfig());
                    String seedContent = llmService.generateContent(systemMsg, prompt);

                    // Parse headline + body
                    String caption = null;
                    String body = seedContent;
                    if (seedContent != null && seedContent.contains("\n")) {
                        String[] parts = seedContent.split("\n", 2);
                        String firstLine = parts[0].trim();
                        String rest = parts.length > 1 ? parts[1].trim() : "";
                        if (firstLine.length() > 0 && firstLine.length() < 80 && rest.length() > 0) {
                            caption = firstLine;
                            body = rest;
                        }
                    }
                    body = cleanContent(body);

                    if (body.isEmpty()) continue;

                    SeedWithMeta seed = new SeedWithMeta();
                    seed.setSeedId(UUID.randomUUID().toString());
                    seed.setContent(body);
                    seed.setCaption(caption);
                    seed.setCategory(meta.getCategory());
                    seed.setMetaConfig(meta.getMetaConfig());

                    PoolPost poolPost = PoolPost.fromSeedFile(seed);
                    poolPostRepository.save(poolPost);

                    log.info("[SeedGen] Generated seed {} for {}", i, meta.getCategory());
                } catch (Exception e) {
                    log.error("[SeedGen] Failed seed {} for {}: {}", i, meta.getCategory(), e.getMessage());
                }
            }
        }
    }

    private String constructPrompt(Meta meta) {
        MetaConfig config = meta.getMetaConfig();
        StringBuilder prompt = new StringBuilder();

        // Core instruction with word count from meta-config
        int minWords = 40, maxWords = 120;
        if (config.getWordCountRange() != null && config.getWordCountRange().size() == 2) {
            minWords = config.getWordCountRange().get(0);
            maxWords = config.getWordCountRange().get(1);
        }

        prompt.append("Write ONE short social media post about '").append(meta.getCategory()).append("'. ");
        prompt.append("Length: ").append(minWords).append("-").append(maxWords).append(" words. ");
        prompt.append("Output ONLY the post text — no titles, labels, hashtags, or quotes around it.\n\n");

        // Use tone_guide from meta-config (this is the rich instruction)
        if (config.getToneGuide() != null && !config.getToneGuide().isEmpty()) {
            prompt.append("TONE: ").append(config.getToneGuide()).append("\n\n");
        }

        // Intensity + pacing
        prompt.append("Intensity: ").append(config.getIntensityRange().get(0))
              .append("-").append(config.getIntensityRange().get(1)).append("/10. ");
        prompt.append("Pacing: ").append(config.getPacing()).append(". ");

        // Triggers
        if (config.getTriggers() != null && !config.getTriggers().isEmpty()) {
            prompt.append("Evoke: ").append(String.join(", ", config.getTriggers())).append(". ");
        }

        // Vocabulary
        if (config.getVocabularyWeight() != null && !config.getVocabularyWeight().isEmpty()) {
            List<String> topWords = config.getVocabularyWeight().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(4)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList());
            prompt.append("Weave in words like: ").append(String.join(", ", topWords)).append(". ");
        }

        // Quality guardrails
        prompt.append("\nBe SPECIFIC — use a real fact, name, number, or vivid detail. ");
        prompt.append("No generic filler. No clickbait. One sharp thought that makes the reader stop and think.");

        return prompt.toString();
    }

    private String cleanContent(String content) {
        if (content == null) return "";
        content = content.replaceAll("(?s)<think>.*?</think>", "");
        content = content.replaceAll("<[^>]+>", "");
        content = content.trim();
        String[] words = content.split("\\s+");
        if (words.length > 130) {
            content = String.join(" ", java.util.Arrays.copyOfRange(words, 0, 120)) + "...";
        }
        return content;
    }
}
