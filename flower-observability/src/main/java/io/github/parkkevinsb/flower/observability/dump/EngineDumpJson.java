package io.github.parkkevinsb.flower.observability.dump;

import io.github.parkkevinsb.flower.core.engine.EngineDump;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;

/**
 * Renders an {@link EngineDump} as a single-line or pretty-printed JSON string.
 *
 * <p>Hand-rolled to avoid pulling Jackson / Gson into observability. The
 * output schema is intentionally minimal:
 *
 * <pre>
 * {
 *   "engineState": "RUNNING",
 *   "workers": [
 *     {
 *       "name": "main",
 *       "state": "RUNNING",
 *       "intervalMillis": 100,
 *       "flows": [
 *         {
 *           "flowType": "quay-work",
 *           "flowKey": "WO-1",
 *           "state": "RUNNING",
 *           "currentStepId": "execute-sts",
 *           "currentStepNo": 10,
 *           "failureCause": null
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Intended for ops/admin endpoints. Not for high-frequency emission.
 */
public final class EngineDumpJson {

    private EngineDumpJson() {
    }

    /** Compact, single-line JSON. */
    public static String toJson(EngineDump dump) {
        return write(dump, false);
    }

    /** Indented JSON (2-space). */
    public static String toPrettyJson(EngineDump dump) {
        return write(dump, true);
    }

    private static String write(EngineDump dump, boolean pretty) {
        if (dump == null) {
            throw new IllegalArgumentException("dump must not be null");
        }
        StringBuilder sb = new StringBuilder(256);
        Writer w = new Writer(sb, pretty);
        w.beginObject();
        w.field("engineState", dump.engineState() == null ? null : dump.engineState().name());
        w.comma();
        w.key("workers");
        w.beginArray();
        boolean firstWorker = true;
        for (EngineDump.WorkerDump worker : dump.workers()) {
            if (!firstWorker) w.comma();
            firstWorker = false;
            writeWorker(w, worker);
        }
        w.endArray();
        w.endObject();
        return sb.toString();
    }

    private static void writeWorker(Writer w, EngineDump.WorkerDump worker) {
        w.beginObject();
        w.field("name", worker.name());
        w.comma();
        w.field("state", worker.state() == null ? null : worker.state().name());
        w.comma();
        w.numberField("intervalMillis", worker.intervalMillis());
        w.comma();
        w.key("flows");
        w.beginArray();
        boolean firstFlow = true;
        for (FlowSnapshot flow : worker.flows()) {
            if (!firstFlow) w.comma();
            firstFlow = false;
            writeFlow(w, flow);
        }
        w.endArray();
        w.endObject();
    }

    private static void writeFlow(Writer w, FlowSnapshot flow) {
        w.beginObject();
        w.field("flowType", flow.flowId().flowType());
        w.comma();
        w.field("flowKey", flow.flowId().flowKey());
        w.comma();
        w.field("state", flow.state() == null ? null : flow.state().name());
        w.comma();
        w.field("currentStepId", flow.currentStepId());
        w.comma();
        w.numberField("currentStepNo", flow.currentStepNo());
        w.comma();
        w.field("failureCause", flow.failureCause() == null ? null : flow.failureCause().toString());
        w.endObject();
    }

    /**
     * Minimal JSON writer. Handles the few primitives we need and applies
     * proper escaping. Keeps the dependency surface at zero.
     */
    private static final class Writer {
        private final StringBuilder out;
        private final boolean pretty;
        private int depth;

        Writer(StringBuilder out, boolean pretty) {
            this.out = out;
            this.pretty = pretty;
        }

        void beginObject() {
            out.append('{');
            depth++;
            newline();
        }

        void endObject() {
            depth--;
            newline();
            out.append('}');
        }

        void beginArray() {
            out.append('[');
            depth++;
            newline();
        }

        void endArray() {
            depth--;
            newline();
            out.append(']');
        }

        void comma() {
            out.append(',');
            newline();
        }

        void key(String name) {
            quote(name);
            out.append(pretty ? ": " : ":");
        }

        void field(String name, String value) {
            key(name);
            if (value == null) {
                out.append("null");
            } else {
                quote(value);
            }
        }

        void numberField(String name, long value) {
            key(name);
            out.append(value);
        }

        private void newline() {
            if (!pretty) return;
            out.append('\n');
            for (int i = 0; i < depth; i++) {
                out.append("  ");
            }
        }

        private void quote(String s) {
            out.append('"');
            for (int i = 0, n = s.length(); i < n; i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':  out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\b': out.append("\\b");  break;
                    case '\f': out.append("\\f");  break;
                    case '\n': out.append("\\n");  break;
                    case '\r': out.append("\\r");  break;
                    case '\t': out.append("\\t");  break;
                    default:
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                }
            }
            out.append('"');
        }
    }
}
