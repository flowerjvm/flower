package io.github.parkkevinsb.flower.check.engine;

import io.github.parkkevinsb.flower.check.finding.Finding;
import io.github.parkkevinsb.flower.check.rule.Severity;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of one check run: the findings and whether they cross the configured
 * fail threshold. The CLI maps {@link #failed()} to a non-zero exit code.
 */
public final class CheckResult {

    private final List<Finding> findings;
    private final List<Finding> acceptedFindings;
    private final Severity worstSeverity;
    private final boolean failed;

    public CheckResult(List<Finding> findings, Severity worstSeverity, boolean failed) {
        this(findings, Collections.<Finding>emptyList(), worstSeverity, failed);
    }

    public CheckResult(List<Finding> findings,
                       List<Finding> acceptedFindings,
                       Severity worstSeverity,
                       boolean failed) {
        this.findings = Collections.unmodifiableList(findings);
        this.acceptedFindings = Collections.unmodifiableList(acceptedFindings);
        this.worstSeverity = worstSeverity;
        this.failed = failed;
    }

    public List<Finding> findings() {
        return findings;
    }

    /** Findings accepted by the baseline. They are reported but do not fail the run. */
    public List<Finding> acceptedFindings() {
        return acceptedFindings;
    }

    /** Highest severity seen, or null when there were no findings. */
    public Severity worstSeverity() {
        return worstSeverity;
    }

    /** True when at least one finding is at or above the configured failOn level. */
    public boolean failed() {
        return failed;
    }
}
