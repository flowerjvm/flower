package io.github.flowerjvm.flower.observability.logging;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.step.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Convenience logger for use inside a {@link io.github.flowerjvm.flower.core.step.Step}.
 *
 * <p>Wraps an SLF4J {@link Logger} and ensures that every emitted log entry
 * carries the MDC keys defined in {@link LoggingMdcKeys}, derived from the
 * supplied {@link StepContext}. This is the "stepLog" helper users can use
 * instead of manually populating MDC inside each Step.
 *
 * <p>Typical usage:
 * <pre>{@code
 * private static final Logger LOG = LoggerFactory.getLogger(MyStep.class);
 *
 * @Override
 * protected StepResult onTick(StepContext ctx) {
 *     StepLogger.with(LOG, ctx).info("dispatching command");
 *     ...
 * }
 * }</pre>
 *
 * <p>{@code StepLogger} is a short-lived value object - construct one per
 * call site rather than caching it. The wrapped {@link Logger} should still
 * be cached as a {@code static final} field.
 */
public final class StepLogger {

    private final Logger log;
    private final FlowId flowId;
    private final String stepId;
    private final int stepNo;

    private StepLogger(Logger log, FlowId flowId, String stepId, int stepNo) {
        this.log = log;
        this.flowId = flowId;
        this.stepId = stepId;
        this.stepNo = stepNo;
    }

    /**
     * Bind a logger to the current Step / Flow context.
     */
    public static StepLogger with(Logger log, StepContext ctx) {
        if (log == null) {
            throw new IllegalArgumentException("logger must not be null");
        }
        if (ctx == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return new StepLogger(log, ctx.flowId(), ctx.currentStepId(), ctx.stepNo());
    }

    /**
     * Convenience for callers who already have an SLF4J binding by class.
     */
    public static StepLogger of(Class<?> source, StepContext ctx) {
        return with(LoggerFactory.getLogger(source), ctx);
    }

    public void trace(String msg) { run(Level.TRACE, msg, EMPTY, null); }
    public void trace(String fmt, Object arg) { run(Level.TRACE, fmt, new Object[]{arg}, null); }
    public void trace(String fmt, Object a1, Object a2) { run(Level.TRACE, fmt, new Object[]{a1, a2}, null); }
    public void trace(String fmt, Object... args) { run(Level.TRACE, fmt, args, null); }

    public void debug(String msg) { run(Level.DEBUG, msg, EMPTY, null); }
    public void debug(String fmt, Object arg) { run(Level.DEBUG, fmt, new Object[]{arg}, null); }
    public void debug(String fmt, Object a1, Object a2) { run(Level.DEBUG, fmt, new Object[]{a1, a2}, null); }
    public void debug(String fmt, Object... args) { run(Level.DEBUG, fmt, args, null); }

    public void info(String msg) { run(Level.INFO, msg, EMPTY, null); }
    public void info(String fmt, Object arg) { run(Level.INFO, fmt, new Object[]{arg}, null); }
    public void info(String fmt, Object a1, Object a2) { run(Level.INFO, fmt, new Object[]{a1, a2}, null); }
    public void info(String fmt, Object... args) { run(Level.INFO, fmt, args, null); }

    public void warn(String msg) { run(Level.WARN, msg, EMPTY, null); }
    public void warn(String fmt, Object arg) { run(Level.WARN, fmt, new Object[]{arg}, null); }
    public void warn(String fmt, Object a1, Object a2) { run(Level.WARN, fmt, new Object[]{a1, a2}, null); }
    public void warn(String fmt, Object... args) { run(Level.WARN, fmt, args, null); }
    public void warn(String msg, Throwable t) { run(Level.WARN, msg, EMPTY, t); }

    public void error(String msg) { run(Level.ERROR, msg, EMPTY, null); }
    public void error(String fmt, Object arg) { run(Level.ERROR, fmt, new Object[]{arg}, null); }
    public void error(String fmt, Object a1, Object a2) { run(Level.ERROR, fmt, new Object[]{a1, a2}, null); }
    public void error(String fmt, Object... args) { run(Level.ERROR, fmt, args, null); }
    public void error(String msg, Throwable t) { run(Level.ERROR, msg, EMPTY, t); }

    private static final Object[] EMPTY = new Object[0];

    private enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    private void run(Level level, String fmt, Object[] args, Throwable t) {
        if (!isEnabled(level)) return;
        String prevType = MDC.get(LoggingMdcKeys.FLOW_TYPE);
        String prevKey = MDC.get(LoggingMdcKeys.FLOW_KEY);
        String prevStepId = MDC.get(LoggingMdcKeys.STEP_ID);
        String prevStepNo = MDC.get(LoggingMdcKeys.STEP_NO);
        MDC.put(LoggingMdcKeys.FLOW_TYPE, flowId.flowType());
        MDC.put(LoggingMdcKeys.FLOW_KEY, flowId.flowKey());
        if (stepId != null) {
            MDC.put(LoggingMdcKeys.STEP_ID, stepId);
        }
        MDC.put(LoggingMdcKeys.STEP_NO, Integer.toString(stepNo));
        try {
            if (t != null) {
                emitWithThrowable(level, fmt, t);
            } else {
                emit(level, fmt, args);
            }
        } finally {
            restore(LoggingMdcKeys.FLOW_TYPE, prevType);
            restore(LoggingMdcKeys.FLOW_KEY, prevKey);
            restore(LoggingMdcKeys.STEP_ID, prevStepId);
            restore(LoggingMdcKeys.STEP_NO, prevStepNo);
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

    private void emit(Level level, String fmt, Object[] args) {
        switch (level) {
            case TRACE: log.trace(fmt, args); break;
            case DEBUG: log.debug(fmt, args); break;
            case INFO:  log.info(fmt, args);  break;
            case WARN:  log.warn(fmt, args);  break;
            case ERROR: log.error(fmt, args); break;
        }
    }

    private void emitWithThrowable(Level level, String msg, Throwable t) {
        switch (level) {
            case TRACE: log.trace(msg, t); break;
            case DEBUG: log.debug(msg, t); break;
            case INFO:  log.info(msg, t);  break;
            case WARN:  log.warn(msg, t);  break;
            case ERROR: log.error(msg, t); break;
        }
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, prev);
        }
    }
}
