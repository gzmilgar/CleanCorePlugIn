package com.sap.cleancore.analyzer.model;

/**
 * A single custom (Z*) ABAP object discovered in the customer system.
 * Filled progressively by collectors (TADIR -> source -> static analysis -> ATC).
 */
public class ZObject {

    private String name;
    private ZObjectType type;
    private String devClass;        // SAP package
    private String author;
    private String createdOn;
    private String source;          // ABAP source code (populated on demand)
    private int loc;                // lines of code
    private int complexity;         // cyclomatic-ish
    private boolean modification;   // is a SAP standard modification (REPS in SAP package)
    private long usageCount = -1;   // ABAP Call Monitor (SCMON/SUSG) usage; -1 = unknown, 0 = unused

    public ZObject() {}

    public ZObject(String name, ZObjectType type, String devClass) {
        this.name = name;
        this.type = type;
        this.devClass = devClass;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ZObjectType getType() { return type; }
    public void setType(ZObjectType type) { this.type = type; }

    public String getDevClass() { return devClass; }
    public void setDevClass(String devClass) { this.devClass = devClass; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCreatedOn() { return createdOn; }
    public void setCreatedOn(String createdOn) { this.createdOn = createdOn; }

    public String getSource() { return source; }
    public void setSource(String source) {
        this.source = source;
        if (source != null) this.loc = countLoc(source);
    }

    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }

    public int getComplexity() { return complexity; }
    public void setComplexity(int complexity) { this.complexity = complexity; }

    public boolean isModification() { return modification; }
    public void setModification(boolean modification) { this.modification = modification; }

    public long getUsageCount() { return usageCount; }
    public void setUsageCount(long usageCount) { this.usageCount = usageCount; }

    private static int countLoc(String s) {
        int lines = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') lines++;
        }
        return lines + 1;
    }

    @Override
    public String toString() {
        return type + " " + name + " (" + devClass + ")";
    }
}
