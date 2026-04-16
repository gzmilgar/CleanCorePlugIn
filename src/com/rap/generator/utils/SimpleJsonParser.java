package com.rap.generator.utils;

import java.util.*;

/**
 * Lightweight JSON parser for SAP release data.
 * No external dependencies required.
 * Handles the specific JSON structure from objectReleaseInfoLatest.json
 */
public class SimpleJsonParser {

    private String json;
    private int pos;

    public SimpleJsonParser(String json) {
        this.json = json;
        this.pos = 0;
    }

    public Map<String, Object> parseObject() {
        skipWhitespace();
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else {
                break;
            }
        }
        expect('}');
        return map;
    }

    public List<Object> parseArray() {
        skipWhitespace();
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else {
                break;
            }
        }
        expect(']');
        return list;
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        if (c == '"') return parseString();
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') return parseNull();
        if (c == '-' || Character.isDigit(c)) return parseNumber();
        throw new RuntimeException("Unexpected character '" + c + "' at position " + pos);
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '\\') {
                pos++;
                char escaped = json.charAt(pos);
                switch (escaped) {
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
                    default: sb.append(escaped);
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
        String numStr = json.substring(start, pos);
        if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
            return Double.parseDouble(numStr);
        }
        long val = Long.parseLong(numStr);
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }

    private Boolean parseBoolean() {
        if (json.startsWith("true", pos)) { pos += 4; return true; }
        if (json.startsWith("false", pos)) { pos += 5; return false; }
        throw new RuntimeException("Expected boolean at " + pos);
    }

    private Object parseNull() {
        if (json.startsWith("null", pos)) { pos += 4; return null; }
        throw new RuntimeException("Expected null at " + pos);
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= json.length()) throw new RuntimeException("Unexpected end of JSON");
        return json.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) throw new RuntimeException("Expected '" + c + "' but got '" + peek() + "' at " + pos);
        pos++;
    }

    // --- Convenience methods ---

    @SuppressWarnings("unchecked")
    public static String getString(Map<String, Object> obj, String key) {
        Object val = obj.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getArray(Map<String, Object> obj, String key) {
        Object val = obj.get(key);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return Collections.emptyList();
    }
}
