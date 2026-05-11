package com.sap.cleancore.analyzer.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny dependency-free JSON parser. Handles objects, arrays, strings, numbers,
 * booleans, null. Sufficient for mapping/object_rules JSON and the response
 * shape of api.sap.com endpoints we touch.
 */
public class SimpleJsonParser {

    private final String json;
    private int pos;

    public SimpleJsonParser(String json) {
        this.json = json;
        this.pos = 0;
    }

    public static Object parse(String json) {
        SimpleJsonParser p = new SimpleJsonParser(json);
        p.skipWs();
        return p.parseValue();
    }

    private Object parseValue() {
        skipWs();
        char c = peek();
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') return parseNull();
        if (c == '-' || Character.isDigit(c)) return parseNumber();
        throw new RuntimeException("Unexpected character '" + c + "' at " + pos);
    }

    public Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWs();
            if (peek() == ',') { pos++; } else break;
        }
        expect('}');
        return map;
    }

    public List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWs();
            if (peek() == ',') { pos++; } else break;
        }
        expect(']');
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '\\') {
                pos++;
                char esc = json.charAt(pos);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        String hex = json.substring(pos + 1, pos + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default: sb.append(esc);
                }
            } else if (c == '"') {
                pos++;
                return sb.toString();
            } else {
                sb.append(c);
            }
            pos++;
        }
        throw new RuntimeException("Unterminated string");
    }

    private Number parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < json.length() && Character.isDigit(peek())) pos++;
        if (pos < json.length() && peek() == '.') {
            pos++;
            while (pos < json.length() && Character.isDigit(peek())) pos++;
        }
        if (pos < json.length() && (peek() == 'e' || peek() == 'E')) {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            while (pos < json.length() && Character.isDigit(peek())) pos++;
        }
        String s = json.substring(start, pos);
        if (s.contains(".") || s.contains("e") || s.contains("E")) return Double.parseDouble(s);
        long v = Long.parseLong(s);
        if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return (int) v;
        return v;
    }

    private Boolean parseBoolean() {
        if (json.startsWith("true", pos))  { pos += 4; return true;  }
        if (json.startsWith("false", pos)) { pos += 5; return false; }
        throw new RuntimeException("Expected boolean at " + pos);
    }

    private Object parseNull() {
        if (json.startsWith("null", pos)) { pos += 4; return null; }
        throw new RuntimeException("Expected null at " + pos);
    }

    private void skipWs() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= json.length()) throw new RuntimeException("Unexpected end of JSON");
        return json.charAt(pos);
    }

    private void expect(char c) {
        skipWs();
        if (peek() != c) throw new RuntimeException("Expected '" + c + "' but got '" + peek() + "' at " + pos);
        pos++;
    }

    // ---- Convenience accessors ----

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : v.toString();
    }

    public static double dbl(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) try { return Double.parseDouble((String) v); } catch (Exception e) { return def; }
        return def;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> arr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<Object>) v) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
            return out;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }
}
