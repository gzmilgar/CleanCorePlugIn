package com.sap.cleancore.analyzer.utils;

import java.util.List;
import java.util.Map;

/**
 * Dependency-free JSON serializer. Produces indented output for the
 * mapping/object_rules files we save back to disk.
 */
public class JsonWriter {

    private final StringBuilder sb = new StringBuilder();
    private final boolean pretty;

    public JsonWriter(boolean pretty) { this.pretty = pretty; }

    public static String write(Object value) {
        JsonWriter w = new JsonWriter(true);
        w.writeValue(value, 0);
        return w.sb.toString();
    }

    private void writeValue(Object v, int indent) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof Map) { writeMap((Map<?, ?>) v, indent); return; }
        if (v instanceof List) { writeList((List<?>) v, indent); return; }
        if (v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Number) { sb.append(v.toString()); return; }
        sb.append('"').append(escape(v.toString())).append('"');
    }

    private void writeMap(Map<?, ?> m, int indent) {
        if (m.isEmpty()) { sb.append("{}"); return; }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            newLine(indent + 1);
            sb.append('"').append(escape(e.getKey().toString())).append("\":");
            if (pretty) sb.append(' ');
            writeValue(e.getValue(), indent + 1);
        }
        newLine(indent);
        sb.append('}');
    }

    private void writeList(List<?> l, int indent) {
        if (l.isEmpty()) { sb.append("[]"); return; }
        sb.append('[');
        boolean first = true;
        for (Object v : l) {
            if (!first) sb.append(',');
            first = false;
            newLine(indent + 1);
            writeValue(v, indent + 1);
        }
        newLine(indent);
        sb.append(']');
    }

    private void newLine(int indent) {
        if (!pretty) return;
        sb.append('\n');
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
