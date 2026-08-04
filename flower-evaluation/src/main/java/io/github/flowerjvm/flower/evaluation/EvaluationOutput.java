package io.github.flowerjvm.flower.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured result and trace references returned by an evaluation target. */
public final class EvaluationOutput {

    private final Map<String, Object> actual;
    private final Map<String, Double> metrics;
    private final String traceId;
    private final String runId;

    private EvaluationOutput(Builder builder) {
        this.actual = EvaluationValues.immutableMap(builder.actual);
        this.metrics = EvaluationValues.immutableMetrics(builder.metrics);
        this.traceId = EvaluationValues.cleanText(builder.traceId);
        this.runId = EvaluationValues.cleanText(builder.runId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> actual() {
        return actual;
    }

    public Map<String, Double> metrics() {
        return metrics;
    }

    public String traceId() {
        return traceId;
    }

    public String runId() {
        return runId;
    }

    public static final class Builder {
        private final Map<String, Object> actual = new LinkedHashMap<String, Object>();
        private final Map<String, Double> metrics = new LinkedHashMap<String, Double>();
        private String traceId;
        private String runId;

        public Builder actual(String name, Object value) {
            actual.put(EvaluationValues.requireText("actual name", name), value);
            return this;
        }

        public Builder metric(String name, double value) {
            metrics.put(EvaluationValues.requireText("metric name", name), value);
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public EvaluationOutput build() {
            return new EvaluationOutput(this);
        }
    }
}
