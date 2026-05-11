package com.sap.cleancore.analyzer.analyzers;

import java.util.regex.Pattern;

/**
 * Cyclomatic-ish complexity for ABAP source. Counts decision keywords
 * (IF/ELSEIF/CASE WHEN/DO/WHILE/LOOP/TRY-CATCH) line by line. Comments stripped.
 */
public class ComplexityCalculator {

    private static final Pattern[] PATTERNS = new Pattern[] {
            Pattern.compile("\\bIF\\b",       Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bELSEIF\\b",   Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bWHEN\\b",     Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDO\\b",       Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bWHILE\\b",    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bLOOP\\b",     Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCATCH\\b",    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bAND\\b",      Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bOR\\b",       Pattern.CASE_INSENSITIVE)
    };

    public int compute(String source) {
        if (source == null || source.isEmpty()) return 1;
        int complexity = 1;
        for (String rawLine : source.split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;
            for (Pattern p : PATTERNS) {
                java.util.regex.Matcher m = p.matcher(line);
                while (m.find()) complexity++;
            }
        }
        return complexity;
    }

    private String stripComment(String line) {
        // ABAP: full-line comment starts with '*'. Inline starts with '"' (excluding string lits).
        String t = line.trim();
        if (t.startsWith("*")) return "";
        int q = -1;
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') inStr = !inStr;
            else if (c == '"' && !inStr) { q = i; break; }
        }
        return q >= 0 ? line.substring(0, q) : line;
    }
}
