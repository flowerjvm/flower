package demo;

import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;

public final class LegacyBlockingStep extends Step {

    @Override
    protected StepResult onTick(StepContext ctx) {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return StepResult.done();
    }
}
