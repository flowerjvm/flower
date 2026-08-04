package io.github.flowerjvm.flower.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Agent, prompt, model, Tool, or policy version being evaluated. */
public final class EvaluationCandidate {

    private final String id;
    private final String version;
    private final Map<String, Object> attributes;

    private EvaluationCandidate(Builder builder) {
        this.id = EvaluationValues.requireText("id", builder.id);
        this.version = EvaluationValues.requireText("version", builder.version);
        this.attributes = EvaluationValues.immutableMap(builder.attributes);
    }

    public static Builder builder(String id, String version) {
        return new Builder(id, version);
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public static final class Builder {
        private final String id;
        private final String version;
        private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

        private Builder(String id, String version) {
            this.id = id;
            this.version = version;
        }

        public Builder attribute(String name, Object value) {
            attributes.put(EvaluationValues.requireText("attribute name", name), value);
            return this;
        }

        public EvaluationCandidate build() {
            return new EvaluationCandidate(this);
        }
    }
}
