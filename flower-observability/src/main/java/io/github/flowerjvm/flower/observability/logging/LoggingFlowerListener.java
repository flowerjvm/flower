package io.github.flowerjvm.flower.observability.logging;

import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * {@link FlowerListener} that emits SLF4J log entries for every Flow / Step
 * lifecycle event.
 *
 * <p>Levels (configurable via {@link Builder}):
 * <ul>
 *   <li>{@code INFO}  - flow submitted / finished / cancelled</li>
 *   <li>{@code DEBUG} - step entered / exited</li>
 *   <li>{@code WARN}  - flow failed (also logs the cause)</li>
 * </ul>
 *
 * <p>Each invocation populates {@link LoggingMdcKeys} so structured log
 * appenders can index by flow type, flow key, step id and step no. Existing
 * MDC entries are restored after the call returns.
 *
 * <p>The listener is invoked from the Worker tick thread, so it must be cheap.
 * SLF4J's parameterized logging keeps allocations away from the hot path when
 * the level is disabled.
 */
public final class LoggingFlowerListener implements FlowerListener {

    private final Logger log;
    private final Level lifecycleLevel;
    private final Level stepLevel;
    private final Level failureLevel;

    public enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    public LoggingFlowerListener() {
        this(builder());
    }

    private LoggingFlowerListener(Builder b) {
        this.log = b.logger != null ? b.logger : LoggerFactory.getLogger("flower");
        this.lifecycleLevel = b.lifecycleLevel;
        this.stepLevel = b.stepLevel;
        this.failureLevel = b.failureLevel;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void onFlowSubmitted(FlowSnapshot flow) {
        if (!isEnabled(lifecycleLevel)) return;
        MdcScope scope = MdcScope.openFlow(flow);
        try {
            emit(lifecycleLevel, "flow submitted: {}", flow.flowId());
        } finally {
            scope.close();
        }
    }

    @Override
    public void onStepEntered(FlowSnapshot flow, String stepId) {
        if (!isEnabled(stepLevel)) return;
        MdcScope scope = MdcScope.openStep(flow, stepId);
        try {
            emit(stepLevel, "step entered: {} @{}", flow.flowId(), stepId);
        } finally {
            scope.close();
        }
    }

    @Override
    public void onStepExited(FlowSnapshot flow, String stepId) {
        if (!isEnabled(stepLevel)) return;
        MdcScope scope = MdcScope.openStep(flow, stepId);
        try {
            emit(stepLevel, "step exited: {} @{}", flow.flowId(), stepId);
        } finally {
            scope.close();
        }
    }

    @Override
    public void onFlowFinished(FlowSnapshot flow) {
        if (!isEnabled(lifecycleLevel)) return;
        MdcScope scope = MdcScope.openFlow(flow);
        try {
            emit(lifecycleLevel, "flow finished: {}", flow.flowId());
        } finally {
            scope.close();
        }
    }

    @Override
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        if (!isEnabled(failureLevel)) return;
        MdcScope scope = MdcScope.openFlow(flow);
        try {
            emitWithThrowable(failureLevel, "flow failed: {}", flow.flowId(), cause);
        } finally {
            scope.close();
        }
    }

    @Override
    public void onFlowCancelled(FlowSnapshot flow) {
        if (!isEnabled(lifecycleLevel)) return;
        MdcScope scope = MdcScope.openFlow(flow);
        try {
            emit(lifecycleLevel, "flow cancelled: {}", flow.flowId());
        } finally {
            scope.close();
        }
    }

    private boolean isEnabled(Level level) {
        switch (level) {
            case TRACE: return log.isTraceEnabled();
            case DEBUG: return log.isDebugEnabled();
            case INFO:  return log.isInfoEnabled();
            case WARN:  return log.isWarnEnabled();
            case ERROR: return log.isErrorEnabled();
            default: return false;
        }
    }

