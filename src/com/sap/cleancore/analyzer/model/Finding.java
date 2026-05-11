package com.sap.cleancore.analyzer.model;

/**
 * A single ATC or static-analyzer finding attached to a Z object.
 * Source can be ATC (S4HANA_READINESS) or one of our bundled static rules.
 */
public class Finding {

    public enum Severity { INFO, WARNING, ERROR }
    public enum Source { ATC, STATIC, READINESS_CHECK, OBSOLETE_API, MODIFICATION }

    private String checkId;
    private String message;
    private Severity severity;
    private Source source;
    private int line;
    private String category;        // e.g. "SQL", "Released API", "Modification"

    public Finding() {}

    public Finding(String checkId, String message, Severity severity, Source source) {
        this.checkId = checkId;
        this.message = message;
        this.severity = severity;
        this.source = source;
    }

    public String getCheckId() { return checkId; }
    public void setCheckId(String checkId) { this.checkId = checkId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Override
    public String toString() {
        return "[" + severity + "/" + source + "] " + checkId + ": " + message;
    }
}
