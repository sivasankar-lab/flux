package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.User;
import com.example.socialmedia_poc.service.GoogleTokenVerifierService;
import com.example.socialmedia_poc.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserService userService;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    public UserController(UserService userService, GoogleTokenVerifierService googleTokenVerifierService) {
        this.userService = userService;
        this.googleTokenVerifierService = googleTokenVerifierService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String email = request.get("email");
            String displayName = request.get("display_name");
            String password = request.get("password");

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

            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Password is required"
                ));
            }

            User user = userService.createUser(username, email, displayName != null ? displayName : username, password);

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
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to register user: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Username is required"
                ));
            }

            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Password is required"
                ));
            }

            User user = userService.loginUser(username, password);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Login successful",
                "user", user
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to login: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        try {
            String idToken = request.get("id_token");
            if (idToken == null || idToken.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Google ID token is required"
                ));
            }

            if (!googleTokenVerifierService.isConfigured()) {
                return ResponseEntity.status(501).body(Map.of(
                    "status", "error",
                    "message", "Google Sign-In is not configured on this server"
                ));
            }

            GoogleTokenVerifierService.GoogleUserInfo googleUser = googleTokenVerifierService.verifyIdToken(idToken);
            if (googleUser == null) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Invalid Google token"
                ));
            }

            User user = userService.loginOrCreateGoogleUser(
                    googleUser.getGoogleId(),
                    googleUser.getEmail(),
                    googleUser.getName(),
                    googleUser.getPictureUrl()
            );

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Google login successful",
                "user", user
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Google login failed: " + e.getMessage()
            ));
        }
    }

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
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to validate session: " + e.getMessage()
            ));
        }
    }

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
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to logout: " + e.getMessage()
            ));
        }
    }

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

            user.setSessionToken(null);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "user", user
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to fetch user profile: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            users.forEach(u -> u.setSessionToken(null));

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", users.size(),
                "users", users
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to fetch users: " + e.getMessage()
            ));
        }
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/interests")
    public ResponseEntity<?> saveInterests(@RequestBody Map<String, Object> request) {
        try {
            // Get authenticated user from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User)) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Not authenticated"
                ));
            }
            User authenticatedUser = (User) auth.getPrincipal();
            String userId = authenticatedUser.getUserId();

            Object interestsObj = request.get("interests");
            if (interestsObj == null || !(interestsObj instanceof List)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "interests array is required"
                ));
            }
            List<String> interests = (List<String>) interestsObj;

            // Allow skip (empty list with skip flag)
            boolean skip = Boolean.TRUE.equals(request.get("skip"));
            if (!skip && interests.size() < 3) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Please select at least 3 interests"
                ));
            }

            User user = userService.saveInterests(userId, interests);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Interests saved successfully",
                "user", user
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", "Failed to save interests: " + e.getMessage()
            ));
        }
    }
}
