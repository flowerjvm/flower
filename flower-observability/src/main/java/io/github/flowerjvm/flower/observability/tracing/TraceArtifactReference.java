package io.github.flowerjvm.flower.observability.tracing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable metadata replacing content stored outside the trace event. */
public final class TraceArtifactReference {

    private final String artifactId;
    private final String location;
    private final String mediaType;
    private final long sizeBytes;
    private final String sha256;

    public TraceArtifactReference(
            String artifactId,
            String location,
            String mediaType,
            long sizeBytes,
            String sha256) {
        this.artifactId = requireText("artifactId", artifactId);
        this.location = requireText("location", location);
        this.mediaType = requireText("mediaType", mediaType);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative: " + sizeBytes);
        }
        this.sizeBytes = sizeBytes;
        this.sha256 = requireText("sha256", sha256);
    }

    public String artifactId() {
        return artifactId;
    }

    public String location() {
        return location;
    }

    public String mediaType() {
        return mediaType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256() {
        return sha256;
    }

    /** JSON-safe attribute representation understood without Java type metadata. */
    public Map<String, Object> asAttributeValue() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("capture", TraceContentPolicy.Decision.ARTIFACT.name());
        value.put("artifactId", artifactId);
        value.put("location", location);
        value.put("mediaType", mediaType);
        value.put("sizeBytes", sizeBytes);
        value.put("sha256", sha256);
        return Collections.unmodifiableMap(value);
    }

    private static String requireText(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
