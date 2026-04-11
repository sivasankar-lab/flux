package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.WallPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface WallPostRepository extends JpaRepository<WallPost, Long> {
    List<WallPost> findByUserIdOrderByBatchAscIdAsc(String userId);
    List<WallPost> findByUserIdAndBatch(String userId, int batch);
    void deleteByUserId(String userId);

    @Query("SELECT MAX(w.batch) FROM WallPost w WHERE w.userId = :userId")
    Optional<Integer> findMaxBatchByUserId(@Param("userId") String userId);

    @Query("SELECT w.postId FROM WallPost w WHERE w.userId = :userId")
    Set<String> findPostIdsByUserId(@Param("userId") String userId);

    long countByUserId(String userId);
    long countByUserIdAndSource(String userId, WallPost.PostSource source);
}
