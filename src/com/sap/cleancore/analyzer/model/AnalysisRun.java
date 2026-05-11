package com.sap.cleancore.analyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One full scan of a customer system. Holds capabilities, all migration items
 * (one per Z object) and computed totals.
 */
public class AnalysisRun {

    private String systemDisplay;       // e.g. "ECC_100_USER"
    private String startedAt;
    private String finishedAt;
    private SystemCapabilities capabilities;
    private List<MigrationItem> items = new ArrayList<>();
    private List<String> packageFilter = new ArrayList<>();
    private double totalMD;
    private int countS, countM, countL, countXL;

    public String getSystemDisplay() { return systemDisplay; }
    public void setSystemDisplay(String systemDisplay) { this.systemDisplay = systemDisplay; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public SystemCapabilities getCapabilities() { return capabilities; }
    public void setCapabilities(SystemCapabilities capabilities) { this.capabilities = capabilities; }

    public List<MigrationItem> getItems() { return items; }
    public void setItems(List<MigrationItem> items) { this.items = items; }
    public void addItem(MigrationItem item) { this.items.add(item); }

    public List<String> getPackageFilter() { return packageFilter; }
    public void setPackageFilter(List<String> packageFilter) { this.packageFilter = packageFilter; }

    public double getTotalMD() { return totalMD; }
    public int getCountS() { return countS; }
    public int getCountM() { return countM; }
    public int getCountL() { return countL; }
    public int getCountXL() { return countXL; }

    /** Aggregate totals from items list. Call after items are filled. */
    public void recomputeTotals() {
        totalMD = 0;
        countS = countM = countL = countXL = 0;
        for (MigrationItem it : items) {
            totalMD += it.getEstimatedMD();
            if (it.getEffortCategory() == null) continue;
            switch (it.getEffortCategory()) {
                case S:  countS++;  break;
                case M:  countM++;  break;
                case L:  countL++;  break;
                case XL: countXL++; break;
            }
        }
    }
}
