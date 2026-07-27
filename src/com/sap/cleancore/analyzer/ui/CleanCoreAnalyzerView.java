package com.sap.cleancore.analyzer.ui;

import com.sap.cleancore.analyzer.collectors.AnalysisService;
import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.AnalysisFilter;
import com.sap.cleancore.analyzer.model.AnalysisRun;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.preferences.CleanCorePreferences;
import com.sap.cleancore.analyzer.utils.ExportUtil;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import java.io.File;

/**
 * Main Eclipse view for Clean Core Analyzer.
 * Layout: top toolbar (Connect/Run/Export/Open Maintenance), middle table of
 * migration items, bottom split: findings table + mappings table for the
 * selected item, status bar at the very bottom.
 */
public class CleanCoreAnalyzerView extends ViewPart {

    public static final String ID = "com.sap.cleancore.analyzer.analyzerView";

    private TableViewer mainTable;
    private TableViewer findingsTable;
    private TableViewer mappingsTable;
    private Text reasoningText;
    private Label statusLabel;
    private ProgressBar progressBar;

    // "Current File" tab — single-file analysis results
    private TabItem currentFileTab;
    private TableViewer currentFileTable;
    private Label currentFileSummaryLabel;
    private TabFolder tabFolder;

    private AnalysisRun currentRun;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // Toolbar
        Composite toolbar = new Composite(parent, SWT.NONE);
        toolbar.setLayout(new GridLayout(8, false));
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button connectBtn = new Button(toolbar, SWT.PUSH);
        connectBtn.setText("Connect...");
        connectBtn.addListener(SWT.Selection, e -> onConnect());

        Button runBtn = new Button(toolbar, SWT.PUSH);
        runBtn.setText("Run Analysis");
        runBtn.addListener(SWT.Selection, e -> onRun());

        Button exportCsv = new Button(toolbar, SWT.PUSH);
        exportCsv.setText("Export CSV");
        exportCsv.addListener(SWT.Selection, e -> onExport(false));

        Button exportJson = new Button(toolbar, SWT.PUSH);
        exportJson.setText("Export JSON");
        exportJson.addListener(SWT.Selection, e -> onExport(true));

        Button exportExcel = new Button(toolbar, SWT.PUSH);
        exportExcel.setText("Export Excel");
        exportExcel.addListener(SWT.Selection, e -> onExportExcel());

        Button mappingsBtn = new Button(toolbar, SWT.PUSH);
        mappingsBtn.setText("Mapping Maintenance");
        mappingsBtn.addListener(SWT.Selection, e -> openMaintenance());

        new Label(toolbar, SWT.NONE).setText("   ");

        progressBar = new ProgressBar(toolbar, SWT.HORIZONTAL);
        GridData pgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pgd.minimumWidth = 200;
        progressBar.setLayoutData(pgd);

