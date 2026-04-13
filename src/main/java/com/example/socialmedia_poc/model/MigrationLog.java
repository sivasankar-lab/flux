package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.time.Instant;

/**
 * Tracks every data migration run — who triggered it, what it did,
 * how many records it touched, and whether it succeeded.
 * Designed for reuse across any future migration task.
 */
@Entity
@Table(name = "migration_logs", indexes = {
    @Index(name = "idx_mlog_name", columnList = "migration_name"),
    @Index(name = "idx_mlog_status", columnList = "status"),
    @Index(name = "idx_mlog_started", columnList = "started_at")
})
public class MigrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "migration_name", nullable = false)
    @JsonProperty("migration_name")
    private String migrationName;

    /** Short human-readable description of what this migration does. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "total_records")
    @JsonProperty("total_records")
    private int totalRecords;

    @Column(name = "processed_records")
    @JsonProperty("processed_records")
    private int processedRecords;

    @Column(name = "updated_records")
    @JsonProperty("updated_records")
    private int updatedRecords;

    @Column(name = "skipped_records")
    @JsonProperty("skipped_records")
    private int skippedRecords;

    @Column(name = "failed_records")
    @JsonProperty("failed_records")
    private int failedRecords;

    /** Detailed log of what happened — kept as TEXT for flexibility. */
    @Column(name = "detail_log", columnDefinition = "TEXT")
    @JsonProperty("detail_log")
    private String detailLog;

    /** Error message if the migration failed overall. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    @JsonProperty("error_message")
    private String errorMessage;

    @Column(name = "triggered_by")
    @JsonProperty("triggered_by")
    private String triggeredBy;

    @Column(name = "started_at")
    @JsonProperty("started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    @JsonProperty("completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    @JsonProperty("duration_ms")
    private long durationMs;

    public enum Status {
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED
    }

    public MigrationLog() {}

    /** Start a new migration log entry. */
    public static MigrationLog start(String name, String description, String triggeredBy) {
        MigrationLog log = new MigrationLog();
        log.migrationName = name;
        log.description = description;
        log.triggeredBy = triggeredBy;
        log.status = Status.RUNNING;
        log.startedAt = Instant.now();
        return log;
    }

    /** Mark this migration as complete. */
    public void complete() {
        this.completedAt = Instant.now();
        this.durationMs = this.completedAt.toEpochMilli() - this.startedAt.toEpochMilli();
        if (this.failedRecords > 0) {
            this.status = Status.COMPLETED_WITH_ERRORS;
        } else {
            this.status = Status.COMPLETED;
        }
    }

    /** Mark this migration as failed. */
    public void fail(String error) {
        this.completedAt = Instant.now();
        this.durationMs = this.completedAt.toEpochMilli() - this.startedAt.toEpochMilli();
        this.status = Status.FAILED;
        this.errorMessage = error;
    }

    // --- Getters / Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMigrationName() { return migrationName; }
    public void setMigrationName(String migrationName) { this.migrationName = migrationName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

    public int getProcessedRecords() { return processedRecords; }
    public void setProcessedRecords(int processedRecords) { this.processedRecords = processedRecords; }

    public int getUpdatedRecords() { return updatedRecords; }
    public void setUpdatedRecords(int updatedRecords) { this.updatedRecords = updatedRecords; }

    public int getSkippedRecords() { return skippedRecords; }
    public void setSkippedRecords(int skippedRecords) { this.skippedRecords = skippedRecords; }

    public int getFailedRecords() { return failedRecords; }
    public void setFailedRecords(int failedRecords) { this.failedRecords = failedRecords; }

    public String getDetailLog() { return detailLog; }
    public void setDetailLog(String detailLog) { this.detailLog = detailLog; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
