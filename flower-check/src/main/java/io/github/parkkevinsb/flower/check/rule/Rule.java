package io.github.parkkevinsb.flower.check.rule;

import io.github.parkkevinsb.flower.check.finding.Finding;
import io.github.parkkevinsb.flower.check.parse.SourceUnit;

import java.util.List;

/**
 * A single, self-contained check. The extension point of flower-check.
 *
 * <p>Rules are pure functions: they receive a parsed {@link SourceUnit} and the
 * shared, read-only {@link RuleContext}, and return findings. A rule must not do
 * IO, parse, or call {@code System.exit}. This keeps each rule independently
 * testable and lets the engine run, order, and report them uniformly.
 *
 * <p>Rules are discovered by {@link java.util.ServiceLoader}. To add one,
 * implement this interface (usually via {@link AbstractRule}) and register it in
 * {@code META-INF/services/io.github.parkkevinsb.flower.check.rule.Rule}. Do not
 * hard-wire rules into the engine.
 */
public interface Rule {

    /** Stable id, e.g. {@code "FLOWER-CHECK-001"}. */
    String id();

    /** Severity used unless the user overrides it in config. */
    Severity defaultSeverity();

    /** One-line human summary, shown in {@code --list-rules} style output. */
    String title();

    /** Inspect one source unit against the shared context; return findings. */
    List<Finding> apply(SourceUnit unit, RuleContext context);
}
