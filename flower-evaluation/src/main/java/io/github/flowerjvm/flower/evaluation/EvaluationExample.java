package io.github.flowerjvm.flower.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One versioned dataset case with structured input and expected facts. */
public final class EvaluationExample {

    private final String id;
    private final Map<String, Object> input;
    private final Map<String, Object> expected;
    private final Set<String> tags;
    private final Map<String, Object> metadata;

    private EvaluationExample(Builder builder) {
        this.id = EvaluationValues.requireText("id", builder.id);
        this.input = EvaluationValues.immutableMap(builder.input);
        this.expected = EvaluationValues.immutableMap(builder.expected);
        this.tags = EvaluationValues.immutableTextSet(builder.tags);
        this.metadata = EvaluationValues.immutableMap(builder.metadata);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public Map<String, Object> input() {
        return input;
    }

    public Map<String, Object> expected() {
        return expected;
    }

    public Set<String> tags() {
        return tags;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static final class Builder {
        private final String id;
        private final Map<String, Object> input = new LinkedHashMap<String, Object>();
        private final Map<String, Object> expected = new LinkedHashMap<String, Object>();
        private final List<String> tags = new ArrayList<String>();
        private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder input(String name, Object value) {
            input.put(EvaluationValues.requireText("input name", name), value);
            return this;
        }

        public Builder expected(String name, Object value) {
            expected.put(EvaluationValues.requireText("expected name", name), value);
            return this;
        }

        public Builder tag(String tag) {
            tags.add(tag);
            return this;
        }

        public Builder metadata(String name, Object value) {
            metadata.put(EvaluationValues.requireText("metadata name", name), value);
            return this;
        }

        public EvaluationExample build() {
            return new EvaluationExample(this);
        }
    }
}
