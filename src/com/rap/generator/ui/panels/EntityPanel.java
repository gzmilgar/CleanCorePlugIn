package com.rap.generator.ui.panels;

import com.rap.generator.data.AdtFieldFetcher;
import com.rap.generator.model.*;
import com.rap.generator.utils.AbapTypeRegistry;

import org.eclipse.core.resources.IProject;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.*;

import java.util.ArrayList;
import java.util.List;

public class EntityPanel {

    private Composite container;
    private ScrolledComposite scrolled;
    private Composite inner;
    private RapApplication model;
    private EntityDefinition currentEntity;

    // Form widgets
    private Text txtName;
    private Text txtObjectName;
    private Text txtProjectionName;
    private Text txtSource;
    private Combo cmbSourceType;
    private Text txtParentKey;
    private Button chkDraft;
    private Label lblConnectionStatus;
    private Combo cmbAbapProject;
    private List<IProject> abapProjects = new java.util.ArrayList<>();

    // Field table
    private TableViewer fieldTable;

    public EntityPanel(Composite parent, RapApplication model) {
        this.model = model;

        scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        inner = new Composite(scrolled, SWT.NONE);
        inner.setLayout(new GridLayout(1, false));

        scrolled.setContent(inner);

        createConnectionBar();
        createEntityForm();
        createFieldTable();

        inner.setSize(inner.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        container = scrolled;
    }

    private void createConnectionBar() {
        // ===== ABAP PROJECT SELECTION =====
        Group grpConn = new Group(inner, SWT.NONE);
        grpConn.setText("ABAP System");
        grpConn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grpConn.setLayout(new GridLayout(2, false));

        new Label(grpConn, SWT.NONE).setText("ABAP Project:");
        cmbAbapProject = new Combo(grpConn, SWT.READ_ONLY);
        cmbAbapProject.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Load ABAP projects safely
        try {
            abapProjects = AdtFieldFetcher.getInstance().findAbapProjects();
            for (IProject p : abapProjects) {
                cmbAbapProject.add(p.getName());
            }
            if (!abapProjects.isEmpty()) {
                cmbAbapProject.select(0);
            }
        } catch (Exception e) {
            abapProjects = new java.util.ArrayList<>();
        }

        lblConnectionStatus = new Label(grpConn, SWT.WRAP);
        lblConnectionStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        if (abapProjects.isEmpty()) {
            lblConnectionStatus.setText("No ABAP projects found. Use '+ Add Field' or 'Import from Clipboard'.");
        } else {
            lblConnectionStatus.setText("Open the CDS/table in editor first, then click 'Fetch Fields from System'.");
        }
    }

    private IProject getSelectedAbapProject() {
        int idx = cmbAbapProject.getSelectionIndex();
        if (idx >= 0 && idx < abapProjects.size()) {
            return abapProjects.get(idx);
        }
        return null;
    }

    private void createEntityForm() {
        Group grpEntity = new Group(inner, SWT.NONE);
        grpEntity.setText("Entity Properties");
        grpEntity.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grpEntity.setLayout(new GridLayout(4, false));

        // Row 1: Name + Object Name
        new Label(grpEntity, SWT.NONE).setText("Name:");
        txtName = new Text(grpEntity, SWT.BORDER);
        txtName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtName.addModifyListener(e -> {
            if (currentEntity != null) {
                currentEntity.setName(txtName.getText().trim());
                currentEntity.generateObjectNames(model.getPrefix());
                txtObjectName.setText(currentEntity.getObjectName());
                txtProjectionName.setText(currentEntity.getProjectionName());
            }
        });

        new Label(grpEntity, SWT.NONE).setText("Data Model (ZI_*):");
        txtObjectName = new Text(grpEntity, SWT.BORDER | SWT.READ_ONLY);
        txtObjectName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Row 2: Source + Projection
        new Label(grpEntity, SWT.NONE).setText("Source:");
        txtSource = new Text(grpEntity, SWT.BORDER);
        txtSource.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtSource.addModifyListener(e -> {
            if (currentEntity != null) {
                currentEntity.setUnderlyingSource(txtSource.getText().trim());
            }
        });

        new Label(grpEntity, SWT.NONE).setText("Projection (ZC_*):");
        txtProjectionName = new Text(grpEntity, SWT.BORDER | SWT.READ_ONLY);
        txtProjectionName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Row 3: Source Type + Parent Key
        new Label(grpEntity, SWT.NONE).setText("Source Type:");
        cmbSourceType = new Combo(grpEntity, SWT.READ_ONLY);
        cmbSourceType.setItems("Table", "CDS View");
        cmbSourceType.select(0);
        cmbSourceType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cmbSourceType.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity != null) {
                    currentEntity.setSourceType(cmbSourceType.getSelectionIndex() == 0
                        ? EntityDefinition.SourceType.TABLE
                        : EntityDefinition.SourceType.CDS);
                }
            }
        });

        new Label(grpEntity, SWT.NONE).setText("Parent Key Field:");
        txtParentKey = new Text(grpEntity, SWT.BORDER);
        txtParentKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtParentKey.addModifyListener(e -> {
            if (currentEntity != null) {
                currentEntity.setParentKeyField(txtParentKey.getText().trim());
            }
        });

        // Row 4: Draft
        chkDraft = new Button(grpEntity, SWT.CHECK);
        chkDraft.setText("Draft Enabled");
        chkDraft.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
        chkDraft.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity != null) {
                    currentEntity.setDraftEnabled(chkDraft.getSelection());
                }
            }
        });
    }

    private void createFieldTable() {
        Group grpFields = new Group(inner, SWT.NONE);
        grpFields.setText("Fields");
        grpFields.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        grpFields.setLayout(new GridLayout(1, false));

        // ===== FIELD ACTION BUTTONS =====
        Composite btnBar = new Composite(grpFields, SWT.NONE);
        btnBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnBar.setLayout(new GridLayout(6, false));

        // Fetch from system
        Button btnFetch = new Button(btnBar, SWT.PUSH);
        btnFetch.setText(">> Fetch Fields from System <<");
        btnFetch.setToolTipText("Connect to SAP system and fetch CDS/table fields automatically");
        btnFetch.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                fetchFieldsFromSystem();
            }
        });

        // Import from clipboard
        Button btnImportClipboard = new Button(btnBar, SWT.PUSH);
        btnImportClipboard.setText("Import from Clipboard");
        btnImportClipboard.setToolTipText("Paste field definitions from SE11/SE16 or a text list.\nFormat per line: FIELD_NAME  TYPE  LENGTH  LABEL");
        btnImportClipboard.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                importFieldsFromClipboard();
            }
        });

        // Separator
        new Label(btnBar, SWT.SEPARATOR | SWT.VERTICAL)
            .setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, true));

        Button btnAdd = new Button(btnBar, SWT.PUSH);
        btnAdd.setText("+ Add Field");
        btnAdd.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addFieldManually();
            }
        });

        Button btnAddKey = new Button(btnBar, SWT.PUSH);
        btnAddKey.setText("+ Add UUID Key");
        btnAddKey.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (currentEntity != null) {
                    FieldDefinition keyField = new FieldDefinition(
                        currentEntity.getName() + "UUID", "sysuuid_x16",
                        currentEntity.getName() + " UUID", true);
                    keyField.setReadOnly(true);
                    currentEntity.addField(keyField);
                    fieldTable.refresh();
                }
            }
        });

        Button btnRemove = new Button(btnBar, SWT.PUSH);
        btnRemove.setText("- Remove");
        btnRemove.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                removeSelectedField();
            }
        });

        // ===== FIELD TABLE =====
        fieldTable = new TableViewer(grpFields,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL);
        Table table = fieldTable.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Columns
        createColumn("Name", 150);
        createColumn("ABAP Name", 150);
        createColumn("Type", 150);
        createColumn("Label", 150);
        createColumn("Key", 50);
        createColumn("Mandatory", 70);
        createColumn("ReadOnly", 70);

        fieldTable.setContentProvider(ArrayContentProvider.getInstance());
        fieldTable.setLabelProvider(new FieldLabelProvider());

        // Cell editors for inline editing
        String[] abapTypes = AbapTypeRegistry.getTypeNames();
        fieldTable.setColumnProperties(new String[]{
            "name", "abapName", "type", "label", "key", "mandatory", "readOnly"});

        CellEditor[] editors = new CellEditor[7];
        editors[0] = new TextCellEditor(table);
        editors[1] = new TextCellEditor(table);
        editors[2] = new ComboBoxCellEditor(table, abapTypes, SWT.READ_ONLY);
        editors[3] = new TextCellEditor(table);
        editors[4] = new CheckboxCellEditor(table);
        editors[5] = new CheckboxCellEditor(table);
        editors[6] = new CheckboxCellEditor(table);
        fieldTable.setCellEditors(editors);
        fieldTable.setCellModifier(new FieldCellModifier());
    }

    private TableViewerColumn createColumn(String title, int width) {
        TableViewerColumn col = new TableViewerColumn(fieldTable, SWT.NONE);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.getColumn().setResizable(true);
        return col;
    }

    // ===== FETCH FROM SAP SYSTEM =====
    private void fetchFieldsFromSystem() {
        if (currentEntity == null) {
            showMessage(SWT.ICON_WARNING, "No Entity", "Please select an entity first.");
            return;
        }

        String source = currentEntity.getUnderlyingSource();
        if (source == null || source.isEmpty()) {
            showMessage(SWT.ICON_WARNING, "No Source",
                "Please enter a Source table or CDS view name in the 'Source' field first.");
            return;
        }

        IProject project = getSelectedAbapProject();
        if (project == null) {
            showMessage(SWT.ICON_WARNING, "No ABAP Project",
                "No ABAP project selected.\n"
                + "Please select an ABAP project from the dropdown at the top.");
            return;
        }

        // Show progress
        lblConnectionStatus.setText("Fetching fields for '" + source + "' from " + project.getName() + "...");

        AdtFieldFetcher fetcher = AdtFieldFetcher.getInstance();
        try {
            List<FieldDefinition> fields;
            boolean isCds = currentEntity.getSourceType() == EntityDefinition.SourceType.CDS;

            if (isCds) {
                fields = fetcher.fetchCdsFields(project, source);
            } else {
                fields = fetcher.fetchTableFields(project, source);
            }

            if (fields.isEmpty()) {
                lblConnectionStatus.setText("No fields found for '" + source + "'.");
                showMessage(SWT.ICON_WARNING, "No Fields",
                    "No fields found for '" + source + "' in " + project.getName() + ".\n\n"
                    + "Check:\n"
                    + "- Is the object name correct?\n"
                    + "- Is Source Type correct (Table vs CDS View)?\n"
                    + "- Does it exist in system " + project.getName() + "?");
                return;
            }

            lblConnectionStatus.setText(fields.size() + " fields loaded from '" + source + "'.");
            applyFetchedFields(fields, source);

        } catch (Exception ex) {
            lblConnectionStatus.setText("Fetch failed. See error details.");
            showMessage(SWT.ICON_ERROR, "Fetch Failed",
                ex.getMessage() + "\n\n"
                + "Alternative: Use 'Import from Clipboard'.");
        }
    }

    // ===== IMPORT FROM CLIPBOARD =====
    private void importFieldsFromClipboard() {
        if (currentEntity == null) {
            showMessage(SWT.ICON_WARNING, "No Entity", "Please select an entity first.");
            return;
        }

        // Read clipboard
        Clipboard clipboard = new Clipboard(inner.getDisplay());
        String text = (String) clipboard.getContents(TextTransfer.getInstance());
        clipboard.dispose();

        if (text == null || text.trim().isEmpty()) {
            showMessage(SWT.ICON_WARNING, "Empty Clipboard",
                "Copy field definitions to clipboard first.\n\n"
                + "Supported formats:\n"
                + "1. From SE11: Copy the field list from table/structure display\n"
                + "2. Simple text: One field per line\n"
                + "   FIELD_NAME  CHAR  40  Field Label\n"
                + "   FIELD_NAME2  NUMC  10  Another Label\n"
                + "3. Tab-separated: Paste from spreadsheet\n"
                + "   FieldName\\tCHAR\\t40\\tLabel");
            return;
        }

        List<FieldDefinition> fields = parseClipboardText(text);

        if (fields.isEmpty()) {
            showMessage(SWT.ICON_WARNING, "Parse Error",
                "Could not parse field definitions from clipboard.\n\n"
                + "Expected format (one field per line):\n"
                + "FIELD_NAME  TYPE  LENGTH  LABEL\n\n"
                + "Or just field names, one per line:\n"
                + "TRAVEL_ID\nAGENCY_ID\nCUSTOMER_ID");
            return;
        }

        applyFetchedFields(fields, "clipboard");
    }

    private List<FieldDefinition> parseClipboardText(String text) {
        List<FieldDefinition> fields = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.startsWith("-")) {
                continue;
            }

            // Split by tabs or multiple spaces
            String[] parts = line.split("[\\t]+|\\s{2,}");

            if (parts.length == 0) continue;

            String fieldName = parts[0].trim();
            // Skip header-like lines
            if (fieldName.equalsIgnoreCase("FIELDNAME") || fieldName.equalsIgnoreCase("Field")
                || fieldName.equalsIgnoreCase("Name") || fieldName.equalsIgnoreCase("Column")) {
                continue;
            }

            FieldDefinition field = new FieldDefinition();
            field.setAbapName(fieldName.toLowerCase());
            field.setName(toCamelCase(fieldName));

            if (parts.length >= 2) {
                String typeOrLen = parts[1].trim();
                field.setAbapType(resolveType(typeOrLen, parts.length >= 3 ? parts[2].trim() : null));
            } else {
                field.setAbapType("abap.char(40)");
            }

            if (parts.length >= 4) {
                field.setLabel(parts[3].trim());
            } else {
                field.setLabel(camelToLabel(field.getName()));
            }

            // Detect key fields by naming convention
            if (fieldName.toUpperCase().endsWith("_ID") || fieldName.toUpperCase().endsWith("UUID")
                || fieldName.toUpperCase().equals("MANDT") || fieldName.toUpperCase().equals("CLIENT")) {
                // Don't auto-set key, let user decide
            }

            // Skip client field
            if (fieldName.equalsIgnoreCase("MANDT") || fieldName.equalsIgnoreCase("CLIENT")) {
                continue;
            }

            fields.add(field);
        }

        return fields;
    }

    private void applyFetchedFields(List<FieldDefinition> fields, String source) {
        MessageBox confirm = new MessageBox(inner.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirm.setText("Fields Found: " + fields.size());
        confirm.setMessage(fields.size() + " fields found from '" + source + "'.\n\n"
            + "Replace existing fields?\n"
            + "  YES = Clear current fields and add new ones\n"
            + "  NO = Append to existing fields");
        int result = confirm.open();

        if (result == SWT.YES) {
            currentEntity.getFields().clear();
        }

        for (FieldDefinition field : fields) {
            currentEntity.addField(field);
        }

        fieldTable.setInput(currentEntity.getFields());
        fieldTable.refresh();

        showMessage(SWT.ICON_INFORMATION, "Fields Loaded",
            fields.size() + " fields loaded from '" + source + "'.\n\n"
            + "You can now:\n"
            + "- Edit field names/types by clicking cells\n"
            + "- Mark Key fields in the Key column\n"
            + "- Go to UI Config tab to set list/filter positions");
    }

    // ===== MANUAL ADD =====
    private void addFieldManually() {
        if (currentEntity == null) return;

        // Open a small dialog for field details
        Shell dialog = new Shell(inner.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText("Add Field");
        dialog.setLayout(new GridLayout(2, false));
        dialog.setSize(400, 250);

        new Label(dialog, SWT.NONE).setText("Field Name:");
        Text txtFieldName = new Text(dialog, SWT.BORDER);
        txtFieldName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtFieldName.setMessage("e.g. TravelId or travel_id");

        new Label(dialog, SWT.NONE).setText("ABAP Type:");
        Combo cmbType = new Combo(dialog, SWT.READ_ONLY);
        cmbType.setItems(AbapTypeRegistry.getTypeNames());
        cmbType.select(0); // default abap.char(10)
        cmbType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(dialog, SWT.NONE).setText("Label:");
        Text txtLabel = new Text(dialog, SWT.BORDER);
        txtLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button chkKey = new Button(dialog, SWT.CHECK);
        chkKey.setText("Key Field");
        chkKey.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

        // Auto-fill label from name
        txtFieldName.addModifyListener(e -> {
            String name = txtFieldName.getText().trim();
            if (!name.isEmpty() && txtLabel.getText().isEmpty()) {
                txtLabel.setText(camelToLabel(toCamelCase(name)));
            }
        });

        Composite btnBar = new Composite(dialog, SWT.NONE);
        btnBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
        btnBar.setLayout(new GridLayout(2, true));

        Button btnOk = new Button(btnBar, SWT.PUSH);
        btnOk.setText("Add");
        btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String name = txtFieldName.getText().trim();
                if (name.isEmpty()) return;

                FieldDefinition field = new FieldDefinition();
                field.setName(toCamelCase(name));
                field.setAbapName(name.contains("_") ? name.toLowerCase() : toSnakeCase(name));
                field.setAbapType(AbapTypeRegistry.getTypeNames()[cmbType.getSelectionIndex()]);
                field.setLabel(txtLabel.getText().trim().isEmpty() ? camelToLabel(toCamelCase(name)) : txtLabel.getText().trim());
                field.setKey(chkKey.getSelection());

                currentEntity.addField(field);
                fieldTable.refresh();
                dialog.close();
            }
        });

        Button btnCancel = new Button(btnBar, SWT.PUSH);
        btnCancel.setText("Cancel");
        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                dialog.close();
            }
        });

        dialog.open();
    }

    private void removeSelectedField() {
        IStructuredSelection selection = fieldTable.getStructuredSelection();
        for (Object obj : selection.toList()) {
            FieldDefinition field = (FieldDefinition) obj;
            currentEntity.removeField(field.getName());
        }
        fieldTable.refresh();
    }

    public void setEntity(EntityDefinition entity) {
        this.currentEntity = entity;
        if (entity != null) {
            txtName.setText(entity.getName() != null ? entity.getName() : "");
            txtObjectName.setText(entity.getObjectName() != null ? entity.getObjectName() : "");
            txtProjectionName.setText(entity.getProjectionName() != null ? entity.getProjectionName() : "");
            txtSource.setText(entity.getUnderlyingSource() != null ? entity.getUnderlyingSource() : "");
            cmbSourceType.select(entity.getSourceType() == EntityDefinition.SourceType.TABLE ? 0 : 1);
            txtParentKey.setText(entity.getParentKeyField() != null ? entity.getParentKeyField() : "");
            chkDraft.setSelection(entity.isDraftEnabled());
            fieldTable.setInput(entity.getFields());
        }
    }

    public Control getControl() {
        return scrolled;
    }

    // ===== UTILITY METHODS =====

    private void showMessage(int icon, String title, String message) {
        MessageBox msgBox = new MessageBox(inner.getShell(), icon | SWT.OK);
        msgBox.setText(title);
        msgBox.setMessage(message);
        msgBox.open();
    }

    private String toCamelCase(String input) {
        if (input == null) return "";
        // Already camelCase?
        if (!input.contains("_") && !input.equals(input.toUpperCase())) return input;

        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : input.toLowerCase().toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString();
    }

    private String toSnakeCase(String camelCase) {
        if (camelCase == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private String camelToLabel(String camelCase) {
        if (camelCase == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }

    private String resolveType(String type, String length) {
        if (type == null) return "abap.char(40)";
        type = type.toUpperCase();
        switch (type) {
            case "CHAR": return "abap.char(" + (length != null ? length : "40") + ")";
            case "NUMC": return "abap.numc(" + (length != null ? length : "10") + ")";
            case "DATS": return "abap.dats";
            case "TIMS": return "abap.tims";
            case "INT4": case "INT": return "abap.int4";
            case "INT8": return "abap.int8";
            case "DEC": return "abap.dec(" + (length != null ? length : "15") + ",2)";
            case "CURR": return "abap.curr(" + (length != null ? length : "15") + ",2)";
            case "QUAN": return "abap.quan(" + (length != null ? length : "13") + ",3)";
            case "STRING": return "abap.string";
            case "FLTP": return "abap.fltp";
            case "RAW": return "abap.raw(" + (length != null ? length : "16") + ")";
            case "CLNT": return "abap.clnt";
            case "LANG": return "abap.lang";
            case "CUKY": return "abap.cuky";
            case "UNIT": return "abap.unit";
            case "UTCLONG": return "abap.utclong";
            default: return "abap.char(" + (length != null ? length : "40") + ")";
        }
    }

    // ===== TABLE LABEL PROVIDER =====
    private class FieldLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int columnIndex) {
            FieldDefinition f = (FieldDefinition) element;
            switch (columnIndex) {
                case 0: return f.getName();
                case 1: return f.getAbapName();
                case 2: return f.getAbapType();
                case 3: return f.getLabel();
                case 4: return f.isKey() ? "Yes" : "";
                case 5: return f.isMandatory() ? "Yes" : "";
                case 6: return f.isReadOnly() ? "Yes" : "";
                default: return "";
            }
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int columnIndex) {
            return null;
        }
    }

    // ===== CELL MODIFIER FOR INLINE EDITING =====
    private class FieldCellModifier implements ICellModifier {
        @Override
        public boolean canModify(Object element, String property) {
            return true;
        }

        @Override
        public Object getValue(Object element, String property) {
            FieldDefinition f = (FieldDefinition) element;
            switch (property) {
                case "name": return f.getName();
                case "abapName": return f.getAbapName();
                case "type":
                    String[] types = AbapTypeRegistry.getTypeNames();
                    for (int i = 0; i < types.length; i++) {
                        if (types[i].equals(f.getAbapType())) return i;
                    }
                    return 0;
                case "label": return f.getLabel();
                case "key": return f.isKey();
                case "mandatory": return f.isMandatory();
                case "readOnly": return f.isReadOnly();
                default: return "";
            }
        }

        @Override
        public void modify(Object element, String property, Object value) {
            if (element instanceof org.eclipse.swt.widgets.TableItem) {
                element = ((org.eclipse.swt.widgets.TableItem) element).getData();
            }
            FieldDefinition f = (FieldDefinition) element;
            switch (property) {
                case "name": f.setName((String) value); break;
                case "abapName": f.setAbapName((String) value); break;
                case "type":
                    String[] types = AbapTypeRegistry.getTypeNames();
                    int idx = (Integer) value;
                    if (idx >= 0 && idx < types.length) f.setAbapType(types[idx]);
                    break;
                case "label": f.setLabel((String) value); break;
                case "key": f.setKey((Boolean) value); break;
                case "mandatory": f.setMandatory((Boolean) value); break;
                case "readOnly": f.setReadOnly((Boolean) value); break;
            }
            fieldTable.update(f, null);
        }
    }
}
