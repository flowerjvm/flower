package io.github.flowerjvm.flower.check.rule;

/**
 * Finding severity. Ordered from least to most serious so thresholds can be
 * compared with {@link #atLeast(Severity)}.
 *
 * <pre>
 * INFO     advisory (e.g. parse-fallback notes); never fails a build by itself
 * WARNING  reported; does not fail the build by default
 * ERROR    fails the build by default
 * </pre>
 */
public enum Severity {

    INFO,
    WARNING,
    ERROR;

    /** True when this severity is at least as serious as {@code other}. */
    public boolean atLeast(Severity other) {
        return this.ordinal() >= other.ordinal();
    }
}
