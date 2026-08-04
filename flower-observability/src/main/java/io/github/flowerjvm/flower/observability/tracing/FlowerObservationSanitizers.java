package io.github.flowerjvm.flower.observability.tracing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Common exact-name sanitizers for observation attributes. */
public final class FlowerObservationSanitizers {

    private FlowerObservationSanitizers() {
    }

    public static FlowerObservationSanitizer removeAttributes(String... attributeNames) {
        Set<String> names = names(attributeNames);
        return event -> filter(event, names, null, FilterMode.REMOVE);
    }

    public static FlowerObservationSanitizer redactAttributes(
            String replacement,
            String... attributeNames) {
        if (replacement == null) {
            throw new IllegalArgumentException("replacement must not be null");
        }
        Set<String> names = names(attributeNames);
        return event -> filter(event, names, replacement, FilterMode.REDACT);
    }

    public static FlowerObservationSanitizer allowAttributes(String... attributeNames) {
        Set<String> names = names(attributeNames);
        return event -> filter(event, names, null, FilterMode.ALLOW);
    }

    public static FlowerObservationSanitizer compose(
            FlowerObservationSanitizer... sanitizers) {
        if (sanitizers == null) {
            throw new IllegalArgumentException("sanitizers must not be null");
        }
        List<FlowerObservationSanitizer> copy = new ArrayList<>(Arrays.asList(sanitizers));
        for (FlowerObservationSanitizer sanitizer : copy) {
            if (sanitizer == null) {
                throw new IllegalArgumentException("sanitizers must not contain null");
            }
        }
        List<FlowerObservationSanitizer> chain = Collections.unmodifiableList(copy);
        return event -> {
            FlowerObservationEvent sanitized = event;
            for (FlowerObservationSanitizer sanitizer : chain) {
                sanitized = sanitizer.sanitize(sanitized);
                if (sanitized == null) {
                    return null;
                }
            }
            return sanitized;
        };
    }

    private static FlowerObservationEvent filter(
            FlowerObservationEvent event,
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
        return changed ? event.toBuilder().clearAttributes()
                .attributes(filtered).build() : event;
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
