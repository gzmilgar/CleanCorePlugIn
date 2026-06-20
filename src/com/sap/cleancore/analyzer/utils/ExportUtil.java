package com.sap.cleancore.analyzer.utils;

import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.report.ProjectEstimate;
import com.sap.cleancore.analyzer.report.ProjectEstimateBuilder;
import com.sap.cleancore.analyzer.report.ProjectReportExporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CSV / JSON / minimal XLSX export for an AnalysisRun.
 * XLSX is written as a tab-separated .xls (Excel reads it). True OOXML
 * generation is out of scope without external libs.
 */
public class ExportUtil {

    public static void exportCsv(AnalysisRun run, File target) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8))) {
            w.write("Package;ZObject;Type;LOC;Complexity;Findings;ModernEquivalent;Category;EstimatedMD;Risk;Disposition;UsageCount");
            w.newLine();
            for (MigrationItem it : run.getItems()) {
                String pkg = safe(it.getzObject().getDevClass());
                String name = safe(it.getzObject().getName());
                String type = it.getzObject().getType() != null ? it.getzObject().getType().name() : "";
                String mod = "";
                if (!it.getRecommendedMappings().isEmpty()) {
                    MappingEntry m = it.getRecommendedMappings().get(0);
                    mod = m.getModernType() + " " + m.getModernName();
                }
                w.write(String.join(";",
                        pkg, name, type,
                        String.valueOf(it.getzObject().getLoc()),
                        String.valueOf(it.getzObject().getComplexity()),
                        String.valueOf(it.getFindings().size()),
                        safe(mod),
                        it.getEffortCategory() != null ? it.getEffortCategory().name() : "",
                        String.valueOf(it.getEstimatedMD()),
                        safe(it.getRisk()),
                        it.getDisposition() != null ? it.getDisposition().name() : "",
                        it.getUsageCount() >= 0 ? String.valueOf(it.getUsageCount()) : ""));
                w.newLine();
            }
        }
    }

    public static void exportJson(AnalysisRun run, File target) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("system", run.getSystemDisplay());
        if (run.getScenario() != null) {
            Map<String, Object> sc = new LinkedHashMap<>();
            sc.put("id", run.getScenario().getId());
            sc.put("displayName", run.getScenario().getDisplayName());
            sc.put("sourceRelease", run.getScenario().getSourceRelease() != null
                    ? run.getScenario().getSourceRelease().name() : null);
            sc.put("targetPlatform", run.getScenario().getTargetPlatform() != null
                    ? run.getScenario().getTargetPlatform().name() : null);
            root.put("scenario", sc);
        }
        root.put("startedAt", run.getStartedAt());
        root.put("finishedAt", run.getFinishedAt());
        root.put("totalMD", run.getTotalMD());
        root.put("inventoryMD", run.getInventoryMD());
        root.put("grandTotalMD", run.getGrandTotalMD());
        root.put("countS", run.getCountS());
        root.put("countM", run.getCountM());
        root.put("countL", run.getCountL());
        root.put("countXL", run.getCountXL());

        java.util.List<Object> arr = new java.util.ArrayList<>();
        for (MigrationItem it : run.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", it.getzObject().getName());
            row.put("type", it.getzObject().getType() != null ? it.getzObject().getType().name() : null);
            row.put("devClass", it.getzObject().getDevClass());
            row.put("loc", it.getzObject().getLoc());
            row.put("complexity", it.getzObject().getComplexity());
            row.put("findingsCount", it.getFindings().size());
            row.put("category", it.getEffortCategory() != null ? it.getEffortCategory().name() : null);
            row.put("estimatedMD", it.getEstimatedMD());
            row.put("risk", it.getRisk());
            row.put("disposition", it.getDisposition() != null ? it.getDisposition().name() : null);
            if (it.getUsageCount() >= 0) row.put("usageCount", it.getUsageCount());
            row.put("reasoning", it.getReasoning());
            java.util.List<Object> maps = new java.util.ArrayList<>();
            for (MappingEntry m : it.getRecommendedMappings()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("legacyType", m.getLegacyType() != null ? m.getLegacyType().name() : null);
                mm.put("legacyName", m.getLegacyName());
                mm.put("modernType", m.getModernType() != null ? m.getModernType().name() : null);
                mm.put("modernName", m.getModernName());
                mm.put("releaseState", m.getReleaseState() != null ? m.getReleaseState().name() : null);
                maps.add(mm);
            }
            row.put("recommendedMappings", maps);
            arr.add(row);
        }
        root.put("items", arr);

        java.util.List<Object> inv = new java.util.ArrayList<>();
        if (run.getInventory() != null) {
            for (com.sap.cleancore.analyzer.model.inventory.InventoryItem it : run.getInventory()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", it.getCategory() != null ? it.getCategory().name() : null);
                row.put("name", it.getName());
                row.put("target", it.getTarget());
                row.put("protocol", it.getProtocol());
                row.put("direction", it.getDirection());
                if (it.getUsageCount() >= 0) row.put("usageCount", it.getUsageCount());
                row.put("category_effort", it.getEffortCategory() != null ? it.getEffortCategory().name() : null);
                row.put("estimatedMD", it.getEstimatedMD());
                row.put("risk", it.getRisk());
                row.put("migrationNote", it.getMigrationNote());
                inv.add(row);
            }
        }
        root.put("inventory", inv);

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8))) {
            w.write(JsonWriter.write(root));
        }
    }

    /**
     * Builds the project-level estimate from the run and writes a presales HTML
     * report. A summary CSV is written next to it (same name, .csv).
     */
    public static void exportProjectReportHtml(AnalysisRun run, File target) throws Exception {
        ProjectEstimate est = new ProjectEstimateBuilder().build(run);
        ProjectReportExporter exp = new ProjectReportExporter();
        exp.exportHtml(est, target);
        String path = target.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        File csv = new File(dot > 0 ? path.substring(0, dot) + ".csv" : path + ".csv");
        try { exp.exportSummaryCsv(est, csv); } catch (Exception ignored) { /* best-effort companion */ }
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ');
    }
}
