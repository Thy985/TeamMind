package com.teammind.cli.registry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * CLI 调用演示
 *
 * 展示 TeamMind 核心能力：调用本地 Agent CLI 并实时流式读取 stdout。
 *
 * 用法（前提：PATH 上有 claude 或 codex）：
 *   java -cp target/classes com.teammind.cli.registry.CLIInvocationDemo
 *
 * 这个 demo 演示：
 *  1. 调用 claude 询问 "用一句话回答 Spring Boot 是什么"
 *  2. 流式读取 stdout 并打印到控制台
 *  3. 如果 claude 不可用，回退到 codex
 */
public class CLIInvocationDemo {

    public static void main(String[] args) {
        // 找到任意一个已安装的 CLI
        CLIDiscovery.DiscoveryResult claude = CLIDiscovery.discover(
                CLIDiscovery.KNOWN_CLIS.stream()
                        .filter(d -> d.id().equals("claude-code"))
                        .findFirst().orElseThrow()
        );
        CLIDiscovery.DiscoveryResult codex = CLIDiscovery.discover(
                CLIDiscovery.KNOWN_CLIS.stream()
                        .filter(d -> d.id().equals("codex"))
                        .findFirst().orElseThrow()
        );

        CLIDiscovery.DiscoveryResult chosen = claude.installed() ? claude
                : codex.installed() ? codex
                : null;

        if (chosen == null) {
            System.out.println("No Agent CLI installed. Please install claude-code or codex first.");
            System.exit(1);
        }

        System.out.println("=================================================");
        System.out.println(" TeamMind CLI Invocation Demo");
        System.out.println("=================================================");
        System.out.println();
        System.out.println("Using CLI: " + chosen.descriptor().displayName() + " (v" + chosen.version() + ")");
        System.out.println("Binary:    " + chosen.binaryPath());
        System.out.println();
        System.out.println("Prompt:    \"用一句话回答：Spring Boot 是什么？\"");
        System.out.println();
        System.out.println("----- Streaming Output -----");

        try {
            // 大多数 CLI 接受 prompt 作为参数或 stdin
            // 这里采用最通用的方式：通过 stdin 传入 prompt
            ProcessBuilder pb = new ProcessBuilder(
                    chosen.binaryPath().toString(),
                    "--print" // Claude Code 的非交互模式
            );
            // codex 用不同的 flag，回退到简单形式
            if (chosen.descriptor().id().equals("codex")) {
                pb = new ProcessBuilder(
                        chosen.binaryPath().toString(),
                        "exec",
                        "用一句话回答：Spring Boot 是什么？"
                );
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 通过 stdin 传入 prompt（如果 CLI 支持）
            if (!chosen.descriptor().id().equals("codex")) {
                try (var writer = process.getOutputStream()) {
                    writer.write("用一句话回答：Spring Boot 是什么？\n".getBytes(StandardCharsets.UTF_8));
                    writer.flush();
                }
            }

            // 流式读取 stdout
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                long startTime = System.currentTimeMillis();
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + chosen.descriptor().id() + "] " + line);
                    System.out.flush();
                }
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                long duration = System.currentTimeMillis() - startTime;
                if (!finished) {
                    System.out.println();
                    System.out.println("[TIMEOUT after 30s] Killing process...");
                    process.destroyForcibly();
                } else {
                    System.out.println();
                    System.out.println("----- Completed in " + duration + "ms, exit code: " + process.exitValue() + " -----");
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}