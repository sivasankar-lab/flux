package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.InterestProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterestProfileRepository extends JpaRepository<InterestProfile, String> {
    // Primary key is userId — findById(userId) works out of the box
}
