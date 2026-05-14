package io.github.parkkevinsb.flower.observability.logging;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.observability.support.CapturingLogger;
import io.github.parkkevinsb.flower.observability.support.FakeStepContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepLoggerTest {

    private final CapturingLogger logger = new CapturingLogger();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void emitsMessageWithFlowAndStepMdc() {
        FakeStepContext ctx = new FakeStepContext(FlowId.of("quay-work", "WO-1"), "execute-sts", 10);

        StepLogger.with(logger, ctx).info("dispatching {}", "command");

        assertThat(logger.records()).hasSize(1);
        CapturingLogger.Record r = logger.records().get(0);
        assertThat(r.level).isEqualTo(Level.INFO);
        assertThat(r.message).isEqualTo("dispatching {}");
        assertThat(r.args).containsExactly("command");
        assertThat(r.mdc)
                .containsEntry(LoggingMdcKeys.FLOW_TYPE, "quay-work")
                .containsEntry(LoggingMdcKeys.FLOW_KEY, "WO-1")
                .containsEntry(LoggingMdcKeys.STEP_ID, "execute-sts")
                .containsEntry(LoggingMdcKeys.STEP_NO, "10");
    }

    @Test
    void mdcIsRemovedAfterCall() {
        FakeStepContext ctx = new FakeStepContext(FlowId.of("t", "k"), "s", 0);

        StepLogger.with(logger, ctx).debug("hi");

        assertThat(MDC.get(LoggingMdcKeys.FLOW_TYPE)).isNull();
        assertThat(MDC.get(LoggingMdcKeys.FLOW_KEY)).isNull();
        assertThat(MDC.get(LoggingMdcKeys.STEP_ID)).isNull();
        assertThat(MDC.get(LoggingMdcKeys.STEP_NO)).isNull();
    }

    @Test
    void preexistingMdcIsRestoredAfterCall() {
        MDC.put(LoggingMdcKeys.FLOW_TYPE, "outer-type");
        MDC.put("custom", "value");
        FakeStepContext ctx = new FakeStepContext(FlowId.of("inner", "k"), "s", 0);

        StepLogger.with(logger, ctx).warn("hi");

        assertThat(MDC.get(LoggingMdcKeys.FLOW_TYPE)).isEqualTo("outer-type");
        assertThat(MDC.get("custom")).isEqualTo("value");
    }

    @Test
    void throwableOverloadIsRecorded() {
        FakeStepContext ctx = new FakeStepContext(FlowId.of("t", "k"), "s", 0);
        IllegalStateException cause = new IllegalStateException("boom");

        StepLogger.with(logger, ctx).error("failure", cause);

        CapturingLogger.Record r = logger.records().get(0);
        assertThat(r.level).isEqualTo(Level.ERROR);
        assertThat(r.message).isEqualTo("failure");
        assertThat(r.throwable).isSameAs(cause);
    }

    @Test
    void rejectsNullArgs() {
        FakeStepContext ctx = new FakeStepContext(FlowId.of("t", "k"), "s", 0);
        assertThatThrownBy(() -> StepLogger.with(null, ctx))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StepLogger.with(logger, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
