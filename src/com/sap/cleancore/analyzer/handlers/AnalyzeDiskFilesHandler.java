package com.sap.cleancore.analyzer.handlers;

import com.sap.cleancore.analyzer.collectors.AnalysisService;
import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.ui.CleanCoreAnalyzerView;
import com.sap.cleancore.analyzer.utils.OfflineSourceReader;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for "Clean Core - Analyze ABAP Files from Disk...".
 *
 * Lets the user pick one or more local ABAP source files via an SWT
 * FileDialog and analyses them WITHOUT requiring any SAP system connection.
 * This is the recommended workflow for R/3 systems (no ADT, no /sap/bc/adt
 * endpoints): the user dumps the source from SE38 / SE80 to disk, then runs
 * the analyser locally.
 *
 * Each picked file becomes a synthetic {@link ZObject} (devClass = "DISK",
 * name = file stem, type inferred from extension) with its source pre-loaded
 * before being handed to {@link AnalysisService#runOnObjects}. The pipeline
 * skips its own source-fetch tiers because the source is already present, so
 * static analyzers + obsolete-API detector + mapping + effort run normally.
 *
 * Results land in the Clean Core Analyzer view (main table + detail tabs +
 * Export). A flat summary is also pushed into the "Current File" tab.
 */
public class AnalyzeDiskFilesHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        Shell shell = window != null ? window.getShell() : HandlerUtil.getActiveShell(event);

        FileDialog dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
        dialog.setText("Analyze ABAP Files from Disk");
        dialog.setFilterExtensions(new String[] {
                "*.abap;*.clas;*.aclass;*.prog;*.asprog;*.intf;*.asintf;"
                  + "*.fugr;*.asfunc;*.asinc;*.reps;*.txt",
                "*.abap",
                "*.*"
        });
        dialog.setFilterNames(new String[] {
                "ABAP source files (*.abap, *.clas, *.prog, *.intf, ...)",
                "Plain ABAP (*.abap)",
                "All files"
        });
        String first = dialog.open();
        if (first == null) {
            return null; // user cancelled
        }
        String dir = dialog.getFilterPath();
        String[] names = dialog.getFileNames();
        if (names == null || names.length == 0) {
            return null;
        }

        final List<File> files = new ArrayList<>(names.length);
        for (String n : names) files.add(new File(dir, n));
        final IWorkbenchWindow win = window;

        Job job = new Job("Clean Core - Analyze " + files.size() + " disk file(s)") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    return analyse(files, win, monitor);
                } catch (OperationCanceledException oce) {
                    return Status.CANCEL_STATUS;
                } catch (Throwable t) {
                    return new Status(IStatus.ERROR, "com.sap.cleancore",
                            "Analyze ABAP Files from Disk failed: " + t.getMessage(), t);
                }
            }
        };
        job.setUser(true);
        job.schedule();
        return null;
    }

    private IStatus analyse(List<File> files, IWorkbenchWindow window,
                            IProgressMonitor monitor) {
        monitor.beginTask("Reading files", Math.max(1, files.size()));

        // 1) Read each file and build a ZObject with pre-loaded source
        //    (shared offline reader; same extension→type mapping as before).
        OfflineSourceReader.ScanResult scan = new OfflineSourceReader().readFiles(files);
        List<ZObject> zObjects = scan.objects;
        int skipped = scan.skipped;
        StringBuilder skippedNames = new StringBuilder();
        for (String n : scan.skippedNames) {
            if (skippedNames.length() > 0) skippedNames.append(", ");
            skippedNames.append(n);
        }
        monitor.worked(files.size());

        if (zObjects.isEmpty()) {
            asyncInfo(window, "Clean Core - Analyze ABAP Files from Disk",
                    "None of the selected file(s) could be read"
                  + (skipped > 0 ? " (" + skipped + " skipped)" : "")
                  + ".\n\nPlease pick text-based ABAP source files.");
            return Status.OK_STATUS;
        }

        // 2) Run the full pipeline (source fetch is skipped because each
        //    ZObject.source is already populated).
        MappingRepository.getInstance().loadIfNeeded();
        AnalysisRun run;
        try {
            run = new AnalysisService().runOnObjects(
                    zObjects, "Disk: " + zObjects.size() + " file(s)", monitor);
        } catch (Throwable t) {
            return new Status(IStatus.ERROR, "com.sap.cleancore",
                    "Analysis pipeline failed: " + t.getMessage(), t);
        }

        // 3) Flat findings list for the Current File summary tab.
        List<Finding> flat = new ArrayList<>();
        for (MigrationItem it : run.getItems()) {
            if (it.getFindings() != null) flat.addAll(it.getFindings());
        }

        // 4) Push results to the view on the UI thread.
        final AnalysisRun runRef = run;
        final List<Finding> flatRef = flat;
        final int analysedRef = zObjects.size();
        final int skippedRef = skipped;
        final String skippedNamesRef = skippedNames.toString();
        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchPage page = window != null ? window.getActivePage() : null;
                if (page == null) return;
                CleanCoreAnalyzerView view =
                        (CleanCoreAnalyzerView) page.showView(CleanCoreAnalyzerView.ID);
                view.showAnalysisRun(runRef);
                String label = "Disk: " + analysedRef + " file(s) analysed"
                        + (skippedRef > 0
                                ? " (" + skippedRef + " skipped"
                                  + (skippedNamesRef.isEmpty() ? "" : ": " + skippedNamesRef)
                                  + ")"
                                : "");
                view.showCurrentFileFindings(label, flatRef);
            } catch (Throwable t) {
                MessageDialog.openError(window != null ? window.getShell() : null,
                        "Clean Core - Analyze ABAP Files from Disk",
                        "Collected " + flatRef.size()
                              + " finding(s) but the results view could not be opened:\n"
                              + t.getMessage());
            }
        });

        monitor.done();
        return Status.OK_STATUS;
    }

    // ---------- helpers ----------

    private void asyncInfo(IWorkbenchWindow window, String title, String msg) {
        Display.getDefault().asyncExec(() ->
                MessageDialog.openInformation(window != null ? window.getShell() : null, title, msg));
    }

}
