package io.github.parkkevinsb.flower.eventloop;

/**
 * Side effect the {@link EventWorker} runs after it has accepted an
 * {@link EventStepResult}.
 *
 * <p>The most common use is publishing an outbound request only after the
 * worker has registered the await conditions that should receive the response:
 *
 * <pre>{@code
 * return EventStepResult.await(AwaitCondition.event(Response.class))
 *         .thenPublish(new Request(...));
 * }</pre>
 *
 * <p>Effects run on the event-loop thread. They must be quick and
 * non-blocking: publish a request, signal another component, or enqueue work
 * elsewhere. Do not perform network I/O, sleeps, or long CPU work here. Use
 * {@link EventStepResult#thenRunOffloaded(EventEffect)} for work that may
 * block.
 */
@FunctionalInterface
public interface EventEffect {

    void apply(EventStepContext ctx);
}
