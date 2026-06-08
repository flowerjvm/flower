package io.github.parkkevinsb.flower.testkit;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;

/**
 * Small factories for readable test execution contexts.
 */
public final class TestExecutionContexts {

    private TestExecutionContexts() {
    }

    public static ExecutionContext empty() {
        return ExecutionContext.empty();
    }

    public static ExecutionContext of(String tenantId) {
        return tenant(tenantId);
    }

    public static ExecutionContext tenant(String tenantId) {
        return ExecutionContext.builder()
                .tenantId(tenantId)
                .build();
    }

    public static ExecutionContext run(String runId) {
        return ExecutionContext.builder()
                .runId(runId)
                .build();
    }

    public static ExecutionContext tenantRun(String tenantId, String runId) {
        return ExecutionContext.builder()
                .tenantId(tenantId)
                .runId(runId)
                .build();
    }

    public static ExecutionContext basic(String tenantId, String userId, String runId) {
        return ExecutionContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .runId(runId)
                .build();
    }

    public static ExecutionContext full(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String traceId,
            String correlationId) {
        return ExecutionContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .sessionId(sessionId)
                .runId(runId)
                .traceId(traceId)
                .correlationId(correlationId)
                .build();
    }
}
