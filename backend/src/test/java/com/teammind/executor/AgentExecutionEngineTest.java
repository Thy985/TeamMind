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
}
