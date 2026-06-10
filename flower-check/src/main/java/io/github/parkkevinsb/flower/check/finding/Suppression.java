package io.github.parkkevinsb.flower.check.finding;

import java.util.Objects;

/**
 * One inline suppression parsed from a source comment:
 * {@code // flower-check:ignore FLOWER-CHECK-004 reason: <text>}.
 *
 * <p>A reason is mandatory (see {@code docs/01-architecture.md} Suppression).
 * Skeleton: a value holder. The comment scanner and the match logic against a
 * {@link Finding} (same file, same rule, same/adjacent line) are TODO.
 */
public final class Suppression {

    private final String file;
    private final int line;
    private final String ruleId;
    private final String reason;

    public Suppression(String file, int line, String ruleId, String reason) {
        this.file = Objects.requireNonNull(file, "file");
        this.line = line;
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    public String ruleId() {
        return ruleId;
    }

    public String reason() {
        return reason;
    }

    /** True when this suppression should silence the given finding. */
    public boolean suppresses(Finding finding) {
        // TODO(codex): match file + ruleId, and line within the suppressed element.
        return file.equals(finding.file())
                && ruleId.equals(finding.ruleId())
                && line == finding.line();
    }
}
