package com.sap.cleancore.analyzer.data;

import com.sap.cleancore.analyzer.model.SystemCapabilities;

/**
 * Probes a freshly-connected ADT system to discover which clean-core features
 * are reachable. Used to decide between ATC, Readiness, and the static fallback.
 *
 * Each probe is best-effort. When a probe fails, the first failure's message
 * is captured in diagnosticMessage so the UI can show the user WHY the system
 * appears unreachable (instead of silently returning empty results).
 */
public class CapabilityDetector {

    public SystemCapabilities detect() {
        SystemCapabilities c = new SystemCapabilities();
        c.setDetectedAt(java.time.LocalDateTime.now().toString());
        AdtConnectionService adt = AdtConnectionService.getInstance();
        if (!adt.isConnected()) {
            c.setDiagnosticMessage("No SAP system connected. Use the Connect button first.");
            return c;
        }

        String firstError = null;

        ProbeResult r;
        r = probe(adt, "/sap/bc/adt/repository/informationsystem/objectproperties?uri=/sap/bc/adt/programs/programs/SAPMV45A", "application/xml");
        c.setTadirAccess(r.ok);
        if (!r.ok && firstError == null) firstError = "TADIR probe: " + r.error;

        r = probe(adt, "/sap/bc/adt/programs/programs/SAPMV45A/source/main", "text/plain");
        c.setSourceAccess(r.ok);
        if (!r.ok && firstError == null) firstError = "Source probe: " + r.error;

        r = probe(adt, "/sap/bc/adt/atc/customizing", "application/xml");
        c.setAtcAvailable(r.ok);

        r = probe(adt, "/sap/bc/adt/cts/transportrequests", "application/xml");
        c.setReadinessCheckAvailable(r.ok);

        r = probe(adt, "/sap/bc/adt/releaseinformation", "application/xml");
        c.setReleasedObjectsAvailable(r.ok);

        c.setSystemVersion(probeVersion(adt));

        if (firstError != null) c.setDiagnosticMessage(firstError);
        return c;
    }

    private ProbeResult probe(AdtConnectionService adt, String path, String accept) {
        try {
            adt.get(path, accept);
            return ProbeResult.success();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            // 401/403 still means the endpoint exists; capability is technically there.
            if (msg.contains("401") || msg.contains("403")) return ProbeResult.success();
            return ProbeResult.failure(msg);
        }
    }

    private String probeVersion(AdtConnectionService adt) {
        try {
            String xml = adt.get("/sap/bc/adt/discovery", "application/xml");
            int s = xml.indexOf("release=\"");
            if (s > 0) {
                int e = xml.indexOf("\"", s + 9);
                if (e > s) return xml.substring(s + 9, e);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final class ProbeResult {
        final boolean ok;
        final String error;
        private ProbeResult(boolean ok, String error) { this.ok = ok; this.error = error; }
        static ProbeResult success() { return new ProbeResult(true, null); }
        static ProbeResult failure(String e) { return new ProbeResult(false, e); }
    }
}
