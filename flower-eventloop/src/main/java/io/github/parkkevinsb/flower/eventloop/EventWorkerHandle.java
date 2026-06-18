package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;

import java.util.List;

/**
 * Common control surface for raw and specialized event-loop workers.
 */
public interface EventWorkerHandle {

    String name();

    List<FlowerListener> listeners();

    EventWorker delegate();

    void submit(EventFlow flow);

    void submit(EventFlow flow, DuplicatePolicy policy);

    boolean cancel(FlowId flowId);

    FlowState stateOf(FlowId flowId);

    int activeCount();

    void signal(String signalName, String signalKey);

    void signal(String signalName, String signalKey, Object payload);

    void drain();

    void start();

    void stop();
}
