package io.github.parkkevinsb.flower.eventloop.worker;

import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpointStore;
import io.github.parkkevinsb.flower.eventloop.step.AwaitCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CheckpointCoordinator {

    private static final long NO_DEADLINE = Long.MIN_VALUE;

    private final String workerName;
    private final Clock clock;
    private final EventFlowCheckpointStore checkpointStore;
    private final ListenerDispatcher listenerDispatcher;

    CheckpointCoordinator(
            String workerName,
            Clock clock,
            EventFlowCheckpointStore checkpointStore,
            ListenerDispatcher listenerDispatcher) {
        this.workerName = workerName;
        this.clock = clock;
        this.checkpointStore = checkpointStore;
        this.listenerDispatcher = listenerDispatcher;
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

    void save(
            EventFlow flow,
            boolean entered,
            long awaitGeneration,
            List<EventAwaitCheckpoint> awaits) {
        if (flow.persistence() != FlowPersistence.DURABLE || flow.state().isTerminal()) {
            return;
        }
        checkpointStore.save(new EventFlowCheckpoint(
                flow.flowId(),
                flow.state(),
                flow.currentStepId(),
                entered,
                flow.persistence(),
                workerName,
                clock.currentTimeMillis(),
                flow.definitionVersion(),
                flow.executionContext(),
                awaitGeneration,
                awaits));
    }

    void delete(EventFlow flow) {
        if (flow.persistence() != FlowPersistence.DURABLE) {
            return;
        }
        try {
            checkpointStore.delete(flow.flowId());
        } catch (Throwable t) {
            System.err.println("[flower] eventloop " + workerName + " checkpoint delete failed for "
                    + flow.flowId() + ": " + t);
            listenerDispatcher.workerError(t);
        }
    }

    private long deadlineAt(long now, long millisFromNow) {
        if (millisFromNow > Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + millisFromNow;
    }
}
