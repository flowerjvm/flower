package demo;

import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class GoodStep extends Step {

    @Override
    protected StepResult onTick(StepContext ctx) {
        return StepResult.done();
    }
}
