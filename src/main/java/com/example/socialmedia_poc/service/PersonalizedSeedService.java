package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.Meta;
import com.example.socialmedia_poc.model.MetaConfig;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PersonalizedSeedService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizedSeedService.class);

    private static final String BASE_SYSTEM_MESSAGE =
            "You are Flux, a generative social media platform that creates short-form content. " +
            "Write like a sharp, knowledgeable human — NOT like an AI assistant. " +
            "NEVER include <think> tags, reasoning, or explanations. " +
            "CRITICAL FORMAT — you MUST follow this exactly:\n" +
            "Line 1: A punchy, magnetic headline (5-10 words). Write it like a top journalist — " +
            "use power words, create curiosity gaps, be specific. NO quotes around it. " +
            "Examples of great headlines: 'NASA Found Something Strange on Europa', " +
            "'Why Your Brain Lies About Time', 'The $2 Billion Typo That Changed History'.\n" +
            "Line 2: MUST be blank.\n" +
            "Line 3+: The post body.\n\n" +
            "ABSOLUTE BANS — never use these words or phrases: " +
            "\"delve\", \"tapestry\", \"leverage\", \"paradigm shift\", \"it's worth noting\", " +
            "\"in a world where\", \"at the end of the day\", \"game-changer\", \"deep dive\", " +
            "\"unpack\", \"robust\", \"nuanced\", \"pivotal\", \"synergy\", \"holistic\", " +
            "\"groundbreaking\", \"transformative\", \"revolutionize\", \"landscape\", " +
            "\"navigate\", \"realm\", \"foster\", \"embark\", \"shed light on\", " +
            "\"it goes without saying\", \"needless to say\", \"a testament to\". " +
            "Instead: be direct, use concrete language, name specific things. " +
            "Keep posts under 120 words (not counting the headline).";

    private static String buildSystemMessage(MetaConfig config) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_MESSAGE);
        if (config.getLanguage() != null && !config.getLanguage().isEmpty()) {
            sb.append(" Write this post in: ").append(String.join(" or ", config.getLanguage())).append(".");
        }
        return sb.toString();
    }

    private final InteractionService interactionService;
    private final LLMService llmService;

    public PersonalizedSeedService(InteractionService interactionService,
                                   @Qualifier("activeLLMService") LLMService llmService) {
        this.interactionService = interactionService;
        this.llmService = llmService;
    }

    public List<SeedWithMeta> generatePersonalizedSeeds(String userId) throws IOException {
        return generatePersonalizedSeeds(userId, 5);
    }

    public List<SeedWithMeta> generatePersonalizedSeeds(String userId, int count) throws IOException {
        InteractionService.UserPreference preference = interactionService.analyzeUserPreference(userId);
        
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = TypeReference.class.getResourceAsStream("/meta-configs.json");
        List<Meta> allMetas = mapper.readValue(inputStream, new TypeReference<List<Meta>>(){});
        
        List<Meta> preferredMetas = allMetas.stream()
            .filter(meta -> preference.getPreferredCategories().isEmpty() || 
                          preference.getPreferredCategories().contains(meta.getCategory()))
            .collect(Collectors.toList());
        
        if (preferredMetas.isEmpty()) {
            preferredMetas = allMetas;
        }
        
        List<SeedWithMeta> personalizedSeeds = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Meta meta = preferredMetas.get(i % preferredMetas.size());
            String prompt = constructPersonalizedPrompt(meta, preference);
            
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
                    // Headline is typically short (under 80 chars) and the body follows
                    if (firstLine.length() > 0 && firstLine.length() < 80 && rest.length() > 0) {
                        caption = firstLine;
                        body = rest;
                    }
                }
                body = cleanContent(body);
                
                if (body.isEmpty()) continue;
                
                SeedWithMeta seedWithMeta = new SeedWithMeta();
                seedWithMeta.setSeedId(UUID.randomUUID().toString());
                seedWithMeta.setContent(body);
                seedWithMeta.setCaption(caption);
                seedWithMeta.setCategory(meta.getCategory());
                seedWithMeta.setMetaConfig(meta.getMetaConfig());
                
                SeedWithMeta.GenerationContext context = new SeedWithMeta.GenerationContext();
                context.setBasedOnInteraction("user_preference_analysis");
                context.setUserPreferenceSignal(preference.getEngagementLevel());
                context.setNarrativeDepth(preference.getPreferredDepth());
                seedWithMeta.setGenerationContext(context);
                
                personalizedSeeds.add(seedWithMeta);
                log.debug("[PersonalizedSeed] Generated for '{}' via {}", meta.getCategory(), llmService.getProviderName());
            } catch (Exception e) {
                log.error("[PersonalizedSeed] Failed for {}: {}", meta.getCategory(), e.getMessage());
            }
        }
        
        return personalizedSeeds;
    }

    private static final String[] ANGLE_VARIATIONS = {
            "Focus on a surprising fact most people don't know.",
            "Start with a counter-intuitive observation.",
            "Tell a micro-story — one specific moment that reveals a larger truth.",
            "Use a vivid analogy to explain something in a new way.",
            "Present a sharp contrast or paradox.",
            "Zoom in on one specific detail that reveals the bigger picture.",
            "Connect two unrelated ideas in an unexpected way.",
            "Ask a provocative question and give a surprising answer.",
            "Describe a real experiment or scenario with an unexpected outcome.",
            "Drop a specific number that reframes how we see this topic."
    };

    private int angleIndex = 0;

    private String constructPersonalizedPrompt(Meta meta, InteractionService.UserPreference preference) {
        MetaConfig config = meta.getMetaConfig();
        StringBuilder prompt = new StringBuilder();

        // Word count from meta-config
        int minWords = 40, maxWords = 120;
        if (config.getWordCountRange() != null && config.getWordCountRange().size() == 2) {
            minWords = config.getWordCountRange().get(0);
            maxWords = config.getWordCountRange().get(1);
        }

        // Adjust for preference
        if ("deep".equals(preference.getPreferredDepth())) {
            maxWords = Math.min(maxWords + 50, 250);
        } else if ("shallow".equals(preference.getPreferredDepth())) {
            maxWords = Math.min(maxWords, 80);
        }

        prompt.append("Write ONE short social media post about '").append(meta.getCategory()).append("'. ");
        prompt.append("Length: ").append(minWords).append("-").append(maxWords).append(" words. ");
        prompt.append("Output ONLY the post text — no titles, labels, hashtags, or quotes.\n\n");

        // Use tone_guide (rich instruction from meta-config)
        if (config.getToneGuide() != null && !config.getToneGuide().isEmpty()) {
            prompt.append("TONE: ").append(config.getToneGuide()).append("\n\n");
        }

        // Intensity (adjusted by engagement)
        if ("high".equals(preference.getEngagementLevel())) {
            int maxIntensity = config.getIntensityRange().get(1);
            prompt.append("Intensity: ").append(maxIntensity).append("/10 (high engagement user). ");
        } else {
            int avgIntensity = (config.getIntensityRange().get(0) + config.getIntensityRange().get(1)) / 2;
            prompt.append("Intensity: ").append(avgIntensity).append("/10. ");
        }

        prompt.append("Pacing: ").append(preference.getPreferredPacing()).append(". ");

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
                    .collect(Collectors.toList());
            prompt.append("Weave in: ").append(String.join(", ", topWords)).append(". ");
        }

        // Quality + variety
        prompt.append("\nBe SPECIFIC — use a real fact, name, number, or vivid detail. No generic filler. ");
        prompt.append("Write like a human expert sharing something fascinating, NOT like an AI generating content. ");
        prompt.append("No clichés, no motivational poster language, no buzzwords. ");
        prompt.append("ANGLE: ").append(ANGLE_VARIATIONS[angleIndex % ANGLE_VARIATIONS.length]);
        angleIndex++;

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

    /**
     * Generate posts for a specific category (used by trigger system).
     */
    public List<SeedWithMeta> generateForCategory(String userId, String category, int count) throws IOException {
        InteractionService.UserPreference preference = interactionService.analyzeUserPreference(userId);

        ObjectMapper mapperLocal = new ObjectMapper();
        InputStream is = TypeReference.class.getResourceAsStream("/meta-configs.json");
        List<Meta> allMetas = mapperLocal.readValue(is, new TypeReference<List<Meta>>(){});

        Meta targetMeta = allMetas.stream()
                .filter(m -> category.equalsIgnoreCase(m.getCategory()))
                .findFirst()
                .orElse(allMetas.get(0));

        List<SeedWithMeta> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prompt = constructPersonalizedPrompt(targetMeta, preference);
            try {
                String systemMsg = buildSystemMessage(targetMeta.getMetaConfig());
                String seedContent = cleanContent(llmService.generateContent(systemMsg, prompt));
                // Parse headline + body for category generation too
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
                if (body == null || body.isEmpty()) continue;

                SeedWithMeta seed = new SeedWithMeta();
                seed.setSeedId(UUID.randomUUID().toString());
                seed.setContent(body);
                seed.setCaption(caption);
                seed.setCategory(targetMeta.getCategory());
                seed.setMetaConfig(targetMeta.getMetaConfig());

                SeedWithMeta.GenerationContext ctx = new SeedWithMeta.GenerationContext();
                ctx.setBasedOnInteraction("trigger_generation");
                ctx.setUserPreferenceSignal(preference.getEngagementLevel());
                ctx.setNarrativeDepth(preference.getPreferredDepth());
                seed.setGenerationContext(ctx);

                results.add(seed);
            } catch (Exception e) {
                log.error("[PersonalizedSeed] trigger gen failed for {}: {}", category, e.getMessage());
            }
        }
        return results;
    }
}
