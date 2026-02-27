package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class User {
    @JsonProperty("user_id")
    private String userId;
    
    private String username;
    
    private String email;
    
    @JsonProperty("display_name")
    private String displayName;
    
    @JsonProperty("created_at")
    private Instant createdAt;
    
    @JsonProperty("last_login")
    private Instant lastLogin;
    
    @JsonProperty("session_token")
    private String sessionToken;

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

    private String generateUserId() {
        return "user_" + System.currentTimeMillis() + "_" + 
               Long.toHexString(Double.doubleToLongBits(Math.random()));
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
}
