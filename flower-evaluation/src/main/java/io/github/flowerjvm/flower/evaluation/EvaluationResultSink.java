package io.github.flowerjvm.flower.evaluation;

/** Receives completed experiment results for persistence or external transport. */
public interface EvaluationResultSink {

    void publish(EvaluationExperimentResult result) throws Exception;

    static EvaluationResultSink noop() {
        return new EvaluationResultSink() {
            @Override
            public void publish(EvaluationExperimentResult result) {
                // Intentionally empty.
            }
        };
    }
}
