package com.teammind.cli.registry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * CLI 自动扫描器
 *
 * 扫描用户 PATH 上已安装的 AI Agent CLI。
 * 当前支持：
 *   - OpenCode (opencode)
 *   - Claude Code (claude)
 *   - Codex CLI (codex)
 *   - Gemini CLI (gemini)
 *   - Aider (aider)
 *
 * 用法：
 *   java -cp target/classes com.teammind.cli.registry.CLIDiscovery
 *
 * 输出：
 *   ✓ opencode     OpenCode             v0.5.1
 *   ✓ claude       Claude Code          v1.0.30
 *   ✗ codex        Codex CLI            NOT INSTALLED
 *   ...
 */
public class CLIDiscovery {

    /**
     * 已知 CLI 描述符
     */
    public record CLIDescriptor(
            String id,
            String binary,
            String displayName,
            String[] versionArgs,
            String homepage
    ) {}

    public static final List<CLIDescriptor> KNOWN_CLIS = Arrays.asList(
            new CLIDescriptor(
                    "opencode",
                    "opencode",
                    "OpenCode",
                    new String[]{"--version"},
                    "https://github.com/opencode-ai/opencode"
            ),
            new CLIDescriptor(
                    "claude-code",
                    "claude",
                    "Claude Code",
                    new String[]{"--version"},
                    "https://github.com/anthropics/claude-code"
            ),
            new CLIDescriptor(
                    "codex",
                    "codex",
                    "Codex CLI",
                    new String[]{"--version"},
                    "https://github.com/openai/codex"
            ),
            new CLIDescriptor(
                    "gemini-cli",
                    "gemini",
                    "Gemini CLI",
                    new String[]{"--version"},
                    "https://github.com/google-gemini/gemini-cli"
            ),
            new CLIDescriptor(
                    "aider",
                    "aider",
                    "Aider",
                    new String[]{"--version"},
                    "https://github.com/Aider-AI/aider"
            )
    );

    /**
     * 探测单个 CLI 是否安装并可用
     *
     * @param descriptor CLI 描述符
     * @return 探测结果
     */
    public static DiscoveryResult discover(CLIDescriptor descriptor) {
        Path binaryPath = findInPath(descriptor.binary());
        if (binaryPath == null) {
            return DiscoveryResult.notInstalled(descriptor);
        }

        String version = tryGetVersion(binaryPath, descriptor.versionArgs());
        return DiscoveryResult.installed(descriptor, binaryPath, version);
    }

    /**
     * 探测所有已知 CLI
     */
    public static List<DiscoveryResult> discoverAll() {
        List<DiscoveryResult> results = new ArrayList<>();
        for (CLIDescriptor descriptor : KNOWN_CLIS) {
            results.add(discover(descriptor));
        }
        return results;
    }

    /**
     * 在 PATH 中查找可执行文件
     */
    private static Path findInPath(String binaryName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        // Windows 使用 where，Unix 使用 which；这里做跨平台处理
        String[] pathDirs = pathEnv.split(java.io.File.pathSeparator);

        // Windows 上常见的可执行文件扩展名
        List<String> extensions = isWindows()
                ? Arrays.asList(".exe", ".bat", ".cmd", "")
                : Arrays.asList("");

        for (String dir : pathDirs) {
            for (String ext : extensions) {
                Path candidate = Paths.get(dir, binaryName + ext);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 尝试执行 --version 获取版本号
     */
    private static String tryGetVersion(Path binaryPath, String[] args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(buildCommand(binaryPath, args));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出（带超时）
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "TIMEOUT";
            }

            if (process.exitValue() != 0) {
                return "ERROR";
            }

            String output = new String(process.getInputStream().readAllBytes()).trim();
            // 截取第一行，提取版本号
            if (output.isEmpty()) {
                return "UNKNOWN";
            }
            String firstLine = output.split("\n")[0].trim();
            // 提取形如 vX.Y.Z 或 X.Y.Z 的版本号
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("v?\\d+\\.\\d+(\\.\\d+)?")
                    .matcher(firstLine);
            if (matcher.find()) {
                return matcher.group();
            }
            return firstLine.length() > 30 ? firstLine.substring(0, 30) + "..." : firstLine;
        } catch (IOException | InterruptedException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static List<String> buildCommand(Path binaryPath, String[] args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(binaryPath.toString());
        for (String arg : args) {
            cmd.add(arg);
        }
        return cmd;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 探测结果
     */
    public record DiscoveryResult(
            CLIDescriptor descriptor,
            boolean installed,
            Path binaryPath,
            String version
    ) {
        public static DiscoveryResult installed(CLIDescriptor d, Path path, String version) {
            return new DiscoveryResult(d, true, path, version);
        }

        public static DiscoveryResult notInstalled(CLIDescriptor d) {
            return new DiscoveryResult(d, false, null, null);
        }

        public String statusIcon() {
            return installed ? "[OK]" : "[--]";
        }

        public String displayString() {
            if (installed) {
                return String.format("%s %-12s %-20s v%s (%s)",
                        statusIcon(),
                        descriptor.id(),
                        descriptor.displayName(),
                        version,
                        binaryPath);
            } else {
                return String.format("%s %-12s %-20s NOT INSTALLED",
                        statusIcon(),
                        descriptor.id(),
                        descriptor.displayName());
            }
        }
    }

    /**
     * 入口：打印当前系统检测到的所有 CLI
     */
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" TeamMind CLI Auto-Discovery");
        System.out.println("=================================================");
        System.out.println();
        System.out.println("Scanning PATH for AI Agent CLIs...");
        System.out.println();

        List<DiscoveryResult> results = discoverAll();
        int installedCount = 0;
        for (DiscoveryResult result : results) {
            System.out.println("  " + result.displayString());
            if (result.installed()) installedCount++;
        }

        System.out.println();
        System.out.println("-------------------------------------------------");
        System.out.println(String.format(" Detected: %d / %d CLIs", installedCount, results.size()));
        System.out.println("-------------------------------------------------");

        if (installedCount == 0) {
            System.out.println();
            System.out.println("No AI Agent CLI found. To use TeamMind, please install at least one:");
            for (CLIDescriptor descriptor : KNOWN_CLIS) {
                System.out.println("  - " + descriptor.displayName() + ": " + descriptor.homepage());
            }
        } else {
            System.out.println();
            System.out.println("Ready! Start TeamMind with: start-all.bat");
        }
    }
}