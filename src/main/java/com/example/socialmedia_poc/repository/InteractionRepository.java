package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, Long> {
    List<Interaction> findByUserIdOrderByTimestampAsc(String userId);
    long countByUserId(String userId);

    @Query("SELECT DISTINCT i.userId FROM Interaction i")
    List<String> findDistinctUserIds();
}
