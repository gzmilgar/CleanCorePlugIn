package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.analyzers.ComplexityCalculator;
import com.sap.cleancore.analyzer.analyzers.DispositionClassifier;
import com.sap.cleancore.analyzer.analyzers.ModificationDetector;
import com.sap.cleancore.analyzer.analyzers.ObsoleteApiDetector;
import com.sap.cleancore.analyzer.analyzers.StaticAbapAnalyzer;
import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.data.CapabilityDetector;
import com.sap.cleancore.analyzer.effort.EffortEstimator;
import com.sap.cleancore.analyzer.effort.EffortRules;
import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.AnalysisFilter;
import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.SystemCapabilities;
import com.sap.cleancore.analyzer.model.TransformationScenario;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.scenario.ScenarioRegistry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates one analysis run end-to-end:
 *   1. Detect system capabilities.
 *   2. Collect Z* inventory (TADIR).
 *   3. (Optional) Run ATC variant; if unavailable, rely on static analyzers.
 *   4. For each object: fetch source, run analyzers, attach mappings, estimate effort.
 *   5. Return an AnalysisRun with totals.
 *
 * Progress is reported via a ProgressListener so UI can show a progress bar,
 * and an IProgressMonitor for native Eclipse Job UI / cancellation.
 */
public class AnalysisService {

    public interface ProgressListener {
        void onProgress(int done, int total, String message);
    }

    private final ZObjectCollector zCollector = new ZObjectCollector();
    private final WorkspaceAdtCollector workspaceCollector = new WorkspaceAdtCollector();
    private final SourceFetcher sourceFetcher = new SourceFetcher();
    private final AdtResourceSourceFetcher workspaceSource = new AdtResourceSourceFetcher();
    private final AtcCollector atcCollector = new AtcCollector();
    private final StaticAbapAnalyzer staticAnalyzer = new StaticAbapAnalyzer();
    private final ObsoleteApiDetector apiDetector = new ObsoleteApiDetector();
    private final ModificationDetector modDetector = new ModificationDetector();
    private final ComplexityCalculator complexityCalc = new ComplexityCalculator();
    private final EffortEstimator estimator = new EffortEstimator();
    private final DispositionClassifier dispositionClassifier = new DispositionClassifier();

    /**
     * Backwards-compatible entry point — uses the default transformation
     * scenario, preserving today's behaviour for existing callers.
     */
    public AnalysisRun run(AnalysisFilter filter, String atcVariant,
                           ProgressListener listener, IProgressMonitor monitor) throws Exception {
        return run(filter, ScenarioRegistry.getInstance().getDefault(), atcVariant, listener, monitor);
    }

