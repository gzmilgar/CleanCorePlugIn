package com.sap.cleancore.analyzer.ui;

import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.mapping.SapApiHubClient;
import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.preferences.CleanCorePreferences;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import java.util.List;

/**
 * Mapping maintenance UI: search, add, edit, delete entries, sync from
 * api.sap.com. User edits are persisted to the workspace state file.
 */
public class MappingMaintenanceView extends ViewPart {

    public static final String ID = "com.sap.cleancore.analyzer.mappingView";

    private Text searchText;
    private TableViewer table;
    private Text legacyNameText, modernNameText, notesText, urlText;
    private Combo legacyTypeCombo, modernTypeCombo, releaseStateCombo;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // Top: search bar
        Composite topBar = new Composite(parent, SWT.NONE);
        topBar.setLayout(new GridLayout(6, false));
        topBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(topBar, SWT.NONE).setText("Search:");
        searchText = new Text(topBar, SWT.BORDER);
        searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchText.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { refresh(); }
        });

        Button syncBtn = new Button(topBar, SWT.PUSH);
        syncBtn.setText("Sync from api.sap.com");
        syncBtn.addListener(SWT.Selection, e -> onSync());

        Button syncRepoBtn = new Button(topBar, SWT.PUSH);
        syncRepoBtn.setText("Sync from SAP Cloudification Repo");
        syncRepoBtn.addListener(SWT.Selection, e -> onSyncCloudificationRepo());

        Button addBtn = new Button(topBar, SWT.PUSH);
        addBtn.setText("New");
        addBtn.addListener(SWT.Selection, e -> clearForm());

        Button delBtn = new Button(topBar, SWT.PUSH);
        delBtn.setText("Delete");
        delBtn.addListener(SWT.Selection, e -> onDelete());

        // Center: table
        table = new TableViewer(parent, SWT.FULL_SELECTION | SWT.BORDER);
        table.getTable().setHeaderVisible(true);
        table.getTable().setLinesVisible(true);
        table.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        addCol("Legacy",   220, m -> m.getLegacyType() + " " + safe(m.getLegacyName()));
        addCol("Modern",   320, m -> m.getModernType() + " " + safe(m.getModernName()));
        addCol("Release",  100, m -> m.getReleaseState().name());
        addCol("Source",    90, m -> m.getSource() != null ? m.getSource().name() : "");
        addCol("Override",  80, m -> m.isUserOverride() ? "USER" : "");
        addCol("Notes",    400, m -> safe(m.getNotes()));
        table.setContentProvider(new IStructuredContentProvider() {
            @Override public Object[] getElements(Object input) {
                if (input instanceof List) return ((List<?>) input).toArray();
                return new Object[0];
            }
            @Override public void dispose() {}
            @Override public void inputChanged(Viewer v, Object o, Object n) {}
        });
        table.addSelectionChangedListener(e -> {
            Object sel = ((IStructuredSelection) e.getSelection()).getFirstElement();
            if (sel instanceof MappingEntry) fillForm((MappingEntry) sel);
        });

        // Bottom: edit form
        Composite form = new Composite(parent, SWT.NONE);
        form.setLayout(new GridLayout(4, false));
        GridData fgd = new GridData(SWT.FILL, SWT.FILL, true, false);
        form.setLayoutData(fgd);

        new Label(form, SWT.NONE).setText("Legacy name:");
        legacyNameText = new Text(form, SWT.BORDER);
        legacyNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(form, SWT.NONE).setText("Legacy type:");
        legacyTypeCombo = new Combo(form, SWT.READ_ONLY);
        for (MappingEntry.LegacyType t : MappingEntry.LegacyType.values()) legacyTypeCombo.add(t.name());

        new Label(form, SWT.NONE).setText("Modern name:");
        modernNameText = new Text(form, SWT.BORDER);
        modernNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(form, SWT.NONE).setText("Modern type:");
        modernTypeCombo = new Combo(form, SWT.READ_ONLY);
        for (MappingEntry.ModernType t : MappingEntry.ModernType.values()) modernTypeCombo.add(t.name());

        new Label(form, SWT.NONE).setText("Release state:");
        releaseStateCombo = new Combo(form, SWT.READ_ONLY);
        for (MappingEntry.ReleaseState t : MappingEntry.ReleaseState.values()) releaseStateCombo.add(t.name());
        new Label(form, SWT.NONE).setText("Documentation URL:");
        urlText = new Text(form, SWT.BORDER);
        urlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(form, SWT.NONE).setText("Notes:");
        notesText = new Text(form, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData ng = new GridData(SWT.FILL, SWT.FILL, true, false);
        ng.horizontalSpan = 3;
        ng.heightHint = 60;
        notesText.setLayoutData(ng);

        Composite formButtons = new Composite(parent, SWT.NONE);
        formButtons.setLayout(new GridLayout(2, false));
        Button saveBtn = new Button(formButtons, SWT.PUSH);
        saveBtn.setText("Save (User Override)");
        saveBtn.addListener(SWT.Selection, e -> onSave());
        Button reloadBtn = new Button(formButtons, SWT.PUSH);
        reloadBtn.setText("Reload from disk");
        reloadBtn.addListener(SWT.Selection, e -> { MappingRepository.getInstance().reload(); refresh(); });

        MappingRepository.getInstance().loadIfNeeded();
        refresh();
    }

    private void addCol(String name, int width, MapCol extractor) {
        TableViewerColumn col = new TableViewerColumn(table, SWT.NONE);
        TableColumn tc = col.getColumn();
        tc.setText(name);
        tc.setWidth(width);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return element instanceof MappingEntry ? extractor.get((MappingEntry) element) : "";
            }
        });
    }

    private void refresh() {
        String q = searchText != null ? searchText.getText() : "";
        table.setInput(MappingRepository.getInstance().search(q));
    }

    private void fillForm(MappingEntry m) {
        legacyNameText.setText(safe(m.getLegacyName()));
        legacyTypeCombo.setText(m.getLegacyType() != null ? m.getLegacyType().name() : "");
        modernNameText.setText(safe(m.getModernName()));
        modernTypeCombo.setText(m.getModernType() != null ? m.getModernType().name() : "");
        releaseStateCombo.setText(m.getReleaseState() != null ? m.getReleaseState().name() : "");
        urlText.setText(safe(m.getDocumentationUrl()));
        notesText.setText(safe(m.getNotes()));
    }

    private void clearForm() {
        legacyNameText.setText("");
        modernNameText.setText("");
        urlText.setText("");
        notesText.setText("");
        if (legacyTypeCombo.getItemCount() > 0)  legacyTypeCombo.select(0);
        if (modernTypeCombo.getItemCount() > 0)  modernTypeCombo.select(0);
        if (releaseStateCombo.getItemCount() > 0) releaseStateCombo.select(0);
    }

    private void onSave() {
        if (legacyNameText.getText().trim().isEmpty()) {
            MessageDialog.openWarning(getSite().getShell(), "Mapping", "Legacy name is required.");
            return;
        }
        MappingEntry m = new MappingEntry();
        m.setLegacyName(legacyNameText.getText().trim().toUpperCase());
        m.setLegacyType(MappingEntry.LegacyType.valueOf(legacyTypeCombo.getText()));
        m.setModernName(modernNameText.getText().trim());
        m.setModernType(MappingEntry.ModernType.valueOf(modernTypeCombo.getText()));
        m.setReleaseState(MappingEntry.ReleaseState.valueOf(releaseStateCombo.getText()));
        m.setDocumentationUrl(urlText.getText().trim());
        m.setNotes(notesText.getText());
        MappingRepository.getInstance().upsert(m);
        try {
            MappingRepository.getInstance().saveUserOverrides();
        } catch (Exception ex) {
            MessageDialog.openError(getSite().getShell(), "Save failed", ex.getMessage());
            return;
        }
        refresh();
    }

    private void onDelete() {
        Object sel = ((IStructuredSelection) table.getSelection()).getFirstElement();
        if (!(sel instanceof MappingEntry)) return;
        MappingEntry m = (MappingEntry) sel;
        if (!MessageDialog.openConfirm(getSite().getShell(), "Delete",
                "Delete mapping " + m + "?")) return;
        MappingRepository.getInstance().remove(m);
        try { MappingRepository.getInstance().saveUserOverrides(); }
        catch (Exception ex) { /* swallow: user can retry */ }
        refresh();
    }

    private void onSync() {
        try {
            SapApiHubClient client = new SapApiHubClient(CleanCorePreferences.getApiHubUrl());
            MappingRepository.getInstance().mergeRemote(client.fetch());
            refresh();
            MessageDialog.openInformation(getSite().getShell(), "Sync",
                    "Synced from api.sap.com (user overrides preserved).");
        } catch (Exception ex) {
            MessageDialog.openError(getSite().getShell(), "Sync failed", ex.getMessage());
        }
    }

    /**
     * Pulls the SAP Cloudification Repository
     * (https://sap.github.io/abap-atc-cr-cv-s4hc/) via the bundled
     * SapReleaseDataService and merges its tadirObject/successor entries
     * into the mapping repository as MappingEntry rows.
     *
     * Runs on a background thread so the UI stays responsive while the
     * ~MB-sized JSON is fetched/cached.
     */
    private void onSyncCloudificationRepo() {
        org.eclipse.core.runtime.jobs.Job job = new org.eclipse.core.runtime.jobs.Job(
                "Sync SAP Cloudification Repository") {
            @Override
            protected org.eclipse.core.runtime.IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                monitor.beginTask("Downloading SAP Cloudification data...",
                        org.eclipse.core.runtime.IProgressMonitor.UNKNOWN);
                try {
                    int count = new com.sap.cleancore.analyzer.mapping.SapCloudificationBridge()
                            .syncIntoRepository();
                    org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
                        refresh();
                        MessageDialog.openInformation(getSite().getShell(),
                                "Cloudification Repo Sync",
                                "Merged " + count + " entries from SAP Cloudification Repository.\n"
                                        + "(User overrides preserved.)");
                    });
                    return org.eclipse.core.runtime.Status.OK_STATUS;
                } catch (Exception ex) {
                    final String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                    org.eclipse.swt.widgets.Display.getDefault().asyncExec(() ->
                            MessageDialog.openError(getSite().getShell(),
                                    "Cloudification Repo Sync failed", msg));
                    return new org.eclipse.core.runtime.Status(
                            org.eclipse.core.runtime.IStatus.ERROR,
                            "com.sap.cleancore", msg, ex);
                } finally {
                    monitor.done();
                }
            }
        };
        job.setUser(true);
        job.schedule();
    }

    @Override public void setFocus() {
        if (searchText != null) searchText.setFocus();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    @FunctionalInterface
    private interface MapCol { String get(MappingEntry m); }
}
