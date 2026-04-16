package com.rap.generator.ui;

import com.rap.generator.data.SapReleaseDataService;
import com.rap.generator.generators.GeneratorEngine;
import com.rap.generator.generators.GeneratorEngine.GeneratedArtifact;
import com.rap.generator.model.*;
import com.rap.generator.model.ServiceConfig.BindingType;
import com.rap.generator.ui.panels.*;
import com.rap.generator.ui.widgets.EntityTreeViewer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import java.util.Map;

public class RapGeneratorView extends ViewPart {

    public static final String ID = "com.rap.generator.view";

    private RapApplication model = new RapApplication();
    private GeneratorEngine engine = new GeneratorEngine();

    // UI Components
    private EntityTreeViewer entityTree;
    private CTabFolder mainTabs;
    private EntityPanel entityPanel;
    private UiConfigPanel uiConfigPanel;
    private ActionPanel actionPanel;
    private ReleaseCheckPanel releaseCheckPanel;
    private CodeOutputPanel codeOutputPanel;

    // Service config widgets
    private Text txtServiceName;
    private Combo cmbBindingType;
    private Button chkDraftEnabled;
    private Text txtPrefix;

    @Override
    public void createPartControl(Composite parent) {
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createLeftPanel(sash);
        createRightPanel(sash);

        sash.setWeights(new int[]{25, 75});

        // Load cached SAP data on startup
        SapReleaseDataService.getInstance().loadFromCache();
    }

