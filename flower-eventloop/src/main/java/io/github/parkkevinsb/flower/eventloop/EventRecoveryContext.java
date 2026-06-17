package io.github.parkkevinsb.flower.eventloop;

import java.util.List;

/**
 * Recovery information passed to {@link EventStep#onRecover}.
 *
 * <p>The step should use this to re-register the awaits represented by the
 * checkpoint without repeating one-shot request effects that already happened
 * before the process stopped.
 */
public final class EventRecoveryContext {

    private final EventFlowCheckpoint checkpoint;

    EventRecoveryContext(EventFlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        this.checkpoint = checkpoint;
    }

    public EventFlowCheckpoint checkpoint() {
        return checkpoint;
    }

    public List<EventAwaitCheckpoint> awaits() {
        return checkpoint.awaits();
    }

    public long awaitGeneration() {
        return checkpoint.awaitGeneration();
    }

    public long millisUntil(EventAwaitCheckpoint deadlineAwait, long nowMillis) {
        if (deadlineAwait == null) {
            throw new IllegalArgumentException("deadlineAwait must not be null");
        }
        if (deadlineAwait.type() != EventAwaitCheckpoint.Type.DEADLINE) {
            throw new IllegalArgumentException("await is not a deadline: " + deadlineAwait);
        }
        return Math.max(0L, deadlineAwait.deadlineAtMillis() - nowMillis);
    }
}
