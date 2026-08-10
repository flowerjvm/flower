package io.github.flowerjvm.flower.check.engine;

import io.github.flowerjvm.flower.check.finding.Finding;
import io.github.flowerjvm.flower.check.rule.Severity;

import java.util.ArrayList;
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

    /** Integrity diagnostics that cannot be suppressed or accepted as baseline debt. */
    public List<Finding> parseDiagnostics() {
        List<Finding> diagnostics = new ArrayList<>();
        for (Finding finding : findings) {
            if (FlowerCheckEngine.PARSE_DIAGNOSTIC_ID.equals(finding.ruleId())) {
                diagnostics.add(finding);
            }
        }
        return Collections.unmodifiableList(diagnostics);
    }

    /** Rule findings eligible for baseline generation; parse diagnostics are excluded. */
    public List<Finding> baselineCandidates() {
        List<Finding> candidates = new ArrayList<>(acceptedFindings);
        for (Finding finding : findings) {
            if (!FlowerCheckEngine.PARSE_DIAGNOSTIC_ID.equals(finding.ruleId())) {
                candidates.add(finding);
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    public boolean hasFailingParseDiagnostic() {
        for (Finding finding : parseDiagnostics()) {
            if (finding.severity() == Severity.ERROR) {
                return true;
            }
        }
        return false;
    }
}
