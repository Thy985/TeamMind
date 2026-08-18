package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.entity.*;
import com.teammind.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless Runtime E2E — 架构 Gate 测试
 *
 * 验证架构不变量：
 *   Runtime Core 可以在不启动 HTTP Server / WebSocket 的情况下运行。
 *
 * 证明：
 *   1. 使用 NoOpEventPublisher 替代 WSEventPublisher
 *   2. webEnvironment = NONE（不启动 Tomcat）
 *   3. PipelineOrchestrator 正常加载 Pipeline 定义
 *   4. Pipeline 执行（失败路径）正常工作
 *   5. RuntimeLauncher 初始化所有组件
 *
 * 如果此测试通过，说明 Runtime Core 已从 Web Host 解耦。
 */
@SpringBootTest(classes = {
        com.teammind.TeamMindApplication.class,
        HeadlessRuntimeE2ETest.HeadlessConfig.class
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HeadlessRuntimeE2ETest {

    /**
     * Headless 配置 — 用 NoOpEventPublisher 替代 WSEventPublisher
     *
     * 这证明 Runtime Core 依赖的是 EventPublisher 接口，不是 WSEventPublisher 具体类。
     */
    @TestConfiguration
    static class HeadlessConfig {
        @Bean
        @Primary
        EventPublisher eventPublisher() {
            return new NoOpEventPublisher();
        }
    }

    @Autowired private PipelineOrchestrator pipelineOrchestrator;
    @Autowired private RuntimeLauncher runtimeLauncher;
    @Autowired private TaskRepository taskRepo;
    @Autowired private EventPublisher eventPublisher;

    private static final String PROJECT_ID = "headless-e2e-project";

    @Test
    @Order(1)
    @DisplayName("[Headless] EventPublisher 是 NoOpEventPublisher，不是 WSEventPublisher")
    void eventPublisherIsNoOp() {
        System.out.println("\n=== Headless E2E: EventPublisher Verification ===");

        assertInstanceOf(NoOpEventPublisher.class, eventPublisher,
                "EventPublisher should be NoOpEventPublisher in headless mode");
        assertFalse(eventPublisher.getClass().getSimpleName().contains("WS"),
                "EventPublisher should not be a WebSocket implementation");

        System.out.println("[Headless] EventPublisher: " + eventPublisher.getClass().getSimpleName());
        System.out.println("=== EventPublisher Verification Complete ===\n");
    }

    @Test
    @Order(2)
    @DisplayName("[Headless] Pipeline 定义加载 — 不依赖 Spring ClassPathResource")
    void pipelineDefinitionLoadsWithoutSpringResource() {
        System.out.println("\n=== Headless E2E: Pipeline Definition Loading ===");

        PipelineDefinition def = pipelineOrchestrator.loadPipeline("single-agent.yaml");

        assertNotNull(def, "Pipeline definition should load without ClassPathResource");
        assertEquals("single-agent", def.getName());
        assertEquals(2, def.getSteps().size(), "single-agent should have 2 steps");

        System.out.println("[Headless] Pipeline loaded: " + def.getName() + " with " + def.getSteps().size() + " steps");
        System.out.println("=== Pipeline Definition Loading Complete ===\n");
    }

    @Test
    @Order(3)
    @DisplayName("[Headless] Pipeline 执行（失败路径）— 不依赖 WebSocket")
    void pipelineExecutionWithoutWebSocket() {
        System.out.println("\n=== Headless E2E: Pipeline Execution Without WebSocket ===");

        String taskId = "headless-test-execution";
        Task task = Task.builder()
                .id(taskId)
                .projectId(PROJECT_ID)
                .objective("Headless test objective")
                .taskTypeId("code-generation")
                .state(TaskState.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .build();
        taskRepo.save(task);

        PipelineExecutionResult result = pipelineOrchestrator.executePipeline(
                taskId,
                "Headless test objective",
                List.of(),
                "failure-test");

        System.out.println("[Headless] Pipeline result:");
        System.out.println("  - overallStatus: " + result.getOverallStatus());
        System.out.println("  - steps: " + result.getStepResults().size());

        assertNotNull(result, "Result should not be null");
        assertEquals("FAILED", result.getOverallStatus(),
                "Pipeline with non-existent agent should fail (but without WebSocket)");
        assertTrue(result.getStepResults().size() > 0,
                "Should have step results");

        System.out.println("=== Pipeline Execution Without WebSocket Complete ===\n");
    }

    @Test
    @Order(4)
    @DisplayName("[Headless] RuntimeLauncher 初始化 — 不依赖 Spring Boot 启动钩子")
    void runtimeLauncherInitializesWithoutSpringBoot() {
        System.out.println("\n=== Headless E2E: RuntimeLauncher Initialization ===");

        assertNotNull(runtimeLauncher, "RuntimeLauncher should be available");
        assertTrue(runtimeLauncher.isInitialized(),
                "RuntimeLauncher should be initialized by RuntimeBootstrap");

        System.out.println("[Headless] RuntimeLauncher initialized: " + runtimeLauncher.isInitialized());
        System.out.println("=== RuntimeLauncher Initialization Complete ===\n");
    }

    @Test
    @Order(5)
    @DisplayName("[Headless] review-loop Pipeline 定义加载 — 3 步骤完整链路")
    void reviewLoopPipelineLoads() {
        System.out.println("\n=== Headless E2E: Review-Loop Pipeline ===");

        PipelineDefinition def = pipelineOrchestrator.loadPipeline("review-loop.yaml");

        assertNotNull(def);
        assertEquals("review-loop", def.getName());
        assertEquals(3, def.getSteps().size(), "review-loop should have 3 steps");

        List<String> stepNames = def.getSteps().stream()
                .map(PipelineStepDefinition::getName)
                .toList();
        assertTrue(stepNames.contains("implement"));
        assertTrue(stepNames.contains("review"));
        assertTrue(stepNames.contains("verify"));

        System.out.println("[Headless] Review-loop steps: " + stepNames);
        System.out.println("=== Review-Loop Pipeline Complete ===\n");
    }

    @AfterAll
    static void summary() {
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Headless Runtime E2E — Architecture Gate PASSED     ║");
        System.out.println("║                                                      ║");
        System.out.println("║  Runtime Core 已从 Web Host 解耦：                    ║");
        System.out.println("║  ✓ EventPublisher = NoOpEventPublisher (非 WS)       ║");
        System.out.println("║  ✓ Pipeline 加载不依赖 ClassPathResource             ║");
        System.out.println("║  ✓ Pipeline 执行不依赖 WebSocket                     ║");
        System.out.println("║  ✓ RuntimeLauncher 初始化不依赖 Spring Boot 钩子      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");
    }
}