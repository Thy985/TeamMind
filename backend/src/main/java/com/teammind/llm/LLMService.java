package com.teammind.llm;

import com.teammind.llm.LLMRequest.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 服务
 * 
 * 提供高级 LLM 功能封装，支持多种提供商
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final List<LLMClient> clients;
    
    @Value("${teammind.llm.default-provider:qianfan}")
    private String defaultProvider;
    
    @Value("${teammind.llm.default-model:deepseek-v3.2}")
    private String defaultModel;

    /**
     * 获取客户端
     */
    public Optional<LLMClient> getClient(String provider) {
        return clients.stream()
                .filter(c -> c.getProvider().equalsIgnoreCase(provider))
                .filter(LLMClient::isAvailable)
                .findFirst();
    }

    /**
     * 获取默认客户端
     */
    public Optional<LLMClient> getDefaultClient() {
        // 先尝试指定的默认提供商
        Optional<LLMClient> client = getClient(defaultProvider);
        if (client.isPresent()) {
            return client;
        }
        
        // 回退到第一个可用的客户端
        return clients.stream()
                .filter(LLMClient::isAvailable)
                .findFirst();
    }

    /**
     * 发送聊天请求
     */
    public LLMResponse chat(LLMRequest request) {
        Optional<LLMClient> client = getDefaultClient();
        if (client.isEmpty()) {
            return LLMResponse.failure("No LLM client available", "none");
        }
        
        if (request.getModel() == null) {
            request.setModel(defaultModel);
        }
        
        return client.get().chat(request);
    }

    /**
     * 发送简单提示
     */
    public LLMResponse chat(String prompt) {
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(Message.user(prompt)))
                .build());
    }

    /**
     * 发送带系统提示的请求
     */
    public LLMResponse chat(String systemPrompt, String userPrompt) {
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user(userPrompt)
                ))
                .build());
    }

    /**
     * 多轮对话
     */
    public LLMResponse chat(List<Message> messages) {
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(messages)
                .build());
    }

    /**
     * 使用指定提供商发送请求
     */
    public LLMResponse chat(String provider, LLMRequest request) {
        Optional<LLMClient> client = getClient(provider);
        if (client.isEmpty()) {
            return LLMResponse.failure("LLM client not available: " + provider, provider);
        }
        return client.get().chat(request);
    }

    // ==================== Agent 专用方法 ====================

    /**
     * Agent 执行任务
     */
    public LLMResponse executeAgentTask(String agentPrompt, String task, Map<String, Object> context) {
        String systemPrompt = buildAgentSystemPrompt(agentPrompt, context);
        
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user(task)
                ))
                .temperature(0.3)  // Agent 任务使用较低温度
                .build());
    }

    /**
     * 优化 Prompt
     */
    public LLMResponse optimizePrompt(String currentPrompt, String feedback) {
        String systemPrompt = """
                You are an expert prompt engineer. Your task is to improve the given prompt based on feedback.
                
                Rules:
                1. Keep the core intent of the original prompt
                2. Add clarity and specificity
                3. Include error handling instructions
                4. Add output format specifications
                5. Make it more robust and effective
                
                Return ONLY the optimized prompt, nothing else.
                """;
        
        String userPrompt = String.format("""
                Current Prompt:
                ```
                %s
                ```
                
                Feedback for improvement:
                %s
                
                Please provide an optimized version of this prompt.
                """, currentPrompt, feedback);
        
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user(userPrompt)
                ))
                .temperature(0.5)
                .build());
    }

    /**
     * 生成工具代码
     */
    public LLMResponse generateTool(String description, String language) {
        String systemPrompt = String.format("""
                You are an expert %s developer. Generate a tool/function based on the description.
                
                Requirements:
                1. Clean, well-documented code
                2. Proper error handling
                3. Input validation
                4. Return the tool in the following JSON format:
                {
                  "name": "tool_name",
                  "description": "Tool description",
                  "parameters": { ... },
                  "code": "the actual code"
                }
                """, language);
        
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user("Generate a tool for: " + description)
                ))
                .temperature(0.3)
                .build());
    }

    /**
     * 分析协作拓扑
     */
    public LLMResponse analyzeTopology(String currentTopology, String performanceData) {
        String systemPrompt = """
                You are an expert in multi-agent systems and workflow optimization.
                Analyze the current agent collaboration topology and suggest improvements.
                
                Return your analysis as JSON:
                {
                  "analysis": "Current state analysis",
                  "bottlenecks": ["list of issues"],
                  "suggestions": ["list of improvements"],
                  "optimized_topology": { ... }
                }
                """;
        
        String userPrompt = String.format("""
                Current Topology:
                %s
                
                Performance Data:
                %s
                
                Analyze and suggest optimizations.
                """, currentTopology, performanceData);
        
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user(userPrompt)
                ))
                .temperature(0.4)
                .build());
    }

    /**
     * 总结文本
     */
    public LLMResponse summarize(String text, int maxLength) {
        String systemPrompt = String.format("""
                You are a summarization expert. Summarize the given text in at most %d words.
                Focus on the key points and actionable information.
                """, maxLength);
        
        return chat(LLMRequest.builder()
                .model(defaultModel)
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user(text)
                ))
                .maxTokens(maxLength * 2)
                .build());
    }

    /**
     * 构建 Agent 系统提示
     */
    private String buildAgentSystemPrompt(String agentPrompt, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder(agentPrompt);
        
        if (context != null && !context.isEmpty()) {
            sb.append("\n\n## Context\n");
            context.forEach((key, value) -> {
                sb.append("- ").append(key).append(": ").append(value).append("\n");
            });
        }
        
        return sb.toString();
    }

    // ==================== 状态检查 ====================

    /**
     * 检查是否有可用的 LLM
     */
    public boolean hasAvailableClient() {
        return clients.stream().anyMatch(LLMClient::isAvailable);
    }

    /**
     * 获取所有可用提供商
     */
    public List<String> getAvailableProviders() {
        return clients.stream()
                .filter(LLMClient::isAvailable)
                .map(LLMClient::getProvider)
                .toList();
    }

    /**
     * 获取 LLM 状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("defaultProvider", defaultProvider);
        status.put("defaultModel", defaultModel);
        status.put("hasAvailableClient", hasAvailableClient());
        
        List<Map<String, Object>> clientStatuses = new ArrayList<>();
        for (LLMClient client : clients) {
            Map<String, Object> clientStatus = new LinkedHashMap<>();
            clientStatus.put("provider", client.getProvider());
            clientStatus.put("available", client.isAvailable());
            clientStatus.put("models", client.listModels());
            clientStatuses.add(clientStatus);
        }
        status.put("clients", clientStatuses);
        
        return status;
    }
}
