package com.teammind.runtime;

import com.teammind.entity.Artifact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PipelineContextTest {

    @Test
    @DisplayName("recordHandoff adds to history")
    void shouldRecordHandoff() {
        PipelineContext ctx = PipelineContext.builder().build();
        ctx.recordHandoff("implement", "review", "success");
        assertThat(ctx.getHandoffHistory()).hasSize(1);
        assertThat(ctx.getHandoffHistory().get(0).getFromStep()).isEqualTo("implement");
        assertThat(ctx.getHandoffHistory().get(0).getToStep()).isEqualTo("review");
    }

    @Test
    @DisplayName("recordStepResult stores artifact and result")
    void shouldRecordStepResult() {
        Artifact art = Artifact.builder()
                .id("art-1").type("CODE_DIFF").summary("done").build();
        PipelineStepResult result = PipelineStepResult.builder()
                .stepName("implement").artifact(art).state("SUCCESS").build();

        PipelineContext ctx = PipelineContext.builder().build();
        ctx.recordStepResult("implement", result);

        assertThat(ctx.getArtifacts()).containsKey("implement");
        assertThat(ctx.getStepResults()).containsKey("implement");
        assertThat(ctx.getArtifacts().get("implement").getSummary()).isEqualTo("done");
    }

    @Test
    @DisplayName("getNextStep follows handoff chain")
    void shouldFollowHandoffChain() {
        PipelineDefinition def = PipelineDefinition.builder()
                .steps(List.of(
                        PipelineStepDefinition.builder().name("implement").handoff("review").build(),
                        PipelineStepDefinition.builder().name("review").handoff("verify").build(),
                        PipelineStepDefinition.builder().name("verify").handoff(null).build()
                ))
                .build();

        PipelineContext ctx = PipelineContext.builder()
                .currentStep("implement")
                .build();
        assertThat(ctx.getNextStep(def)).isEqualTo("review");

        ctx.setCurrentStep("review");
        assertThat(ctx.getNextStep(def)).isEqualTo("verify");

        ctx.setCurrentStep("verify");
        assertThat(ctx.getNextStep(def)).isNull();
    }

    @Test
    @DisplayName("getNextStep uses condition-based routing")
    void shouldUseConditionRouting() {
        PipelineStepDefinition reviewStep = PipelineStepDefinition.builder()
                .name("review").onCritical("approve").onSuccess("verify").build();
        PipelineDefinition def = PipelineDefinition.builder()
                .steps(List.of(reviewStep)).build();

        PipelineContext ctx = PipelineContext.builder()
                .currentStep("review")
                .build();

        // Critical result → approve
        ctx.recordStepResult("review", PipelineStepResult.builder().state("SUCCESS").critical(true).build());
        assertThat(ctx.getNextStep(def)).isEqualTo("approve");

        // Success result → verify
        ctx.recordStepResult("review", PipelineStepResult.builder().state("SUCCESS").critical(false).build());
        assertThat(ctx.getNextStep(def)).isEqualTo("verify");
    }

    @Test
    @DisplayName("isCompleted returns true after setting completedAt")
    void shouldTrackCompletion() {
        PipelineContext ctx = PipelineContext.builder().build();
        assertThat(ctx.isCompleted()).isFalse();
        ctx.setCompletedAt(LocalDateTime.now());
        assertThat(ctx.isCompleted()).isTrue();
    }
}
