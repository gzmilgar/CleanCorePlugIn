package com.rap.generator.ui.dialogs;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/**
 * Dialog that collects deployment parameters for abapGit-compatible export:
 * - Target ABAP Package
 * - Transport Request (optional for $TMP)
 * - Export Directory
 *
 * The collected values are used to generate an abapGit folder structure
 * that can be imported into SAP via abapGit.
 */
public class DeployDialog {

    private String packageName;
    private String transportRequest;
    private String exportDirectory;
    private boolean confirmed = false;

    /**
     * Opens the dialog and blocks until the user confirms or cancels.
     * @return true if the user clicked OK, false if cancelled
     */
    public boolean open(Shell parent) {
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        dialog.setText("Export as abapGit Repository");
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(520, 380);

        // --- Description ---
        Label lblDesc = new Label(dialog, SWT.WRAP);
        lblDesc.setText(
            "Export the generated RAP artifacts in an abapGit-compatible folder structure. "
            + "You can then import the repository into your SAP system using abapGit.\n\n"
            + "The .abapgit.xml configuration file will be generated with the package name "
            + "so that abapGit places the objects in the correct package."
        );
        GridData gdDesc = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gdDesc.widthHint = 480;
        lblDesc.setLayoutData(gdDesc);

        // --- Package & Transport ---
        Group grpDeploy = new Group(dialog, SWT.NONE);
        grpDeploy.setText("SAP Package Settings");
        grpDeploy.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grpDeploy.setLayout(new GridLayout(3, false));

        // Package
        new Label(grpDeploy, SWT.NONE).setText("Package:");
        Text txtPackage = new Text(grpDeploy, SWT.BORDER);
        txtPackage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        txtPackage.setMessage("e.g. $TMP, ZRAP_DEMO, ZPACKAGE");
        txtPackage.setText("$TMP");

        // Transport Request
        new Label(grpDeploy, SWT.NONE).setText("Transport Request:");
        Text txtTransport = new Text(grpDeploy, SWT.BORDER);
        txtTransport.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtTransport.setMessage("e.g. NS4K900001 (optional for $TMP)");

        Label lblTrHint = new Label(grpDeploy, SWT.NONE);
        lblTrHint.setText("(optional for local)");

        // Hint label for transport
        Label lblTransportInfo = new Label(grpDeploy, SWT.WRAP);
        lblTransportInfo.setText("Leave empty when using $TMP (local objects). Required for transportable packages.");
        GridData gdInfo = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
        gdInfo.widthHint = 440;
        lblTransportInfo.setLayoutData(gdInfo);

        // --- Export Directory ---
        Group grpDir = new Group(dialog, SWT.NONE);
        grpDir.setText("Export Location");
        grpDir.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grpDir.setLayout(new GridLayout(3, false));

        new Label(grpDir, SWT.NONE).setText("Directory:");
        Text txtDir = new Text(grpDir, SWT.BORDER | SWT.READ_ONLY);
        txtDir.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtDir.setMessage("Select export directory...");

        Button btnBrowse = new Button(grpDir, SWT.PUSH);
        btnBrowse.setText("Browse...");
        btnBrowse.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                DirectoryDialog dirDlg = new DirectoryDialog(dialog, SWT.OPEN);
                dirDlg.setText("Select Export Directory");
                dirDlg.setMessage("Choose where to create the abapGit repository folder:");
                String selected = dirDlg.open();
                if (selected != null) {
                    txtDir.setText(selected);
                }
            }
        });

        // --- Buttons ---
        Composite btnBar = new Composite(dialog, SWT.NONE);
        btnBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        btnBar.setLayout(new GridLayout(2, true));

        Button btnOk = new Button(btnBar, SWT.PUSH);
        btnOk.setText("Export");
        GridData gdBtn = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gdBtn.widthHint = 90;
        btnOk.setLayoutData(gdBtn);

        Button btnCancel = new Button(btnBar, SWT.PUSH);
        btnCancel.setText("Cancel");
        btnCancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // --- Validation Label ---
        Label lblValidation = new Label(dialog, SWT.WRAP);
        lblValidation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String pkg = txtPackage.getText().trim().toUpperCase();
                String tr = txtTransport.getText().trim().toUpperCase();
                String dir = txtDir.getText().trim();

                // Validate package
                if (pkg.isEmpty()) {
                    lblValidation.setText("Please enter a package name.");
                    return;
                }

                // Validate transport for non-local packages
                boolean isLocal = pkg.startsWith("$");
                if (!isLocal && tr.isEmpty()) {
                    lblValidation.setText("Transport request is required for package " + pkg
                        + ". Use $TMP for local objects without transport.");
                    return;
                }

                // Validate directory
                if (dir.isEmpty()) {
                    lblValidation.setText("Please select an export directory.");
                    return;
                }

                packageName = pkg;
                transportRequest = tr.isEmpty() ? null : tr;
                exportDirectory = dir;
                confirmed = true;
                dialog.close();
            }
        });

        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                dialog.close();
            }
        });

        // Set default button
        dialog.setDefaultButton(btnOk);

        // Center the dialog on parent
        dialog.setLocation(
            parent.getLocation().x + (parent.getSize().x - dialog.getSize().x) / 2,
            parent.getLocation().y + (parent.getSize().y - dialog.getSize().y) / 2
        );

        dialog.open();
        Display display = parent.getDisplay();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }

        return confirmed;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getTransportRequest() {
        return transportRequest;
    }

    public String getExportDirectory() {
        return exportDirectory;
    }
}
