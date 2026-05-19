package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.AnalysisFilter;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Harvests Z-star and Y-star objects from sources already cached in this
 * Eclipse session, with NO HTTP traffic to the SAP system.
 *
 * The ADT Project Explorer tree is NOT exposed through the standard Eclipse
 * IResource API — it is rendered by ADT's own content provider, so
 * IProject.members() returns nothing useful. We therefore use a two-tier
 * harvest strategy:
 *
 *   Tier 1 — Open editors:  every Z file the user has opened (Analyze Current
 *            File workflow extended). Reliable and HTTP-free.
 *   Tier 2 — IProject.members():  works only on plain (non-ADT) Eclipse
 *            installations. Kept as a defensive fallback; usually empty for
 *            ADT projects.
 *
 * AnalysisFilter is applied after collection (mode + Y opt-in + package
 * prefix), so the wizard's filter contract stays the same.
 */
public class WorkspaceAdtCollector {

    /**
     * Last collection's diagnostic summary — populated on every call so
     * AnalysisService can include it in the user-facing error message when
     * the run produces zero objects.
     */
    private final List<String> lastEditorNames = new ArrayList<>();
    private int lastFilteredOut = 0;

    public List<String> getLastEditorNames() { return lastEditorNames; }
    public int getLastFilteredOut() { return lastFilteredOut; }

