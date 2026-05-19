package com.sap.cleancore.analyzer.handlers;

import com.sap.cleancore.analyzer.collectors.AnalysisService;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        // 1) Read each file and build a ZObject with pre-loaded source.
        List<ZObject> zObjects = new ArrayList<>(files.size());
        int skipped = 0;
        StringBuilder skippedNames = new StringBuilder();
        for (File f : files) {
            checkCancel(monitor);
            monitor.subTask("Reading " + f.getName());
            String content;
            try {
                Path p = Paths.get(f.getAbsolutePath());
                content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            } catch (Throwable t) {
                skipped++;
                if (skippedNames.length() < 200) {
                    if (skippedNames.length() > 0) skippedNames.append(", ");
                    skippedNames.append(f.getName());
                }
                monitor.worked(1);
                continue;
            }
            if (content == null || content.isEmpty()) {
                skipped++;
                monitor.worked(1);
                continue;
            }

            ZObject z = new ZObject();
            z.setName(stemOf(f.getName()).toUpperCase(Locale.ROOT));
            z.setType(typeFor(f.getName()));
            z.setDevClass("DISK");
            z.setSource(content);
            zObjects.add(z);
            monitor.worked(1);
        }

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
            case "asprog":
            case "abap":     return ZObjectType.Z_REPORT;
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
}
