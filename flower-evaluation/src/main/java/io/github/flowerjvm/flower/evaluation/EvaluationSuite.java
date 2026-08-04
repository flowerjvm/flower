package io.github.flowerjvm.flower.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ordered set of evaluators applied to every completed dataset example. */
public final class EvaluationSuite {

    private final List<Evaluator> evaluators;

    private EvaluationSuite(Builder builder) {
        if (builder.evaluators.isEmpty()) {
            throw new IllegalArgumentException("suite must contain at least one evaluator");
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (Evaluator evaluator : builder.evaluators) {
            if (evaluator == null) {
                throw new IllegalArgumentException("evaluator must not be null");
            }
            String id = EvaluationValues.requireText("evaluator id", evaluator.id());
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate evaluator id: " + id);
            }
        }
        this.evaluators = Collections.unmodifiableList(
                new ArrayList<Evaluator>(builder.evaluators));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Evaluator> evaluators() {
        return evaluators;
    }

    public static final class Builder {
        private final List<Evaluator> evaluators = new ArrayList<Evaluator>();

        public Builder evaluator(Evaluator evaluator) {
            evaluators.add(evaluator);
            return this;
        }

        public EvaluationSuite build() {
            return new EvaluationSuite(this);
        }
    }
}
