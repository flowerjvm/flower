package io.github.parkkevinsb.flower.eventloop.worker;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.eventloop.EventWorker;
import io.github.parkkevinsb.flower.eventloop.checkpoint.EventFlowCheckpointStore;

import java.util.concurrent.Executor;

/**
 * Event-loop worker facade for MCP and tool-callback flows.
 */
public final class McpEventWorker extends SpecializedEventWorker {

    private McpEventWorker(EventWorker delegate, EventFlowCheckpointStore checkpointStore) {
        super("mcp", delegate, checkpointStore);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static McpEventWorker create(String name, Clock clock, EventBus eventBus) {
        return builder(name).clock(clock).eventBus(eventBus).build();
    }

    public static McpEventWorker create(
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
            super("mcp", name);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public McpEventWorker build() {
            return new McpEventWorker(buildDelegate(), checkpointStore());
        }
    }
}
