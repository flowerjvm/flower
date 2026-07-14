package io.github.flowerjvm.flower.check.report;

import java.util.Locale;

public enum ReportFormat {

    PLAIN,
    SARIF;

    public static ReportFormat parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if ("TEXT".equals(normalized)) {
            return PLAIN;
        }
        try {
            return ReportFormat.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid report format: " + value + " (use plain|sarif)");
        }
    }
}
