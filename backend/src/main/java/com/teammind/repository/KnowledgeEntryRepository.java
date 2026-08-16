package com.teammind.repository;

import com.teammind.entity.KnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, String> {
    List<KnowledgeEntry> findByTaskId(String taskId);
    List<KnowledgeEntry> findByProjectId(String projectId);
    List<KnowledgeEntry> findByTaskIdAndDismissedFalse(String taskId);
    List<KnowledgeEntry> findByProjectIdAndDismissedFalse(String projectId);
    List<KnowledgeEntry> findByType(KnowledgeEntry.KnowledgeType type);
}
