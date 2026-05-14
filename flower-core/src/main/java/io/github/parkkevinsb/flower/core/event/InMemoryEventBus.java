package io.github.parkkevinsb.flower.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous in-memory {@link EventBus}.
 *
 * <p>Used as the default in unit tests and for standalone Flower setups
 * that do not bind to an external event system. Dispatch is synchronous
 * on the publisher's thread, which is what gives Flower its deterministic
 * behavior in tests when paired with {@link io.github.parkkevinsb.flower.core.time.ManualClock}.
 *
 * <p>Listener exceptions are caught per-handler so that one bad subscriber
 * cannot stop the rest from receiving the event. Exceptions are reported
 * through an optional handler installed via {@link #onListenerError}.
 */
public final class InMemoryEventBus implements EventBus {

    /**
     * Receives exceptions thrown by individual listener invocations so the bus
     * can keep dispatching to the remaining listeners.
     */
    public interface ListenerErrorHandler {
        void onError(Object event, EventHandler<?> handler, Throwable cause);
    }

    private final Map<Class<?>, CopyOnWriteArrayList<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();
    private volatile ListenerErrorHandler errorHandler;

    private InMemoryEventBus() {
    }

    public static InMemoryEventBus create() {
        return new InMemoryEventBus();
    }

    public void onListenerError(ListenerErrorHandler handler) {
        this.errorHandler = handler;
    }

    @Override
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        CopyOnWriteArrayList<HandlerEntry<?>> list = handlers.computeIfAbsent(
                eventType, k -> new CopyOnWriteArrayList<>());
        HandlerEntry<E> entry = new HandlerEntry<>(eventType, handler);
        list.add(entry);
        return () -> {
            CopyOnWriteArrayList<HandlerEntry<?>> existing = handlers.get(eventType);
            if (existing != null) {
                existing.remove(entry);
            }
        };
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        CopyOnWriteArrayList<HandlerEntry<?>> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        // Snapshot to a local list so handlers added during dispatch are not invoked
        // for the in-flight event - this preserves deterministic ordering.
        List<HandlerEntry<?>> snapshot = new ArrayList<>(list);
        for (HandlerEntry<?> entry : snapshot) {
            dispatch(entry, event);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(HandlerEntry<?> entry, Object event) {
        try {
            ((EventHandler) entry.handler).handle(event);
        } catch (Throwable t) {
            ListenerErrorHandler eh = errorHandler;
            if (eh != null) {
                try {
                    eh.onError(event, entry.handler, t);
                } catch (Throwable ignored) {
                    // ignore secondary failures
                }
            }
        }
    }

    private static final class HandlerEntry<E> {
        final Class<E> eventType;
        final EventHandler<E> handler;

        HandlerEntry(Class<E> eventType, EventHandler<E> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }
    }
}
