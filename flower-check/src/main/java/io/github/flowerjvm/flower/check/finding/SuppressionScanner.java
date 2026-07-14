package io.github.flowerjvm.flower.check.finding;

import io.github.flowerjvm.flower.check.source.SourceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses inline {@code // flower-check:ignore} comments from a loaded source file.
 *
 * <p>The grammar is deliberately tiny and review-friendly:
 * {@code flower-check:ignore FLOWER-CHECK-004 reason: <text>}.
 */
public final class SuppressionScanner {

    private static final Pattern VALID = Pattern.compile(
            "flower-check:ignore\\s+(FLOWER-CHECK-\\d{3})\\s+reason:\\s*(\\S.*)$");
    private static final Pattern PREFIX = Pattern.compile("flower-check:ignore\\b");

    public List<Suppression> scan(SourceFile file) {
        List<Suppression> suppressions = new ArrayList<>();
        List<String> lines = file.lines();
        for (int i = 0; i < lines.size(); i++) {
            String line = lineCommentPayload(lines.get(i));
            if (line == null) {
                continue;
            }
            line = line.trim();
            if (!PREFIX.matcher(line).find()) {
                continue;
            }
            Matcher matcher = VALID.matcher(line);
            if (!matcher.find()) {
                throw new IllegalArgumentException(file.relativePath() + ":" + (i + 1)
                        + ": invalid flower-check suppression; use "
                        + "flower-check:ignore FLOWER-CHECK-000 reason: <text>");
            }
            suppressions.add(new Suppression(
                    file.relativePath(),
                    i + 1,
                    matcher.group(1),
                    matcher.group(2)));
        }
        return suppressions;
    }

    private static String lineCommentPayload(String line) {
        int slash = line.indexOf("//");
        if (slash < 0) {
            return null;
        }
        String before = line.substring(0, slash).trim();
        if (before.startsWith("*") || before.startsWith("/*")) {
            return null;
        }
        return line.substring(slash + 2);
    }
}
