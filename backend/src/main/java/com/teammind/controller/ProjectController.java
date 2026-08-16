package com.teammind.controller;

import com.teammind.entity.Project;
import com.teammind.plugin.PluginManager;
import com.teammind.plugin.adapter.CLIAdapter;
import com.teammind.plugin.adapter.CLIProcessTracker;
import com.teammind.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Project Controller — Phase 3A: Project CRUD + CLI Health endpoint
 *
 * GET  /api/projects              — 项目列表
 * POST /api/projects              — 创建项目
 * GET  /api/projects/{id}         — 项目详情
 * PUT  /api/projects/{id}         — 更新项目
 * DELETE /api/projects/{id}       — 删除项目
 * GET  /api/projects/{id}/cli-health  — 该项目绑定的 CLI 健康状态（3A 验证 3B）
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepo;
    private final PluginManager pluginManager;
    private final CLIProcessTracker processTracker;

    @GetMapping
    public List<Map<String, Object>> listProjects() {
        return projectRepo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public Project createProject(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        Project project = Project.builder()
                .id(id)
                .name((String) body.getOrDefault("name", "Untitled"))
                .description((String) body.get("description"))
                .rootPath((String) body.getOrDefault("rootPath", "."))
                .controlMode(com.teammind.common.ControlMode.SUPERVISED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return projectRepo.save(project);
    }

    @GetMapping("/{id}")
    public Optional<Project> getProject(@PathVariable String id) {
        return projectRepo.findById(id);
    }

    @PutMapping("/{id}")
    public Project updateProject(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Project project = projectRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));
        if (body.get("name") != null) project.setName((String) body.get("name"));
        if (body.get("description") != null) project.setDescription((String) body.get("description"));
        if (body.get("rootPath") != null) project.setRootPath((String) body.get("rootPath"));
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepo.save(project);
    }

    @DeleteMapping("/{id}")
    public void deleteProject(@PathVariable String id) {
        projectRepo.deleteById(id);
    }

    /**
     * CLI 健康检查 — Phase 3A 验证 Phase 3B 成果的核心端点
     * 返回所有已注入 CLI 的状态（3B 发现 + 3A 验证）
     */
    @GetMapping("/{id}/cli-health")
    public Map<String, Object> getCLIHealth(@PathVariable String id) {
        Project project = projectRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", id);
        result.put("projectName", project.getName());
        result.put("checkedAt", LocalDateTime.now());

        Map<String, Object> cliStatuses = new LinkedHashMap<>();
        for (var plugin : pluginManager.getAll()) {
            if (plugin.type() != com.teammind.plugin.Plugin.PluginType.AGENT) continue;
            try {
                CLIAdapter adapter = (CLIAdapter) plugin;
                com.teammind.plugin.Plugin.PluginHealth health = adapter.inspect();
                boolean alive = adapter.isAlive();
                long pid = alive ? adapter.getProcessHandle().map(ProcessHandle::pid).orElse(-1L) : -1L;
                cliStatuses.put(adapter.id(), Map.of(
                        "health", health.name(),
                        "processAlive", alive,
                        "pid", pid,
                        "command", adapter.config().command(),
                        "outputFormat", adapter.config().outputFormat().name()
                ));
            } catch (ClassCastException e) {
                // 非 CLI 插件
            }
        }
        result.put("cliStatuses", cliStatuses);
        result.put("totalCLIs", cliStatuses.size());

        int aliveCount = (int) cliStatuses.values().stream()
                .filter(s -> Boolean.TRUE.equals(((Map<String, Object>) s).get("processAlive")))
                .count();
        result.put("aliveCount", aliveCount);

        return result;
    }

    private Map<String, Object> toDto(Project p) {
        return Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "description", p.getDescription(),
                "rootPath", p.getRootPath(),
                "controlMode", p.getControlMode() != null ? p.getControlMode().name() : "SUPERVISED",
                "createdAt", p.getCreatedAt(),
                "updatedAt", p.getUpdatedAt(),
                "lastRunAt", p.getLastRunAt()
        );
    }
}
