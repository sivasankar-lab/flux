package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.User;
import com.example.socialmedia_poc.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Register new user
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String email = request.get("email");
            String displayName = request.get("display_name");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Username is required"
                ));
            }

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Email is required"
                ));
            }

            User user = userService.createUser(username, email, displayName != null ? displayName : username);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "User registered successfully",
                "user", user
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to register user: " + e.getMessage()
            ));
        }
    }

    // Login user
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Username is required"
                ));
            }

            User user = userService.loginUser(username);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Login successful",
                "user", user
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to login: " + e.getMessage()
            ));
        }
    }

    // Validate session
    @GetMapping("/session/{sessionToken}")
    public ResponseEntity<?> validateSession(@PathVariable String sessionToken) {
        try {
            User user = userService.validateSession(sessionToken);
            
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Invalid or expired session"
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "user", user
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to validate session: " + e.getMessage()
            ));
        }
    }

    // Logout user
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        try {
            String sessionToken = request.get("session_token");

            if (sessionToken == null || sessionToken.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Session token is required"
                ));
            }

            userService.logoutUser(sessionToken);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Logout successful"
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to logout: " + e.getMessage()
            ));
        }
    }

    // Get user profile
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId) {
        try {
            User user = userService.getUserById(userId);
            
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "status", "error",
                    "message", "User not found"
                ));
            }
            
            // Don't send session token in profile
            user.setSessionToken(null);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "user", user
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to fetch user profile: " + e.getMessage()
            ));
        }
    }

    // Get all users (admin function)
    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            
            // Remove session tokens from response
            users.forEach(u -> u.setSessionToken(null));
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", users.size(),
                "users", users
            ));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to fetch users: " + e.getMessage()
            ));
        }
    }
}
