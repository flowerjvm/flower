package io.github.flowerjvm.flower.check.parse;

import io.github.flowerjvm.flower.check.source.SourceFile;

/**
 * Turns a {@link SourceFile} into a {@link SourceUnit}.
 *
 * <p>This is an extension point. The default parser wraps JavaParser and
 * delegates to {@link TextFallbackParser} when a file cannot be parsed. The
 * engine depends only on this interface, so parser choice does not affect rule
 * discovery or reporting.
 */
public interface Parser {

    /**
     * Parse one file. Implementations must not throw on malformed input - they
     * return a {@link SourceUnit} with {@code parsed() == false} instead, so one
     * bad file never aborts the whole run.
     */
    SourceUnit parse(SourceFile file);
}
