package io.github.parkkevinsb.flower.core.support;

import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable Step implementations for tests.
 */
public final class TestSteps {

    private TestSteps() {}

    /** Records every lifecycle call against the supplied trace list. */
    public static final class RecordingStep extends Step {
        private final String tag;
        private final List<String> trace;
        private final StepResult result;

        public RecordingStep(String tag, List<String> trace, StepResult result) {
            this.tag = tag;
            this.trace = trace;
            this.result = result;
        }

        @Override
        protected void onEnter(StepContext ctx) {
            trace.add("enter:" + tag);
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            trace.add("tick:" + tag);
            return result;
        }

        @Override
        protected void onExit(StepContext ctx) {
            trace.add("exit:" + tag);
        }

        @Override
        protected void onReset(StepContext ctx) {
            trace.add("reset:" + tag);
        }
    }

    /** Returns DONE on the Nth tick, STAY before that. */
    public static final class CountThenDoneStep extends Step {
        private final int targetTicks;
        private int ticks;
        public final List<Integer> stepNoSeen = new ArrayList<>();

        public CountThenDoneStep(int targetTicks) {
            this.targetTicks = targetTicks;
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            stepNoSeen.add(ctx.stepNo());
            ticks++;
            return ticks >= targetTicks ? StepResult.done() : StepResult.stay();
        }
    }

    /** Drives an internal stepNo cursor across three sub-steps then completes the step. */
    public static final class StepNoCursorStep extends Step {
        public final List<Integer> trace = new ArrayList<>();

        @Override
        protected StepResult onTick(StepContext ctx) {
            trace.add(ctx.stepNo());
            switch (ctx.stepNo()) {
                case 0:
                    ctx.setStepNo(10);
                    return StepResult.stay();
                case 10:
                    ctx.setStepNo(20);
                    return StepResult.stay();
                case 20:
                    return StepResult.done();
                default:
                    return StepResult.fail(new IllegalStateException("bad stepNo: " + ctx.stepNo()));
            }
        }
    }
}
