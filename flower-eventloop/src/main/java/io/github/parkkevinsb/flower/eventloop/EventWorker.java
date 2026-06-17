package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.Subscription;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.Clock;

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
    private static final Command NOOP = new Command() {
        @Override
        public void execute() {
        }
    };

    private final String name;
    private final Clock clock;
    private final EventBus eventBus;
    private final EventFlowCheckpointStore checkpointStore;
    private final List<FlowerListener> listeners;
    private final Executor offloadExecutor;

    private final Object stateLock = new Object();
    private final Map<FlowId, FlowRuntime> active = new LinkedHashMap<>();
    private final LinkedBlockingQueue<Command> inbox = new LinkedBlockingQueue<>();
    private final PriorityQueue<DeadlineEntry> deadlines = new PriorityQueue<>();

    private volatile boolean running;
    private volatile boolean stopped;
    private Thread loopThread;
    private long deadlineSeq;

    public EventWorker(String name, Clock clock, EventBus eventBus) {
        this(name, clock, eventBus, EventFlowCheckpointStore.NOOP);
    }

    public EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            Executor offloadExecutor) {
        this(name, clock, eventBus, EventFlowCheckpointStore.NOOP,
                Collections.<FlowerListener>emptyList(), offloadExecutor);
    }

    public EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            List<FlowerListener> listeners) {
        this(name, clock, eventBus, EventFlowCheckpointStore.NOOP, listeners);
    }

    public EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            EventFlowCheckpointStore checkpointStore) {
        this(name, clock, eventBus, checkpointStore, Collections.<FlowerListener>emptyList());
    }

    public EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            EventFlowCheckpointStore checkpointStore,
            Executor offloadExecutor) {
        this(name, clock, eventBus, checkpointStore,
                Collections.<FlowerListener>emptyList(), offloadExecutor);
    }

    public EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            EventFlowCheckpointStore checkpointStore,
            List<FlowerListener> listeners) {
        this(name, clock, eventBus, checkpointStore, listeners, null);
    }

    public EventWorker(
            String name,
            Clock clock,
            EventBus eventBus,
            EventFlowCheckpointStore checkpointStore,
            List<FlowerListener> listeners,
            Executor offloadExecutor) {
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
        this.checkpointStore = checkpointStore;
        this.offloadExecutor = offloadExecutor;
        this.listeners = listeners == null
                ? Collections.<FlowerListener>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(listeners));
        for (FlowerListener listener : this.listeners) {
            if (listener == null) {
                throw new IllegalArgumentException("listeners must not contain null");
            }
        }
    }

    public String name() {
        return name;
    }

    public List<FlowerListener> listeners() {
        return listeners;
    }

    // ------------------------------------------------------------------
    // Submission
    // ------------------------------------------------------------------

    /** Submit a flow. It enters its first step on the next {@link #drain()} or loop pass. */
    public void submit(EventFlow flow) {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        if (flow.state() != FlowState.CREATED) {
            throw new IllegalStateException("flow must be CREATED before submit: " + flow.flowId());
        }
        if (stopped) {
            throw new IllegalStateException("EventWorker " + name + " is stopped");
        }
        final FlowRuntime rt = new FlowRuntime(flow);
        synchronized (stateLock) {
            if (stopped) {
                throw new IllegalStateException("EventWorker " + name + " is stopped");
            }
            if (active.containsKey(flow.flowId())) {
                throw new IllegalStateException("flow already submitted: " + flow.flowId());
            }
            active.put(flow.flowId(), rt);
        }
        fireFlowSubmitted(flow);
        enqueue(new Command() {
            @Override
            public void execute() {
                enterCurrent(rt);
            }
        });
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
        enqueue(new Command() {
            @Override
            public void execute() {
                cancelRuntime(rt);
            }
        });
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
            notifyWorkerError(t);
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
        if (rt.flow.state() == FlowState.CREATED) {
            EventFlowCheckpoint checkpoint = rt.flow.recoveryCheckpoint();
            if (checkpoint == null) {
                rt.flow.markRunningAt(0);
            } else {
                rt.flow.activateRecoveryCheckpoint(checkpoint);
                rt.awaitGeneration = checkpoint.awaitGeneration();
                rt.awaitCheckpoints = checkpoint.awaits();
                rt.recoveryCheckpoint = checkpoint;
            }
        }
        EventStepDefinition def = rt.flow.currentStep();
        if (def == null) {
            rt.flow.finish();
            deleteCheckpoint(rt);
            fireFlowTerminated(rt.flow);
            remove(rt);
            return;
        }
        EventStepResult result;
        boolean entered = false;
        try {
            if (rt.recoveryCheckpoint == null) {
                result = def.step().onEnter(rt.ctx);
            } else {
                result = def.step().onRecover(rt.ctx, new EventRecoveryContext(rt.recoveryCheckpoint));
            }
            rt.entered = true;
            entered = true;
            fireStepEntered(rt.flow, def.stepId());
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
                fireStepEntered(rt.flow, def.stepId());
            }
        }
        applyResult(rt, result);
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
            result = def.step().onEvent(rt.ctx, event);
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
        rt.awaitCheckpoints = withoutDeadlines(rt.awaitCheckpoints);
        EventStepDefinition def = rt.flow.currentStep();
        if (def == null) {
            return;
        }
        EventStepResult result;
        try {
            result = def.step().onTimeout(rt.ctx);
        } catch (Throwable t) {
            result = EventStepResult.fail(t);
        }
        if (result == null) {
            try {
                saveCheckpoint(rt);
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
                safeExit(rt);
                clearAwaits(rt);
                int next = rt.flow.currentIndex() + 1;
                if (next >= rt.flow.steps().size()) {
                    rt.flow.finish();
                    deleteCheckpoint(rt);
                    fireFlowTerminated(rt.flow);
                    remove(rt);
                    return;
                }
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
                safeExit(rt);
                clearAwaits(rt);
                rt.flow.finish();
                deleteCheckpoint(rt);
                fireFlowTerminated(rt.flow);
                remove(rt);
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
        enqueue(new Command() {
            @Override
            public void execute() {
                enterCurrent(rt);
            }
        });
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

    private void offload(final Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (offloadExecutor == null) {
            throw new IllegalStateException("EventWorker " + name + " has no offload executor");
        }
        offloadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Throwable t) {
                    notifyWorkerError(t);
                }
            }
        });
    }

    private void failFlow(FlowRuntime rt, Throwable cause) {
        safeExit(rt);
        clearAwaits(rt);
        rt.flow.fail(cause);
        deleteCheckpoint(rt);
        fireFlowTerminated(rt.flow);
        remove(rt);
    }

    // ------------------------------------------------------------------
    // Await registration
    // ------------------------------------------------------------------

    private void registerAwaits(FlowRuntime rt, List<AwaitCondition> conditions) {
        long now = clock.currentTimeMillis();
        List<EventAwaitCheckpoint> checkpointAwaits = checkpointAwaitsFor(rt, conditions, now);
        clearAwaits(rt);
        long generation = rt.awaitGeneration;
        long earliestDeadline = NO_DEADLINE;
        for (AwaitCondition cond : conditions) {
            if (cond instanceof AwaitCondition.Event) {
                rt.subscriptions.add(subscribeEvent(rt, (AwaitCondition.Event) cond, generation));
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
        saveCheckpoint(rt);
    }

    private long deadlineAt(long now, long millisFromNow) {
        if (millisFromNow > Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + millisFromNow;
    }

    private List<EventAwaitCheckpoint> checkpointAwaitsFor(
            FlowRuntime rt,
            List<AwaitCondition> conditions,
            long now) {
        if (rt.flow.persistence() != FlowPersistence.DURABLE) {
            return Collections.emptyList();
        }
        List<EventAwaitCheckpoint> out = new ArrayList<>();
        long earliestDeadline = NO_DEADLINE;
        for (AwaitCondition cond : conditions) {
            if (cond instanceof AwaitCondition.Event) {
                AwaitCondition.Event event = (AwaitCondition.Event) cond;
                if (event.hasPredicate()) {
                    throw new IllegalStateException(
                            "Durable EventFlow cannot checkpoint predicate-based event await yet: "
                                    + event.eventType().getName());
                }
                out.add(EventAwaitCheckpoint.event(event.eventType().getName()));
            } else if (cond instanceof AwaitCondition.Deadline) {
                long deadline = deadlineAt(now, ((AwaitCondition.Deadline) cond).millisFromNow());
                earliestDeadline = (earliestDeadline == NO_DEADLINE)
                        ? deadline
                        : Math.min(earliestDeadline, deadline);
            }
        }
        if (earliestDeadline != NO_DEADLINE) {
            out.add(EventAwaitCheckpoint.deadline(earliestDeadline));
        }
        return Collections.unmodifiableList(out);
    }

    private List<EventAwaitCheckpoint> withoutDeadlines(List<EventAwaitCheckpoint> awaits) {
        if (awaits.isEmpty()) {
            return awaits;
        }
        List<EventAwaitCheckpoint> out = new ArrayList<>();
        for (EventAwaitCheckpoint await : awaits) {
            if (await.type() != EventAwaitCheckpoint.Type.DEADLINE) {
                out.add(await);
            }
        }
        return Collections.unmodifiableList(out);
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
                    enqueue(new Command() {
                        @Override
                        public void execute() {
                            failFlow(rt, t);
                        }
                    });
                    return;
                }
                if (matches) {
                    enqueue(new Command() {
                        @Override
                        public void execute() {
                            deliverEvent(rt, event, generation);
                        }
                    });
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
            def.step().onExit(rt.ctx);
        } catch (Throwable ignored) {
            // best-effort cleanup
        }
        fireStepExited(rt.flow, def.stepId());
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
        clearAwaits(rt);
        safeExit(rt);
        rt.flow.cancel();
        deleteCheckpoint(rt);
        fireFlowTerminated(rt.flow);
        remove(rt);
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
                saveCheckpoint(rt);
            } catch (Throwable t) {
                System.err.println("[flower] eventloop " + name + " checkpoint save failed for "
                        + rt.flow.flowId() + ": " + t);
                notifyWorkerError(t);
            }
            clearAwaits(rt);
            safeExit(rt);
            if (!rt.flow.state().isTerminal() && rt.flow.persistence() != FlowPersistence.DURABLE) {
                rt.flow.cancel();
                deleteCheckpoint(rt);
                fireFlowTerminated(rt.flow);
            }
        }
        deadlines.clear();
        inbox.clear();
    }

    private void saveCheckpoint(FlowRuntime rt) {
        if (rt.flow.persistence() != FlowPersistence.DURABLE || rt.flow.state().isTerminal()) {
            return;
        }
        checkpointStore.save(new EventFlowCheckpoint(
                rt.flow.flowId(),
                rt.flow.state(),
                rt.flow.currentStepId(),
                rt.entered,
                rt.flow.persistence(),
                name,
                clock.currentTimeMillis(),
                rt.flow.definitionVersion(),
                rt.flow.executionContext(),
                rt.awaitGeneration,
                rt.awaitCheckpoints));
    }

    private void deleteCheckpoint(FlowRuntime rt) {
        if (rt.flow.persistence() != FlowPersistence.DURABLE) {
            return;
        }
        try {
            checkpointStore.delete(rt.flow.flowId());
        } catch (Throwable t) {
            System.err.println("[flower] eventloop " + name + " checkpoint delete failed for "
                    + rt.flow.flowId() + ": " + t);
            notifyWorkerError(t);
        }
    }

    // ------------------------------------------------------------------
    // Listener fanout
    // ------------------------------------------------------------------

    private void fireFlowSubmitted(EventFlow flow) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener listener : listeners) {
            try {
                listener.onFlowSubmitted(snap);
            } catch (Throwable t) {
                notifyListenerError(snap, "onFlowSubmitted", t);
            }
        }
    }

    private void fireStepEntered(EventFlow flow, String stepId) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener listener : listeners) {
            try {
                listener.onStepEntered(snap, stepId);
            } catch (Throwable t) {
                notifyListenerError(snap, "onStepEntered", t);
            }
        }
    }

    private void fireStepExited(EventFlow flow, String stepId) {
        FlowSnapshot snap = flow.snapshot();
        for (FlowerListener listener : listeners) {
            try {
                listener.onStepExited(snap, stepId);
            } catch (Throwable t) {
                notifyListenerError(snap, "onStepExited", t);
            }
        }
    }

    private void fireFlowTerminated(EventFlow flow) {
        FlowSnapshot snap = flow.snapshot();
        switch (flow.state()) {
            case FINISHED:
                for (FlowerListener listener : listeners) {
                    try {
                        listener.onFlowFinished(snap);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onFlowFinished", t);
                    }
                }
                break;
            case FAILED:
                Throwable cause = flow.failureCause();
                for (FlowerListener listener : listeners) {
                    try {
                        listener.onFlowFailed(snap, cause);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onFlowFailed", t);
                    }
                }
                break;
            case CANCELLED:
                for (FlowerListener listener : listeners) {
                    try {
                        listener.onFlowCancelled(snap);
                    } catch (Throwable t) {
                        notifyListenerError(snap, "onFlowCancelled", t);
                    }
                }
                break;
            default:
                // not terminal
        }
    }

    private void notifyListenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
        for (FlowerListener listener : listeners) {
            try {
                listener.onListenerError(flow, callbackName, cause);
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyWorkerError(Throwable cause) {
        for (FlowerListener listener : listeners) {
            try {
                listener.onWorkerError(name, cause);
            } catch (Throwable ignored) {
            }
        }
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
        public void offload(Runnable task) {
            EventWorker.this.offload(task);
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

    private interface Command {
        void execute();
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
