package io.github.parkkevinsb.flower.check.report;

import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;
import io.github.parkkevinsb.flower.check.rule.Rule;
import io.github.parkkevinsb.flower.check.rule.Severity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Human-readable inventory of available rules and their effective config state.
 */
public final class RuleListReporter {

    public void report(List<Rule> allRules, List<Rule> enabledRules, FlowerCheckConfig config, Appendable out) {
        Set<String> enabledIds = ids(enabledRules);
        int enabled = 0;
        int optIn = 0;
        int disabled = 0;

        try {
            out.append("flower-check rules:\n");
            for (Rule rule : allRules) {
                String status = status(rule, enabledIds, config);
                if ("enabled".equals(status)) {
                    enabled++;
                } else if ("opt-in".equals(status)) {
                    optIn++;
                } else if ("disabled".equals(status)) {
                    disabled++;
                }

                Severity severity = config.effectiveSeverity(rule.id(), rule.defaultSeverity());
                out.append(pad(rule.id(), 18))
                        .append(pad(severity.name(), 9))
                        .append(pad(status, 10))
                        .append(rule.title())
                        .append("\n");
            }
            out.append("flower-check: ")
                    .append(Integer.toString(allRules.size()))
                    .append(" rules. enabled=")
                    .append(Integer.toString(enabled))
                    .append(" opt-in=")
                    .append(Integer.toString(optIn))
                    .append(" disabled=")
                    .append(Integer.toString(disabled))
                    .append("\n");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write rule list", e);
        }
    }

    private static Set<String> ids(List<Rule> rules) {
        Set<String> ids = new HashSet<>();
        for (Rule rule : rules) {
            ids.add(rule.id());
        }
        return ids;
    }

    private static String status(Rule rule, Set<String> enabledIds, FlowerCheckConfig config) {
        if (config.isDisabled(rule.id())) {
            return "disabled";
        }
        if (enabledIds.contains(rule.id())) {
            return "enabled";
        }
        return "opt-in";
    }

    private static String pad(String value, int width) {
        StringBuilder padded = new StringBuilder(value);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }
}
