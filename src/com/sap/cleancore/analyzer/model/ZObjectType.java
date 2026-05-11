package com.sap.cleancore.analyzer.model;

/**
 * Categorizes custom (Z*) ABAP objects. Aligned with TADIR object types
 * but normalized to clean-core-relevant buckets.
 */
public enum ZObjectType {
    Z_FUNCTION_MODULE("FUNC"),
    Z_REPORT("PROG"),
    Z_CLASS("CLAS"),
    Z_INTERFACE("INTF"),
    Z_DDIC_TABLE("TABL"),
    Z_DDIC_STRUCTURE("STRU"),
    Z_DOMAIN("DOMA"),
    Z_DATA_ELEMENT("DTEL"),
    Z_CDS_VIEW("DDLS"),
    Z_BADI_IMPL("SXCI"),
    Z_ENHANCEMENT("ENHO"),
    Z_INCLUDE("REPS"),
    MODIFICATION("MODI"),
    UNKNOWN("?");

    private final String tadirCode;

    ZObjectType(String tadirCode) {
        this.tadirCode = tadirCode;
    }

    public String tadirCode() {
        return tadirCode;
    }

    public static ZObjectType fromTadir(String code) {
        if (code == null) return UNKNOWN;
        for (ZObjectType t : values()) {
            if (t.tadirCode.equalsIgnoreCase(code)) return t;
        }
        return UNKNOWN;
    }
}
