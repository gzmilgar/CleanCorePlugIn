package com.rap.generator.ui.panels;

import com.rap.generator.model.*;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

public class UiConfigPanel {

    private Composite container;
    private RapApplication model;
    private EntityDefinition currentEntity;
    private TableViewer configTable;

    public UiConfigPanel(Composite parent, RapApplication model) {
        this.model = model;
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        Label info = new Label(container, SWT.WRAP);
        info.setText("Configure UI annotations for each field. Set positions to include fields in List Report, " +
            "Selection (filter), Identification, or Field Groups. Leave position empty to exclude.");
        info.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        createConfigTable();
    }

    private void createConfigTable() {
        configTable = new TableViewer(container,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        Table table = configTable.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Columns
        createColumn("Field Name", 130);
        createColumn("List Pos", 70);
        createColumn("List Importance", 100);
        createColumn("Filter Pos", 70);
        createColumn("Identification Pos", 100);
        createColumn("Field Group", 100);
        createColumn("FG Pos", 60);
        createColumn("Search", 60);

        configTable.setContentProvider(ArrayContentProvider.getInstance());
        configTable.setLabelProvider(new UiConfigLabelProvider());

        // Cell editors
        configTable.setColumnProperties(new String[]{
            "name", "lineItemPos", "lineItemImp", "selFieldPos", "identPos", "fgQualifier", "fgPos", "search"
        });

        CellEditor[] editors = new CellEditor[8];
        editors[0] = null; // name not editable
        editors[1] = new TextCellEditor(table);
        editors[2] = new ComboBoxCellEditor(table, new String[]{"", "HIGH", "MEDIUM", "LOW"}, SWT.READ_ONLY);
        editors[3] = new TextCellEditor(table);
        editors[4] = new TextCellEditor(table);
        editors[5] = new TextCellEditor(table);
        editors[6] = new TextCellEditor(table);
        editors[7] = new CheckboxCellEditor(table);
        configTable.setCellEditors(editors);
        configTable.setCellModifier(new UiConfigCellModifier());

        // Quick config buttons
        Composite btnBar = new Composite(container, SWT.NONE);
        btnBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnBar.setLayout(new GridLayout(3, false));

        Button btnAutoList = new Button(btnBar, SWT.PUSH);
        btnAutoList.setText("Auto: All Fields to List");
        btnAutoList.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity == null) return;
                int pos = 10;
                for (FieldDefinition f : currentEntity.getFields()) {
                    if (!f.isAdminField()) {
                        f.setLineItemPosition(pos);
                        f.setLineItemImportance(f.isKey() ? "HIGH" : "MEDIUM");
                        pos += 10;
                    }
                }
                configTable.refresh();
            }
        });

        Button btnAutoFilter = new Button(btnBar, SWT.PUSH);
        btnAutoFilter.setText("Auto: Key Fields to Filter");
        btnAutoFilter.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity == null) return;
                int pos = 10;
                for (FieldDefinition f : currentEntity.getFields()) {
                    if (f.isKey() || f.isMandatory()) {
                        f.setSelectionFieldPosition(pos);
                        pos += 10;
                    }
                }
                configTable.refresh();
            }
        });

        Button btnAutoIdent = new Button(btnBar, SWT.PUSH);
        btnAutoIdent.setText("Auto: All to Identification");
        btnAutoIdent.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity == null) return;
                int pos = 10;
                for (FieldDefinition f : currentEntity.getFields()) {
                    if (!f.isAdminField()) {
                        f.setIdentificationPosition(pos);
                        pos += 10;
                    }
                }
                configTable.refresh();
            }
        });
    }

    private TableViewerColumn createColumn(String title, int width) {
        TableViewerColumn col = new TableViewerColumn(configTable, SWT.NONE);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.getColumn().setResizable(true);
        return col;
    }

    public void setEntity(EntityDefinition entity) {
        this.currentEntity = entity;
        if (entity != null) {
            configTable.setInput(entity.getFields());
        }
    }

    public Control getControl() { return container; }

    private class UiConfigLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int col) {
            FieldDefinition f = (FieldDefinition) element;
            switch (col) {
                case 0: return f.getName();
                case 1: return f.getLineItemPosition() != null ? String.valueOf(f.getLineItemPosition()) : "";
                case 2: return f.getLineItemImportance() != null ? f.getLineItemImportance() : "";
                case 3: return f.getSelectionFieldPosition() != null ? String.valueOf(f.getSelectionFieldPosition()) : "";
                case 4: return f.getIdentificationPosition() != null ? String.valueOf(f.getIdentificationPosition()) : "";
                case 5: return f.getFieldGroupQualifier() != null ? f.getFieldGroupQualifier() : "";
                case 6: return f.getFieldGroupPosition() != null ? String.valueOf(f.getFieldGroupPosition()) : "";
                case 7: return f.isSearchField() ? "Yes" : "";
                default: return "";
            }
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int col) { return null; }
    }

    private class UiConfigCellModifier implements ICellModifier {
        @Override
        public boolean canModify(Object element, String property) {
            return !"name".equals(property);
        }

        @Override
        public Object getValue(Object element, String property) {
            FieldDefinition f = (FieldDefinition) element;
            switch (property) {
                case "lineItemPos": return f.getLineItemPosition() != null ? String.valueOf(f.getLineItemPosition()) : "";
                case "lineItemImp":
                    String imp = f.getLineItemImportance();
                    if ("HIGH".equals(imp)) return 1;
                    if ("MEDIUM".equals(imp)) return 2;
                    if ("LOW".equals(imp)) return 3;
                    return 0;
                case "selFieldPos": return f.getSelectionFieldPosition() != null ? String.valueOf(f.getSelectionFieldPosition()) : "";
                case "identPos": return f.getIdentificationPosition() != null ? String.valueOf(f.getIdentificationPosition()) : "";
                case "fgQualifier": return f.getFieldGroupQualifier() != null ? f.getFieldGroupQualifier() : "";
                case "fgPos": return f.getFieldGroupPosition() != null ? String.valueOf(f.getFieldGroupPosition()) : "";
                case "search": return f.isSearchField();
                default: return "";
            }
        }

        @Override
        public void modify(Object element, String property, Object value) {
            if (element instanceof TableItem) {
                element = ((TableItem) element).getData();
            }
            FieldDefinition f = (FieldDefinition) element;
            switch (property) {
                case "lineItemPos":
                    f.setLineItemPosition(parseIntOrNull((String) value));
                    break;
                case "lineItemImp":
                    int idx = (Integer) value;
                    String[] imps = {"", "HIGH", "MEDIUM", "LOW"};
                    f.setLineItemImportance(idx > 0 ? imps[idx] : null);
                    break;
                case "selFieldPos":
                    f.setSelectionFieldPosition(parseIntOrNull((String) value));
                    break;
                case "identPos":
                    f.setIdentificationPosition(parseIntOrNull((String) value));
                    break;
                case "fgQualifier":
                    String q = ((String) value).trim();
                    f.setFieldGroupQualifier(q.isEmpty() ? null : q);
                    break;
                case "fgPos":
                    f.setFieldGroupPosition(parseIntOrNull((String) value));
                    break;
                case "search":
                    f.setSearchField((Boolean) value);
                    break;
            }
            configTable.update(f, null);
        }

        private Integer parseIntOrNull(String s) {
            if (s == null || s.trim().isEmpty()) return null;
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { return null; }
        }
    }
}
