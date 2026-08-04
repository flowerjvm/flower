package io.github.flowerjvm.flower.observability.tracing;

/** Common size-based policies for explicitly opted-in textual content. */
public final class TraceContentPolicies {

    private TraceContentPolicies() {
    }

    public static TraceContentPolicy dropAll() {
        return (event, attributeName, content) -> TraceContentPolicy.Decision.DROP;
    }

    public static TraceContentPolicy artifactAll() {
        return (event, attributeName, content) -> TraceContentPolicy.Decision.ARTIFACT;
    }

    public static TraceContentPolicy inlineUpTo(long maxInlineBytes) {
        requireNonNegative(maxInlineBytes);
        return (event, attributeName, content) -> content.sizeBytes() <= maxInlineBytes
                ? TraceContentPolicy.Decision.INLINE
                : TraceContentPolicy.Decision.DROP;
    }

    public static TraceContentPolicy inlineOrArtifact(long maxInlineBytes) {
        requireNonNegative(maxInlineBytes);
        return (event, attributeName, content) -> content.sizeBytes() <= maxInlineBytes
                ? TraceContentPolicy.Decision.INLINE
                : TraceContentPolicy.Decision.ARTIFACT;
    }

    private static void requireNonNegative(long maxInlineBytes) {
        if (maxInlineBytes < 0) {
            throw new IllegalArgumentException(
                    "maxInlineBytes must not be negative: " + maxInlineBytes);
        }
    }
}
