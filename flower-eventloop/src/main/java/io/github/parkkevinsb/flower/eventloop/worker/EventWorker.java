package io.github.parkkevinsb.flower.eventloop.worker;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.Subscription;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import io.github.parkkevinsb.flower.eventloop.event.EventSignal;
import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpointStore;
import io.github.parkkevinsb.flower.eventloop.recovery.EventRecoveryContext;
import io.github.parkkevinsb.flower.eventloop.step.AwaitCondition;
import io.github.parkkevinsb.flower.eventloop.step.EventEffect;
import io.github.parkkevinsb.flower.eventloop.step.EventStepContext;
import io.github.parkkevinsb.flower.eventloop.step.EventStepDefinition;
import io.github.parkkevinsb.flower.eventloop.step.EventStepResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event-driven execution context for a set of {@link EventFlow}s.
 *
 * <p>This is the queue-reactor counterpart to the tick-driven core
 * {@code Worker}. There is no fixed interval and no active-flow scan. A flow
 * makes progress only when a command reaches the worker inbox or a declared
 * {@link AwaitCondition} deadline becomes due:
 *
 * <pre>
 * flow submitted        -> enter first step
 * awaited event arrives -> deliver to current step
 * awaited signal arrives -> deliver to current step
 * deadline reached      -> time out current step
 * </pre>
 *
 * <p>Two drive modes:
 *
 * <ul>
 *   <li><b>Manual / test</b>: call {@link #submit(EventFlow)} then
 *   {@link #drain()} to process everything currently runnable. Paired with
 *   {@code ManualClock}, this gives deterministic tests, the same way core
 *   uses {@code tickOnce()}.</li>
 *   <li><b>Background</b>: {@link #start()} runs a single daemon thread that
 *   blocks on the inbox queue and wakes for the nearest deadline. Use with a
 *   real clock such as {@code SystemClock}.</li>
 * </ul>
 *
 * <p>Do not mix the two modes on the same worker instance.
 */
public final class EventWorker {

    private static final long NO_DEADLINE = Long.MIN_VALUE;
    private static final AtomicLong THREAD_SEQ = new AtomicLong();
    private static final Command NOOP = () -> {
    };

    private final String name;
    private final Clock clock;
    private final EventBus eventBus;
    private final List<FlowerListener> listeners;
    private final ListenerDispatcher listenerDispatcher;
    private final CheckpointCoordinator checkpointCoordinator;
    private final Executor asyncExecutor;

    private final Object stateLock = new Object();
    private final Map<FlowId, FlowRuntime> active = new LinkedHashMap<>();
    private final LinkedBlockingQueue<Command> inbox = new LinkedBlockingQueue<>();
    private final PriorityQueue<DeadlineEntry> deadlines = new PriorityQueue<>();

    private volatile boolean running;
    private volatile boolean stopped;
    private Thread loopThread;
    private long deadlineSeq;

    public static EventWorkerBuilder builder(String name) {
        return new EventWorkerBuilder(name);
    }

    EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            EventFlowCheckpointStore checkpointStore,
            List<FlowerListener> listeners,
            Executor asyncExecutor) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus must not be null");
        }
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        this.name = name;
        this.clock = clock;
        this.eventBus = eventBus;
        this.asyncExecutor = asyncExecutor;
        this.listeners = listeners == null
                ? Collections.<FlowerListener>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(listeners));
        for (FlowerListener listener : this.listeners) {
            if (listener == null) {
                throw new IllegalArgumentException("listeners must not contain null");
            }
        }
        this.listenerDispatcher = new ListenerDispatcher(name, this.listeners);
        this.checkpointCoordinator = new CheckpointCoordinator(
                name,
                clock,
                checkpointStore,
                this.listenerDispatcher);
    }

    public String name() {
        return name;
    }

    public List<FlowerListener> listeners() {
        return listenerDispatcher.listeners();
    }

    // ------------------------------------------------------------------
    // Submission
    // ------------------------------------------------------------------

    /** Submit a flow. It enters its first step on the next {@link #drain()} or loop pass. */
    public void submit(EventFlow flow) {
        submit(flow, DuplicatePolicy.REJECT);
    }

    /** Submit a flow with an explicit duplicate-id policy. */
    public void submit(EventFlow flow, DuplicatePolicy policy) {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        if (flow.state() != FlowState.CREATED) {
            throw new IllegalStateException("flow must be CREATED before submit: " + flow.flowId());
        }
        if (stopped) {
            throw new IllegalStateException("EventWorker " + name + " is stopped");
        }
        final FlowRuntime rt = new FlowRuntime(flow);
        final FlowRuntime replaced;
        synchronized (stateLock) {
            if (stopped) {
                throw new IllegalStateException("EventWorker " + name + " is stopped");
            }
            replaced = active.get(flow.flowId());
            if (replaced != null) {
                switch (policy) {
                    case REJECT:
                        throw new IllegalStateException(
                                "Flow already submitted to event worker " + name + ": " + flow.flowId());
                    case IGNORE:
                        return;
                    case REPLACE:
                        replaced.cancelRequested = true;
                        break;
                    default:
                        throw new IllegalStateException("Unknown DuplicatePolicy: " + policy);
                }
            }
            active.put(flow.flowId(), rt);
        }
        listenerDispatcher.flowSubmitted(flow);
        if (replaced != null) {
            enqueue(() -> cancelReplacedRuntime(replaced));
        }
        enqueue(() -> enterCurrent(rt));
    }

    /** Cancel a flow by id. Returns true if it was active. */
    public boolean cancel(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        final FlowRuntime rt;
        synchronized (stateLock) {
            rt = active.get(flowId);
            if (rt != null) {
                rt.cancelRequested = true;
            }
        }
        if (rt == null) {
            return false;
        }
        enqueue(() -> cancelRuntime(rt));
        return true;
    }

    public FlowState stateOf(FlowId flowId) {
        synchronized (stateLock) {
            FlowRuntime rt = active.get(flowId);
            return rt == null ? null : rt.flow.state();
        }
    }

    public int activeCount() {
        synchronized (stateLock) {
            return active.size();
        }
    }

    /** Publish a named external callback signal to this worker's event bus. */
    public void signal(String signalName, String signalKey) {
        eventBus.publish(EventSignal.of(signalName, signalKey));
    }

    /** Publish a named external callback signal with a payload to this worker's event bus. */
    public void signal(String signalName, String signalKey, Object payload) {
        eventBus.publish(EventSignal.of(signalName, signalKey, payload));
    }

    // ------------------------------------------------------------------
    // Manual drive (deterministic tests)
    // ------------------------------------------------------------------

    /**
     * Process everything currently runnable: queued wake-ups and any deadlines
     * that are due at the current clock time, repeating until the worker is
     * idle. Intended for tests with {@code ManualClock}.
     */
    public void drain() {
        if (running) {
            throw new IllegalStateException(
                    "EventWorker " + name + " is running in background mode; do not call drain()");
        }
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            Command command;
            while ((command = inbox.poll()) != null) {
                executeSafely(command);
                progressed = true;
            }
            if (fireDueDeadlines()) {
                progressed = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // Background drive (production)
    // ------------------------------------------------------------------

    public synchronized void start() {
        if (stopped) {
            throw new IllegalStateException("EventWorker " + name + " is stopped");
        }
        if (running) {
            return;
        }
        running = true;
        loopThread = new Thread(this::loop, "flower-eventloop-" + name + "-" + THREAD_SEQ.incrementAndGet());
        loopThread.setDaemon(true);
        loopThread.start();
    }

    public void stop() {
        Thread thread;
        synchronized (this) {
            if (stopped) {
                return;
            }
            running = false;
            stopped = true;
            thread = loopThread;
            inbox.offer(NOOP);
        }
        if (thread == null) {
            shutdownActive();
            return;
        }
        if (thread != Thread.currentThread()) {
            try {
                thread.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
    }

    private void loop() {
        try {
            while (running) {
                try {
                    Command command = takeNextCommand();
                    if (command != null) {
                        executeSafely(command);
                    }
                    fireDueDeadlines();
                } catch (InterruptedException e) {
                    if (running) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
            }
        } finally {
            shutdownActive();
            synchronized (this) {
                if (Thread.currentThread() == loopThread) {
                    loopThread = null;
                }
            }
        }
    }

    private Command takeNextCommand() throws InterruptedException {
        DeadlineEntry next = peekLiveDeadline();
        if (next == null) {
            return inbox.take();
        }
        long now = clock.currentTimeMillis();
        long waitMillis = Math.max(0L, next.deadlineMillis - now);
        if (waitMillis == 0L) {
            return null;
        }
        return inbox.poll(waitMillis, TimeUnit.MILLISECONDS);
    }

    private void executeSafely(Command command) {
        try {
            command.execute();
        } catch (Throwable t) {
            System.err.println("[flower] eventloop " + name + " command failed: " + t);
            t.printStackTrace(System.err);
            listenerDispatcher.workerError(t);
        }
    }

    private void enqueue(Command command) {
        if (!stopped) {
            inbox.offer(command);
        }
    }

    // ------------------------------------------------------------------
    // Transition engine
    // ------------------------------------------------------------------

    private void enterCurrent(FlowRuntime rt) {
        if (!isActive(rt) || rt.flow.state().isTerminal()) {
            return;
        }
        if (rt.cancelRequested) {
            cancelRuntime(rt);
            return;
        }
        activateForEntry(rt);
        EventStepDefinition def = rt.flow.currentStep();
        if (def == null) {
            finishFlow(rt);
            return;
        }
        applyResult(rt, enterStep(rt, def));
    }

    private void activateForEntry(FlowRuntime rt) {
        if (rt.flow.state() != FlowState.CREATED) {
            return;
        }
        EventFlowCheckpoint checkpoint = rt.flow.recoveryCheckpoint();
        if (checkpoint == null) {
            rt.flow.markRunningAt(0);
            return;
        }
        enterRecovered(rt, checkpoint);
    }

    private void enterRecovered(FlowRuntime rt, EventFlowCheckpoint checkpoint) {
        rt.flow.activateRecoveryCheckpoint(checkpoint);
        rt.awaitGeneration = checkpoint.awaitGeneration();
        rt.awaitCheckpoints = checkpoint.awaits();
        rt.recoveryCheckpoint = checkpoint;
    }

    private EventStepResult enterStep(FlowRuntime rt, EventStepDefinition def) {
        EventStepResult result;
        boolean entered = false;
        try {
            result = invokeEnter(rt, def);
            rt.entered = true;
            entered = true;
            listenerDispatcher.stepEntered(rt.flow, def.stepId());
        } catch (Throwable t) {
            result = EventStepResult.fail(t);
        } finally {
            rt.flow.clearRecoveryCheckpoint();
            rt.recoveryCheckpoint = null;
        }
        if (result == null) {
            result = EventStepResult.fail(new IllegalStateException(
                    "onEnter returned null for step '" + def.stepId() + "'"));
            if (!entered) {
                rt.entered = true;
                listenerDispatcher.stepEntered(rt.flow, def.stepId());
            }
        }
        return result;
    }

    private EventStepResult invokeEnter(FlowRuntime rt, EventStepDefinition def) {
        if (rt.recoveryCheckpoint == null) {
            return def.enter(rt.ctx);
        }
        return def.recover(rt.ctx, new EventRecoveryContext(rt.recoveryCheckpoint));
    }

    private void deliverEvent(FlowRuntime rt, Object event, long generation) {
        if (!isActive(rt)
                || generation != rt.awaitGeneration
                || rt.flow.state().isTerminal()
                || !rt.entered) {
            return;
        }
        EventStepDefinition def = rt.flow.currentStep();
        if (def == null) {
            return;
        }
        EventStepResult result;
        try {
            result = def.event(rt.ctx, event);
        } catch (Throwable t) {
            result = EventStepResult.fail(t);
        }
        if (result == null) {
            return; // ignore; keep existing awaits active
        }
        applyResult(rt, result);
    }

    private void fireTimeout(FlowRuntime rt, long generation) {
        if (!isActive(rt)
                || generation != rt.awaitGeneration
                || rt.flow.state().isTerminal()
                || !rt.entered) {
            return;
        }
        rt.deadlineMillis = NO_DEADLINE;
        rt.awaitCheckpoints = checkpointCoordinator.withoutDeadlines(rt.awaitCheckpoints);
        EventStepDefinition def = rt.flow.currentStep();
        if (def == null) {
            return;
        }
        EventStepResult result;
        try {
            result = def.timeout(rt.ctx);
        } catch (Throwable t) {
            result = EventStepResult.fail(t);
        }
        if (result == null) {
            try {
                checkpointCoordinator.save(
                        rt.flow,
                        rt.entered,
                        rt.awaitGeneration,
                        rt.awaitCheckpoints);
            } catch (Throwable t) {
                failFlow(rt, t);
            }
            return; // keep waiting on remaining (event) conditions
        }
        applyResult(rt, result);
    }

    private void applyResult(FlowRuntime rt, EventStepResult result) {
        if (!isActive(rt) || rt.flow.state().isTerminal()) {
            return;
        }
        switch (result.type()) {
            case AWAIT:
                try {
                    registerAwaits(rt, result.awaits());
                } catch (Throwable t) {
                    failFlow(rt, t);
                    return;
                }
                runEffects(rt, result);
                return;

            case NEXT: {
                if (!runEffects(rt, result)) {
                    return;
                }
                int next = rt.flow.currentIndex() + 1;
                if (next >= rt.flow.steps().size()) {
                    finishFlow(rt);
                    return;
                }
                safeExit(rt);
                clearAwaits(rt);
                rt.flow.setCurrentIndex(next);
                rt.entered = false;
                enqueueEnter(rt);
                return;
            }

            case GOTO: {
                if (!runEffects(rt, result)) {
                    return;
                }
                Integer target = rt.flow.indexOf(result.targetStepId());
                if (target == null) {
                    failFlow(rt, new IllegalStateException(
                            "goTo target stepId not found: " + result.targetStepId()));
                    return;
                }
                safeExit(rt);
                clearAwaits(rt);
                rt.flow.setCurrentIndex(target);
                rt.entered = false;
                enqueueEnter(rt);
                return;
            }

            case FINISH:
                if (!runEffects(rt, result)) {
                    return;
                }
                finishFlow(rt);
                return;

            case FAIL:
                if (!runEffects(rt, result)) {
                    return;
                }
                failFlow(rt, result.cause());
                return;

            default:
                failFlow(rt, new IllegalStateException("unknown result type: " + result.type()));
        }
    }

    private void enqueueEnter(final FlowRuntime rt) {
        enqueue(() -> enterCurrent(rt));
    }

    private boolean runEffects(FlowRuntime rt, EventStepResult result) {
        for (EventEffect effect : result.effects()) {
            try {
                effect.apply(rt.ctx);
            } catch (Throwable t) {
                failFlow(rt, t);
                return false;
            }
            if (!isActive(rt) || rt.flow.state().isTerminal()) {
                return false;
            }
        }
        return true;
    }

    private void runAsync(final Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (asyncExecutor == null) {
            throw new IllegalStateException("EventWorker " + name + " has no async executor");
        }
        asyncExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    listenerDispatcher.workerError(t);
                }
            }
        });
    }

    private void finishFlow(FlowRuntime rt) {
        terminate(rt, TerminalTransition.FINISH, null, true);
    }

    private void failFlow(FlowRuntime rt, Throwable cause) {
        terminate(rt, TerminalTransition.FAIL, cause, true);
    }

    private void cancelFlow(FlowRuntime rt, boolean removeActive) {
        terminate(rt, TerminalTransition.CANCEL, null, removeActive);
    }

    private void terminate(
            FlowRuntime rt,
            TerminalTransition transition,
            Throwable cause,
            boolean removeActive) {
        safeExit(rt);
        clearAwaits(rt);
        switch (transition) {
            case FINISH:
                rt.flow.finish();
                break;
            case FAIL:
                rt.flow.fail(cause);
                break;
            case CANCEL:
                rt.flow.cancel();
                break;
            default:
                throw new IllegalStateException("Unknown terminal transition: " + transition);
        }
        checkpointCoordinator.delete(rt.flow);
        listenerDispatcher.flowTerminated(rt.flow);
        if (removeActive) {
            remove(rt);
        }
    }

    // ------------------------------------------------------------------
    // Await registration
    // ------------------------------------------------------------------

    private void registerAwaits(FlowRuntime rt, List<AwaitCondition> conditions) {
        long now = clock.currentTimeMillis();
        List<EventAwaitCheckpoint> checkpointAwaits =
                checkpointCoordinator.checkpointAwaitsFor(rt.flow, conditions, now);
        clearAwaits(rt);
        long generation = rt.awaitGeneration;
        long earliestDeadline = NO_DEADLINE;
        for (AwaitCondition cond : conditions) {
            if (cond instanceof AwaitCondition.Event) {
                rt.subscriptions.add(subscribeEvent(rt, (AwaitCondition.Event) cond, generation));
            } else if (cond instanceof AwaitCondition.Signal) {
                rt.subscriptions.add(subscribeSignal(rt, (AwaitCondition.Signal) cond, generation));
            } else if (cond instanceof AwaitCondition.Deadline) {
                long deadline = deadlineAt(now, ((AwaitCondition.Deadline) cond).millisFromNow());
                earliestDeadline = (earliestDeadline == NO_DEADLINE)
                        ? deadline
                        : Math.min(earliestDeadline, deadline);
            }
        }
        if (earliestDeadline != NO_DEADLINE) {
            rt.deadlineMillis = earliestDeadline;
            deadlines.add(new DeadlineEntry(rt, generation, earliestDeadline, ++deadlineSeq));
        }
        rt.awaitCheckpoints = checkpointAwaits;
        checkpointCoordinator.save(
                rt.flow,
                rt.entered,
                rt.awaitGeneration,
                rt.awaitCheckpoints);
    }

    private long deadlineAt(long now, long millisFromNow) {
        if (millisFromNow > Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + millisFromNow;
    }

    private <E> Subscription subscribeEvent(
            final FlowRuntime rt,
            final AwaitCondition.Event condition,
            final long generation) {
        @SuppressWarnings("unchecked")
        Class<E> eventType = (Class<E>) condition.eventType();
        return eventBus.subscribe(eventType, new io.github.parkkevinsb.flower.core.event.EventHandler<E>() {
            @Override
            public void handle(final E event) {
                final boolean matches;
                try {
                    matches = condition.matches(event);
                } catch (final Throwable t) {
                    enqueue(() -> failFlow(rt, t));
                    return;
                }
                if (matches) {
                    enqueue(() -> deliverEvent(rt, event, generation));
                }
            }
        });
    }

    private Subscription subscribeSignal(
            final FlowRuntime rt,
            final AwaitCondition.Signal condition,
            final long generation) {
        return eventBus.subscribe(EventSignal.class,
                new io.github.parkkevinsb.flower.core.event.EventHandler<EventSignal>() {
                    @Override
                    public void handle(final EventSignal signal) {
                        if (condition.matches(signal)) {
                            enqueue(() -> deliverEvent(rt, signal, generation));
                        }
                    }
                });
    }

    private void clearAwaits(FlowRuntime rt) {
        for (Subscription sub : rt.subscriptions) {
            try {
                sub.unsubscribe();
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        rt.subscriptions.clear();
        rt.deadlineMillis = NO_DEADLINE;
        rt.awaitCheckpoints = Collections.emptyList();
        rt.awaitGeneration++;
    }

    private boolean fireDueDeadlines() {
        long now = clock.currentTimeMillis();
        boolean fired = false;
        while (true) {
            DeadlineEntry entry = peekLiveDeadline();
            if (entry == null || entry.deadlineMillis > now) {
                return fired;
            }
            deadlines.poll();
            if (isLiveDeadline(entry)) {
                fireTimeout(entry.runtime, entry.generation);
                fired = true;
            }
        }
    }

    private void safeExit(FlowRuntime rt) {
        if (!rt.entered) {
            return;
        }
        EventStepDefinition def = rt.flow.currentStep();
        rt.entered = false;
        if (def == null) {
            return;
        }
        try {
            def.exit(rt.ctx);
        } catch (Throwable ignored) {
            // best-effort cleanup
        }
        listenerDispatcher.stepExited(rt.flow, def.stepId());
    }

    private void remove(FlowRuntime rt) {
        synchronized (stateLock) {
            active.remove(rt.flow.flowId());
        }
    }

    private boolean isActive(FlowRuntime rt) {
        synchronized (stateLock) {
            return active.get(rt.flow.flowId()) == rt;
        }
    }

    private void cancelRuntime(FlowRuntime rt) {
        if (!isActive(rt) || rt.flow.state().isTerminal()) {
            return;
        }
        cancelFlow(rt, true);
    }

    private void cancelReplacedRuntime(FlowRuntime rt) {
        if (rt.flow.state().isTerminal()) {
            return;
        }
        cancelFlow(rt, false);
    }

    private DeadlineEntry peekLiveDeadline() {
        while (true) {
            DeadlineEntry entry = deadlines.peek();
            if (entry == null) {
                return null;
            }
            if (isLiveDeadline(entry)) {
                return entry;
            }
            deadlines.poll();
        }
    }

    private boolean isLiveDeadline(DeadlineEntry entry) {
        FlowRuntime rt = entry.runtime;
        return isActive(rt)
                && !rt.flow.state().isTerminal()
                && rt.deadlineMillis == entry.deadlineMillis
                && rt.awaitGeneration == entry.generation;
    }

    private void shutdownActive() {
        List<FlowRuntime> runtimes;
        synchronized (stateLock) {
            if (active.isEmpty()) {
                runtimes = Collections.emptyList();
            } else {
                runtimes = new ArrayList<>(active.values());
                active.clear();
            }
        }
        for (FlowRuntime rt : runtimes) {
            try {
                checkpointCoordinator.save(
                        rt.flow,
                        rt.entered,
                        rt.awaitGeneration,
                        rt.awaitCheckpoints);
            } catch (Throwable t) {
                System.err.println("[flower] eventloop " + name + " checkpoint save failed for "
                        + rt.flow.flowId() + ": " + t);
                listenerDispatcher.workerError(t);
            }
            clearAwaits(rt);
            safeExit(rt);
            if (!rt.flow.state().isTerminal() && rt.flow.persistence() != FlowPersistence.DURABLE) {
                rt.flow.cancel();
                checkpointCoordinator.delete(rt.flow);
                listenerDispatcher.flowTerminated(rt.flow);
            }
        }
        deadlines.clear();
        inbox.clear();
    }

    // ------------------------------------------------------------------
    // Per-flow runtime state
    // ------------------------------------------------------------------

    private final class FlowRuntime {
        final EventFlow flow;
        final EventStepContext ctx;
        final List<Subscription> subscriptions = new ArrayList<>();
        List<EventAwaitCheckpoint> awaitCheckpoints = Collections.emptyList();
        EventFlowCheckpoint recoveryCheckpoint;
        long deadlineMillis = NO_DEADLINE;
        long awaitGeneration;
        boolean entered;
        volatile boolean cancelRequested;

        FlowRuntime(EventFlow flow) {
            this.flow = flow;
            this.ctx = new Ctx(flow);
        }
    }

    private final class Ctx implements EventStepContext {
        private final EventFlow flow;

        Ctx(EventFlow flow) {
            this.flow = flow;
        }

        @Override
        public FlowId flowId() {
            return flow.flowId();
        }

        @Override
        public ExecutionContext executionContext() {
            return flow.executionContext();
        }

        @Override
        public String currentStepId() {
            return flow.currentStepId();
        }

        @Override
        public EventBus eventBus() {
            return eventBus;
        }

        @Override
        public void signal(String signalName, String signalKey) {
            EventWorker.this.signal(signalName, signalKey);
        }

        @Override
        public void signal(String signalName, String signalKey, Object payload) {
            EventWorker.this.signal(signalName, signalKey, payload);
        }

        @Override
        public void runAsync(Runnable task) {
            EventWorker.this.runAsync(task);
        }

        @Override
        public Clock clock() {
            return clock;
        }

        @Override
        public long now() {
            return clock.currentTimeMillis();
        }
    }

    @FunctionalInterface
    private interface Command {
        void execute();
    }

    private enum TerminalTransition {
        FINISH,
        FAIL,
        CANCEL
    }

    private static final class DeadlineEntry implements Comparable<DeadlineEntry> {
        final FlowRuntime runtime;
        final long generation;
        final long deadlineMillis;
        final long sequence;

        DeadlineEntry(FlowRuntime runtime, long generation, long deadlineMillis, long sequence) {
            this.runtime = runtime;
            this.generation = generation;
            this.deadlineMillis = deadlineMillis;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(DeadlineEntry other) {
            int deadlineCompare = Long.compare(deadlineMillis, other.deadlineMillis);
            if (deadlineCompare != 0) {
                return deadlineCompare;
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
