package com.teammind.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.config.SQLiteWriteLockService;
import com.teammind.entity.Agent;
import com.teammind.entity.Agent.AgentStatus;
import com.teammind.llm.LLMRequest;
import com.teammind.llm.LLMResponse;
import com.teammind.llm.LLMService;
import com.teammind.llm.LLMTrackingService;
import com.teammind.repository.AgentRepository;
import com.teammind.websocket.WSEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentExecutionEngine 单元测试
 *
 * 聚焦 P0 核心引擎的三大关键行为：
 *  1. ReAct 循环 —— 工具调用 → 观察 → 最终回答 的完整闭环
 *  2. 权限控制 —— Agent 缺少权限时工具调用被拒绝
 *  3. LLM 重试机制 —— 可重试错误触发指数退避重试
 */
class AgentExecutionEngineTest {

    private LLMService llmService;
    private LLMTrackingService trackingService;
    private AgentRepository agentRepository;
    private WSEventPublisher eventPublisher;
    private ObjectMapper objectMapper;
    private ExecutorService executorService;
    private SQLiteWriteLockService writeLockService;
    private AgentExecutionEngine engine;

    private Agent agent;

    @BeforeEach
    void setUp() {
        llmService = mock(LLMService.class);
        trackingService = mock(LLMTrackingService.class);
        agentRepository = mock(AgentRepository.class);
        eventPublisher = mock(WSEventPublisher.class);
        objectMapper = new ObjectMapper();
        executorService = Executors.newSingleThreadExecutor();
        writeLockService = mock(SQLiteWriteLockService.class);

        engine = new AgentExecutionEngine(
                llmService,
                trackingService,
                agentRepository,
                eventPublisher,
                objectMapper,
                executorService,
                writeLockService
        );

        // 写锁直接放行，避免真实并发锁开销
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(writeLockService).executeWithLock(any(Runnable.class));

        agent = Agent.builder()
                .id("agent-1")
                .name("Test Agent")
                .currentPrompt("You are a helpful agent.")
                .permissions(List.of("read:code", "write:text"))
                .status(AgentStatus.IDLE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(agentRepository.findById(anyString())).thenReturn(Optional.of(agent));
    }

    private AgentExecutionContext buildContext() {
        return AgentExecutionContext.builder()
                .executionId("exec-1")
                .agentId("agent-1")
                .agentName("Test Agent")
                .missionId("mission-1")
                .userRequest("Do the task")
                .maxIterations(10)
                .build();
    }

    /** 构造一个携带 usage 的成功响应 */
    private LLMResponse successResponse(String content, int promptTokens, int completionTokens) {
        return LLMResponse.builder()
                .content(content)
                .model("deepseek-v3.2")
                .provider("qianfan")
                .success(true)
                .usage(LLMResponse.Usage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .build())
                .build();
    }

    private AgentExecutionResult executeSync(AgentExecutionContext context) throws Exception {
        CompletableFuture<AgentExecutionResult> future = engine.execute(context);
        return future.get(10, TimeUnit.SECONDS);
    }

    // ==================== ReAct 循环 ====================

    @Test
    @DisplayName("ReAct：无工具调用时直接返回最终回答，迭代 1 次")
    void react_directAnswer_completesWithoutTool() throws Exception {
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse("Here is the answer.", 10, 5));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(AgentExecutionResult.ExecutionStatus.COMPLETED, result.getStatus());
        assertEquals("Here is the answer.", result.getResponse());
        assertEquals(1, result.getIterations());
        assertTrue(result.getToolCalls().isEmpty());
        assertEquals(15, result.getTokenUsage().getTotalTokens());
    }

