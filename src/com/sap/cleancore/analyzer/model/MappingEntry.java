package com.sap.cleancore.analyzer.model;

/**
 * A single legacy -> modern API mapping entry.
 *
 * Examples:
 *   BAPI_SALESORDER_CREATEFROMDAT2 (BAPI) -> API_SALES_ORDER_SRV (OData)
 *   ALV_GRID_DISPLAY (FM)                 -> CL_SALV_TABLE (CLASS)
 *   READ_TEXT (FM)                        -> Released FM 'READ_TEXT' (still released - flag as KEEP)
 */
public class MappingEntry {

    public enum LegacyType { FM, BAPI, CLASS, INTERFACE, REPORT, TABLE, CDS, INCLUDE, OTHER }
    public enum ModernType { FM, API_ODATA, API_REST, RAP_BO, CDS_VIEW, CLASS, KEEP_AS_IS, NO_REPLACEMENT }
    public enum ReleaseState { RELEASED, DEPRECATED, NOT_RELEASED, UNKNOWN, REMOVED }
    public enum Source { BASELINE, API_HUB, USER }

    private String legacyName;
    private LegacyType legacyType;
    private String modernName;
    private ModernType modernType;
    private ReleaseState releaseState = ReleaseState.UNKNOWN;
    private Source source = Source.BASELINE;
    private boolean userOverride;
    private String notes;
    private String documentationUrl;

    public MappingEntry() {}

    public MappingEntry(String legacyName, LegacyType legacyType,
                        String modernName, ModernType modernType) {
        this.legacyName = legacyName;
        this.legacyType = legacyType;
        this.modernName = modernName;
        this.modernType = modernType;
    }

    public String key() {
        return (legacyType != null ? legacyType.name() : "?") + "::" + (legacyName != null ? legacyName.toUpperCase() : "");
    }

    public String getLegacyName() { return legacyName; }
    public void setLegacyName(String legacyName) { this.legacyName = legacyName; }

    public LegacyType getLegacyType() { return legacyType; }
    public void setLegacyType(LegacyType legacyType) { this.legacyType = legacyType; }

    public String getModernName() { return modernName; }
    public void setModernName(String modernName) { this.modernName = modernName; }

    public ModernType getModernType() { return modernType; }
    public void setModernType(ModernType modernType) { this.modernType = modernType; }

    public ReleaseState getReleaseState() { return releaseState; }
    public void setReleaseState(ReleaseState releaseState) { this.releaseState = releaseState; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public boolean isUserOverride() { return userOverride; }
    public void setUserOverride(boolean userOverride) { this.userOverride = userOverride; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDocumentationUrl() { return documentationUrl; }
    public void setDocumentationUrl(String documentationUrl) { this.documentationUrl = documentationUrl; }

    @Override
    public String toString() {
        return legacyType + " " + legacyName + " -> " + modernType + " " + modernName;
    }
}