    /** New entry point with filter + transformation scenario + cancellation. */
    public AnalysisRun run(AnalysisFilter filter, TransformationScenario scenario, String atcVariant,
                           ProgressListener listener, IProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = new NullProgressMonitor();
        if (filter == null) filter = AnalysisFilter.fullScan();
        scenario = applyScenario(scenario);

        AnalysisRun run = new AnalysisRun();
        run.setStartedAt(LocalDateTime.now().toString());
        run.setSystemDisplay(AdtConnectionService.getInstance().getDisplayName());
        run.setScenario(scenario);
        run.setPackageFilter(filter.getPackagePrefixes());

        // 1) Capabilities (best-effort — used only to decide ATC + display.
        //    Failure no longer aborts: the workspace-based collector below
        //    works without HTTP for systems already opened in ADT.)
        monitor.subTask("Detecting system capabilities...");
        SystemCapabilities caps = new CapabilityDetector().detect();
        run.setCapabilities(caps);
        progress(listener, 0, 1, "Detected capabilities: " + caps.summary());
        checkCancel(monitor);
        boolean httpReachable = !caps.isAllUnavailable();

        // 2) Inventory — combine BOTH sources so the user gets every Z*/Y*
        //    matching their filter, regardless of whether ADT cached it
        //    locally or only the remote system knows about it.
        //
        //    (a) Workspace ADT path: open editors + IResource walk — no HTTP.
        //    (b) HTTP TADIR search: queries /sap/bc/adt/repository/.../search
        //        for every matching Z*/Y* in the system; only attempted when
        //        the discovery probe succeeded (httpReachable). Skipped on
        //        SAProuter-only systems where it would just time out.
        //
        //    Both lists are de-duplicated by upper-case object name.
        monitor.subTask("Collecting Z* inventory from ADT workspace...");
        progress(listener, 0, 1, "Collecting Z* inventory from ADT workspace...");
        List<ZObject> objects = workspaceCollector.collect(filter);
        boolean usedWorkspace = !objects.isEmpty();

        if (httpReachable) {
            monitor.subTask("Also querying TADIR over HTTP...");
            progress(listener, 0, 1, "Also querying TADIR over HTTP...");
            try {
                List<ZObject> http = zCollector.collect(filter);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (ZObject z : objects) {
                    if (z.getName() != null) seen.add(z.getName().toUpperCase(java.util.Locale.ROOT));
                }
                int added = 0;
                for (ZObject z : http) {
                    if (z.getName() == null) continue;
                    if (seen.add(z.getName().toUpperCase(java.util.Locale.ROOT))) {
                        objects.add(z);
                        added++;
                    }
                }
                progress(listener, 0, 1, "HTTP TADIR added " + added
                        + " new object(s) to " + (objects.size() - added) + " from workspace.");
            } catch (Exception httpEx) {
                // HTTP path failed (timeout / firewall) — keep the workspace
                // results without aborting.
                progress(listener, 0, 1,
                        "HTTP TADIR failed (" + httpEx.getMessage()
                      + "); using workspace results only.");
            }
        }

        if (objects.isEmpty()) {
            // Build a diagnostic-rich error so the user knows exactly what
            // happened — how many editors were inspected, which prefixes were
            // tried, and a "did you mean" suggestion when names look close.
            StringBuilder msg = new StringBuilder("No Z*/Y* objects found.\n\n");
            msg.append("ADT does not expose package contents to external plug-ins; only nodes\n")
               .append("you have expanded in Project Explorer are visible. Either expand the\n")
               .append("target package(s) and re-run, or right-click the package \u2192 Clean Core\n")
               .append("\u2192 Analyze Selected Package.\n\n");

            java.util.List<String> editorNames = workspaceCollector.getLastEditorNames();
            int filteredOut = workspaceCollector.getLastFilteredOut();

            msg.append("Editors currently open in the workbench: ")
               .append(editorNames.size()).append("\n");
            if (!editorNames.isEmpty()) {
                int show = Math.min(editorNames.size(), 8);
                for (int i = 0; i < show; i++) msg.append("  • ").append(editorNames.get(i)).append("\n");
                if (editorNames.size() > show) msg.append("  …and ").append(editorNames.size() - show).append(" more\n");
            }

            msg.append("Filter mode: ").append(filter.getMode());
            if (filter.getMode() == AnalysisFilter.Mode.PACKAGE_PREFIX) {
                msg.append("  | prefix(es): ").append(filter.getPackagePrefixes());
            }
            msg.append("\n").append(filteredOut).append(" editor(s) were filtered out by the prefix.\n\n");

            // "Did you mean" — extract the leading [A-Z0-9_]* chunk of the
            // first ABAP-looking editor and suggest a wildcard prefix.
            String suggestion = suggestPrefix(editorNames);
            if (suggestion != null) {
                msg.append("Tip: try the prefix '").append(suggestion)
                   .append("*' in the wizard, or pick 'Full Z*/Y* scan'.\n\n");
            }

            msg.append("Other reasons this can happen:\n")
               .append("  - You haven't opened the target package(s) in Project Explorer yet —\n")
               .append("    ADT only caches what you've expanded. Expand the package node\n")
               .append("    and double-click a Z program first, then re-run.\n")
               .append("  - The system is behind SAProuter and the plug-in's HTTP client\n")
               .append("    cannot reach it; the workspace cache was the only source.\n")
               .append("  - The wizard's package prefix didn't match any open editor name\n")
               .append("    (case-insensitive, treated as startsWith; trailing '*' optional).\n");

            throw new Exception(msg.toString());
        }
        checkCancel(monitor);

        // 3) ATC (optional)
        Map<String, List<Finding>> atcByName = null;
        if (caps.isAtcAvailable() && atcVariant != null && !atcVariant.isEmpty()) {
            monitor.subTask("Running ATC variant " + atcVariant + "...");
            progress(listener, 0, objects.size(), "Running ATC variant " + atcVariant + "...");
            atcByName = atcCollector.run(atcVariant, objects);
            checkCancel(monitor);
        }

        // Mapping repo
        MappingRepository.getInstance().loadIfNeeded();

        analyseObjects(objects, run, atcByName, usedWorkspace, httpReachable, listener, monitor);

        run.setFinishedAt(LocalDateTime.now().toString());
        run.recomputeTotals();
        monitor.done();
        return run;
    }

