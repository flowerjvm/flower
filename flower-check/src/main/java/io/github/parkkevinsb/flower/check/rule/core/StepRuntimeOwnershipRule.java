package io.github.parkkevinsb.flower.check.rule.core;

import io.github.parkkevinsb.flower.check.model.AnalysisFact;
import io.github.parkkevinsb.flower.check.rule.AbstractFactRule;
import io.github.parkkevinsb.flower.check.rule.Severity;

public final class StepRuntimeOwnershipRule extends AbstractFactRule {

    public StepRuntimeOwnershipRule() {
        super("FLOWER-CHECK-011", Severity.ERROR,
                "A Step must not own Engine/Worker lifecycle",
                AnalysisFact.RUNTIME_OWNERSHIP);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Flower layering is Engine -> Worker -> Flow -> Step. A Step that creates or "
                + "controls runtime infrastructure inverts that ownership.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Build Engine/Worker in application wiring or the Spring starter; pass only "
                + "domain services into Steps.";
    }
}
