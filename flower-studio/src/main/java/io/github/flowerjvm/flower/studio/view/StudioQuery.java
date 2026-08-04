package io.github.flowerjvm.flower.studio.view;

/** Filters for the local trace-list API. */
public final class StudioQuery {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;

    private final String text;
    private final String source;
    private final TraceStatus status;
    private final int limit;

    public StudioQuery(String text, String source, TraceStatus status, int limit) {
        this.text = clean(text);
        this.source = clean(source);
        this.status = status;
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        this.limit = limit;
    }

    public static StudioQuery defaults() {
        return new StudioQuery(null, null, null, DEFAULT_LIMIT);
    }

    public String text() {
        return text;
    }

    public String source() {
        return source;
    }

    public TraceStatus status() {
        return status;
    }

    public int limit() {
        return limit;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String selected = value.trim();
        return selected.isEmpty() ? null : selected;
    }
}