    /**
     * Runs the full per-object analysis pipeline on a pre-collected list of
     * ZObjects. Used by handlers (e.g. AnalyzeSelectedPackageHandler) that
     * have already discovered the objects via the Project Explorer instead
     * of TADIR/HTTP. Source fetch goes through the workspace tier only
     * (which works once the handler has opened the objects in editors).
     */
    public AnalysisRun runOnObjects(List<ZObject> objects, String runLabel,
                                    IProgressMonitor monitor) throws Exception {
        return runOnObjects(objects, runLabel, ScenarioRegistry.getInstance().getDefault(), monitor);
    }

    /** Scenario-aware variant of {@link #runOnObjects(List, String, IProgressMonitor)}. */
    public AnalysisRun runOnObjects(List<ZObject> objects, String runLabel,
                                    TransformationScenario scenario, IProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = new NullProgressMonitor();
        if (objects == null) objects = new java.util.ArrayList<>();
        scenario = applyScenario(scenario);

        AnalysisRun run = new AnalysisRun();
        run.setStartedAt(LocalDateTime.now().toString());
        run.setSystemDisplay(AdtConnectionService.getInstance().getDisplayName());
        run.setScenario(scenario);
        if (runLabel != null) {
            java.util.List<String> labels = new java.util.ArrayList<>();
            labels.add(runLabel);
            run.setPackageFilter(labels);
        }

        MappingRepository.getInstance().loadIfNeeded();
        // HTTP fallback: if an ADT project is connected, try fetching source
        // via /sap/bc/adt/.../source/main for objects that did NOT get
        // opened in the workbench (ADT's open silently fails for many
        // candidates in big packages). Workspace tier still runs first;
        // HTTP only kicks in when workspace returns null for that object.
        boolean httpReachable = AdtConnectionService.getInstance().getAdtProject() != null;
        analyseObjects(objects, run, null, true, httpReachable, null, monitor);

        run.setFinishedAt(LocalDateTime.now().toString());
        run.recomputeTotals();
        return run;
    }

    /**
     * Per-object analysis loop shared by {@link #run} and {@link #runOnObjects}.
     * Fetches source, applies static + obsolete-API + modification analyzers,
     * attaches recommended mappings, computes effort, and appends a
     * {@link MigrationItem} to {@code run} for each object.
     */
    private void analyseObjects(List<ZObject> objects, AnalysisRun run,
                                Map<String, List<Finding>> atcByName,
                                boolean usedWorkspace, boolean httpReachable,
                                ProgressListener listener, IProgressMonitor monitor) {
        monitor.beginTask("Analyzing Z objects", Math.max(1, objects.size()));

        int idx = 0;
        for (ZObject z : objects) {
            checkCancel(monitor);
            idx++;
            String name = z.getName() != null ? z.getName() : "?";
            monitor.subTask("Analyzing " + name + " (" + idx + "/" + objects.size() + ")");
            progress(listener, idx, objects.size(), "Analyzing " + name);

            analyseOneInternal(z, run, atcByName, usedWorkspace, httpReachable);
            monitor.worked(1);
        }
    }

    /**
     * Run the full per-object pipeline on a single ZObject. Caller manages
     * its own progress monitor. atcByName is null for the per-object
     * (Analyze Selected Package) workflow since ATC is not used there.
     */
    public void analyseOne(ZObject z, AnalysisRun run,
                           boolean usedWorkspace, boolean httpReachable) {
        analyseOneInternal(z, run, null, usedWorkspace, httpReachable);
    }

