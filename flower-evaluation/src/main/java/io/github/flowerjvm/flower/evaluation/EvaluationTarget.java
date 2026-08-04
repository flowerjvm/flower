package io.github.flowerjvm.flower.evaluation;

/** Executes one dataset example using host-owned Agent, Harness, or application code. */
public interface EvaluationTarget {

    EvaluationOutput execute(EvaluationExample example) throws Exception;
}
