package com.teammind.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.entity.Agent;
import com.teammind.llm.*;
import com.teammind.repository.AgentRepository;
import com.teammind.common.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Agent 执行引擎
 * 
 * 核心组件：负责执行 Agent 任务，处理工具调用，管理执行状态
 */
@Slf4j
@Component
public class AgentExecutionEngine {

    private final LLMService llmService;
    private final LLMTrackingService trackingService;
    private final AgentRepository agentRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final SQLiteWriteLockService writeLockService;

    // 执行缓存
    private final Map<String, AgentExecutionContext> activeContexts = new ConcurrentHashMap<>();

    /**
     * 文件读取沙箱根目录（配置 teammind.data-path），防止路径穿越逃逸
     */
    @Value("${teammind.data-path:${user.home}/.teammind}")
    private String dataPath;

    /**
     * 网络搜索提供商端点（可配置，未配置时工具返回诚实提示而非伪造结果）
     */
    @Value("${teammind.tools.search-endpoint:}")
    private String searchEndpoint;

    /**
     * 网络搜索超时（毫秒）
     */
    @Value("${teammind.tools.search-timeout-ms:5000}")
    private long searchTimeoutMs;

    /**
     * 构造函数 - 注入统一的有界线程池
     */
    public AgentExecutionEngine(
            LLMService llmService,
            LLMTrackingService trackingService,
            AgentRepository agentRepository,
            EventPublisher eventPublisher,
            ObjectMapper objectMapper,
            @Qualifier("agentExecutorService") ExecutorService executorService,
            SQLiteWriteLockService writeLockService) {
        this.llmService = llmService;
        this.trackingService = trackingService;
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        this.writeLockService = writeLockService;
    }

