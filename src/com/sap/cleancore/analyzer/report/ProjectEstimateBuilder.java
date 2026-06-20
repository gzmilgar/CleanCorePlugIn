package com.sap.cleancore.analyzer.report;

import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Disposition;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;
import com.sap.cleancore.analyzer.utils.ResourceLoader;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates an {@link AnalysisRun} into a {@link ProjectEstimate}: category /
 * disposition / size / object-type breakdowns, a wave plan, contingency and a
 * role split. Report settings (contingency %, role ratios) are read from the
 * {@code report} block of object_rules.json with safe built-in fallbacks.
 *
 * <p>Wave plan (each item lands in exactly one wave):
 * <ul>
 *   <li>Wave 0 — Decommission: RETIRE (unused).</li>
 *   <li>Wave 1 — Quick wins: RETAIN + small (S) adapts.</li>
 *   <li>Wave 2 — Adapt: medium (M) adapts.</li>
 *   <li>Wave 3 — Renovate &amp; heavy: RENOVATE + L/XL + modifications +
 *       integration.</li>
 * </ul>
 */
public class ProjectEstimateBuilder {

    // Categories
    private static final String CAT_CUSTOM = "custom_code";
    private static final String CAT_MODIFICATION = "modifications";
    private static final String CAT_DDIC = "ddic";
    private static final String CAT_INTEGRATION = "integration";

    private int contingencyPct = 15;
    private Map<String, Double> roleSplit = defaultRoleSplit();

    public ProjectEstimateBuilder() {
        loadConfig();
    }

    public ProjectEstimate build(AnalysisRun run) {
        ProjectEstimate est = new ProjectEstimate();
        if (run == null) return est;

        est.setSystemDisplay(run.getSystemDisplay());
        if (run.getScenario() != null) {
            est.setScenarioDisplay(run.getScenario().getDisplayName() != null
                    ? run.getScenario().getDisplayName() : run.getScenario().getId());
        }

        waveIndex.clear();
        waveIndex.put("wave0", new ProjectEstimate.Wave("wave0", "Wave 0 — Decommission (retire unused)"));
        waveIndex.put("wave1", new ProjectEstimate.Wave("wave1", "Wave 1 — Quick wins (retain + small adapts)"));
        waveIndex.put("wave2", new ProjectEstimate.Wave("wave2", "Wave 2 — Adapt (medium)"));
        waveIndex.put("wave3", new ProjectEstimate.Wave("wave3", "Wave 3 — Renovate & heavy (large + modifications + integration)"));

        double codeMD = 0;
        for (MigrationItem it : run.getItems()) {
            if (it == null || it.getzObject() == null) continue;
            double md = it.getEstimatedMD();
            codeMD += md;

            String cat = categoryOf(it);
            addD(est.getMdByCategory(), cat, md);
            addI(est.getCountByCategory(), cat, 1);

            Disposition d = it.getDisposition() != null ? it.getDisposition() : Disposition.UNDECIDED;
            est.getMdByDisposition().merge(d, md, Double::sum);
            est.getCountByDisposition().merge(d, 1, Integer::sum);

            if (it.getEffortCategory() != null) addI(est.getCountBySize(), it.getEffortCategory().name(), 1);

            ZObjectType t = it.getzObject().getType();
            addI(est.getCountByObjectType(), t != null ? t.name() : "UNKNOWN", 1);

            ProjectEstimate.Wave w = waveFor(it);
            w.itemCount++; w.md += md;
        }

        // Integration inventory (Phase 3): adds to the 'integration' category
        // and the wave plan (decommission when unused, else heavy/Wave3).
        double inventoryMD = 0;
        if (run.getInventory() != null) {
            for (com.sap.cleancore.analyzer.model.inventory.InventoryItem inv : run.getInventory()) {
                if (inv == null) continue;
                double md = inv.getEstimatedMD();
                inventoryMD += md;
                addD(est.getMdByCategory(), CAT_INTEGRATION, md);
                addI(est.getCountByCategory(), CAT_INTEGRATION, 1);
                if (inv.getEffortCategory() != null) addI(est.getCountBySize(), inv.getEffortCategory().name(), 1);
                boolean retire = inv.getUsageCount() == 0;
                ProjectEstimate.Wave w = waveById(retire ? "wave0" : "wave3");
                w.itemCount++; w.md += md;
            }
        }

        est.getWaves().addAll(waveIndex.values());

        est.setCodeMD(round(codeMD));
        est.setInventoryMD(round(inventoryMD));
        double total = codeMD + inventoryMD;
        est.setTotalMD(round(total));
        est.setContingencyPct(contingencyPct);
        est.setTotalWithContingencyMD(round(total * (1.0 + contingencyPct / 100.0)));

        for (Map.Entry<String, Double> e : roleSplit.entrySet()) {
            est.getMdByRole().put(e.getKey(), round(total * e.getValue()));
        }
        return est;
    }

