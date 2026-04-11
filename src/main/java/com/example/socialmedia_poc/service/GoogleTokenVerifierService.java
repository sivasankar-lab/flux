package com.example.socialmedia_poc.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;

@Service
public class GoogleTokenVerifierService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifierService.class);

    @Value("${google.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        if (googleClientId != null && !googleClientId.isEmpty()) {
            verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            log.info("Google ID Token Verifier initialized for client: {}...{}",
                    googleClientId.substring(0, Math.min(8, googleClientId.length())),
                    googleClientId.substring(Math.max(0, googleClientId.length() - 4)));
        } else {
            log.warn("Google Client ID not configured. Google Sign-In will be disabled.");
        }
    }

    /**
     * Verifies a Google ID token and returns user info.
     * Returns null if verification fails.
     */
    public GoogleUserInfo verifyIdToken(String idTokenString) {
        if (verifier == null) {
            throw new IllegalStateException("Google Sign-In is not configured");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token verification failed — invalid token");
                return null;
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Require verified email
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                log.warn("Google account email not verified for: {}", payload.getEmail());
                return null;
            }

            return new GoogleUserInfo(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")
            );
        } catch (Exception e) {
            log.error("Google ID token verification error: {}", e.getMessage());
            return null;
        }
    }

    public boolean isConfigured() {
        return verifier != null;
    }

    /**
     * Simple DTO for verified Google user info.
     */
    public static class GoogleUserInfo {
        private final String googleId;
        private final String email;
        private final String name;
        private final String pictureUrl;

        public GoogleUserInfo(String googleId, String email, String name, String pictureUrl) {
            this.googleId = googleId;
            this.email = email;
            this.name = name;
            this.pictureUrl = pictureUrl;
        }

        public String getGoogleId() { return googleId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getPictureUrl() { return pictureUrl; }
    }
}
