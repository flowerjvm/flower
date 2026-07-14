package io.github.flowerjvm.flower.observability.support;

import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SLF4J test logger that records every emitted call along with a snapshot of
 * the MDC at the moment the listener invoked it. Built on
 * {@link AbstractLogger} so all of SLF4J's level-specific overloads are
 * funneled through a single capture method.
 */
public final class CapturingLogger extends AbstractLogger {

    public static final class Record {
        public final Level level;
        public final String message;
        public final Object[] args;
        public final Throwable throwable;
        public final Map<String, String> mdc;

        Record(Level level, String message, Object[] args, Throwable throwable, Map<String, String> mdc) {
            this.level = level;
            this.message = message;
            this.args = args;
            this.throwable = throwable;
            this.mdc = mdc;
        }
    }

    private final List<Record> records = new ArrayList<>();

    public CapturingLogger() {
        this("test");
    }

    public CapturingLogger(String name) {
        this.name = name;
    }

    public List<Record> records() {
        return Collections.unmodifiableList(records);
    }

    @Override
    protected void handleNormalizedLoggingCall(
            Level level, Marker marker, String message, Object[] args, Throwable throwable) {
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        if (snapshot == null) snapshot = new HashMap<>();
        records.add(new Record(level, message, args == null ? new Object[0] : args.clone(),
                throwable, snapshot));
    }

    @Override protected String getFullyQualifiedCallerName() { return null; }

    @Override public boolean isTraceEnabled() { return true; }
    @Override public boolean isTraceEnabled(Marker marker) { return true; }
    @Override public boolean isDebugEnabled() { return true; }
    @Override public boolean isDebugEnabled(Marker marker) { return true; }
    @Override public boolean isInfoEnabled() { return true; }
    @Override public boolean isInfoEnabled(Marker marker) { return true; }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public boolean isWarnEnabled(Marker marker) { return true; }
    @Override public boolean isErrorEnabled() { return true; }
    @Override public boolean isErrorEnabled(Marker marker) { return true; }
}
