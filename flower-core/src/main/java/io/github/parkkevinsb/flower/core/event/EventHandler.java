package io.github.parkkevinsb.flower.core.event;

/**
 * Receives events of type {@code E} from an {@link EventBus}.
 *
 * <p>Handlers are expected to return quickly. They should not perform long
 * work, hold locks, or mutate Flow/Step state directly. The recommended
 * pattern is to record a signal or enqueue a payload on the owning
 * {@link io.github.parkkevinsb.flower.core.step.StepContext} and let the
 * Worker tick decide the next state transition.
 */
@FunctionalInterface
public interface EventHandler<E> {

    void handle(E event);
}
