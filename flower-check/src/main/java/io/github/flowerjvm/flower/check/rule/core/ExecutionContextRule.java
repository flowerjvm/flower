package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class ExecutionContextRule extends AbstractFactRule {

    public ExecutionContextRule() {
        super("FLOWER-CHECK-014", Severity.WARNING,
                "ExecutionContext is not a business context",
                AnalysisFact.EXECUTION_CONTEXT_BUSINESS_USE);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "ExecutionContext is only an execution identity card: tenant, user, session, "
                + "run, trace, and correlation. Business policy/state belongs outside core.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Keep identity in ExecutionContext and move roles, approvals, policy, actions, "
                + "and domain state to domain services or higher-level agent modules.";
    }
}
