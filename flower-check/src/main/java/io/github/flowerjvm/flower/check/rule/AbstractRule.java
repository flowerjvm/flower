package io.github.flowerjvm.flower.check.rule;

import io.github.flowerjvm.flower.check.finding.Finding;

import java.util.Objects;

/**
 * Convenience base for rules: stores id/severity/title and provides a
 * {@link #finding} helper that pre-fills the rule id and the effective severity
 * (honoring any config override). Subclasses implement
 * {@link Rule#apply(io.github.flowerjvm.flower.check.parse.SourceUnit, RuleContext)}.
 */
public abstract class AbstractRule implements Rule {

    private final String id;
    private final Severity defaultSeverity;
    private final String title;

    protected AbstractRule(String id, Severity defaultSeverity, String title) {
        this.id = Objects.requireNonNull(id, "id");
        this.defaultSeverity = Objects.requireNonNull(defaultSeverity, "defaultSeverity");
        this.title = Objects.requireNonNull(title, "title");
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Severity defaultSeverity() {
        return defaultSeverity;
    }

    @Override
    public final String title() {
        return title;
    }

    /**
     * Start a finding for this rule at the given location, with severity resolved
     * through config. Callers fill {@code what/why/fix} and {@code build()}.
     */
    protected Finding.Builder finding(RuleContext ctx, String file, int line) {
        Severity effective = ctx.config().effectiveSeverity(id, defaultSeverity);
        return Finding.builder()
                .ruleId(id)
                .severity(effective)
                .file(file)
                .line(line);
    }
}
