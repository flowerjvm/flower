package io.github.flowerjvm.flower.studio.view;

/** Aggregate and latest-candidate quality from loaded evaluation experiments. */
public final class MonitoringEvaluationView {

    private final int experimentCount;
    private final int totalCases;
    private final int passed;
    private final int failed;
    private final int errors;
    private final double passRate;
    private final String latestExperimentId;
    private final String latestCandidateId;
    private final String latestCandidateVersion;
    private final double latestPassRate;
    private final int regressedExamples;
    private final int improvedExamples;

    MonitoringEvaluationView(
            int experimentCount,
            int totalCases,
            int passed,
            int failed,
            int errors,
            String latestExperimentId,
            String latestCandidateId,
            String latestCandidateVersion,
            double latestPassRate,
            int regressedExamples,
            int improvedExamples) {
        this.experimentCount = experimentCount;
        this.totalCases = totalCases;
        this.passed = passed;
        this.failed = failed;
        this.errors = errors;
        this.passRate = totalCases == 0 ? 0.0d : ((double) passed) / totalCases;
        this.latestExperimentId = latestExperimentId;
        this.latestCandidateId = latestCandidateId;
        this.latestCandidateVersion = latestCandidateVersion;
        this.latestPassRate = latestPassRate;
        this.regressedExamples = regressedExamples;
        this.improvedExamples = improvedExamples;
    }

    public int getExperimentCount() { return experimentCount; }
    public int getTotalCases() { return totalCases; }
    public int getPassed() { return passed; }
    public int getFailed() { return failed; }
    public int getErrors() { return errors; }
    public double getPassRate() { return passRate; }
    public String getLatestExperimentId() { return latestExperimentId; }
    public String getLatestCandidateId() { return latestCandidateId; }
    public String getLatestCandidateVersion() { return latestCandidateVersion; }
    public double getLatestPassRate() { return latestPassRate; }
    public int getRegressedExamples() { return regressedExamples; }
    public int getImprovedExamples() { return improvedExamples; }
}
