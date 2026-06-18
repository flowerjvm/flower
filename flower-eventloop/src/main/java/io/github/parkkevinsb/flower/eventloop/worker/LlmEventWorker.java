package io.github.parkkevinsb.flower.eventloop.worker;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.eventloop.EventWorker;
import io.github.parkkevinsb.flower.eventloop.checkpoint.EventFlowCheckpointStore;

import java.util.concurrent.Executor;

/**
 * Event-loop worker facade for LLM request/response flows.
 */
public final class LlmEventWorker extends SpecializedEventWorker {

    private LlmEventWorker(EventWorker delegate, EventFlowCheckpointStore checkpointStore) {
        super("llm", delegate, checkpointStore);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static LlmEventWorker create(String name, Clock clock, EventBus eventBus) {
        return builder(name).clock(clock).eventBus(eventBus).build();
    }

    public static LlmEventWorker create(
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
            super("llm", name);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public LlmEventWorker build() {
            return new LlmEventWorker(buildDelegate(), checkpointStore());
        }
    }
}
