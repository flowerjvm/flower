package io.github.flowerjvm.flower.evaluation;

/** Scores one completed target output. Implementations may be rules, code, LLMs, or external services. */
public interface Evaluator {

    String id();

    EvaluationScore evaluate(EvaluationContext context) throws Exception;

    default boolean required() {
        return true;
    }
}
