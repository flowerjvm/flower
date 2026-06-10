package io.github.parkkevinsb.flower.check.rule.core;

import io.github.parkkevinsb.flower.check.model.AnalysisFact;
import io.github.parkkevinsb.flower.check.rule.AbstractFactRule;
import io.github.parkkevinsb.flower.check.rule.Severity;

public final class GoToTargetRule extends AbstractFactRule {

    public GoToTargetRule() {
        super("FLOWER-CHECK-009", Severity.ERROR,
                "goTo target must be a declared step id",
                AnalysisFact.GOTO_UNKNOWN_TARGET);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return "StepResult.goTo(\"" + fact.subject() + "\") targets no declared step id found in analyzed flows.";
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Flower navigates by flow-level string step id. A stale or typoed id fails at runtime.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Make the goTo literal match a declared .step(...) or .durableStep(...) id, "
                + "or add the missing step to the Flow builder.";
    }
}
