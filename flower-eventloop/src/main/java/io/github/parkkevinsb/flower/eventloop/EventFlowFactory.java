package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.flow.FlowId;

/**
 * Rebuilds a fresh {@link EventFlow} definition for one durable event-flow id.
 */
@FunctionalInterface
public interface EventFlowFactory {

    EventFlow create(FlowId flowId);
}
