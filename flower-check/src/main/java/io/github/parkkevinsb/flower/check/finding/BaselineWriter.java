package io.github.parkkevinsb.flower.check.finding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the current findings as an adoption baseline.
 */
public final class BaselineWriter {

    public int write(Collection<Finding> findings, Path path) {
        List<String> lines = toLines(findings);
        try {
            Path parent = path.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = lines.isEmpty() ? "" : joinLines(lines) + "\n";
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            return lines.size();
        } catch (IOException e) {
            throw new UncheckedIOException("could not write baseline file: " + path, e);
        }
    }

    private static String joinLines(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines.get(i));
        }
        return out.toString();
    }

    private List<String> toLines(Collection<Finding> findings) {
        List<Finding> sorted = new ArrayList<>(findings);
        Collections.sort(sorted, new Comparator<Finding>() {
            @Override
            public int compare(Finding left, Finding right) {
                int file = normalize(left.file()).compareTo(normalize(right.file()));
                if (file != 0) {
                    return file;
                }
                int line = Integer.compare(left.line(), right.line());
                if (line != 0) {
                    return line;
                }
                return left.ruleId().compareTo(right.ruleId());
            }
        });

        Set<String> unique = new LinkedHashSet<>();
        for (Finding finding : sorted) {
            unique.add(finding.ruleId() + " " + normalize(finding.file()) + ":" + finding.line());
        }
        return new ArrayList<>(unique);
    }

    private static String normalize(String file) {
        return file.replace('\\', '/');
    }
}
