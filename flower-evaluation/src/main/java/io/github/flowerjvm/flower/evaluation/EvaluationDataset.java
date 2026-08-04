package io.github.flowerjvm.flower.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, versioned collection of evaluation examples. */
public final class EvaluationDataset {

    private final String id;
    private final String name;
    private final String version;
    private final List<EvaluationExample> examples;
    private final Map<String, Object> metadata;

    private EvaluationDataset(Builder builder) {
        this.id = EvaluationValues.requireText("id", builder.id);
        this.name = EvaluationValues.requireText("name", builder.name);
        this.version = EvaluationValues.requireText("version", builder.version);
        if (builder.examples.isEmpty()) {
            throw new IllegalArgumentException("dataset must contain at least one example");
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (EvaluationExample example : builder.examples) {
            if (example == null) {
                throw new IllegalArgumentException("example must not be null");
            }
            if (!ids.add(example.id())) {
                throw new IllegalArgumentException("duplicate example id: " + example.id());
            }
        }
        this.examples = Collections.unmodifiableList(
                new ArrayList<EvaluationExample>(builder.examples));
        this.metadata = EvaluationValues.immutableMap(builder.metadata);
    }

    public static Builder builder(String id, String version) {
        return new Builder(id, version);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public List<EvaluationExample> examples() {
        return examples;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static final class Builder {
        private final String id;
        private final String version;
        private String name;
        private final List<EvaluationExample> examples = new ArrayList<EvaluationExample>();
        private final Map<String, Object> metadata = new LinkedHashMap<String, Object>();

        private Builder(String id, String version) {
            this.id = id;
            this.version = version;
            this.name = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder example(EvaluationExample example) {
            examples.add(example);
            return this;
        }

        public Builder metadata(String name, Object value) {
            metadata.put(EvaluationValues.requireText("metadata name", name), value);
            return this;
        }

        public EvaluationDataset build() {
            return new EvaluationDataset(this);
        }
    }
}
