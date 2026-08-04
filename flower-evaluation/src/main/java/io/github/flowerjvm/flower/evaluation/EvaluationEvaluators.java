package io.github.flowerjvm.flower.evaluation;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Factory methods for common deterministic evaluators. */
public final class EvaluationEvaluators {

    private EvaluationEvaluators() {
    }

    public static Evaluator requiredActualField(final String path) {
        final String selectedPath = EvaluationValues.requireText("path", path);
        return rule(
                "required-actual:" + selectedPath,
                1.0d,
                new EvaluationRule() {
                    @Override
                    public boolean test(EvaluationContext context) {
                        Object value = EvaluationValues.valueAt(
                                context.output().actual(), selectedPath);
                        return value != null && (!(value instanceof String)
                                || !((String) value).trim().isEmpty());
                    }
                },
                "required actual field is present",
                "required actual field is missing");
    }

    public static Evaluator expectedEquals(final String path) {
        final String selectedPath = EvaluationValues.requireText("path", path);
        return rule(
                "expected-equals:" + selectedPath,
                1.0d,
                new EvaluationRule() {
                    @Override
                    public boolean test(EvaluationContext context) {
                        if (!EvaluationValues.hasPath(
                                context.example().expected(), selectedPath)) {
                            return false;
                        }
                        Object expected = EvaluationValues.valueAt(
                                context.example().expected(), selectedPath);
                        Object actual = EvaluationValues.valueAt(
                                context.output().actual(), selectedPath);
                        return Objects.deepEquals(expected, actual);
                    }
                },
                "actual value matches expected value",
                "actual value differs from expected value");
    }

    public static Evaluator minimumActualCollectionSize(final String path, final int minimum) {
        if (minimum < 0) {
            throw new IllegalArgumentException("minimum must not be negative");
        }
        final String selectedPath = EvaluationValues.requireText("path", path);
        return rule(
                "minimum-size:" + selectedPath,
                1.0d,
                new EvaluationRule() {
                    @Override
                    public boolean test(EvaluationContext context) {
                        Object value = EvaluationValues.valueAt(
                                context.output().actual(), selectedPath);
                        return sizeOf(value) >= minimum;
                    }
                },
                "actual collection satisfies the minimum size",
                "actual collection is smaller than the minimum size");
    }

    public static Evaluator metricAtMost(final String metricName, final double maximum) {
        if (Double.isNaN(maximum) || Double.isInfinite(maximum) || maximum < 0.0d) {
            throw new IllegalArgumentException("maximum must be a finite non-negative number");
        }
        final String selectedMetric = EvaluationValues.requireText("metricName", metricName);
        return rule(
                "metric-at-most:" + selectedMetric,
                1.0d,
                new EvaluationRule() {
                    @Override
                    public boolean test(EvaluationContext context) {
                        Double value = context.output().metrics().get(selectedMetric);
                        return value != null && value <= maximum;
                    }
                },
                "metric is within the configured maximum",
                "metric exceeds the configured maximum or is missing");
    }

    public static Evaluator rule(
            final String id,
            final double threshold,
            final EvaluationRule rule,
            final String passReason,
            final String failReason) {
        final String selectedId = EvaluationValues.requireText("id", id);
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
        if (Double.isNaN(threshold) || Double.isInfinite(threshold)
                || threshold < 0.0d || threshold > 1.0d) {
            throw new IllegalArgumentException("threshold must be between 0 and 1");
        }
        return new Evaluator() {
            @Override
            public String id() {
                return selectedId;
            }

            @Override
            public EvaluationScore evaluate(EvaluationContext context) throws Exception {
                boolean passed = rule.test(context);
                return EvaluationScore.scored(
                        selectedId,
                        passed ? 1.0d : 0.0d,
                        threshold,
                        passed ? passReason : failReason);
            }
        };
    }

    public static Evaluator optional(final Evaluator delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        return new Evaluator() {
            @Override
            public String id() {
                return delegate.id();
            }

            @Override
            public EvaluationScore evaluate(EvaluationContext context) throws Exception {
                return delegate.evaluate(context);
            }

            @Override
            public boolean required() {
                return false;
            }
        };
    }

    private static int sizeOf(Object value) {
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).size();
        }
        if (value instanceof Map<?, ?>) {
            return ((Map<?, ?>) value).size();
        }
        if (value != null && value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return -1;
    }
}