    /** Each item maps to exactly one wave (partition guaranteed). */
    private ProjectEstimate.Wave waveFor(MigrationItem it) {
        Disposition d = it.getDisposition() != null ? it.getDisposition() : Disposition.UNDECIDED;
        boolean isMod = it.getzObject().isModification() || isModType(it.getzObject().getType());
        MigrationItem.EffortCategory cat = it.getEffortCategory();

        if (d == Disposition.RETIRE) return waveById("wave0");
        if (d == Disposition.RENOVATE) return waveById("wave3");
        if (isMod) return waveById("wave3");
        if (d == Disposition.RETAIN) return waveById("wave1");
        // ADAPT or UNDECIDED -> by size
        if (cat == MigrationItem.EffortCategory.S) return waveById("wave1");
        if (cat == MigrationItem.EffortCategory.M) return waveById("wave2");
        return waveById("wave3"); // L/XL or unknown
    }

    // Waves are created fresh per build(); this resolves by id within that build.
    private final Map<String, ProjectEstimate.Wave> waveIndex = new LinkedHashMap<>();
    private ProjectEstimate.Wave waveById(String id) {
        return waveIndex.get(id);
    }

    private String categoryOf(MigrationItem it) {
        ZObject z = it.getzObject();
        if (z.isModification() || isModType(z.getType())) return CAT_MODIFICATION;
        if (isDdicType(z.getType())) return CAT_DDIC;
        return CAT_CUSTOM;
    }

    private boolean isModType(ZObjectType t) {
        return t == ZObjectType.MODIFICATION || t == ZObjectType.STANDARD_MODIFICATION
                || t == ZObjectType.USER_EXIT;
    }

    private boolean isDdicType(ZObjectType t) {
        if (t == null) return false;
        switch (t) {
            case Z_DDIC_TABLE: case Z_DDIC_STRUCTURE: case Z_DOMAIN: case Z_DATA_ELEMENT:
            case Z_APPEND_STRUCTURE: case CUSTOM_FIELD:
                return true;
            default:
                return false;
        }
    }

    private static void addD(Map<String, Double> m, String k, double v) { m.merge(k, v, Double::sum); }
    private static void addI(Map<String, Integer> m, String k, int v) { m.merge(k, v, Integer::sum); }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        try {
            String text = ResourceLoader.readBundleText(null, "resources/mapping/object_rules.json");
            Map<String, Object> root = (Map<String, Object>) SimpleJsonParser.parse(text);
            Map<String, Object> rep = SimpleJsonParser.obj(root, "report");
            contingencyPct = (int) SimpleJsonParser.dbl(rep, "contingencyPct", 15);
            Map<String, Object> rs = SimpleJsonParser.obj(rep, "roleSplit");
            if (!rs.isEmpty()) {
                Map<String, Double> parsed = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : rs.entrySet()) {
                    if (e.getValue() instanceof Number) parsed.put(e.getKey(), ((Number) e.getValue()).doubleValue());
                }
                if (!parsed.isEmpty()) roleSplit = parsed;
            }
        } catch (Exception e) {
            contingencyPct = 15;
            roleSplit = defaultRoleSplit();
        }
    }

    private static Map<String, Double> defaultRoleSplit() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("developer", 0.7);
        m.put("functional", 0.2);
        m.put("basis", 0.1);
        return m;
    }
}
