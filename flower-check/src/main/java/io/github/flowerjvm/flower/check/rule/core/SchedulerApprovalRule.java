package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class SchedulerApprovalRule extends AbstractFactRule {

    public SchedulerApprovalRule() {
        super("FLOWER-CHECK-016", Severity.ERROR,
                "Recurring scheduler usage requires explicit user approval",
                AnalysisFact.UNAPPROVED_RECURRING_SCHEDULER);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Recurring schedulers can hide orchestration outside Flower's Worker/Flow boundary. "
                + "They are a common escape hatch for polling or approval bypass code, so each "
                + "use must be reviewed explicitly.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Remove the recurring scheduler and model the wait as a Flower Step/event flow, "
                + "or add @FlowerSchedulerApproved (or a configured approval annotation) only "
                + "after the user has approved that scheduled work.";
    }
}
