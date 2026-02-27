package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.service.OllamaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/flux")
public class FluxController {

    private final OllamaService ollamaService;
    private final String model;

    public FluxController(OllamaService ollamaService, @Value("${ollama.model}") String model) {
        this.ollamaService = ollamaService;
        this.model = model;
    }

    @GetMapping("/feed")
    public Mono<String> getFeed() {
        String prompt = "You are Flux, the world’s first AI-native, generative social media platform. " +
                "Unlike traditional platforms that serve a library of existing human content, you generate the content in real-time specifically for the person viewing it. " +
                "Your feed is an Infinite Narrative Stream. It is not a static list of posts; it is a living story that evolves based on user behavior. " +
                "Generate the next text card for the user. Remember, every card is unique. The AI writes the next card based on the previous one. " +
                "Use Dwell-Time as a primary signal. If you linger, the AI deepens the story. If you skip, the AI pivots the topic. " +
                "Use Branching Choices to give the user agency over the narrative, turning passive scrolling into an active experience. " +
                "You aim to solve the doomscrolling problem by replacing empty calories (random content) with Personalized Meaning. " +
                "Use Variable Rewards: The AI alternates between high-intensity drama and low-intensity utility. " +
                "Every card should end with a cliffhanger, forcing the brain to want to close the loop by seeing the next card. " +
                "Now, generate the first card of the feed.";

        return ollamaService.generate(model, prompt);
    }
}