        // Vertical SashForm so the main table (top) and detail tabs (bottom)
        // are always both visible and the user can drag the divider.
        SashForm sash = new SashForm(parent, SWT.VERTICAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Main table
        mainTable = new TableViewer(sash, SWT.FULL_SELECTION | SWT.BORDER);
        mainTable.getTable().setHeaderVisible(true);
        mainTable.getTable().setLinesVisible(true);
        mainTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        addMainColumn("Package",    180, it -> safe(it.getzObject().getDevClass()));
        addMainColumn("Object",     200, it -> safe(it.getzObject().getName()));
        addMainColumn("Type",       110, it -> it.getzObject().getType() != null ? it.getzObject().getType().name() : "");
        addMainColumn("LOC",         70, it -> String.valueOf(it.getzObject().getLoc()));
        addMainColumn("Findings",    80, it -> String.valueOf(it.getFindings().size()));
        addMainColumn("Modern",     220, it -> it.getRecommendedMappings().isEmpty()
                ? "" : it.getRecommendedMappings().get(0).getModernType() + " " + it.getRecommendedMappings().get(0).getModernName());
        addMainColumn("Category",    80, it -> it.getEffortCategory() != null ? it.getEffortCategory().name() : "");
        addMainColumn("MD",          70, it -> String.valueOf(it.getEstimatedMD()));
        addMainColumn("Risk",        80, it -> safe(it.getRisk()));

        mainTable.setContentProvider(new IStructuredContentProvider() {
            @Override public Object[] getElements(Object input) {
                if (input instanceof AnalysisRun) return ((AnalysisRun) input).getItems().toArray();
                return new Object[0];
            }
            @Override public void dispose() {}
            @Override public void inputChanged(Viewer v, Object o, Object n) {}
        });
        mainTable.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override public void selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent event) {
                Object sel = ((IStructuredSelection) event.getSelection()).getFirstElement();
                if (sel instanceof MigrationItem) showDetail((MigrationItem) sel);
            }
        });

        // Detail tabs (second pane of the SashForm)
        TabFolder tabs = new TabFolder(sash, SWT.NONE);
        this.tabFolder = tabs;

        // 60% main table, 40% detail tabs. User can drag.
        sash.setWeights(new int[] { 60, 40 });

        TabItem findingsTab = new TabItem(tabs, SWT.NONE);
        findingsTab.setText("Findings");
        findingsTable = new TableViewer(tabs, SWT.FULL_SELECTION | SWT.BORDER);
        findingsTable.getTable().setHeaderVisible(true);
        findingsTable.getTable().setLinesVisible(true);
        addCol(findingsTable, "Severity", 80,  f -> ((Finding) f).getSeverity().name());
        addCol(findingsTable, "Source",   90,  f -> ((Finding) f).getSource().name());
        addCol(findingsTable, "Check ID", 160, f -> safe(((Finding) f).getCheckId()));
        addCol(findingsTable, "Line",     50,  f -> String.valueOf(((Finding) f).getLine()));
        addCol(findingsTable, "Message",  600, f -> safe(((Finding) f).getMessage()));
        findingsTable.setContentProvider(new IStructuredContentProvider() {
            @Override public Object[] getElements(Object input) {
                if (input instanceof MigrationItem) return ((MigrationItem) input).getFindings().toArray();
                return new Object[0];
            }
            @Override public void dispose() {}
            @Override public void inputChanged(Viewer v, Object o, Object n) {}
        });
        findingsTab.setControl(findingsTable.getTable());

        TabItem mappingsTab = new TabItem(tabs, SWT.NONE);
        mappingsTab.setText("Recommended Mappings");
        mappingsTable = new TableViewer(tabs, SWT.FULL_SELECTION | SWT.BORDER);
        mappingsTable.getTable().setHeaderVisible(true);
        mappingsTable.getTable().setLinesVisible(true);
        addCol(mappingsTable, "Legacy",     180, m -> ((MappingEntry) m).getLegacyType() + " " + safe(((MappingEntry) m).getLegacyName()));
        addCol(mappingsTable, "Modern",     280, m -> ((MappingEntry) m).getModernType() + " " + safe(((MappingEntry) m).getModernName()));
        addCol(mappingsTable, "Release",    120, m -> ((MappingEntry) m).getReleaseState().name());
        addCol(mappingsTable, "Notes",      400, m -> safe(((MappingEntry) m).getNotes()));
        mappingsTable.setContentProvider(new IStructuredContentProvider() {
            @Override public Object[] getElements(Object input) {
                if (input instanceof MigrationItem) return ((MigrationItem) input).getRecommendedMappings().toArray();
                return new Object[0];
            }
            @Override public void dispose() {}
            @Override public void inputChanged(Viewer v, Object o, Object n) {}
        });
        mappingsTab.setControl(mappingsTable.getTable());

        TabItem reasoningTab = new TabItem(tabs, SWT.NONE);
        reasoningTab.setText("Reasoning");
        reasoningText = new Text(tabs, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY);
        reasoningTab.setControl(reasoningText);

        // Current File analysis tab (Clean Core → Analyze Current File output)
        currentFileTab = new TabItem(tabs, SWT.NONE);
        currentFileTab.setText("Current File");
        Composite cfContainer = new Composite(tabs, SWT.NONE);
        cfContainer.setLayout(new GridLayout(1, false));
        currentFileSummaryLabel = new Label(cfContainer, SWT.NONE);
        currentFileSummaryLabel.setText("Use 'Clean Core → Analyze Current File' menu (Ctrl+Shift+K).");
        currentFileSummaryLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        currentFileTable = new TableViewer(cfContainer, SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL);
        currentFileTable.getTable().setHeaderVisible(true);
        currentFileTable.getTable().setLinesVisible(true);
        currentFileTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        addCol(currentFileTable, "Object",   180, f -> safe(((Finding) f).getObjectName()));
        addCol(currentFileTable, "Severity", 80,  f -> ((Finding) f).getSeverity().name());
        addCol(currentFileTable, "Rule",     70,  f -> safe(((Finding) f).getCheckId()));
        addCol(currentFileTable, "Name",     220, f -> safe(((Finding) f).getRuleName()));
        addCol(currentFileTable, "Line",     50,  f -> String.valueOf(((Finding) f).getLine()));
        addCol(currentFileTable, "Category", 140, f -> safe(((Finding) f).getCategory()));
        addCol(currentFileTable, "Matched Code", 320, f -> safe(((Finding) f).getMatchedCode()));
        addCol(currentFileTable, "Suggestion",   320, f -> safe(((Finding) f).getSuggestion()));
        currentFileTable.setContentProvider(new IStructuredContentProvider() {
            @Override public Object[] getElements(Object input) {
                if (input instanceof java.util.List) return ((java.util.List<?>) input).toArray();
                return new Object[0];
            }
            @Override public void dispose() {}
            @Override public void inputChanged(Viewer v, Object o, Object n) {}
        });
        currentFileTab.setControl(cfContainer);

        // Status bar
        statusLabel = new Label(parent, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateStatus();
    }

    private void addMainColumn(String name, int width, ColumnExtractor<MigrationItem> extractor) {
        TableViewerColumn col = new TableViewerColumn(mainTable, SWT.NONE);
        TableColumn tc = col.getColumn();
        tc.setText(name);
        tc.setWidth(width);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return element instanceof MigrationItem ? extractor.get((MigrationItem) element) : "";
            }
        });
    }

    private void addCol(TableViewer viewer, String name, int width, ColumnExtractor<Object> extractor) {
        TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
        TableColumn tc = col.getColumn();
        tc.setText(name);
        tc.setWidth(width);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) { return extractor.get(element); }
        });
    }

    private void showDetail(MigrationItem item) {
        findingsTable.setInput(item);
        mappingsTable.setInput(item);
        reasoningText.setText(item.getReasoning() != null ? item.getReasoning() : "");
    }

    private void onConnect() {
        ConnectionDialog d = new ConnectionDialog(getSite().getShell());
        d.open();
        if (d.isConnected()) {
            MessageDialog.openInformation(getSite().getShell(), "Clean Core",
                    "Connected to " + AdtConnectionService.getInstance().getDisplayName());
        }
        updateStatus();
    }

    private void onRun() {
        try {
            doRun();
        } catch (Throwable t) {
            // Surface any silent failure so the user (and Error Log) sees it.
            try {
                com.sap.cleancore.Activator.getDefault().getLog().log(
                        new org.eclipse.core.runtime.Status(
                                org.eclipse.core.runtime.IStatus.ERROR,
                                "com.sap.cleancore",
                                "Run Analysis failed: " + t.getMessage(), t));
            } catch (Throwable ignored) {}
            MessageDialog.openError(getSite().getShell(), "Run Analysis failed",
                    "Could not start analysis:\n\n" + (t.getMessage() != null ? t.getMessage() : t.toString()));
        }
    }

    private void doRun() {
        if (!AdtConnectionService.getInstance().isConnected()) {
            MessageDialog.openWarning(getSite().getShell(), "Clean Core",
                    "Please connect to an SAP system first.");
            return;
        }

        // Ask the user what to scan.
        AnalysisWizardDialog dlg = new AnalysisWizardDialog(getSite().getShell());
        if (dlg.open() != org.eclipse.jface.window.Window.OK) return;
        final AnalysisFilter filter = dlg.getResult();
        if (filter == null) return;

        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setSelection(0);

        final String variant = CleanCorePreferences.getAtcVariant();
        final AnalysisService service = new AnalysisService();

        Job job = new Job("Clean Core Analysis") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    AnalysisRun run = service.run(filter, variant,
                            (done, total, msg) -> {
                                // Update view progress bar on UI thread (async to avoid blocking the job)
                                if (PlatformUI.getWorkbench() != null
                                        && !PlatformUI.getWorkbench().getDisplay().isDisposed()) {
                                    PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
                                        if (progressBar.isDisposed()) return;
                                        if (total > 0) {
                                            progressBar.setMaximum(total);
                                            progressBar.setSelection(done);
                                        }
                                        statusLabel.setText(msg);
                                    });
                                }
                            },
                            monitor);

                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;

                    // Hand results back to the UI thread
                    PlatformUI.getWorkbench().getDisplay().asyncExec(() -> showAnalysisRun(run));
                    return Status.OK_STATUS;
                } catch (org.eclipse.core.runtime.OperationCanceledException oce) {
                    return Status.CANCEL_STATUS;
                } catch (Exception ex) {
                    final String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                    PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
                        if (statusLabel.isDisposed()) return;
                        MessageDialog.openError(getSite().getShell(), "Analysis failed", msg);
                    });
                    return new Status(IStatus.ERROR, "com.sap.cleancore", msg, ex);
                }
            }
        };
        job.setUser(true);   // show in the user's Progress view with Cancel button
        job.schedule();
    }

    private void onExport(boolean json) {
        if (currentRun == null) {
            MessageDialog.openWarning(getSite().getShell(), "Clean Core", "No analysis run to export.");
            return;
        }
        FileDialog fd = new FileDialog(getSite().getShell(), SWT.SAVE);
        fd.setFileName("cleancore_analysis." + (json ? "json" : "csv"));
        String path = fd.open();
        if (path == null) return;
        try {
            if (json) ExportUtil.exportJson(currentRun, new File(path));
            else      ExportUtil.exportCsv(currentRun, new File(path));
            MessageDialog.openInformation(getSite().getShell(), "Clean Core", "Exported to " + path);
        } catch (Exception ex) {
            MessageDialog.openError(getSite().getShell(), "Export failed", ex.getMessage());
        }
    }

    private void onExportExcel() {
        if (currentRun == null) {
            MessageDialog.openWarning(getSite().getShell(), "Clean Core", "No analysis run to export.");
            return;
        }
        FileDialog fd = new FileDialog(getSite().getShell(), SWT.SAVE);
        fd.setFileName("cleancore_analysis.xlsx");
        fd.setFilterExtensions(new String[]{"*.xlsx"});
        fd.setFilterNames(new String[]{"Excel Workbook (*.xlsx)"});
        String path = fd.open();
        if (path == null) return;
        try {
            ExportUtil.exportExcel(currentRun, new File(path));
            MessageDialog.openInformation(getSite().getShell(), "Clean Core",
                    "Excel exported to " + path
                    + "\n\nSheets:\n1. Summary\n2. Migration Items\n3. Findings Detail\n4. Mappings\n5. Full Detail (denormalized)");
        } catch (Exception ex) {
            MessageDialog.openError(getSite().getShell(), "Excel export failed", ex.getMessage());
        }
    }

    private void openMaintenance() {
        try {
            getSite().getWorkbenchWindow().getActivePage().showView(
                    "com.sap.cleancore.analyzer.mappingView");
        } catch (Exception e) {
            MessageDialog.openError(getSite().getShell(), "Clean Core", e.getMessage());
        }
    }

    private void updateStatus() {
        String conn = AdtConnectionService.getInstance().getDisplayName();
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(conn);
        // Show local mapping cache size — tells the analyst whether the
        // pre-loaded SAP Cloudification + api.sap.com data has landed yet.
        try {
            int n = com.sap.cleancore.analyzer.mapping.MappingRepository
                    .getInstance().all().size();
            sb.append("  |  Mapping cache: ").append(n).append(" entries");
        } catch (Exception ignored) {}
        if (currentRun != null) {
            sb.append("  |  Items: ").append(currentRun.getItems().size());
            sb.append("  |  Total MD: ").append(currentRun.getTotalMD());
            sb.append("  |  S/M/L/XL: ")
                    .append(currentRun.getCountS()).append("/")
                    .append(currentRun.getCountM()).append("/")
                    .append(currentRun.getCountL()).append("/")
                    .append(currentRun.getCountXL());
            if (currentRun.getCapabilities() != null) {
                sb.append("  |  ").append(currentRun.getCapabilities().summary());
            }
        }
        statusLabel.setText(sb.toString());
    }

    /**
     * Populate the main table + detail tabs (Findings / Recommended Mappings
     * / Reasoning) with the given AnalysisRun. Called by both the built-in
     * Run Analysis flow and external handlers (e.g. Analyze Selected
     * Package). Must be called on the UI thread.
     */
    public void showAnalysisRun(AnalysisRun run) {
        if (mainTable == null || mainTable.getTable().isDisposed()) return;
        currentRun = run;
        mainTable.setInput(currentRun);
        updateStatus();
    }

    /**
     * Populate the "Current File" tab with findings from a single-file
     * analysis (Clean Core → Analyze Current File). Switches focus to that
     * tab and updates the summary label.
     */
    public void showCurrentFileFindings(String fileName, java.util.List<Finding> findings) {
        if (currentFileTable == null || currentFileSummaryLabel == null) return;
        int critical = 0, warning = 0, info = 0;
        for (Finding f : findings) {
            if (f.getSeverity() == Finding.Severity.ERROR) critical++;
            else if (f.getSeverity() == Finding.Severity.WARNING) warning++;
            else info++;
        }
        currentFileSummaryLabel.setText(
                (fileName != null ? fileName : "(unknown)")
                + "    |    Critical: " + critical
                + "    Warning: " + warning
                + "    Info: " + info
                + "    Total: " + findings.size());
        currentFileSummaryLabel.getParent().layout();

        currentFileTable.setInput(findings);
        if (tabFolder != null && currentFileTab != null) {
            tabFolder.setSelection(currentFileTab);
        }
    }

    @Override public void setFocus() {
        if (mainTable != null) mainTable.getTable().setFocus();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    @FunctionalInterface
    private interface ColumnExtractor<T> { String get(T item); }
}
