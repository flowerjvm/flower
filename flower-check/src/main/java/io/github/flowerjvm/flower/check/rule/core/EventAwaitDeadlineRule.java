package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class EventAwaitDeadlineRule extends AbstractFactRule {

    public EventAwaitDeadlineRule() {
        super("FLOWER-CHECK-018", Severity.WARNING,
                "A finite EventStep await should declare a deadline",
                AnalysisFact.MISSING_EVENT_AWAIT_DEADLINE);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.subject() + " waits for an event or signal without a deadline.";
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "An EventFlow has no periodic tick that can discover a lost response. Without "
                + "a matching event or deadline, the Flow can remain asleep forever.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Add AwaitCondition.deadlineIn(...) and handle onTimeout(...) with fail, "
                + "goTo, or an explicit recovery decision.";
    }
}
