package com.example.socialmedia_poc.controller;

import com.example.socialmedia_poc.model.WallPost;
import com.example.socialmedia_poc.service.PostPoolService;
import com.example.socialmedia_poc.service.WallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/wall")
public class WallController {

    private final WallService wallService;
    private final PostPoolService poolService;

    public WallController(WallService wallService, PostPoolService poolService) {
        this.wallService = wallService;
        this.poolService = poolService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getWall(@PathVariable String userId) {
        try {
            List<WallPost> wall = wallService.getWall(userId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("total", wall.size());
            resp.put("posts", wall);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", "Failed to load wall: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/next")
    public ResponseEntity<?> generateNextBatch(@PathVariable String userId) {
        try {
            List<WallPost> newPosts = wallService.generateNextBatch(userId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("message", "Generated " + newPosts.size() + " new posts");
            resp.put("batch", newPosts.isEmpty() ? 0 : newPosts.get(0).getBatch());
            resp.put("posts", newPosts);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", "Failed to generate posts: " + e.getMessage()));
        }
    }

    @GetMapping("/{userId}/batch/{batch}")
    public ResponseEntity<?> getWallBatch(@PathVariable String userId, @PathVariable int batch) {
        try {
            List<WallPost> posts = wallService.getWallBatch(userId, batch);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("batch", batch);
            resp.put("count", posts.size());
            resp.put("posts", posts);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", "Failed to load batch: " + e.getMessage()));
        }
    }

    @GetMapping("/{userId}/stats")
    public ResponseEntity<?> getWallStats(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(wallService.getWallStats(userId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", "Failed to load stats: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/reset")
    public ResponseEntity<?> resetWall(@PathVariable String userId) {
        try {
            List<WallPost> wall = wallService.resetWall(userId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "success");
            resp.put("message", "Wall reset with " + wall.size() + " fresh posts");
            resp.put("posts", wall);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", "Failed to reset wall: " + e.getMessage()));
        }
    }

    @GetMapping("/pool/stats")
    public ResponseEntity<?> getPoolStats() {
        try {
            return ResponseEntity.ok(poolService.getPoolStats());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", "Failed to load pool stats: " + e.getMessage()));
        }
    }
}
