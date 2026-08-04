package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class FlowerTraceEventJson {

    private static final int MAX_NESTING_DEPTH = 64;

    private FlowerTraceEventJson() {
    }

    static String toJson(FlowerTraceEvent event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", event.schemaVersion());
        document.put("source", event.source());
        document.put("eventId", event.eventId());
        document.put("eventType", event.type().name());
        document.put("traceId", event.traceId());
        document.put("flowRunId", event.flowRunId());
        document.put("stepRunId", event.stepRunId());
        document.put("parentRunId", event.parentRunId());
        document.put("flowType", event.flowId().flowType());
        document.put("flowKey", event.flowId().flowKey());
        document.put("workerName", event.workerName());
        document.put("sequence", event.sequence());
        document.put("occurredAt", event.occurredAt().toString());
        document.put("attributes", event.attributes());
        return new Encoder().encode(document);
    }

    private static final class Encoder {
        private final StringBuilder out = new StringBuilder(512);
        private final IdentityHashMap<Object, Boolean> containers = new IdentityHashMap<>();

        private String encode(Object value) {
            write(value, 0);
            return out.toString();
        }

        private void write(Object value, int depth) {
            if (depth > MAX_NESTING_DEPTH) {
                throw new IllegalArgumentException(
                        "trace attribute nesting exceeds " + MAX_NESTING_DEPTH);
            }
            if (value == null) {
                out.append("null");
            } else if (value instanceof String || value instanceof Character) {
                quote(String.valueOf(value));
            } else if (value instanceof Boolean) {
                out.append(value);
            } else if (value instanceof Number) {
                number((Number) value);
            } else if (value instanceof Enum<?>) {
                quote(((Enum<?>) value).name());
            } else if (value instanceof Instant) {
                quote(value.toString());
            } else if (value instanceof Map<?, ?>) {
                map((Map<?, ?>) value, depth);
            } else if (value instanceof Iterable<?>) {
                iterable((Iterable<?>) value, depth);
            } else if (value.getClass().isArray()) {
                array(value, depth);
            } else {
                throw new IllegalArgumentException(
                        "unsupported trace attribute type: " + value.getClass().getName());
            }
        }

        private void map(Map<?, ?> value, int depth) {
            enter(value);
            try {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : value.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("trace attribute map keys must be strings");
                    }
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    quote((String) entry.getKey());
                    out.append(':');
                    write(entry.getValue(), depth + 1);
                }
                out.append('}');
            } finally {
                exit(value);
            }
        }

        private void iterable(Iterable<?> value, int depth) {
            enter(value);
            try {
                out.append('[');
                boolean first = true;
                for (Object element : value) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    write(element, depth + 1);
                }
                out.append(']');
            } finally {
                exit(value);
            }
        }

        private void array(Object value, int depth) {
            enter(value);
            try {
                out.append('[');
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    if (index > 0) {
                        out.append(',');
                    }
                    write(Array.get(value, index), depth + 1);
                }
                out.append(']');
            } finally {
                exit(value);
            }
        }

        private void number(Number value) {
            if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long
                    || value instanceof BigInteger) {
                out.append(value.toString());
                return;
            }
            if (value instanceof Float || value instanceof Double) {
                double number = value.doubleValue();
                if (Double.isNaN(number) || Double.isInfinite(number)) {
                    throw new IllegalArgumentException("non-finite trace number: " + value);
                }
                out.append(value.toString());
                return;
            }
            if (value instanceof BigDecimal) {
                out.append(((BigDecimal) value).toPlainString());
                return;
            }
            throw new IllegalArgumentException(
                    "unsupported trace number type: " + value.getClass().getName());
        }

        private void enter(Object container) {
            if (containers.put(container, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("cyclic trace attribute value");
            }
        }

        private void exit(Object container) {
            containers.remove(container);
        }

        private void quote(String value) {
            out.append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"':
                        out.append("\\\"");
                        break;
                    case '\\':
                        out.append("\\\\");
                        break;
                    case '\b':
                        out.append("\\b");
                        break;
                    case '\f':
                        out.append("\\f");
                        break;
                    case '\n':
                        out.append("\\n");
                        break;
                    case '\r':
                        out.append("\\r");
                        break;
                    case '\t':
                        out.append("\\t");
                        break;
                    default:
                        if (character < 0x20) {
                            appendUnicodeEscape(character);
                        } else {
                            out.append(character);
                        }
                }
            }
            out.append('"');
        }

        private void appendUnicodeEscape(char character) {
            String hex = Integer.toHexString(character);
            out.append("\\u");
            for (int index = hex.length(); index < 4; index++) {
                out.append('0');
            }
            out.append(hex);
        }
    }
}
