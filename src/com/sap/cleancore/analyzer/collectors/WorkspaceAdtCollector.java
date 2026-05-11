package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.AnalysisFilter;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Walks the Eclipse ABAP project tree (already populated by ADT in the
 * Project Explorer) and harvests Z-star and Y-star objects without making a
 * single HTTP call to the SAP system. This is the path that works behind
 * SAProuter / VPN-less networks where ADT itself can manually fetch nodes but
 * our plain HttpURLConnection cannot.
 *
 * Strategy: project.members(DEPTH_INFINITE) returns IResources that ADT
 * advertises. Each ABAP object is exposed with an IAdtObjectReference
 * adapter; we reach the name/type/package via reflection so we don't depend
 * on com.sap.adt.* classes being on our compile classpath.
 *
 * Returns an empty list — with a clear log line — when ADT API isn't
 * reachable; AnalysisService falls back to the HTTP-based ZObjectCollector
 * automatically in that case.
 */
public class WorkspaceAdtCollector {

    public List<ZObject> collect(AnalysisFilter filter) {
        List<ZObject> out = new ArrayList<>();
        IProject project = AdtConnectionService.getInstance().getAdtProject();
        if (project == null || !project.isOpen()) return out;

        try {
            IResource[] members = project.members();
            walk(members, filter, out, new HashSet<>());
        } catch (Throwable t) {
            // best-effort; AnalysisService catches an empty list and falls back
        }
        return out;
    }

    private void walk(IResource[] members, AnalysisFilter filter,
                      List<ZObject> out, Set<String> seen) {
        if (members == null) return;
        for (IResource res : members) {
            if (res == null) continue;
            ZObject z = tryAsAdtObject(res);
            if (z != null && matchesFilter(z, filter) && seen.add(z.getName().toUpperCase(Locale.ROOT))) {
                out.add(z);
            }
            // Recurse into containers
            try {
                Method m = res.getClass().getMethod("members");
                Object child = m.invoke(res);
                if (child instanceof IResource[]) walk((IResource[]) child, filter, out, seen);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Try to read an IAdtObjectReference adapter off the resource. Falls back
     * to the resource name if the adapter isn't found — useful for non-ADT
     * Eclipse installations during development.
     */
    private ZObject tryAsAdtObject(IResource res) {
        try {
            Class<?> refClass = Class.forName("com.sap.adt.tools.core.model.IAdtObjectReference");
            Object ref = res.getAdapter(refClass);
            if (ref == null) return null;
            String name = (String) invoke(ref, "getName");
            String type = (String) invoke(ref, "getType");
            String pkg = (String) invoke(ref, "getPackageName");
            if (name == null || name.isEmpty()) return null;
            ZObject z = new ZObject();
            z.setName(name);
            z.setType(mapAdtType(type));
            z.setDevClass(pkg);
            return z;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean matchesFilter(ZObject z, AnalysisFilter filter) {
        if (filter == null) return true;
        String name = z.getName() != null ? z.getName().toUpperCase(Locale.ROOT) : "";

        // Only Z*/Y* names (and respect includeY)
        boolean isZ = name.startsWith("Z");
        boolean isY = name.startsWith("Y");
        if (!isZ && !isY) return false;
        if (isY && !filter.isIncludeY()) return false;

        // Mode handling
        switch (filter.getMode()) {
            case SINGLE_OBJECT:
                return filter.getSingleObjectName() != null
                        && name.equalsIgnoreCase(filter.getSingleObjectName().trim());
            case PACKAGE_PREFIX:
                if (z.getDevClass() == null) return false;
                String pkg = z.getDevClass().toUpperCase(Locale.ROOT);
                for (String prefix : filter.getPackagePrefixes()) {
                    if (prefix == null || prefix.isEmpty()) continue;
                    String p = prefix.toUpperCase(Locale.ROOT);
                    if (p.endsWith("*")) {
                        if (pkg.startsWith(p.substring(0, p.length() - 1))) return true;
                    } else if (pkg.equalsIgnoreCase(prefix)) {
                        return true;
                    }
                }
                return false;
            case FULL:
            default:
                return true;
        }
    }

    private ZObjectType mapAdtType(String adtType) {
        if (adtType == null) return ZObjectType.UNKNOWN;
        String t = adtType.toUpperCase(Locale.ROOT);
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

    private Object invoke(Object target, String name) {
        try {
            Method m = target.getClass().getMethod(name);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
