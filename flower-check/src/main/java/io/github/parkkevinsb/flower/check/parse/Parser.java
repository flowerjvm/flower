package io.github.parkkevinsb.flower.check.parse;

import io.github.parkkevinsb.flower.check.source.SourceFile;

/**
 * Turns a {@link SourceFile} into a {@link SourceUnit}.
 *
 * <p>This is an extension point. The skeleton ships {@link TextFallbackParser};
 * the production parser wraps JavaParser and produces a real AST behind the same
 * interface. The engine depends only on this interface, so swapping in the AST
 * parser changes one wiring line and nothing in the rules.
 */
public interface Parser {

    /**
     * Parse one file. Implementations must not throw on malformed input — they
     * return a {@link SourceUnit} with {@code parsed() == false} instead, so one
     * bad file never aborts the whole run.
     */
    SourceUnit parse(SourceFile file);
}
