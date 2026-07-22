package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class RawSubscriptionRule extends AbstractFactRule {

    public RawSubscriptionRule() {
        super("FLOWER-CHECK-012", Severity.WARNING,
                "Prefer framework-managed subscriptions in a Step",
                AnalysisFact.RAW_SUBSCRIPTION);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Subscriptions made through ctx.subscribe(...) are released by Flower when the "
                + "Step exits, resets, or terminates. Raw EventBus subscriptions are not.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        if (fact.detail().startsWith("EventStep")) {
            return "Declare inbound wakeups with EventStepResult.await(AwaitCondition.event/signal/deadline).";
        }
        return "Use ctx.subscribe(...). If raw eventBus() access is required, store the "
                + "Subscription and unsubscribe it in onExit/onReset.";
    }
}
