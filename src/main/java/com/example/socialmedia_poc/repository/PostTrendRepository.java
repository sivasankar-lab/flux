package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.PostTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostTrendRepository extends JpaRepository<PostTrend, Long> {

    Optional<PostTrend> findByPostIdAndTrendDate(String postId, LocalDate trendDate);

    List<PostTrend> findByTrendDateOrderByTrendScoreDesc(LocalDate trendDate);

    List<PostTrend> findByTrendDateAndFeaturedTrueOrderByEngagementScoreDesc(LocalDate trendDate);

    @Query("SELECT pt FROM PostTrend pt WHERE pt.trendDate = :date ORDER BY pt.likeCount DESC")
    List<PostTrend> findMostLikedByDate(@Param("date") LocalDate date);

    @Query("SELECT pt FROM PostTrend pt WHERE pt.trendDate = :date AND pt.category = :category ORDER BY pt.trendScore DESC")
    List<PostTrend> findTrendingByCategoryAndDate(@Param("category") String category, @Param("date") LocalDate date);

    void deleteByTrendDateBefore(LocalDate date);
}
