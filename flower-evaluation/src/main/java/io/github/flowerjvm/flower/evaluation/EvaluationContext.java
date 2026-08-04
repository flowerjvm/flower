package io.github.flowerjvm.flower.evaluation;

/** Input to one deterministic, LLM-backed, or host-defined evaluator. */
public final class EvaluationContext {

    private final EvaluationDataset dataset;
    private final EvaluationCandidate candidate;
    private final EvaluationExample example;
    private final EvaluationOutput output;

    EvaluationContext(
            EvaluationDataset dataset,
            EvaluationCandidate candidate,
            EvaluationExample example,
            EvaluationOutput output) {
        this.dataset = dataset;
        this.candidate = candidate;
        this.example = example;
        this.output = output;
    }

    public EvaluationDataset dataset() {
        return dataset;
    }

    public EvaluationCandidate candidate() {
        return candidate;
    }

    public EvaluationExample example() {
        return example;
    }

    public EvaluationOutput output() {
        return output;
    }
}