    private void emit(Level level, String fmt, Object a1) {
        switch (level) {
            case TRACE: log.trace(fmt, a1); break;
            case DEBUG: log.debug(fmt, a1); break;
            case INFO:  log.info(fmt, a1);  break;
            case WARN:  log.warn(fmt, a1);  break;
            case ERROR: log.error(fmt, a1); break;
        }
    }

    private void emit(Level level, String fmt, Object a1, Object a2) {
        switch (level) {
            case TRACE: log.trace(fmt, a1, a2); break;
            case DEBUG: log.debug(fmt, a1, a2); break;
            case INFO:  log.info(fmt, a1, a2);  break;
            case WARN:  log.warn(fmt, a1, a2);  break;
            case ERROR: log.error(fmt, a1, a2); break;
        }
    }

    private void emitWithThrowable(Level level, String fmt, Object a1, Throwable t) {
        switch (level) {
            case TRACE: log.trace(fmt, a1, t); break;
            case DEBUG: log.debug(fmt, a1, t); break;
            case INFO:  log.info(fmt, a1, t);  break;
            case WARN:  log.warn(fmt, a1, t);  break;
            case ERROR: log.error(fmt, a1, t); break;
        }
    }

    /**
     * Save / restore wrapper around {@link MDC} so listener calls do not leak
     * MDC entries into surrounding application code on the same thread.
     */
    private static final class MdcScope {
        private final String prevType;
        private final String prevKey;
        private final String prevStepId;
        private final String prevStepNo;

        private MdcScope() {
            this.prevType = MDC.get(LoggingMdcKeys.FLOW_TYPE);
            this.prevKey = MDC.get(LoggingMdcKeys.FLOW_KEY);
            this.prevStepId = MDC.get(LoggingMdcKeys.STEP_ID);
            this.prevStepNo = MDC.get(LoggingMdcKeys.STEP_NO);
        }

        static MdcScope openFlow(FlowSnapshot flow) {
            MdcScope s = new MdcScope();
            MDC.put(LoggingMdcKeys.FLOW_TYPE, flow.flowId().flowType());
            MDC.put(LoggingMdcKeys.FLOW_KEY, flow.flowId().flowKey());
            return s;
        }

        static MdcScope openStep(FlowSnapshot flow, String stepId) {
            MdcScope s = openFlow(flow);
            if (stepId != null) {
                MDC.put(LoggingMdcKeys.STEP_ID, stepId);
            }
            MDC.put(LoggingMdcKeys.STEP_NO, Integer.toString(flow.currentStepNo()));
            return s;
        }

        void close() {
            restore(LoggingMdcKeys.FLOW_TYPE, prevType);
            restore(LoggingMdcKeys.FLOW_KEY, prevKey);
            restore(LoggingMdcKeys.STEP_ID, prevStepId);
            restore(LoggingMdcKeys.STEP_NO, prevStepNo);
        }

        private static void restore(String key, String prev) {
            if (prev == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, prev);
            }
        }
    }

    public static final class Builder {
        private Logger logger;
        private Level lifecycleLevel = Level.INFO;
        private Level stepLevel = Level.DEBUG;
        private Level failureLevel = Level.WARN;

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder loggerName(String name) {
            this.logger = LoggerFactory.getLogger(name);
            return this;
        }

        public Builder lifecycleLevel(Level level) {
            this.lifecycleLevel = required(level, "lifecycleLevel");
            return this;
        }

        public Builder stepLevel(Level level) {
            this.stepLevel = required(level, "stepLevel");
            return this;
        }

        public Builder failureLevel(Level level) {
            this.failureLevel = required(level, "failureLevel");
            return this;
        }

        public LoggingFlowerListener build() {
            return new LoggingFlowerListener(this);
        }

        private static Level required(Level level, String name) {
            if (level == null) {
                throw new IllegalArgumentException(name + " must not be null");
            }
            return level;
        }
    }
}
