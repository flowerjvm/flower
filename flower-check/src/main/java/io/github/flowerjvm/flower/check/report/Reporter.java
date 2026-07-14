package io.github.flowerjvm.flower.check.report;

import io.github.flowerjvm.flower.check.finding.Finding;

import java.util.List;

/**
 * Renders findings to an output sink. The CLI currently ships plain text and
 * SARIF implementations behind the same interface.
 */
public interface Reporter {

    void report(List<Finding> findings, Appendable out);

    default void report(List<Finding> findings, List<Finding> acceptedFindings, Appendable out) {
        report(findings, out);
    }
}