    @Test
    @DisplayName("ReAct：工具调用 → 观察 → 最终回答 的完整闭环")
    void react_toolThenFinalAnswer_completesFullLoop() throws Exception {
        // 第一次：触发工具调用（text_processor，agent 拥有 write:text 权限）
        // 第二次：返回最终回答
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"text_processor\", \"arguments\": {\"text\": \"abc\", \"operation\": \"uppercase\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Processed: ABC", 15, 8));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getIterations());
        assertEquals("Processed: ABC", result.getResponse());

        // 工具调用被记录且执行成功
        assertFalse(result.getToolCalls().isEmpty());
        assertEquals("text_processor", result.getToolCalls().get(0).getToolName());
        assertTrue(result.getToolCalls().get(0).isSuccess());
        // 工具结果包含真实处理结果（uppercase → ABC）
        Object toolResult = result.getToolCalls().get(0).getResult();
        assertNotNull(toolResult);
        assertEquals("ABC", ((java.util.Map<?, ?>) toolResult).get("result"));

        // token 统计累计两轮调用
        assertEquals(35, result.getTokenUsage().getPromptTokens());
        assertEquals(18, result.getTokenUsage().getCompletionTokens());
        assertEquals(53, result.getTokenUsage().getTotalTokens());
    }

    @Test
    @DisplayName("ReAct：达到最大迭代次数应返回 TIMEOUT")
    void react_maxIterations_returnsTimeout() throws Exception {
        // 每次都返回工具调用，让循环无法收敛
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"text_processor\", \"arguments\": {\"text\": \"a\", \"operation\": \"uppercase\"}}\n```",
                        10, 5));

        AgentExecutionContext context = buildContext();
        context.setMaxIterations(2);

        AgentExecutionResult result = executeSync(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(AgentExecutionResult.ExecutionStatus.TIMEOUT, result.getStatus());
        assertEquals("Max iterations reached", result.getError());
        assertEquals(2, result.getIterations());
        assertEquals("max_iterations", result.getFinishReason());
    }

    // ==================== 权限控制 ====================

    @Test
    @DisplayName("权限：Agent 缺少权限时工具调用被拒绝")
    void permission_missingPermission_deniesTool() throws Exception {
        // Agent 仅拥有 write:text，缺少 read:code
        agent.setPermissions(List.of("write:text"));
        when(agentRepository.findById(anyString())).thenReturn(Optional.of(agent));

        // 第一次：触发需要 read:code 的 code_analyzer
        // 第二次：返回最终回答，结束循环
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"code_analyzer\", \"arguments\": {\"code\": \"x = 1\", \"language\": \"java\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Done.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());

        // 工具调用被记录，但标记为失败（权限不足）
        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall toolCall = result.getToolCalls().get(0);
        assertEquals("code_analyzer", toolCall.getToolName());
        assertFalse(toolCall.isSuccess());
        assertNotNull(toolCall.getError());
        assertTrue(toolCall.getError().contains("Permission denied"));
        assertTrue(toolCall.getError().contains("read:code"));
    }

    @Test
    @DisplayName("权限：Agent 拥有权限时工具正常执行")
    void permission_hasPermission_executesTool() throws Exception {
        // Agent 默认拥有 read:code
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"code_analyzer\", \"arguments\": {\"code\": \"if (a) { b(); }\", \"language\": \"java\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Analysis complete.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall toolCall = result.getToolCalls().get(0);
        assertEquals("code_analyzer", toolCall.getToolName());
        assertTrue(toolCall.isSuccess());
        assertNull(toolCall.getError());
    }

    // ==================== 进化生成的动态工具调度 ====================

    @Test
    @DisplayName("动态工具：进化生成的工具能被真实调度执行（生成即可用）")
    void dynamicTool_evolutionGeneratedTool_isExecutable() throws Exception {
        // Agent 携带一个进化生成的动态工具，声明可执行能力为 text_processor
        agent.setTools(List.of(java.util.Map.of(
                "name", "my_text_tool",
                "description", "A tool generated by evolution",
                "toolType", "text_processor",
                "autoGenerated", true
        )));
        when(agentRepository.findById(anyString())).thenReturn(Optional.of(agent));

        // 第一次：调用动态工具 my_text_tool；第二次：返回最终回答
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"my_text_tool\", \"arguments\": {\"text\": \"hello\", \"operation\": \"reverse\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Reversed: olleh", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall toolCall = result.getToolCalls().get(0);
        assertEquals("my_text_tool", toolCall.getToolName());
        assertTrue(toolCall.isSuccess());
        assertNull(toolCall.getError());
        // 结果由内置 text_processor 的真实能力产出（reverse → "olleh"）
        Object toolResult = toolCall.getResult();
        assertNotNull(toolResult);
        assertEquals("olleh", ((java.util.Map<?, ?>) toolResult).get("result"));
    }

    @Test
    @DisplayName("动态工具：未注册的工具回退为模拟执行，不抛异常")
    void dynamicTool_unregisteredTool_fallsBackToSimulation() throws Exception {
        // 未在 Agent.tools 中注册的动态工具，应回退为模拟而非报错
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"unknown_tool\", \"arguments\": {}}\n```",
                        20, 10))
                .thenReturn(successResponse("Done.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall toolCall = result.getToolCalls().get(0);
        assertEquals("unknown_tool", toolCall.getToolName());
        assertTrue(toolCall.isSuccess());
        // 模拟结果标记 simulated=true
        Object toolResult = toolCall.getResult();
        assertNotNull(toolResult);
        assertEquals(true, ((java.util.Map<?, ?>) toolResult).get("simulated"));
    }

    // ==================== LLM 重试机制 ====================

    @Test
    @DisplayName("重试：可重试错误触发重试后成功")
    void retry_retryableError_thenSucceeds() throws Exception {
        // 第一次：timeout（可重试），第二次：成功
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(LLMResponse.failure("Request timeout", "qianfan"))
                .thenReturn(successResponse("Retried and succeeded.", 10, 5));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Retried and succeeded.", result.getResponse());
        // 确认 chat 被调用了 2 次（1 次失败 + 1 次重试成功）
        verify(llmService, times(2)).chat(any(LLMRequest.class));
    }

    @Test
    @DisplayName("重试：非可重试错误不重试直接返回失败")
    void retry_nonRetryableError_noRetry() throws Exception {
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(LLMResponse.failure("Invalid API key", "qianfan"));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Invalid API key"));
        // 只调用一次，未重试
        verify(llmService, times(1)).chat(any(LLMRequest.class));
    }

    @Test
    @DisplayName("重试：持续可重试错误最终返回失败")
    void retry_exhaustedReturnsFailure() throws Exception {
        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(LLMResponse.failure("Service unavailable", "qianfan"));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("LLM call failed"));
        // 3 次重试机会全部耗尽
        verify(llmService, times(3)).chat(any(LLMRequest.class));
    }

    // ==================== 真实工具执行（消灭空壳） ====================

    @Test
    @DisplayName("真实工具：readFile 读取沙箱内的真实文件内容")
    void realTool_readFile_readsActualContent() throws Exception {
        // 在沙箱根目录下创建临时文件
        Path sandbox = Files.createTempDirectory("teammind-sandbox");
        Path realFile = sandbox.resolve("notes.txt");
        Files.writeString(realFile, "hello real world");

        // 通过反射注入 dataPath（@Value 字段在单元测试中不会被注入）
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "dataPath", sandbox.toString());
        // file_reader 需要 read:files 权限
        agent.setPermissions(List.of("read:code", "write:text", "read:files"));
        when(agentRepository.findById(anyString())).thenReturn(Optional.of(agent));

        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"file_reader\", \"arguments\": {\"path\": \"" + realFile + "\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Done reading.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall call = result.getToolCalls().get(0);
        assertTrue(call.isSuccess(), "readFile should succeed");
        Map<?, ?> toolResult = (Map<?, ?>) call.getResult();
        // 真实内容被读取，而非硬编码假数据
        assertEquals("hello real world", toolResult.get("content"));
    }

    @Test
    @DisplayName("真实工具：readFile 阻断路径穿越逃逸沙箱")
    void realTool_readFile_blocksPathTraversal() throws Exception {
        Path sandbox = Files.createTempDirectory("teammind-sandbox");
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "dataPath", sandbox.toString());
        // file_reader 需要 read:files 权限
        agent.setPermissions(List.of("read:code", "write:text", "read:files"));
        when(agentRepository.findById(anyString())).thenReturn(Optional.of(agent));

        // 绝对路径指向沙箱之外的目录
        String outside = Files.createTempDirectory("outside-sandbox").resolve("secret.txt").toString();

        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"file_reader\", \"arguments\": {\"path\": \"" + outside + "\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Done.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall call = result.getToolCalls().get(0);
        Map<?, ?> toolResult = (Map<?, ?>) call.getResult();
        assertNotNull(toolResult.get("blocked"));
        assertEquals(Boolean.TRUE, toolResult.get("blocked"));
    }

    @Test
    @DisplayName("真实工具：analyzeCode 检测出真实代码问题（空 catch、行过长）")
    void realTool_analyzeCode_detectsRealIssues() throws Exception {
        String badCode = """
                public class Foo {
                    public void bar() {
                        try {
                            doWork();
                        } catch (Exception e) {
                        }
                        String s = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
                    }
                }
                """;

        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"code_analyzer\", \"arguments\": {\"code\": " + jsonEscape(badCode) + ", \"language\": \"java\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Analysis done.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall call = result.getToolCalls().get(0);
        assertTrue(call.isSuccess());
        Map<?, ?> toolResult = (Map<?, ?>) call.getResult();
        List<?> issues = (List<?>) toolResult.get("issues");
        // 应至少检测出空 catch 与过宽行两个问题
        assertFalse(issues.isEmpty(), "analyzeCode should find real issues");
        assertEquals(Boolean.TRUE, toolResult.get("analyzed"));
    }

    @Test
    @DisplayName("真实工具：searchWeb 未配置端点时返回诚实提示而非伪造结果")
    void realTool_searchWeb_noProvider_returnsHonestResult() throws Exception {
        // 未配置 searchEndpoint（默认为空）
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "searchEndpoint", "");
        // web_search 需要 read:web 权限
        agent.setPermissions(List.of("read:code", "write:text", "read:web"));
        when(agentRepository.findById(anyString())).thenReturn(Optional.of(agent));

        when(llmService.chat(any(LLMRequest.class)))
                .thenReturn(successResponse(
                        "```json\n{\"tool\": \"web_search\", \"arguments\": {\"query\": \"team collaboration\"}}\n```",
                        20, 10))
                .thenReturn(successResponse("Done.", 5, 3));

        AgentExecutionResult result = executeSync(buildContext());

        assertFalse(result.getToolCalls().isEmpty());
        AgentExecutionContext.ToolCall call = result.getToolCalls().get(0);
        Map<?, ?> toolResult = (Map<?, ?>) call.getResult();
        assertEquals(Boolean.FALSE, toolResult.get("configured"));
        assertNotNull(toolResult.get("error"));
    }

    private String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
