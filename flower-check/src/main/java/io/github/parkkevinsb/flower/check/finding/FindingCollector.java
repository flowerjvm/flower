package io.github.parkkevinsb.flower.check.finding;

import io.github.parkkevinsb.flower.check.rule.Severity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Accumulates findings from a run and answers the questions the engine needs:
 * the sorted finding list and the worst severity seen.
 *
 * <p>Suppression filtering and baseline handling hook in here (TODO), so the
 * engine stays simple.
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

    /** Findings sorted by file, then line, then rule id — stable output order. */
    public List<Finding> findings() {
        List<Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator
                .comparing(Finding::file)
                .thenComparingInt(Finding::line)
                .thenComparing(Finding::ruleId));
        return sorted;
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
}
