package io.github.flowerjvm.flower.evaluation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationComparatorTest {

    @Test
    void reportsRegressionsImprovementsAndEvaluatorDeltas() throws Exception {
        EvaluationDataset dataset = EvaluationDataset.builder("ops", "v1")
                .example(EvaluationExample.builder("one").expected("ok", true).build())
                .example(EvaluationExample.builder("two").expected("ok", true).build())
                .build();
        EvaluationSuite suite = EvaluationSuite.builder()
                .evaluator(EvaluationEvaluators.expectedEquals("ok"))
                .build();
        EvaluationExperimentResult baseline = run(
                "baseline", dataset, suite, true, false);
        EvaluationExperimentResult candidate = run(
                "candidate", dataset, suite, false, true);

        EvaluationComparison comparison = EvaluationComparator.compare(baseline, candidate);

        assertThat(comparison.getRegressedExampleIds()).containsExactly("one");
        assertThat(comparison.getImprovedExampleIds()).containsExactly("two");
        assertThat(comparison.getPassRateDelta()).isZero();
        assertThat(comparison.getEvaluatorDeltas()).hasSize(1);
    }

    private static EvaluationExperimentResult run(
            String id,
            EvaluationDataset dataset,
            EvaluationSuite suite,
            final boolean one,
            final boolean two) throws Exception {
        EvaluationExperiment experiment = EvaluationExperiment.builder(id)
                .dataset(dataset)
                .candidate(EvaluationCandidate.builder("agent", id).build())
                .target(new EvaluationTarget() {
                    @Override
                    public EvaluationOutput execute(EvaluationExample example) {
                        return EvaluationOutput.builder()
                                .actual("ok", "one".equals(example.id()) ? one : two)
                                .build();
                    }
                })
                .suite(suite)
                .build();
        return new EvaluationRunner(Clock.fixed(
                Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)).run(experiment);
    }
}
