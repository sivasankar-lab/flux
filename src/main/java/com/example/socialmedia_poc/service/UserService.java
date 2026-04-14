package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.User;
import com.example.socialmedia_poc.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class UserService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final InterestProfileService interestProfileService;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                       InterestProfileService interestProfileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.interestProfileService = interestProfileService;
    }

    // Create new user
    @Transactional
    public User createUser(String username, String email, String displayName, String password) {
        if (userRepository.existsByUsernameIgnoreCaseOrEmailIgnoreCase(username, email)) {
            throw new IllegalArgumentException("Username or email already exists");
        }

        User newUser = new User(username, email, displayName);
        newUser.setPasswordHash(passwordEncoder.encode(password));
        issueSession(newUser);
        return userRepository.save(newUser);
    }

    // Login user
    @Transactional
    public User loginUser(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            // Legacy user (no password set) — auto-migrate on first login
            user.setPasswordHash(passwordEncoder.encode(password));
        } else {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new IllegalArgumentException("Invalid credentials");
            }
        }

        user.setLastLogin(Instant.now());
        issueSession(user);
        return userRepository.save(user);
    }

    // Validate session token — returns null if invalid or expired
    public User validateSession(String sessionToken) {
        User user = userRepository.findBySessionToken(sessionToken).orElse(null);
        if (user == null) return null;
        // Check expiry
        if (user.getSessionCreatedAt() == null ||
            Duration.between(user.getSessionCreatedAt(), Instant.now()).compareTo(SESSION_TTL) > 0) {
            // Session expired — clear it
            user.setSessionToken(null);
            user.setSessionCreatedAt(null);
            userRepository.save(user);
            return null;
        }
        return user;
    }

    // Get user by ID
    public User getUserById(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    // Get user by username
    public User getUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username).orElse(null);
    }

    // Logout user
    @Transactional
    public void logoutUser(String sessionToken) {
        userRepository.findBySessionToken(sessionToken)
                .ifPresent(user -> {
                    user.setSessionToken(null);
                    user.setSessionCreatedAt(null);
                    userRepository.save(user);
                });
    }

    // Login or create user via Google Sign-In
    @Transactional
    public User loginOrCreateGoogleUser(String googleId, String email, String displayName, String pictureUrl) {
        // 1. Check if user already linked by Google ID
        User user = userRepository.findByGoogleId(googleId).orElse(null);
        if (user != null) {
            user.setLastLogin(Instant.now());
            issueSession(user);
            if (pictureUrl != null) user.setProfilePictureUrl(pictureUrl);
            return userRepository.save(user);
        }

        // 2. Check if existing user has same email — link accounts
        user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null) {
            user.setGoogleId(googleId);
            user.setAuthProvider("BOTH");
            user.setLastLogin(Instant.now());
            issueSession(user);
            if (pictureUrl != null) user.setProfilePictureUrl(pictureUrl);
            return userRepository.save(user);
        }

        // 3. Brand new Google user
        String autoUsername = "google_" + System.currentTimeMillis();
        User newUser = new User(autoUsername, email, displayName != null ? displayName : autoUsername);
        newUser.setGoogleId(googleId);
        newUser.setAuthProvider("GOOGLE");
        newUser.setProfilePictureUrl(pictureUrl);
        issueSession(newUser);
        return userRepository.save(newUser);
    }

    // Update user role (admin function)
    @Transactional
    public User updateUserRole(String userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setRole(role);
        return userRepository.save(user);
    }

    // Get all users (admin function)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Generate cryptographically secure session token
    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Issue a new session token with creation timestamp
    private void issueSession(User user) {
        user.setSessionToken(generateSessionToken());
        user.setSessionCreatedAt(Instant.now());
    }

    // Save user interests from onboarding
    @Transactional
    public User saveInterests(String userId, List<String> interests) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setInterests(interests);
        user.setOnboarded(true);
        User savedUser = userRepository.save(user);

        // Pre-seed the interest profile from selected topics
        interestProfileService.initializeFromInterests(userId, interests);

        return savedUser;
    }
}
