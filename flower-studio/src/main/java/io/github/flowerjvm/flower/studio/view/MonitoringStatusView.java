package io.github.flowerjvm.flower.studio.view;

/** Count and ratio for one projected Trace status. */
public final class MonitoringStatusView {

    private final TraceStatus status;
    private final int count;
    private final double ratio;

    MonitoringStatusView(TraceStatus status, int count, int total) {
        this.status = status;
        this.count = count;
        this.ratio = total == 0 ? 0.0d : ((double) count) / total;
    }

    public TraceStatus getStatus() { return status; }
    public int getCount() { return count; }
    public double getRatio() { return ratio; }
}
