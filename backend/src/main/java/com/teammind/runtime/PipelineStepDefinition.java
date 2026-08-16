package com.teammind.runtime;

import com.teammind.entity.Artifact;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Single pipeline step definition parsed from YAML.
 *
 * Fields:
 * - name: step identifier (used for handoff reference and artifacts map key)
 * - role: LEAD / REVIEWER / VERIFIER
 * - agent: primary plugin ID (codex / claude-code)
 * - agents: multi-agent candidate list (for parallel verification)
 * - prompt: template supporting {{objective}}, {{constraints}}, {{artifacts.xxx.summary}}
 * - output: output type (CODE_DIFF / REVIEW_FINDINGS / EVIDENCE)
 * - handoff: next step name, null means last step
 * - on_critical / on_success / on_all_pass / on_any_fail: conditional jump targets
 */
@Data
@Builder
public class PipelineStepDefinition {

    private String name;
    private String role;

    /** Primary calling Agent (e.g. "codex") */
    private String agent;

    /** Multi-agent candidate list (e.g. ["git-verifier", "test-runner-verifier"]) */
    private List<String> agents;

    private String prompt;

    /** Output type */
    private String output;

    /** Next step name (handoff) */
    private String handoff;

    /** Conditional jump: on critical finding */
    private String onCritical;

    /** Conditional jump: on success */
    private String onSuccess;

    /** Conditional jump: all verifiers pass */
    private String onAllPass;

    /** Conditional jump: any verifier fails */
    private String onAnyFail;

    /** Timeout in milliseconds */
    private Long timeout;

    /** Retry policy override (optional) */
    private Integer maxRetries;

    /**
     * Check if this is a multi-agent step
     */
    public boolean isMultiAgent() {
        return agents != null && !agents.isEmpty();
    }

    /**
     * Resolve template variables in the prompt
     */
    public String resolvePrompt(String objective, List<String> constraints, PipelineContext context) {
        if (prompt == null) return "";
        String result = prompt
                .replace("{{objective}}", objective != null ? objective : "")
                .replace("{{constraints}}", constraints != null ? String.join(", ", constraints) : "");

        // Replace {{artifacts.xxx.summary}} and {{artifacts.xxx.files}}
        if (context != null && context.getArtifacts() != null) {
            for (Map.Entry<String, Artifact> entry : context.getArtifacts().entrySet()) {
                String sn = entry.getKey();
                Artifact art = entry.getValue();
                if (art == null) continue;
                result = result.replace("{{artifacts." + sn + ".summary}}", art.getSummary());
                result = result.replace("{{artifacts." + sn + ".files}}", formatFiles(art));
                result = result.replace("{{artifacts." + sn + ".data}}", String.valueOf(art.getData()));
            }
        }
        return result;
    }

    private String formatFiles(Artifact artifact) {
        if (artifact == null || artifact.getData() == null) return "";
        Object files = artifact.getData().get("files_changed");
        return files != null ? files.toString() : "";
    }

    /**
     * Determine next step based on execution result
     */
    public String determineNext(PipelineStepResult result) {
        if (result.isCritical()) {
            return onCritical != null ? onCritical : handoff;
        }
        if (result.isSuccess()) {
            if (onSuccess != null) return onSuccess;
            if (onAllPass != null) return onAllPass;
            return handoff;
        }
        if (result.isFailed()) {
            if (onAnyFail != null) return onAnyFail;
            return null;
        }
        return handoff;
    }
}
