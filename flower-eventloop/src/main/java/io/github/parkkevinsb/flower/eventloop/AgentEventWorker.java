package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.time.Clock;

import java.util.concurrent.Executor;

/**
 * Event-loop worker facade for agent runtime flows.
 */
public final class AgentEventWorker extends SpecializedEventWorker {

    private AgentEventWorker(EventWorker delegate, EventFlowCheckpointStore checkpointStore) {
        super("agent", delegate, checkpointStore);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static AgentEventWorker create(String name, Clock clock, EventBus eventBus) {
        return builder(name).clock(clock).eventBus(eventBus).build();
    }

    public static AgentEventWorker create(
            String name,
            Clock clock,
            EventBus eventBus,
            Executor offloadExecutor) {
        return builder(name)
                .clock(clock)
                .eventBus(eventBus)
                .offloadExecutor(offloadExecutor)
                .build();
    }

    public static final class Builder extends SpecializedEventWorkerBuilder<Builder> {
        private Builder(String name) {
            super("agent", name);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public AgentEventWorker build() {
            return new AgentEventWorker(buildDelegate(), checkpointStore());
        }
    }
}
