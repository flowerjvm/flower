package io.github.parkkevinsb.flower.check.rule.core;

import io.github.parkkevinsb.flower.check.model.AnalysisFact;
import io.github.parkkevinsb.flower.check.rule.AbstractFactRule;
import io.github.parkkevinsb.flower.check.rule.Severity;

public final class SharedStepInstanceRule extends AbstractFactRule {

    public SharedStepInstanceRule() {
        super("FLOWER-CHECK-015", Severity.WARNING,
                "Do not share a Step instance across Flows",
                AnalysisFact.SHARED_STEP_INSTANCE);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail() + ": " + fact.subject();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "A Step is stateful and owned by one Flow. Reusing one instance can leak "
                + "stepNo, signals, and timeout state between Flow executions.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Construct fresh Step instances inside the Flow factory for each new Flow.";
    }
}
