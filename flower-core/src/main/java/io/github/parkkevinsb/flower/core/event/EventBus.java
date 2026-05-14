package io.github.parkkevinsb.flower.core.event;

/**
 * Minimal event bus SPI.
 *
 * <p>Flower core ships {@link InMemoryEventBus} as the default. Real
 * deployments may provide an adapter that bridges to an external event
 * system (e.g. Bloom). Core never depends on those adapters.
 *
 * <p>Subscriptions are typed by exact class match: a handler registered
 * for {@code Foo.class} only receives instances whose runtime type is
 * exactly {@code Foo}, not subclasses. Subclass dispatch can be added
 * by adapters if required.
 */
public interface EventBus {

    /**
     * Register a handler for events whose runtime type equals {@code eventType}.
     *
     * @return a {@link Subscription} that the caller is responsible for
     *         disposing. Step-owned subscriptions made through
     *         {@code StepContext.subscribe(...)} are disposed by the framework.
     */
    <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler);

    /**
     * Publish an event. Implementations decide whether dispatch is sync or async.
     */
    void publish(Object event);
}
