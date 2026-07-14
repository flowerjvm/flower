package io.github.flowerjvm.flower.eventloop.recovery;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;

/**
 * Rebuilds a fresh {@link EventFlow} definition for one durable event-flow id.
 */
@FunctionalInterface
public interface EventFlowFactory {

    EventFlow create(FlowId flowId);
}
