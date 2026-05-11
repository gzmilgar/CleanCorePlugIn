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
    private final SourceFetcher sourceFetcher = new SourceFetcher();
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

        // 1) Capabilities
        monitor.subTask("Detecting system capabilities...");
        SystemCapabilities caps = new CapabilityDetector().detect();
        run.setCapabilities(caps);
        progress(listener, 0, 1, "Detected capabilities: " + caps.summary());
        checkCancel(monitor);

        // Bail out early if NO HTTP endpoint responded — analysis has no chance.
        // The diagnostic message tells the user WHY (timeout, refused, SAProuter, etc.).
        if (caps.isAllUnavailable()) {
            String diag = caps.getDiagnosticMessage() != null
                    ? caps.getDiagnosticMessage()
                    : "All ADT probes failed.";
            throw new Exception(
                    "Cannot reach the SAP system over HTTP from this plug-in.\n\n"
                  + "Detail: " + diag + "\n\n"
                  + "Likely causes:\n"
                  + "  - The system is behind SAProuter and the plug-in's plain-HTTP\n"
                  + "    client cannot tunnel through it. Try connecting via VPN or use\n"
                  + "    a directly-accessible system (BTP ABAP trial, S/4HANA Cloud).\n"
                  + "  - Network firewall blocks the system's HTTPS port.\n"
                  + "  - Wrong host or port in the ADT destination.\n\n"
                  + "Mapping Maintenance still works offline and is preloaded with\n"
                  + "thousands of SAP Cloudification Repository entries.");
        }

        // 2) Inventory
        monitor.subTask("Collecting Z* inventory...");
        progress(listener, 0, 1, "Collecting Z* inventory...");
        List<ZObject> objects = zCollector.collect(filter);
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

            // 4a) Source
            String src = sourceFetcher.fetch(z);
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
