package com.teammind.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * CLI Host — 不启动 HTTP Server 的 Runtime 入口
 *
 * 用法：
 *   java -cp teammind-runtime.jar com.teammind.runtime.CliRuntimeHost \
 *        --pipeline single-agent \
 *        --task-id my-task \
 *        --objective "Fix the auth bug"
 *
 * 或通过 Maven：
 *   mvn exec:java -Dexec.mainClass="com.teammind.runtime.CliRuntimeHost" \
 *        -Dexec.args="--pipeline single-agent --objective 'Fix the auth bug'"
 *
 * 架构不变量：
 *   - 不启动 Tomcat / HTTP Server
 *   - 不启动 WebSocket
 *   - 使用 NoOpEventPublisher
 *   - Runtime Core 通过 RuntimeLauncher 初始化
 */
@Slf4j
public class CliRuntimeHost {

    public static void main(String[] args) {
        System.out.println("""
                
                ╔══════════════════════════════════════════╗
               ║       TeamMind CLI Runtime Host           ║
               ║       (No HTTP / No WebSocket)            ║
                ╚══════════════════════════════════════════╝
                """);

        CliArgs cliArgs = parseArgs(args);
        log.info("CLI Host args: {}", cliArgs);

        // 启动 Spring Boot 但不启动 Web Server，使用 CLI Profile
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(
                com.teammind.TeamMindApplication.class)
                .web(WebApplicationType.NONE)
                .headless(true)
                .properties("spring.profiles.active=cli")
                .properties("teammind.plugins.use-database=false")
                .run(args);

        try {
            RuntimeLauncher launcher = ctx.getBean(RuntimeLauncher.class);
            log.info("RuntimeLauncher initialized: {}", launcher.isInitialized());

            if (cliArgs.pipeline != null && cliArgs.objective != null) {
                executePipeline(ctx, cliArgs);
            } else {
                log.info("No --pipeline/--objective specified. Runtime initialized and ready.");
                log.info("Available pipelines: single-agent, review-loop");
                log.info("Usage: --pipeline <name> --objective <text> [--task-id <id>]");
            }

        } finally {
            ctx.close();
            System.out.println("\nTeamMind CLI Host shutdown complete.");
        }
    }

    private static void executePipeline(ConfigurableApplicationContext ctx, CliArgs args) {
        PipelineOrchestrator orchestrator = ctx.getBean(PipelineOrchestrator.class);
        com.teammind.repository.TaskRepository taskRepo =
                ctx.getBean(com.teammind.repository.TaskRepository.class);

        String taskId = args.taskId != null ? args.taskId : "cli-" + System.currentTimeMillis();

        // 创建 Task
        com.teammind.entity.Task task = com.teammind.entity.Task.builder()
                .id(taskId)
                .projectId("cli-host")
                .objective(args.objective)
                .taskTypeId("code-generation")
                .state(com.teammind.common.TaskState.SUBMITTED)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        taskRepo.save(task);

        System.out.println("\nExecuting pipeline: " + args.pipeline);
        System.out.println("Task ID: " + taskId);
        System.out.println("Objective: " + args.objective);
        System.out.println();

        PipelineExecutionResult result = orchestrator.executePipeline(
                taskId,
                args.objective,
                java.util.List.of(),
                args.pipeline);

        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  Pipeline: " + result.getPipelineName());
        System.out.println("  Status:   " + result.getOverallStatus());
        System.out.println("  Steps:    " + result.getStepResults().size());
        System.out.println("  Duration: " + result.getTotalDurationMs() + "ms");
        System.out.println("══════════════════════════════════════════\n");

        for (PipelineStepResult step : result.getStepResults()) {
            System.out.println("  " + step.getStepName() + " → " + step.getState()
                    + " (" + step.getDurationMs() + "ms)");
            if (step.getErrorReason() != null) {
                System.out.println("    error: " + step.getErrorReason());
            }
        }
    }

    private static CliArgs parseArgs(String[] args) {
        CliArgs result = new CliArgs();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pipeline" -> result.pipeline = nextArg(args, i);
                case "--objective" -> result.objective = nextArg(args, i);
                case "--task-id" -> result.taskId = nextArg(args, i);
            }
        }
        return result;
    }

    private static String nextArg(String[] args, int i) {
        return i + 1 < args.length ? args[i + 1] : null;
    }

    private static class CliArgs {
        String pipeline;
        String objective;
        String taskId;

        @Override
        public String toString() {
            return "CliArgs{pipeline='%s', objective='%s', taskId='%s'}"
                    .formatted(pipeline, objective, taskId);
        }
    }
}