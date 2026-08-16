package com.teammind.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PipelineDefinitionTest {

    private final Yaml yaml = new Yaml();

    @Test
    @DisplayName("parse simple pipeline from YAML")
    void shouldParseSimplePipeline() {
        String yamlStr = """
                name: "test-pipeline"
                description: "A test"
                steps:
                  - name: implement
                    role: LEAD
                    agent: codex
                    prompt: "{{objective}}"
                    output: CODE_DIFF
                    handoff: review
                  - name: review
                    role: REVIEWER
                    agent: claude-code
                    prompt: "Review"
                    output: REVIEW_FINDINGS
                    handoff: null
                retry:
                  max_attempts: 5
                  backoff_ms: 2000
                """;

        @SuppressWarnings("unchecked")
        Map<String, Object> map = yaml.load(yamlStr);
        PipelineDefinition def = parseDef(map);

        assertThat(def.getName()).isEqualTo("test-pipeline");
        assertThat(def.getSteps()).hasSize(2);
        assertThat(def.getSteps().get(0).getName()).isEqualTo("implement");
        assertThat(def.getSteps().get(0).getHandoff()).isEqualTo("review");
        assertThat(def.getSteps().get(1).getName()).isEqualTo("review");
        assertThat(def.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(def.getRetry().getBackoffMs()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("getStep returns correct step by name")
    void shouldFindStepByName() {
        PipelineDefinition def = PipelineDefinition.builder()
                .steps(List.of(
                        PipelineStepDefinition.builder().name("a").agent("codex").build(),
                        PipelineStepDefinition.builder().name("b").agent("claude").build()
                ))
                .build();

        assertThat(def.getStep("a").getAgent()).isEqualTo("codex");
        assertThat(def.getStep("b").getAgent()).isEqualTo("claude");
        assertThat(def.getStep("nonexistent")).isNull();
    }

    @Test
    @DisplayName("nextStepName follows handoff chain")
    void shouldFollowHandoffChain() {
        PipelineDefinition def = PipelineDefinition.builder()
                .steps(List.of(
                        PipelineStepDefinition.builder().name("implement").handoff("review").build(),
                        PipelineStepDefinition.builder().name("review").handoff("verify").build(),
                        PipelineStepDefinition.builder().name("verify").handoff(null).build()
                ))
                .build();

        assertThat(def.nextStepName("implement")).isEqualTo("review");
        assertThat(def.nextStepName("review")).isEqualTo("verify");
        assertThat(def.nextStepName("verify")).isNull();
        assertThat(def.nextStepName("missing")).isNull();
    }

    @Test
    @DisplayName("parse pipeline with multi-agent step")
    void shouldParseMultiAgentStep() {
        String yamlStr = """
                name: "verify-pipeline"
                steps:
                  - name: verify
                    role: VERIFIER
                    agents:
                      - git-verifier
                      - test-runner-verifier
                    output: EVIDENCE
                    on_all_pass: done
                    on_any_fail: implement
                """;

        @SuppressWarnings("unchecked")
        Map<String, Object> map = yaml.load(yamlStr);
        PipelineDefinition def = parseDef(map);

        PipelineStepDefinition verify = def.getSteps().get(0);
        assertThat(verify.getName()).isEqualTo("verify");
        assertThat(verify.isMultiAgent()).isTrue();
        assertThat(verify.getAgents()).containsExactly("git-verifier", "test-runner-verifier");
        assertThat(verify.getOnAllPass()).isEqualTo("done");
        assertThat(verify.getOnAnyFail()).isEqualTo("implement");
    }

    @Test
    @DisplayName("default retry policy when not specified")
    void shouldUseDefaultRetry() {
        String yamlStr = """
                name: "simple"
                steps:
                  - name: step1
                    agent: codex
                """;

        @SuppressWarnings("unchecked")
        Map<String, Object> map = yaml.load(yamlStr);
        PipelineDefinition def = parseDef(map);

        assertThat(def.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(def.getRetry().getBackoffMs()).isEqualTo(5000L);
    }

    private PipelineDefinition parseDef(Map<String, Object> map) {
        PipelineDefinition.PipelineDefinitionBuilder builder = PipelineDefinition.builder();
        builder.name(getString(map, "name"));
        builder.description(getString(map, "description"));

        var stepsRaw = getList(map.get("steps"));
        var steps = new java.util.ArrayList<PipelineStepDefinition>();
        for (Object s : stepsRaw) {
            if (s instanceof Map) steps.add(parseStep((Map<String, Object>) s));
        }
        builder.steps(steps);

        var retryMap = getMap(map.get("retry"));
        if (!retryMap.isEmpty()) {
            builder.retry(PipelineRetryPolicy.builder()
                    .maxAttempts(getInt(retryMap.get("max_attempts"), 3))
                    .backoffMs(getLong(retryMap.get("backoff_ms"), 5000L))
                    .build());
        } else {
            builder.retry(PipelineRetryPolicy.DEFAULT);
        }

        return builder.build();
    }

    private PipelineStepDefinition parseStep(Map<String, Object> map) {
        PipelineStepDefinition.PipelineStepDefinitionBuilder b = PipelineStepDefinition.builder();
        b.name(getString(map, "name"));
        b.role(getString(map, "role"));
        b.agent(getString(map, "agent"));
        b.prompt(getString(map, "prompt"));
        b.output(getString(map, "output"));
        b.handoff(getString(map, "handoff"));
        b.onCritical(getString(map, "on_critical"));
        b.onSuccess(getString(map, "on_success"));
        b.onAllPass(getString(map, "on_all_pass"));
        b.onAnyFail(getString(map, "on_any_fail"));

        var agentsRaw = getList(map.get("agents"));
        if (!agentsRaw.isEmpty()) {
            var agents = new java.util.ArrayList<String>();
            for (Object a : agentsRaw) if (a instanceof String) agents.add((String) a);
            b.agents(agents);
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : Map.of();
    }

    private int getInt(Object obj, int defaultVal) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        return defaultVal;
    }

    private long getLong(Object obj, long defaultVal) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        return defaultVal;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : null;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> getList(Object obj) {
        return obj instanceof java.util.List ? (java.util.List<Object>) obj : java.util.List.of();
    }
}
