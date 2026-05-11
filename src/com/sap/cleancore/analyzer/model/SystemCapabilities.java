package com.sap.cleancore.analyzer.model;

/**
 * What a target SAP system supports. Detected once per analysis run.
 *
 * - R/3 (ECC 6.0 < EHP8): typically only TADIR + REPOSRC are reachable.
 * - ECC 6.0 EHP8+: may have ATC remote.
 * - S/4HANA: full readiness check + released-objects API.
 */
public class SystemCapabilities {

    private boolean tadirAccess;
    private boolean sourceAccess;
    private boolean atcAvailable;
    private boolean readinessCheckAvailable;
    private boolean releasedObjectsAvailable;
    private String systemVersion;       // e.g. "740 SP25", "S/4 2023"
    private String detectedAt;

    public boolean isTadirAccess() { return tadirAccess; }
    public void setTadirAccess(boolean tadirAccess) { this.tadirAccess = tadirAccess; }

    public boolean isSourceAccess() { return sourceAccess; }
    public void setSourceAccess(boolean sourceAccess) { this.sourceAccess = sourceAccess; }

    public boolean isAtcAvailable() { return atcAvailable; }
    public void setAtcAvailable(boolean atcAvailable) { this.atcAvailable = atcAvailable; }

    public boolean isReadinessCheckAvailable() { return readinessCheckAvailable; }
    public void setReadinessCheckAvailable(boolean readinessCheckAvailable) {
        this.readinessCheckAvailable = readinessCheckAvailable;
    }

    public boolean isReleasedObjectsAvailable() { return releasedObjectsAvailable; }
    public void setReleasedObjectsAvailable(boolean releasedObjectsAvailable) {
        this.releasedObjectsAvailable = releasedObjectsAvailable;
    }

    public String getSystemVersion() { return systemVersion; }
    public void setSystemVersion(String systemVersion) { this.systemVersion = systemVersion; }

    public String getDetectedAt() { return detectedAt; }
    public void setDetectedAt(String detectedAt) { this.detectedAt = detectedAt; }

    /**
     * Human-readable explanation of WHY probes failed (if any did).
     * AnalysisService surfaces this to the UI when the run cannot proceed.
     */
    private String diagnosticMessage;
    public String getDiagnosticMessage() { return diagnosticMessage; }
    public void setDiagnosticMessage(String diagnosticMessage) { this.diagnosticMessage = diagnosticMessage; }

    /** True when zero capabilities were detected — analysis can't proceed. */
    public boolean isAllUnavailable() {
        return !tadirAccess && !sourceAccess && !atcAvailable
                && !readinessCheckAvailable && !releasedObjectsAvailable;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("TADIR ").append(tadirAccess ? "OK" : "X").append(" | ");
        sb.append("Source ").append(sourceAccess ? "OK" : "X").append(" | ");
        sb.append("ATC ").append(atcAvailable ? "OK" : "X").append(" | ");
        sb.append("Readiness ").append(readinessCheckAvailable ? "OK" : "X").append(" | ");
        sb.append("Released ").append(releasedObjectsAvailable ? "OK" : "X");
        if (systemVersion != null) sb.append(" | v").append(systemVersion);
        return sb.toString();
    }
}
