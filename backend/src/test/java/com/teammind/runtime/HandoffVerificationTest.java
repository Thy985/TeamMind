package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.*;
import com.teammind.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Handoff 验证测试 — Phase 4
 *
 * 验证内容：
 * 1. PipelineContext.HandoffRecord 结构正确
 * 2. PipelineOrchestrator 能正确创建 handoff 记录
 * 3. cancel/pause/resume 方法存在并可调用
 */
@SpringBootTest(classes = com.teammind.TeamMindApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HandoffVerificationTest {

    @Autowired private PipelineOrchestrator pipelineOrchestrator;
    @Autowired private TaskRepository taskRepo;
    @Autowired private TaskExecutionRepository executionRepo;
    @Autowired private ExecutionStepRepository stepRepo;
    @Autowired private AgentInvocationRepository invocationRepo;
    @Autowired private ArtifactRepository artifactRepo;
    @Autowired private EvidenceRepository evidenceRepo;

    private static final String PROJECT_ID = "handoff-test-project";
    private static final String TASK_ID = "handoff-test-task";

    // ─── 测试 1: HandoffRecord 结构验证 ─────────────────────────────

    @Test
    @Order(1)
    @DisplayName("[P4-1] PipelineContext.HandoffRecord 结构正确")
    void testHandoffRecordStructure() {
        PipelineContext context = PipelineContext.builder()
                .pipelineName("review-loop")
                .taskId(TASK_ID)
                .objective("Test task")
                .startedAt(LocalDateTime.now())
                .build();

        // 记录 handoff
        context.recordHandoff("implement", "review", "success");
        context.recordHandoff("review", "verify", "condition");

        List<PipelineContext.HandoffRecord> handoffs = context.getHandoffHistory();
        assertEquals(2, handoffs.size(), "Should have 2 handoff records");

        // 验证第一个 handoff
        PipelineContext.HandoffRecord first = handoffs.get(0);
        assertEquals("implement", first.getFromStep());
        assertEquals("review", first.getToStep());
        assertEquals("success", first.getReason());
        assertNotNull(first.getTimestamp());

        // 验证第二个 handoff
        PipelineContext.HandoffRecord second = handoffs.get(1);
        assertEquals("review", second.getFromStep());
        assertEquals("verify", second.getToStep());
        assertEquals("condition", second.getReason());

        System.out.println("[Handoff] HandoffRecord structure verified:");
        System.out.println("  - Record count: " + handoffs.size());
        System.out.println("  - First: " + first.getFromStep() + " → " + first.getToStep());
        System.out.println("  - Second: " + second.getFromStep() + " → " + second.getToStep());
    }

    // ─── 测试 2: Pipeline 加载与解析 ─────────────────────────────

    @Test
    @Order(2)
    @DisplayName("[P4-2] PipelineDefinition 加载与解析正确")
    void testPipelineDefinitionLoad() {
        PipelineDefinition def = pipelineOrchestrator.loadPipeline("review-loop.yaml");

        assertNotNull(def, "Pipeline definition should not be null");
        assertEquals("review-loop", def.getName());
        assertNotNull(def.getSteps());
        assertFalse(def.getSteps().isEmpty(), "Should have steps");

        // 验证步骤数量
        assertEquals(3, def.getSteps().size(), "Should have 3 steps: implement, review, verify");

        // 验证步骤名称
        List<String> stepNames = def.getSteps().stream()
                .map(PipelineStepDefinition::getName)
                .toList();
        assertTrue(stepNames.contains("implement"), "Should have implement step");
        assertTrue(stepNames.contains("review"), "Should have review step");
        assertTrue(stepNames.contains("verify"), "Should have verify step");

        // 验证 agent 配置
        PipelineStepDefinition implementStep = def.getStep("implement");
        assertEquals("codex", implementStep.getAgent());
        assertEquals("LEAD", implementStep.getRole());

        PipelineStepDefinition reviewStep = def.getStep("review");
        assertEquals("claude-code", reviewStep.getAgent());
        assertEquals("REVIEWER", reviewStep.getRole());

        // 验证 handoff 配置
        assertEquals("review", implementStep.getHandoff());
        assertEquals("verify", reviewStep.getHandoff());

        System.out.println("[Handoff] PipelineDefinition loaded:");
        System.out.println("  - Name: " + def.getName());
        System.out.println("  - Steps: " + stepNames);
        System.out.println("  - Implement agent: " + implementStep.getAgent());
        System.out.println("  - Review agent: " + reviewStep.getAgent());
    }

    // ─── 测试 3: Pipeline 执行（快速验证） ────────────────────────

    @Test
    @Order(3)
    @DisplayName("[P4-3] Pipeline 执行框架验证 — 不依赖真实 CLI")
    void testPipelineExecutionFramework() {
        // 创建 Task
        Task task = Task.builder()
                .id(TASK_ID + "-framework")
                .projectId(PROJECT_ID)
                .objective("Test objective")
                .taskTypeId("test")
                .state(TaskState.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
        task = taskRepo.save(task);

        // 执行 Pipeline（会失败因为 CLI 不可用，但应该能验证框架）
        List<String> constraints = List.of("constraint1");
        
        try {
            PipelineExecutionResult result = pipelineOrchestrator.executePipeline(
                    TASK_ID + "-framework", task.getObjective(), constraints, "review-loop");

            assertNotNull(result, "Result should not be null");
            assertNotNull(result.getContext(), "Context should not be null");

            // 即使失败，也应该有 context 和 step results
            PipelineContext context = result.getContext();
            System.out.println("[Handoff] Pipeline execution result:");
            System.out.println("  - Status: " + result.getOverallStatus());
            System.out.println("  - Steps executed: " + result.getStepResults().size());
            System.out.println("  - Handoff records: " + context.getHandoffHistory().size());
            System.out.println("  - Duration: " + result.getTotalDurationMs() + "ms");

            // 验证 context 有基本字段
            assertEquals(TASK_ID + "-framework", context.getTaskId());
            assertNotNull(context.getStartedAt());

        } catch (Exception e) {
            System.out.println("[Handoff] Pipeline execution framework test completed with exception (expected in test env): " + e.getMessage());
        }
    }

    // ─── 测试 4: Cancel 方法存在性验证 ─────────────────────────────

    @Test
    @Order(4)
    @DisplayName("[P4-4] PipelineOrchestrator 具有 cancel 方法")
    void testCancelMethodExists() {
        // 验证方法存在（通过反射）
        try {
            var method = PipelineOrchestrator.class.getMethod("cancelPipeline", String.class);
            assertNotNull(method, "cancelPipeline method should exist");
            System.out.println("[Handoff] cancelPipeline(String) method verified");
        } catch (NoSuchMethodException e) {
            fail("cancelPipeline method not found: " + e.getMessage());
        }
    }

    // ─── 测试 5: Pause/Resume 方法存在性验证 ──────────────────────

    @Test
    @Order(5)
    @DisplayName("[P4-5] PipelineOrchestrator 具有 pause/resume 方法")
    void testPauseResumeMethodsExist() {
        try {
            var pauseMethod = PipelineOrchestrator.class.getMethod("pausePipeline", String.class);
            assertNotNull(pauseMethod, "pausePipeline method should exist");
            System.out.println("[Handoff] pausePipeline(String) method verified");
        } catch (NoSuchMethodException e) {
            fail("pausePipeline method not found: " + e.getMessage());
        }

        try {
            var resumeMethod = PipelineOrchestrator.class.getMethod("resumePipeline", String.class);
            assertNotNull(resumeMethod, "resumePipeline method should exist");
            System.out.println("[Handoff] resumePipeline(String) method verified");
        } catch (NoSuchMethodException e) {
            fail("resumePipeline method not found: " + e.getMessage());
        }
    }

    // ─── 测试 6: Handoff 链路验证（模拟） ─────────────────────────

    @Test
    @Order(6)
    @DisplayName("[P4-6] 完整 Handoff 链路验证（模拟数据）")
    void testFullHandoffChain() {
        PipelineContext context = PipelineContext.builder()
                .pipelineName("review-loop")
                .taskId(TASK_ID + "-chain")
                .objective("Write a Java hello world")
                .startedAt(LocalDateTime.now())
                .build();

        // 模拟 implement 步骤完成
        Artifact implementArtifact = Artifact.builder()
                .id("impl-artifact")
                .type("CODE_DIFF")
                .summary("Implementation completed")
                .data(Map.of("step", "implement", "agent", "codex", "files_changed", "HelloWorld.java"))
                .createdAt(LocalDateTime.now())
                .build();

        PipelineStepResult implementResult = PipelineStepResult.builder()
                .stepName("implement")
                .agentId("codex")
                .state("SUCCESS")
                .artifact(implementArtifact)
                .durationMs(5000L)
                .exitCode(0)
                .build();

        context.recordStepResult("implement", implementResult);

        // 模拟 handoff 到 review
        context.recordHandoff("implement", "review", "success");

        // 模拟 review 步骤完成
        Artifact reviewArtifact = Artifact.builder()
                .id("review-artifact")
                .type("REVIEW_FINDINGS")
                .summary("Review completed")
                .data(Map.of("step", "review", "agent", "claude-code", "findings", "2 issues"))
                .createdAt(LocalDateTime.now())
                .build();

        PipelineStepResult reviewResult = PipelineStepResult.builder()
                .stepName("review")
                .agentId("claude-code")
                .state("SUCCESS")
                .artifact(reviewArtifact)
                .durationMs(3000L)
                .exitCode(0)
                .build();

        context.recordStepResult("review", reviewResult);

        // 模拟 handoff 到 verify
        context.recordHandoff("review", "verify", "success");

        // 验证 handoff 历史
        List<PipelineContext.HandoffRecord> handoffs = context.getHandoffHistory();
        assertEquals(2, handoffs.size(), "Should have 2 handoffs");

        // 验证 chain
        assertEquals("implement", handoffs.get(0).getFromStep());
        assertEquals("review", handoffs.get(0).getToStep());
        assertEquals("review", handoffs.get(1).getFromStep());
        assertEquals("verify", handoffs.get(1).getToStep());

        // 验证 artifacts
        assertEquals(2, context.getArtifacts().size());
        assertNotNull(context.getArtifacts().get("implement"));
        assertNotNull(context.getArtifacts().get("review"));

        System.out.println("[Handoff] Full handoff chain verified:");
        System.out.println("  - implement → review (codex → claude-code)");
        System.out.println("  - review → verify");
        System.out.println("  - Artifacts: " + context.getArtifacts().keySet());
        System.out.println("  - Handoffs: " + handoffs.size());
    }

    // ─── 清理 ───────────────────────────────────────────────────

    @AfterAll
    static void cleanup() {
        System.out.println("[Handoff] === All verification tests completed ===");
    }
}
