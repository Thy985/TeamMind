package com.teammind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.dto.*;
import com.teammind.entity.Agent;
import com.teammind.entity.Agent.AgentStatus;
import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.evolution.EvolutionEngine;
import com.teammind.repository.AgentRepository;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.websocket.WSEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Agent Service
 * 
 * 处理 Agent 相关的业务逻辑，包括进化能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final EvolutionRecordRepository evolutionRecordRepository;
    private final EvolutionEngine evolutionEngine;
    private final WSEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final SQLiteWriteLockService writeLockService;
    private final AgentMetricsService agentMetricsService;

    @Value("${teammind.agents-path:${user.home}/.teammind/agents}")
    private String agentsPath;

    /**
     * 获取所有 Agent（市场和已安装）
     */
    public List<AgentDTO> listAgents() {
        List<Agent> agents = agentRepository.findAll();
        return agents.stream().map(this::toDTO).toList();
    }

    /**
     * 获取已安装的 Agent
     */
    public List<AgentDTO> listInstalledAgents() {
        List<Agent> agents = agentRepository.findByInstalledTrue();
        return agents.stream().map(this::toDTO).toList();
    }

    /**
     * 获取 Agent 详情
     */
    public AgentDTO getAgent(String id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + id));
        return toDTO(agent);
    }

    /**
     * 安装 Agent
     */
    @Transactional
    public AgentDTO installAgent(String id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + id));

        if (agent.getInstalled()) {
            throw new RuntimeException("Agent already installed: " + id);
        }

        agent.setInstalled(true);
        agent.setEnabled(true);
        agent.setInstalledAt(LocalDateTime.now());
        agent.setDownloadCount(agent.getDownloadCount() + 1);

        Agent finalAgent = agent;
        agent = writeLockService.executeWithLock(() -> agentRepository.save(finalAgent));

        // 加载 Agent 配置
        loadAgentConfig(agent);

        return toDTO(agent);
    }

    /**
     * 卸载 Agent
     */
    @Transactional
    public void uninstallAgent(String id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + id));

        agent.setInstalled(false);
        agent.setEnabled(false);
        Agent finalAgent = agent;
        writeLockService.executeWithLock(() -> agentRepository.save(finalAgent));
    }

    /**
     * 切换 Agent 启用状态
     */
    @Transactional
    public AgentDTO toggleAgent(String id, boolean enabled) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + id));

        agent.setEnabled(enabled);
        Agent finalAgent = agent;
        agent = writeLockService.executeWithLock(() -> agentRepository.save(finalAgent));
        return toDTO(agent);
    }

    /**
     * 创建自定义 Agent
     */
    @Transactional
    public AgentDTO createAgent(CreateAgentRequest request) {
        Agent agent = Agent.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .icon(request.getIcon() != null ? request.getIcon() : "🤖")
                .version("1.0.0")
                .author("User")
                .status(AgentStatus.IDLE)
                .currentPrompt(request.getPrompt())
                .originalPrompt(request.getPrompt())
                .permissions(request.getPermissions())
                .tools(request.getTools())
                .installed(true)
                .enabled(true)
                .installedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .downloadCount(0)
                .evolutionVersion(1)
                .evolutionScore(0.0)
                .build();

        Agent finalAgent = agent;
        agent = writeLockService.executeWithLock(() -> agentRepository.save(finalAgent));

        // 保存 Agent 配置到 Markdown 文件
        saveAgentConfig(agent);

        return toDTO(agent);
    }

    /**
     * 触发进化
     */
    @Transactional
    public EvolutionResultDTO triggerEvolution(String agentId, EvolutionRequest request) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));

        EvolutionType type = EvolutionType.valueOf(request.getType().toUpperCase());
        
        log.info("Triggering {} evolution for agent: {}", type, agentId);

        // 进化前指标快照（用于前后对比验收）
        Map<String, Object> metricsBefore = agentMetricsService.getAgentMetrics(agentId);

        // 发布进化触发事件
        eventPublisher.publishEvolutionTriggered(agentId, type.name(), request.getReason());

        // 执行进化
        EvolutionResultDTO result = evolutionEngine.evolve(agent, type, request);

        if (result.getSuccess()) {
            // 进化后指标快照（用于前后对比验收）
            result.setMetricsBefore(metricsBefore);
            result.setMetricsAfter(agentMetricsService.getAgentMetrics(agentId));

            // 更新 Agent 版本
            agent.setEvolutionVersion(result.getToVersion());
            agent.setUpdatedAt(LocalDateTime.now());
            
            // 更新进化分数
            if (result.getScoreChange() != null) {
                double newScore = (agent.getEvolutionScore() != null ? agent.getEvolutionScore() : 0) 
                        + result.getScoreChange();
                agent.setEvolutionScore(newScore);
            }

            Agent finalAgent = agent;
            writeLockService.executeWithLock(() -> agentRepository.save(finalAgent));

            // 保存更新后的配置
            saveAgentConfig(agent);

            // 发布进化完成事件
            eventPublisher.publishEvolutionCompleted(agentId, type.name(), 
                    Map.of("version", result.getToVersion(), "scoreChange", result.getScoreChange()));
        }

        return result;
    }

    /**
     * 回滚进化
     */
    @Transactional
    public AgentDTO rollbackEvolution(Long recordId) {
        EvolutionRecord record = evolutionRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Evolution record not found: " + recordId));

        if (record.getIsRolledBack()) {
            throw new RuntimeException("Evolution already rolled back");
        }

        Agent agent = agentRepository.findById(record.getAgentId())
                .orElseThrow(() -> new RuntimeException("Agent not found: " + record.getAgentId()));

        // 恢复之前的状态
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeState = record.getBeforeState();
        
        if (beforeState != null) {
            if (beforeState.containsKey("prompt")) {
                agent.setCurrentPrompt((String) beforeState.get("prompt"));
            }
            if (beforeState.containsKey("tools")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tools = objectMapper.convertValue(
                        beforeState.get("tools"), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
                agent.setTools(tools);
            }
        }

        agent.setEvolutionVersion(record.getFromVersion());
        agent.setUpdatedAt(LocalDateTime.now());
        agentRepository.save(agent);

        // 标记记录为已回滚
        record.setIsRolledBack(true);
        evolutionRecordRepository.save(record);

        log.info("Rolled back evolution for agent: {}, version {} -> {}", 
                agent.getId(), record.getToVersion(), record.getFromVersion());

        return toDTO(agent);
    }

    /**
     * 获取进化历史
     */
    public List<EvolutionResultDTO> getEvolutionHistory(String agentId) {
        List<EvolutionRecord> records = evolutionRecordRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId);

        return records.stream()
                .map(this::toEvolutionResultDTO)
                .toList();
    }

    /**
     * 用户评分（真实进化评估闭环）
     */
    @Transactional
    public AgentDTO rateAgent(String agentId, double rating) {
        Agent agent = agentMetricsService.rateAgent(agentId, rating);
        return toDTO(agent);
    }

    /**
     * 获取 Agent 真实执行指标（真实进化评估闭环）
     */
    public Map<String, Object> getAgentMetrics(String agentId) {
        return agentMetricsService.getAgentMetrics(agentId);
    }

    /**
     * 加载 Agent 配置文件
     */
    private void loadAgentConfig(Agent agent) {
        if (agent.getConfigPath() == null) {
            return;
        }

        try {
            Path configPath = Paths.get(agentsPath, agent.getConfigPath());
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                // 解析 Markdown 配置
                parseAgentMarkdown(agent, content);
                log.info("Loaded config for agent: {}", agent.getId());
            }
        } catch (IOException e) {
            log.error("Failed to load agent config: {}", agent.getId(), e);
        }
    }

    /**
     * 保存 Agent 配置到 Markdown 文件
     */
    private void saveAgentConfig(Agent agent) {
        try {
            Path agentsDir = Paths.get(agentsPath).toAbsolutePath();
            Files.createDirectories(agentsDir);

            String filename = agent.getName().toLowerCase().replaceAll("[^a-z0-9]", "-") + ".md";
            Path configPath = agentsDir.resolve(filename);

            StringBuilder md = new StringBuilder();
            md.append("# ").append(agent.getName()).append("\n\n");
            md.append("> ").append(agent.getDescription()).append("\n\n");
            md.append("**Version:** ").append(agent.getVersion()).append("\n");
            md.append("**Author:** ").append(agent.getAuthor()).append("\n");
            md.append("**Evolution Version:** ").append(agent.getEvolutionVersion()).append("\n\n");
            
            md.append("## Prompt\n\n");
            md.append("```markdown\n");
            md.append(agent.getCurrentPrompt() != null ? agent.getCurrentPrompt() : "");
            md.append("\n```\n\n");

            if (agent.getTools() != null && !agent.getTools().isEmpty()) {
                md.append("## Tools\n\n");
                for (Map<String, Object> tool : agent.getTools()) {
                    md.append("- **").append(tool.get("name")).append("**: ");
                    md.append(tool.get("description")).append("\n");
                }
                md.append("\n");
            }

            md.append("## Permissions\n\n");
            if (agent.getPermissions() != null) {
                for (String perm : agent.getPermissions()) {
                    md.append("- ").append(perm).append("\n");
                }
            }

            Files.writeString(configPath, md.toString());
            agent.setConfigPath(filename);
            log.info("Saved config for agent: {} to {}", agent.getId(), configPath);

        } catch (IOException e) {
            log.error("Failed to save agent config: {}", agent.getId(), e);
        }
    }

    /**
     * 解析 Agent Markdown 配置
     */
    private void parseAgentMarkdown(Agent agent, String content) {
        // 简单解析，实际可以使用 CommonMark 或其他 Markdown 解析库
        String[] lines = content.split("\n");
        boolean inPromptBlock = false;
        StringBuilder prompt = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                agent.setName(line.substring(2).trim());
            } else if (line.startsWith("> ")) {
                agent.setDescription(line.substring(2).trim());
            } else if (line.contains("```markdown")) {
                inPromptBlock = true;
            } else if (line.contains("```") && inPromptBlock) {
                inPromptBlock = false;
            } else if (inPromptBlock) {
                prompt.append(line).append("\n");
            }
        }

        if (!prompt.isEmpty()) {
            agent.setCurrentPrompt(prompt.toString().trim());
        }
    }

    /**
     * 转换为 DTO
     */
    private AgentDTO toDTO(Agent agent) {
        return AgentDTO.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .icon(agent.getIcon())
                .version(agent.getVersion())
                .author(agent.getAuthor())
                .downloadCount(agent.getDownloadCount())
                .rating(agent.getRating())
                .status(agent.getStatus().name().toLowerCase())
                .permissions(agent.getPermissions())
                .configPath(agent.getConfigPath())
                .currentPrompt(agent.getCurrentPrompt())
                .evolutionVersion(agent.getEvolutionVersion())
                .evolutionScore(agent.getEvolutionScore())
                .totalMissions(agent.getTotalMissions())
                .successfulMissions(agent.getSuccessfulMissions())
                .totalTokensUsed(agent.getTotalTokensUsed())
                .userRating(agent.getUserRating())
                .ratingCount(agent.getRatingCount())
                .installed(agent.getInstalled())
                .enabled(agent.getEnabled())
                .installedAt(formatDateTime(agent.getInstalledAt()))
                .testReport(agent.getTestReport())
                .build();
    }

    /**
     * 转换为进化结果 DTO
     */
    private EvolutionResultDTO toEvolutionResultDTO(EvolutionRecord record) {
        return EvolutionResultDTO.builder()
                .recordId(record.getId())
                .agentId(record.getAgentId())
                .type(record.getType().name())
                .fromVersion(record.getFromVersion())
                .toVersion(record.getToVersion())
                .description(record.getDescription())
                .scoreChange(record.getScoreChange())
                .success(true)
                .rollbackUrl("/api/agents/" + record.getAgentId() + "/evolution/" + record.getId() + "/rollback")
                .build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
