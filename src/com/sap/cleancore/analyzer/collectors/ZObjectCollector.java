package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.AnalysisFilter;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the Z* object inventory from a system via the ADT repository
 * information system endpoint:
 *
 *   GET /sap/bc/adt/repository/informationsystem/search?operation=quickSearch
 *       &maxResults=N&query=Z*&objectType=PROG/P
 *
 * Filter-aware: accepts an AnalysisFilter that controls scan mode
 * (FULL / PACKAGE_PREFIX / SINGLE_OBJECT), object-type subset, max results.
 */
public class ZObjectCollector {

    private static final Pattern OBJ_REF = Pattern.compile(
            "<adtcore:objectReference[^>]*adtcore:name=\"([^\"]+)\"[^>]*adtcore:type=\"([^\"]+)\"[^>]*?(?:adtcore:packageName=\"([^\"]*)\")?",
            Pattern.DOTALL);

    /** New entry point. */
    public List<ZObject> collect(AnalysisFilter filter) throws Exception {
        if (filter == null) filter = AnalysisFilter.fullScan();
        AdtConnectionService adt = AdtConnectionService.getInstance();

        // SINGLE_OBJECT fast path: skip search, fetch by name only.
        if (filter.getMode() == AnalysisFilter.Mode.SINGLE_OBJECT) {
            return collectSingle(adt, filter);
        }

        List<ZObject> all = new ArrayList<>();
        List<String> types = filter.getIncludedObjectTypes().isEmpty()
                ? Arrays.asList(AnalysisFilter.ALL_OBJECT_TYPES)
                : new ArrayList<>(filter.getIncludedObjectTypes());
        int max = filter.getMaxResults() > 0 ? filter.getMaxResults() : 1000;

        for (String objType : types) {
            try {
                all.addAll(searchType(adt, objType, "Z*", max));
                if (filter.isIncludeY()) {
                    all.addAll(searchType(adt, objType, "Y*", max));
                }
            } catch (Exception e) {
                // Some types may not exist on older systems; continue.
            }
        }

        if (filter.getMode() == AnalysisFilter.Mode.PACKAGE_PREFIX
                && !filter.getPackagePrefixes().isEmpty()) {
            List<ZObject> filtered = new ArrayList<>();
            for (ZObject z : all) {
                for (String p : filter.getPackagePrefixes()) {
                    if (matchesPackage(z.getDevClass(), p)) { filtered.add(z); break; }
                }
            }
            return filtered;
        }
        return all;
    }

    /** Backwards-compatible overload for old callers using a package list. */
    public List<ZObject> collect(List<String> packageFilter) throws Exception {
        AnalysisFilter f = new AnalysisFilter();
        if (packageFilter != null && !packageFilter.isEmpty()) {
            f.setMode(AnalysisFilter.Mode.PACKAGE_PREFIX);
            f.setPackagePrefixes(packageFilter);
        }
        return collect(f);
    }

    private List<ZObject> collectSingle(AdtConnectionService adt, AnalysisFilter filter) throws Exception {
        String type = filter.getSingleObjectType();
        String name = filter.getSingleObjectName();
        if (name == null || name.isEmpty()) return new ArrayList<>();
        // Use search with the exact name as query — works regardless of object type quirks.
        String query = name;
        String searchType = (type != null && !type.isEmpty()) ? type : "PROG/P";
        List<ZObject> out = new ArrayList<>();
        try {
            out.addAll(searchType(adt, searchType, query, 50));
        } catch (Exception e) {
            // ignore
        }
        // Keep only exact-name matches (case-insensitive).
        List<ZObject> exact = new ArrayList<>();
        for (ZObject z : out) {
            if (z.getName() != null && z.getName().equalsIgnoreCase(name)) exact.add(z);
        }
        return exact;
    }

    private List<ZObject> searchType(AdtConnectionService adt, String objType, String query, int max) throws Exception {
        String path = "/sap/bc/adt/repository/informationsystem/search"
                + "?operation=quickSearch&maxResults=" + max
                + "&query=" + urlEncode(query)
                + "&objectType=" + urlEncode(objType);
        String xml = adt.get(path, "application/xml");
        List<ZObject> out = new ArrayList<>();
        Matcher m = OBJ_REF.matcher(xml);
        while (m.find()) {
            String name = m.group(1);
            String type = m.group(2);
            String pkg = m.group(3);
            ZObject z = new ZObject();
            z.setName(name);
            z.setType(mapAdtType(type));
            z.setDevClass(pkg);
            out.add(z);
        }
        return out;
    }

    private ZObjectType mapAdtType(String adtType) {
        if (adtType == null) return ZObjectType.UNKNOWN;
        String t = adtType.toUpperCase();
        if (t.startsWith("PROG"))   return ZObjectType.Z_REPORT;
        if (t.startsWith("FUGR"))   return ZObjectType.Z_FUNCTION_MODULE;
        if (t.startsWith("FUNC"))   return ZObjectType.Z_FUNCTION_MODULE;
        if (t.startsWith("CLAS"))   return ZObjectType.Z_CLASS;
        if (t.startsWith("INTF"))   return ZObjectType.Z_INTERFACE;
        if (t.startsWith("TABL"))   return ZObjectType.Z_DDIC_TABLE;
        if (t.startsWith("DDLS"))   return ZObjectType.Z_CDS_VIEW;
        if (t.startsWith("ENHO"))   return ZObjectType.Z_ENHANCEMENT;
        if (t.startsWith("SXCI"))   return ZObjectType.Z_BADI_IMPL;
        if (t.startsWith("DOMA"))   return ZObjectType.Z_DOMAIN;
        if (t.startsWith("DTEL"))   return ZObjectType.Z_DATA_ELEMENT;
        return ZObjectType.UNKNOWN;
    }

    private boolean matchesPackage(String pkg, String filter) {
        if (pkg == null) return false;
        if (filter == null || filter.isEmpty()) return true;
        if (filter.endsWith("*")) {
            return pkg.toUpperCase().startsWith(filter.substring(0, filter.length() - 1).toUpperCase());
        }
        return pkg.equalsIgnoreCase(filter);
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
