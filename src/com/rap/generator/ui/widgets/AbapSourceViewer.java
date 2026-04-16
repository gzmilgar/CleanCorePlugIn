package com.rap.generator.ui.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbapSourceViewer {

    private Composite container;
    private StyledText styledText;

    // ABAP keywords (case-insensitive in CDS, case-sensitive in ABAP OO)
    private static final Set<String> CDS_KEYWORDS = new HashSet<>(Arrays.asList(
        "define", "root", "view", "entity", "as", "select", "from",
        "association", "composition", "to", "parent", "on", "key",
        "where", "group", "by", "having", "union", "all", "distinct",
        "left", "right", "inner", "outer", "join", "cross",
        "case", "when", "then", "else", "end",
        "cast", "true", "false", "null", "not",
        "and", "or", "between", "like", "in", "exists", "is",
        "managed", "implementation", "class", "unique", "strict",
        "with", "draft", "persistent", "table", "lock", "master",
        "dependent", "etag", "authorization", "global",
        "create", "update", "delete", "action", "validation",
        "determination", "field", "readonly", "mandatory",
        "result", "features", "instance", "static",
        "provider", "contract", "transactional_query",
        "redirected", "child", "expose", "service",
        "annotate", "total", "optimized"
    ));

    private static final Set<String> ABAP_KEYWORDS = new HashSet<>(Arrays.asList(
        "CLASS", "DEFINITION", "IMPLEMENTATION", "PUBLIC", "PRIVATE", "PROTECTED",
        "FINAL", "ABSTRACT", "CREATE", "INHERITING", "FROM",
        "INTERFACES", "METHODS", "METHOD", "ENDMETHOD", "ENDCLASS",
        "DATA", "TYPES", "CONSTANTS", "FIELD-SYMBOL", "FIELD-SYMBOLS",
        "READ", "MODIFY", "ENTITIES", "ENTITY", "FIELDS",
        "VALUE", "FOR", "IN", "LOCAL", "MODE",
        "WITH", "CORRESPONDING", "RESULT", "FAILED", "REPORTED",
        "IMPORTING", "EXPORTING", "CHANGING", "RETURNING",
        "IF", "ELSE", "ELSEIF", "ENDIF",
        "LOOP", "AT", "ENDLOOP", "INTO", "ASSIGNING",
        "DO", "ENDDO", "WHILE", "ENDWHILE",
        "TRY", "CATCH", "ENDTRY", "CLEANUP",
        "RAISE", "EXCEPTION", "MESSAGE",
        "SELECT", "FROM", "WHERE", "ORDER", "BY",
        "INSERT", "UPDATE", "DELETE",
        "APPEND", "CLEAR", "REFRESH", "FREE",
        "MOVE", "WRITE", "CONCATENATE",
        "WHEN", "OTHERS", "COND", "SWITCH",
        "SECTION", "ENDMETHOD"
    ));

    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@[A-Za-z][A-Za-z0-9_.]*");
    private static final Pattern STRING_PATTERN = Pattern.compile("'[^']*'");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(//.*$|\".*$|\\*.*$)", Pattern.MULTILINE);
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    public AbapSourceViewer(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        styledText = new StyledText(container, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        styledText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        styledText.setFont(new Font(Display.getDefault(), "Courier New", 11, SWT.NORMAL));
        styledText.setMargins(5, 5, 5, 5);

        // Add syntax highlighting
        styledText.addLineStyleListener(new AbapLineStyleListener());
    }

    public void setSource(String source) {
        styledText.setText(source != null ? source : "");
    }

    public String getSource() {
        return styledText.getText();
    }

    public Control getControl() {
        return container;
    }

    private class AbapLineStyleListener implements LineStyleListener {

        private final Color keywordColor;
        private final Color annotationColor;
        private final Color stringColor;
        private final Color commentColor;

        AbapLineStyleListener() {
            Display display = Display.getDefault();
            keywordColor = new Color(display, 0, 0, 180);       // Blue
            annotationColor = new Color(display, 0, 128, 0);     // Green
            stringColor = new Color(display, 180, 0, 0);         // Red
            commentColor = new Color(display, 128, 128, 128);    // Gray
        }

        @Override
        public void lineGetStyle(LineStyleEvent event) {
            String line = event.lineText;
            List<StyleRange> styles = new ArrayList<>();

            // Check for full-line comment first
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("\"") || trimmed.startsWith("*")) {
                StyleRange commentStyle = new StyleRange();
                commentStyle.start = event.lineOffset;
                commentStyle.length = line.length();
                commentStyle.foreground = commentColor;
                commentStyle.fontStyle = SWT.ITALIC;
                event.styles = new StyleRange[]{commentStyle};
                return;
            }

            // Annotations (@...)
            Matcher annotMatcher = ANNOTATION_PATTERN.matcher(line);
            while (annotMatcher.find()) {
                StyleRange style = new StyleRange();
                style.start = event.lineOffset + annotMatcher.start();
                style.length = annotMatcher.end() - annotMatcher.start();
                style.foreground = annotationColor;
                styles.add(style);
            }

            // Strings ('...')
            Matcher strMatcher = STRING_PATTERN.matcher(line);
            while (strMatcher.find()) {
                StyleRange style = new StyleRange();
                style.start = event.lineOffset + strMatcher.start();
                style.length = strMatcher.end() - strMatcher.start();
                style.foreground = stringColor;
                styles.add(style);
            }

            // Keywords
            Matcher wordMatcher = WORD_PATTERN.matcher(line);
            while (wordMatcher.find()) {
                String word = wordMatcher.group();
                if (CDS_KEYWORDS.contains(word.toLowerCase()) || ABAP_KEYWORDS.contains(word)) {
                    // Check it's not inside a string or annotation
                    int start = wordMatcher.start();
                    if (!isInsideRange(styles, event.lineOffset + start)) {
                        StyleRange style = new StyleRange();
                        style.start = event.lineOffset + start;
                        style.length = word.length();
                        style.foreground = keywordColor;
                        style.fontStyle = SWT.BOLD;
                        styles.add(style);
                    }
                }
            }

            // Inline comments
            int commentIdx = line.indexOf("//");
            if (commentIdx >= 0) {
                StyleRange style = new StyleRange();
                style.start = event.lineOffset + commentIdx;
                style.length = line.length() - commentIdx;
                style.foreground = commentColor;
                style.fontStyle = SWT.ITALIC;
                styles.add(style);
            }

            event.styles = styles.toArray(new StyleRange[0]);
        }

        private boolean isInsideRange(List<StyleRange> ranges, int offset) {
            for (StyleRange r : ranges) {
                if (offset >= r.start && offset < r.start + r.length) {
                    return true;
                }
            }
            return false;
        }
    }
}
