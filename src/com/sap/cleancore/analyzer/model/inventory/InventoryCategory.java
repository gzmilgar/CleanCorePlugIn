package com.sap.cleancore.analyzer.model.inventory;

import java.util.Locale;

/**
 * Categories of integration / system-inventory objects relevant to a
 * transformation assessment. These typically live in Basis config tables that
 * are not exposed by ADT REST, so they are sourced primarily from an extract
 * file (see {@code resources/extract/Z_TRANSFORM_INVENTORY.abap.txt}).
 */
public enum InventoryCategory {
    RFC_DESTINATION,   // SM59 / RFCDES
    IDOC_PARTNER,      // WE20 / EDPP1, EDP13, EDP21
    SICF_SERVICE,      // SICF / ICFSERVICE, ICFSERVLOC
    ODATA_SERVICE,     // /IWFND/* OData services
    WEB_SERVICE,       // SOAMANAGER / SRT_*
    PI_PO_INTERFACE,   // PI/PO interfaces (→ Integration Suite)
    BTP_INTEGRATION,   // BTP / Integration Suite artifacts
    UNKNOWN;

    public static InventoryCategory fromString(String s) {
        if (s == null) return UNKNOWN;
        try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return UNKNOWN; }
    }
}