    private void createLeftPanel(Composite parent) {
        Composite leftPanel = new Composite(parent, SWT.BORDER);
        leftPanel.setLayout(new GridLayout(1, false));

        // Prefix config
        Composite prefixRow = new Composite(leftPanel, SWT.NONE);
        prefixRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        prefixRow.setLayout(new GridLayout(2, false));

        new Label(prefixRow, SWT.NONE).setText("Namespace Prefix:");
        txtPrefix = new Text(prefixRow, SWT.BORDER);
        txtPrefix.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtPrefix.setText("Z");
        txtPrefix.addModifyListener(e -> model.setPrefix(txtPrefix.getText().trim()));

        // Entity tree
        Label lblEntities = new Label(leftPanel, SWT.NONE);
        lblEntities.setText("Entities:");
        lblEntities.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        entityTree = new EntityTreeViewer(leftPanel, model);
        entityTree.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        entityTree.addSelectionListener(entity -> {
            if (entity != null) {
                if (entityPanel != null) entityPanel.setEntity(entity);
                if (uiConfigPanel != null) uiConfigPanel.setEntity(entity);
                if (actionPanel != null) actionPanel.setEntity(entity);
            }
        });

        // Add/Remove entity buttons
        Composite btnRow = new Composite(leftPanel, SWT.NONE);
        btnRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnRow.setLayout(new GridLayout(2, true));

        Button btnAddRoot = new Button(btnRow, SWT.PUSH);
        btnAddRoot.setText("+ Root");
        btnAddRoot.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnAddRoot.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addEntity(true);
            }
        });

        Button btnAddChild = new Button(btnRow, SWT.PUSH);
        btnAddChild.setText("+ Child");
        btnAddChild.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnAddChild.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addEntity(false);
            }
        });

        Button btnRemove = new Button(leftPanel, SWT.PUSH);
        btnRemove.setText("Remove Selected");
        btnRemove.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnRemove.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                EntityDefinition selected = entityTree.getSelectedEntity();
                if (selected != null) {
                    model.removeEntity(selected.getId());
                    entityTree.refresh();
                }
            }
        });

        // Separator
        new Label(leftPanel, SWT.SEPARATOR | SWT.HORIZONTAL)
            .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Service configuration
        Label lblService = new Label(leftPanel, SWT.NONE);
        lblService.setText("Service Configuration:");

        Composite svcGroup = new Composite(leftPanel, SWT.NONE);
        svcGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        svcGroup.setLayout(new GridLayout(2, false));

        new Label(svcGroup, SWT.NONE).setText("Name:");
        txtServiceName = new Text(svcGroup, SWT.BORDER);
        txtServiceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtServiceName.addModifyListener(e -> model.getService().setServiceName(txtServiceName.getText().trim()));

        new Label(svcGroup, SWT.NONE).setText("Binding:");
        cmbBindingType = new Combo(svcGroup, SWT.READ_ONLY);
        cmbBindingType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cmbBindingType.setItems("OData V4 - UI", "OData V2 - UI");
        cmbBindingType.select(0);
        cmbBindingType.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                model.getService().setBindingType(
                    cmbBindingType.getSelectionIndex() == 0 ? BindingType.ODATA_V4 : BindingType.ODATA_V2);
            }
        });

        chkDraftEnabled = new Button(leftPanel, SWT.CHECK);
        chkDraftEnabled.setText("Enable Draft");
        chkDraftEnabled.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        chkDraftEnabled.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                model.setDraftEnabled(chkDraftEnabled.getSelection());
            }
        });

        // Separator
        new Label(leftPanel, SWT.SEPARATOR | SWT.HORIZONTAL)
            .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Generate button
        Button btnGenerate = new Button(leftPanel, SWT.PUSH);
        btnGenerate.setText("Generate Code");
        btnGenerate.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnGenerate.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                generateCode();
            }
        });
    }

    private void createRightPanel(Composite parent) {
        mainTabs = new CTabFolder(parent, SWT.BORDER | SWT.BOTTOM);
        mainTabs.setSimple(false);

        try {
            CTabItem entityTab = new CTabItem(mainTabs, SWT.NONE);
            entityTab.setText("Entity Config");
            entityPanel = new EntityPanel(mainTabs, model);
            entityTab.setControl(entityPanel.getControl());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            CTabItem uiTab = new CTabItem(mainTabs, SWT.NONE);
            uiTab.setText("UI Config");
            uiConfigPanel = new UiConfigPanel(mainTabs, model);
            uiTab.setControl(uiConfigPanel.getControl());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            CTabItem actionsTab = new CTabItem(mainTabs, SWT.NONE);
            actionsTab.setText("Actions");
            actionPanel = new ActionPanel(mainTabs, model);
            actionsTab.setControl(actionPanel.getControl());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            CTabItem releaseTab = new CTabItem(mainTabs, SWT.NONE);
            releaseTab.setText("Release Check");
            releaseCheckPanel = new ReleaseCheckPanel(mainTabs);
            releaseTab.setControl(releaseCheckPanel.getControl());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            CTabItem codeTab = new CTabItem(mainTabs, SWT.NONE);
            codeTab.setText("Generated Code");
            codeOutputPanel = new CodeOutputPanel(mainTabs);
            codeTab.setControl(codeOutputPanel.getControl());
        } catch (Exception e) {
            e.printStackTrace();
        }

        mainTabs.setSelection(0);
    }

    private void addEntity(boolean isRoot) {
        Shell shell = getSite().getShell();
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(isRoot ? "Add Root Entity" : "Add Child Entity");
        dialog.setLayout(new GridLayout(2, false));
        dialog.setSize(400, 200);

        new Label(dialog, SWT.NONE).setText("Entity Name:");
        Text txtName = new Text(dialog, SWT.BORDER);
        txtName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(dialog, SWT.NONE).setText("Underlying Source:");
        Text txtSource = new Text(dialog, SWT.BORDER);
        txtSource.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(dialog, SWT.NONE).setText("Source Type:");
        Combo cmbType = new Combo(dialog, SWT.READ_ONLY);
        cmbType.setItems("Table", "CDS View");
        cmbType.select(0);
        cmbType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite btnBar = new Composite(dialog, SWT.NONE);
        btnBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
        btnBar.setLayout(new GridLayout(2, true));

        Button btnOk = new Button(btnBar, SWT.PUSH);
        btnOk.setText("OK");
        btnOk.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button btnCancel = new Button(btnBar, SWT.PUSH);
        btnCancel.setText("Cancel");
        btnCancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String name = txtName.getText().trim();
                String source = txtSource.getText().trim();
                if (!name.isEmpty() && !source.isEmpty()) {
                    EntityDefinition entity = new EntityDefinition(name);
                    entity.setUnderlyingSource(source);
                    entity.setSourceType(cmbType.getSelectionIndex() == 0
                        ? EntityDefinition.SourceType.TABLE
                        : EntityDefinition.SourceType.CDS);
                    entity.setRoot(isRoot);
                    entity.generateObjectNames(model.getPrefix());

                    if (!isRoot) {
                        EntityDefinition selectedParent = entityTree.getSelectedEntity();
                        if (selectedParent != null) {
                            entity.setParentId(selectedParent.getId());
                            selectedParent.addChild(entity.getId());
                        } else if (model.getRootEntity().isPresent()) {
                            EntityDefinition root = model.getRootEntity().get();
                            entity.setParentId(root.getId());
                            root.addChild(entity.getId());
                        }
                        entity.setFacetLabel(name + "s");
                    }

                    // Auto-generate service names from root entity
                    if (isRoot) {
                        model.getService().generateNames(model.getPrefix(), name);
                        if (txtServiceName != null) {
                            txtServiceName.setText(model.getService().getServiceName());
                        }
                    }

                    model.addEntity(entity);
                    entityTree.refresh();
                    if (entityPanel != null) entityPanel.setEntity(entity);
                    dialog.close();
                }
            }
        });

        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                dialog.close();
            }
        });

        dialog.open();
    }

    private void generateCode() {
        if (model.getEntities().isEmpty()) {
            MessageBox msgBox = new MessageBox(getSite().getShell(), SWT.ICON_WARNING | SWT.OK);
            msgBox.setText("No Entities");
            msgBox.setMessage("Please add at least one entity before generating code.");
            msgBox.open();
            return;
        }

        // Ensure draft admin fields are added if draft is enabled
        if (model.isDraftEnabled()) {
            for (EntityDefinition entity : model.getEntities()) {
                entity.setDraftEnabled(true);
                addDraftFieldsIfMissing(entity);
            }
        }

        Map<String, GeneratedArtifact> artifacts = engine.generateAll(model);
        if (codeOutputPanel != null) {
            codeOutputPanel.displayArtifacts(artifacts);
            mainTabs.setSelection(mainTabs.getItemCount() - 1);
        }
    }

    private void addDraftFieldsIfMissing(EntityDefinition entity) {
        boolean hasCreatedBy = entity.getFields().stream().anyMatch(f -> "CreatedBy".equals(f.getName()));
        if (!hasCreatedBy) {
            entity.addField(FieldDefinition.createdBy());
            entity.addField(FieldDefinition.createdAt());
            entity.addField(FieldDefinition.lastChangedBy());
            entity.addField(FieldDefinition.lastChangedAt());
            entity.addField(FieldDefinition.localLastChangedAt());
        }
    }

    @Override
    public void setFocus() {
        if (mainTabs != null) mainTabs.setFocus();
    }

}
