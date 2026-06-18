package io.github.parkkevinsb.flower.eventloop.recovery;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.eventloop.EventFlow;

/**
 * Rebuilds a fresh {@link EventFlow} definition for one durable event-flow id.
 */
@FunctionalInterface
public interface EventFlowFactory {

    EventFlow create(FlowId flowId);
}
