package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Common, exact-name trace attribute sanitizers. */
public final class TraceSanitizers {

    private TraceSanitizers() {
    }

    public static TraceSanitizer removeAttributes(String... attributeNames) {
        Set<String> names = names(attributeNames);
        return event -> filter(event, names, null, FilterMode.REMOVE);
    }

    public static TraceSanitizer redactAttributes(
            String replacement,
            String... attributeNames) {
        if (replacement == null) {
            throw new IllegalArgumentException("replacement must not be null");
        }
        Set<String> names = names(attributeNames);
        return event -> filter(event, names, replacement, FilterMode.REDACT);
    }

    public static TraceSanitizer allowAttributes(String... attributeNames) {
        Set<String> names = names(attributeNames);
        return event -> filter(event, names, null, FilterMode.ALLOW);
    }

    public static TraceSanitizer compose(TraceSanitizer... sanitizers) {
        if (sanitizers == null) {
            throw new IllegalArgumentException("sanitizers must not be null");
        }
        List<TraceSanitizer> copy = new ArrayList<>(Arrays.asList(sanitizers));
        for (TraceSanitizer sanitizer : copy) {
            if (sanitizer == null) {
                throw new IllegalArgumentException("sanitizers must not contain null");
            }
        }
        List<TraceSanitizer> chain = Collections.unmodifiableList(copy);
        return event -> {
            FlowerTraceEvent sanitized = event;
            for (TraceSanitizer sanitizer : chain) {
                sanitized = sanitizer.sanitize(sanitized);
                if (sanitized == null) {
                    return null;
                }
            }
            return sanitized;
        };
    }

    private static FlowerTraceEvent filter(
            FlowerTraceEvent event,
            Set<String> names,
            String replacement,
            FilterMode mode) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Object> entry : event.attributes().entrySet()) {
            boolean selected = names.contains(entry.getKey());
            if (mode == FilterMode.REMOVE && selected) {
                changed = true;
                continue;
            }
            if (mode == FilterMode.ALLOW && !selected) {
                changed = true;
                continue;
            }
            if (mode == FilterMode.REDACT && selected) {
                filtered.put(entry.getKey(), replacement);
                changed = changed || !replacement.equals(entry.getValue());
            } else {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return changed ? FlowerTraceEvents.withAttributes(event, filtered) : event;
    }

    private static Set<String> names(String[] attributeNames) {
        if (attributeNames == null) {
            throw new IllegalArgumentException("attributeNames must not be null");
        }
        Set<String> names = new LinkedHashSet<>();
        for (String name : attributeNames) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
            names.add(name.trim());
        }
        return Collections.unmodifiableSet(names);
    }

    private enum FilterMode {
        REMOVE,
        REDACT,
        ALLOW
    }
}
