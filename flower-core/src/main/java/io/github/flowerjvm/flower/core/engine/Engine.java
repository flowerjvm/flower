package io.github.flowerjvm.flower.core.engine;

import io.github.flowerjvm.flower.core.event.EventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.core.worker.DuplicatePolicy;
import io.github.flowerjvm.flower.core.worker.FlowOwnershipRegistry;
import io.github.flowerjvm.flower.core.worker.Worker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level container for Workers, Clock, EventBus, and lifecycle listeners.
 *
 * <p>Engine is the public entry point users construct via
 * {@link #builder()}. It owns the shared services and forwards lifecycle
 * commands to its Workers.
 *
 * <p>Two usage modes:
 * <ul>
 *   <li><b>Production</b>: {@link #start()} attaches Workers to their shared
 *   services and starts their tick schedulers.</li>
 *   <li><b>Manual / test</b>: {@link #attach()} only attaches; the test
 *   then drives ticks via {@link Worker#tickOnce()}. This pairs naturally
 *   with {@link io.github.flowerjvm.flower.core.time.ManualClock}.</li>
 * </ul>
 */
public final class Engine {

    private final Clock clock;
    private final EventBus eventBus;
    private final Map<String, Worker> workers;
    private final List<FlowerListener> listeners;
    private final FlowCheckpointStore checkpointStore;
    private final FlowOwnershipRegistry ownershipRegistry = new FlowOwnershipRegistry();

    private final Object lock = new Object();
    private volatile EngineState state = EngineState.CREATED;
    private boolean attached = false;

    Engine(
            Clock clock,
            EventBus eventBus,
            Map<String, Worker> workers,
            List<FlowerListener> listeners,
            FlowCheckpointStore checkpointStore) {
        this.clock = clock;
        this.eventBus = eventBus;
        this.workers = Collections.unmodifiableMap(new LinkedHashMap<>(workers));
        this.listeners = Collections.unmodifiableList(new ArrayList<>(listeners));
        this.checkpointStore = checkpointStore == null ? FlowCheckpointStore.NOOP : checkpointStore;
    }

    public static EngineBuilder builder() {
        return new EngineBuilder();
    }

    public Clock clock() {
        return clock;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public EngineState state() {
        return state;
    }

    public Worker worker(String name) {
        Worker w = workers.get(name);
        if (w == null) {
            throw new IllegalArgumentException("no worker named: " + name);
        }
        return w;
    }

    public Collection<Worker> workers() {
        return workers.values();
    }

    public List<FlowerListener> listeners() {
        return listeners;
    }

    public FlowCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    public void submit(String workerName, Flow flow) {
        submit(workerName, flow, DuplicatePolicy.REJECT);
    }

    public void submit(String workerName, Flow flow, DuplicatePolicy policy) {
        worker(workerName).submit(flow, policy);
    }

    public boolean cancel(FlowId flowId) {
        String owner = ownershipRegistry.ownerOf(flowId);
        if (owner == null) {
            return false;
        }
        Worker worker = workers.get(owner);
        return worker != null && worker.cancel(flowId);
    }

    /**
     * Attach all Workers to the shared {@link Clock}, {@link EventBus}, and
     * listeners. Idempotent. Used directly by tests that want to drive
     * ticks manually; {@link #start()} also calls this.
     */
    public void attach() {
        synchronized (lock) {
            if (attached) return;
            for (Worker w : workers.values()) {
                w.attach(clock, eventBus, listeners, checkpointStore, ownershipRegistry);
            }
            attached = true;
        }
    }

    /**
     * Attach Workers and start their tick schedulers. After {@code start()}
     * the engine is in {@link EngineState#RUNNING}.
     */
    public void start() {
        synchronized (lock) {
            if (state == EngineState.STOPPED) {
                throw new IllegalStateException("Engine is already stopped");
            }
            if (state == EngineState.RUNNING) return;
            attach();
            for (Worker w : workers.values()) {
                w.start();
            }
            state = EngineState.RUNNING;
        }
    }

    /**
     * Stop all Workers and transition to {@link EngineState#STOPPED}.
     * Safe to call multiple times.
     */
    public void stop() {
        synchronized (lock) {
            if (state == EngineState.STOPPED) return;
            state = EngineState.STOPPING;
            for (Worker w : workers.values()) {
                try {
                    w.stop();
                } catch (Throwable t) {
                    // best-effort; keep stopping the rest
                    System.err.println("[flower] failed to stop worker " + w.name() + ": " + t);
                }
            }
            state = EngineState.STOPPED;
        }
    }

    public EngineDump dump() {
        List<EngineDump.WorkerDump> wd = new ArrayList<>(workers.size());
        for (Worker w : workers.values()) {
            List<FlowSnapshot> flows = w.snapshotWithStepDefinitions();
            wd.add(new EngineDump.WorkerDump(
                    w.name(),
                    w.state(),
                    w.driveMode(),
                    w.intervalMillis(),
                    flows));
        }
        return new EngineDump(state, wd);
    }
}
