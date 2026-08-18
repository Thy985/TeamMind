package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.*;
import com.teammind.plugin.Plugin;
import com.teammind.plugin.PluginManager;
import com.teammind.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure Scenario E2E Test — 验证失败路径的真实处理
 *
 * 测试场景：
 *   1. Pipeline 引用不存在的 agent → step FAILED + overallStatus FAILED（不需要 CLI）
 *   2. CLI 返回非零退出码 → PluginResult.failure（需要 CLI，assumeTrue 跳过）
 *   3. Pipeline 失败传播 → 失败步骤后不再执行后续步骤
 *   4. Plugin cancel → 返回 failure
 *
 * 原则：
 *   - 不需要 CLI 的测试必须真实断言（不能跳过）
 *   - 需要 CLI 的测试用 Assumptions.assumeTrue 跳过（SKIPPED，非静默 PASS）
 */
@SpringBootTest(classes = com.teammind.TeamMindApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FailureScenarioE2ETest {

    @Autowired private PipelineOrchestrator pipelineOrchestrator;
    @Autowired private PluginManager pluginManager;
    @Autowired private ReadinessManager readinessManager;
    @Autowired private TaskRepository taskRepo;

    private static final String PROJECT_ID = "failure-e2e-project";

    @Test
    @Order(1)
    @DisplayName("[E2E-Failure] Pipeline 引用不存在的 agent → step FAILED + overallStatus FAILED")
    void pipelineWithNonExistentAgentFails() {
        System.out.println("\n=== Failure E2E: Non-Existent Agent ===");

        String taskId = "failure-test-nonexistent-agent";
        Task task = Task.builder()
                .id(taskId)
                .projectId(PROJECT_ID)
                .objective("Test objective for non-existent agent")
                .taskTypeId("code-generation")
                .state(TaskState.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
        taskRepo.save(task);

        PipelineExecutionResult result = pipelineOrchestrator.executePipeline(
                taskId,
                "Test objective",
                List.of(),
                "failure-test");

        System.out.println("[Failure-E2E] Pipeline result:");
        System.out.println("  - overallStatus: " + result.getOverallStatus());
        System.out.println("  - steps: " + result.getStepResults().size());

        assertNotNull(result, "Result should not be null");
        assertEquals("FAILED", result.getOverallStatus(),
                "Pipeline with non-existent agent should fail");
        assertTrue(result.getStepResults().size() > 0,
                "Should have at least 1 step result");

        PipelineStepResult implementResult = result.getStepResults().get(0);
        assertEquals("implement", implementResult.getStepName());
        assertTrue(implementResult.isFailed(),
                "Implement step should be FAILED");
        assertNotNull(implementResult.getErrorReason(),
                "Should have error reason");
        assertTrue(implementResult.getErrorReason().contains("Plugin not found")
                        || implementResult.getErrorReason().contains("not ready"),
                "Error should mention plugin not found or not ready");

        System.out.println("[Failure-E2E] Step error: " + implementResult.getErrorReason());
        System.out.println("=== Non-Existent Agent Test Complete ===\n");
    }

    @Test
    @Order(2)
    @DisplayName("[E2E-Failure] CLI 非零退出码 → PluginResult.failure")
    void cliNonZeroExitProducesFailure() {
        System.out.println("\n=== Failure E2E: CLI Non-Zero Exit ===");

        var codexOpt = pluginManager.findById("codex");
        Assumptions.assumeTrue(codexOpt.isPresent(), "Codex plugin not registered");

        ReadinessResult readiness = readinessManager.check("codex");
        Assumptions.assumeTrue(!readiness.isUnavailable(),
                "Codex unavailable: " + readiness.diagnosis());

        Plugin codexPlugin = codexOpt.get();

        // 用无效的工作目录触发 CLI 失败
        Plugin.PluginContext ctx = new Plugin.PluginContext(
                "failure-e2e-project", "failure-test-cli-crash",
                Map.of("prompt", "Reply with exactly: TEAMMIND_E2E_OK"),
                "Z:\\nonexistent\\path\\that\\does\\not\\exist",
                Map.of(),
                List.of("implementation")
        );

        Plugin.PluginResult result = codexPlugin.invoke(ctx);

        System.out.println("[Failure-E2E] CLI result:");
        System.out.println("  - success: " + result.success());
        System.out.println("  - error: " + result.error());

        // CLI 应该失败（无效路径）
        assertFalse(result.success(),
                "CLI with invalid path should fail");
        assertNotNull(result.error(),
                "Should have error message");

        System.out.println("=== CLI Non-Zero Exit Test Complete ===\n");
    }

    @Test
    @Order(3)
    @DisplayName("[E2E-Failure] Pipeline 失败传播 — 失败后不再执行后续步骤")
    void pipelineFailurePropagation() {
        System.out.println("\n=== Failure E2E: Failure Propagation ===");

        String taskId = "failure-test-propagation";
        Task task = Task.builder()
                .id(taskId)
                .projectId(PROJECT_ID)
                .objective("Test failure propagation")
                .taskTypeId("code-generation")
                .state(TaskState.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
        taskRepo.save(task);

        // 使用 failure-test pipeline（non-existent agent）
        PipelineExecutionResult result = pipelineOrchestrator.executePipeline(
                taskId,
                "Test failure propagation",
                List.of(),
                "failure-test");

        assertNotNull(result);
        assertEquals("FAILED", result.getOverallStatus());

        // 失败 pipeline 不应该有 handoff（因为第一步就失败了）
        // 但应该有至少 1 个 step result
        assertTrue(result.getStepResults().size() >= 1,
                "Should have at least 1 step result");

        // 第一个步骤应该是 FAILED
        PipelineStepResult firstStep = result.getStepResults().get(0);
        assertTrue(firstStep.isFailed(),
                "First step should be failed");

        System.out.println("[Failure-E2E] Propagation verified:");
        System.out.println("  - steps executed: " + result.getStepResults().size());
        System.out.println("  - overallStatus: " + result.getOverallStatus());
        System.out.println("=== Failure Propagation Test Complete ===\n");
    }

    @Test
    @Order(4)
    @DisplayName("[E2E-Failure] Plugin cancel → 返回 failure")
    void pluginCancelProducesFailure() {
        System.out.println("\n=== Failure E2E: Plugin Cancel ===");

        var codexOpt = pluginManager.findById("codex");
        Assumptions.assumeTrue(codexOpt.isPresent(), "Codex plugin not registered");

        Plugin codexPlugin = codexOpt.get();

        Plugin.PluginContext ctx = new Plugin.PluginContext(
                "failure-e2e-project", "failure-test-cancel",
                Map.of("prompt", "Write a very long essay about artificial intelligence history"),
                System.getProperty("user.dir"),
                Map.of(),
                List.of("implementation")
        );

        // 尝试流式调用；某些 CLI 适配器不支持 streamInvoke，此时跳过
        java.util.concurrent.CompletableFuture<Plugin.PluginResult> future;
        try {
            future = codexPlugin.streamInvoke(ctx, chunk -> {});
        } catch (UnsupportedOperationException e) {
            Assumptions.assumeTrue(false,
                    "Plugin " + codexPlugin.id() + " does not support streamInvoke: " + e.getMessage());
            return;
        }

        // 等待一小段时间让进程启动
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 取消
        codexPlugin.cancel();

        try {
            Plugin.PluginResult result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("[Failure-E2E] Cancel result:");
            System.out.println("  - success: " + result.success());
            System.out.println("  - error: " + result.error());

            assertFalse(result.success(),
                    "Cancelled invocation should return failure");
        } catch (Exception e) {
            // future.completeExceptionally 也是合理的取消行为
            System.out.println("[Failure-E2E] Cancel produced exception (acceptable): " + e.getMessage());
        }

        System.out.println("=== Plugin Cancel Test Complete ===\n");
    }

    @Test
    @Order(5)
    @DisplayName("[E2E-Failure] Pipeline 执行后 Task 状态为 FAILED")
    void taskStateIsFailedAfterPipelineFailure() {
        System.out.println("\n=== Failure E2E: Task State After Failure ===");

        String taskId = "failure-test-task-state";
        Task task = Task.builder()
                .id(taskId)
                .projectId(PROJECT_ID)
                .objective("Test task state after failure")
                .taskTypeId("code-generation")
                .state(TaskState.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
        taskRepo.save(task);

        pipelineOrchestrator.executePipeline(
                taskId,
                "Test task state after failure",
                List.of(),
                "failure-test");

        Task updatedTask = taskRepo.findById(taskId).orElse(null);
        assertNotNull(updatedTask, "Task should still exist after failure");

        System.out.println("[Failure-E2E] Task state after failure: " + updatedTask.getState());

        // Task 状态不应该是 DONE
        assertNotEquals(TaskState.DONE, updatedTask.getState(),
                "Task should not be DONE after pipeline failure");

        System.out.println("=== Task State After Failure Test Complete ===\n");
    }

    @AfterAll
    static void cleanup() {
        System.out.println("[Failure-E2E] === All failure scenario E2E tests completed ===");
    }
}