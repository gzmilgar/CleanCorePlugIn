package com.rap.generator.ui.panels;

import com.rap.generator.model.*;
import com.rap.generator.model.ActionDefinition.ActionPlacement;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.List;

public class ActionPanel {

    private Composite container;
    private RapApplication model;
    private EntityDefinition currentEntity;
    private TableViewer actionTable;

    public ActionPanel(Composite parent, RapApplication model) {
        this.model = model;
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        Label info = new Label(container, SWT.WRAP);
        info.setText("Define actions (buttons) for the selected entity. " +
            "Each action generates a button on the Fiori UI and a method stub in the behavior implementation.");
        info.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        createActionTable();
        createAddActionArea();
    }

    private void createActionTable() {
        actionTable = new TableViewer(container,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        Table table = actionTable.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createColumn("Action Name", 150);
        createColumn("Label", 150);
        createColumn("Placement", 120);
        createColumn("Feature Control", 100);
        createColumn("Control Field", 120);

        actionTable.setContentProvider(ArrayContentProvider.getInstance());
        actionTable.setLabelProvider(new ActionLabelProvider());
    }

    private void createAddActionArea() {
        Group grpAdd = new Group(container, SWT.NONE);
        grpAdd.setText("Add Action");
        grpAdd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grpAdd.setLayout(new GridLayout(4, false));

        // Row 1
        new Label(grpAdd, SWT.NONE).setText("Action Name:");
        Text txtActionName = new Text(grpAdd, SWT.BORDER);
        txtActionName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtActionName.setMessage("e.g. acceptTravel");

        new Label(grpAdd, SWT.NONE).setText("Label:");
        Text txtLabel = new Text(grpAdd, SWT.BORDER);
        txtLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtLabel.setMessage("e.g. Accept Travel");

        // Row 2
        new Label(grpAdd, SWT.NONE).setText("Placement:");
        Combo cmbPlacement = new Combo(grpAdd, SWT.READ_ONLY);
        cmbPlacement.setItems("List & Object Page", "List Only", "Object Page Only");
        cmbPlacement.select(0);
        cmbPlacement.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button chkFeature = new Button(grpAdd, SWT.CHECK);
        chkFeature.setText("Feature Control");

        Text txtControlField = new Text(grpAdd, SWT.BORDER);
        txtControlField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtControlField.setMessage("Status field name");
        txtControlField.setEnabled(false);

        chkFeature.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                txtControlField.setEnabled(chkFeature.getSelection());
            }
        });

        // Row 3: Buttons
        Composite btnRow = new Composite(grpAdd, SWT.NONE);
        btnRow.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 4, 1));
        btnRow.setLayout(new GridLayout(2, true));

        Button btnAdd = new Button(btnRow, SWT.PUSH);
        btnAdd.setText("Add Action");
        btnAdd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnAdd.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity == null) return;
                String name = txtActionName.getText().trim();
                String label = txtLabel.getText().trim();
                if (name.isEmpty() || label.isEmpty()) return;

                ActionDefinition action = new ActionDefinition(name, label, currentEntity.getId());
                switch (cmbPlacement.getSelectionIndex()) {
                    case 0: action.setPlacement(ActionPlacement.BOTH); break;
                    case 1: action.setPlacement(ActionPlacement.LIST); break;
                    case 2: action.setPlacement(ActionPlacement.OBJECT_PAGE); break;
                }
                action.setHasFeatureControl(chkFeature.getSelection());
                if (chkFeature.getSelection()) {
                    action.setFeatureControlField(txtControlField.getText().trim());
                }

                model.addAction(action);
                refreshTable();

                // Clear inputs
                txtActionName.setText("");
                txtLabel.setText("");
                cmbPlacement.select(0);
                chkFeature.setSelection(false);
                txtControlField.setText("");
                txtControlField.setEnabled(false);
            }
        });

        Button btnRemove = new Button(btnRow, SWT.PUSH);
        btnRemove.setText("Remove Selected");
        btnRemove.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnRemove.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IStructuredSelection sel = actionTable.getStructuredSelection();
                ActionDefinition action = (ActionDefinition) sel.getFirstElement();
                if (action != null) {
                    model.removeAction(action.getId());
                    refreshTable();
                }
            }
        });
    }

    private TableViewerColumn createColumn(String title, int width) {
        TableViewerColumn col = new TableViewerColumn(actionTable, SWT.NONE);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.getColumn().setResizable(true);
        return col;
    }

    private void refreshTable() {
        if (currentEntity != null) {
            List<ActionDefinition> actions = model.getActionsForEntity(currentEntity.getId());
            actionTable.setInput(actions);
        }
    }

    public void setEntity(EntityDefinition entity) {
        this.currentEntity = entity;
        refreshTable();
    }

    public Control getControl() { return container; }

    private class ActionLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int col) {
            ActionDefinition a = (ActionDefinition) element;
            switch (col) {
                case 0: return a.getName();
                case 1: return a.getLabel();
                case 2:
                    switch (a.getPlacement()) {
                        case LIST: return "List";
                        case OBJECT_PAGE: return "Object Page";
                        case BOTH: return "Both";
                    }
                    return "";
                case 3: return a.isHasFeatureControl() ? "Yes" : "No";
                case 4: return a.getFeatureControlField() != null ? a.getFeatureControlField() : "";
                default: return "";
            }
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int col) { return null; }
    }
}
