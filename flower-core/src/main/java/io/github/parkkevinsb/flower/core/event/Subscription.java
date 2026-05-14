package io.github.parkkevinsb.flower.core.event;

/**
 * Handle to a single {@link EventBus#subscribe(Class, EventHandler)} registration.
 *
 * <p>Calling {@link #unsubscribe()} more than once is a no-op.
 */
public interface Subscription {

    void unsubscribe();
}
