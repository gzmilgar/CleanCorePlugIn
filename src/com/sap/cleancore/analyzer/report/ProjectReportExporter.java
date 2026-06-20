package com.sap.cleancore.analyzer.report;

import com.sap.cleancore.analyzer.model.Disposition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Renders a {@link ProjectEstimate} as a presales-friendly, dependency-free
 * HTML report (and a summary CSV). Uses plain string building — no external
 * libraries — consistent with {@code JsonWriter}/{@code ExportUtil}.
 */
public class ProjectReportExporter {

    public void exportHtml(ProjectEstimate est, File target) throws Exception {
        String html = renderHtml(est);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8))) {
            w.write(html);
        }
    }

    public void exportSummaryCsv(ProjectEstimate est, File target) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(target), StandardCharsets.UTF_8))) {
            w.write("Section;Key;Value"); w.newLine();
            w.write(csv("Summary", "System", str(est.getSystemDisplay()))); w.newLine();
            w.write(csv("Summary", "Scenario", str(est.getScenarioDisplay()))); w.newLine();
            w.write(csv("Summary", "Total MD", num(est.getTotalMD()))); w.newLine();
            w.write(csv("Summary", "Contingency %", String.valueOf(est.getContingencyPct()))); w.newLine();
            w.write(csv("Summary", "Total + Contingency MD", num(est.getTotalWithContingencyMD()))); w.newLine();
            for (Map.Entry<Disposition, Double> e : est.getMdByDisposition().entrySet()) {
                w.write(csv("Disposition MD", e.getKey().name(), num(e.getValue()))); w.newLine();
            }
            for (Map.Entry<String, Double> e : est.getMdByCategory().entrySet()) {
                w.write(csv("Category MD", e.getKey(), num(e.getValue()))); w.newLine();
            }
            for (ProjectEstimate.Wave wv : est.getWaves()) {
                w.write(csv("Wave", wv.name, wv.itemCount + " items / " + num(wv.md) + " MD")); w.newLine();
            }
            for (Map.Entry<String, Double> e : est.getMdByRole().entrySet()) {
                w.write(csv("Role MD", e.getKey(), num(e.getValue()))); w.newLine();
            }
        }
    }

    String renderHtml(ProjectEstimate est) {
        StringBuilder b = new StringBuilder();
        b.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        b.append("<title>SAP Transformation Assessment</title><style>");
        b.append("body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#222}");
        b.append("h1{font-size:22px}h2{font-size:16px;margin-top:28px;border-bottom:1px solid #ddd;padding-bottom:4px}");
        b.append("table{border-collapse:collapse;margin-top:8px}td,th{border:1px solid #ccc;padding:6px 12px;text-align:left}");
        b.append("th{background:#f4f6f8}.kpi{display:inline-block;background:#0a6ed1;color:#fff;border-radius:8px;padding:12px 18px;margin:6px 10px 6px 0}");
        b.append(".kpi b{font-size:22px;display:block}.muted{color:#777;font-size:12px}");
        b.append("</style></head><body>");

        b.append("<h1>SAP Transformation Assessment</h1>");
        b.append("<div class=\"muted\">System: ").append(esc(est.getSystemDisplay()))
         .append(" &nbsp;|&nbsp; Scenario: ").append(esc(est.getScenarioDisplay())).append("</div>");

        b.append("<div style=\"margin-top:16px\">");
        b.append(kpi("Total effort", num(est.getTotalMD()) + " MD"));
        b.append(kpi("With contingency (" + est.getContingencyPct() + "%)", num(est.getTotalWithContingencyMD()) + " MD"));
        b.append(kpi("Custom code", num(est.getCodeMD()) + " MD"));
        b.append(kpi("Integration", num(est.getInventoryMD()) + " MD"));
        b.append("</div>");

        b.append("<h2>Disposition (SAP decision tree)</h2>");
        b.append("<table><tr><th>Disposition</th><th>Items</th><th>MD</th></tr>");
        for (Disposition d : Disposition.values()) {
            Integer c = est.getCountByDisposition().get(d);
            if (c == null) continue;
            Double md = est.getMdByDisposition().getOrDefault(d, 0.0);
            b.append(row(d.name(), String.valueOf(c), num(md)));
        }
        b.append("</table>");

        b.append("<h2>Work category</h2>");
        b.append("<table><tr><th>Category</th><th>Items</th><th>MD</th></tr>");
        for (Map.Entry<String, Double> e : est.getMdByCategory().entrySet()) {
            int c = est.getCountByCategory().getOrDefault(e.getKey(), 0);
            b.append(row(esc(e.getKey()), String.valueOf(c), num(e.getValue())));
        }
        b.append("</table>");

        b.append("<h2>Wave / phase plan</h2>");
        b.append("<table><tr><th>Wave</th><th>Items</th><th>MD</th></tr>");
        for (ProjectEstimate.Wave wv : est.getWaves()) {
            b.append(row(esc(wv.name), String.valueOf(wv.itemCount), num(wv.md)));
        }
        b.append("</table>");

        b.append("<h2>Effort size mix</h2>");
        b.append("<table><tr><th>Size</th><th>Items</th></tr>");
        for (String size : new String[]{"S", "M", "L", "XL"}) {
            Integer c = est.getCountBySize().get(size);
            if (c != null) b.append(row(size, String.valueOf(c), null));
        }
        b.append("</table>");

        b.append("<h2>Role split</h2>");
        b.append("<table><tr><th>Role</th><th>MD</th></tr>");
        for (Map.Entry<String, Double> e : est.getMdByRole().entrySet()) {
            b.append(row(esc(e.getKey()), num(e.getValue()), null));
        }
        b.append("</table>");

        b.append("<p class=\"muted\" style=\"margin-top:28px\">Generated by SAP Transformation Assessment (Clean Core Analyzer). "
                + "Estimates are indicative and intended for presales scoping.</p>");
        b.append("</body></html>");
        return b.toString();
    }

    private String kpi(String label, String value) {
        return "<span class=\"kpi\">" + esc(label) + "<b>" + esc(value) + "</b></span>";
    }

    private String row(String a, String c1, String c2) {
        StringBuilder s = new StringBuilder("<tr><td>").append(a).append("</td><td>").append(c1).append("</td>");
        if (c2 != null) s.append("<td>").append(c2).append("</td>");
        s.append("</tr>");
        return s.toString();
    }

    private static String num(double v) {
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private static String str(String s) { return s == null ? "" : s; }

    private static String csv(String a, String b, String c) {
        return safe(a) + ";" + safe(b) + ";" + safe(c);
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ');
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
