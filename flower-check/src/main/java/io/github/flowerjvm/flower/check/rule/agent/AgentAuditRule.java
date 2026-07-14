package io.github.flowerjvm.flower.check.rule.agent;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class AgentAuditRule extends AbstractFactRule {

    public AgentAuditRule() {
        super("FLOWER-CHECK-007", Severity.WARNING,
                "Business write must emit/require an audit event",
                AnalysisFact.AGENT_MISSING_AUDIT);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Important agent/business writes need an operation trail so operators can see "
                + "cost, failures, stuck work, and recovery context.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Emit an audit or operation event alongside the write, or make the action "
                + "contract require one.";
    }
}
