package io.github.parkkevinsb.flower.check.finding;

import java.util.Objects;

/**
 * One inline suppression parsed from a source comment:
 * {@code // flower-check:ignore FLOWER-CHECK-004 reason: <text>}.
 *
 * <p>A reason is mandatory (see {@code docs/01-architecture.md} Suppression).
 * Suppressions are intentionally narrow: same file, same rule, and the same
 * line or the immediately following line.
 */
public final class Suppression {

    private final String file;
    private final int line;
    private final String ruleId;
    private final String reason;

    public Suppression(String file, int line, String ruleId, String reason) {
        this.file = Objects.requireNonNull(file, "file");
        if (line < 1) {
            throw new IllegalArgumentException("suppression line must be positive");
        }
        this.line = line;
        this.ruleId = requiredText(ruleId, "ruleId");
        this.reason = requiredText(reason, "reason");
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
        return file.equals(finding.file())
                && ruleId.equals(finding.ruleId())
                && (line == finding.line() || line + 1 == finding.line());
    }

    private static String requiredText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("suppression " + name + " is required");
        }
        return text;
    }
}
