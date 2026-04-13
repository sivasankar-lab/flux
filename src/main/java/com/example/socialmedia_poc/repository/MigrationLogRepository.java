package com.example.socialmedia_poc.repository;

import com.example.socialmedia_poc.model.MigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationLogRepository extends JpaRepository<MigrationLog, Long> {
    List<MigrationLog> findByMigrationNameOrderByStartedAtDesc(String migrationName);
    List<MigrationLog> findAllByOrderByStartedAtDesc();
    List<MigrationLog> findByStatus(MigrationLog.Status status);
}
