package io.github.flowerjvm.flower.testkit;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestExecutionContextsTest {

    @Test
    void creates_readable_contexts_for_tests() {
        ExecutionContext context = TestExecutionContexts.basic("tenant-a", "user-1", "run-1");

        assertThat(context.tenantId()).contains("tenant-a");
        assertThat(context.userId()).contains("user-1");
        assertThat(context.runId()).contains("run-1");
    }
}
