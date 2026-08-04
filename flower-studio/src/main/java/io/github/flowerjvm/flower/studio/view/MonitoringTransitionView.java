package io.github.flowerjvm.flower.studio.view;

/** Frequency of one effective Step outcome and selected target. */
public final class MonitoringTransitionView {

    private final String stepId;
    private final String outcome;
    private final String targetStepId;
    private final int count;
    private final double ratioForStep;

    MonitoringTransitionView(
            String stepId,
            String outcome,
            String targetStepId,
            int count,
            int stepTotal) {
        this.stepId = stepId;
        this.outcome = outcome;
        this.targetStepId = targetStepId;
        this.count = count;
        this.ratioForStep = stepTotal == 0 ? 0.0d : ((double) count) / stepTotal;
    }

    public String getStepId() { return stepId; }
    public String getOutcome() { return outcome; }
    public String getTargetStepId() { return targetStepId; }
    public int getCount() { return count; }
    public double getRatioForStep() { return ratioForStep; }
}
