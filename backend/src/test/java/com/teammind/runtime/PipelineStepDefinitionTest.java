package com.teammind.runtime;

import com.teammind.entity.Artifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PipelineStepDefinitionTest {

    private PipelineStepDefinition step;

    @BeforeEach
    void setUp() {
        step = PipelineStepDefinition.builder()
                .name("review")
                .role("REVIEWER")
                .agent("claude-code")
                .prompt("Review {{objective}} with files {{artifacts.implement.files}}")
                .output("REVIEW_FINDINGS")
                .handoff("verify")
                .onCritical("request_approval")
                .onSuccess("verify")
                .build();
    }

    @Nested
    @DisplayName("resolvePrompt")
    class ResolvePromptTests {

        @Test
        void shouldReplaceObjectiveAndConstraints() {
            PipelineContext ctx = PipelineContext.builder().build();
            String result = step.resolvePrompt("add auth module", List.of("security", "test"), ctx);
            assertThat(result).contains("add auth module");
        }

        @Test
        void shouldReturnEmptyWhenPromptIsNull() {
            PipelineStepDefinition noPrompt = PipelineStepDefinition.builder().prompt(null).build();
            PipelineContext ctx = PipelineContext.builder().build();
            assertThat(noPrompt.resolvePrompt("obj", null, ctx)).isEmpty();
        }
    }

    @Nested
    @DisplayName("determineNext")
    class DetermineNextTests {

        @Test
        void shouldJumpToOnCritical() {
            PipelineStepResult critical = PipelineStepResult.builder()
                    .state("SUCCESS").critical(true).build();
            assertThat(step.determineNext(critical)).isEqualTo("request_approval");
        }

        @Test
        void shouldFollowOnSuccess() {
            PipelineStepResult success = PipelineStepResult.builder()
                    .state("SUCCESS").critical(false).build();
            assertThat(step.determineNext(success)).isEqualTo("verify");
        }

        @Test
        void shouldReturnNullWhenFailedAndNoOnAnyFail() {
            PipelineStepDefinition noFallback = PipelineStepDefinition.builder()
                    .handoff("next").onAnyFail(null).build();
            PipelineStepResult failed = PipelineStepResult.builder()
                    .state("FAILED").build();
            assertThat(noFallback.determineNext(failed)).isNull();
        }
    }

    @Test
    void isMultiAgent_shouldReturnTrueWhenAgentsSet() {
        PipelineStepDefinition multi = PipelineStepDefinition.builder()
                .agents(List.of("git-verifier", "test-runner-verifier")).build();
        assertThat(multi.isMultiAgent()).isTrue();
    }

    @Test
    void isMultiAgent_shouldReturnFalseWhenNoAgents() {
        PipelineStepDefinition single = PipelineStepDefinition.builder()
                .agent("codex").build();
        assertThat(single.isMultiAgent()).isFalse();
    }
}
