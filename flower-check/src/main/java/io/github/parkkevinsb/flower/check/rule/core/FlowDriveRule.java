package io.github.parkkevinsb.flower.check.rule.core;

import io.github.parkkevinsb.flower.check.model.AnalysisFact;
import io.github.parkkevinsb.flower.check.rule.AbstractFactRule;
import io.github.parkkevinsb.flower.check.rule.Severity;

public final class FlowDriveRule extends AbstractFactRule {

    public FlowDriveRule() {
        super("FLOWER-CHECK-003", Severity.ERROR,
                "A Flow must not directly drive another Flow",
                AnalysisFact.FLOW_DRIVE_CALL);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Tick and lifecycle ownership belongs to the Worker. Driving runtime progress "
                + "inline breaks the deterministic Worker tick boundary.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Submit child work to a Worker and observe it through state, events, signals, "
                + "or durable domain state. Do not call tick/lifecycle methods directly.";
    }
}
