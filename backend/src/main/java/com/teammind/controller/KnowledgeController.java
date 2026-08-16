package com.teammind.controller;

import com.teammind.entity.KnowledgeEntry;
import com.teammind.repository.KnowledgeEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * KnowledgeController — Sprint 4: Knowledge Promotion API
 *
 *   POST   /api/knowledge              → 保存 ADR / Lesson
 *   GET    /api/knowledge/task/{id}    → 按任务查询
 *   GET    /api/knowledge/project/{id} → 按项目查询
 *   DELETE /api/knowledge/{id}         → 删除
 *   POST   /api/knowledge/{id}/dismiss → 标记为忽略
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    private final KnowledgeEntryRepository repo;

    public KnowledgeController(KnowledgeEntryRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "LESSON"));
        String title = String.valueOf(body.getOrDefault("title", ""));
        String description = String.valueOf(body.getOrDefault("description", ""));
        String taskId = body.get("taskId") != null ? String.valueOf(body.get("taskId")) : null;
        String projectId = body.get("projectId") != null ? String.valueOf(body.get("projectId")) : null;
        String source = body.get("source") != null ? String.valueOf(body.get("source")) : null;

        KnowledgeEntry entry = KnowledgeEntry.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .projectId(projectId)
                .type(KnowledgeEntry.KnowledgeType.valueOf(type.toUpperCase()))
                .title(title)
                .description(description)
                .source(source)
                .confidence(0.5)
                .dismissed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        entry = repo.save(entry);
        log.info("Saved knowledge entry: type={}, title={}, taskId={}", type, title, taskId);

        return Map.of(
                "id", entry.getId(),
                "type", entry.getType().name(),
                "title", entry.getTitle(),
                "saved", true
        );
    }

    @GetMapping("/task/{taskId}")
    public List<Map<String, Object>> getByTask(@PathVariable String taskId) {
        return repo.findByTaskId(taskId).stream()
                .map(this::toMap)
                .toList();
    }

    @GetMapping("/project/{projectId}")
    public List<Map<String, Object>> getByProject(@PathVariable String projectId) {
        return repo.findByProjectId(projectId).stream()
                .map(this::toMap)
                .toList();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        repo.deleteById(id);
        log.info("Deleted knowledge entry: {}", id);
        return Map.of("id", id, "deleted", true);
    }

    @PostMapping("/{id}/dismiss")
    public Map<String, Object> dismiss(@PathVariable String id) {
        Optional<KnowledgeEntry> opt = repo.findById(id);
        if (opt.isPresent()) {
            KnowledgeEntry entry = opt.get();
            entry.setDismissed(true);
            entry.setUpdatedAt(LocalDateTime.now());
            repo.save(entry);
            return Map.of("id", id, "dismissed", true);
        }
        return Map.of("id", id, "error", "not found");
    }

    private Map<String, Object> toMap(KnowledgeEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("taskId", e.getTaskId());
        m.put("projectId", e.getProjectId());
        m.put("type", e.getType().name());
        m.put("title", e.getTitle());
        m.put("description", e.getDescription());
        m.put("source", e.getSource());
        m.put("confidence", e.getConfidence());
        m.put("dismissed", e.getDismissed());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }
}
