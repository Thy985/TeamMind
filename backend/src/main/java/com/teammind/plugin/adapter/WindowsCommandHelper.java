package com.teammind.plugin.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * WindowsCommandHelper — Windows 平台特殊命令处理
 *
 * 解决问题：
 *   Java ProcessBuilder 不能直接执行 PowerShell 脚本 (.ps1)
 *   Windows 上的 "bash" 可能是 WSL 启动器（不是真的 Git Bash/MSYS2）
 *
 * 自动检测以下场景：
 *   - 命令以 .ps1 结尾 → 用 powershell.exe -Command "& '<script>' args" 包装
 *   - 命令以 .bat 或 .cmd 结尾 → 用 cmd.exe /c 包装
 *   - 命令是裸名称（如 "codex"）→ 先在 PATH 中查找 .exe / .ps1 / .bat
 *
 * 关键：使用 Git Bash (MSYS2) 而不是 PATH 中的 bash（可能是 WSL）。
 * WSL 的 $HOME=/home/lenovo，看不到 Windows 的 /c/Users/lenovo/.codex/。
 * Git Bash 的 $HOME=/c/Users/lenovo，能正确访问 Windows 主目录。
 */
@Slf4j
public final class WindowsCommandHelper {

    private WindowsCommandHelper() {}

    /** 已找到的 Git Bash 路径（缓存） */
    private static volatile String cachedBashPath = null;

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("windows");
    }

    /**
     * 查找 Git Bash (MSYS2) 路径
     * 优先使用标准安装位置，避免 PATH 中的 WSL bash
     */
    private static String findGitBash() {
        if (cachedBashPath != null) {
            return cachedBashPath;
        }
        String[] candidates = {
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files\\Git\\usr\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\usr\\bin\\bash.exe"
        };
        for (String path : candidates) {
            if (Files.isRegularFile(Paths.get(path))) {
                log.debug("Found Git Bash at: {}", path);
                cachedBashPath = path;
                return path;
            }
        }
        log.warn("Git Bash not found in standard locations, falling back to PATH 'bash'");
        cachedBashPath = "bash";
        return "bash";
    }

    public static List<String> wrap(List<String> command) {
        if (!isWindows() || command.isEmpty()) {
            return command;
        }
        String first = command.get(0);
        if (isAlreadyWrapped(first)) {
            return command;
        }

        if (first.toLowerCase().endsWith(".ps1")) {
            return wrapPowerShell(first, command.subList(1, command.size()));
        }

        if (first.toLowerCase().endsWith(".bat") || first.toLowerCase().endsWith(".cmd")) {
            return wrapCmd(first, command.subList(1, command.size()));
        }

        ResolvedScript resolved = resolveInPath(first);
        if (resolved != null) {
            log.debug("Resolved '{}' to {} script at {}", first, resolved.type, resolved.path);
            if ("ps1".equals(resolved.type)) {
                return wrapPowerShell(resolved.path, command.subList(1, command.size()));
            }
            if ("bat".equals(resolved.type) || "cmd".equals(resolved.type)) {
                return wrapCmd(resolved.path, command.subList(1, command.size()));
            }
            List<String> result = new ArrayList<>();
            result.add(resolved.path);
            result.addAll(command.subList(1, command.size()));
            return result;
        }

        return command;
    }

    /**
     * 包装单元素命令
     * shell 表达式用 Git Bash (MSYS2) 包装
     */
    public static List<String> wrap(String command) {
        if (!isWindows()) {
            return List.of("bash", "-c", command);
        }
        if (looksLikeShellExpression(command)) {
            String gitBash = findGitBash();
            return List.of(gitBash, "-c", command);
        }
        return wrap(List.of(command.split(" ")));
    }

    private static boolean looksLikeShellExpression(String command) {
        return command.contains("||") || command.contains("&&") || command.contains("~")
                || command.contains("$") || command.contains("|")
                || command.contains(">") || command.contains("<");
    }

    private static boolean isAlreadyWrapped(String first) {
        String lower = first.toLowerCase();
        return lower.endsWith(".exe")
                || lower.contains("powershell")
                || lower.contains("cmd.exe")
                || lower.endsWith("wsl.exe");
    }

    private static List<String> wrapPowerShell(String scriptPath, List<String> restArgs) {
        StringBuilder sb = new StringBuilder();
        sb.append("& '").append(scriptPath).append("'");
        for (String arg : restArgs) {
            sb.append(' ').append(quoteArg(arg));
        }
        List<String> result = new ArrayList<>();
        result.add("powershell.exe");
        result.add("-NoProfile");
        result.add("-ExecutionPolicy");
        result.add("Bypass");
        result.add("-Command");
        result.add(sb.toString());
        return result;
    }

    private static String quoteArg(String arg) {
        if (arg == null) return "''";
        if (arg.contains(" ") || arg.contains("'") || arg.contains("$")
                || arg.contains("\"") || arg.contains("&") || arg.contains("|")) {
            return "'" + arg.replace("'", "''") + "'";
        }
        return arg;
    }

    private static List<String> wrapCmd(String scriptPath, List<String> restArgs) {
        List<String> result = new ArrayList<>();
        result.add("cmd.exe");
        result.add("/c");
        result.add(scriptPath);
        result.addAll(restArgs);
        return result;
    }

    private static ResolvedScript resolveInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] extensions;
        String pathext = System.getenv("PATHEXT");
        if (pathext != null && !pathext.isBlank()) {
            extensions = pathext.toLowerCase().split(";");
        } else {
            extensions = new String[]{".exe", ".bat", ".cmd", ".ps1", ""};
        }
        boolean hasPs1 = false;
        for (String ext : extensions) {
            if (".ps1".equals(ext)) { hasPs1 = true; break; }
        }
        if (!hasPs1) {
            String[] withPs1 = new String[extensions.length + 1];
            System.arraycopy(extensions, 0, withPs1, 0, extensions.length);
            withPs1[extensions.length] = ".ps1";
            extensions = withPs1;
        }

        // 优先 .cmd (更稳定，不依赖 PowerShell pipeline)
        // Node.js CLI 通常同时有 .ps1 和 .cmd，.cmd 在无 TTY 环境更可靠
        String[] extPriority = {".cmd", ".ps1", ".exe", ".bat"};

        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir == null || dir.isBlank()) continue;
            for (String ext : extPriority) {
                File candidate = new File(dir, command + ext);
                if (candidate.isFile() && candidate.canExecute()) {
                    String type = ext.startsWith(".") ? ext.substring(1) : ext;
                    return new ResolvedScript(candidate.getAbsolutePath(), type);
                }
            }
        }
        return null;
    }

    private record ResolvedScript(String path, String type) {}
}