    /** Per-object pipeline body. Shared by analyseObjects and analyseOne. */
    private void analyseOneInternal(ZObject z, AnalysisRun run,
                                    Map<String, List<Finding>> atcByName,
                                    boolean usedWorkspace, boolean httpReachable) {
        MigrationItem item = new MigrationItem(z);

        // 4a) Source — caller may have set z.setSource(...) up-front
        //      (e.g. AnalyzeDiskFilesHandler reads from local files).
        //      Otherwise prefer workspace cache (no HTTP), then HTTP.
        String src = z.getSource();
        if (src == null && usedWorkspace) {
            src = workspaceSource.fetch(z);
        }
        if (src == null && httpReachable) {
            src = sourceFetcher.fetch(z);
        }
        if (src != null) {
            z.setSource(src);
            z.setComplexity(complexityCalc.compute(src));

            // 4b) Static rules
            for (Finding f : staticAnalyzer.analyze(src)) {
                f.setObjectName(z.getName());
                item.addFinding(f);
            }

            // 4c) Obsolete-API rules (uses MappingRepository)
            ObsoleteApiDetector.Result apiRes = apiDetector.analyze(src);
            for (Finding f : apiRes.findings) {
                f.setObjectName(z.getName());
                item.addFinding(f);
            }
            for (com.sap.cleancore.analyzer.model.MappingEntry m : apiRes.matchedMappings) {
                item.addMapping(m);
            }
        }

        // 4d) Modification (devClass-based)
        Finding modFinding = modDetector.analyze(z);
        if (modFinding != null) {
            modFinding.setObjectName(z.getName());
            item.addFinding(modFinding);
        }

        // 4e) ATC findings (if available)
        if (atcByName != null && z.getName() != null) {
            List<Finding> atc = atcByName.get(z.getName().toUpperCase());
            if (atc != null) {
                for (Finding f : atc) {
                    f.setObjectName(z.getName());
                    item.addFinding(f);
                }
            }
        }

        // 4f) Effort
        estimator.estimate(item);

        // 4g) Disposition (SAP decision tree). No-op for the legacy default
        //     scenario; may force RETIRE (MD≈0) when usage data shows it's unused.
        dispositionClassifier.classify(item, run.getScenario());

        run.addItem(item);
    }

    /** Backwards-compatible legacy overload (used to be called with List<String>). */
    public AnalysisRun run(List<String> packageFilter, String atcVariant, ProgressListener listener) throws Exception {
        AnalysisFilter f = new AnalysisFilter();
        if (packageFilter != null && !packageFilter.isEmpty()) {
            f.setMode(AnalysisFilter.Mode.PACKAGE_PREFIX);
            f.setPackagePrefixes(packageFilter);
        }
        return run(f, atcVariant, listener, new NullProgressMonitor());
    }

    /**
     * Resolves the scenario (default when null) and activates its effort
     * profile so per-object effort estimation reflects the scenario. Keeps the
     * {@code coefficientsFor}/{@code thresholds} signatures untouched.
     */
    private TransformationScenario applyScenario(TransformationScenario scenario) {
        if (scenario == null) scenario = ScenarioRegistry.getInstance().getDefault();
        EffortRules.getInstance().setActiveProfile(scenario.getEffortProfileId());
        return scenario;
    }

    private void checkCancel(IProgressMonitor monitor) {
        if (monitor != null && monitor.isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    private void progress(ProgressListener l, int done, int total, String msg) {
        if (l != null) l.onProgress(done, total, msg);
    }

    /**
     * Pick a sensible prefix suggestion from the first open editor whose name
     * looks like an ABAP source. e.g. "ZNT_000_CL_001.aclass" → "ZNT_".
     * Returns null when no suggestion makes sense.
     */
    private String suggestPrefix(java.util.List<String> editorNames) {
        if (editorNames == null || editorNames.isEmpty()) return null;
        for (String name : editorNames) {
            if (name == null) continue;
            int dot = name.lastIndexOf('.');
            String stem = (dot > 0 ? name.substring(0, dot) : name).toUpperCase(java.util.Locale.ROOT);
            if (stem.isEmpty()) continue;
            if (!(stem.startsWith("Z") || stem.startsWith("Y"))) continue;
            // Prefix up to (and including) the first underscore — useful in
            // SAP shops that namespace by department: ZNT_, ZFI_, ZMM_, ...
            int underscore = stem.indexOf('_');
            return underscore > 0 ? stem.substring(0, underscore + 1) : stem;
        }
        return null;
    }
}
