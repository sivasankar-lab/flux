package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final ObjectMapper objectMapper;
    private final Path usersFilePath;
    private final Path userDataDir;

    public UserService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        this.usersFilePath = Paths.get("src/main/resources/users.json");
        this.userDataDir = Paths.get("src/main/resources/user-data");
        
        try {
            Files.createDirectories(userDataDir);
            if (!Files.exists(usersFilePath)) {
                objectMapper.writeValue(usersFilePath.toFile(), new ArrayList<User>());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create new user
    public User createUser(String username, String email, String displayName) throws IOException {
        List<User> users = getAllUsers();
        
        // Check if username or email already exists
        boolean exists = users.stream()
            .anyMatch(u -> u.getUsername().equalsIgnoreCase(username) || 
                          u.getEmail().equalsIgnoreCase(email));
        
        if (exists) {
            throw new IllegalArgumentException("Username or email already exists");
        }
        
        User newUser = new User(username, email, displayName);
        newUser.setSessionToken(generateSessionToken());
        
        users.add(newUser);
        saveUsers(users);
        
        // Create user-specific directories
        createUserDirectories(newUser.getUserId());
        
        return newUser;
    }

    // Login user
    public User loginUser(String username) throws IOException {
        List<User> users = getAllUsers();
        
        Optional<User> userOpt = users.stream()
            .filter(u -> u.getUsername().equalsIgnoreCase(username))
            .findFirst();
        
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        user.setLastLogin(Instant.now());
        user.setSessionToken(generateSessionToken());
        
        saveUsers(users);
        
        return user;
    }

    // Validate session token
    public User validateSession(String sessionToken) throws IOException {
        List<User> users = getAllUsers();
        
        return users.stream()
            .filter(u -> sessionToken.equals(u.getSessionToken()))
            .findFirst()
            .orElse(null);
    }

    // Get user by ID
    public User getUserById(String userId) throws IOException {
        List<User> users = getAllUsers();
        
        return users.stream()
            .filter(u -> userId.equals(u.getUserId()))
            .findFirst()
            .orElse(null);
    }

    // Get user by username
    public User getUserByUsername(String username) throws IOException {
        List<User> users = getAllUsers();
        
        return users.stream()
            .filter(u -> username.equalsIgnoreCase(u.getUsername()))
            .findFirst()
            .orElse(null);
    }

    // Logout user
    public void logoutUser(String sessionToken) throws IOException {
        List<User> users = getAllUsers();
        
        users.stream()
            .filter(u -> sessionToken.equals(u.getSessionToken()))
            .findFirst()
            .ifPresent(u -> u.setSessionToken(null));
        
        saveUsers(users);
    }

    // Get all users (admin function)
    public List<User> getAllUsers() throws IOException {
        File file = usersFilePath.toFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(file, new TypeReference<List<User>>() {});
    }

    // Save users to file
    private void saveUsers(List<User> users) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(usersFilePath.toFile(), users);
    }

    // Generate session token
    private String generateSessionToken() {
        return "session_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().replace("-", "");
    }

    // Create user-specific directories
    private void createUserDirectories(String userId) throws IOException {
        Path userDir = userDataDir.resolve(userId);
        Files.createDirectories(userDir);
        
        // Initialize empty JSON files for user
        Path interactionsFile = userDir.resolve("interactions.json");
        Path nextSeedsFile = userDir.resolve("next-seeds.json");
        Path wallFile = userDir.resolve("wall.json");
        
        if (!Files.exists(interactionsFile)) {
            objectMapper.writeValue(interactionsFile.toFile(), new ArrayList<>());
        }
        
        if (!Files.exists(nextSeedsFile)) {
            objectMapper.writeValue(nextSeedsFile.toFile(), new ArrayList<>());
        }
        
        if (!Files.exists(wallFile)) {
            objectMapper.writeValue(wallFile.toFile(), new ArrayList<>());
        }
    }

    // Get user data directory path
    public Path getUserDataDirectory(String userId) {
        return userDataDir.resolve(userId);
    }

    // Get user interactions file path
    public Path getUserInteractionsFile(String userId) {
        return getUserDataDirectory(userId).resolve("interactions.json");
    }

    // Get user next-seeds file path
    public Path getUserNextSeedsFile(String userId) {
        return getUserDataDirectory(userId).resolve("next-seeds.json");
    }

    // Get user wall file path
    public Path getUserWallFile(String userId) {
        return getUserDataDirectory(userId).resolve("wall.json");
    }
}
