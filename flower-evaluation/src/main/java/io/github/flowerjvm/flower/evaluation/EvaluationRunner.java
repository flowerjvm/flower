package io.github.flowerjvm.flower.evaluation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runs an evaluation experiment sequentially and publishes its immutable result.
 *
 * <p>The target may block. Run this class from a test, build job, or host executor,
 * never from a Flower Worker tick.</p>
 */
public final class EvaluationRunner {

    private final Clock clock;
    private final EvaluationResultSink sink;

    public EvaluationRunner() {
        this(Clock.systemUTC(), EvaluationResultSink.noop());
    }

    public EvaluationRunner(Clock clock) {
        this(clock, EvaluationResultSink.noop());
    }

    public EvaluationRunner(Clock clock, EvaluationResultSink sink) {
        if (clock == null || sink == null) {
            throw new IllegalArgumentException("clock and sink must not be null");
        }
        this.clock = clock;
        this.sink = sink;
    }

    public EvaluationExperimentResult run(EvaluationExperiment experiment) throws Exception {
        if (experiment == null) {
            throw new IllegalArgumentException("experiment must not be null");
        }

        Instant startedAt = clock.instant();
        List<EvaluationExampleResult> cases = new ArrayList<EvaluationExampleResult>();
        for (EvaluationExample example : experiment.dataset().examples()) {
            cases.add(runExample(experiment, example));
        }
        Instant completedAt = clock.instant();

        EvaluationSummary summary = summarize(cases, startedAt, completedAt);
        EvaluationExperimentResult result = new EvaluationExperimentResult(
                EvaluationExperimentResult.SCHEMA_VERSION,
                experiment.id(),
                experiment.name(),
                experiment.baselineExperimentId(),
                experiment.dataset().id(),
                experiment.dataset().name(),
                experiment.dataset().version(),
                experiment.candidate().id(),
                experiment.candidate().version(),
                experiment.candidate().attributes(),
                experiment.metadata(),
                startedAt.toString(),
                completedAt.toString(),
                summary,
                cases);
        sink.publish(result);
        return result;
    }

    private EvaluationExampleResult runExample(
            EvaluationExperiment experiment,
            EvaluationExample example) {
        Instant startedAt = clock.instant();
        EvaluationOutput output;
        try {
            output = experiment.target().execute(example);
            if (output == null) {
                throw new IllegalStateException("evaluation target returned null");
            }
        } catch (Exception failure) {
            Instant completedAt = clock.instant();
            return new EvaluationExampleResult(
                    example.id(),
                    EvaluationCaseStatus.ERROR,
                    null,
                    null,
                    startedAt.toString(),
                    completedAt.toString(),
                    example.input(),
                    example.expected(),
                    Collections.<String, Object>emptyMap(),
                    Collections.<String, Double>emptyMap(),
                    Collections.<EvaluationScore>emptyList(),
                    failure.getClass().getName());
        }

        EvaluationContext context = new EvaluationContext(
                experiment.dataset(), experiment.candidate(), example, output);
        List<EvaluationScore> scores = new ArrayList<EvaluationScore>();
        EvaluationCaseStatus status = EvaluationCaseStatus.PASS;
        for (Evaluator evaluator : experiment.suite().evaluators()) {
            EvaluationScore score = evaluate(evaluator, context);
            scores.add(score);
            if (evaluator.required() && score.getVerdict() == EvaluationVerdict.ERROR) {
                status = EvaluationCaseStatus.ERROR;
            } else if (status != EvaluationCaseStatus.ERROR
                    && evaluator.required()
                    && score.getVerdict() == EvaluationVerdict.FAIL) {
                status = EvaluationCaseStatus.FAIL;
            }
        }

        Instant completedAt = clock.instant();
        return new EvaluationExampleResult(
                example.id(),
                status,
                output.traceId(),
                output.runId(),
                startedAt.toString(),
                completedAt.toString(),
                example.input(),
                example.expected(),
                output.actual(),
                output.metrics(),
                scores,
                null);
    }

    private static EvaluationScore evaluate(Evaluator evaluator, EvaluationContext context) {
        String evaluatorId = EvaluationValues.requireText("evaluator id", evaluator.id());
        try {
            EvaluationScore score = evaluator.evaluate(context);
            if (score == null) {
                throw new IllegalStateException("evaluator returned null");
            }
            if (!evaluatorId.equals(score.getEvaluatorId())) {
                throw new IllegalStateException("evaluator returned a score for a different id");
            }
            return score.withRequired(evaluator.required());
        } catch (Exception failure) {
            return EvaluationScore.error(evaluatorId, failure.getClass().getName())
                    .withRequired(evaluator.required());
        }
    }

    private static EvaluationSummary summarize(
            List<EvaluationExampleResult> cases,
            Instant startedAt,
            Instant completedAt) {
        int passed = 0;
        int failed = 0;
        int errors = 0;
        double scoreSum = 0.0d;
        int scoreCount = 0;
        long inputTokens = 0L;
        long outputTokens = 0L;
        long toolCalls = 0L;

        for (EvaluationExampleResult result : cases) {
            if (result.getStatus() == EvaluationCaseStatus.PASS) {
                passed++;
            } else if (result.getStatus() == EvaluationCaseStatus.FAIL) {
                failed++;
            } else {
                errors++;
            }
            for (EvaluationScore score : result.getScores()) {
                if (score.getVerdict() != EvaluationVerdict.ERROR) {
                    scoreSum += score.getValue();
                    scoreCount++;
                }
            }
            inputTokens += metricAsLong(result.getMetrics(), EvaluationMetrics.INPUT_TOKENS);
            outputTokens += metricAsLong(result.getMetrics(), EvaluationMetrics.OUTPUT_TOKENS);
            toolCalls += metricAsLong(result.getMetrics(), EvaluationMetrics.TOOL_CALLS);
        }

        int total = cases.size();
        return new EvaluationSummary(
                total,
                passed,
                failed,
                errors,
                total == 0 ? 0.0d : ((double) passed) / total,
                scoreCount == 0 ? 0.0d : scoreSum / scoreCount,
                Math.max(0L, Duration.between(startedAt, completedAt).toMillis()),
                inputTokens,
                outputTokens,
                toolCalls);
    }

    private static long metricAsLong(Map<String, Double> metrics, String name) {
        Double value = metrics.get(name);
        return value == null ? 0L : Math.round(value);
    }
}
