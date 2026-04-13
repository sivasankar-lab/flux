package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.SeedWithMeta;
import com.example.socialmedia_poc.service.TrendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/explore")
public class ExploreController {

    private final TrendService trendService;

    public ExploreController(TrendService trendService) {
        this.trendService = trendService;
    }

    @GetMapping("/trending")
    public ResponseEntity<?> getTrending(@RequestParam(defaultValue = "10") int limit) {
        List<PoolPost> posts = trendService.getTrendingPosts(limit);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "type", "trending",
            "posts", posts.stream().map(this::toDto).collect(Collectors.toList())
        ));
    }

    @GetMapping("/featured")
    public ResponseEntity<?> getFeatured(@RequestParam(defaultValue = "10") int limit) {
        List<PoolPost> posts = trendService.getFeaturedPosts(limit);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "type", "featured",
            "posts", posts.stream().map(this::toDto).collect(Collectors.toList())
        ));
    }

    @GetMapping("/most-liked")
    public ResponseEntity<?> getMostLiked(@RequestParam(defaultValue = "10") int limit) {
        List<PoolPost> posts = trendService.getMostLikedPosts(limit);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "type", "most_liked",
            "posts", posts.stream().map(this::toDto).collect(Collectors.toList())
        ));
    }

    @PostMapping("/compute-trends")
    public ResponseEntity<?> computeTrends() {
        trendService.computeTrends();
        return ResponseEntity.ok(Map.of("status", "success", "message", "Trends computed"));
    }

    private Map<String, Object> toDto(PoolPost post) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("post_id", post.getPostId());
        dto.put("content", post.getContent());
        dto.put("caption", post.getCaption());
        dto.put("category", post.getCategory());
        dto.put("tags", post.getTags());
        dto.put("like_count", post.getLikeCount());
        dto.put("view_count", post.getViewCount());
        dto.put("engagement_score", post.getEngagementScore());
        dto.put("source", post.getSource());
        return dto;
    }
}
