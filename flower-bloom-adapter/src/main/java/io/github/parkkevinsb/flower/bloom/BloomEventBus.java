package io.github.parkkevinsb.flower.bloom;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.EventHandler;
import io.github.parkkevinsb.flower.core.event.Subscription;

/**
 * Adapter that exposes a Bloom {@link io.github.parkkevinsb.bloom.EventBus} as a
 * Flower {@link EventBus}.
 *
 * <p>The two SPIs share the same shape ({@code subscribe(Class, handler) ->
 * Subscription}, {@code publish(Object)}) so the adapter is a thin pass-through:
 * subscriptions register a Bloom handler that simply forwards to the Flower
 * handler, and the returned {@link Subscription} delegates {@code unsubscribe()}
 * to {@link io.github.parkkevinsb.bloom.Subscription#close()}.
 *
 * <p>flower-core never references this class. Users opt in by adding the
 * {@code flower-bloom-adapter} module on their classpath and wiring an instance
 * of {@code BloomEventBus} into the {@code Engine} builder:
 *
 * <pre>{@code
 * io.github.parkkevinsb.bloom.EventBus bloom =
 *         io.github.parkkevinsb.bloom.LocalEventBus.create();
 *
 * Engine engine = Engine.builder()
 *         .eventBus(BloomEventBus.wrap(bloom))
 *         .worker(Worker.builder("main").intervalMillis(100).build())
 *         .build();
 * }</pre>
 *
 * <p>Threading and dispatch semantics (sync vs. async) come entirely from the
 * wrapped Bloom bus. Wrapping {@link io.github.parkkevinsb.bloom.AsyncEventBus}
 * gives Flower an async publish path without changing core.
 */
public final class BloomEventBus implements EventBus {

    private final io.github.parkkevinsb.bloom.EventBus delegate;

    private BloomEventBus(io.github.parkkevinsb.bloom.EventBus delegate) {
        this.delegate = delegate;
    }

    /**
     * Wrap a Bloom event bus so it can be plugged into Flower core.
     *
     * @param delegate the underlying Bloom bus; must not be null
     * @throws IllegalArgumentException if {@code delegate} is null
     */
    public static BloomEventBus wrap(io.github.parkkevinsb.bloom.EventBus delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        return new BloomEventBus(delegate);
    }

    /** @return the underlying Bloom bus. Useful for tests and Spring wiring. */
    public io.github.parkkevinsb.bloom.EventBus delegate() {
        return delegate;
    }

    @Override
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        io.github.parkkevinsb.bloom.Subscription bloomSub =
                delegate.subscribe(eventType, handler::handle);
        return bloomSub::close;
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        delegate.publish(event);
    }
}