    public List<ZObject> collect(AnalysisFilter filter) {
        List<ZObject> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        lastEditorNames.clear();
        lastFilteredOut = 0;

        // ---- Tier 1: open editors (the path that actually works for ADT) ----
        collectFromOpenEditors(filter, out, seen);

        // ---- Tier 2: standard IResource fallback ----
        IProject project = AdtConnectionService.getInstance().getAdtProject();
        if (project != null && project.isOpen()) {
            try {
                walk(project.members(), filter, out, seen);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /**
     * Walks every open editor in every Workbench window/page and turns each
     * editor's file name into a candidate ZObject. ADT names files like
     * "ZNT_000_CL_001.aclass" / "ZBP_REPORT.prog" — we use the suffix to map
     * the object type.
     */
    private void collectFromOpenEditors(AnalysisFilter filter,
                                         List<ZObject> out, Set<String> seen) {
        // PlatformUI APIs must be touched on the UI thread; if we're already
        // there (handler context), call directly to avoid syncExec dead-locks.
        try {
            org.eclipse.swt.widgets.Display current = org.eclipse.swt.widgets.Display.getCurrent();
            if (current != null) {
                doCollectOnUiThread(filter, out, seen);
                return;
            }
            org.eclipse.swt.widgets.Display display =
                    org.eclipse.swt.widgets.Display.getDefault();
            if (display == null) return;
            display.syncExec(() -> doCollectOnUiThread(filter, out, seen));
        } catch (Throwable ignored) {}
    }

    private void doCollectOnUiThread(AnalysisFilter filter,
                                      List<ZObject> out, Set<String> seen) {
        try {
            IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
            for (IWorkbenchWindow win : windows) {
                if (win == null) continue;
                for (IWorkbenchPage page : win.getPages()) {
                    if (page == null) continue;
                    for (IEditorReference ref : page.getEditorReferences()) {
                        if (ref == null) continue;
                        String editorName = ref.getName();
                        if (editorName == null || editorName.isEmpty()) continue;
                        lastEditorNames.add(editorName);
                        ZObject z = fromEditorName(editorName);
                        if (z == null) continue; // not an ABAP file extension
                        if (!matchesFilter(z, filter)) { lastFilteredOut++; continue; }
                        if (seen.add(z.getName().toUpperCase(Locale.ROOT))) out.add(z);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Translates an ADT-style editor name (NAME.ext) into a ZObject.
     * Returns null when the file is not a recognised ABAP source artefact.
     */
    private ZObject fromEditorName(String editorName) {
        // Strip directory part if any
        int slash = Math.max(editorName.lastIndexOf('/'), editorName.lastIndexOf('\\'));
        String base = slash >= 0 ? editorName.substring(slash + 1) : editorName;

        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext  = dot > 0 ? base.substring(dot + 1).toLowerCase(Locale.ROOT) : "";

        ZObjectType type = mapExtensionType(ext);
        if (type == ZObjectType.UNKNOWN) return null;

        ZObject z = new ZObject();
        z.setName(stem.toUpperCase(Locale.ROOT));
        z.setType(type);
        // package is unknown from filename alone — left null so PACKAGE_PREFIX
        // filter only matches when user provides "*"
        return z;
    }

    private ZObjectType mapExtensionType(String ext) {
        if (ext == null || ext.isEmpty()) return ZObjectType.UNKNOWN;
        switch (ext) {
            case "aclass":  // class source
            case "clas":
                return ZObjectType.Z_CLASS;
            case "asinc":   // includes
            case "reps":
                return ZObjectType.Z_INCLUDE;
            case "prog":    // reports
            case "asprog":
                return ZObjectType.Z_REPORT;
            case "fugr":
            case "asfunc":
                return ZObjectType.Z_FUNCTION_MODULE;
            case "intf":
            case "asintf":
                return ZObjectType.Z_INTERFACE;
            case "tabl":
            case "asddic":
                return ZObjectType.Z_DDIC_TABLE;
            case "ddls":
            case "asddls":
                return ZObjectType.Z_CDS_VIEW;
            case "enho":
                return ZObjectType.Z_ENHANCEMENT;
            default:
                return ZObjectType.UNKNOWN;
        }
    }

    // ---- Tier 2 helpers (IResource walk, kept for non-ADT Eclipses) ----

    private void walk(IResource[] members, AnalysisFilter filter,
                      List<ZObject> out, Set<String> seen) {
        if (members == null) return;
        for (IResource res : members) {
            if (res == null) continue;
            ZObject z = tryAsAdtObject(res);
            if (z != null && matchesFilter(z, filter)
                    && seen.add(z.getName().toUpperCase(Locale.ROOT))) {
                out.add(z);
            }
            try {
                Method m = res.getClass().getMethod("members");
                Object child = m.invoke(res);
                if (child instanceof IResource[]) walk((IResource[]) child, filter, out, seen);
            } catch (Throwable ignored) {}
        }
    }

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

        boolean isZ = name.startsWith("Z");
        boolean isY = name.startsWith("Y");
        if (!isZ && !isY) return false;
        if (isY && !filter.isIncludeY()) return false;

        switch (filter.getMode()) {
            case SINGLE_OBJECT:
                return filter.getSingleObjectName() != null
                        && name.equalsIgnoreCase(filter.getSingleObjectName().trim());
            case PACKAGE_PREFIX:
                // Open-editor harvest has no package metadata; fall through to
                // name-based prefix matching so the wizard still gives useful
                // filtering (e.g. ZNT_000 matches ZNT_000_*).
                if (filter.getPackagePrefixes() == null || filter.getPackagePrefixes().isEmpty()) {
                    // Empty prefix list → treat like FULL (don't lose findings)
                    return true;
                }
                for (String prefix : filter.getPackagePrefixes()) {
                    if (prefix == null) continue;
                    String trimmed = prefix.trim();
                    if (trimmed.isEmpty()) continue;
                    // Lone "*" means "match anything"
                    if ("*".equals(trimmed)) return true;
                    String p = trimmed.toUpperCase(Locale.ROOT);
                    // Strip ONE trailing "*"; we treat the prefix as a startsWith
                    if (p.endsWith("*")) p = p.substring(0, p.length() - 1);
                    if (p.isEmpty()) return true;
                    if (z.getDevClass() != null
                            && z.getDevClass().toUpperCase(Locale.ROOT).startsWith(p)) return true;
                    if (name.startsWith(p)) return true;
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
