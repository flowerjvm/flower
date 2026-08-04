package io.github.flowerjvm.flower.evaluation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRunnerTest {

    @Test
    void runsEveryExampleAndPublishesAggregateUsage() throws Exception {
        EvaluationDataset dataset = EvaluationDataset.builder("ops", "v1")
                .name("Game operations")
                .example(example("healthy", "NO_ACTION_NEEDED"))
                .example(example("degraded", "RESOLVED"))
                .build();
        EvaluationSuite suite = EvaluationSuite.builder()
                .evaluator(EvaluationEvaluators.expectedEquals("outcome"))
                .evaluator(EvaluationEvaluators.optional(
                        EvaluationEvaluators.metricAtMost(EvaluationMetrics.TOOL_CALLS, 2.0d)))
                .build();
        final List<EvaluationExperimentResult> published =
                new ArrayList<EvaluationExperimentResult>();
        EvaluationExperiment experiment = experiment(
                "candidate-v2",
                dataset,
                suite,
                new EvaluationTarget() {
                    @Override
                    public EvaluationOutput execute(EvaluationExample example) {
                        String outcome = "healthy".equals(example.id())
                                ? "NO_ACTION_NEEDED" : "FAILED";
                        return EvaluationOutput.builder()
                                .actual("outcome", outcome)
                                .metric(EvaluationMetrics.INPUT_TOKENS, 10.0d)
                                .metric(EvaluationMetrics.OUTPUT_TOKENS, 4.0d)
                                .metric(EvaluationMetrics.TOOL_CALLS, 3.0d)
                                .traceId("trace-" + example.id())
                                .runId("run-" + example.id())
                                .build();
                    }
                });

        EvaluationExperimentResult result = new EvaluationRunner(
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC),
                new EvaluationResultSink() {
                    @Override
                    public void publish(EvaluationExperimentResult selected) {
                        published.add(selected);
                    }
                }).run(experiment);

        assertThat(result.getSummary().getPassed()).isEqualTo(1);
        assertThat(result.getSummary().getFailed()).isEqualTo(1);
        assertThat(result.getSummary().getErrors()).isZero();
        assertThat(result.getSummary().getInputTokens()).isEqualTo(20L);
        assertThat(result.getSummary().getOutputTokens()).isEqualTo(8L);
        assertThat(result.getSummary().getToolCalls()).isEqualTo(6L);
        assertThat(result.getCases().get(0).getScores().get(1).isRequired()).isFalse();
        assertThat(published).containsExactly(result);
    }

    @Test
    void isolatesTargetAndEvaluatorFailuresByExample() throws Exception {
        EvaluationDataset dataset = EvaluationDataset.builder("ops", "v1")
                .example(example("target-error", "RESOLVED"))
                .example(example("evaluator-error", "RESOLVED"))
                .build();
        EvaluationSuite suite = EvaluationSuite.builder()
                .evaluator(new Evaluator() {
                    @Override
                    public String id() {
                        return "unstable-evaluator";
                    }

                    @Override
                    public EvaluationScore evaluate(EvaluationContext context) {
                        throw new IllegalArgumentException("not persisted");
                    }
                })
                .build();
        EvaluationExperiment experiment = experiment(
                "candidate-v2",
                dataset,
                suite,
                new EvaluationTarget() {
                    @Override
                    public EvaluationOutput execute(EvaluationExample example) {
                        if ("target-error".equals(example.id())) {
                            throw new IllegalStateException("secret detail");
                        }
                        return EvaluationOutput.builder().actual("outcome", "RESOLVED").build();
                    }
                });

        EvaluationExperimentResult result = new EvaluationRunner().run(experiment);

        assertThat(result.getSummary().getErrors()).isEqualTo(2);
        assertThat(result.getCases().get(0).getErrorType())
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(result.getCases().get(1).getScores().get(0).getReason())
                .isEqualTo(IllegalArgumentException.class.getName());
    }

    @Test
    void optionalEvaluatorFailureDoesNotFailTheCase() throws Exception {
        EvaluationSuite suite = EvaluationSuite.builder()
                .evaluator(EvaluationEvaluators.optional(new Evaluator() {
                    @Override
                    public String id() {
                        return "advisory";
                    }

                    @Override
                    public EvaluationScore evaluate(EvaluationContext context) {
                        throw new IllegalStateException("offline");
                    }
                }))
                .build();
        EvaluationExperiment experiment = experiment(
                "candidate-v2",
                EvaluationDataset.builder("ops", "v1")
                        .example(example("healthy", "NO_ACTION_NEEDED"))
                        .build(),
                suite,
                new EvaluationTarget() {
                    @Override
                    public EvaluationOutput execute(EvaluationExample example) {
                        return EvaluationOutput.builder().actual("outcome", "NO_ACTION_NEEDED").build();
                    }
                });

        EvaluationExperimentResult result = new EvaluationRunner().run(experiment);

        assertThat(result.getCases().get(0).getStatus()).isEqualTo(EvaluationCaseStatus.PASS);
        assertThat(result.getCases().get(0).getScores().get(0).getVerdict())
                .isEqualTo(EvaluationVerdict.ERROR);
    }

    private static EvaluationExample example(String id, String outcome) {
        return EvaluationExample.builder(id)
                .input("serverId", "server-" + id)
                .expected("outcome", outcome)
                .build();
    }

    private static EvaluationExperiment experiment(
            String id,
            EvaluationDataset dataset,
            EvaluationSuite suite,
            EvaluationTarget target) {
        return EvaluationExperiment.builder(id)
                .name("Candidate evaluation")
                .dataset(dataset)
                .candidate(EvaluationCandidate.builder("game-agent", "v2").build())
                .target(target)
                .suite(suite)
                .build();
    }
}
