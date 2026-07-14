package io.github.flowerjvm.flower.check.finding;

import java.util.Objects;

/**
 * One accepted finding recorded in {@code flower-check-baseline.txt}.
 */
public final class BaselineEntry {

    private final String ruleId;
    private final String file;
    private final int line;

    public BaselineEntry(String ruleId, String file, int line) {
        this.ruleId = requiredText(ruleId, "ruleId");
        this.file = normalizeFile(requiredText(file, "file"));
        if (line < 1) {
            throw new IllegalArgumentException("baseline line must be positive");
        }
        this.line = line;
    }

    public String ruleId() {
        return ruleId;
    }

    public String file() {
        return file;
    }

    public int line() {
        return line;
    }

    public boolean matches(Finding finding) {
        return ruleId.equals(finding.ruleId())
                && file.equals(normalizeFile(finding.file()))
                && line == finding.line();
    }

    private static String requiredText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("baseline " + name + " is required");
        }
        return text;
    }

    private static String normalizeFile(String value) {
        return value.trim().replace('\\', '/');
    }
}
