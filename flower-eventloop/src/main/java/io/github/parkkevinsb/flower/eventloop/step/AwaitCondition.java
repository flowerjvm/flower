package io.github.parkkevinsb.flower.eventloop.step;

import io.github.parkkevinsb.flower.eventloop.event.EventSignal;

import java.util.function.Predicate;

/**
 * Declares what should wake a waiting {@link EventStep}.
 *
 * <p>This is the core idea that separates the event-driven runtime from the
 * tick-driven {@code flower-core} model. Instead of returning
 * {@code StepResult.stay()} to mean "tick me again later", an event step
 * returns {@link EventStepResult#await(AwaitCondition...)} to say
 * "wake me when one of these conditions fires".
 *
 * <p>Three condition kinds are supported in this experimental runtime:
 *
 * <ul>
 *   <li>{@link Event}: wake when an event of the given runtime type is
 *   published on the {@code EventBus} (exact-type match, same as core).</li>
 *   <li>{@link Signal}: wake when an external callback publishes a named
 *   signal with a matching correlation key.</li>
 *   <li>{@link Deadline}: wake when the {@code Clock} reaches a deadline
 *   relative to the moment the await is registered.</li>
 * </ul>
 */
public abstract class AwaitCondition {

    private AwaitCondition() {
    }

    /** Wake when an event whose runtime type equals {@code eventType} is published. */
    public static AwaitCondition event(Class<?> eventType) {
        return new Event(eventType, null);
    }

    /**
     * Wake when an event whose runtime type equals {@code eventType} is
     * published and {@code predicate} accepts it.
     */
    public static <E> AwaitCondition event(Class<E> eventType, Predicate<? super E> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("predicate must not be null");
        }
        final Class<E> type = eventType;
        return new Event(eventType, new Predicate<Object>() {
            @Override
            public boolean test(Object event) {
                return predicate.test(type.cast(event));
            }
        });
    }

    /** Wake when a named external signal with the given key is published. */
    public static AwaitCondition signal(String name, String key) {
        return new Signal(name, key);
    }

    /** Wake when {@code millisFromNow} have elapsed on the worker's clock. */
    public static AwaitCondition deadlineIn(long millisFromNow) {
        return new Deadline(millisFromNow);
    }

    /** Wake on an event of an exact runtime type. */
    public static final class Event extends AwaitCondition {
        private final Class<?> eventType;
        private final Predicate<Object> predicate;

        Event(Class<?> eventType, Predicate<Object> predicate) {
            if (eventType == null) {
                throw new IllegalArgumentException("eventType must not be null");
            }
            this.eventType = eventType;
            this.predicate = predicate;
        }

        public Class<?> eventType() {
            return eventType;
        }

        public boolean hasPredicate() {
            return predicate != null;
        }

        public boolean matches(Object event) {
            if (event == null || event.getClass() != eventType) {
                return false;
            }
            return predicate == null || predicate.test(event);
        }
    }

    /** Wake on a durable-friendly named signal and correlation key. */
    public static final class Signal extends AwaitCondition {
        private final String name;
        private final String key;

        Signal(String name, String key) {
            validateSignalPart("signal name", name);
            validateSignalPart("signal key", key);
            this.name = name;
            this.key = key;
        }

        public String name() {
            return name;
        }

        public String key() {
            return key;
        }

        public boolean matches(EventSignal signal) {
            return signal != null
                    && name.equals(signal.name())
                    && key.equals(signal.key());
        }
    }

    /** Wake when a deadline relative to registration time is reached. */
    public static final class Deadline extends AwaitCondition {
        private final long millisFromNow;

        Deadline(long millisFromNow) {
            if (millisFromNow < 0L) {
                throw new IllegalArgumentException("deadline must not be negative: " + millisFromNow);
            }
            this.millisFromNow = millisFromNow;
        }

        public long millisFromNow() {
            return millisFromNow;
        }
    }

    static void validateSignalPart(String label, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
    }
}