    /**
     * 执行 Agent 任务
     */
    public CompletableFuture<AgentExecutionResult> execute(AgentExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> doExecute(context), executorService);
    }

    /**
     * 同步执行
     */
    private AgentExecutionResult doExecute(AgentExecutionContext context) {
        String executionId = context.getExecutionId();
        long startTime = System.currentTimeMillis();

        log.info("Starting agent execution: agent={}, execution={}", 
                context.getAgentId(), executionId);

        // 注册上下文
        activeContexts.put(executionId, context);

        try {
            // 获取 Agent
            Agent agent = agentRepository.findById(context.getAgentId())
                    .orElseThrow(() -> new RuntimeException("Agent not found: " + context.getAgentId()));

            // 更新状态为运行中
            updateAgentStatus(agent, Agent.AgentStatus.RUNNING);
            publishStatusUpdate(context, "running");

            // 构建初始消息
            List<LLMRequest.Message> messages = buildMessages(agent, context);

            // 执行循环
            AgentExecutionResult result = executeLoop(agent, context, messages);

            // 更新最终状态
            if (result.isSuccess()) {
                updateAgentStatus(agent, Agent.AgentStatus.SUCCESS);
            } else {
                updateAgentStatus(agent, Agent.AgentStatus.ERROR);
            }

            // 记录执行时间
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            log.info("Agent execution completed: agent={}, success={}, time={}ms",
                    context.getAgentId(), result.isSuccess(), result.getExecutionTimeMs());

            return result;

        } catch (Exception e) {
            log.error("Agent execution failed: agent={}", context.getAgentId(), e);

            AgentExecutionResult result = AgentExecutionResult.failure(
                    executionId, 
                    context.getAgentId(),
                    "Execution failed: " + e.getMessage()
            );
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            // 更新状态
            try {
                Agent agent = agentRepository.findById(context.getAgentId()).orElse(null);
                if (agent != null) {
                    updateAgentStatus(agent, Agent.AgentStatus.ERROR);
                }
            } catch (Exception ignored) {}

            return result;

        } finally {
            activeContexts.remove(executionId);
        }
    }

    /**
     * 执行循环 - ReAct 模式
     */
    private AgentExecutionResult executeLoop(Agent agent, AgentExecutionContext context, 
                                             List<LLMRequest.Message> messages) {
        String executionId = context.getExecutionId();
        List<AgentExecutionContext.ToolCall> toolCalls = new ArrayList<>();
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;

        while (context.getCurrentIteration() < context.getMaxIterations()) {
            context.setCurrentIteration(context.getCurrentIteration() + 1);

            log.debug("Agent iteration {}/{}: agent={}", 
                    context.getCurrentIteration(), context.getMaxIterations(), agent.getId());

            // 调用 LLM
            LLMRequest request = LLMRequest.builder()
                    .model(null)  // 使用默认模型
                    .messages(messages)
                    .temperature(0.3)
                    .build();

            // ✅ 修复：添加 LLM 重试机制
            LLMResponse response = chatWithRetry(request, 3);

            // 记录调用
            trackingService.recordCall(request, response, "agent_task", 
                    agent.getId(), context.getMissionId());

            if (!response.isSuccess()) {
                return AgentExecutionResult.failure(executionId, agent.getId(), 
                        "LLM call failed: " + response.getError());
            }

            // 统计 Token
            if (response.getUsage() != null) {
                totalPromptTokens += response.getUsage().getPromptTokens();
                totalCompletionTokens += response.getUsage().getCompletionTokens();
            }

            String content = response.getContent();

            // 检查是否需要工具调用
            List<ToolCallRequest> toolRequests = parseToolCalls(content);

            if (toolRequests.isEmpty()) {
                // 没有工具调用，返回最终结果
                return AgentExecutionResult.builder()
                        .executionId(executionId)
                        .agentId(agent.getId())
                        .success(true)
                        .status(AgentExecutionResult.ExecutionStatus.COMPLETED)
                        .response(content)
                        .toolCalls(toolCalls)
                        .iterations(context.getCurrentIteration())
                        .tokenUsage(AgentExecutionResult.TokenUsage.builder()
                                .promptTokens(totalPromptTokens)
                                .completionTokens(totalCompletionTokens)
                                .totalTokens(totalPromptTokens + totalCompletionTokens)
                                .build())
                        .finishReason("completed")
                        .completedAt(LocalDateTime.now())
                        .build();
            }

            // 执行工具调用
            for (ToolCallRequest toolRequest : toolRequests) {
                AgentExecutionContext.ToolCall toolCall = executeTool(toolRequest, context);
                toolCalls.add(toolCall);

                // 将工具结果添加到消息
                messages.add(LLMRequest.Message.assistant(content));
                try {
                    messages.add(LLMRequest.Message.user(
                            "Tool " + toolRequest.name + " result: " + 
                            objectMapper.writeValueAsString(toolCall.getResult())
                    ));
                } catch (Exception e) {
                    messages.add(LLMRequest.Message.user(
                            "Tool " + toolRequest.name + " result: " + toolCall.getResult()
                    ));
                }
            }
        }

        // 达到最大迭代次数
        return AgentExecutionResult.builder()
                .executionId(executionId)
                .agentId(agent.getId())
                .success(false)
                .status(AgentExecutionResult.ExecutionStatus.TIMEOUT)
                .error("Max iterations reached")
                .toolCalls(toolCalls)
                .iterations(context.getCurrentIteration())
                .finishReason("max_iterations")
                .completedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构建消息列表
     */
    private List<LLMRequest.Message> buildMessages(Agent agent, AgentExecutionContext context) {
        List<LLMRequest.Message> messages = new ArrayList<>();

        // 系统提示
        String systemPrompt = buildSystemPrompt(agent, context);
        messages.add(LLMRequest.Message.system(systemPrompt));

        // 用户请求
        if (context.getUserRequest() != null) {
            messages.add(LLMRequest.Message.user(context.getUserRequest()));
        }

        // 输入数据
        if (context.getInput() != null && !context.getInput().isEmpty()) {
            messages.add(LLMRequest.Message.user(
                    "Input data: " + toJson(context.getInput())
            ));
        }

        // 依赖数据
        if (context.getDependencies() != null && !context.getDependencies().isEmpty()) {
            messages.add(LLMRequest.Message.user(
                    "Context from other agents: " + toJson(context.getDependencies())
            ));
        }

        return messages;
    }

    /**
     * 构建系统提示
     */
    private String buildSystemPrompt(Agent agent, AgentExecutionContext context) {
        StringBuilder sb = new StringBuilder();

        // Agent 的主 Prompt
        if (agent.getCurrentPrompt() != null) {
            sb.append(agent.getCurrentPrompt()).append("\n\n");
        }

        // 添加工具说明
        if (agent.getTools() != null && !agent.getTools().isEmpty()) {
            sb.append("## Available Tools\n\n");
            for (Map<String, Object> tool : agent.getTools()) {
                sb.append("- **").append(tool.get("name")).append("**: ");
                sb.append(tool.get("description")).append("\n");
            }
            sb.append("\nTo use a tool, format your response as:\n");
            sb.append("```json\n{\"tool\": \"tool_name\", \"arguments\": {...}}\n```\n\n");
        }

        // 添加约束
        sb.append("## Constraints\n");
        sb.append("- Maximum iterations: ").append(context.getMaxIterations()).append("\n");
        sb.append("- Provide clear and concise responses\n");
        sb.append("- If you need more information, ask for it\n");

        return sb.toString();
    }

    /**
     * 解析工具调用请求
     */
    private List<ToolCallRequest> parseToolCalls(String content) {
        List<ToolCallRequest> requests = new ArrayList<>();

        // 简单的 JSON 解析
        try {
            // 查找 JSON 块
            int start = content.indexOf("```json");
            if (start >= 0) {
                int end = content.indexOf("```", start + 7);
                if (end > start) {
                    String json = content.substring(start + 7, end).trim();
                    Map<String, Object> parsed = objectMapper.readValue(json, 
                            new TypeReference<Map<String, Object>>() {});
                    
                    if (parsed.containsKey("tool")) {
                        requests.add(new ToolCallRequest(
                                (String) parsed.get("tool"),
                                (Map<String, Object>) parsed.get("arguments")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No tool calls found in response");
        }

        return requests;
    }

    /**
     * 执行工具
     */
    private AgentExecutionContext.ToolCall executeTool(ToolCallRequest request, 
                                                       AgentExecutionContext context) {
        log.info("Executing tool: {} for agent={}", request.name, context.getAgentId());

        AgentExecutionContext.ToolCall toolCall = AgentExecutionContext.ToolCall.builder()
                .toolName(request.name)
                .arguments(request.arguments)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            // ✅ 权限控制：Agent 必须拥有执行该工具所需的权限
            Agent agent = agentRepository.findById(context.getAgentId()).orElse(null);
            String requiredPermission = requiredPermissionForTool(request.name);
            if (agent != null && requiredPermission != null
                    && (agent.getPermissions() == null || !agent.getPermissions().contains(requiredPermission))) {
                String msg = "Permission denied: agent lacks permission '" + requiredPermission
                        + "' to execute tool '" + request.name + "'";
                log.warn("{}, agent={}", msg, agent.getId());
                toolCall.setSuccess(false);
                toolCall.setError(msg);
                toolCall.setResult(Map.of("error", msg, "permission", requiredPermission));
                return toolCall;
            }

            // ✅ 修复：实现真实工具而非模拟
            // 内置工具 + 进化生成的动态工具（生成即可用）
            Object result = executeRealTool(agent, request.name, request.arguments);
            toolCall.setResult(result);
            toolCall.setSuccess(true);

        } catch (Exception e) {
            log.error("Tool execution failed: {}", request.name, e);
            toolCall.setSuccess(false);
            toolCall.setError(e.getMessage());
            toolCall.setResult(Map.of("error", e.getMessage()));
        }

        return toolCall;
    }

    /**
     * ✅ Agent 权限控制：返回执行工具所需的最小权限
     */
    private String requiredPermissionForTool(String toolName) {
        switch (toolName.toLowerCase()) {
            case "code_analyzer":
                return "read:code";
            case "file_reader":
                return "read:files";
            case "web_search":
                return "read:web";
            case "text_processor":
                return "write:text";
            default:
                return null; // 未知工具不做强制权限限制
        }
    }

    /**
     * ✅ 新增：执行真实工具（内置工具 + 进化生成的动态工具）
     */
    private Object executeRealTool(Agent agent, String toolName, Map<String, Object> arguments) throws Exception {
        switch (toolName.toLowerCase()) {
            case "code_analyzer":
                return analyzeCode((String) arguments.get("code"), 
                                 (String) arguments.get("language"));
            
            case "text_processor":
                return processText((String) arguments.get("text"),
                                 (String) arguments.get("operation"));
            
            case "web_search":
                return searchWeb((String) arguments.get("query"));
            
            case "file_reader":
                return readFile((String) arguments.get("path"));
            
            default:
                // 尝试调度进化生成的动态工具（生成即可用）
                Object dynamic = executeDynamicTool(agent, toolName, arguments);
                if (dynamic != null) {
                    return dynamic;
                }
                // 未知工具：明确失败，不再模拟
                throw new UnsupportedOperationException(
                    "Unknown tool '" + toolName + "' — no built-in or dynamic implementation found. " +
                    "Simulate fallback has been removed to prevent false success.");
        }
    }

    /**
     * ✅ 新增：执行进化生成的动态工具
     *
     * 闭环：EvolutionEngine.generateTool 将 LLM 生成的工具写入 Agent.tools，
     * 这里在执行时从 Agent.tools 中按名称查找并调度。工具需声明其执行能力
     * （toolType），可复用内置的真实执行能力，实现"生成即可用"。
     *
     * @return 执行结果；若未找到或不可执行则返回 null
     */
    private Object executeDynamicTool(Agent agent, String toolName, Map<String, Object> arguments) {
        if (agent == null || agent.getTools() == null) {
            return null;
        }
        for (Map<String, Object> tool : agent.getTools()) {
            if (toolName.equalsIgnoreCase(String.valueOf(tool.get("name")))) {
                // 依据声明的能力类型委托给内置真实执行能力
                String toolType = String.valueOf(tool.get("toolType"));
                try {
                    switch (toolType.toLowerCase()) {
                        case "text_processor":
                            return processText(
                                    (String) arguments.get("text"),
                                    (String) arguments.get("operation"));
                        case "code_analyzer":
                            return analyzeCode(
                                    (String) arguments.get("code"),
                                    (String) arguments.get("language"));
                        case "web_search":
                            return searchWeb((String) arguments.get("query"));
                        case "file_reader":
                            return readFile((String) arguments.get("path"));
                        default:
                            log.warn("Dynamic tool '{}' has unsupported toolType '{}'", toolName, toolType);
                            return Map.of(
                                    "error", "Dynamic tool registered but toolType '" + toolType
                                            + "' is not executable by this runtime",
                                    "tool", toolName);
                    }
                } catch (Exception e) {
                    log.warn("Dynamic tool '{}' execution failed: {}", toolName, e.getMessage());
                    return Map.of("error", "Dynamic tool execution failed: " + e.getMessage(), "tool", toolName);
                }
            }
        }
        return null;
    }

    /**
     * 真实代码分析工具：扫描常见问题（过宽行、空 catch、TODO/FIXME、嵌套过深、
     * 危险调用、重复空白等），并基于真实发现计算质量分。
     */
    private Map<String, Object> analyzeCode(String code, String language) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        
        if (code == null) {
            code = "";
        }
        String[] codeLines = code.split("\n", -1);
        int lines = codeLines.length;
        int complexity = calculateComplexity(code);

        // 逐行扫描真实问题
        int lineIndex = 0;
        int nestingDepth = 0;
        int maxNesting = 0;
        boolean inBlockComment = false;
        for (String line : codeLines) {
            lineIndex++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 块注释状态机（简化：// 与 /* */）
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            int blockStart = trimmed.indexOf("/*");
            if (blockStart >= 0) {
                int blockEnd = trimmed.indexOf("*/", blockStart);
                if (blockEnd < 0) {
                    inBlockComment = true;
                    continue;
                }
            }
            String noComment = stripLineComment(trimmed);
            if (noComment.trim().isEmpty()) {
                continue;
            }

            // 1. 行过长（> 120 字符，非注释）
            if (line.length() > 120) {
                issues.add(issue("line_too_long", "Line exceeds 120 characters (" + line.length() + ")", lineIndex, "high"));
            }

            // 3. TODO / FIXME / HACK 标记
            if (Pattern.compile("(?i)\\b(todo|fixme|hack|xxx)\\b").matcher(noComment).find()) {
                issues.add(issue("todo_marker", "TODO/FIXME marker left in code", lineIndex, "info"));
            }

            // 4. 危险调用（不完整示例集，用于真实检测）
            for (String danger : DANGEROUS_CALLS) {
                if (noComment.contains(danger)) {
                    issues.add(issue("dangerous_call", "Potentially unsafe call: " + danger, lineIndex, "warning"));
                    break;
                }
            }

            // 5. 行尾空白
            if (line.length() > trimmed.length()) {
                issues.add(issue("trailing_whitespace", "Trailing whitespace at end of line", lineIndex, "low"));
            }

            // 6. 大括号缩进/嵌套深度统计（粗粒度）
            nestingDepth += countOccurrences(noComment, "{") - countOccurrences(noComment, "}");
            maxNesting = Math.max(maxNesting, nestingDepth);
        }

        // 2. 空 catch 块整体检测：catch (…) { …空… } 捕获空实现（可能跨行）
        Matcher emptyCatch = EMPTY_CATCH_MULTILINE.matcher(code);
        int emptyCatchCount = 0;
        while (emptyCatch.find()) {
            emptyCatchCount++;
            issues.add(issue("empty_catch", "Empty catch block silently swallows exceptions", -1, "high"));
        }
        // 7. 嵌套过深
        if (maxNesting > 5) {
            issues.add(issue("deep_nesting", "Maximum nesting depth of " + maxNesting + " exceeds recommended limit of 5", -1, "medium"));
        }

        // 8. 重复行（简单启发：同一非空行出现 >= 3 次）
        Map<String, Long> lineCounts = Arrays.stream(codeLines)
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .collect(Collectors.groupingBy(l -> l, Collectors.counting()));
        lineCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 3 && e.getKey().length() > 3)
                .limit(5)
                .forEach(e -> issues.add(issue("duplicated_line", "Duplicated line repeated " + e.getValue() + " times: '" + truncate(e.getKey(), 60) + "'", -1, "low")));

        // 真实质量分：满分 100，按发现的问题严重度扣分，复杂度单独计
        int issueDeduction = 0;
        for (Map<String, Object> it : issues) {
            String sev = String.valueOf(it.get("severity"));
            switch (sev) {
                case "high" -> issueDeduction += 15;
                case "warning" -> issueDeduction += 8;
                case "medium" -> issueDeduction += 5;
                default -> issueDeduction += 2; // low/info
            }
        }
        int qualityScore = Math.max(0, 100 - issueDeduction - Math.min(complexity, 20));

        result.put("issues", issues);
        result.put("issue_count", issues.size());
        result.put("empty_catch_count", emptyCatchCount);
        result.put("quality_score", qualityScore);
        result.put("complexity", complexity);
        result.put("max_nesting_depth", maxNesting);
        result.put("lines_of_code", lines);
        result.put("language", language);
        result.put("analyzed", true);
        
        return result;
    }

    /** 常见危险调用特征（用于真实静态检测） */
    private static final String[] DANGEROUS_CALLS = {
        "eval(", "exec(", "Runtime.getRuntime().exec",
        "ProcessBuilder", "child_process", "shell=True",
        "SELECT * FROM", "DROP TABLE", "DELETE FROM", "INSERT INTO"
    };

    /** 空 catch 跨行整体检测：catch (…) { 只含空白 } */
    private static final Pattern EMPTY_CATCH_MULTILINE =
            Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");

    private Map<String, Object> issue(String type, String message, int line, String severity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("message", message);
        if (line > 0) {
            m.put("line", line);
        }
        m.put("severity", severity);
        return m;
    }

    private String stripLineComment(String line) {
        int idx = line.indexOf("//");
        if (idx >= 0) {
            return line.substring(0, idx);
        }
        return line;
    }

    private String truncate(String s, int max) {
        if (s == null) return s;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /**
     * ✅ 新增：文本处理工具
     */
    private Map<String, Object> processText(String text, String operation) throws Exception {
        Map<String, Object> result = new HashMap<>();
        
        switch (operation.toLowerCase()) {
            case "uppercase":
                result.put("result", text.toUpperCase());
                break;
            case "lowercase":
                result.put("result", text.toLowerCase());
                break;
            case "reverse":
                result.put("result", new StringBuilder(text).reverse().toString());
                break;
            case "statistics":
                result.put("result", getTextStatistics(text));
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
        
        result.put("operation", operation);
        return result;
    }

    /**
     * 真实网络搜索工具：若配置了 teammind.tools.search-endpoint 则发起 HTTP 请求；
     * 否则返回诚实提示（未配置搜索服务），绝不返回伪造结果。
     */
    private Map<String, Object> searchWeb(String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("timestamp", LocalDateTime.now().toString());

        if (query == null || query.trim().isEmpty()) {
            result.put("error", "Empty search query");
            result.put("results", List.of());
            return result;
        }

        String endpoint = searchEndpoint == null ? "" : searchEndpoint.trim();
        if (endpoint.isEmpty()) {
            result.put("error", "No search provider configured (set teammind.tools.search-endpoint)");
            result.put("results", List.of());
            result.put("configured", false);
            return result;
        }

        try {
            WebClient client = WebClient.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                    .build();
            String uri = endpoint + (endpoint.contains("?") ? "&" : "?") + "q=" + urlEncode(query);
            String body = client.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(Math.max(searchTimeoutMs, 1000)));
            result.put("configured", true);
            result.put("raw", body);
            result.put("results", List.of(Map.of("source", "configured_provider", "content", truncate(body, 2000))));
        } catch (Exception e) {
            result.put("configured", true);
            result.put("error", "Search request failed: " + e.getMessage());
            result.put("results", List.of());
        }
        return result;
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 真实文件读取工具：仅在配置的沙箱根目录（teammind.data-path）内读取，
     * 防止路径穿越（../）逃逸到沙箱之外。
     */
    private Map<String, Object> readFile(String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested_path", path);

        if (path == null || path.trim().isEmpty()) {
            result.put("error", "Empty file path");
            return result;
        }

        try {
            Path sandboxRoot = Paths.get(dataPath == null || dataPath.trim().isEmpty()
                    ? System.getProperty("user.home") + "/.teammind" : dataPath)
                    .toAbsolutePath().normalize();
            Path target = Paths.get(path).toAbsolutePath().normalize();

            // 关键：拒绝任何逃逸沙箱根目录的路径
            if (!target.startsWith(sandboxRoot)) {
                result.put("error", "Path is outside the sandbox root and was blocked: " + sandboxRoot);
                result.put("blocked", true);
                return result;
            }

            if (!Files.exists(target)) {
                result.put("error", "File not found: " + path);
                return result;
            }
            if (Files.isDirectory(target)) {
                List<String> entries;
                try (var stream = Files.list(target)) {
                    entries = stream.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
                }
                result.put("is_directory", true);
                result.put("entries", entries);
                result.put("size", entries.size());
                return result;
            }

            String content = Files.readString(target, StandardCharsets.UTF_8);
            long size = Files.size(target);
            result.put("path", target.toString());
            result.put("content", content);
            result.put("size", size);
            result.put("lines", content.split("\n", -1).length);
        } catch (InvalidPathException e) {
            result.put("error", "Invalid path: " + path);
        } catch (IOException e) {
            result.put("error", "Failed to read file: " + e.getMessage());
        }
        return result;
    }

    /**
     * 计算代码复杂度
     */
    private int calculateComplexity(String code) {
        int complexity = 1;
        complexity += countOccurrences(code, "if");
        complexity += countOccurrences(code, "for");
        complexity += countOccurrences(code, "while");
        return Math.min(complexity, 50);
    }

    /**
     * 计算文本统计
     */
    private Map<String, Object> getTextStatistics(String text) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("length", text.length());
        stats.put("words", text.split("\\s+").length);
        stats.put("lines", text.split("\n").length);
        return stats;
    }

    /**
     * 计算字符串出现次数
     */
    private int countOccurrences(String text, String pattern) {
        return text.split(java.util.regex.Pattern.quote(pattern), -1).length - 1;
    }

    /**

     * 更新 Agent 状态（SQLite 写串行化）
     */
    private void updateAgentStatus(Agent agent, Agent.AgentStatus status) {
        try {
            // 重新从数据库获取Agent以避免合并冲突（写操作在锁内）
            writeLockService.executeWithLock(() -> {
                Agent freshAgent = agentRepository.findById(agent.getId()).orElse(null);
                if (freshAgent != null) {
                    freshAgent.setStatus(status);
                    freshAgent.setUpdatedAt(LocalDateTime.now());
                    agentRepository.save(freshAgent);
                    // 同步更新当前agent对象
                    agent.setStatus(status);
                    agent.setUpdatedAt(LocalDateTime.now());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to update agent status: agent={}, status={}, error={}", 
                    agent.getId(), status, e.getMessage());
            // 继续执行，不因为状态更新失败而中断
        }
    }

    /**
     * 发布状态更新
     */
    private void publishStatusUpdate(AgentExecutionContext context, String status) {
        eventPublisher.publishAgentStatusUpdate(
                context.getMissionId(),
                context.getAgentId(),
                status
        );
    }

    /**
     * 转换为 JSON
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /**
     * ✅ 新增：LLM 调用重试机制
     * 使用指数退避策略重试失败的 LLM 调用
     */
    private LLMResponse chatWithRetry(LLMRequest request, int maxRetries) {
        int retries = 0;
        long backoffMs = 1000;
        
        while (retries < maxRetries) {
            try {
                LLMResponse response = llmService.chat(request);
                
                if (response.isSuccess()) {
                    if (retries > 0) {
                        log.info("LLM call succeeded after {} retries", retries);
                    }
                    return response;
                }
                
                // 检查是否可重试的错误
                if (!isRetryableError(response.getError())) {
                    log.warn("LLM call failed with non-retryable error: {}", response.getError());
                    return response;
                }
                
                retries++;
                if (retries < maxRetries) {
                    log.warn("LLM call failed, retrying ({}/{}): {}", 
                        retries, maxRetries, response.getError());
                    
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return LLMResponse.failure("Interrupted during retry", "openai");
                    }
                    
                    // 指数退避：1s → 2s → 4s (最多 30s)
                    backoffMs = Math.min(backoffMs * 2, 30000);
                }
            } catch (Exception e) {
                log.error("LLM call exception: {}", e.getMessage());
                retries++;
                
                if (retries < maxRetries) {
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 30000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return LLMResponse.failure("Interrupted during retry", "openai");
                    }
                }
            }
        }
        
        return LLMResponse.failure("Max retries exceeded after " + maxRetries + " attempts", "openai");
    }

    /**
     * ✅ 新增：判断错误是否可重试
     */
    private boolean isRetryableError(String error) {
        if (error == null) return false;
        
        String lowerError = error.toLowerCase();
        return lowerError.contains("timeout") ||
               lowerError.contains("429") ||      // Rate limit
               lowerError.contains("503") ||      // Service unavailable
               lowerError.contains("502") ||      // Bad gateway
               lowerError.contains("connection") ||
               lowerError.contains("temporarily") ||
               lowerError.contains("unavailable");
    }

    /**
     * 工具调用请求
     */
    private record ToolCallRequest(String name, Map<String, Object> arguments) {}
}
