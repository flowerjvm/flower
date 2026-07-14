package io.github.flowerjvm.flower.observability.logging;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.observability.support.CapturingLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFlowerListenerTest {

    private final CapturingLogger logger = new CapturingLogger();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void onFlowSubmittedLogsAtLifecycleLevelWithMdc() {
        LoggingFlowerListener listener = LoggingFlowerListener.builder()
                .logger(logger)
                .build();
        FlowSnapshot snap = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);

        listener.onFlowSubmitted(snap);

        assertThat(logger.records()).hasSize(1);
        CapturingLogger.Record r = logger.records().get(0);
        assertThat(r.level).isEqualTo(Level.INFO);
        assertThat(r.message).isEqualTo("flow submitted: {}");
        assertThat(r.mdc)
                .containsEntry(LoggingMdcKeys.FLOW_TYPE, "quay-work")
                .containsEntry(LoggingMdcKeys.FLOW_KEY, "WO-1");
    }

    @Test
    void onStepEnteredLogsAtStepLevelWithStepMdc() {
        LoggingFlowerListener listener = LoggingFlowerListener.builder()
                .logger(logger)
                .build();
        FlowSnapshot snap = snapshot("quay-work", "WO-1", FlowState.RUNNING, "execute-sts", 10);

        listener.onStepEntered(snap, "execute-sts");

        assertThat(logger.records()).hasSize(1);
        CapturingLogger.Record r = logger.records().get(0);
        assertThat(r.level).isEqualTo(Level.DEBUG);
        assertThat(r.mdc)
                .containsEntry(LoggingMdcKeys.FLOW_TYPE, "quay-work")
                .containsEntry(LoggingMdcKeys.FLOW_KEY, "WO-1")
                .containsEntry(LoggingMdcKeys.STEP_ID, "execute-sts")
                .containsEntry(LoggingMdcKeys.STEP_NO, "10");
    }

    @Test
    void onFlowFailedLogsAtFailureLevelWithThrowable() {
        LoggingFlowerListener listener = LoggingFlowerListener.builder()
                .logger(logger)
                .build();
        FlowSnapshot snap = snapshot("quay-work", "WO-1", FlowState.FAILED, null, 0);
        IllegalStateException cause = new IllegalStateException("boom");

        listener.onFlowFailed(snap, cause);

        assertThat(logger.records()).hasSize(1);
        CapturingLogger.Record r = logger.records().get(0);
        assertThat(r.level).isEqualTo(Level.WARN);
        assertThat(r.throwable).isSameAs(cause);
    }

    @Test
    void mdcIsRestoredAfterCall() {
        MDC.put(LoggingMdcKeys.FLOW_TYPE, "preexisting");
        MDC.put("unrelated", "keep-me");
        LoggingFlowerListener listener = LoggingFlowerListener.builder()
                .logger(logger)
                .build();
        FlowSnapshot snap = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);

        listener.onFlowSubmitted(snap);

        assertThat(MDC.get(LoggingMdcKeys.FLOW_TYPE)).isEqualTo("preexisting");
        assertThat(MDC.get(LoggingMdcKeys.FLOW_KEY)).isNull();
        assertThat(MDC.get("unrelated")).isEqualTo("keep-me");
    }

    @Test
    void customLevelsAreRespected() {
        LoggingFlowerListener listener = LoggingFlowerListener.builder()
                .logger(logger)
                .lifecycleLevel(LoggingFlowerListener.Level.DEBUG)
                .stepLevel(LoggingFlowerListener.Level.TRACE)
                .failureLevel(LoggingFlowerListener.Level.ERROR)
                .build();
        FlowSnapshot snap = snapshot("quay-work", "WO-1", FlowState.RUNNING, "s", 0);

        listener.onFlowSubmitted(snap);
        listener.onStepEntered(snap, "s");
        listener.onFlowFailed(snap, new RuntimeException("x"));

        assertThat(logger.records()).extracting(rec -> rec.level)
                .containsExactly(Level.DEBUG, Level.TRACE, Level.ERROR);
    }

    private static FlowSnapshot snapshot(String flowType, String flowKey, FlowState state,
                                         String currentStepId, int currentStepNo) {
        return new FlowSnapshot(FlowId.of(flowType, flowKey), state, currentStepId, currentStepNo, null);
    }
}
