package com.sap.cleancore.analyzer.utils;

import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.model.MigrationItem;

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

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8))) {
            w.write(JsonWriter.write(root));
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ');
    }
}
