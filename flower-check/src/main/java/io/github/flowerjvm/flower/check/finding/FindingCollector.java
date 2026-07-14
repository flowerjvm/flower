package io.github.flowerjvm.flower.check.finding;

import io.github.flowerjvm.flower.check.rule.Severity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Accumulates findings from a run and answers the questions the engine needs:
 * the sorted finding list and the worst severity seen.
 *
 * <p>Suppression and baseline filtering hook in here so the engine stays simple.
 */
public final class FindingCollector {

    private final List<Finding> findings = new ArrayList<>();

    public void add(Finding finding) {
        findings.add(finding);
    }

    public void addAll(Collection<Finding> more) {
        findings.addAll(more);
    }

    public boolean isEmpty() {
        return findings.isEmpty();
    }

    public int size() {
        return findings.size();
    }

    public void suppressAll(Collection<Suppression> suppressions) {
        if (suppressions.isEmpty() || findings.isEmpty()) {
            return;
        }
        List<Finding> kept = new ArrayList<>();
        for (Finding finding : findings) {
            if (!isSuppressed(finding, suppressions)) {
                kept.add(finding);
            }
        }
        findings.clear();
        findings.addAll(kept);
    }

    public List<Finding> acceptBaseline(Collection<BaselineEntry> baselineEntries) {
        List<Finding> accepted = new ArrayList<>();
        if (baselineEntries.isEmpty() || findings.isEmpty()) {
            return accepted;
        }
        List<Finding> kept = new ArrayList<>();
        for (Finding finding : findings) {
            if (isBaselined(finding, baselineEntries)) {
                accepted.add(finding);
            } else {
                kept.add(finding);
            }
        }
        findings.clear();
        findings.addAll(kept);
        return sorted(accepted);
    }

    /** Findings sorted by file, then line, then rule id — stable output order. */
    public List<Finding> findings() {
        return sorted(findings);
    }

    /** Highest severity among collected findings, or null when empty. */
    public Severity worstSeverity() {
        Severity worst = null;
        for (Finding f : findings) {
            if (worst == null || f.severity().atLeast(worst)) {
                worst = f.severity();
            }
        }
        return worst;
    }

    private static boolean isSuppressed(Finding finding, Collection<Suppression> suppressions) {
        for (Suppression suppression : suppressions) {
            if (suppression.suppresses(finding)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBaselined(Finding finding, Collection<BaselineEntry> baselineEntries) {
        for (BaselineEntry entry : baselineEntries) {
            if (entry.matches(finding)) {
                return true;
            }
        }
        return false;
    }

    private static List<Finding> sorted(Collection<Finding> source) {
        List<Finding> sorted = new ArrayList<>(source);
        sorted.sort(Comparator
                .comparing(Finding::file)
                .thenComparingInt(Finding::line)
                .thenComparing(Finding::ruleId));
        return sorted;
    }
}
