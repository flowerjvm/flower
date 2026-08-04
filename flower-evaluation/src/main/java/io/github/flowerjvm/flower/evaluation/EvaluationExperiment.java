package io.github.flowerjvm.flower.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

/** One candidate run over one fixed dataset and evaluator suite. */
public final class EvaluationExperiment {

    private final String id;
    private final String name;
    private final EvaluationDataset dataset;
    private final EvaluationCandidate candidate;
    private final EvaluationTarget target;
    private final EvaluationSuite suite;
    private final String baselineExperimentId;
    private final Map<String, Object> metadata;

    private EvaluationExperiment(Builder builder) {
        this.id = EvaluationValues.requireText("id", builder.id);
        this.name = EvaluationValues.requireText("name", builder.name);
        if (builder.dataset == null
                || builder.candidate == null
                || builder.target == null
                || builder.suite == null) {
            throw new IllegalArgumentException(
                    "dataset, candidate, target, and suite must not be null");
        }
        this.dataset = builder.dataset;
        this.candidate = builder.candidate;
        this.target = builder.target;
        this.suite = builder.suite;
        this.baselineExperimentId = EvaluationValues.cleanText(builder.baselineExperimentId);
        if (id.equals(baselineExperimentId)) {
            throw new IllegalArgumentException("experiment cannot use itself as baseline");
        }
        this.metadata = EvaluationValues.immutableMap(builder.metadata);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public EvaluationDataset dataset() {
        return dataset;
    }

    public EvaluationCandidate candidate() {
        return candidate;
    }

    public EvaluationTarget target() {
        return target;
    }

    public EvaluationSuite suite() {
        return suite;
    }

    public String baselineExperimentId() {
        return baselineExperimentId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static final class Builder {
        private final String id;
        private String name;
        private EvaluationDataset dataset;
        private EvaluationCandidate candidate;
        private EvaluationTarget target;
        private EvaluationSuite suite;
        private String baselineExperimentId;
        private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

        private Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder dataset(EvaluationDataset dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder candidate(EvaluationCandidate candidate) {
            this.candidate = candidate;
            return this;
        }

        public Builder target(EvaluationTarget target) {
            this.target = target;
            return this;
        }

        public Builder suite(EvaluationSuite suite) {
            this.suite = suite;
            return this;
        }

        public Builder baselineExperimentId(String baselineExperimentId) {
            this.baselineExperimentId = baselineExperimentId;
            return this;
        }

        public Builder metadata(String name, Object value) {
            metadata.put(EvaluationValues.requireText("metadata name", name), value);
            return this;
        }

        public EvaluationExperiment build() {
            return new EvaluationExperiment(this);
        }
    }
}
