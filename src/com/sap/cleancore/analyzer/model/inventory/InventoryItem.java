package com.sap.cleancore.analyzer.model.inventory;

import com.sap.cleancore.analyzer.model.MigrationItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single integration / system-inventory item (e.g. an RFC destination, an
 * IDoc partner profile, a SICF/OData service, a PI/PO interface). Carries
 * enough metadata for the project report and an effort estimate.
 *
 * <p>Plain data holder — no Eclipse dependencies.
 */
public class InventoryItem {

    private InventoryCategory category = InventoryCategory.UNKNOWN;
    private String name;
    private String target;          // host / target system / endpoint
    private String protocol;        // e.g. RFC, HTTP, SOAP, IDoc type
    private String direction;       // INBOUND / OUTBOUND / BIDIRECTIONAL
    private long usageCount = -1;    // -1 = unknown, 0 = unused (retire candidate)
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private String migrationNote;

    private MigrationItem.EffortCategory effortCategory;
    private double estimatedMD;
    private String risk;            // LOW / MEDIUM / HIGH

    public InventoryItem() {}

    public InventoryItem(InventoryCategory category, String name) {
        this.category = (category != null ? category : InventoryCategory.UNKNOWN);
        this.name = name;
    }

    public InventoryCategory getCategory() { return category; }
    public void setCategory(InventoryCategory category) {
        this.category = (category != null ? category : InventoryCategory.UNKNOWN);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public long getUsageCount() { return usageCount; }
    public void setUsageCount(long usageCount) { this.usageCount = usageCount; }

    public Map<String, String> getAttributes() { return attributes; }
    public void putAttribute(String k, String v) { if (k != null) attributes.put(k, v); }

    public String getMigrationNote() { return migrationNote; }
    public void setMigrationNote(String migrationNote) { this.migrationNote = migrationNote; }

    public MigrationItem.EffortCategory getEffortCategory() { return effortCategory; }
    public void setEffortCategory(MigrationItem.EffortCategory effortCategory) { this.effortCategory = effortCategory; }

    public double getEstimatedMD() { return estimatedMD; }
    public void setEstimatedMD(double estimatedMD) { this.estimatedMD = estimatedMD; }

    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }

    @Override
    public String toString() {
        return category + " " + name + (target != null ? " → " + target : "");
    }
}
