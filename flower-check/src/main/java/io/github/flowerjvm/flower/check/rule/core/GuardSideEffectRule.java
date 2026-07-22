package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class GuardSideEffectRule extends AbstractFactRule {

    public GuardSideEffectRule() {
        super("FLOWER-CHECK-017", Severity.ERROR,
                "A Guard must not perform business side effects",
                AnalysisFact.GUARD_SIDE_EFFECT);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.subject() + " performs business work or mutates external state: "
                + fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Flower checks a Guard before Step entry and before every tick. A side effect "
                + "hidden there can repeat without a Step lifecycle, timeout, or retry boundary.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Keep the Guard quick and limited to readiness decisions. Move persistence, "
                + "publication, dispatch, and other business work into a Step.";
    }
}
