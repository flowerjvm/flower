package io.github.flowerjvm.flower.check.finding;

import io.github.flowerjvm.flower.check.rule.Severity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads accepted findings from a baseline file.
 *
 * <p>Accepted line shapes:
 *
 * <pre>
 * FLOWER-CHECK-001 src/main/java/WaitStep.java:42
 * FLOWER-CHECK-001 ERROR src/main/java/WaitStep.java:42
 * </pre>
 */
public final class BaselineLoader {

    public List<BaselineEntry> load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("baseline file does not exist: " + path);
        }
        try {
            return parse(path, Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read baseline file: " + path, e);
        }
    }

    private List<BaselineEntry> parse(Path path, List<String> lines) {
        List<BaselineEntry> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = stripComment(lines.get(i)).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                line = line.substring(1).trim();
            }
            entries.add(parseEntry(path, i + 1, line));
        }
        return entries;
    }

    private BaselineEntry parseEntry(Path path, int lineNo, String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            throw error(path, lineNo, "expected FLOWER-CHECK-000 file:line");
        }

        String ruleId = parts[0];
        String location;
        if (parts.length >= 3 && isSeverity(parts[1])) {
            location = parts[2];
        } else {
            location = parts[1];
        }
        int split = location.lastIndexOf(':');
        if (split <= 0 || split == location.length() - 1) {
            throw error(path, lineNo, "expected file:line location");
        }
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(location.substring(split + 1));
        } catch (NumberFormatException e) {
            throw error(path, lineNo, "invalid baseline line number: " + location.substring(split + 1));
        }
        return new BaselineEntry(ruleId, location.substring(0, split), lineNumber);
    }

    private boolean isSeverity(String value) {
        try {
            Severity.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private IllegalArgumentException error(Path path, int lineNo, String message) {
        return new IllegalArgumentException(path + ":" + lineNo + ": " + message);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }
}
