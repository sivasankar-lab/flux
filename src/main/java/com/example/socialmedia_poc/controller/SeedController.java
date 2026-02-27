package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.Meta;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.example.socialmedia_poc.service.SeedGenerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/v1/seeds")
public class SeedController {

    private final SeedGenerationService seedGenerationService;
    private final ObjectMapper mapper;

    public SeedController(SeedGenerationService seedGenerationService) {
        this.seedGenerationService = seedGenerationService;
        this.mapper = new ObjectMapper();
    }

    @PostMapping("/generate")
    public String generateSeeds() {
        try {
            seedGenerationService.generateSeeds();
            return "Seed generation completed.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error during seed generation: " + e.getMessage();
        }
    }

    @GetMapping("/random")
    public List<String> getRandomSeeds() throws IOException {
        Path seedsDir = Paths.get("src/main/resources/seeds");
        if (!Files.exists(seedsDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(seedsDir)) {
            List<Path> seedFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".st"))
                    .collect(Collectors.toList());

            Collections.shuffle(seedFiles);

            return seedFiles.stream()
                    .limit(10)
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return "";
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    @GetMapping("/with-meta")
    public List<SeedWithMeta> getSeedsWithMeta(@RequestParam(defaultValue = "10") int limit) throws IOException {
        Path seedsDir = Paths.get("src/main/resources/seeds");
        if (!Files.exists(seedsDir)) {
            return Collections.emptyList();
        }

        // Load meta configs
        InputStream inputStream = TypeReference.class.getResourceAsStream("/meta-configs.json");
        List<Meta> metas = mapper.readValue(inputStream, new TypeReference<List<Meta>>(){});
        Map<String, Meta> metaMap = metas.stream()
            .collect(Collectors.toMap(Meta::getCategory, m -> m));

        try (Stream<Path> paths = Files.walk(seedsDir)) {
            List<Path> seedFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".st"))
                    .collect(Collectors.toList());

            Collections.shuffle(seedFiles);

            return seedFiles.stream()
                    .limit(limit)
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();
                            
                            // Extract category from filename
                            String category = extractCategoryFromFileName(fileName);
                            Meta meta = metaMap.get(category);
                            
                            SeedWithMeta seedWithMeta = new SeedWithMeta();
                            seedWithMeta.setSeedId(UUID.randomUUID().toString());
                            seedWithMeta.setContent(content);
                            seedWithMeta.setCategory(category);
                            
                            if (meta != null) {
                                seedWithMeta.setMetaConfig(meta.getMetaConfig());
                            }
                            
                            return seedWithMeta;
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    @GetMapping("/by-category/{category}")
    public List<SeedWithMeta> getSeedsByCategory(@PathVariable String category) throws IOException {
        Path seedsDir = Paths.get("src/main/resources/seeds");
        if (!Files.exists(seedsDir)) {
            return Collections.emptyList();
        }

        // Load meta config for this category
        InputStream inputStream = TypeReference.class.getResourceAsStream("/meta-configs.json");
        List<Meta> metas = mapper.readValue(inputStream, new TypeReference<List<Meta>>(){});
        Meta categoryMeta = metas.stream()
            .filter(m -> m.getCategory().equals(category))
            .findFirst()
            .orElse(null);

        try (Stream<Path> paths = Files.walk(seedsDir)) {
            List<Path> seedFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".st"))
                    .filter(path -> extractCategoryFromFileName(path.getFileName().toString()).equals(category))
                    .collect(Collectors.toList());

            Collections.shuffle(seedFiles);

            return seedFiles.stream()
                    .limit(5)
                    .map(path -> {
                        try {
                            String content = Files.readString(path);
                            
                            SeedWithMeta seedWithMeta = new SeedWithMeta();
                            seedWithMeta.setSeedId(UUID.randomUUID().toString());
                            seedWithMeta.setContent(content);
                            seedWithMeta.setCategory(category);
                            
                            if (categoryMeta != null) {
                                seedWithMeta.setMetaConfig(categoryMeta.getMetaConfig());
                            }
                            
                            return seedWithMeta;
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private String extractCategoryFromFileName(String fileName) {
        // Remove the .st extension and the numeric suffix
        String nameWithoutExt = fileName.replace(".st", "");
        int lastDashIndex = nameWithoutExt.lastIndexOf('-');
        if (lastDashIndex > 0) {
            String category = nameWithoutExt.substring(0, lastDashIndex);
            // Replace underscores with spaces and forward slashes
            return category.replace("___", " / ").replace("__", " & ").replace("_", " ");
        }
        return nameWithoutExt;
    }
}
