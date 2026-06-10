package io.github.parkkevinsb.flower.check.report;

import io.github.parkkevinsb.flower.check.finding.Finding;

import java.util.List;

/**
 * Renders findings to an output sink. An extension point: the skeleton ships
 * {@link PlainTextReporter}; a SARIF reporter (for CI annotations) plugs in
 * behind the same interface later.
 */
public interface Reporter {

    void report(List<Finding> findings, Appendable out);

    default void report(List<Finding> findings, List<Finding> acceptedFindings, Appendable out) {
        report(findings, out);
    }
}
