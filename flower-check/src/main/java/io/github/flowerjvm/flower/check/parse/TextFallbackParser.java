package io.github.flowerjvm.flower.check.parse;

import io.github.flowerjvm.flower.check.source.SourceFile;

/**
 * Skeleton parser: it does not build an AST. It wraps the file so the pipeline
 * runs end to end and text-based rules work today.
 *
 * <p>Replace/augment with a JavaParser-backed implementation that sets a real
 * AST on the {@link SourceUnit}. Keep this class as the genuine fallback the AST
 * parser delegates to when a file fails to parse (see {@code docs/01-architecture.md}
 * Parsing Strategy).
 */
public final class TextFallbackParser implements Parser {

    @Override
    public SourceUnit parse(SourceFile file) {
        // No AST yet. Marked as not-parsed so the engine knows structural rules
        // cannot fully analyze it, while text rules still run from file lines.
        return new SourceUnit(file, null, false);
    }
}
