package io.github.parkkevinsb.flower.eventloop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Outcome an {@link EventStep} returns from {@code onEnter}, {@code onEvent},
 * or {@code onTimeout}.
 *
 * <p>Unlike core {@code StepResult}, there is no {@code stay()}. A step that
 * is not finished returns {@link #await(AwaitCondition...)} and explicitly
 * declares what should wake it next.
 *
 * <ul>
 *   <li>{@link #await(AwaitCondition...)}: keep this step and wait for the
 *   declared conditions.</li>
 *   <li>{@link #next()}: finish this step; move to the next declared step,
 *   or finish the flow if this was the last step.</li>
 *   <li>{@link #goTo(String)}: jump to another flow-level step id.</li>
 *   <li>{@link #finish()}: finish the flow successfully now.</li>
 *   <li>{@link #fail(Throwable)}: fail the flow.</li>
 * </ul>
 *
 * <p>{@code onEvent} and {@code onTimeout} may also return {@code null} to mean
 * "ignore this wake-up and keep the current awaits active".
 */
public final class EventStepResult {

    public enum Type { AWAIT, NEXT, GOTO, FINISH, FAIL }

    private final Type type;
    private final List<AwaitCondition> awaits;
    private final String targetStepId;
    private final Throwable cause;
    private final List<EventEffect> effects;

    private EventStepResult(
            Type type,
            List<AwaitCondition> awaits,
            String targetStepId,
            Throwable cause,
            List<EventEffect> effects) {
        this.type = type;
        this.awaits = awaits;
        this.targetStepId = targetStepId;
        this.cause = cause;
        this.effects = effects;
    }

    public static EventStepResult await(AwaitCondition... conditions) {
        if (conditions == null || conditions.length == 0) {
            throw new IllegalArgumentException("await requires at least one condition");
        }
        for (AwaitCondition condition : conditions) {
            if (condition == null) {
                throw new IllegalArgumentException("await condition must not be null");
            }
        }
        List<AwaitCondition> list = Collections.unmodifiableList(Arrays.asList(conditions.clone()));
        return new EventStepResult(Type.AWAIT, list, null, null, Collections.<EventEffect>emptyList());
    }

    public static EventStepResult next() {
        return new EventStepResult(
                Type.NEXT,
                Collections.<AwaitCondition>emptyList(),
                null,
                null,
                Collections.<EventEffect>emptyList());
    }

    public static EventStepResult goTo(String stepId) {
        if (stepId == null || stepId.isEmpty()) {
            throw new IllegalArgumentException("goTo stepId must not be null or empty");
        }
        return new EventStepResult(
                Type.GOTO,
                Collections.<AwaitCondition>emptyList(),
                stepId,
                null,
                Collections.<EventEffect>emptyList());
    }

    public static EventStepResult finish() {
        return new EventStepResult(
                Type.FINISH,
                Collections.<AwaitCondition>emptyList(),
                null,
                null,
                Collections.<EventEffect>emptyList());
    }

    public static EventStepResult fail(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("fail cause must not be null");
        }
        return new EventStepResult(
                Type.FAIL,
                Collections.<AwaitCondition>emptyList(),
                null,
                cause,
                Collections.<EventEffect>emptyList());
    }

    /**
     * Run an effect after the worker has accepted this result.
     *
     * <p>For {@link Type#AWAIT}, effects run after event subscriptions and
     * deadlines are registered. This lets steps safely publish outbound
     * requests without losing synchronous response events.
     */
    public EventStepResult thenRun(EventEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("effect must not be null");
        }
        List<EventEffect> nextEffects = new ArrayList<>(effects);
        nextEffects.add(effect);
        return new EventStepResult(
                type,
                awaits,
                targetStepId,
                cause,
                Collections.unmodifiableList(nextEffects));
    }

    /**
     * Offload an effect after the worker has accepted this result.
     *
     * <p>For {@link Type#AWAIT}, the offload is scheduled after awaits are
     * registered and checkpointed. The offloaded task may block, but it should
     * publish completion/failure events back to {@link EventStepContext#eventBus()}.
     */
    public EventStepResult thenRunOffloaded(final EventEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("effect must not be null");
        }
        return thenRun(new EventEffect() {
            @Override
            public void apply(final EventStepContext ctx) {
                ctx.offload(new Runnable() {
                    @Override
                    public void run() {
                        effect.apply(ctx);
                    }
                });
            }
        });
    }

    /**
     * Publish an event after the worker has accepted this result.
     *
     * <p>Use this with {@link #await(AwaitCondition...)} for request/response
     * workflows:
     *
     * <pre>{@code
     * return EventStepResult.await(AwaitCondition.event(Response.class))
     *         .thenPublish(new Request(...));
     * }</pre>
     */
    public EventStepResult thenPublish(final Object event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        return thenRun(new EventEffect() {
            @Override
            public void apply(EventStepContext ctx) {
                ctx.eventBus().publish(event);
            }
        });
    }

    public Type type() {
        return type;
    }

    public List<AwaitCondition> awaits() {
        return awaits;
    }

    public String targetStepId() {
        return targetStepId;
    }

    public Throwable cause() {
        return cause;
    }

    public List<EventEffect> effects() {
        return effects;
    }
}
