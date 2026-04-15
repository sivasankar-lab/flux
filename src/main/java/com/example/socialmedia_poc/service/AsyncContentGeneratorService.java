package com.example.socialmedia_poc.service;

import com.example.socialmedia_poc.model.PoolPost;
import com.example.socialmedia_poc.model.SeedWithMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Async background service that generates content WITHOUT blocking user requests.
 *
 * Architecture:
 *  - In-memory ConcurrentLinkedQueue of GenerationTasks
 *  - Single background thread drains the queue (one LLM call at a time)
 *  - Scheduled pool health monitor replenishes low categories every 2 min
 *  - All generated content lands in the shared pool for cross-user recommendation
 *
 * Callers (triggers, wall service, controllers) call enqueue() and return instantly.
 */
@Service
public class AsyncContentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AsyncContentGeneratorService.class);
    private static final String SYSTEM_USER = "system";
    private static final int MIN_POSTS_PER_CATEGORY = 12;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long FAILURE_COOLDOWN_MS = 300_000; // 5 minutes

    /** Tracks consecutive LLM failures to implement a circuit breaker */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long cooldownUntil = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "async-content-gen");
        t.setDaemon(true);
        return t;
    });

    private final Queue<GenerationTask> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private final PersonalizedSeedService personalizedSeedService;
    private final PostPoolService poolService;
    private final com.example.socialmedia_poc.config.ApiKeyStore apiKeyStore;

    public AsyncContentGeneratorService(PersonalizedSeedService personalizedSeedService,
                                        PostPoolService poolService,
                                        com.example.socialmedia_poc.config.ApiKeyStore apiKeyStore) {
        this.personalizedSeedService = personalizedSeedService;
        this.poolService = poolService;
        this.apiKeyStore = apiKeyStore;
    }

    // ──────────────────────────────────────────────
    // Public API: enqueue generation tasks
    // ──────────────────────────────────────────────

    /**
     * Enqueue a single generation task. Returns immediately.
     */
    public void enqueue(String userId, String category, int count, String reason) {
        if (!apiKeyStore.isPoolGenerationEnabled()) {
            log.info("[AsyncGen] Pool generation disabled — skipping enqueue for '{}'", category);
            return;
        }
        taskQueue.add(new GenerationTask(userId, category, count, reason));
        log.info("[AsyncGen] Queued: {} posts for '{}' (reason: {}, user: {})",
                count, category, reason, userId);
        kickOffProcessing();
    }

    /**
     * Enqueue multiple categories at once. Returns immediately.
     */
    public void enqueueBulk(String userId, Map<String, Integer> categoryCountMap, String reason) {
        if (!apiKeyStore.isPoolGenerationEnabled()) {
            log.info("[AsyncGen] Pool generation disabled — skipping bulk enqueue");
            return;
        }
        for (Map.Entry<String, Integer> entry : categoryCountMap.entrySet()) {
            taskQueue.add(new GenerationTask(userId, entry.getKey(), entry.getValue(), reason));
        }
        log.info("[AsyncGen] Bulk queued {} categories (reason: {})", categoryCountMap.size(), reason);
        kickOffProcessing();
    }

    /**
     * Current queue depth (for stats / monitoring).
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    // ──────────────────────────────────────────────
    // Background queue processor
    // ──────────────────────────────────────────────

    private void kickOffProcessing() {
        if (processing.compareAndSet(false, true)) {
            executor.submit(() -> {
                try {
                    drainQueue();
                } finally {
                    processing.set(false);
                    // Re-check: tasks may have arrived while we were finishing
                    if (!taskQueue.isEmpty()) {
                        kickOffProcessing();
                    }
                }
            });
        }
    }

    /**
     * Returns true if in cooldown due to repeated LLM failures (circuit breaker open).
     */
    private boolean isInCooldown() {
        if (cooldownUntil == 0) return false;
        if (System.currentTimeMillis() >= cooldownUntil) {
            // Cooldown expired — reset and allow retries
            cooldownUntil = 0;
            consecutiveFailures.set(0);
            log.info("[AsyncGen] Cooldown expired — resuming generation");
            return false;
        }
        return true;
    }

    private void drainQueue() {
        GenerationTask task;
        while ((task = taskQueue.poll()) != null) {
            // Circuit breaker: if too many consecutive failures, drop remaining tasks and wait
            if (isInCooldown()) {
                log.debug("[AsyncGen] Circuit breaker open — discarding queued task for '{}'", task.category);
                continue;
            }

            final GenerationTask current = task;
            try {
                log.info("[AsyncGen] ▶ Generating {} posts for '{}' ({})", current.count, current.category, current.reason);
                long start = System.currentTimeMillis();

                List<SeedWithMeta> generated = personalizedSeedService
                        .generateForCategory(current.userId, current.category, current.count);

                if (!generated.isEmpty()) {
                    List<PoolPost> poolPosts = generated.stream()
                            .map(s -> PoolPost.fromGenerated(s, current.category))
                            .collect(Collectors.toList());
                    poolService.addToPool(poolPosts);
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("[AsyncGen] ✓ Added {} posts to pool for '{}' ({}ms)",
                            poolPosts.size(), current.category, elapsed);
                    consecutiveFailures.set(0); // reset on success
                } else {
                    int failures = consecutiveFailures.incrementAndGet();
                    log.warn("[AsyncGen] LLM returned empty for '{}' (consecutive failures: {})", current.category, failures);
                    if (failures >= MAX_CONSECUTIVE_FAILURES) {
                        cooldownUntil = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
                        log.error("[AsyncGen] ⚠ {} consecutive failures — entering 5min cooldown. Check your LLM API key!", failures);
                        // Drain remaining tasks without executing them
                        int dropped = 0;
                        while (taskQueue.poll() != null) dropped++;
                        if (dropped > 0) log.warn("[AsyncGen] Dropped {} queued tasks during cooldown", dropped);
                        return;
                    }
                }
            } catch (Exception e) {
                int failures = consecutiveFailures.incrementAndGet();
                log.error("[AsyncGen] ✗ Failed: {} posts for '{}' – {} (consecutive failures: {})",
                        current.count, current.category, e.getMessage(), failures);
                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    cooldownUntil = System.currentTimeMillis() + FAILURE_COOLDOWN_MS;
                    log.error("[AsyncGen] ⚠ {} consecutive failures — entering 5min cooldown. Check your LLM API key!", failures);
                    int dropped = 0;
                    while (taskQueue.poll() != null) dropped++;
                    if (dropped > 0) log.warn("[AsyncGen] Dropped {} queued tasks during cooldown", dropped);
                    return;
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Scheduled: pool health monitor
    // ──────────────────────────────────────────────

    /**
     * Every 2 minutes, check pool health per category.
     * If any category has fewer than MIN_POSTS_PER_CATEGORY, enqueue generation.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void monitorPoolHealth() {
        if (!apiKeyStore.isPoolGenerationEnabled()) {
            log.debug("[AsyncGen] Pool generation disabled — skipping health check");
            return;
        }
        if (isInCooldown()) {
            long remainingSec = (cooldownUntil - System.currentTimeMillis()) / 1000;
            log.warn("[AsyncGen] Circuit breaker open — skipping pool health check ({}s remaining)", remainingSec);
            return;
        }
        try {
            Map<String, Object> stats = poolService.getPoolStats();
            @SuppressWarnings("unchecked")
            Map<String, Long> categories = (Map<String, Long>) stats.get("categories");

            // If pool is completely empty, re-seed from .st files and enqueue generation for all known categories
            if (categories == null || categories.isEmpty()) {
                log.info("[AsyncGen] Pool is completely empty — re-seeding and generating for all categories");
                poolService.reseedIfEmpty();
                // Enqueue generation for standard categories so the pool fills up
                Map<String, Integer> allCategories = new LinkedHashMap<>();
                allCategories.put("Hooks", 3);
                allCategories.put("History & Society", 3);
                allCategories.put("Science & How Things Work", 3);
                allCategories.put("Psychology & Human Behavior", 3);
                allCategories.put("Technology & Future", 3);
                allCategories.put("Philosophy & Life Questions", 3);
                allCategories.put("Health & Lifestyle Tips", 3);
                enqueueBulk(SYSTEM_USER, allCategories, "POOL_EMPTY");
                return;
            }

            Map<String, Integer> deficits = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : categories.entrySet()) {
                if (entry.getValue() < MIN_POSTS_PER_CATEGORY) {
                    int needed = (int) (MIN_POSTS_PER_CATEGORY - entry.getValue());
                    deficits.put(entry.getKey(), Math.min(needed, 3)); // cap at 3 per check
                }
            }

            if (!deficits.isEmpty()) {
                log.info("[AsyncGen] Pool health: {} categories low, replenishing", deficits.size());
                enqueueBulk(SYSTEM_USER, deficits, "POOL_HEALTH");
            } else {
                log.debug("[AsyncGen] Pool health: all categories healthy");
            }
        } catch (Exception e) {
            log.error("[AsyncGen] Pool health check failed: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Task DTO
    // ──────────────────────────────────────────────

    public static class GenerationTask {
        final String userId;
        final String category;
        final int count;
        final String reason;

        public GenerationTask(String userId, String category, int count, String reason) {
            this.userId = userId;
            this.category = category;
            this.count = count;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("GenTask[user=%s, cat=%s, count=%d, reason=%s]",
                    userId, category, count, reason);
        }
    }
}
