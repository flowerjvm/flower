package io.github.flowerjvm.flower.eventloop.persistence;

import io.github.flowerjvm.flower.eventloop.flow.EventFlow;

import java.util.Objects;

/**
 * Durable description of one await condition registered by an {@link EventFlow}.
 *
 * <p>This stores data that can be written to a checkpoint. Runtime artifacts
 * such as {@code Subscription}, predicate lambdas, or Java object instances are
 * intentionally not stored here.
 */
public final class EventAwaitCheckpoint {

    public enum Type { EVENT, SIGNAL, DEADLINE }

    private static final long NO_DEADLINE = Long.MIN_VALUE;

    private final Type type;
    private final String eventTypeName;
    private final String signalName;
    private final String signalKey;
    private final long deadlineAtMillis;

    private EventAwaitCheckpoint(
            Type type,
            String eventTypeName,
            String signalName,
            String signalKey,
            long deadlineAtMillis) {
        this.type = type;
        this.eventTypeName = eventTypeName;
        this.signalName = signalName;
        this.signalKey = signalKey;
        this.deadlineAtMillis = deadlineAtMillis;
    }

    public static EventAwaitCheckpoint event(String eventTypeName) {
        if (eventTypeName == null || eventTypeName.isEmpty()) {
            throw new IllegalArgumentException("eventTypeName must not be null or empty");
        }
        return new EventAwaitCheckpoint(Type.EVENT, eventTypeName, null, null, NO_DEADLINE);
    }

    public static EventAwaitCheckpoint signal(String signalName, String signalKey) {
        validateSignalPart("signalName", signalName);
        validateSignalPart("signalKey", signalKey);
        return new EventAwaitCheckpoint(Type.SIGNAL, null, signalName, signalKey, NO_DEADLINE);
    }

    public static EventAwaitCheckpoint deadline(long deadlineAtMillis) {
        if (deadlineAtMillis < 0L) {
            throw new IllegalArgumentException("deadlineAtMillis must not be negative: " + deadlineAtMillis);
        }
        return new EventAwaitCheckpoint(Type.DEADLINE, null, null, null, deadlineAtMillis);
    }

    public Type type() {
        return type;
    }

    public String eventTypeName() {
        return eventTypeName;
    }

    public String signalName() {
        return signalName;
    }

    public String signalKey() {
        return signalKey;
    }

    public long deadlineAtMillis() {
        return deadlineAtMillis;
    }

    @Override
    public String toString() {
        if (type == Type.EVENT) {
            return "EventAwaitCheckpoint{event=" + eventTypeName + "}";
        }
        if (type == Type.SIGNAL) {
            return "EventAwaitCheckpoint{signal=" + signalName + ", key=" + signalKey + "}";
        }
        return "EventAwaitCheckpoint{deadlineAtMillis=" + deadlineAtMillis + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EventAwaitCheckpoint)) {
            return false;
        }
        EventAwaitCheckpoint that = (EventAwaitCheckpoint) o;
        return deadlineAtMillis == that.deadlineAtMillis
                && type == that.type
                && Objects.equals(eventTypeName, that.eventTypeName)
                && Objects.equals(signalName, that.signalName)
                && Objects.equals(signalKey, that.signalKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, eventTypeName, signalName, signalKey, deadlineAtMillis);
    }

    private static void validateSignalPart(String label, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
    }
}
