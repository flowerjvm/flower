package io.github.flowerjvm.flower.evaluation;

/** Receives explicit human feedback for persistence or external transport. */
public interface EvaluationFeedbackSink {

    void publish(EvaluationFeedback feedback) throws Exception;
}
