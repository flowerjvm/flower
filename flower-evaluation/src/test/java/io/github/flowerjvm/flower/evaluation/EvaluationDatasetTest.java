package io.github.flowerjvm.flower.evaluation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationDatasetTest {

    @Test
    void recursivelyCopiesStructuredValues() {
        List<String> values = new ArrayList<String>();
        values.add("initial");
        EvaluationExample example = EvaluationExample.builder("one")
                .input("values", values)
                .build();

        values.add("later");

        assertThat(example.input().get("values"))
                .isEqualTo(java.util.Collections.singletonList("initial"));
        assertThatThrownBy(() -> example.input().put("extra", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateExampleIds() {
        assertThatThrownBy(() -> EvaluationDataset.builder("ops", "v1")
                .example(EvaluationExample.builder("same").build())
                .example(EvaluationExample.builder("same").build())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate example id");
    }

    @Test
    void feedbackRequiresAnExplicitTimestamp() {
        assertThatThrownBy(() -> EvaluationFeedback.builder(
                        "feedback-1", "experiment-1", FeedbackRating.POSITIVE)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("createdAt");
    }
}
