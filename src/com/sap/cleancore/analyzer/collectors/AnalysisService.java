package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.analyzers.ComplexityCalculator;
import com.sap.cleancore.analyzer.analyzers.ModificationDetector;
import com.sap.cleancore.analyzer.analyzers.ObsoleteApiDetector;
import com.sap.cleancore.analyzer.analyzers.StaticAbapAnalyzer;
import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.data.CapabilityDetector;
import com.sap.cleancore.analyzer.effort.EffortEstimator;
import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.AnalysisFilter;
import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.SystemCapabilities;
import com.sap.cleancore.analyzer.model.ZObject;

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

    /** New entry point with filter + cancellation. */
    public AnalysisRun run(AnalysisFilter filter, String atcVariant,
                           ProgressListener listener, IProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = new NullProgressMonitor();
        if (filter == null) filter = AnalysisFilter.fullScan();

        AnalysisRun run = new AnalysisRun();
        run.setStartedAt(LocalDateTime.now().toString());
        run.setSystemDisplay(AdtConnectionService.getInstance().getDisplayName());
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
            throw new Exception(
                    "No Z*/Y* objects found.\n\n"
                  + "Likely causes:\n"
                  + "  - You haven't opened the target package(s) in Project Explorer yet,\n"
                  + "    so ADT hasn't cached them. Expand the package node first, then re-run.\n"
                  + "  - The package prefix filter in the wizard didn't match any package\n"
                  + "    that is currently visible in this ABAP project's tree.\n"
                  + "  - The system is behind SAProuter and the plug-in's HTTP client\n"
                  + "    cannot reach it; the ADT workspace cache was empty too.\n\n"
                  + "Tip: open the package(s) you want to analyse in Project Explorer\n"
                  + "(double-click a Z program to confirm ADT can resolve them), then\n"
                  + "re-run with the package prefix.");
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

        // Prepare per-object work
        monitor.beginTask("Analyzing Z objects", Math.max(1, objects.size()));

        int idx = 0;
        for (ZObject z : objects) {
            checkCancel(monitor);
            idx++;
            String name = z.getName() != null ? z.getName() : "?";
            monitor.subTask("Analyzing " + name + " (" + idx + "/" + objects.size() + ")");
            progress(listener, idx, objects.size(), "Analyzing " + name);

            MigrationItem item = new MigrationItem(z);

            // 4a) Source — prefer workspace cache (no HTTP), fall back to HTTP
            //      fetcher when the workspace path can't resolve it.
            String src = null;
            if (usedWorkspace) {
                src = workspaceSource.fetch(z);
            }
            if (src == null && httpReachable) {
                src = sourceFetcher.fetch(z);
            }
            if (src != null) {
                z.setSource(src);
                z.setComplexity(complexityCalc.compute(src));

                // 4b) Static rules
                for (Finding f : staticAnalyzer.analyze(src)) item.addFinding(f);

                // 4c) Obsolete-API rules (uses MappingRepository)
                ObsoleteApiDetector.Result apiRes = apiDetector.analyze(src);
                for (Finding f : apiRes.findings) item.addFinding(f);
                for (com.sap.cleancore.analyzer.model.MappingEntry m : apiRes.matchedMappings) {
                    item.addMapping(m);
                }
            }

            // 4d) Modification (devClass-based)
            Finding modFinding = modDetector.analyze(z);
            if (modFinding != null) item.addFinding(modFinding);

            // 4e) ATC findings (if available)
            if (atcByName != null) {
                List<Finding> atc = atcByName.get(z.getName().toUpperCase());
                if (atc != null) for (Finding f : atc) item.addFinding(f);
            }

            // 4f) Effort
            estimator.estimate(item);

            run.addItem(item);
            monitor.worked(1);
        }

        run.setFinishedAt(LocalDateTime.now().toString());
        run.recomputeTotals();
        monitor.done();
        return run;
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

    private void checkCancel(IProgressMonitor monitor) {
        if (monitor != null && monitor.isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    private void progress(ProgressListener l, int done, int total, String msg) {
        if (l != null) l.onProgress(done, total, msg);
    }
}
