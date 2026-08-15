package com.teammind.runtime;

import com.teammind.common.EvidenceStatus;
import com.teammind.common.EvidenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceLifecycleServiceTest {

    private EvidenceLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceLifecycleService();
    }

    @Nested
    @DisplayName("claim")
    class ClaimTests {

        @Test
        void shouldCreateClaimedEvidence() {
            var ev = service.claim("inv-001", EvidenceType.GIT_DIFF, "3 files changed");
            assertThat(ev.getStatus()).isEqualTo(EvidenceStatus.CLAIMED);
            assertThat(ev.getInvocationId()).isEqualTo("inv-001");
            assertThat(ev.getType()).isEqualTo(EvidenceType.GIT_DIFF);
            assertThat(ev.getDescription()).isEqualTo("3 files changed");
        }
    }

    @Nested
    @DisplayName("canTransition")
    class TransitionTests {

        @Test
        void shouldAllowCollectFromClaimed() {
            assertThat(service.canTransition(EvidenceStatus.CLAIMED, EvidenceStatus.COLLECTED)).isTrue();
        }

        @Test
        void shouldAllowVerifyFromCollected() {
            assertThat(service.canTransition(EvidenceStatus.COLLECTED, EvidenceStatus.VERIFIED)).isTrue();
        }

        @Test
        void shouldAllowInvalidateFromAny() {
            assertThat(service.canTransition(EvidenceStatus.CLAIMED, EvidenceStatus.INVALIDATED)).isTrue();
            assertThat(service.canTransition(EvidenceStatus.COLLECTED, EvidenceStatus.INVALIDATED)).isTrue();
            assertThat(service.canTransition(EvidenceStatus.VERIFIED, EvidenceStatus.INVALIDATED)).isTrue();
        }

        @Test
        void shouldRejectVerifyFromClaimed() {
            assertThat(service.canTransition(EvidenceStatus.CLAIMED, EvidenceStatus.VERIFIED)).isFalse();
        }

        @Test
        void shouldRejectCollectFromVerified() {
            assertThat(service.canTransition(EvidenceStatus.VERIFIED, EvidenceStatus.COLLECTED)).isFalse();
        }
    }

    @Nested
    @DisplayName("getAvailableCommands")
    class CommandsTests {

        @Test
        void shouldBeAbleToCollectFromClaimed() {
            var cmds = service.getAvailableCommands(EvidenceStatus.CLAIMED);
            assertThat(cmds).contains("collect");
        }

        @Test
        void shouldBeAbleToVerifyOrInvalidateFromCollected() {
            var cmds = service.getAvailableCommands(EvidenceStatus.COLLECTED);
            assertThat(cmds).contains("verify", "invalidate");
        }

        @Test
        void shouldBeAbleToInvalidateFromVerified() {
            var cmds = service.getAvailableCommands(EvidenceStatus.VERIFIED);
            assertThat(cmds).contains("invalidate");
        }

        @Test
        void shouldHaveNoCommandsForInvalidated() {
            var cmds = service.getAvailableCommands(EvidenceStatus.INVALIDATED);
            assertThat(cmds).isEmpty();
        }
    }
}
