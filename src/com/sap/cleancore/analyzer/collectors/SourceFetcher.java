package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;

import java.util.Locale;

/**
 * Fetches the ABAP source text for a single ZObject via ADT REST.
 * URI patterns differ per object type; we route here.
 */
public class SourceFetcher {

    public String fetch(ZObject z) {
        if (z == null || z.getName() == null) return null;
        AdtConnectionService adt = AdtConnectionService.getInstance();
        String name = z.getName().toLowerCase(Locale.ROOT);
        String path = uriFor(z.getType(), name);
        if (path == null) return null;
        try {
            return adt.get(path, "text/plain");
        } catch (Exception e) {
            return null;
        }
    }

    private String uriFor(ZObjectType type, String name) {
        if (type == null) return null;
        switch (type) {
            case Z_REPORT:
                return "/sap/bc/adt/programs/programs/" + name + "/source/main";
            case Z_INCLUDE:
                return "/sap/bc/adt/programs/includes/" + name + "/source/main";
            case Z_CLASS:
                return "/sap/bc/adt/oo/classes/" + name + "/source/main";
            case Z_INTERFACE:
                return "/sap/bc/adt/oo/interfaces/" + name + "/source/main";
            case Z_FUNCTION_MODULE:
                // Function module URI needs group; if name follows ZFG_<grp>_<fm> we can split.
                // Pragmatic fallback: hit the function-group source (good enough for grep-style analysis).
                return "/sap/bc/adt/functions/groups/" + name + "/source/main";
            case Z_CDS_VIEW:
                return "/sap/bc/adt/ddic/ddl/sources/" + name + "/source/main";
            case Z_ENHANCEMENT:
                return "/sap/bc/adt/enhancements/elements/" + name + "/source/main";
            default:
                return null;
        }
    }
}
