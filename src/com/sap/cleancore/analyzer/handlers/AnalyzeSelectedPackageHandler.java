package com.sap.cleancore.analyzer.handlers;

import com.sap.cleancore.analyzer.collectors.AnalysisService;
import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;
import com.sap.cleancore.analyzer.ui.CleanCoreAnalyzerView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

/**
 * Handler for "Clean Core - Analyze Selected Package" (Project Explorer popup).
 *
 * ADT Project Explorer nodes are not plain IResource instances - they are
 * ADT-specific tree elements (category folders like "Dictionary" / "Source
 * Code Library", and leaf nodes that adapt to IAdtObjectReference rather than
 * IFile). Children are loaded lazily by ADT's content provider, so we use
 * three strategies in order:
 *
 *   1) If the selection adapts to IResource, walk via IResource.members().
 *   2) Otherwise, locate the TreeViewer that owns the selection and ask its
 *      ITreeContentProvider for children (forces ADT's lazy load).
 *   3) Reflection - many ADT nodes expose getChildren()/members().
 *
 * For each Z-or-Y leaf we discover, we try to OPEN it in an editor (priming
 * ADT's source cache):
 *   - IFile leaves: IDE.openEditor(page, file).
 *   - IAdtObjectReference leaves: ADT navigation service via reflection
 *     (com.sap.adt.tools.core.ui.navigation.AdtNavigationServiceFactory).
 *
 * Source fetch + per-object analysis (static rules, obsolete-API detection,
 * modification check, mappings, effort) is then delegated to
 * AnalysisService.runOnObjects(...), and the resulting AnalysisRun is pushed
 * into the Clean Core Analyzer view's main table + Findings / Recommended
 * Mappings / Reasoning tabs (and as a flat summary into the Current File tab).
 *
 * Hard-capped at MAX_CHILDREN per run; cancellable Eclipse Job; all workbench
 * access hopped onto the SWT UI thread.
 */
public class AnalyzeSelectedPackageHandler extends AbstractHandler {

    private static final int MAX_CHILDREN = 50;

    /**
     * Folder labels (case-insensitive) under which we DO NOT descend - they
     * contain non-code data objects (Dictionary, Texts) that this analyzer
     * cannot do anything useful with. ABAP code lives under categories like
     * "Source Code Library", "Classes", "Programs", "Includes", "Function
     * Groups", "Interfaces", "Enhancement Implementations", "BAdI Implementa-
     * tions", "Exit Implementations".
     */
    private static final Set<String> SKIP_CATEGORY_LABELS = new HashSet<>();
    static {
        for (String s : new String[] {
                "Dictionary", "Sözlük", "Sozluk",
                "Texts", "Metinler",
                "Database Tables", "Veri Tabanı Tabloları",
                "Data Elements", "Veri Elementleri",
                "Domains", "Etki Alanları",
                "Structures", "Yapılar",
                "Table Types", "Tablo Tipleri",
                "Search Helps", "Arama Yardımları",
                "Lock Objects", "Kilit Nesneleri",
                "Views", "Görünümler",
                "Type Groups", "Tip Grupları",
                "Type Pools",
                // Non-text-source UI / runtime objects that open in native
                // SAP GUI maintenance screens (SmartForm Maintenance, SE93,
                // SE91, ...) and would block the analyser with modal dialogs.
                "Form Objects", "Form-Objekte", "Form Nesneleri",
                "SmartForms", "Smart Forms", "Akıllı Formlar",
                "SAPscript Forms", "SAPscript Formları",
                "Transactions", "İşlemler", "Transaktionen",
                "Number Ranges", "Numara Serileri", "Nummernkreise",
                "Message Classes", "İleti Sınıfları", "Nachrichtenklassen",
                "Authorization Objects", "Yetkilendirme Nesneleri",
                "Match Codes", "Match-Codes",
                "Web Dynpro Components", "Web Dynpro Bileşenleri",
                "OData Services",
                "Logical Databases", "Mantıksal Veri Tabanları",
                // Additional GUI-opening categories observed in the field
                "Area Menus", "Alan Menüleri", "Bereichsmenüs",
                "Transformations", "Dönüşümler", "Transformationen",
                "Service Definitions", "Servis Tanımları",
                "Service Bindings", "Servis Bağlamları",
                "Enterprise Services", "Kurumsal Servisler",
                "Enhancement Project", "Geliştirme Projeleri",
                "Enhancement Spots", "Geliştirme Noktaları",
                "Enhancements", "Geliştirmeler", "Erweiterungen",
                "Enhancement Implementations", "Geliştirme Uygulamaları",
                "Composite Enhancement Implementations",
                "Bileşik Geliştirme Uygulamaları",
                "Checkpoint Groups", "Kontrol Noktası Grupları",
                "Business Add-Ins", "İş Eklentileri",
                "IDoc Types", "IDoc Tipleri",
                "Form Painters",
                "GUI Status", "GUI Titles", "GUI Title"
        }) SKIP_CATEGORY_LABELS.add(s.toLowerCase(Locale.ROOT));
    }

