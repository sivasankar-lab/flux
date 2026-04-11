package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findBySessionToken(String sessionToken);
    boolean existsByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByEmailIgnoreCase(String email);
}
