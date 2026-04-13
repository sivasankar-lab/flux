package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.MigrationLog;
import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.WallPost;
import com.example.socialmedia_poc.repository.MigrationLogRepository;
import com.example.socialmedia_poc.repository.PoolPostRepository;
import com.example.socialmedia_poc.repository.WallPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable migration service — register named migration tasks and
 * run them on demand via the admin API. Each run is fully logged.
 *
 * To add a new migration in the future:
 *   1. Write a private method that does the work.
 *   2. Register it in the constructor: migrations.put("my_migration", (dryRun, by) -> myMethod(dryRun, by));
 *   3. Call POST /v1/admin/migrations/run  { "name": "my_migration" }
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final PoolPostRepository poolPostRepository;
    private final WallPostRepository wallPostRepository;
    private final MigrationLogRepository migrationLogRepository;

    /** Registry of available migration tasks. */
    private final Map<String, MigrationTask> migrations = new LinkedHashMap<>();

    @FunctionalInterface
    public interface MigrationTask {
        MigrationLog execute(boolean dryRun, String triggeredBy);
    }

    public MigrationService(PoolPostRepository poolPostRepository,
                            WallPostRepository wallPostRepository,
                            MigrationLogRepository migrationLogRepository) {
        this.poolPostRepository = poolPostRepository;
        this.wallPostRepository = wallPostRepository;
        this.migrationLogRepository = migrationLogRepository;

        // ── Register migrations ──
        migrations.put("backfill_captions", this::backfillCaptions);
    }

    /** List all registered migration names. */
    public Map<String, String> listMigrations() {
        Map<String, String> list = new LinkedHashMap<>();
        list.put("backfill_captions", "Extract captions from content for old posts that have null captions (pool_posts + wall_posts)");
        // Add future migrations here with a description
        return list;
    }

    /** Run a named migration. */
    public MigrationLog runMigration(String name, boolean dryRun, String triggeredBy) {
        MigrationTask task = migrations.get(name);
        if (task == null) {
            throw new IllegalArgumentException("Unknown migration: " + name + ". Available: " + migrations.keySet());
        }
        return task.execute(dryRun, triggeredBy);
    }

    /** Get all migration logs. */
    public List<MigrationLog> getLogs() {
        return migrationLogRepository.findAllByOrderByStartedAtDesc();
    }

    /** Get logs for a specific migration. */
    public List<MigrationLog> getLogsForMigration(String name) {
        return migrationLogRepository.findByMigrationNameOrderByStartedAtDesc(name);
    }

    // ═══════════════════════════════════════════════════════
    // Migration: backfill_captions
    // ═══════════════════════════════════════════════════════

    /**
     * Find all pool_posts and wall_posts where caption IS NULL,
     * and extract a caption from the first line of content
     * (same logic used by PersonalizedSeedService / SeedGenerationService).
     */
    private MigrationLog backfillCaptions(boolean dryRun, String triggeredBy) {
        String name = "backfill_captions";
        String desc = "Extract headline/caption from content for posts with null caption";
        if (dryRun) desc += " [DRY RUN]";

        MigrationLog mlog = MigrationLog.start(name, desc, triggeredBy);
        mlog = migrationLogRepository.save(mlog);

        StringBuilder details = new StringBuilder();
        int poolUpdated = 0, poolSkipped = 0, poolFailed = 0;
        int wallUpdated = 0, wallSkipped = 0, wallFailed = 0;

        try {
            // ── Pool Posts ──
            List<PoolPost> allPoolPosts = poolPostRepository.findAll();
            details.append("=== Pool Posts ===\n");
            details.append("Total pool posts: ").append(allPoolPosts.size()).append("\n");

            for (PoolPost post : allPoolPosts) {
                if (post.getCaption() != null && !post.getCaption().isBlank()) {
                    poolSkipped++;
                    continue;
                }
                try {
                    String extracted = extractCaption(post.getContent());
                    if (extracted != null) {
                        details.append("  [POOL] ").append(post.getPostId())
                               .append(" -> \"").append(truncate(extracted, 50)).append("\"\n");
                        if (!dryRun) {
                            post.setCaption(extracted);
                            poolPostRepository.save(post);
                        }
                        poolUpdated++;
                    } else {
                        poolSkipped++;
                    }
                } catch (Exception e) {
                    poolFailed++;
                    details.append("  [POOL FAIL] ").append(post.getPostId())
                           .append(": ").append(e.getMessage()).append("\n");
                }
            }

            // ── Wall Posts ──
            List<WallPost> allWallPosts = wallPostRepository.findAll();
            details.append("\n=== Wall Posts ===\n");
            details.append("Total wall posts: ").append(allWallPosts.size()).append("\n");

            for (WallPost post : allWallPosts) {
                if (post.getCaption() != null && !post.getCaption().isBlank()) {
                    wallSkipped++;
                    continue;
                }
                try {
                    String extracted = extractCaption(post.getContent());
                    if (extracted != null) {
                        details.append("  [WALL] ").append(post.getPostId())
                               .append(" -> \"").append(truncate(extracted, 50)).append("\"\n");
                        if (!dryRun) {
                            post.setCaption(extracted);
                            wallPostRepository.save(post);
                        }
                        wallUpdated++;
                    } else {
                        wallSkipped++;
                    }
                } catch (Exception e) {
                    wallFailed++;
                    details.append("  [WALL FAIL] ").append(post.getPostId())
                           .append(": ").append(e.getMessage()).append("\n");
                }
            }

            // ── Summary ──
            int totalRecords = allPoolPosts.size() + allWallPosts.size();
            int totalUpdated = poolUpdated + wallUpdated;
            int totalSkipped = poolSkipped + wallSkipped;
            int totalFailed = poolFailed + wallFailed;

            details.append("\n=== Summary ===\n");
            details.append("Pool: ").append(poolUpdated).append(" updated, ")
                   .append(poolSkipped).append(" skipped, ")
                   .append(poolFailed).append(" failed\n");
            details.append("Wall: ").append(wallUpdated).append(" updated, ")
                   .append(wallSkipped).append(" skipped, ")
                   .append(wallFailed).append(" failed\n");
            details.append("Total: ").append(totalUpdated).append(" updated / ")
                   .append(totalRecords).append(" total\n");
            if (dryRun) details.append("\n** DRY RUN — no changes persisted **\n");

            mlog.setTotalRecords(totalRecords);
            mlog.setProcessedRecords(totalRecords);
            mlog.setUpdatedRecords(totalUpdated);
            mlog.setSkippedRecords(totalSkipped);
            mlog.setFailedRecords(totalFailed);
            mlog.setDetailLog(details.toString());
            mlog.complete();

            log.info("[Migration] {} finished: {} updated, {} skipped, {} failed (dryRun={})",
                    name, totalUpdated, totalSkipped, totalFailed, dryRun);

        } catch (Exception e) {
            log.error("[Migration] {} crashed: {}", name, e.getMessage(), e);
            mlog.fail(e.getMessage());
            mlog.setDetailLog(details.toString());
        }

        return migrationLogRepository.save(mlog);
    }

    // ═══════════════════════════════════════════════════════
    // Shared helpers
    // ═══════════════════════════════════════════════════════

    /**
     * Extract a caption from content — same logic used at generation time.
     * Looks for a short first line (under 80 chars) separated by a newline.
     * Falls back to taking the first 7 words as a caption.
     */
    static String extractCaption(String content) {
        if (content == null || content.isBlank()) return null;

        // Try: first line is a headline
        if (content.contains("\n")) {
            String[] parts = content.split("\n", 2);
            String firstLine = parts[0].trim();
            String rest = parts.length > 1 ? parts[1].trim() : "";
            if (firstLine.length() > 0 && firstLine.length() < 80 && rest.length() > 0) {
                return cleanCaption(firstLine);
            }
        }

        // Fallback: first 7 words
        String clean = content.trim().replaceAll("\\s+", " ");
        String[] words = clean.split(" ");
        if (words.length <= 2) return null; // too short to bother
        int take = Math.min(7, words.length);
        String caption = String.join(" ", java.util.Arrays.copyOfRange(words, 0, take));
        caption = caption.replaceAll("[.,;:!?]+$", "");
        if (words.length > 7) caption += "...";
        return caption;
    }

    /** Strip markdown artifacts, quotes, extra punctuation from a headline. */
    private static String cleanCaption(String raw) {
        String s = raw;
        // Remove leading  ## / ** / > / quotes
        s = s.replaceAll("^[#*>\"'`]+\\s*", "");
        // Remove trailing  ** / quotes
        s = s.replaceAll("[*\"'`]+$", "");
        return s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
