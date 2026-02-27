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
            "You are Flux, a generative social media platform that creates highly engaging, " +
            "culturally relevant short-form content. " +
            "NEVER include <think> tags, reasoning, or explanations. Output ONLY the post content. " +
            "Keep posts under 50 words.";

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
                seedContent = cleanContent(seedContent);
                
                if (seedContent.isEmpty()) continue;
                
                SeedWithMeta seedWithMeta = new SeedWithMeta();
                seedWithMeta.setSeedId(UUID.randomUUID().toString());
                seedWithMeta.setContent(seedContent);
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

    private String constructPersonalizedPrompt(Meta meta, InteractionService.UserPreference preference) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Generate a personalized post for '").append(meta.getCategory()).append("'. ");
        prompt.append("Respond with EXACTLY 50 words or less. Only the post content. ");
        
        MetaConfig config = meta.getMetaConfig();
        
        if ("deep".equals(preference.getPreferredDepth())) {
            prompt.append("Make the content more detailed and thought-provoking. ");
        } else if ("shallow".equals(preference.getPreferredDepth())) {
            prompt.append("Keep the content lightweight and easy to digest. ");
        }
        
        prompt.append("The post should have ");
        
        if ("high".equals(preference.getEngagementLevel())) {
            int maxIntensity = config.getIntensityRange().get(1);
            prompt.append("an intensity around ").append(maxIntensity).append(", ");
        } else {
            int avgIntensity = (config.getIntensityRange().get(0) + config.getIntensityRange().get(1)) / 2;
            prompt.append("an intensity around ").append(avgIntensity).append(", ");
        }
        
        prompt.append("a ").append(preference.getPreferredPacing()).append(" pacing, ");
        prompt.append("and trigger feelings of ").append(String.join(", ", config.getTriggers())).append(". ");
        
        if (config.getVocabularyWeight() != null && !config.getVocabularyWeight().isEmpty()) {
            prompt.append("Use words like ").append(String.join(", ", config.getVocabularyWeight().keySet())).append(".");
        }
        
        return prompt.toString();
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
                if (seedContent.isEmpty()) continue;

                SeedWithMeta seed = new SeedWithMeta();
                seed.setSeedId(UUID.randomUUID().toString());
                seed.setContent(seedContent);
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
