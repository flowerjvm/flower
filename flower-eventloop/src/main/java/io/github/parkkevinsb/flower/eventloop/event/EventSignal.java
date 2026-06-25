package io.github.parkkevinsb.flower.eventloop.event;

/**
 * Built-in event type for named external callbacks.
 *
 * <p>Signals let durable event flows wait on data-shaped correlation keys such
 * as {@code ("tool-call", callId)} or {@code ("human-approval", approvalId)}
 * without storing predicate lambdas in a checkpoint.
 */
public final class EventSignal {

    private final String name;
    private final String key;
    private final Object payload;

    private EventSignal(String name, String key, Object payload) {
        validateSignalPart("signal name", name);
        validateSignalPart("signal key", key);
        this.name = name;
        this.key = key;
        this.payload = payload;
    }

    public static EventSignal of(String name, String key) {
        return new EventSignal(name, key, null);
    }

    public static EventSignal of(String name, String key, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return new EventSignal(name, key, payload);
    }

    public String name() {
        return name;
    }

    public String key() {
        return key;
    }

    public boolean hasPayload() {
        return payload != null;
    }

    public Object payload() {
        return payload;
    }

    public <T> T payload(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return payload == null ? null : type.cast(payload);
    }

    @Override
    public String toString() {
        return "EventSignal{name='" + name + "', key='" + key
                + "', hasPayload=" + (payload != null) + "}";
    }

    private static void validateSignalPart(String label, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
    }
}
