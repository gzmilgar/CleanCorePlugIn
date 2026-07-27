package com.sap.cleancore.analyzer.utils;

import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.model.MigrationItem;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * CSV / JSON / XLSX export for an AnalysisRun.
 * XLSX is generated as true Office Open XML (ZIP of XML files) using only
 * the JDK's built-in java.util.zip — no external libraries required.
 */
public class ExportUtil {

    public static void exportCsv(AnalysisRun run, File target) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8))) {
            w.write("Package;ZObject;Type;LOC;Complexity;Findings;ModernEquivalent;Category;EstimatedMD;Risk");
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
                        safe(it.getRisk())));
                w.newLine();
            }
        }
    }

    public static void exportJson(AnalysisRun run, File target) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("system", run.getSystemDisplay());
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

    // ── XLSX (Office Open XML) export ──────────────────────────────────

    /**
     * Export an AnalysisRun to a real .xlsx file with multiple sheets:
     *   Sheet 1 – Summary        (system info, totals, category breakdown)
     *   Sheet 2 – Migration Items (one row per Z object, with error/warning/info counts)
     *   Sheet 3 – Findings Detail (one row per finding, with parent object info)
     *   Sheet 4 – Mappings       (one row per mapping, with parent object info)
     *   Sheet 5 – Full Detail    (denormalized: object + finding + mapping on every row,
     *                              ideal for Excel filtering/pivot)
     */
    public static void exportExcel(AnalysisRun run, File target) throws Exception {
        List<String> sst = new ArrayList<>();
        Map<String, Integer> sstIdx = new LinkedHashMap<>();

        // ── Sheet 1: Summary ──
        List<String[]> summaryRows = new ArrayList<>();
        summaryRows.add(new String[]{"Clean Core Analysis Report"});
        summaryRows.add(new String[]{""});
        summaryRows.add(new String[]{"System", nvl(run.getSystemDisplay())});
        summaryRows.add(new String[]{"Started At", nvl(run.getStartedAt())});
        summaryRows.add(new String[]{"Finished At", nvl(run.getFinishedAt())});
        summaryRows.add(new String[]{""});
        summaryRows.add(new String[]{"Total Objects", String.valueOf(run.getItems().size())});
        summaryRows.add(new String[]{"Total Man-Days", String.valueOf(run.getTotalMD())});
        summaryRows.add(new String[]{""});
        summaryRows.add(new String[]{"Category", "Count"});
        summaryRows.add(new String[]{"S (Small)", String.valueOf(run.getCountS())});
        summaryRows.add(new String[]{"M (Medium)", String.valueOf(run.getCountM())});
        summaryRows.add(new String[]{"L (Large)", String.valueOf(run.getCountL())});
        summaryRows.add(new String[]{"XL (Extra Large)", String.valueOf(run.getCountXL())});
        if (run.getCapabilities() != null) {
            summaryRows.add(new String[]{""});
            summaryRows.add(new String[]{"Capabilities", run.getCapabilities().summary()});
        }

        // ── Sheet 2: Migration Items (with severity breakdown) ──
        List<String[]> itemRows = new ArrayList<>();
        itemRows.add(new String[]{"Package", "Object", "Type", "LOC", "Complexity",
                "Errors", "Warnings", "Info", "Total Findings",
                "Modern Equivalent", "Category", "Estimated MD", "Risk", "Reasoning"});
        for (MigrationItem it : run.getItems()) {
            String mod = "";
            if (!it.getRecommendedMappings().isEmpty()) {
                MappingEntry m = it.getRecommendedMappings().get(0);
                mod = m.getModernType() + " " + m.getModernName();
            }
            int errors = it.countSeverity(Finding.Severity.ERROR);
            int warnings = it.countSeverity(Finding.Severity.WARNING);
            int infos = it.countSeverity(Finding.Severity.INFO);
            itemRows.add(new String[]{
                    nvl(it.getzObject().getDevClass()),
                    nvl(it.getzObject().getName()),
                    it.getzObject().getType() != null ? it.getzObject().getType().name() : "",
                    String.valueOf(it.getzObject().getLoc()),
                    String.valueOf(it.getzObject().getComplexity()),
                    String.valueOf(errors),
                    String.valueOf(warnings),
                    String.valueOf(infos),
                    String.valueOf(it.getFindings().size()),
                    mod,
                    it.getEffortCategory() != null ? it.getEffortCategory().name() : "",
                    String.valueOf(it.getEstimatedMD()),
                    nvl(it.getRisk()),
                    nvl(it.getReasoning())
            });
        }

        // ── Sheet 3: Findings Detail (with parent object context) ──
        List<String[]> findingRows = new ArrayList<>();
        findingRows.add(new String[]{"Package", "Object", "Type", "Category (Effort)",
                "Risk", "Severity", "Source", "Check ID", "Rule Name",
                "Line", "Category (Finding)", "Message", "Matched Code",
                "Suggestion", "Clean Core API"});
        for (MigrationItem it : run.getItems()) {
            String pkg = nvl(it.getzObject().getDevClass());
            String objName = nvl(it.getzObject().getName());
            String objType = it.getzObject().getType() != null ? it.getzObject().getType().name() : "";
            String cat = it.getEffortCategory() != null ? it.getEffortCategory().name() : "";
            String risk = nvl(it.getRisk());
            for (Finding f : it.getFindings()) {
                findingRows.add(new String[]{
                        pkg, objName, objType, cat, risk,
                        f.getSeverity() != null ? f.getSeverity().name() : "",
                        f.getSource() != null ? f.getSource().name() : "",
                        nvl(f.getCheckId()),
                        nvl(f.getRuleName()),
                        String.valueOf(f.getLine()),
                        nvl(f.getCategory()),
                        nvl(f.getMessage()),
                        nvl(f.getMatchedCode()),
                        nvl(f.getSuggestion()),
                        nvl(f.getCleanCoreApi())
                });
            }
        }

        // ── Sheet 4: Mappings (with parent object context) ──
        List<String[]> mappingRows = new ArrayList<>();
        mappingRows.add(new String[]{"Package", "Object", "Type", "Category (Effort)",
                "Risk", "Legacy Type", "Legacy Name",
                "Modern Type", "Modern Name", "Release State",
                "Source", "Notes", "Documentation URL"});
        for (MigrationItem it : run.getItems()) {
            String pkg = nvl(it.getzObject().getDevClass());
            String objName = nvl(it.getzObject().getName());
            String objType = it.getzObject().getType() != null ? it.getzObject().getType().name() : "";
            String cat = it.getEffortCategory() != null ? it.getEffortCategory().name() : "";
            String risk = nvl(it.getRisk());
            for (MappingEntry m : it.getRecommendedMappings()) {
                mappingRows.add(new String[]{
                        pkg, objName, objType, cat, risk,
                        m.getLegacyType() != null ? m.getLegacyType().name() : "",
                        nvl(m.getLegacyName()),
                        m.getModernType() != null ? m.getModernType().name() : "",
                        nvl(m.getModernName()),
                        m.getReleaseState() != null ? m.getReleaseState().name() : "",
                        m.getSource() != null ? m.getSource().name() : "",
                        nvl(m.getNotes()),
                        nvl(m.getDocumentationUrl())
                });
            }
        }

        // ── Sheet 5: Full Detail (denormalized — one row per finding+mapping pair) ──
        List<String[]> fullRows = new ArrayList<>();
        fullRows.add(new String[]{
                "Package", "Object", "Type", "LOC", "Complexity",
                "Category", "Estimated MD", "Risk", "Reasoning",
                // Finding columns
                "Finding Severity", "Finding Source", "Check ID", "Rule Name",
                "Line", "Finding Category", "Finding Message",
                "Matched Code", "Suggestion", "Clean Core API",
                // Mapping columns
                "Legacy Type", "Legacy Name", "Modern Type", "Modern Name",
                "Release State", "Mapping Source", "Mapping Notes"
        });
        for (MigrationItem it : run.getItems()) {
            String pkg = nvl(it.getzObject().getDevClass());
            String objName = nvl(it.getzObject().getName());
            String objType = it.getzObject().getType() != null ? it.getzObject().getType().name() : "";
            String loc = String.valueOf(it.getzObject().getLoc());
            String complexity = String.valueOf(it.getzObject().getComplexity());
            String cat = it.getEffortCategory() != null ? it.getEffortCategory().name() : "";
            String md = String.valueOf(it.getEstimatedMD());
            String risk = nvl(it.getRisk());
            String reasoning = nvl(it.getReasoning());

            List<Finding> findings = it.getFindings();
            List<MappingEntry> mappings = it.getRecommendedMappings();
            int maxRows = Math.max(1, Math.max(findings.size(), mappings.size()));

            for (int i = 0; i < maxRows; i++) {
                String[] row = new String[26];
                // Object info (repeated on each sub-row)
                row[0] = pkg; row[1] = objName; row[2] = objType;
                row[3] = loc; row[4] = complexity;
                row[5] = cat; row[6] = md; row[7] = risk; row[8] = reasoning;

                // Finding columns (if available for this index)
                if (i < findings.size()) {
                    Finding f = findings.get(i);
                    row[9]  = f.getSeverity() != null ? f.getSeverity().name() : "";
                    row[10] = f.getSource() != null ? f.getSource().name() : "";
                    row[11] = nvl(f.getCheckId());
                    row[12] = nvl(f.getRuleName());
                    row[13] = String.valueOf(f.getLine());
                    row[14] = nvl(f.getCategory());
                    row[15] = nvl(f.getMessage());
                    row[16] = nvl(f.getMatchedCode());
                    row[17] = nvl(f.getSuggestion());
                    row[18] = nvl(f.getCleanCoreApi());
                } else {
                    for (int j = 9; j <= 18; j++) row[j] = "";
                }

                // Mapping columns (if available for this index)
                if (i < mappings.size()) {
                    MappingEntry m = mappings.get(i);
                    row[19] = m.getLegacyType() != null ? m.getLegacyType().name() : "";
                    row[20] = nvl(m.getLegacyName());
                    row[21] = m.getModernType() != null ? m.getModernType().name() : "";
                    row[22] = nvl(m.getModernName());
                    row[23] = m.getReleaseState() != null ? m.getReleaseState().name() : "";
                    row[24] = m.getSource() != null ? m.getSource().name() : "";
                    row[25] = nvl(m.getNotes());
                } else {
                    for (int j = 19; j <= 25; j++) row[j] = "";
                }

                fullRows.add(row);
            }
        }

        // Register all shared strings
        registerStrings(summaryRows, sst, sstIdx);
        registerStrings(itemRows, sst, sstIdx);
        registerStrings(findingRows, sst, sstIdx);
        registerStrings(mappingRows, sst, sstIdx);
        registerStrings(fullRows, sst, sstIdx);

        // ── Write the XLSX ZIP package (5 sheets) ──
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            zos.setLevel(6);

            writeZipEntry(zos, "[Content_Types].xml", buildContentTypes());
            writeZipEntry(zos, "_rels/.rels", buildRootRels());
            writeZipEntry(zos, "xl/workbook.xml", buildWorkbook());
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRels());
            writeZipEntry(zos, "xl/styles.xml", buildStyles());
            writeZipEntry(zos, "xl/sharedStrings.xml", buildSharedStrings(sst));
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheet(summaryRows, sstIdx, true));
            writeZipEntry(zos, "xl/worksheets/sheet2.xml", buildSheet(itemRows, sstIdx, true));
            writeZipEntry(zos, "xl/worksheets/sheet3.xml", buildSheet(findingRows, sstIdx, true));
            writeZipEntry(zos, "xl/worksheets/sheet4.xml", buildSheet(mappingRows, sstIdx, true));
            writeZipEntry(zos, "xl/worksheets/sheet5.xml", buildSheet(fullRows, sstIdx, true));
        }
    }

    // ── XLSX helper methods ──

    private static void registerStrings(List<String[]> rows,
                                        List<String> sst, Map<String, Integer> idx) {
        for (String[] row : rows) {
            for (String cell : row) {
                if (!idx.containsKey(cell)) {
                    idx.put(cell, sst.size());
                    sst.add(cell);
                }
            }
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String buildContentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet4.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet5.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    private static String buildRootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String buildWorkbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"Summary\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"Migration Items\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "<sheet name=\"Findings Detail\" sheetId=\"3\" r:id=\"rId3\"/>"
                + "<sheet name=\"Mappings\" sheetId=\"4\" r:id=\"rId4\"/>"
                + "<sheet name=\"Full Detail\" sheetId=\"5\" r:id=\"rId5\"/>"
                + "</sheets></workbook>";
    }

    private static String buildWorkbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/>"
                + "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet4.xml\"/>"
                + "<Relationship Id=\"rId5\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet5.xml\"/>"
                + "<Relationship Id=\"rId6\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rId7\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>"
                + "</Relationships>";
    }

    private static String buildStyles() {
        // Style 0 = normal, Style 1 = bold header (font index 1)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF4472C4\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border/></borders>"
                + "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>"
                + "<cellXfs count=\"2\">"
                + "<xf fontId=\"0\" fillId=\"0\" borderId=\"0\"/>"
                + "<xf fontId=\"1\" fillId=\"2\" borderId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "</cellXfs>"
                + "</styleSheet>";
    }

    private static String buildSharedStrings(List<String> sst) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
                .append(sst.size()).append("\" uniqueCount=\"").append(sst.size()).append("\">");
        for (String s : sst) {
            sb.append("<si><t>").append(xmlEsc(s)).append("</t></si>");
        }
        sb.append("</sst>");
        return sb.toString();
    }

    private static String buildSheet(List<String[]> rows, Map<String, Integer> sstIdx,
                                     boolean boldFirstRow) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            sb.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < cells.length; c++) {
                String ref = colRef(c) + (r + 1);
                Integer idx = sstIdx.get(cells[c]);
                if (idx != null) {
                    String style = (boldFirstRow && r == 0) ? " s=\"1\"" : "";
                    sb.append("<c r=\"").append(ref).append("\" t=\"s\"").append(style).append(">")
                            .append("<v>").append(idx).append("</v></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    /** Convert column index (0-based) to Excel column letter (A, B, ..., Z, AA, AB...). */
    private static String colRef(int col) {
        StringBuilder sb = new StringBuilder();
        col++;
        while (col > 0) {
            col--;
            sb.insert(0, (char) ('A' + col % 26));
            col /= 26;
        }
        return sb.toString();
    }

    /** XML-escape a string value for use inside XLSX XML. */
    private static String xmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ');
    }
}
