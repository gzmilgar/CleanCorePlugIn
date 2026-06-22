package com.sap.cleancore.analyzer.handlers;

import com.sap.cleancore.analyzer.collectors.AnalysisService;
import com.sap.cleancore.analyzer.collectors.offline.CodeMetadataIngestor;
import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.TransformationScenario;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.ui.CleanCoreAnalyzerView;
import com.sap.cleancore.analyzer.ui.OfflineAnalysisDialog;
import com.sap.cleancore.analyzer.utils.OfflineSourceReader;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for "Clean Core - Analyze Folder (Offline)".
 *
 * <p>The offline (no-ADT) counterpart of "Analyze Selected Package": the user
 * dumps custom ABAP source into a folder (SE38/SE80 download, abapGit pull,
 * transport/SAPlink), imports it into Eclipse as a plain folder/project,
 * right-clicks it and runs this — no SAP connection required. Works on any old
 * release where ADT does not run.
 *
 * <p>All source files under the folder are read into {@link ZObject}s; an
 * optional {@code metadata.json} sidecar enriches them (package, type, author,
 * usage → enables modification detection and usage-based RETIRE). The user then
 * picks a {@link TransformationScenario}; results land in the Clean Core
 * Analyzer view, ready for "Export Project Report".
 */
public class AnalyzeFolderHandler extends AbstractHandler {

    private final OfflineSourceReader reader = new OfflineSourceReader();
    private final CodeMetadataIngestor metadataIngestor = new CodeMetadataIngestor();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        Shell shell = window != null ? window.getShell() : HandlerUtil.getActiveShell(event);

        // 1) Resolve the target folder — from the navigator selection, else ask.
        File folder = folderFromSelection(HandlerUtil.getCurrentSelection(event));
        if (folder == null) {
            DirectoryDialog dd = new DirectoryDialog(shell, SWT.OPEN);
            dd.setText("Analyze Folder (Offline)");
            dd.setMessage("Select the folder containing the dumped ABAP source files.");
            String p = dd.open();
            if (p == null) return null;
            folder = new File(p);
        }
        if (!folder.isDirectory()) {
            MessageDialog.openWarning(shell, "Clean Core", "Not a folder: " + folder);
            return null;
        }

        // 2) Scenario + optional integration extract (UI thread).
        File metaFile = new File(folder, "metadata.json");
        String info = "Folder: " + folder.getAbsolutePath() + "\n"
                + (metaFile.isFile()
                    ? "metadata.json found — package/type/usage will be applied."
                    : "No metadata.json — analysis uses file names + source only.");
        OfflineAnalysisDialog dlg = new OfflineAnalysisDialog(shell, info);
        if (dlg.open() != Window.OK) return null;
        final TransformationScenario scenario = dlg.getScenario();
        final File extractFile = dlg.getIntegrationExtractFile();
        final File targetFolder = folder;
        final IWorkbenchWindow win = window;

        Job job = new Job("Clean Core - Analyze Folder (Offline)") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    return analyse(targetFolder, scenario, extractFile, win, monitor);
                } catch (OperationCanceledException oce) {
                    return Status.CANCEL_STATUS;
                } catch (Throwable t) {
                    return new Status(IStatus.ERROR, "com.sap.cleancore",
                            "Analyze Folder (Offline) failed: " + t.getMessage(), t);
                }
            }
        };
        job.setUser(true);
        job.schedule();
        return null;
    }

    private IStatus analyse(File folder, TransformationScenario scenario, File extractFile,
                            IWorkbenchWindow window, IProgressMonitor monitor) {
        monitor.beginTask("Scanning folder", IProgressMonitor.UNKNOWN);

        // 1) Read source files → ZObjects, then enrich from metadata.json.
        OfflineSourceReader.ScanResult scan = reader.scanFolder(folder);
        if (scan.objects.isEmpty()) {
            asyncInfo(window, "Clean Core - Analyze Folder (Offline)",
                    "No ABAP source files found under:\n" + folder.getAbsolutePath()
                  + "\n\nExpected files like *.abap, *.prog, *.clas, *.fugr, ...");
            return Status.OK_STATUS;
        }
        metadataIngestor.enrichFromFolder(folder, scan.objects);
        checkCancel(monitor);

        // 2) Run the offline pipeline (source already loaded; no HTTP/ADT).
        MappingRepository.getInstance().loadIfNeeded();
        AnalysisService svc = new AnalysisService();
        svc.setIntegrationExtractFile(extractFile);
        AnalysisRun run;
        try {
            String label = "Folder: " + folder.getName() + " (" + scan.objects.size() + " object(s))";
            run = svc.runOnObjects(scan.objects, label, scenario, monitor);
        } catch (Throwable t) {
            return new Status(IStatus.ERROR, "com.sap.cleancore",
                    "Analysis pipeline failed: " + t.getMessage(), t);
        }

        // 3) Flat findings for the Current File summary tab.
        final List<Finding> flat = new ArrayList<>();
        for (MigrationItem it : run.getItems()) {
            if (it.getFindings() != null) flat.addAll(it.getFindings());
        }

        // 4) Push to the view (UI thread).
        final AnalysisRun runRef = run;
        final int analysed = scan.objects.size();
        final int skipped = scan.skipped;
        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchPage page = window != null ? window.getActivePage() : null;
                if (page == null) return;
                CleanCoreAnalyzerView view =
                        (CleanCoreAnalyzerView) page.showView(CleanCoreAnalyzerView.ID);
                view.showAnalysisRun(runRef);
                view.showCurrentFileFindings(
                        "Folder offline: " + analysed + " object(s) analysed"
                              + (skipped > 0 ? " (" + skipped + " skipped)" : ""),
                        flat);
            } catch (Throwable t) {
                MessageDialog.openError(window != null ? window.getShell() : null,
                        "Clean Core - Analyze Folder (Offline)",
                        "Collected " + flat.size()
                              + " finding(s) but the results view could not be opened:\n"
                              + t.getMessage());
            }
        });

        monitor.done();
        return Status.OK_STATUS;
    }

    /** Extract a filesystem folder from the current navigator selection, if any. */
    private File folderFromSelection(ISelection selection) {
        if (!(selection instanceof IStructuredSelection)) return null;
        Object first = ((IStructuredSelection) selection).getFirstElement();
        if (first == null) return null;

        // IContainer (IProject / IFolder) → filesystem location.
        IContainer container = null;
        if (first instanceof IContainer) {
            container = (IContainer) first;
        } else if (first instanceof org.eclipse.core.runtime.IAdaptable) {
            Object res = ((org.eclipse.core.runtime.IAdaptable) first)
                    .getAdapter(IContainer.class);
            if (res instanceof IContainer) container = (IContainer) res;
        }
        if (container != null) {
            IPath loc = container.getLocation();
            if (loc != null) {
                File f = loc.toFile();
                if (f.isDirectory()) return f;
            }
        }

        // Plain java.io.File (filesystem navigators).
        if (first instanceof File && ((File) first).isDirectory()) {
            return (File) first;
        }
        return null;
    }

    private void asyncInfo(IWorkbenchWindow window, String title, String msg) {
        Display.getDefault().asyncExec(() ->
                MessageDialog.openInformation(window != null ? window.getShell() : null, title, msg));
    }

    private void checkCancel(IProgressMonitor monitor) {
        if (monitor != null && monitor.isCanceled()) {
            throw new OperationCanceledException();
        }
    }
}
