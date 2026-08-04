package io.github.flowerjvm.flower.observability.tracing;

import java.nio.charset.StandardCharsets;

/**
 * Explicit opt-in wrapper for textual trace content such as a model prompt or
 * Tool result.
 *
 * <p>Flower runtime events never create this value. Domain instrumentation
 * must opt in by placing it in a top-level event attribute, after which a
 * {@link TraceContentPolicy} decides whether to drop, inline, or externalize
 * it. {@link #toString()} deliberately never returns the content.
 */
public final class TraceContent {

    public static final String TEXT_PLAIN_UTF8 = "text/plain; charset=UTF-8";

    private final String mediaType;
    private final String text;
    private final long sizeBytes;

    private TraceContent(String mediaType, String text) {
        if (mediaType == null || mediaType.trim().isEmpty()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.mediaType = mediaType.trim();
        this.text = text;
        this.sizeBytes = text.getBytes(StandardCharsets.UTF_8).length;
    }

    public static TraceContent text(String text) {
        return new TraceContent(TEXT_PLAIN_UTF8, text);
    }

    public static TraceContent text(String mediaType, String text) {
        return new TraceContent(mediaType, text);
    }

    public String mediaType() {
        return mediaType;
    }

    public String text() {
        return text;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    byte[] utf8Bytes() {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "TraceContent{" + mediaType + ", sizeBytes=" + sizeBytes + "}";
    }
}
