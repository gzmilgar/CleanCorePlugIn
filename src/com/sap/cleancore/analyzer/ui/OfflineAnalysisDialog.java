package com.sap.cleancore.analyzer.ui;

import com.sap.cleancore.analyzer.model.TransformationScenario;
import com.sap.cleancore.analyzer.preferences.CleanCorePreferences;
import com.sap.cleancore.analyzer.scenario.ScenarioRegistry;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.io.File;
import java.util.List;

/**
 * Minimal pre-analysis dialog for the offline (no-ADT) folder/disk workflows.
 * Lets the user pick a {@link TransformationScenario} and an optional
 * integration extract file. The scope/object-type controls of
 * {@link AnalysisWizardDialog} are irrelevant offline (the object set is the
 * folder contents), so this dialog stays deliberately small.
 */
public class OfflineAnalysisDialog extends Dialog {

    private Combo scenarioCombo;
    private Text extractText;
    private List<TransformationScenario> scenarioList;

    private TransformationScenario scenarioResult;
    private File integrationExtractFile;

    private final String headerInfo;

    public OfflineAnalysisDialog(Shell parent, String headerInfo) {
        super(parent);
        this.headerInfo = headerInfo;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Analyze Folder (Offline) — choose scenario");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(1, false));
        ((GridData) root.getLayoutData()).widthHint = 520;

        if (headerInfo != null && !headerInfo.isEmpty()) {
            Label info = new Label(root, SWT.WRAP);
            info.setText(headerInfo);
            GridData ig = new GridData(SWT.FILL, SWT.CENTER, true, false);
            ig.widthHint = 500;
            info.setLayoutData(ig);
        }

        Composite grid = new Composite(root, SWT.NONE);
        grid.setLayout(new GridLayout(2, false));
        grid.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(grid, SWT.NONE).setText("Transformation scenario:");
        scenarioCombo = new Combo(grid, SWT.READ_ONLY);
        scenarioCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scenarioList = ScenarioRegistry.getInstance().getAll();
        String preselId = CleanCorePreferences.getScenarioId();
        if (preselId == null || preselId.isEmpty()) {
            TransformationScenario def = ScenarioRegistry.getInstance().getDefault();
            preselId = (def != null ? def.getId() : null);
        }
        int sel = 0;
        for (int i = 0; i < scenarioList.size(); i++) {
            TransformationScenario s = scenarioList.get(i);
            scenarioCombo.add(s.getDisplayName() != null ? s.getDisplayName() : s.getId());
            if (preselId != null && preselId.equalsIgnoreCase(s.getId())) sel = i;
        }
        if (scenarioCombo.getItemCount() > 0) scenarioCombo.select(sel);

        new Label(grid, SWT.NONE).setText("Integration extract (optional):");
        Composite row = new Composite(grid, SWT.NONE);
        GridLayout rl = new GridLayout(2, false);
        rl.marginWidth = 0; rl.marginHeight = 0;
        row.setLayout(rl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        extractText = new Text(row, SWT.BORDER);
        extractText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        extractText.setMessage("Z_TRANSFORM_INVENTORY JSON (optional)");
        Button browse = new Button(row, SWT.PUSH);
        browse.setText("Browse...");
        browse.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                FileDialog fd = new FileDialog(getShell(), SWT.OPEN);
                fd.setFilterExtensions(new String[]{"*.json", "*.*"});
                String p = fd.open();
                if (p != null) extractText.setText(p);
            }
        });

        return root;
    }

    @Override
    protected void okPressed() {
        if (scenarioCombo != null) {
            int idx = scenarioCombo.getSelectionIndex();
            if (idx >= 0 && scenarioList != null && idx < scenarioList.size()) {
                scenarioResult = scenarioList.get(idx);
                CleanCorePreferences.setScenarioId(scenarioResult.getId());
            }
        }
        if (scenarioResult == null) scenarioResult = ScenarioRegistry.getInstance().getDefault();

        if (extractText != null) {
            String p = extractText.getText() != null ? extractText.getText().trim() : "";
            if (!p.isEmpty()) {
                File ef = new File(p);
                if (ef.isFile()) integrationExtractFile = ef;
            }
        }
        super.okPressed();
    }

    public TransformationScenario getScenario() { return scenarioResult; }
    public File getIntegrationExtractFile() { return integrationExtractFile; }
}
