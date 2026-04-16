package com.rap.generator.generators;

public class AbapCodeFormatter {

    private final StringBuilder sb = new StringBuilder();
    private int indentLevel = 0;
    private static final String INDENT = "  ";

    public AbapCodeFormatter indent() {
        indentLevel++;
        return this;
    }

    public AbapCodeFormatter dedent() {
        if (indentLevel > 0) indentLevel--;
        return this;
    }

    public AbapCodeFormatter line(String text) {
        for (int i = 0; i < indentLevel; i++) {
            sb.append(INDENT);
        }
        sb.append(text).append("\n");
        return this;
    }

    public AbapCodeFormatter line() {
        sb.append("\n");
        return this;
    }

    public AbapCodeFormatter annotation(String annotation) {
        return line(annotation);
    }

    public AbapCodeFormatter comment(String comment) {
        return line("// " + comment);
    }

    public AbapCodeFormatter blockComment(String comment) {
        line("/*");
        for (String l : comment.split("\n")) {
            line(" * " + l);
        }
        line(" */");
        return this;
    }

    public AbapCodeFormatter append(String text) {
        sb.append(text);
        return this;
    }

    /**
     * Aligns fields/columns at a given character position.
     * E.g., "key travel_id" aligned with "    agency_id"
     */
    public static String padRight(String text, int width) {
        if (text.length() >= width) return text;
        return text + " ".repeat(width - text.length());
    }

    /**
     * Generates a separator line for readability
     */
    public AbapCodeFormatter separator() {
        return line();
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public void reset() {
        sb.setLength(0);
        indentLevel = 0;
    }

    public int length() {
        return sb.length();
    }
}
