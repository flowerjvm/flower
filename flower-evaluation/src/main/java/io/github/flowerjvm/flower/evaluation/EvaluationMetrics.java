package io.github.flowerjvm.flower.evaluation;

/** Conventional metric names carried by {@link EvaluationOutput}. */
public final class EvaluationMetrics {

    public static final String DURATION_MILLIS = "durationMillis";
    public static final String INPUT_TOKENS = "inputTokens";
    public static final String OUTPUT_TOKENS = "outputTokens";
    public static final String TOOL_CALLS = "toolCalls";
    public static final String MODEL_CALLS = "modelCalls";
    public static final String TURNS = "turns";
    public static final String COST = "cost";

    private EvaluationMetrics() {
    }
}
