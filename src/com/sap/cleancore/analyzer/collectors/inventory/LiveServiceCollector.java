package com.sap.cleancore.analyzer.collectors.inventory;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.inventory.InventoryCategory;
import com.sap.cleancore.analyzer.model.inventory.InventoryItem;
import com.sap.cleancore.analyzer.utils.AdtObjectRefParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort, secondary inventory tier that queries a CONNECTED system over
 * ADT REST for service-related objects. Reuses the same proven endpoint as
 * {@link com.sap.cleancore.analyzer.collectors.ZObjectCollector} —
 * {@code /sap/bc/adt/repository/informationsystem/search} — which is the only
 * service catalogue ADT exposes cleanly across releases.
 *
 * <p>Discovers custom (Z* / Y*) OData service definitions (SRVD) and service
 * bindings (SRVB). Everything else (SICF tree, SOAP web services, /IWFND
 * gateway registrations) lives in Basis tables that ADT does not expose, so
 * those stay with the offline extract path.
 *
 * <p>Never throws: any failure (not connected, endpoint missing on older
 * systems, parse error) yields an empty list so the run is never aborted.
 */
public class LiveServiceCollector {

    /** ADT TADIR codes (with /<role> suffix) for OData service objects. */
    private static final String[] SERVICE_TYPES = {
            "SRVD/SRV",  // service definitions
            "SRVB/SVB"   // service bindings (OData V2/V4)
    };

    /** Returns custom OData service items, or an empty list on any problem. */
    public List<InventoryItem> collect() {
        List<InventoryItem> out = new ArrayList<>();
        AdtConnectionService adt = AdtConnectionService.getInstance();
        if (!adt.isConnected()) return out;

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String type : SERVICE_TYPES) {
            for (String query : new String[] { "Z*", "Y*" }) {
                try {
                    collectType(adt, type, query, seen, out);
                } catch (Throwable ignored) {
                    // Older systems may not know SRVD/SRVB — skip silently.
                }
            }
        }
        return out;
    }

    private void collectType(AdtConnectionService adt, String objType, String query,
                             java.util.Set<String> seen, List<InventoryItem> out) throws Exception {
        String path = "/sap/bc/adt/repository/informationsystem/search"
                + "?operation=quickSearch&maxResults=500"
                + "&query=" + urlEncode(query)
                + "&objectType=" + urlEncode(objType);
        String xml = adt.get(path, "application/xml");
        if (xml == null) return;
        for (AdtObjectRefParser.Ref r : AdtObjectRefParser.parse(xml)) {
            String key = r.name.toUpperCase(java.util.Locale.ROOT);
            if (!seen.add(key)) continue;

            InventoryItem it = new InventoryItem(InventoryCategory.ODATA_SERVICE, r.name);
            it.setProtocol("OData");
            it.setDirection("INBOUND");
            if (r.type != null) it.putAttribute("adtType", r.type);
            if (r.pkg != null && !r.pkg.isEmpty()) it.putAttribute("package", r.pkg);
            it.putAttribute("source", "ADT live");
            out.add(it);
        }
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
