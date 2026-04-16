package com.rap.generator.ui.panels;

import com.rap.generator.data.ObjectType;
import com.rap.generator.data.ReleaseObject;
import com.rap.generator.data.SapReleaseDataService;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.text.SimpleDateFormat;
import java.util.List;

public class ReleaseCheckPanel {

    private Composite container;
    private TableViewer resultsTable;
    private Label lblStatus;
    private Text txtSearch;
    private Combo cmbObjectType;
    private Combo cmbState;

    public ReleaseCheckPanel(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        createSearchBar();
        createResultsTable();
        createStatusBar();

        updateStatusBar();
    }

    private void createSearchBar() {
        Composite searchBar = new Composite(container, SWT.NONE);
        searchBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchBar.setLayout(new GridLayout(7, false));

        // Search text
        new Label(searchBar, SWT.NONE).setText("Search:");
        txtSearch = new Text(searchBar, SWT.BORDER | SWT.SEARCH);
        txtSearch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtSearch.setMessage("Object name or app component...");

        // Object type filter
        new Label(searchBar, SWT.NONE).setText("Type:");
        cmbObjectType = new Combo(searchBar, SWT.READ_ONLY);
        String[] types = {"All", "DDLS - CDS View", "TABL - Table", "DTEL - Data Element",
            "BDEF - Behavior Def", "CLAS - Class", "INTF - Interface",
            "SRVD - Service Def", "DDLX - Metadata Ext"};
        cmbObjectType.setItems(types);
        cmbObjectType.select(0);

        // State filter
        new Label(searchBar, SWT.NONE).setText("State:");
        cmbState = new Combo(searchBar, SWT.READ_ONLY);
        cmbState.setItems("All", "released", "deprecated", "notToBeReleased");
        cmbState.select(0);

        // Search button
        Button btnSearch = new Button(searchBar, SWT.PUSH);
        btnSearch.setText("Search");
        btnSearch.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                performSearch();
            }
        });

        // Also search on Enter key
        txtSearch.addListener(SWT.DefaultSelection, event -> performSearch());

        // Download data button
        Composite dlBar = new Composite(container, SWT.NONE);
        dlBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        dlBar.setLayout(new GridLayout(2, false));

        Button btnDownload = new Button(dlBar, SWT.PUSH);
        btnDownload.setText("Download / Refresh SAP Release Data");
        btnDownload.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                btnDownload.setEnabled(false);
                btnDownload.setText("Downloading...");
                SapReleaseDataService.getInstance().downloadDataAsync(() -> {
                    btnDownload.setEnabled(true);
                    btnDownload.setText("Download / Refresh SAP Release Data");
                    updateStatusBar();
                });
            }
        });

        Label dlInfo = new Label(dlBar, SWT.NONE);
        dlInfo.setText("Downloads ~10MB from GitHub (one-time, then cached offline)");
    }

    private void createResultsTable() {
        resultsTable = new TableViewer(container,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        Table table = resultsTable.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createColumn("Object Name", 250);
        createColumn("Type", 80);
        createColumn("State", 120);
        createColumn("App Component", 180);
        createColumn("Software Component", 150);

        resultsTable.setContentProvider(ArrayContentProvider.getInstance());
        resultsTable.setLabelProvider(new ReleaseLabelProvider());

        // Double-click to show details
        resultsTable.addDoubleClickListener(event -> {
            IStructuredSelection sel = (IStructuredSelection) event.getSelection();
            ReleaseObject obj = (ReleaseObject) sel.getFirstElement();
            if (obj != null) {
                showObjectDetails(obj);
            }
        });
    }

    private void createStatusBar() {
        lblStatus = new Label(container, SWT.NONE);
        lblStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void updateStatusBar() {
        SapReleaseDataService svc = SapReleaseDataService.getInstance();
        if (svc.isLoaded()) {
            String date = svc.getLastUpdated() != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(svc.getLastUpdated())
                : "unknown";
            lblStatus.setText(svc.getObjectCount() + " objects loaded | Last updated: " + date);
        } else {
            lblStatus.setText("No data loaded. Click 'Download' to fetch SAP release data.");
        }
    }

    private void performSearch() {
        SapReleaseDataService svc = SapReleaseDataService.getInstance();
        if (!svc.isLoaded()) {
            lblStatus.setText("No data loaded. Please download SAP release data first.");
            return;
        }

        String query = txtSearch.getText().trim();
        String type = getSelectedObjectType();
        String state = cmbState.getSelectionIndex() > 0 ? cmbState.getText() : null;

        List<ReleaseObject> results = svc.search(query, type, state, 500);
        resultsTable.setInput(results);
        lblStatus.setText("Found " + results.size() + " objects");
    }

    private String getSelectedObjectType() {
        int idx = cmbObjectType.getSelectionIndex();
        if (idx <= 0) return null;
        // Extract type code from "DDLS - CDS View" format
        String item = cmbObjectType.getItem(idx);
        return item.split(" - ")[0].trim();
    }

    private void showObjectDetails(ReleaseObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("Object: ").append(obj.getTadirObjName()).append("\n");
        sb.append("Type: ").append(obj.getTadirObject()).append("\n");
        sb.append("State: ").append(obj.getState()).append("\n");
        sb.append("App Component: ").append(obj.getApplicationComponent()).append("\n");
        sb.append("Software Component: ").append(obj.getSoftwareComponent()).append("\n");

        if (obj.getSuccessors() != null && !obj.getSuccessors().isEmpty()) {
            sb.append("\nSuccessors:\n");
            for (ReleaseObject.Successor s : obj.getSuccessors()) {
                sb.append("  - ").append(s.getTadirObject()).append(": ").append(s.getTadirObjName()).append("\n");
            }
        }

        if (obj.getSuccessorConceptName() != null && !obj.getSuccessorConceptName().isEmpty()) {
            sb.append("Successor Concept: ").append(obj.getSuccessorConceptName()).append("\n");
        }

        MessageBox msgBox = new MessageBox(container.getShell(), SWT.ICON_INFORMATION | SWT.OK);
        msgBox.setText("Object Details: " + obj.getTadirObjName());
        msgBox.setMessage(sb.toString());
        msgBox.open();
    }

    private TableViewerColumn createColumn(String title, int width) {
        TableViewerColumn col = new TableViewerColumn(resultsTable, SWT.NONE);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.getColumn().setResizable(true);
        return col;
    }

    public Control getControl() { return container; }

    private class ReleaseLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int col) {
            ReleaseObject obj = (ReleaseObject) element;
            switch (col) {
                case 0: return obj.getTadirObjName();
                case 1: return obj.getTadirObject();
                case 2: return obj.getState();
                case 3: return obj.getApplicationComponent() != null ? obj.getApplicationComponent() : "";
                case 4: return obj.getSoftwareComponent() != null ? obj.getSoftwareComponent() : "";
                default: return "";
            }
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int col) { return null; }
    }
}
