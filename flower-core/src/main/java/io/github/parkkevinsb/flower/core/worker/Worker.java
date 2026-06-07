package io.github.parkkevinsb.flower.core.worker;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.flow.LifecycleObserver;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;
import io.github.parkkevinsb.flower.core.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-threaded execution context for a set of Flows.
 *
 * <p>A Worker owns one tick loop. On each tick it:
 *
 * <ol>
 *   <li>applies pending submits and cancels</li>
 *   <li>ticks every active non-terminal Flow once</li>
 *   <li>removes terminal Flows and fires listener events</li>
 * </ol>
 *
 * <p>Flows are processed in submission order so the result of a tick is
 * deterministic given the same inputs. {@link #tickOnce()} is public so
 * tests can drive the Worker manually with {@link io.github.parkkevinsb.flower.core.time.ManualClock}
 * instead of the real scheduler.
 */
public final class Worker {

    private final String name;
    private final long intervalMillis;

    // Mutated under stateLock. activeFlows uses LinkedHashMap for insertion-order iteration.
    private final Map<FlowId, Flow> activeFlows = new LinkedHashMap<>();
    private final Queue<Flow> pendingSubmits = new ConcurrentLinkedQueue<>();
    private final Queue<FlowId> pendingCancels = new ConcurrentLinkedQueue<>();
    private final Object stateLock = new Object();

    private volatile WorkerState state = WorkerState.CREATED;

    private Clock clock;
    private EventBus eventBus;
    private List<FlowerListener> listeners = Collections.emptyList();
    private FlowCheckpointStore checkpointStore = FlowCheckpointStore.NOOP;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tickFuture;

    Worker(String name, long intervalMillis) {
        this.name = name;
        this.intervalMillis = intervalMillis;
    }

    public static WorkerBuilder builder(String name) {
        return new WorkerBuilder(name);
    }

    public String name() {
        return name;
    }

    public long intervalMillis() {
        return intervalMillis;
    }

    public WorkerState state() {
        return state;
    }

    /**
     * Snapshot of currently-active flows. The returned list does not reflect
     * later mutations.
     */
    public List<FlowSnapshot> snapshot() {
        synchronized (stateLock) {
            List<FlowSnapshot> out = new ArrayList<>(activeFlows.size());
            for (Flow f : activeFlows.values()) {
                out.add(f.snapshot());
            }
            return out;
        }
    }

    /**
     * Snapshot of currently-active flows including static Step definition
     * metadata for admin/dump views. Listener callbacks use the lightweight
     * variant so Worker tick events do not repeatedly materialize Step lists.
     */
    public List<FlowSnapshot> snapshotWithStepDefinitions() {
        synchronized (stateLock) {
            List<FlowSnapshot> out = new ArrayList<>(activeFlows.size());
            for (Flow f : activeFlows.values()) {
                out.add(f.snapshotWithStepDefinitions());
            }
            return out;
        }
    }

    // ------------------------------------------------------------------
    // Engine-facing setup
    // ------------------------------------------------------------------

    /**
     * Bind shared runtime services. Called by {@code Engine} before {@link #start()}.
     */
    public void attach(Clock clock, EventBus eventBus, List<FlowerListener> listeners) {
        attach(clock, eventBus, listeners, FlowCheckpointStore.NOOP);
    }

    /**
     * Bind shared runtime services and checkpoint storage. Called by
     * {@code Engine} before {@link #start()}.
     */
    public void attach(
            Clock clock,
            EventBus eventBus,
            List<FlowerListener> listeners,
            FlowCheckpointStore checkpointStore) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        synchronized (stateLock) {
            if (state != WorkerState.CREATED) {
                throw new IllegalStateException(
                        "Worker " + name + " already attached, state=" + state);
            }
            this.clock = clock;
            this.eventBus = eventBus;
            this.listeners = listeners == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(listeners));
            this.checkpointStore = checkpointStore == null ? FlowCheckpointStore.NOOP : checkpointStore;
        }
    }

    /**
     * Start the internal scheduler. Engine calls this; user code does not
     * need to call it directly when using Engine.
     */
    public void start() {
        synchronized (stateLock) {
            ensureAttached();
            if (state == WorkerState.RUNNING) return;
            if (state == WorkerState.STOPPED) {
                throw new IllegalStateException("Worker " + name + " already stopped");
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory());
            tickFuture = scheduler.scheduleWithFixedDelay(
                    this::tickSafely, 0L, intervalMillis, TimeUnit.MILLISECONDS);
            state = WorkerState.RUNNING;
        }
    }

    /**
     * Stop the internal scheduler. Transient flows are cancelled. Durable
     * flows are checkpointed and suspended so they can be rebuilt and resumed
     * by the host application.
     */
    public void stop() {
        ScheduledExecutorService toShutdown;
        ScheduledFuture<?> toCancel;
        List<Flow> flowsToCancel;
        synchronized (stateLock) {
            if (state == WorkerState.STOPPED) return;
            toShutdown = scheduler;
            toCancel = tickFuture;
            scheduler = null;
            tickFuture = null;
            state = WorkerState.STOPPED;
            flowsToCancel = drainAllFlows();
            pendingCancels.clear();
        }
        if (toCancel != null) toCancel.cancel(false);
        if (toShutdown != null) toShutdown.shutdownNow();
        stopFlows(flowsToCancel);
    }

    public void pause() {
        synchronized (stateLock) {
            if (state == WorkerState.RUNNING) {
                state = WorkerState.PAUSED;
            }
        }
    }

    public void resume() {
        synchronized (stateLock) {
            if (state == WorkerState.PAUSED) {
                state = WorkerState.RUNNING;
            }
        }
    }

    // ------------------------------------------------------------------
    // Submission API
    // ------------------------------------------------------------------

    public void submit(Flow flow) {
        submit(flow, DuplicatePolicy.REJECT);
    }

    public void submit(Flow flow, DuplicatePolicy policy) {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        synchronized (stateLock) {
            ensureAttached();
            if (state == WorkerState.STOPPED) {
                throw new IllegalStateException("Worker " + name + " is stopped");
            }
            FlowId id = flow.flowId();
            boolean duplicateActive = activeFlows.containsKey(id);
            boolean duplicatePending = containsPending(id);
            boolean duplicate = duplicateActive || duplicatePending;
            if (duplicate) {
                switch (policy) {
                    case REJECT:
                        throw new IllegalStateException(
                                "Flow already submitted to worker " + name + ": " + id);
                    case IGNORE:
                        return;
                    case REPLACE:
                        if (duplicatePending) {
                            cancelPendingSubmits(id);
                        }
                        if (duplicateActive) {
                            pendingCancels.add(id);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown DuplicatePolicy: " + policy);
                }
            }
            // Attach now under lock so the Flow has clock+bus before reaching tick.
            // Worker is responsible for a Flow's lifecycle from this point on.
            flow.attach(clock, eventBus, observerFor(flow));
            pendingSubmits.add(flow);
        }
    }

    public boolean cancel(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        synchronized (stateLock) {
            boolean active = activeFlows.containsKey(flowId);
            boolean pending = cancelPendingSubmits(flowId);
            if (!active && !pending) {
                return false;
            }
            if (active) {
                pendingCancels.add(flowId);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Tick - public so tests can drive without start()
    // ------------------------------------------------------------------

    /**
     * Run one Worker tick. Safe to call from tests in lieu of {@link #start()}.
     * Does nothing if the Worker is paused or stopped.
     */
    public void tickOnce() {
        if (state == WorkerState.PAUSED || state == WorkerState.STOPPED) {
            return;
        }
        ensureAttached();
        applyPendingChanges();
        runFlows();
        removeTerminated();
    }

    private void tickSafely() {
        try {
            tickOnce();
        } catch (Throwable t) {
            // Don't let exceptions kill the scheduled task. The Flow runtime
            // already converts step exceptions to FAIL results, so reaching
            // this branch means a framework bug.
            // (No SLF4J - core is dependency free. Fall back to System.err.)
            notifyWorkerError(t);
            System.err.println("[flower] worker " + name + " tick failed: " + t);
            t.printStackTrace(System.err);
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private boolean containsPending(FlowId id) {
        for (Flow f : pendingSubmits) {
            if (id.equals(f.flowId())) return true;
        }
        return false;
    }

    private void ensureAttached() {
        if (clock == null) {
            throw new IllegalStateException(
                    "Worker " + name + " is not attached. Add it to an Engine before use.");
        }
    }

    private void applyPendingChanges() {
        synchronized (stateLock) {
            // Cancels first, so REPLACE semantics work: cancel old, then add new.
            FlowId cid;
            while ((cid = pendingCancels.poll()) != null) {
                Flow active = activeFlows.remove(cid);
                if (active != null) {
                    active.cancel();
                    fireFlowTerminated(active);
                    deleteCheckpoint(active);
                }
            }
            Flow ps;
            while ((ps = pendingSubmits.poll()) != null) {
                FlowId id = ps.flowId();
                if (activeFlows.containsKey(id)) {
                    // Either REJECT slipped past (shouldn't happen) or duplicate-in-pending.
                    // Skip silently; tests assert via state.
                    continue;
                }
                activeFlows.put(id, ps);
                fireFlowSubmitted(ps);
                saveCheckpoint(ps);
            }
        }
    }

    private void runFlows() {
        // Snapshot the active flows so concurrent submit/cancel during tick does
        // not perturb the iteration. New submissions queued during this tick are
        // applied at the start of the next tick.
        List<Flow> snapshot;
        synchronized (stateLock) {
            snapshot = new ArrayList<>(activeFlows.values());
        }
        for (Flow flow : snapshot) {
            if (flow.state().isTerminal()) continue;
            FlowState before = flow.state();
            try {
                flow.tick();
            } catch (Throwable t) {
                // Flow.tick already wraps step exceptions; reaching here is unexpected.
                notifyWorkerError(t);
                System.err.println("[flower] worker " + name + " flow " + flow.flowId() + " tick blew up: " + t);
            }
            FlowState after = flow.state();
            if (!before.isTerminal() && after.isTerminal()) {
                fireFlowTerminated(flow);
            }
            saveOrDeleteCheckpoint(flow);
        }
    }

    private void removeTerminated() {
        synchronized (stateLock) {
            Iterator<Map.Entry<FlowId, Flow>> it = activeFlows.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().state().isTerminal()) {
                    it.remove();
                }
            }
        }
    }

    private boolean cancelPendingSubmits(FlowId id) {
        List<Flow> removed = removePendingSubmits(id);
        cancelAndNotify(removed);
        return !removed.isEmpty();
    }

    private List<Flow> removePendingSubmits(FlowId id) {
        List<Flow> removed = new ArrayList<>();
        List<Flow> survivors = new ArrayList<>();
        Flow flow;
        while ((flow = pendingSubmits.poll()) != null) {
            if (id.equals(flow.flowId())) {
                removed.add(flow);
            } else {
                survivors.add(flow);
            }
        }
        for (Flow survivor : survivors) {
            pendingSubmits.add(survivor);
        }
        return removed;
    }

    private List<Flow> drainAllFlows() {
        List<Flow> drained = new ArrayList<>(activeFlows.values());
        activeFlows.clear();
        Flow pending;
        while ((pending = pendingSubmits.poll()) != null) {
            drained.add(pending);
        }
        return drained;
    }

    private void cancelAndNotify(List<Flow> flows) {
        for (Flow flow : flows) {
            if (flow.state().isTerminal()) {
                continue;
            }
            flow.cancel();
            fireFlowTerminated(flow);
            deleteCheckpoint(flow);
        }
    }

    private void stopFlows(List<Flow> flows) {
        for (Flow flow : flows) {
            if (flow.state().isTerminal()) {
                continue;
            }
            if (flow.persistence() == FlowPersistence.DURABLE) {
                saveCheckpoint(flow);
                flow.suspend();
            } else {
                flow.cancel();
                fireFlowTerminated(flow);
                deleteCheckpoint(flow);
            }
        }
    }

    private void saveOrDeleteCheckpoint(Flow flow) {
        if (flow.state().isTerminal()) {
            deleteCheckpoint(flow);
        } else {
            saveCheckpoint(flow);
        }
    }

    private void saveCheckpoint(Flow flow) {
        if (flow.persistence() != FlowPersistence.DURABLE) {
            return;
        }
        try {
            checkpointStore.save(flow.checkpoint(name, clock.currentTimeMillis()));
        } catch (Throwable t) {
            notifyWorkerError(t);
            System.err.println("[flower] worker " + name + " checkpoint save failed for "
                    + flow.flowId() + ": " + t);
        }
    }

    private void deleteCheckpoint(Flow flow) {
        if (flow.persistence() != FlowPersistence.DURABLE) {
            return;
        }
        try {
            checkpointStore.delete(flow.flowId());
        } catch (Throwable t) {
            notifyWorkerError(t);
            System.err.println("[flower] worker " + name + " checkpoint delete failed for "
                    + flow.flowId() + ": " + t);
        }
    }

    // ------------------------------------------------------------------
    // listener fanout
    // ------------------------------------------------------------------

    private void fireFlowSubmitted(Flow flow) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener l : listeners) {
            try {
                l.onFlowSubmitted(snap);
            } catch (Throwable t) {
                notifyListenerError(snap, "onFlowSubmitted", t);
            }
        }
    }

    private LifecycleObserver observerFor(Flow flow) {
        return new LifecycleObserver() {
            @Override
            public void onStepEntered(String stepId) {
                FlowSnapshot snap = flow.snapshot();
                for (FlowerListener l : listeners) {
                    try {
                        l.onStepEntered(snap, stepId);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onStepEntered", t);
                    }
                }
            }

            @Override
            public void onStepExited(String stepId) {
                FlowSnapshot snap = flow.snapshot();
                for (FlowerListener l : listeners) {
                    try {
                        l.onStepExited(snap, stepId);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onStepExited", t);
                    }
                }
            }
        };
    }

    private void fireFlowTerminated(Flow flow) {
        FlowSnapshot snap = flow.snapshot();
        switch (flow.state()) {
            case FINISHED:
                for (FlowerListener l : listeners) {
                    try {
                        l.onFlowFinished(snap);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onFlowFinished", t);
                    }
                }
                break;
            case FAILED:
                Throwable cause = flow.failureCause();
                for (FlowerListener l : listeners) {
                    try {
                        l.onFlowFailed(snap, cause);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onFlowFailed", t);
                    }
                }
                break;
            case CANCELLED:
                for (FlowerListener l : listeners) {
                    try {
                        l.onFlowCancelled(snap);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onFlowCancelled", t);
                    }
                }
                break;
            default:
                // not terminal - shouldn't happen
        }
    }

    private void notifyListenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
        for (FlowerListener l : listeners) {
            try {
                l.onListenerError(flow, callbackName, cause);
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyWorkerError(Throwable cause) {
        for (FlowerListener l : listeners) {
            try {
                l.onWorkerError(name, cause);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // ThreadFactory
    // ------------------------------------------------------------------

    private static final AtomicLong WORKER_THREAD_SEQ = new AtomicLong();

    private ThreadFactory threadFactory() {
        return r -> {
            Thread t = new Thread(r, "flower-worker-" + name + "-" + WORKER_THREAD_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
