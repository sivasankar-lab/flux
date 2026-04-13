package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.example.socialmedia_poc.service.SeedGenerationService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/seeds")
public class SeedController {

    private final SeedGenerationService seedGenerationService;
    private final PoolPostRepository poolPostRepository;

    public SeedController(SeedGenerationService seedGenerationService,
                          PoolPostRepository poolPostRepository) {
        this.seedGenerationService = seedGenerationService;
        this.poolPostRepository = poolPostRepository;
    }

    @PostMapping("/generate")
    public String generateSeeds() {
        try {
            seedGenerationService.generateSeeds();
            return "Seed generation completed.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error during seed generation: " + e.getMessage();
        }
    }

    @GetMapping("/random")
    public List<String> getRandomSeeds() {
        List<PoolPost> all = poolPostRepository.findAll();
        Collections.shuffle(all);
        return all.stream()
                .limit(10)
                .map(PoolPost::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @GetMapping("/with-meta")
    public List<SeedWithMeta> getSeedsWithMeta(@RequestParam(defaultValue = "10") int limit) {
        List<PoolPost> all = poolPostRepository.findAll();
        Collections.shuffle(all);
        return all.stream()
                .limit(limit)
                .map(PoolPost::toSeedWithMeta)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-category/{category}")
    public List<SeedWithMeta> getSeedsByCategory(@PathVariable String category,
                                                  @RequestParam(defaultValue = "20") int limit) {
        List<PoolPost> catPosts = poolPostRepository.findByCategory(category);
        Collections.shuffle(catPosts);
        return catPosts.stream()
                .limit(limit)
                .map(PoolPost::toSeedWithMeta)
                .collect(Collectors.toList());
    }
}
