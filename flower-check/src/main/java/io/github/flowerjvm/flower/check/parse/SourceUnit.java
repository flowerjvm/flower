package io.github.flowerjvm.flower.check.parse;

import io.github.flowerjvm.flower.check.source.SourceFile;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link SourceFile} together with the result of parsing it.
 *
 * <p>The AST is deliberately typed as {@link Object} so no concrete parser type
 * leaks past the {@code parse} package. Text-based rules use {@link #file()}
 * lines directly; structural model builders can unwrap parser-specific ASTs
 * through helpers that live in {@code parse}.
 *
 * <p>When {@link #parsed()} is false the file could not be parsed. Rules must
 * tolerate that (fall back to conservative text checks or skip), and the engine
 * emits a non-suppressible parse diagnostic. The diagnostic is a WARNING by
 * default and an ERROR when strict parsing is enabled.
 */
public final class SourceUnit {

    private final SourceFile file;
    private final Object ast;
    private final boolean parsed;
    private final String parseFailure;

    public SourceUnit(SourceFile file, Object ast, boolean parsed) {
        this(file, ast, parsed, null);
    }

    public SourceUnit(SourceFile file, Object ast, boolean parsed, String parseFailure) {
        this.file = Objects.requireNonNull(file, "file");
        this.ast = ast;
        this.parsed = parsed;
        this.parseFailure = parseFailure;
    }

    public SourceFile file() {
        return file;
    }

    public boolean parsed() {
        return parsed;
    }

    /** The parsed AST, present only when {@link #parsed()} is true. */
    public Optional<Object> ast() {
        return Optional.ofNullable(ast);
    }

    /** Parser-provided failure detail, present only on best-effort fallback. */
    public Optional<String> parseFailure() {
        return Optional.ofNullable(parseFailure);
    }
}
