package io.github.parkkevinsb.flower.check.report;

import io.github.parkkevinsb.flower.check.finding.Finding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Human-readable reporter. Renders every finding with its what/why/fix block in
 * the format defined in {@code docs/01-architecture.md}.
 */
public final class PlainTextReporter implements Reporter {

    @Override
    public void report(List<Finding> findings, Appendable out) {
        report(findings, java.util.Collections.<Finding>emptyList(), out);
    }

    @Override
    public void report(List<Finding> findings, List<Finding> acceptedFindings, Appendable out) {
        try {
            if (findings.isEmpty() && acceptedFindings.isEmpty()) {
                out.append("flower-check: no findings.\n");
                return;
            }
            if (findings.isEmpty()) {
                out.append("flower-check: no new findings.\n\n");
            } else {
                renderFindings(findings, "", out);
                out.append("flower-check: ").append(Integer.toString(findings.size()))
                        .append(findings.size() == 1 ? " finding.\n" : " findings.\n");
            }
            if (!acceptedFindings.isEmpty()) {
                out.append("\naccepted debt from baseline:\n");
                renderFindings(acceptedFindings, "BASELINE  ", out);
                out.append("flower-check: ").append(Integer.toString(acceptedFindings.size()))
                        .append(acceptedFindings.size() == 1
                                ? " accepted baseline finding.\n"
                                : " accepted baseline findings.\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write report", e);
        }
    }

    private static void renderFindings(List<Finding> findings, String prefix, Appendable out) throws IOException {
        for (Finding f : findings) {
            out.append(prefix)
                    .append(f.ruleId())
                    .append("  ").append(f.severity().name())
                    .append("  ").append(f.file())
                    .append(":").append(Integer.toString(f.line()))
                    .append("\n");
            out.append("  what: ").append(f.what()).append("\n");
            out.append("  why : ").append(f.why()).append("\n");
            out.append("  fix : ").append(f.fix()).append("\n");
            out.append("\n");
        }
    }
}
