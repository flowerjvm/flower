package io.github.flowerjvm.flower.core.engine;

import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.worker.DriveMode;
import io.github.flowerjvm.flower.core.worker.WorkerState;

import java.util.Collections;
import java.util.List;

/**
 * Read-only snapshot of an Engine's runtime state. Useful for ops endpoints,
 * test assertions, and incident triage.
 */
public final class EngineDump {

    private final EngineState engineState;
    private final List<WorkerDump> workers;

    public EngineDump(EngineState engineState, List<WorkerDump> workers) {
        this.engineState = engineState;
        this.workers = Collections.unmodifiableList(workers);
    }

    public EngineState engineState() {
        return engineState;
    }

    public List<WorkerDump> workers() {
        return workers;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Engine: ").append(engineState).append('\n');
        for (WorkerDump w : workers) {
            sb.append("  Worker '").append(w.name()).append("' [")
                    .append(w.state()).append(", drive=")
                    .append(w.driveMode()).append(", interval=")
                    .append(w.intervalMillis()).append("ms]\n");
            for (FlowSnapshot f : w.flows()) {
                sb.append("    ").append(f).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toText();
    }

    public static final class WorkerDump {
        private final String name;
        private final WorkerState state;
        private final DriveMode driveMode;
        private final long intervalMillis;
        private final List<FlowSnapshot> flows;

        public WorkerDump(String name, WorkerState state, long intervalMillis, List<FlowSnapshot> flows) {
            this(name, state, DriveMode.NONE, intervalMillis, flows);
        }

        public WorkerDump(
                String name,
                WorkerState state,
                DriveMode driveMode,
                long intervalMillis,
                List<FlowSnapshot> flows) {
            this.name = name;
            this.state = state;
            this.driveMode = driveMode == null ? DriveMode.NONE : driveMode;
            this.intervalMillis = intervalMillis;
            this.flows = Collections.unmodifiableList(flows);
        }

        public String name() {
            return name;
        }

        public WorkerState state() {
            return state;
        }

        public DriveMode driveMode() {
            return driveMode;
        }

        public long intervalMillis() {
            return intervalMillis;
        }

        public List<FlowSnapshot> flows() {
            return flows;
        }
    }
}
