package com.example.socialmedia_poc.model;

import com.example.socialmedia_poc.config.JpaConverters;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(name = "user_id")
    @JsonProperty("user_id")
    private String userId;

    @Column(unique = true, nullable = false)
    private String username;

    private String email;

    @Column(name = "display_name")
    @JsonProperty("display_name")
    private String displayName;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    @Column(name = "last_login")
    @JsonProperty("last_login")
    private Instant lastLogin;

    @Column(name = "session_token", unique = true)
    @JsonIgnore
    private String sessionToken;

    @Column(name = "session_created_at")
    @JsonIgnore
    private Instant sessionCreatedAt;

    @Column(name = "password_hash")
    @JsonIgnore
    private String passwordHash;

    @Column(name = "google_id", unique = true)
    @JsonIgnore
    private String googleId;

    @Column(name = "auth_provider")
    @JsonProperty("auth_provider")
    private String authProvider = "LOCAL";

    @Column(name = "profile_picture_url")
    @JsonProperty("profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "role")
    private String role = "USER";

    @Column(name = "interests", columnDefinition = "TEXT")
    @Convert(converter = JpaConverters.StringListConverter.class)
    private List<String> interests = new ArrayList<>();

    @Column(name = "onboarded")
    private boolean onboarded = false;

    public User() {
        this.createdAt = Instant.now();
        this.lastLogin = Instant.now();
    }

    public User(String username, String email, String displayName) {
        this.userId = generateUserId();
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
        this.lastLogin = Instant.now();
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateUserId() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return "user_" + hex;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public Instant getSessionCreatedAt() {
        return sessionCreatedAt;
    }

    public void setSessionCreatedAt(Instant sessionCreatedAt) {
        this.sessionCreatedAt = sessionCreatedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public boolean isOnboarded() {
        return onboarded;
    }

    public void setOnboarded(boolean onboarded) {
        this.onboarded = onboarded;
    }
}
