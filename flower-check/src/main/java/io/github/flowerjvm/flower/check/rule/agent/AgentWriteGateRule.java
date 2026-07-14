package io.github.flowerjvm.flower.check.rule.agent;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class AgentWriteGateRule extends AbstractFactRule {

    public AgentWriteGateRule() {
        super("FLOWER-CHECK-006", Severity.ERROR,
                "Agent write must not bypass ActionRegistry/PolicyGate",
                AnalysisFact.AGENT_WRITE_BYPASS);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Agent write actions need a registered, policy-checked boundary so writes are "
                + "controlled, observable, and revocable.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Route the write through the configured ActionRegistry/PolicyGate path instead "
                + "of performing the side effect directly.";
    }
}
