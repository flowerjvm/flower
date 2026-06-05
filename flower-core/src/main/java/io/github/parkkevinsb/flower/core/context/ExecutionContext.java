package io.github.parkkevinsb.flower.core.context;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable identity attached to one logical Flow execution.
 *
 * <p>This is Flower's small "execution id card": who started the Flow, which
 * tenant/session/run it belongs to, and which trace/correlation ids should
 * follow it through logs, checkpoints, dumps, and future admin tooling.
 *
 * <p>It is intentionally not a business context. Do not store roles, policy
 * decisions, approval state, domain objects, or agent/action state here. Those
 * belong in the host application or higher-level modules.
 */
public final class ExecutionContext {

    private static final ExecutionContext EMPTY =
            new ExecutionContext(null, null, null, null, null, null);

    private final String tenantId;
    private final String userId;
    private final String sessionId;
    private final String runId;
    private final String traceId;
    private final String correlationId;

    private ExecutionContext(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String traceId,
            String correlationId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.runId = runId;
        this.traceId = traceId;
        this.correlationId = correlationId;
    }

    private ExecutionContext(Builder b) {
        this.tenantId = b.tenantId;
        this.userId = b.userId;
        this.sessionId = b.sessionId;
        this.runId = b.runId;
        this.traceId = b.traceId;
        this.correlationId = b.correlationId;
    }

    public static ExecutionContext empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .tenantId(tenantId)
                .userId(userId)
                .sessionId(sessionId)
                .runId(runId)
                .traceId(traceId)
                .correlationId(correlationId);
    }

    public Optional<String> tenantId() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<String> userId() {
        return Optional.ofNullable(userId);
    }

    public Optional<String> sessionId() {
        return Optional.ofNullable(sessionId);
    }

    public Optional<String> runId() {
        return Optional.ofNullable(runId);
    }

    public Optional<String> traceId() {
        return Optional.ofNullable(traceId);
    }

    public Optional<String> correlationId() {
        return Optional.ofNullable(correlationId);
    }

    public boolean isEmpty() {
        return tenantId == null
                && userId == null
                && sessionId == null
                && runId == null
                && traceId == null
                && correlationId == null;
    }

    public String tenantIdOrNull() {
        return tenantId;
    }

    public String userIdOrNull() {
        return userId;
    }

    public String sessionIdOrNull() {
        return sessionId;
    }

    public String runIdOrNull() {
        return runId;
    }

    public String traceIdOrNull() {
        return traceId;
    }

    public String correlationIdOrNull() {
        return correlationId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExecutionContext)) {
            return false;
        }
        ExecutionContext that = (ExecutionContext) o;
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(runId, that.runId)
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userId, sessionId, runId, traceId, correlationId);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "ExecutionContext{}";
        }
        StringBuilder sb = new StringBuilder("ExecutionContext{");
        append(sb, "tenantId", tenantId);
        append(sb, "userId", userId);
        append(sb, "sessionId", sessionId);
        append(sb, "runId", runId);
        append(sb, "traceId", traceId);
        append(sb, "correlationId", correlationId);
        sb.append('}');
        return sb.toString();
    }

    private static void append(StringBuilder sb, String name, String value) {
        if (value == null) {
            return;
        }
        if (sb.length() > "ExecutionContext{".length()) {
            sb.append(", ");
        }
        sb.append(name).append('=').append(value);
    }

    public static final class Builder {
        private String tenantId;
        private String userId;
        private String sessionId;
        private String runId;
        private String traceId;
        private String correlationId;

        private Builder() {
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = clean("tenantId", tenantId);
            return this;
        }

        public Builder userId(String userId) {
            this.userId = clean("userId", userId);
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = clean("sessionId", sessionId);
            return this;
        }

        public Builder runId(String runId) {
            this.runId = clean("runId", runId);
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = clean("traceId", traceId);
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = clean("correlationId", correlationId);
            return this;
        }

        public ExecutionContext build() {
            ExecutionContext ctx = new ExecutionContext(this);
            return ctx.isEmpty() ? EMPTY : ctx;
        }

        private static String clean(String name, String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return trimmed;
        }
    }
}
