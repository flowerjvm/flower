package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class MissingTimeoutRule extends AbstractFactRule {

    public MissingTimeoutRule() {
        super("FLOWER-CHECK-004", Severity.WARNING,
                "A waiting Step must have timeout/cancellation",
                AnalysisFact.MISSING_TIMEOUT);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.subject() + " can stay while waiting for a signal/event, but no timeout path was found.";
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "A missed external event can strand the Flow forever, leaving the Worker to keep "
                + "ticking a Step that will never progress.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Call ctx.startTimeout(...) when entering the wait and handle ctx.timedOut() "
                + "with fail(...), repeat(), or a recovery goTo.";
    }
}
