package com.example.socialmedia_poc.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves public configuration to the frontend (e.g. Google Client ID, interest topics).
 */
@RestController
@RequestMapping("/v1/config")
public class ConfigController {

    @Value("${google.client-id:}")
    private String googleClientId;

    @GetMapping("/public")
    public ResponseEntity<?> getPublicConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("google_sign_in_enabled", googleClientId != null && !googleClientId.isEmpty());
        if (googleClientId != null && !googleClientId.isEmpty()) {
            config.put("google_client_id", googleClientId);
        }
        return ResponseEntity.ok(config);
    }

    @GetMapping("/interests")
    public ResponseEntity<?> getInterestTopics() {
        try {
            ClassPathResource resource = new ClassPathResource("interest-topics.json");
            InputStream is = resource.getInputStream();
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // Return raw JSON
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(json);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to load interest topics"
            ));
        }
    }
}