    /**
     * Container labels whose IMMEDIATE children are individual ABAP source
     * objects with extractable text. When the BFS walker sees a TreeItem
     * whose parent label matches one of these, the TreeItem is treated as a
     * leaf (added as a candidate; not descended into) - so we don't dive
     * inside a program's own Events / Textelements sub-sections.
     */
    private static final Set<String> CODE_LEAF_CONTAINERS = new HashSet<>();
    static {
        for (String s : new String[] {
                "Classes", "Sınıflar",
                "Interfaces", "Arayüzler",
                "Programs", "Programlar",
                "Includes",
                "Function Groups", "Fonksiyon Grupları",
                "Function Modules",
                "Subroutine Pools",
                "Module Pools",
                "Type Pools"
                // Enhancement/BAdI/Exit Implementations excluded - they open
                // in SAP GUI maintenance screens (SE19 etc.) in many systems
                // and are skipped via SKIP_CATEGORY_LABELS instead.
        }) CODE_LEAF_CONTAINERS.add(s.toLowerCase(Locale.ROOT));
    }

    /** A discovered child object we want to analyse. */
    private static class Candidate {
        String name;          // Z*/Y* name, uppercase
        ZObjectType type;
        String devClass;      // nearest enclosing ADT package, e.g. "ZNT_020"
        IFile file;           // optional - present for IResource-backed nodes
        Object adtRef;        // optional - present for IAdtObjectReference nodes
        Object nodeData;      // raw TreeItem data, used as a last-resort opener
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        final Object selectedNode = firstSelectedElement(sel);
        if (selectedNode == null) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
                    "Clean Core - Analyze Selected Package",
                    "Right-click an ADT package node in Project Explorer and re-run this command.");
            return null;
        }

        final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        final IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        final String pkgLabel = nodeLabel(selectedNode);

        final TreeViewer[] viewerHolder = new TreeViewer[1];
        Display.getDefault().syncExec(() -> viewerHolder[0] = findTreeViewer(activePart));
        final TreeViewer viewer = viewerHolder[0];

        final IWorkbenchPart partRef = activePart;
        Job job = new Job("Clean Core - Analyze package " + pkgLabel) {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    return analyse(selectedNode, viewer, partRef, pkgLabel, window, monitor);
                } catch (OperationCanceledException oce) {
                    return Status.CANCEL_STATUS;
                } catch (Throwable t) {
                    return new Status(IStatus.ERROR, "com.sap.cleancore",
                            "Analyze Selected Package failed: " + t.getMessage(), t);
                }
            }
        };
        job.setUser(true);
        job.schedule();
        return null;
    }

    private IStatus analyse(Object selectedNode, TreeViewer viewer, IWorkbenchPart activePart,
                            String pkgLabel, IWorkbenchWindow window, IProgressMonitor monitor) {
        // 1) Walk the tree under the selected package and collect candidates.
        Diagnostics diag = new Diagnostics();
        List<Candidate> candidates = collectCandidates(selectedNode, viewer, diag);

        if (candidates.isEmpty()) {
            String detail = "Diagnostics:\n"
                  + "  - Selected node class: " + selectedNode.getClass().getName() + "\n"
                  + "  - TreeViewer found:   " + (viewer != null) + "\n"
                  + "  - Direct children:    " + diag.topLevelChildren + "\n"
                  + "  - Tree items walked:  " + diag.treeItemsWalked + "\n"
                  + "  - Skipped categories: " + diag.skippedCategories + "\n"
                  + "  - Descended into:     " + diag.descended + "\n"
                  + "  - Leaves seen:        " + diag.leaves
                  + " (Z/Y matched: " + diag.zyLeaves + ")\n";
            asyncInfo(window, "Clean Core - Analyze Selected Package",
                    "No Z*/Y* child objects were found under '" + pkgLabel + "'.\n\n"
                  + detail + "\n"
                  + "Possible causes:\n"
                  + "  - The package has no custom (Z* / Y*) objects.\n"
                  + "  - ADT has not loaded the children yet. Expand the package once in\n"
                  + "    Project Explorer, then re-run this command.");
            return Status.OK_STATUS;
        }

        boolean capped = false;
        if (candidates.size() > MAX_CHILDREN) {
            candidates = candidates.subList(0, MAX_CHILDREN);
            capped = true;
        }

        // 2) Sequential per-candidate flow: open -> analyse -> close.
        //    Processing one object at a time lets ADT's editor-creation
        //    background job complete before the next open call, which is
        //    far more reliable than the previous "open all 50 then batch
        //    analyse" approach (most fireOpen calls were silently failing
        //    when fired in rapid succession). Editors we open are also
        //    closed afterwards so the workbench stays uncluttered; editors
        //    the user had open before the run are preserved.
        boolean httpReachable =
                AdtConnectionService.getInstance().getAdtProject() != null;
        AnalysisRun run = new AnalysisRun();
        run.setStartedAt(java.time.LocalDateTime.now().toString());
        run.setSystemDisplay(AdtConnectionService.getInstance().getDisplayName());
        java.util.List<String> labels = new ArrayList<>();
        labels.add("Package: " + pkgLabel);
        run.setPackageFilter(labels);

        MappingRepository.getInstance().loadIfNeeded();
        AnalysisService svc = new AnalysisService();

        int alreadyOpen = 0;
        int openedAndClosed = 0;
        int couldNotOpen = 0;

        monitor.beginTask("Analyzing package " + pkgLabel, candidates.size());
        int idx = 0;
        for (Candidate c : candidates) {
            checkCancel(monitor);
            idx++;
            monitor.subTask("Processing " + c.name + " (" + idx + "/" + candidates.size() + ")");

            boolean wasOpen = isEditorOpenFor(window, c.name);
            boolean justOpened = false;
            if (!wasOpen) {
                if (openCandidateSync(window, viewer, activePart, c)) {
                    // Let ADT's editor-creation job finish before source fetch.
                    if (viewer != null) {
                        Display.getDefault().syncExec(() -> pumpEvents(viewer, 1200));
                    }
                    // Confirm by checking the workbench again - fireOpen
                    // can return true even when ADT silently no-ops.
                    justOpened = isEditorOpenFor(window, c.name);
                }
            }
            if (wasOpen) alreadyOpen++;
            else if (justOpened) openedAndClosed++;
            else couldNotOpen++;

            ZObject z = new ZObject();
            z.setName(c.name);
            z.setType(c.type != null ? c.type : ZObjectType.UNKNOWN);
            z.setDevClass(c.devClass != null ? c.devClass : pkgLabel);

            try {
                svc.analyseOne(z, run, true, httpReachable);
            } catch (Throwable ignored) {
                // Keep going on per-object failures; the next candidate
                // is independent.
            }

            // Close ONLY the editors we just opened. Pre-existing editors
            // (wasOpen=true) are preserved exactly as the user left them.
            if (justOpened) {
                closeJustOpenedEditor(window, c.name);
            }
            monitor.worked(1);
        }

        run.setFinishedAt(java.time.LocalDateTime.now().toString());
        run.recomputeTotals();

        // Count how many items had source successfully fetched (LOC > 0).
        // Findings count is NOT the right metric - clean code legitimately
        // produces zero findings yet was still analysed.
        int analysed = 0;
        List<Finding> combined = new ArrayList<>();
        for (MigrationItem it : run.getItems()) {
            if (it.getzObject() != null && it.getzObject().getLoc() > 0) analysed++;
            if (it.getFindings() != null) combined.addAll(it.getFindings());
        }

        // 3) Diagnostic dump if absolutely nothing was analysed.
        if (analysed == 0 && openedAndClosed == 0 && alreadyOpen == 0) {
            StringBuilder dbg = new StringBuilder();
            dbg.append("Found ").append(candidates.size())
               .append(" Z/Y candidate(s) but could not open or read any of them.\n\n")
               .append("Candidate node classes (first 3):\n");
            int show = Math.min(3, candidates.size());
            for (int i = 0; i < show; i++) {
                Candidate c = candidates.get(i);
                dbg.append("  - ").append(c.name).append(" : ")
                   .append(c.nodeData == null ? "<no data>" : c.nodeData.getClass().getName())
                   .append("\n");
            }
            asyncInfo(window, "Clean Core - Analyze Selected Package", dbg.toString());
        }

        // 4) Push results to the view on the UI thread:
        //    - showAnalysisRun  → main table + Findings/Mappings/Reasoning + Export
        //    - showCurrentFileFindings → Current File tab summary (backup view)
        final AnalysisRun runRef = run;
        final List<Finding> findingsRef = combined;
        final int analysedRef = analysed;
        final int totalRef = candidates.size();
        final int alreadyOpenRef = alreadyOpen;
        final int openedClosedRef = openedAndClosed;
        final int couldNotOpenRef = couldNotOpen;
        final boolean cappedRef = capped;
        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchPage page = window != null ? window.getActivePage() : null;
                if (page == null) return;
                CleanCoreAnalyzerView view =
                        (CleanCoreAnalyzerView) page.showView(CleanCoreAnalyzerView.ID);
                view.showAnalysisRun(runRef);
                StringBuilder lb = new StringBuilder();
                lb.append("Package ").append(pkgLabel).append(" - ")
                  .append(analysedRef).append("/").append(totalRef)
                  .append(" object(s) analysed (");
                boolean first = true;
                if (alreadyOpenRef > 0) {
                    lb.append(alreadyOpenRef).append(" already open");
                    first = false;
                }
                if (openedClosedRef > 0) {
                    if (!first) lb.append(", ");
                    lb.append(openedClosedRef).append(" opened+closed");
                    first = false;
                }
                if (couldNotOpenRef > 0) {
                    if (!first) lb.append(", ");
                    lb.append(couldNotOpenRef).append(" ADT could not open");
                }
                if (first) lb.append("none");
                lb.append(")");
                if (cappedRef) lb.append(" (capped at ").append(MAX_CHILDREN).append(")");
                view.showCurrentFileFindings(lb.toString(), findingsRef);
            } catch (Throwable t) {
                MessageDialog.openError(window != null ? window.getShell() : null,
                        "Clean Core - Analyze Selected Package",
                        "Collected " + findingsRef.size()
                              + " finding(s) but the results view could not be opened:\n"
                              + t.getMessage());
            }
        });

        monitor.done();
        return Status.OK_STATUS;
    }

    // ---------- selection / children resolution ----------

    private Object firstSelectedElement(ISelection sel) {
        if (!(sel instanceof IStructuredSelection)) return null;
        return ((IStructuredSelection) sel).getFirstElement();
    }

    /** Tracks why collectCandidates returned empty so we can show diagnostics. */
    private static class Diagnostics {
        int topLevelChildren = -1;
        int visits = 0;
        int leaves = 0;
        int zyLeaves = 0;
        int treeItemsWalked = 0;
        int skippedCategories = 0;
        int descended = 0;
    }

    /**
     * Collects Z/Y leaf candidates under rootNode.
     *
     * Primary strategy (when TreeViewer is available, e.g. Project Explorer):
     *   - On the UI thread, force-expand the subtree via expandToLevel(node,
     *     ALL_LEVELS), then pump SWT events for up to ~2.5s to let ADT's
     *     async lazy-load jobs finish.
     *   - Walk the SWT TreeItem subtree directly (rather than asking the
     *     content provider) because TreeItems reflect what has actually been
     *     rendered/loaded.
     *   - Each TreeItem.getData() is examined: IFile, IAdtObjectReference,
     *     or - as a final fallback - the node's label text via reflection.
     *
     * Fallback strategy (no TreeViewer): the original recursive childrenOf
     * walk via reflection / IResource members().
     */
    private List<Candidate> collectCandidates(Object rootNode, TreeViewer viewer, Diagnostics diag) {
        List<Candidate> out = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        if (viewer != null) {
            Display.getDefault().syncExec(() -> {
                try {
                    // Only expand the root one level so ADT loads the direct
                    // category children (Source Code Library, Includes, ...).
                    // BFS below expands each container on demand. Going
                    // ALL_LEVELS up front asks ADT to lazy-load thousands of
                    // objects at once; the pump never catches up.
                    viewer.expandToLevel(rootNode, 1);
                    pumpEvents(viewer, 2000);

                    Tree tree = viewer.getTree();
                    TreeItem rootItem = findItemFor(tree.getItems(), rootNode);
                    if (rootItem == null) return;

                    diag.topLevelChildren = rootItem.getItemCount();

                    // BFS through the TreeItem subtree (already-loaded children).
                    Queue<TreeItem> q = new LinkedList<>();
                    for (TreeItem ti : rootItem.getItems()) q.add(ti);

                    while (!q.isEmpty() && out.size() < MAX_CHILDREN) {
                        TreeItem ti = q.poll();
                        diag.treeItemsWalked++;
                        if (ti == null || ti.isDisposed()) continue;

                        // Skip non-code categories (Dictionary, Texts, ...).
                        if (isSkippedCategory(ti.getText())) {
                            diag.skippedCategories++;
                            continue;
                        }

                        // If our parent is a code-leaf container (Classes /
                        // Programs / Includes / ...), we ARE an ABAP source
                        // object - add as candidate and DO NOT descend into
                        // its inner sections (Events, Textelements, etc.).
                        TreeItem parent = ti.getParentItem();
                        if (parent != null && isCodeLeafContainer(parent.getText())) {
                            diag.leaves++;
                            Object data = ti.getData();
                            Candidate c = makeCandidate(data, ti.getText());
                            if (c == null) continue;
                            if (!seenNames.add(c.name)) continue;
                            c.nodeData = data;
                            c.devClass = findEnclosingPackage(ti);
                            diag.zyLeaves++;
                            out.add(c);
                            continue;
                        }

                        // Otherwise we're a container (package, virtual
                        // folder, sub-package) - expand THIS container 2
                        // levels deep so leaf objects under "Classes" /
                        // "Programs" are loaded by ADT, then pump.
                        try {
                            viewer.expandToLevel(ti.getData(), 2);
                            pumpEvents(viewer, 1200);
                        } catch (Throwable ignored) {}
                        diag.descended++;
                        TreeItem[] kids = ti.getItems();
                        if (kids.length > 0) {
                            for (TreeItem k : kids) q.add(k);
                        }
                    }

                    diag.visits = diag.treeItemsWalked;
                } catch (Throwable ignored) {}
            });
            return out;
        }

        // Fallback: no viewer available, use reflection-only walk.
        Queue<Object> q = new LinkedList<>();
        q.add(rootNode);
        boolean isRoot = true;
        int maxVisits = MAX_CHILDREN * 8;
        while (!q.isEmpty() && out.size() < MAX_CHILDREN && diag.visits < maxVisits) {
            Object node = q.poll();
            diag.visits++;
            if (node == null) continue;

            List<Object> kids = childrenOf(node, null);
            if (isRoot) {
                diag.topLevelChildren = kids.size();
                isRoot = false;
            }
            if (!kids.isEmpty()) {
                for (Object child : kids) if (child != null) q.add(child);
                continue;
            }

            diag.leaves++;
            Candidate c = makeCandidate(node, nodeLabel(node));
            if (c == null) continue;
            if (!seenNames.add(c.name)) continue;
            diag.zyLeaves++;
            out.add(c);
        }
        return out;
    }

    /**
     * Builds a Candidate from a tree node + (optional) text label. Returns
     * null if the node doesn't represent a Z/Y ABAP object.
     */
    private Candidate makeCandidate(Object data, String labelText) {
        if (data != null) {
            IFile f = adaptToFile(data);
            if (f != null) {
                String stem = stemOf(f.getName()).toUpperCase(Locale.ROOT);
                if (isZyName(stem)) {
                    Candidate c = new Candidate();
                    c.name = stem;
                    c.type = typeFor(f.getName());
                    c.file = f;
                    return c;
                }
            }
            Object ref = adaptToAdtObjectReference(data);
            if (ref != null) {
                String name = (String) reflectGet(ref, "getName");
                String adtType = (String) reflectGet(ref, "getType");
                if (name != null && !name.isEmpty()) {
                    String upper = name.toUpperCase(Locale.ROOT);
                    if (isZyName(upper)) {
                        Candidate c = new Candidate();
                        c.name = upper;
                        c.type = mapAdtType(adtType);
                        c.adtRef = ref;
                        return c;
                    }
                }
            }
        }

        // Label-based fallback. Project Explorer labels look like
        //   "ZNT_000_CL_001 - some description"
        //   "ZNT_000_CL_001 (Class)"
        // We take the first whitespace/'(' delimited token.
        String label = labelText != null ? labelText : (data != null ? data.toString() : null);
        if (label != null) {
            String first = label.trim();
            int cut = first.length();
            for (int i = 0; i < first.length(); i++) {
                char ch = first.charAt(i);
                if (Character.isWhitespace(ch) || ch == '(' || ch == '-') { cut = i; break; }
            }
            String stem = first.substring(0, cut).toUpperCase(Locale.ROOT);
            if (isZyName(stem)) {
                Candidate c = new Candidate();
                c.name = stem;
                c.type = ZObjectType.UNKNOWN;
                return c;
            }
        }
        return null;
    }

    /** Recursively searches TreeItems for one whose data == target. */
    private TreeItem findItemFor(TreeItem[] items, Object target) {
        if (items == null) return null;
        for (TreeItem ti : items) {
            if (ti == null || ti.isDisposed()) continue;
            if (ti.getData() == target) return ti;
        }
        for (TreeItem ti : items) {
            if (ti == null || ti.isDisposed()) continue;
            TreeItem found = findItemFor(ti.getItems(), target);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Pumps the SWT event loop for up to maxMillis ms so async UI/content
     * provider jobs can finish. Must be called on the UI thread.
     */
    private void pumpEvents(TreeViewer viewer, long maxMillis) {
        Display d = viewer.getControl().getDisplay();
        long deadline = System.currentTimeMillis() + maxMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean did = d.readAndDispatch();
            if (!did) {
                try { Thread.sleep(40); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private boolean isZyName(String upper) {
        return upper != null && !upper.isEmpty()
                && (upper.charAt(0) == 'Z' || upper.charAt(0) == 'Y');
    }

    /**
     * Returns true when this label looks like a non-code virtual folder, e.g.
     *   "Dictionary (4)", "Texts (1)", "Database Tables", "Data Elements (12)".
     * Trims trailing " (N)" before comparison.
     */
    private boolean isSkippedCategory(String label) {
        return cleanedLabel(label) != null
                && SKIP_CATEGORY_LABELS.contains(cleanedLabel(label));
    }

    /** True when label is a container whose direct children are ABAP source objects. */
    private boolean isCodeLeafContainer(String label) {
        String c = cleanedLabel(label);
        return c != null && CODE_LEAF_CONTAINERS.contains(c);
    }

    /**
     * Walks the TreeItem parent chain upwards looking for the nearest ADT
     * package node and returns its name (e.g. "ZNT_020", "Z001"). Beyond
     * skipping known category labels (Source Code Library, Classes, ...) the
     * returned token must also LOOK like an ABAP package: all uppercase
     * letters / digits / underscores, length >= 3, starts with a letter.
     * This filters out display-only intermediate labels such as "Source",
     * "Enhancements", "Custom Development" that some ADT layouts insert
     * between the leaf and the real package node.
     */
    private String findEnclosingPackage(TreeItem leaf) {
        if (leaf == null) return null;
        TreeItem p = leaf.getParentItem();
        while (p != null && !p.isDisposed()) {
            String label = p.getText();
            String cleaned = cleanedLabel(label);
            if (cleaned != null
                    && !SKIP_CATEGORY_LABELS.contains(cleaned)
                    && !CODE_LEAF_CONTAINERS.contains(cleaned)) {
                String first = label.trim();
                int cut = first.length();
                for (int i = 0; i < first.length(); i++) {
                    char ch = first.charAt(i);
                    if (Character.isWhitespace(ch) || ch == '(' || ch == '-') { cut = i; break; }
                }
                String token = first.substring(0, cut);
                if (looksLikePackageName(token)) return token;
            }
            p = p.getParentItem();
        }
        return null;
    }

    /**
     * ABAP package naming: uppercase letter start, then upper/digit/underscore,
     * length >= 3. Filters out display labels like "Source", "Enhancements".
     */
    private boolean looksLikePackageName(String token) {
        if (token == null || token.length() < 3) return false;
        char c0 = token.charAt(0);
        if (!(c0 >= 'A' && c0 <= 'Z')) return false;
        for (int i = 1; i < token.length(); i++) {
            char ch = token.charAt(i);
            boolean ok = (ch >= 'A' && ch <= 'Z')
                      || (ch >= '0' && ch <= '9')
                      || ch == '_'
                      || ch == '/';
            if (!ok) return false;
        }
        // Must contain a digit or underscore OR start with Z/Y - this filters
        // out single-word English labels like "SOURCE", "ENHANCEMENTS".
        if (c0 == 'Z' || c0 == 'Y') return true;
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '_' || ch == '/') return true;
        }
        return false;
    }

    private String cleanedLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        int paren = s.lastIndexOf('(');
        if (paren > 0) s = s.substring(0, paren).trim();
        if (s.isEmpty()) return null;
        return s.toLowerCase(Locale.ROOT);
    }

    /** Best-effort: extract an IFile from any tree node. */
    private IFile adaptToFile(Object node) {
        if (node instanceof IFile) return (IFile) node;
        if (node instanceof IAdaptable) {
            try {
                Object f = ((IAdaptable) node).getAdapter(IFile.class);
                if (f instanceof IFile) return (IFile) f;
                Object r = ((IAdaptable) node).getAdapter(IResource.class);
                if (r instanceof IFile) return (IFile) r;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Returns IAdtObjectReference (or compatible) for the given node, or null.
     * Uses Class.forName so the plug-in still loads on non-ADT Eclipses.
     *
     * Tries, in order:
     *   1) instanceof IAdtObjectReference
     *   2) IAdaptable.getAdapter(IAdtObjectReference.class)
     *   3) Common method names that ADT wrapper classes expose
     *   4) Direct field reflection across the class hierarchy
     */
    private Object adaptToAdtObjectReference(Object node) {
        if (node == null) return null;
        try {
            Class<?> refClass = Class.forName("com.sap.adt.tools.core.model.IAdtObjectReference");
            return adaptToAdtRefRecursive(node, refClass, new HashSet<>());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object adaptToAdtRefRecursive(Object node, Class<?> refClass, Set<Object> visited) {
        if (node == null || !visited.add(node)) return null;
        if (refClass.isInstance(node)) return node;

        if (node instanceof IAdaptable) {
            try {
                Object r = ((IAdaptable) node).getAdapter(refClass);
                if (r != null && refClass.isInstance(r)) return r;
            } catch (Throwable ignored) {}
        }

        // Common wrapper method names in ADT internals.
        for (String mn : new String[] {
                "getObjectReference", "getAdtObjectReference", "getReference",
                "getAdtObject", "getObject", "getAdtCoreObject", "getAdapter"
        }) {
            try {
                Method m = node.getClass().getMethod(mn);
                Object v = m.invoke(node);
                if (v == null || v == node) continue;
                if (refClass.isInstance(v)) return v;
                Object inner = adaptToAdtRefRecursive(v, refClass, visited);
                if (inner != null) return inner;
            } catch (Throwable ignored) {}
        }

        // Field reflection across the class hierarchy.
        Class<?> cls = node.getClass();
        int depth = 0;
        while (cls != null && cls != Object.class && depth++ < 6) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(node);
                    if (v == null || v == node) continue;
                    if (refClass.isInstance(v)) return v;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /**
     * Returns the children of a tree node. Order of preference:
     *   - IResource: IResource.members()
     *   - TreeViewer / ITreeContentProvider (forces ADT's lazy load)
     *   - Reflection on getChildren()/children()/members()
     */
    private List<Object> childrenOf(Object node, TreeViewer viewer) {
        List<Object> out = new ArrayList<>();

        if (node instanceof IResource) {
            try {
                Method m = node.getClass().getMethod("members");
                Object kids = m.invoke(node);
                if (kids instanceof IResource[]) {
                    for (IResource r : (IResource[]) kids) out.add(r);
                    if (!out.isEmpty()) return out;
                }
            } catch (Throwable ignored) {}
        }

        if (viewer != null) {
            final List<Object> uiResult = new ArrayList<>();
            Display.getDefault().syncExec(() -> {
                try {
                    viewer.expandToLevel(node, 1);
                    Object cpObj = viewer.getContentProvider();
                    if (cpObj instanceof ITreeContentProvider) {
                        ITreeContentProvider cp = (ITreeContentProvider) cpObj;
                        Object[] kids = cp.getChildren(node);
                        if (kids != null) {
                            for (Object k : kids) uiResult.add(k);
                        }
                    }
                } catch (Throwable ignored) {}
            });
            if (!uiResult.isEmpty()) return uiResult;
        }

        for (String mname : new String[] { "getChildren", "children", "members" }) {
            try {
                Method m = node.getClass().getMethod(mname);
                Object kids = m.invoke(node);
                if (kids instanceof Object[]) {
                    for (Object k : (Object[]) kids) out.add(k);
                    if (!out.isEmpty()) return out;
                } else if (kids instanceof Iterable<?>) {
                    for (Object k : (Iterable<?>) kids) out.add(k);
                    if (!out.isEmpty()) return out;
                }
            } catch (Throwable ignored) {}
        }

        return out;
    }

    private String nodeLabel(Object node) {
        if (node == null) return "?";
        if (node instanceof IResource) return ((IResource) node).getName();
        for (String mname : new String[] { "getName", "getLabel", "getElementName" }) {
            try {
                Method m = node.getClass().getMethod(mname);
                Object v = m.invoke(node);
                if (v instanceof String && !((String) v).isEmpty()) return (String) v;
            } catch (Throwable ignored) {}
        }
        String s = node.toString();
        return s != null ? s : "?";
    }

    private TreeViewer findTreeViewer(IWorkbenchPart part) {
        if (part == null) return null;
        try {
            Method m = part.getClass().getMethod("getCommonViewer");
            Object v = m.invoke(part);
            if (v instanceof TreeViewer) return (TreeViewer) v;
        } catch (Throwable ignored) {}
        try {
            Method m = part.getClass().getMethod("getViewer");
            Object v = m.invoke(part);
            if (v instanceof TreeViewer) return (TreeViewer) v;
        } catch (Throwable ignored) {}
        try {
            Object v = part.getAdapter(TreeViewer.class);
            if (v instanceof TreeViewer) return (TreeViewer) v;
            Object av = part.getAdapter(AbstractTreeViewer.class);
            if (av instanceof TreeViewer) return (TreeViewer) av;
        } catch (Throwable ignored) {}
        return null;
    }

    // ---------- editor opening ----------

    /**
     * Tries to open the candidate in an editor on the UI thread.
     * Resolution order:
     *   1) IFile  -> IDE.openEditor (always safe; Eclipse text editor)
     *   2) Otherwise, the candidate must have a text-source-bearing type
     *      (see TEXT_SOURCE_TYPES) to proceed. Non-source types like
     *      SmartForm / Transaction / MessageClass / AreaMenu would open in
     *      SAP GUI maintenance screens, possibly behind blocking dialogs
     *      (e.g. Trust Level Classification) that stall the whole package
     *      analysis. For those we skip the open step.
     *   3) IAdtObjectReference -> ADT NavigationService (reflection)
     *   4) Late-extract IAdtObjectReference from nodeData and retry (3).
     *   5) fireOpen on the TreeViewer to invoke any registered IOpenListener
     *      (this is what ADT uses for Project-Explorer double-click). Does
     *      NOT open any extra dialog - just triggers ADT's own open handler.
     */
    private boolean openCandidateSync(IWorkbenchWindow window, TreeViewer viewer,
                                       IWorkbenchPart activePart, Candidate c) {
        if (window == null || c == null) return false;
        final boolean[] ok = { false };
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchPage page = window.getActivePage();
                if (page == null) return;

                if (c.file != null) {
                    IEditorPart ed = IDE.openEditor(page, c.file, false);
                    ok[0] = ed != null;
                    return;
                }

                Object ref = c.adtRef;
                if (ref == null && c.nodeData != null) {
                    ref = adaptToAdtObjectReference(c.nodeData);
                    if (ref != null) c.adtRef = ref;
                }
                if (ref != null) {
                    ok[0] = openAdtRefViaNavigation(ref);
                    if (ok[0]) return;
                }

                // Last resort: fire the viewer's open event so ADT's own
                // OpenListener handles it (same code path as a double-click).
                if (viewer != null && c.nodeData != null) {
                    ok[0] = openViaFireOpen(viewer, c.nodeData);
                }
            } catch (Throwable ignored) {}
        });
        return ok[0];
    }

    /**
     * Selects nodeData in the tree, then invokes the viewer's protected
     * fireOpen via reflection. This triggers any IOpenListener that ADT (or
     * the platform) has registered, opening the item in its default editor
     * without popping a dialog. UI thread only.
     */
    private boolean openViaFireOpen(TreeViewer viewer, Object nodeData) {
        try {
            org.eclipse.jface.viewers.StructuredSelection sel =
                    new org.eclipse.jface.viewers.StructuredSelection(nodeData);
            viewer.setSelection(sel, true);
            org.eclipse.jface.viewers.OpenEvent ev =
                    new org.eclipse.jface.viewers.OpenEvent(viewer, sel);

            // fireOpen is protected on StructuredViewer.
            Class<?> svClass = org.eclipse.jface.viewers.StructuredViewer.class;
            Method fireOpen = svClass.getDeclaredMethod(
                    "fireOpen", org.eclipse.jface.viewers.OpenEvent.class);
            fireOpen.setAccessible(true);
            fireOpen.invoke(viewer, ev);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Opens an IAdtObjectReference using ADT's navigation service via
     * reflection, so the plug-in compiles and runs without a hard dependency
     * on the ADT bundles.
     */
    private boolean openAdtRefViaNavigation(Object adtRef) {
        try {
            IProject project = AdtConnectionService.getInstance().getAdtProject();
            if (project == null) return false;

            Class<?> factoryClass = Class.forName(
                    "com.sap.adt.tools.core.ui.navigation.AdtNavigationServiceFactory");
            Method create = factoryClass.getMethod("createNavigationService");
            Object service = create.invoke(null);
            if (service == null) return false;

            // navigate(IProject, IAdtObjectReference, boolean activate)
            for (Method m : service.getClass().getMethods()) {
                if (!"navigate".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 3
                        && pts[0].isAssignableFrom(IProject.class)
                        && pts[1].isInstance(adtRef)
                        && pts[2] == boolean.class) {
                    m.invoke(service, project, adtRef, Boolean.FALSE);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // ---------- helpers ----------

    private Object reflectGet(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private void asyncInfo(IWorkbenchWindow window, String title, String msg) {
        Display.getDefault().asyncExec(() ->
                MessageDialog.openInformation(window != null ? window.getShell() : null, title, msg));
    }

    /**
     * Returns true when an open editor's name stem (uppercase) matches the
     * given candidate name. Used by the per-candidate flow to skip opening
     * (and skip closing) editors the user already had open.
     */
    private boolean isEditorOpenFor(IWorkbenchWindow window, String candidateName) {
        if (window == null || candidateName == null) return false;
        final String target = candidateName.toUpperCase(Locale.ROOT);
        final boolean[] found = { false };
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchPage page = window.getActivePage();
                if (page == null) return;
                for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
                    if (ref == null) continue;
                    String n = ref.getName();
                    if (n == null) continue;
                    int dot = n.lastIndexOf('.');
                    String stem = (dot > 0 ? n.substring(0, dot) : n).toUpperCase(Locale.ROOT);
                    if (stem.equals(target)) { found[0] = true; return; }
                }
            } catch (Throwable ignored) {}
        });
        return found[0];
    }

    /**
     * Closes the open editor whose name stem matches the given candidate
     * name. No-op if no matching editor is found. Pass false for save so
     * we never prompt the user (we never modified the file).
     */
    private void closeJustOpenedEditor(IWorkbenchWindow window, String candidateName) {
        if (window == null || candidateName == null) return;
        final String target = candidateName.toUpperCase(Locale.ROOT);
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchPage page = window.getActivePage();
                if (page == null) return;
                for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
                    if (ref == null) continue;
                    String n = ref.getName();
                    if (n == null) continue;
                    int dot = n.lastIndexOf('.');
                    String stem = (dot > 0 ? n.substring(0, dot) : n).toUpperCase(Locale.ROOT);
                    if (stem.equals(target)) {
                        IEditorPart ed = ref.getEditor(false);
                        if (ed != null) page.closeEditor(ed, false);
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    /**
     * Counts how many candidate names appear as open editor titles in the
     * workbench. Editor name typically has the form "NAME.ext" - we match by
     * the stem (uppercase) against candidate.name.
     */
    private int countOpenEditorsMatching(IWorkbenchWindow window,
                                         List<Candidate> candidates) {
        if (window == null || candidates == null || candidates.isEmpty()) return 0;
        final Set<String> wanted = new HashSet<>();
        for (Candidate c : candidates) {
            if (c.name != null) wanted.add(c.name.toUpperCase(Locale.ROOT));
        }
        final int[] count = { 0 };
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbenchPage page = window.getActivePage();
                if (page == null) return;
                for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
                    if (ref == null) continue;
                    String n = ref.getName();
                    if (n == null) continue;
                    int dot = n.lastIndexOf('.');
                    String stem = (dot > 0 ? n.substring(0, dot) : n).toUpperCase(Locale.ROOT);
                    if (wanted.contains(stem)) count[0]++;
                }
            } catch (Throwable ignored) {}
        });
        return count[0];
    }

    private void checkCancel(IProgressMonitor monitor) {
        if (monitor != null && monitor.isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    private static String stemOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static ZObjectType typeFor(String fileName) {
        if (fileName == null) return ZObjectType.UNKNOWN;
        int dot = fileName.lastIndexOf('.');
        String ext = dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        switch (ext) {
            case "aclass":
            case "clas":     return ZObjectType.Z_CLASS;
            case "asinc":
            case "reps":     return ZObjectType.Z_INCLUDE;
            case "prog":
            case "asprog":   return ZObjectType.Z_REPORT;
            case "fugr":
            case "asfunc":   return ZObjectType.Z_FUNCTION_MODULE;
            case "intf":
            case "asintf":   return ZObjectType.Z_INTERFACE;
            case "tabl":
            case "asddic":   return ZObjectType.Z_DDIC_TABLE;
            case "ddls":
            case "asddls":   return ZObjectType.Z_CDS_VIEW;
            case "enho":     return ZObjectType.Z_ENHANCEMENT;
            default:         return ZObjectType.UNKNOWN;
        }
    }

    private static ZObjectType mapAdtType(String adtType) {
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
}
