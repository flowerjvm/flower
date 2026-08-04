package io.github.flowerjvm.flower.evaluation;

/** Host-defined deterministic quality rule used by a rule evaluator. */
@FunctionalInterface
public interface EvaluationRule {

    boolean test(EvaluationContext context) throws Exception;
}
