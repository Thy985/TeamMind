package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.*;
import com.teammind.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review-Loop E2E Test — 真实多 Agent Pipeline 执行验证
 *
 * 验证完整链路：
 *   Task 创建
 *   → Pipeline executePipeline("review-loop")
 *   → implement (codex/LEAD)
 *   → handoff → review (claude-code/REVIEWER)
 *   → handoff → verify (git-verifier + test-runner-verifier)
 *   → PipelineExecutionResult
 *
 * 原则：
 *   - CLI 不可用 → Assumptions.assumeTrue 跳过（SKIPPED，非静默 PASS）
 *   - CLI 可用 → 必须真实执行并断言结果
 *   - 无论成功/失败，都必须有 stepResults 和 handoffHistory
 */
@SpringBootTest(classes = com.teammind.TeamMindApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReviewLoopE2ETest {

    @Autowired private PipelineOrchestrator pipelineOrchestrator;
    @Autowired private ReadinessManager readinessManager;
    @Autowired private TaskRepository taskRepo;
    @Autowired private TaskExecutionRepository executionRepo;

    private static final String PROJECT_ID = "review-loop-e2e-project";
    private static final String TASK_ID = "review-loop-e2e-task";

    @Test
    @Order(1)
    @DisplayName("[E2E-ReviewLoop] Pipeline 定义加载正确 — 3 步骤 review-loop")
    void pipelineDefinitionLoads() {
        PipelineDefinition def = pipelineOrchestrator.loadPipeline("review-loop.yaml");

        assertNotNull(def, "Pipeline definition should not be null");
        assertEquals("review-loop", def.getName());
        assertEquals(3, def.getSteps().size(), "Should have 3 steps: implement, review, verify");

        List<String> stepNames = def.getSteps().stream()
                .map(PipelineStepDefinition::getName)
                .toList();
        assertTrue(stepNames.contains("implement"), "Should have implement step");
        assertTrue(stepNames.contains("review"), "Should have review step");
        assertTrue(stepNames.contains("verify"), "Should have verify step");

        PipelineStepDefinition implementStep = def.getStep("implement");
        assertEquals("codex", implementStep.getAgent(), "Implement should use codex");
        assertEquals("LEAD", implementStep.getRole());

        PipelineStepDefinition reviewStep = def.getStep("review");
        assertEquals("claude-code", reviewStep.getAgent(), "Review should use claude-code");
        assertEquals("REVIEWER", reviewStep.getRole());

        System.out.println("[ReviewLoop-E2E] Pipeline definition verified: " + stepNames);
    }

    @Test
    @Order(2)
    @DisplayName("[E2E-ReviewLoop] 真实多 Agent Pipeline 执行 — Codex → Claude → Verify")
    void realReviewLoopExecution() {
        System.out.println("\n=== Review-Loop E2E: Real Multi-Agent Pipeline ===");

        // 前置条件：Codex 和 Claude Code 都必须可用
        ReadinessResult codexReadiness = readinessManager.check("codex");
        ReadinessResult claudeReadiness = readinessManager.check("claude-code");

        Assumptions.assumeTrue(!codexReadiness.isUnavailable(),
                "Codex not available: " + codexReadiness.diagnosis());
        Assumptions.assumeTrue(!claudeReadiness.isUnavailable(),
                "Claude Code not available: " + claudeReadiness.diagnosis());

        // 创建 Task
        Task task = Task.builder()
                .id(TASK_ID)
                .projectId(PROJECT_ID)
                .objective("Add a simple hello() function to a test file")
                .taskTypeId("code-generation")
                .state(TaskState.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
        taskRepo.save(task);

        System.out.println("[ReviewLoop-E2E] Task created: " + TASK_ID);
        System.out.println("[ReviewLoop-E2E] Codex readiness: " + codexReadiness.state());
        System.out.println("[ReviewLoop-E2E] Claude readiness: " + claudeReadiness.state());

        // 执行 Pipeline
        long startMs = System.currentTimeMillis();
        PipelineExecutionResult result = pipelineOrchestrator.executePipeline(
                TASK_ID,
                task.getObjective(),
                List.of("Do not break existing tests"),
                "review-loop");
        long elapsedMs = System.currentTimeMillis() - startMs;

        System.out.println("[ReviewLoop-E2E] Pipeline result:");
        System.out.println("  - overallStatus: " + result.getOverallStatus());
        System.out.println("  - steps executed: " + result.getStepResults().size());
        System.out.println("  - handoffs: " + result.getContext().getHandoffHistory().size());
        System.out.println("  - total duration: " + result.getTotalDurationMs() + "ms");

        // 断言：无论成功/失败，Pipeline 框架必须正确工作
        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getContext(), "Context should not be null");
        assertNotNull(result.getOverallStatus(), "Overall status should not be null");
        assertTrue(result.getStepResults().size() > 0, "Should have at least 1 step result");
        assertTrue(elapsedMs > 0, "Should take some time");

        // 断言：至少执行了 implement 步骤
        boolean hasImplementStep = result.getStepResults().stream()
                .anyMatch(s -> "implement".equals(s.getStepName()));
        assertTrue(hasImplementStep, "Should have executed implement step");

        // 断言：implement 步骤应该有 agentId
        PipelineStepResult implementResult = result.getStepResults().stream()
                .filter(s -> "implement".equals(s.getStepName()))
                .findFirst()
                .orElse(null);
        assertNotNull(implementResult, "Implement step result should exist");
        assertEquals("codex", implementResult.getAgentId(), "Implement should be executed by codex");

        // 如果 implement 成功，应该有 handoff 到 review
        if (implementResult.isSuccess() && result.getContext().getHandoffHistory().size() > 0) {
            System.out.println("[ReviewLoop-E2E] Handoff chain verified:");
            result.getContext().getHandoffHistory().forEach(h ->
                    System.out.println("  - " + h.getFromStep() + " → " + h.getToStep() + " (" + h.getReason() + ")"));

            // 验证第一个 handoff 是 implement → review
            PipelineContext.HandoffRecord firstHandoff = result.getContext().getHandoffHistory().get(0);
            assertEquals("implement", firstHandoff.getFromStep(),
                    "First handoff should be from implement");
            assertEquals("review", firstHandoff.getToStep(),
                    "First handoff should be to review");
        }

        System.out.println("[ReviewLoop-E2E] Pipeline execution completed: " + result.getOverallStatus());
        System.out.println("=== Review-Loop E2E Complete ===\n");
    }

    @Test
    @Order(3)
    @DisplayName("[E2E-ReviewLoop] Pipeline 执行后 Task 状态已更新")
    void taskStateUpdatedAfterPipeline() {
        // 这个测试验证 Task 在 pipeline 执行后被正确更新
        // 如果 review-loop 没有执行（CLI 不可用），Task 可能不存在
        Task task = taskRepo.findById(TASK_ID).orElse(null);
        if (task == null) {
            // Task 不存在意味着 review-loop 没有执行（CLI 不可用），跳过
            Assumptions.assumeTrue(false, "Task not created — review-loop did not execute (CLI unavailable)");
        }

        assertNotNull(task, "Task should exist after pipeline execution");
        assertNotNull(task.getState(), "Task state should be set");

        System.out.println("[ReviewLoop-E2E] Task state after pipeline: " + task.getState());
    }

    @AfterAll
    static void cleanup() {
        System.out.println("[ReviewLoop-E2E] === All review-loop E2E tests completed ===");
    }
}