package io.github.flowerjvm.flower.eventloop.worker;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;
import io.github.flowerjvm.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpointStore;
import io.github.flowerjvm.flower.eventloop.step.AwaitCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class CheckpointCoordinator {

    interface Observer {
        void onSaved(EventFlow flow, EventFlowCheckpoint checkpoint, String action);

        void onFailed(EventFlow flow, Throwable cause, String action);
    }

    private static final long NO_DEADLINE = Long.MIN_VALUE;

    private final String workerName;
    private final Clock clock;
    private final EventFlowCheckpointStore checkpointStore;
    private final ListenerDispatcher listenerDispatcher;
    private final Observer observer;
    private final ConcurrentMap<FlowId, EventFlowCheckpoint> lastSaved = new ConcurrentHashMap<>();

    CheckpointCoordinator(
            String workerName,
            Clock clock,
            EventFlowCheckpointStore checkpointStore,
            ListenerDispatcher listenerDispatcher,
            Observer observer) {
        this.workerName = workerName;
        this.clock = clock;
        this.checkpointStore = checkpointStore;
        this.listenerDispatcher = listenerDispatcher;
        this.observer = observer;
    }

    List<EventAwaitCheckpoint> checkpointAwaitsFor(
            EventFlow flow,
            List<AwaitCondition> conditions,
            long now) {
        if (flow.persistence() != FlowPersistence.DURABLE) {
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
            } else if (cond instanceof AwaitCondition.Signal) {
                AwaitCondition.Signal signal = (AwaitCondition.Signal) cond;
                out.add(EventAwaitCheckpoint.signal(signal.name(), signal.key()));
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

    List<EventAwaitCheckpoint> withoutDeadlines(List<EventAwaitCheckpoint> awaits) {
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

    boolean saveActive(
            EventFlow flow,
            boolean entered,
            long awaitGeneration,
            List<EventAwaitCheckpoint> awaits,
            ExecutionContext checkpointContext) {
        if (!requiresCheckpoint(flow) || flow.state().isTerminal()) {
            return true;
        }
        EventFlowCheckpoint checkpoint = checkpoint(
                flow,
                entered,
                awaitGeneration,
                awaits,
                checkpointContext);
        EventFlowCheckpoint previous = lastSaved.get(flow.flowId());
        if (checkpoint.sameStoredPositionAs(previous)) {
            return true;
        }
        return save(flow, checkpoint, "save", "ACTIVE");
    }

    boolean supportsDurableFlows() {
        return checkpointStore.capabilities().durable();
    }

    boolean saveTerminalTombstone(
            EventFlow flow,
            long awaitGeneration,
            ExecutionContext checkpointContext) {
        if (!requiresCheckpoint(flow)) {
            return true;
        }
        if (flow.state() == FlowState.CHECKPOINT_FAILED) {
            return false;
        }
        return save(flow, checkpoint(
                flow,
                false,
                awaitGeneration,
                Collections.<EventAwaitCheckpoint>emptyList(),
                checkpointContext), "terminal save", "TERMINAL");
    }

    void cleanupTerminalTombstoneBestEffort(EventFlow flow) {
        if (!requiresCheckpoint(flow)) {
            return;
        }
        try {
            checkpointStore.delete(flow.flowId());
        } catch (Throwable t) {
            System.err.println("[flower] eventloop " + workerName + " terminal checkpoint cleanup failed for "
                    + flow.flowId() + ": " + t);
            listenerDispatcher.workerError(t);
        } finally {
            forget(flow.flowId());
        }
    }

    void forget(FlowId flowId) {
        if (flowId != null) {
            lastSaved.remove(flowId);
        }
    }

    private EventFlowCheckpoint checkpoint(
            EventFlow flow,
            boolean entered,
            long awaitGeneration,
            List<EventAwaitCheckpoint> awaits,
            ExecutionContext checkpointContext) {
        return new EventFlowCheckpoint(
                flow.flowId(),
                flow.state(),
                flow.currentStepId(),
                entered,
                flow.persistence(),
                workerName,
                clock.currentTimeMillis(),
                flow.definitionVersion(),
                checkpointContext == null ? flow.executionContext() : checkpointContext,
                awaitGeneration,
                awaits);
    }

    private boolean save(
            EventFlow flow,
            EventFlowCheckpoint checkpoint,
            String failureAction,
            String traceAction) {
        try {
            checkpointStore.save(checkpoint);
            lastSaved.put(flow.flowId(), checkpoint);
            notifySaved(flow, checkpoint, traceAction);
            return true;
        } catch (Throwable t) {
            markCheckpointFailed(flow, t, failureAction);
            return false;
        }
    }

    private boolean requiresCheckpoint(EventFlow flow) {
        return flow.persistence() == FlowPersistence.DURABLE;
    }

    private void markCheckpointFailed(EventFlow flow, Throwable cause, String action) {
        forget(flow.flowId());
        flow.checkpointFailed(cause);
        notifyFailed(flow, cause, action);
        listenerDispatcher.workerError(cause);
        System.err.println("[flower] eventloop " + workerName + " checkpoint " + action + " failed for "
                + flow.flowId() + ": " + cause);
    }

    private void notifySaved(EventFlow flow, EventFlowCheckpoint checkpoint, String action) {
        if (observer == null) {
            return;
        }
        try {
            observer.onSaved(flow, checkpoint, action);
        } catch (Throwable ignored) {
        }
    }

    private void notifyFailed(EventFlow flow, Throwable cause, String action) {
        if (observer == null) {
            return;
        }
        try {
            observer.onFailed(flow, cause, action);
        } catch (Throwable ignored) {
        }
    }

    private long deadlineAt(long now, long millisFromNow) {
        if (millisFromNow > Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + millisFromNow;
    }
}
