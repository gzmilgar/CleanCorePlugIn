package com.sap.cleancore.analyzer.report;

import com.sap.cleancore.analyzer.model.Disposition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-level effort roll-up for a whole transformation assessment: total
 * man-days plus breakdowns by work category, SAP disposition (RETIRE/RETAIN/
 * ADAPT/RENOVATE), effort size, object type, and a wave/phase plan. Built by
 * {@link ProjectEstimateBuilder} from an
 * {@link com.sap.cleancore.analyzer.model.AnalysisRun}.
 *
 * <p>Plain data holder (no Eclipse dependencies) so it can be exercised
 * headlessly and rendered by {@link ProjectReportExporter}.
 */
public class ProjectEstimate {

    /** A delivery wave/phase grouping items by disposition + size. */
    public static class Wave {
        public final String id;
        public final String name;
        public int itemCount;
        public double md;
        public Wave(String id, String name) { this.id = id; this.name = name; }
    }

    private String systemDisplay;
    private String scenarioDisplay;

    private double codeMD;            // effort from custom code (incl. modifications/DDIC)
    private double inventoryMD;       // effort from integration inventory (Phase 3)
    private double totalMD;           // codeMD + inventoryMD
    private int contingencyPct;
    private double totalWithContingencyMD;

    private final Map<String, Double> mdByCategory = new LinkedHashMap<>();      // custom_code/modifications/ddic/integration
    private final Map<String, Integer> countByCategory = new LinkedHashMap<>();
    private final Map<Disposition, Double> mdByDisposition = new EnumMap<>(Disposition.class);
    private final Map<Disposition, Integer> countByDisposition = new EnumMap<>(Disposition.class);
    private final Map<String, Integer> countBySize = new LinkedHashMap<>();      // S/M/L/XL
    private final Map<String, Integer> countByObjectType = new LinkedHashMap<>();
    private final Map<String, Double> mdByRole = new LinkedHashMap<>();          // developer/functional/basis
    private final List<Wave> waves = new ArrayList<>();

    public String getSystemDisplay() { return systemDisplay; }
    public void setSystemDisplay(String s) { this.systemDisplay = s; }

    public String getScenarioDisplay() { return scenarioDisplay; }
    public void setScenarioDisplay(String s) { this.scenarioDisplay = s; }

    public double getCodeMD() { return codeMD; }
    public void setCodeMD(double v) { this.codeMD = v; }

    public double getInventoryMD() { return inventoryMD; }
    public void setInventoryMD(double v) { this.inventoryMD = v; }

    public double getTotalMD() { return totalMD; }
    public void setTotalMD(double v) { this.totalMD = v; }

    public int getContingencyPct() { return contingencyPct; }
    public void setContingencyPct(int v) { this.contingencyPct = v; }

    public double getTotalWithContingencyMD() { return totalWithContingencyMD; }
    public void setTotalWithContingencyMD(double v) { this.totalWithContingencyMD = v; }

    public Map<String, Double> getMdByCategory() { return mdByCategory; }
    public Map<String, Integer> getCountByCategory() { return countByCategory; }
    public Map<Disposition, Double> getMdByDisposition() { return mdByDisposition; }
    public Map<Disposition, Integer> getCountByDisposition() { return countByDisposition; }
    public Map<String, Integer> getCountBySize() { return countBySize; }
    public Map<String, Integer> getCountByObjectType() { return countByObjectType; }
    public Map<String, Double> getMdByRole() { return mdByRole; }
    public List<Wave> getWaves() { return waves; }
}
