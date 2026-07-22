package io.github.flowerjvm.flower.check.rule.core;

import io.github.flowerjvm.flower.check.model.AnalysisFact;
import io.github.flowerjvm.flower.check.rule.AbstractFactRule;
import io.github.flowerjvm.flower.check.rule.Severity;

public final class ProviderCallRule extends AbstractFactRule {

    public ProviderCallRule() {
        super("FLOWER-CHECK-002", Severity.ERROR,
                "No direct LLM/provider SDK calls in Flower callbacks",
                AnalysisFact.PROVIDER_CALL);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return "Flower execution callback directly calls a provider/model client: " + fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Model/provider calls are slow and failure-prone. Running them on a Worker tick, "
                + "EventWorker loop, or Guard check blocks progress and hides retry/refine policy.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Use an async service/harness. Core Steps observe completion across ticks; "
                + "EventSteps use runAsync/thenRunAsync and publish a completion event.";
    }
}
