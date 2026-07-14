package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class DuplicateStepIdRule extends AbstractFactRule {

    public DuplicateStepIdRule() {
        super("FLOWER-CHECK-013", Severity.ERROR,
                "Step ids must be unique within a Flow",
                AnalysisFact.DUPLICATE_STEP_ID);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return "Duplicate step id '" + fact.subject() + "' appears in one Flow builder chain.";
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Flower resolves current step and goTo navigation by step id. Duplicates make "
                + "navigation and dumps ambiguous.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Give every step in the Flow a distinct, stable id.";
    }
}
