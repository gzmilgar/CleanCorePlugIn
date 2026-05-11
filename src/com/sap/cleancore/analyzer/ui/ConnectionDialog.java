package com.sap.cleancore.analyzer.ui;

import com.sap.cleancore.analyzer.data.AdtConnectionService;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.util.List;

/**
 * Customer-system picker.
 *
 * Design intent (per product requirement):
 *   - The analyst's Eclipse already has the customer's ABAP system registered
 *     as an ADT project (NS4_100_GILGAR_EN, CUSTOMER_PRD_001, ...). We pick one
 *     of those and reuse its existing session — no URL/credentials needed.
 *   - All mapping data (SAP Cloudification Repo, api.sap.com) is preloaded by
 *     CleanCoreBootstrapper into a local cache, so the customer system never
 *     needs internet access for the analysis to work.
 */
public class ConnectionDialog extends Dialog {

    private Combo projectCombo;
    private Text infoText;

    private List<IProject> projects;
    private boolean ok;

    public ConnectionDialog(Shell parent) { super(parent); }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Select customer ABAP system");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(2, false));
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint = 520;
        root.setLayoutData(gd);

        new Label(root, SWT.NONE).setText(
                "Pick the customer ABAP system you want to analyse. The list shows\n"
              + "every ADT project already configured in this Eclipse workspace —\n"
              + "the plug-in will reuse its existing session (no extra credentials).");
        GridData gdLabel = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gdLabel.horizontalSpan = 2;
        ((Label) root.getChildren()[root.getChildren().length - 1]).setLayoutData(gdLabel);

        new Label(root, SWT.NONE).setText("ABAP project:");
        projectCombo = new Combo(root, SWT.READ_ONLY);
        projectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        projects = AdtConnectionService.listAbapProjects();
        for (IProject p : projects) projectCombo.add(p.getName());
        if (!projects.isEmpty()) projectCombo.select(0);

        new Label(root, SWT.NONE).setText("");
        infoText = new Text(root, SWT.MULTI | SWT.WRAP | SWT.READ_ONLY);
        infoText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        if (projects.isEmpty()) {
            infoText.setText(
                    "No ADT projects found in this workspace.\n\n"
                  + "Add a customer system first:\n"
                  + "  File → New → ABAP Project, configure the connection,\n"
                  + "  then re-open this dialog.");
        } else {
            infoText.setText(
                    "Mapping data (SAP Cloudification Repo + api.sap.com) is loaded\n"
                  + "from the local cache populated when Eclipse first opened.\n"
                  + "Click OK to start analysing the selected system.");
        }

        return root;
    }

    @Override
    protected void okPressed() {
        if (projects == null || projects.isEmpty() || projectCombo.getSelectionIndex() < 0) {
            MessageDialog.openWarning(getShell(), "Connect",
                    "No ABAP project selected. Add one via File → New → ABAP Project.");
            return;
        }
        IProject p = projects.get(projectCombo.getSelectionIndex());
        AdtConnectionService adt = AdtConnectionService.getInstance();
        adt.connectViaProject(p, p.getName(), null, null);
        ok = adt.isConnected();
        if (!ok) {
            MessageDialog.openError(getShell(), "Connect failed",
                    "Could not bind to ADT project " + p.getName());
            return;
        }
        super.okPressed();
    }

    public boolean isConnected() { return ok; }
}
