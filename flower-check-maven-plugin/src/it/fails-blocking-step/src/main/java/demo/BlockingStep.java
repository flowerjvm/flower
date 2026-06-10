package demo;

import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class BlockingStep extends Step {

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
