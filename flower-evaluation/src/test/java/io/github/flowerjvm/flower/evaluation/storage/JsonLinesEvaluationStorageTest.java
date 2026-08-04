package io.github.flowerjvm.flower.evaluation.storage;

import io.github.flowerjvm.flower.evaluation.EvaluationCandidate;
import io.github.flowerjvm.flower.evaluation.EvaluationDataset;
import io.github.flowerjvm.flower.evaluation.EvaluationEvaluators;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationExperiment;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationFeedback;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;
import io.github.flowerjvm.flower.evaluation.EvaluationRunner;
import io.github.flowerjvm.flower.evaluation.EvaluationSuite;
import io.github.flowerjvm.flower.evaluation.EvaluationTarget;
import io.github.flowerjvm.flower.evaluation.FeedbackRating;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLinesEvaluationStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resultSourceKeepsLatestDuplicateAndReportsBadLines() throws Exception {
        Path file = temporaryDirectory.resolve("evaluations.jsonl");
        EvaluationExperimentResult result = result("experiment-one", "v1");
        JsonLinesEvaluationResultSink sink = new JsonLinesEvaluationResultSink(file);
        sink.publish(result);
        Files.write(
                file,
                ("not-json\n{\"recordType\":\"OTHER\"}\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
        sink.publish(result("experiment-one", "v2"));

        EvaluationResultSnapshot snapshot =
                new JsonLinesEvaluationResultSource(file, 10).load();

        assertThat(snapshot.getExperiments()).hasSize(1);
        assertThat(snapshot.getExperiments().get(0).getCandidateVersion()).isEqualTo("v2");
        assertThat(snapshot.getDiagnostics().getMalformedCount()).isEqualTo(1L);
        assertThat(snapshot.getDiagnostics().getIgnoredCount()).isEqualTo(1L);
        assertThat(snapshot.getDiagnostics().getDuplicateCount()).isEqualTo(1L);
        assertThat(snapshot.getDiagnostics().getMalformedLines()).containsExactly(2L);
    }

    @Test
    void feedbackRoundTripsAndMissingFilesStayAbsent() throws Exception {
        Path file = temporaryDirectory.resolve("nested/feedback.jsonl");
        JsonLinesEvaluationFeedbackSource source =
                new JsonLinesEvaluationFeedbackSource(file, 10);

        assertThat(source.load().getFeedback()).isEmpty();
        assertThat(Files.exists(file)).isFalse();

        EvaluationFeedback feedback = EvaluationFeedback.builder(
                        "feedback-one", "experiment-one", FeedbackRating.PARTIAL)
                .exampleId("healthy")
                .label("needs-review")
                .comment("Evidence was useful, but incomplete.")
                .createdAt(Instant.parse("2026-08-05T00:00:00Z"))
                .build();
        new JsonLinesEvaluationFeedbackSink(file).publish(feedback);

        EvaluationFeedbackSnapshot snapshot = source.load();
        assertThat(snapshot.getFeedback()).hasSize(1);
        assertThat(snapshot.getFeedback().get(0).getLabels()).containsExactly("needs-review");
        assertThat(snapshot.getDiagnostics().isExists()).isTrue();
    }

    @Test
    void sourceReturnsOnlyNewestBoundedRecords() throws Exception {
        Path file = temporaryDirectory.resolve("bounded.jsonl");
        JsonLinesEvaluationResultSink sink = new JsonLinesEvaluationResultSink(file);
        sink.publish(result("one", "v1"));
        sink.publish(result("two", "v1"));
        sink.publish(result("three", "v1"));

        EvaluationResultSnapshot snapshot =
                new JsonLinesEvaluationResultSource(file, 2).load();

        assertThat(snapshot.getExperiments())
                .extracting(EvaluationExperimentResult::getExperimentId)
                .containsExactly("three", "two");
        assertThat(snapshot.getDiagnostics().getTruncatedCount()).isEqualTo(1L);
    }

    private static EvaluationExperimentResult result(String id, String version) throws Exception {
        EvaluationDataset dataset = EvaluationDataset.builder("ops", "v1")
                .example(EvaluationExample.builder("healthy").expected("ok", true).build())
                .build();
        EvaluationExperiment experiment = EvaluationExperiment.builder(id)
                .dataset(dataset)
                .candidate(EvaluationCandidate.builder("agent", version).build())
                .target(new EvaluationTarget() {
                    @Override
                    public EvaluationOutput execute(EvaluationExample example) {
                        return EvaluationOutput.builder()
                                .actual("ok", true)
                                .traceId("trace-one")
                                .build();
                    }
                })
                .suite(EvaluationSuite.builder()
                        .evaluator(EvaluationEvaluators.expectedEquals("ok"))
                        .build())
                .build();
        return new EvaluationRunner().run(experiment);
    }
}
