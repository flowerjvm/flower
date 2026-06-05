package io.github.parkkevinsb.flower.core.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {

    @Test
    void empty_is_null_object() {
        ExecutionContext ctx = ExecutionContext.empty();

        assertThat(ctx).isNotNull();
        assertThat(ctx.isEmpty()).isTrue();
        assertThat(ctx.tenantId()).isEmpty();
        assertThat(ctx.runId()).isEmpty();
    }

    @Test
    void builder_keeps_stable_execution_identity() {
        ExecutionContext ctx = ExecutionContext.builder()
                .tenantId(" tenant-a ")
                .userId("user-1")
                .sessionId("session-1")
                .runId("run-1")
                .traceId("trace-1")
                .correlationId("corr-1")
                .build();

        assertThat(ctx.tenantId()).contains("tenant-a");
        assertThat(ctx.userId()).contains("user-1");
        assertThat(ctx.sessionId()).contains("session-1");
        assertThat(ctx.runId()).contains("run-1");
        assertThat(ctx.traceId()).contains("trace-1");
        assertThat(ctx.correlationId()).contains("corr-1");
        assertThat(ctx.isEmpty()).isFalse();
    }

    @Test
    void empty_values_are_rejected() {
        assertThatThrownBy(() -> ExecutionContext.builder().tenantId(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }
}
