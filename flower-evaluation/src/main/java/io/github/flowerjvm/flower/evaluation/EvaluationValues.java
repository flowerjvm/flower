package io.github.flowerjvm.flower.evaluation;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EvaluationValues {

    private EvaluationValues() {
    }

    static String requireText(String name, String value) {
        String selected = cleanText(value);
        if (selected == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return selected;
    }

    static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String selected = value.trim();
        return selected.isEmpty() ? null : selected;
    }

    static Map<String, Object> immutableMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = requireText("map key", entry.getKey());
            copy.put(key, immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    static Map<String, Double> immutableMetrics(Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> copy = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = requireText("metric name", entry.getKey());
            Double value = entry.getValue();
            if (value == null || value.isNaN() || value.isInfinite() || value < 0.0d) {
                throw new IllegalArgumentException("metric must be a finite non-negative number: " + key);
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    static Set<String> immutableTextSet(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> copy = new LinkedHashSet<String>();
        for (String value : source) {
            copy.add(requireText("set value", value));
        }
        return Collections.unmodifiableSet(copy);
    }

    static Object valueAt(Map<String, Object> values, String path) {
        String selectedPath = requireText("path", path);
        Object current = values;
        for (String part : selectedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?>)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    static boolean hasPath(Map<String, Object> values, String path) {
        String selectedPath = requireText("path", path);
        Object current = values;
        for (String part : selectedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?>)
                    || !((Map<?, ?>) current).containsKey(part)) {
                return false;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return true;
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> nested = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("structured map keys must be strings");
                }
                nested.put((String) entry.getKey(), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof Collection<?>) {
            List<Object> nested = new ArrayList<Object>();
            for (Object element : (Collection<?>) value) {
                nested.add(immutableValue(element));
            }
            return Collections.unmodifiableList(nested);
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> nested = new ArrayList<Object>();
            for (int index = 0; index < Array.getLength(value); index++) {
                nested.add(immutableValue(Array.get(value, index)));
            }
            return Collections.unmodifiableList(nested);
        }
        return value;
    }
}
