package io.github.flowerjvm.flower.check.source;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single Java source file loaded for analysis.
 *
 * <p>Holds both the raw content and a line view so text-based rules and the
 * line resolver do not re-split repeatedly. {@code relativePath} is what
 * findings report; {@code absolutePath} is for IO only.
 */
public final class SourceFile {

    private final String relativePath;
    private final String absolutePath;
    private final String content;
    private final List<String> lines;

    public SourceFile(String relativePath, String absolutePath, String content, List<String> lines) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.absolutePath = Objects.requireNonNull(absolutePath, "absolutePath");
        this.content = Objects.requireNonNull(content, "content");
        this.lines = Collections.unmodifiableList(Objects.requireNonNull(lines, "lines"));
    }

    public String relativePath() {
        return relativePath;
    }

    public String absolutePath() {
        return absolutePath;
    }

    public String content() {
        return content;
    }

    /** 0-based list; line N in a finding is {@code lines.get(N - 1)}. */
    public List<String> lines() {
        return lines;
    }

    @Override
    public String toString() {
        return relativePath;
    }
}
