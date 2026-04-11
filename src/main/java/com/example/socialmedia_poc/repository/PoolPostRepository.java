package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.PoolPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoolPostRepository extends JpaRepository<PoolPost, String> {
    List<PoolPost> findByCategory(String category);
    long countByCategory(String category);
    long countBySource(PoolPost.PostSource source);
    List<PoolPost> findByPostIdNotIn(java.util.Set<String> seenIds);
    List<PoolPost> findBySource(PoolPost.PostSource source);
}
