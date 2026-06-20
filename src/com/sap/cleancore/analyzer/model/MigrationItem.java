package com.sap.cleancore.analyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The unit of migration backlog: one Z object + findings + recommended modern replacements + effort.
 */
public class MigrationItem {

    public enum EffortCategory { S, M, L, XL }

    private ZObject zObject;
    private List<Finding> findings = new ArrayList<>();
    private List<MappingEntry> recommendedMappings = new ArrayList<>();
    private EffortCategory effortCategory;
    private double estimatedMD;     // man-days
    private String risk;            // LOW / MEDIUM / HIGH
    private String reasoning;       // human-readable explanation
    private Disposition disposition = Disposition.UNDECIDED; // SAP decision tree: RETIRE/RETAIN/ADAPT/RENOVATE
    private long usageCount = -1;   // ABAP Call Monitor (SCMON/SUSG) usage; -1 = unknown, 0 = unused (retire candidate)

    public MigrationItem() {}

    public MigrationItem(ZObject zObject) {
        this.zObject = zObject;
    }

    public ZObject getzObject() { return zObject; }
    public void setzObject(ZObject zObject) { this.zObject = zObject; }

    public List<Finding> getFindings() { return findings; }
    public void setFindings(List<Finding> findings) { this.findings = findings; }
    public void addFinding(Finding f) { this.findings.add(f); }

    public List<MappingEntry> getRecommendedMappings() { return recommendedMappings; }
    public void setRecommendedMappings(List<MappingEntry> recommendedMappings) {
        this.recommendedMappings = recommendedMappings;
    }
    public void addMapping(MappingEntry m) { this.recommendedMappings.add(m); }

    public EffortCategory getEffortCategory() { return effortCategory; }
    public void setEffortCategory(EffortCategory effortCategory) { this.effortCategory = effortCategory; }

    public double getEstimatedMD() { return estimatedMD; }
    public void setEstimatedMD(double estimatedMD) { this.estimatedMD = estimatedMD; }

    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Disposition getDisposition() { return disposition; }
    public void setDisposition(Disposition disposition) {
        this.disposition = (disposition != null ? disposition : Disposition.UNDECIDED);
    }

    public long getUsageCount() { return usageCount; }
    public void setUsageCount(long usageCount) { this.usageCount = usageCount; }

    /** Count findings of a given severity. */
    public int countSeverity(Finding.Severity sev) {
        int n = 0;
        for (Finding f : findings) if (f.getSeverity() == sev) n++;
        return n;
    }
}
