package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class EventCallbackRule extends AbstractFactRule {

    public EventCallbackRule() {
        super("FLOWER-CHECK-010", Severity.ERROR,
                "Event callbacks may only record, not decide",
                AnalysisFact.CALLBACK_CONTROL);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Event handlers can run on a publisher thread. Flower progress must be decided "
                + "later by Step.onTick on the Worker thread.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Only record ctx.signal(...) or enqueue a payload in the callback; convert it "
                + "to StepResult from onTick.";
    }
}
