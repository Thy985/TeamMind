package com.teammind.plugin.adapter;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * WindowsCommandHelper — Windows 平台特殊命令处理
 *
 * 解决的问题：
 *   Java 的 ProcessBuilder 不能直接执行 PowerShell 脚本（.ps1）
 *   也不能直接执行 .bat 文件（除非 .bat 在 PATHEXT 中）
 *
 * 自动检测以下场景：
 *   - 命令以 .ps1 结尾 → 用 powershell.exe -File 包装
 *   - 命令以 .bat 或 .cmd 结尾 → 直接执行（PATHEXT 通常已支持）
 *   - 命令是裸名称（如 "codex"） → 先在 PATH 中查找 .exe / .ps1 / .bat
 *     如果找到 .ps1，则需要通过 powershell.exe 执行
 *
 * 非 Windows 平台直接返回原命令。
 */
@Slf4j
public final class WindowsCommandHelper {

    private WindowsCommandHelper() {}

    /**
     * 检测系统是否为 Windows
     */
    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("windows");
    }

    /**
     * 将命令包装为可执行的形式
     *
     * 输入: ["codex", "--prompt", "hello"] 或 ["claude", "--print", "hello"]
     * 输出（Windows + .ps1）: ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "C:\\path\\to\\codex.ps1", "--prompt", "hello"]
     * 输出（Windows + .bat）: ["cmd.exe", "/c", "codex.bat", ...]
     * 输出（非 Windows）: 原样返回
     */
    public static List<String> wrap(List<String> command) {
        if (!isWindows() || command.isEmpty()) {
            return command;
        }
        String first = command.get(0);
        // 如果已经是 powershell.exe / cmd.exe / 绝对路径含可执行扩展，跳过
        if (isAlreadyWrapped(first)) {
            return command;
        }

        // 1) 如果是 .ps1 脚本的绝对路径 → 用 powershell.exe 包装
        if (first.toLowerCase().endsWith(".ps1")) {
            return wrapPowerShell(first, command.subList(1, command.size()));
        }

        // 2) 如果是 .bat / .cmd 脚本的绝对路径 → 用 cmd.exe /c 包装
        if (first.toLowerCase().endsWith(".bat") || first.toLowerCase().endsWith(".cmd")) {
            return wrapCmd(first, command.subList(1, command.size()));
        }

        // 3) 如果是裸命令 → 在 PATH 中查找，找到 .ps1 就包装
        ResolvedScript resolved = resolveInPath(first);
        if (resolved != null) {
            log.debug("Resolved '{}' to {} script at {}", first, resolved.type, resolved.path);
            if ("ps1".equals(resolved.type)) {
                return wrapPowerShell(resolved.path, command.subList(1, command.size()));
            }
            if ("bat".equals(resolved.type) || "cmd".equals(resolved.type)) {
                return wrapCmd(resolved.path, command.subList(1, command.size()));
            }
            // exe — 用绝对路径替换
            List<String> result = new ArrayList<>();
            result.add(resolved.path);
            result.addAll(command.subList(1, command.size()));
            return result;
        }

        // 找不到或就是 .exe，留给 ProcessBuilder 自己去处理
        return command;
    }

    /**
     * 包装单元素命令（用于 health check 的简单场景）
     *
     * 如果命令看起来是 shell 表达式（包含 ||, &&, ~, $ 等），会尝试用 bash -c 包装
     */
    public static List<String> wrap(String command) {
        if (!isWindows()) {
            return List.of("bash", "-c", command);
        }
        if (looksLikeShellExpression(command)) {
            // shell 表达式 → 用 bash -c 包装
            return List.of("bash", "-c", command);
        }
        return wrap(List.of(command.split(" ")));
    }

    /**
     * 判断命令是否像 shell 表达式
     */
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
        // 使用 -Command "& '<script>' args..." 而不是 -File '<script>' args
        // -File 在 Java ProcessBuilder 下会挂起（30+ 秒），-Command 1 秒完成
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

    /**
     * 对参数进行 PowerShell 风格的引号转义
     * 单引号包裹，内部单引号转义为两个单引号
     */
    private static String quoteArg(String arg) {
        if (arg == null) return "''";
        // 如果包含空格或特殊字符，用单引号包裹
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

    /**
     * 在 PATH 中查找命令对应的实际脚本/可执行文件
     */
    private static ResolvedScript resolveInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        // 先尝试 PATHEXT 扩展
        String[] extensions;
        String pathext = System.getenv("PATHEXT");
        if (pathext != null && !pathext.isBlank()) {
            extensions = pathext.toLowerCase().split(";");
        } else {
            extensions = new String[]{".exe", ".bat", ".cmd", ".ps1", ""};
        }
        // .ps1 不在 PATHEXT 中需手动添加
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

        // 优先检查 .exe → .bat → .cmd → .ps1
        // 排序: ps1 优先（因为 Java ProcessBuilder 直接调用 ps1 会失败）
        String[] extPriority = {".ps1", ".exe", ".bat", ".cmd"};

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