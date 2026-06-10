package io.github.parkkevinsb.flower.check.rule.core;

import io.github.parkkevinsb.flower.check.model.AnalysisFact;
import io.github.parkkevinsb.flower.check.rule.AbstractFactRule;
import io.github.parkkevinsb.flower.check.rule.Severity;

public final class ProviderCallRule extends AbstractFactRule {

    public ProviderCallRule() {
        super("FLOWER-CHECK-002", Severity.ERROR,
                "No direct LLM/provider SDK calls in a Step",
                AnalysisFact.PROVIDER_CALL);
    }

    @Override
    protected String what(AnalysisFact fact) {
        return "Step lifecycle directly calls a provider/model client: " + fact.detail();
    }

    @Override
    protected String why(AnalysisFact fact) {
        return "Model/provider calls are slow and failure-prone. Running them on a Worker tick "
                + "blocks other Flows and hides retry/refine policy inside a Step.";
    }

    @Override
    protected String fix(AnalysisFact fact) {
        return "Submit provider work to an async service or harness in onEnter, then return "
                + "StepResult.stay() until an event, signal, or timeout resolves it.";
    }
}
