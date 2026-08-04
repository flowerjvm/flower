package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies an explicit content capture policy to top-level {@link TraceContent}
 * attributes.
 *
 * <p>Policy or artifact storage failures drop the entire event, preventing raw
 * content from reaching the delegate. Artifact storage may block, so this sink
 * belongs behind {@link AsyncFlowerTraceSink}.
 */
public final class ContentCaptureFlowerTraceSink implements FlowerTraceSink {

    private final FlowerTraceSink delegate;
    private final TraceContentPolicy policy;
    private final TraceArtifactStore artifactStore;
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong droppedContent = new AtomicLong();
    private final AtomicLong inlineContent = new AtomicLong();
    private final AtomicLong artifactContent = new AtomicLong();

    public ContentCaptureFlowerTraceSink(
            FlowerTraceSink delegate,
            TraceContentPolicy policy) {
        this(delegate, policy, null);
    }

    public ContentCaptureFlowerTraceSink(
            FlowerTraceSink delegate,
            TraceContentPolicy policy,
            TraceArtifactStore artifactStore) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.delegate = delegate;
        this.policy = policy;
        this.artifactStore = artifactStore;
    }

    @Override
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        FlowerTraceEvent captured;
        try {
            captured = capture(event);
        } catch (RuntimeException failure) {
            failed.incrementAndGet();
            return;
        }
        delegate.publish(captured);
        published.incrementAndGet();
    }

    public long publishedCount() {
        return published.get();
    }

    public long failedCount() {
        return failed.get();
    }

    public long droppedContentCount() {
        return droppedContent.get();
    }

    public long inlineContentCount() {
        return inlineContent.get();
    }

    public long artifactContentCount() {
        return artifactContent.get();
    }

    private FlowerTraceEvent capture(FlowerTraceEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Object> entry : event.attributes().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof TraceContent)) {
                attributes.put(entry.getKey(), value);
                continue;
            }
            changed = true;
            TraceContent content = (TraceContent) value;
            TraceContentPolicy.Decision decision = policy.decide(
                    event,
                    entry.getKey(),
                    content);
            if (decision == null) {
                throw new IllegalStateException("content policy returned null");
            }
            switch (decision) {
                case DROP:
                    droppedContent.incrementAndGet();
                    break;
                case INLINE:
                    attributes.put(entry.getKey(), inline(content));
                    inlineContent.incrementAndGet();
                    break;
                case ARTIFACT:
                    if (artifactStore == null) {
                        throw new IllegalStateException(
                                "content policy selected ARTIFACT without an artifact store");
                    }
                    TraceArtifactReference reference = artifactStore.store(
                            event,
                            entry.getKey(),
                            content);
                    if (reference == null) {
                        throw new IllegalStateException("artifact store returned null");
                    }
                    attributes.put(entry.getKey(), reference.asAttributeValue());
                    artifactContent.incrementAndGet();
                    break;
                default:
                    throw new IllegalStateException("unsupported content decision: " + decision);
            }
        }
        return changed ? FlowerTraceEvents.withAttributes(event, attributes) : event;
    }

    private static Map<String, Object> inline(TraceContent content) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("capture", TraceContentPolicy.Decision.INLINE.name());
        value.put("mediaType", content.mediaType());
        value.put("sizeBytes", content.sizeBytes());
        value.put("text", content.text());
        return Collections.unmodifiableMap(value);
    }
}
