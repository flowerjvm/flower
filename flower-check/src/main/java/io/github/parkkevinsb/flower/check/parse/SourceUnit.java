package io.github.parkkevinsb.flower.check.parse;

import io.github.parkkevinsb.flower.check.source.SourceFile;

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
 * emits at most an INFO note - a parse failure alone never fails the build.
 */
public final class SourceUnit {

    private final SourceFile file;
    private final Object ast;
    private final boolean parsed;

    public SourceUnit(SourceFile file, Object ast, boolean parsed) {
        this.file = Objects.requireNonNull(file, "file");
        this.ast = ast;
        this.parsed = parsed;
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
}
