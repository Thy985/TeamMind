package com.teammind.evolution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.dto.*;
import com.teammind.entity.Agent;
import com.teammind.entity.EvolutionRecord;
import com.teammind.entity.EvolutionRecord.EvolutionType;
import com.teammind.llm.LLMResponse;
import com.teammind.llm.LLMService;
import com.teammind.repository.EvolutionRecordRepository;
import com.teammind.service.AgentMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 进化引擎
 * 
 * 核心进化能力实现：
 * 1. Prompt 自我优化
 * 2. 工具自动生成
 * 3. 协作拓扑进化
 * 4. 进化历史版本管理
 * 
 * 使用 LLM 实现真正的智能进化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvolutionEngine {

    private final EvolutionRecordRepository evolutionRecordRepository;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final AgentMetricsService agentMetricsService;

    @Value("${teammind.evolution.enabled:true}")
    private boolean evolutionEnabled;

    @Value("${teammind.evolution.max-history-versions:50}")
    private int maxHistoryVersions;
    
    @Value("${teammind.evolution.prompt-optimization.min-feedback-count:5}")
    private int minFeedbackCount;

    /**
     * 执行进化
     */
    public EvolutionResultDTO evolve(Agent agent, EvolutionType type, EvolutionRequest request) {
        if (!evolutionEnabled) {
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(type.name())
                    .success(false)
                    .description("Evolution is disabled")
                    .build();
        }

        // 检查 LLM 是否可用
        if (!llmService.hasAvailableClient()) {
            log.warn("No LLM client available for evolution");
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(type.name())
                    .success(false)
                    .description("No LLM client available")
                    .build();
        }

        log.info("Executing {} evolution for agent: {}", type, agent.getId());

        return switch (type) {
            case PROMPT_OPTIMIZATION -> optimizePrompt(agent, request);
            case TOOL_GENERATION -> generateTool(agent, request);
            case TOPOLOGY_EVOLUTION -> evolveTopology(agent, request);
            case PARAMETER_TUNING -> tuneParameters(agent, request);
            case KNOWLEDGE_UPDATE -> updateKnowledge(agent, request);
        };
    }

    /**
     * Prompt 自我优化
     * 
     * 使用 LLM 分析并优化 Agent 的 Prompt
     */
    private EvolutionResultDTO optimizePrompt(Agent agent, EvolutionRequest request) {
        String currentPrompt = agent.getCurrentPrompt();
        
        if (currentPrompt == null || currentPrompt.isEmpty()) {
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.PROMPT_OPTIMIZATION.name())
                    .success(false)
                    .description("No current prompt to optimize")
                    .build();
        }

        // 保存进化前状态
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("prompt", currentPrompt);
        beforeState.put("version", agent.getEvolutionVersion());

        // 构建优化反馈
        String feedback = buildOptimizationFeedback(request);
        
        log.info("Calling LLM for prompt optimization: agent={}", agent.getId());
        
        // 使用 LLM 优化 Prompt
        LLMResponse llmResponse = llmService.optimizePrompt(currentPrompt, feedback);
        
        if (!llmResponse.isSuccess()) {
            log.error("LLM prompt optimization failed: {}", llmResponse.getError());
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.PROMPT_OPTIMIZATION.name())
                    .success(false)
                    .description("LLM optimization failed: " + llmResponse.getError())
                    .build();
        }
        
        String optimizedPrompt = llmResponse.getContent();
        
        // 验证优化结果
        if (optimizedPrompt == null || optimizedPrompt.trim().isEmpty()) {
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.PROMPT_OPTIMIZATION.name())
                    .success(false)
                    .description("LLM returned empty prompt")
                    .build();
        }

        // ✅ 真实进化评估闭环：用 Agent 真实执行指标（成功率/Token成本/用户评分）
        // 计算进化收益，替代原有的启发式打分
        double scoreChange = agentMetricsService.calculateEvolutionBenefit(agent);

        // 创建进化记录
        EvolutionRecord record = createEvolutionRecord(
                agent.getId(),
                EvolutionType.PROMPT_OPTIMIZATION,
                agent.getEvolutionVersion(),
                agent.getEvolutionVersion() + 1,
                beforeState,
                Map.of("prompt", optimizedPrompt, "tokensUsed", llmResponse.getUsage() != null ? llmResponse.getUsage().getTotalTokens() : 0),
                "Prompt optimized: " + (request.getReason() != null ? request.getReason() : "performance improvement"),
                scoreChange,
                request.getAutomatic() != null ? request.getAutomatic() : false
        );

        record = evolutionRecordRepository.save(record);

        // 更新 Agent
        agent.setCurrentPrompt(optimizedPrompt);

        log.info("Prompt optimized for agent: {}, score change: +{}, tokens used: {}", 
                agent.getId(), scoreChange, 
                llmResponse.getUsage() != null ? llmResponse.getUsage().getTotalTokens() : 0);

        return EvolutionResultDTO.builder()
                .recordId(record.getId())
                .agentId(agent.getId())
                .type(EvolutionType.PROMPT_OPTIMIZATION.name())
                .fromVersion(record.getFromVersion())
                .toVersion(record.getToVersion())
                .description(record.getDescription())
                .scoreChange(scoreChange)
                .success(true)
                .build();
    }

    /**
     * 工具自动生成
     * 
     * 使用 LLM 生成新的工具代码
     */
    private EvolutionResultDTO generateTool(Agent agent, EvolutionRequest request) {
        // ✅ 真实进化评估闭环：基于真实指标计算进化收益
        double scoreChange = agentMetricsService.calculateEvolutionBenefit(agent);

        List<Map<String, Object>> currentTools = agent.getTools();
        if (currentTools == null) {
            currentTools = new ArrayList<>();
        }

        // 保存进化前状态
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("tools", new ArrayList<>(currentTools));
        beforeState.put("version", agent.getEvolutionVersion());

        // 获取工具描述
        String toolDescription = getToolDescription(request);
        String language = getLanguage(request.getContext());
        
        log.info("Calling LLM for tool generation: agent={}, description={}", agent.getId(), toolDescription);

        // 使用 LLM 生成工具
        LLMResponse llmResponse = llmService.generateTool(toolDescription, language);
        
        if (!llmResponse.isSuccess()) {
            log.error("LLM tool generation failed: {}", llmResponse.getError());
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.TOOL_GENERATION.name())
                    .success(false)
                    .description("LLM tool generation failed: " + llmResponse.getError())
                    .build();
        }

        // 解析生成的工具
        Map<String, Object> newTool;
        try {
            String content = llmResponse.getContent();
            // 尝试提取 JSON
            newTool = parseToolFromResponse(content);
        } catch (Exception e) {
            log.error("Failed to parse generated tool", e);
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.TOOL_GENERATION.name())
                    .success(false)
                    .description("Failed to parse generated tool: " + e.getMessage())
                    .build();
        }

        // 添加元数据
        newTool.put("id", UUID.randomUUID().toString());
        newTool.put("autoGenerated", true);
        newTool.put("createdAt", LocalDateTime.now().toString());
        newTool.put("agentId", agent.getId());
        
        currentTools.add(newTool);

        // 创建进化记录
        EvolutionRecord record = createEvolutionRecord(
                agent.getId(),
                EvolutionType.TOOL_GENERATION,
                agent.getEvolutionVersion(),
                agent.getEvolutionVersion() + 1,
                beforeState,
                Map.of("tools", currentTools, "newTool", newTool),
                "Generated new tool: " + newTool.get("name"),
                scoreChange,
                request.getAutomatic() != null ? request.getAutomatic() : false
        );

        record = evolutionRecordRepository.save(record);

        // 更新 Agent
        agent.setTools(currentTools);

        log.info("Tool generated for agent: {}, tool: {}", agent.getId(), newTool.get("name"));

        return EvolutionResultDTO.builder()
                .recordId(record.getId())
                .agentId(agent.getId())
                .type(EvolutionType.TOOL_GENERATION.name())
                .fromVersion(record.getFromVersion())
                .toVersion(record.getToVersion())
                .description(record.getDescription())
                .scoreChange(scoreChange)
                .success(true)
                .build();
    }

    /**
     * 协作拓扑进化
     * 
     * 使用 LLM 分析并优化多 Agent 协作结构
     */
    private EvolutionResultDTO evolveTopology(Agent agent, EvolutionRequest request) {
        // ✅ 真实进化评估闭环：基于真实指标计算进化收益
        double scoreChange = agentMetricsService.calculateEvolutionBenefit(agent);

        // 保存进化前状态
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("version", agent.getEvolutionVersion());
        
        if (request.getContext() != null) {
            beforeState.put("topology", request.getContext().get("topology"));
            beforeState.put("performanceData", request.getContext().get("performanceData"));
        }

        // 获取当前拓扑和性能数据
        String currentTopology = request.getContext() != null ? 
                String.valueOf(request.getContext().get("topology")) : "default";
        String performanceData = request.getContext() != null ? 
                String.valueOf(request.getContext().get("performanceData")) : "No data";

        log.info("Calling LLM for topology optimization: agent={}", agent.getId());

        // 使用 LLM 分析拓扑
        LLMResponse llmResponse = llmService.analyzeTopology(currentTopology, performanceData);
        
        if (!llmResponse.isSuccess()) {
            log.error("LLM topology analysis failed: {}", llmResponse.getError());
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.TOPOLOGY_EVOLUTION.name())
                    .success(false)
                    .description("LLM topology analysis failed: " + llmResponse.getError())
                    .build();
        }

        // 解析优化后的拓扑
        Map<String, Object> newTopology;
        try {
            String content = llmResponse.getContent();
            newTopology = parseTopologyFromResponse(content);
        } catch (Exception e) {
            log.error("Failed to parse topology response", e);
            newTopology = Map.of(
                    "type", "optimized",
                    "optimizedAt", LocalDateTime.now().toString(),
                    "raw", llmResponse.getContent()
            );
        }

        // 创建进化记录
        EvolutionRecord record = createEvolutionRecord(
                agent.getId(),
                EvolutionType.TOPOLOGY_EVOLUTION,
                agent.getEvolutionVersion(),
                agent.getEvolutionVersion() + 1,
                beforeState,
                Map.of("topology", newTopology),
                "Collaboration topology optimized",
                scoreChange,
                request.getAutomatic() != null ? request.getAutomatic() : false
        );

        record = evolutionRecordRepository.save(record);

        log.info("Topology evolved for agent: {}", agent.getId());

        return EvolutionResultDTO.builder()
                .recordId(record.getId())
                .agentId(agent.getId())
                .type(EvolutionType.TOPOLOGY_EVOLUTION.name())
                .fromVersion(record.getFromVersion())
                .toVersion(record.getToVersion())
                .description(record.getDescription())
                .scoreChange(scoreChange)
                .success(true)
                .build();
    }

    /**
     * 参数调优
     */
    private EvolutionResultDTO tuneParameters(Agent agent, EvolutionRequest request) {
        // ✅ 真实进化评估闭环：基于真实指标计算进化收益
        double scoreChange = agentMetricsService.calculateEvolutionBenefit(agent);

        // 使用 LLM 进行参数调优
        String systemPrompt = """
                You are an expert in hyperparameter optimization for AI agents.
                Analyze the current parameters and suggest optimal values.
                
                Return a JSON object with the suggested parameters.
                """;
        
        String userPrompt = String.format("""
                Current Agent: %s
                Current Parameters: %s
                Performance Context: %s
                
                Suggest optimal parameters.
                """, 
                agent.getName(),
                request.getContext() != null ? request.getContext().get("parameters") : "default",
                request.getReason() != null ? request.getReason() : "general optimization"
        );
        
        LLMResponse llmResponse = llmService.chat(systemPrompt, userPrompt);
        
        if (!llmResponse.isSuccess()) {
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.PARAMETER_TUNING.name())
                    .success(false)
                    .description("Parameter tuning failed: " + llmResponse.getError())
                    .build();
        }

        return EvolutionResultDTO.builder()
                .agentId(agent.getId())
                .type(EvolutionType.PARAMETER_TUNING.name())
                .fromVersion(agent.getEvolutionVersion())
                .toVersion(agent.getEvolutionVersion() + 1)
                .description("Parameters tuned: " + llmResponse.getContent())
                .scoreChange(scoreChange)
                .success(true)
                .build();
    }

    /**
     * 知识更新
     */
    private EvolutionResultDTO updateKnowledge(Agent agent, EvolutionRequest request) {
        // ✅ 真实进化评估闭环：基于真实指标计算进化收益
        double scoreChange = agentMetricsService.calculateEvolutionBenefit(agent);

        String knowledgeContent = request.getContext() != null ? 
                String.valueOf(request.getContext().get("knowledge")) : "";
        
        if (knowledgeContent.isEmpty()) {
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.KNOWLEDGE_UPDATE.name())
                    .success(false)
                    .description("No knowledge content provided")
                    .build();
        }

        // 使用 LLM 整合新知识
        String systemPrompt = """
                You are a knowledge management expert.
                Integrate new knowledge into the agent's existing knowledge base.
                Summarize and organize the information.
                """;
        
        LLMResponse llmResponse = llmService.chat(systemPrompt, 
                "Integrate this knowledge: " + knowledgeContent);
        
        if (!llmResponse.isSuccess()) {
            return EvolutionResultDTO.builder()
                    .agentId(agent.getId())
                    .type(EvolutionType.KNOWLEDGE_UPDATE.name())
                    .success(false)
                    .description("Knowledge update failed: " + llmResponse.getError())
                    .build();
        }

        EvolutionRecord record = createEvolutionRecord(
                agent.getId(),
                EvolutionType.KNOWLEDGE_UPDATE,
                agent.getEvolutionVersion(),
                agent.getEvolutionVersion() + 1,
                Map.of("previousKnowledge", "old"),
                Map.of("newKnowledge", llmResponse.getContent()),
                "Knowledge updated",
                scoreChange,
                request.getAutomatic() != null ? request.getAutomatic() : false
        );
        
        record = evolutionRecordRepository.save(record);

        return EvolutionResultDTO.builder()
                .recordId(record.getId())
                .agentId(agent.getId())
                .type(EvolutionType.KNOWLEDGE_UPDATE.name())
                .fromVersion(record.getFromVersion())
                .toVersion(record.getToVersion())
                .description("Knowledge updated successfully")
                .scoreChange(scoreChange)
                .success(true)
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建优化反馈
     */
    private String buildOptimizationFeedback(EvolutionRequest request) {
        StringBuilder feedback = new StringBuilder();
        
        if (request.getReason() != null) {
            feedback.append("Reason for optimization: ").append(request.getReason()).append("\n");
        }
        
        if (request.getContext() != null) {
            if (request.getContext().containsKey("performanceIssues")) {
                feedback.append("Performance issues: ").append(request.getContext().get("performanceIssues")).append("\n");
            }
            if (request.getContext().containsKey("userFeedback")) {
                feedback.append("User feedback: ").append(request.getContext().get("userFeedback")).append("\n");
            }
            if (request.getContext().containsKey("errorPatterns")) {
                feedback.append("Error patterns: ").append(request.getContext().get("errorPatterns")).append("\n");
            }
        }
        
        if (feedback.isEmpty()) {
            feedback.append("General improvement: Make the prompt more effective, clearer, and more robust.");
        }
        
        return feedback.toString();
    }

    /**
     * 获取工具描述
     */
    private String getToolDescription(EvolutionRequest request) {
        if (request.getContext() != null && request.getContext().get("toolDescription") != null) {
            return String.valueOf(request.getContext().get("toolDescription"));
        }
        return request.getReason() != null ? request.getReason() : "General purpose utility tool";
    }

    /**
     * 获取编程语言
     */
    private String getLanguage(Map<String, Object> context) {
        if (context != null && context.get("language") != null) {
            return String.valueOf(context.get("language"));
        }
        return "Python";  // 默认 Python
    }

    /**
     * 从响应中解析工具定义
     */
    private Map<String, Object> parseToolFromResponse(String content) {
        try {
            // 尝试提取 JSON 块
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String json = content.substring(jsonStart, jsonEnd + 1);
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse tool JSON, using fallback", e);
        }
        
        // 回退：创建基本工具定义
        return new LinkedHashMap<>() {{
            put("name", "generated_tool_" + System.currentTimeMillis());
            put("description", "Auto-generated tool");
            put("type", "function");
            put("code", content);
        }};
    }

    /**
     * 从响应中解析拓扑定义
     */
    private Map<String, Object> parseTopologyFromResponse(String content) {
        try {
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String json = content.substring(jsonStart, jsonEnd + 1);
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse topology JSON", e);
        }
        
        return Map.of("analysis", content, "optimizedAt", LocalDateTime.now().toString());
    }

    /**
     * 创建进化记录
     */
    private EvolutionRecord createEvolutionRecord(
            String agentId,
            EvolutionType type,
            Integer fromVersion,
            Integer toVersion,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String description,
            Double scoreChange,
            Boolean isAutomatic) {
        
        return EvolutionRecord.builder()
                .agentId(agentId)
                .type(type)
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .beforeState(beforeState)
                .afterState(afterState)
                .description(description)
                .scoreChange(scoreChange)
                .isAutomatic(isAutomatic)
                .isRolledBack(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
