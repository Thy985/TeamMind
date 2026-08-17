package com.teammind.plugin.adapter;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Quick sanity check for WindowsCommandHelper.
 * Not part of CI — only useful for manual debugging on Windows.
 */
class WindowsCommandHelperSanityTest {

    @Test
    void shouldWrapCodexPs1Correctly() throws Exception {
        if (!WindowsCommandHelper.isWindows()) {
            System.out.println("[sanity] Non-Windows platform, skipping.");
            return;
        }

        List<String> cmd = WindowsCommandHelper.wrap(List.of("codex", "--version"));
        System.out.println("[sanity] Wrapped command: " + cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true);
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new AssertionError("codex --version did not complete within 30s");
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        System.out.println("[sanity] Exit code: " + p.exitValue());
        System.out.println("[sanity] Output: " + output);

        if (p.exitValue() != 0) {
            throw new AssertionError("codex --version returned exit " + p.exitValue());
        }
        if (!output.toString().toLowerCase().contains("codex")) {
            throw new AssertionError("Unexpected output: " + output);
        }
    }
}