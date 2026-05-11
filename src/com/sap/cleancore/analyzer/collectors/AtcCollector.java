package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.ZObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional collector: runs an ATC check variant against the discovered objects
 * and returns findings keyed by ZObject name. Used only when
 * SystemCapabilities.atcAvailable is true.
 *
 * The exact ATC REST surface varies (S/4 has /sap/bc/adt/atc/runs, EHP8 has
 * /sap/bc/adt/atc/checkruns). We use the documented S/4HANA shape and degrade
 * gracefully on HTTP errors.
 */
public class AtcCollector {

    private static final Pattern FINDING_RE = Pattern.compile(
            "<atc:finding[^>]*atc:objectName=\"([^\"]+)\"[^>]*atc:priority=\"([^\"]+)\"[^>]*atc:checkId=\"([^\"]+)\"[^>]*atc:messageId=\"([^\"]+)\"[^>]*atc:line=\"([^\"]+)\"[^>]*atc:message=\"([^\"]+)\"",
            Pattern.DOTALL);

    public Map<String, List<Finding>> run(String variantName, List<ZObject> objects) {
        Map<String, List<Finding>> byName = new HashMap<>();
        AdtConnectionService adt = AdtConnectionService.getInstance();
        if (!adt.isConnected() || objects == null || objects.isEmpty()) return byName;

        // Try to trigger a run; if any step fails, we return an empty map and
        // the engine will fall back to static analyzers.
        try {
            String runId = startRun(adt, variantName, objects);
            if (runId == null) return byName;
            String resultsXml = adt.get("/sap/bc/adt/atc/worklists/" + runId, "application/xml");
            return parseFindings(resultsXml);
        } catch (Exception e) {
            return byName;
        }
    }

    private String startRun(AdtConnectionService adt, String variant, List<ZObject> objects) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("<atc:run xmlns:atc=\"http://www.sap.com/adt/atc\"");
            body.append(" atc:checkVariant=\"").append(variant != null ? variant : "S4HANA_READINESS_REMOTE").append("\">");
            body.append("<atc:objectSets>");
            for (ZObject z : objects) {
                if (z.getName() == null) continue;
                body.append("<atc:objectSet><atc:objectName>")
                        .append(z.getName().toLowerCase(Locale.ROOT))
                        .append("</atc:objectName></atc:objectSet>");
            }
            body.append("</atc:objectSets></atc:run>");
            String resp = adt.post("/sap/bc/adt/atc/runs", "application/atc.run.v1+xml", body.toString());
            Matcher m = Pattern.compile("atc:runId=\"([^\"]+)\"").matcher(resp);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, List<Finding>> parseFindings(String xml) {
        Map<String, List<Finding>> out = new HashMap<>();
        if (xml == null) return out;
        Matcher m = FINDING_RE.matcher(xml);
        while (m.find()) {
            String objName = m.group(1).toUpperCase(Locale.ROOT);
            String prio = m.group(2);
            String checkId = m.group(3);
            String messageId = m.group(4);
            String line = m.group(5);
            String msg = m.group(6);

            Finding f = new Finding(checkId + "/" + messageId, msg, mapSeverity(prio), Finding.Source.ATC);
            try { f.setLine(Integer.parseInt(line)); } catch (Exception ignored) {}
            f.setCategory("ATC " + prio);

            out.computeIfAbsent(objName, k -> new ArrayList<>()).add(f);
        }
        return out;
    }

    private Finding.Severity mapSeverity(String prio) {
        if (prio == null) return Finding.Severity.INFO;
        switch (prio.toUpperCase(Locale.ROOT)) {
            case "1": case "ERROR": return Finding.Severity.ERROR;
            case "2": case "WARNING": return Finding.Severity.WARNING;
            default: return Finding.Severity.INFO;
        }
    }
}
