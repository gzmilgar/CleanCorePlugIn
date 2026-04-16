package com.rap.generator.ui.dialogs;

import com.rap.generator.data.AdtConnectionService;
import com.rap.generator.data.AdtConnectionService.ProjectInfo;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.List;

public class ConnectionDialog {

    private boolean connected = false;

    // Manual connection fields (accessible from project selection)
    private Text txtUrl;
    private Text txtClient;
    private Text txtUser;
    private Text txtPass;

    public boolean open(Shell parent) {
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        dialog.setText("Connect to SAP System");
        dialog.setLayout(new GridLayout(1, false));
        dialog.setSize(580, 480);

        List<ProjectInfo> projects = AdtConnectionService.findAbapProjects();

        // ===== OPTION 1: ADT Project =====
        Group grp1 = new Group(dialog, SWT.NONE);
        grp1.setText("Select ABAP Project (auto-detected from Eclipse workspace)");
        grp1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grp1.setLayout(new GridLayout(2, false));

        new Label(grp1, SWT.NONE).setText("Project:");
        Combo cmbProjects = new Combo(grp1, SWT.READ_ONLY);
        cmbProjects.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        for (ProjectInfo p : projects) {
            String label = p.displayName + "  [" + p.systemId + ", " + p.client + ", " + p.user + "]";
            cmbProjects.add(label);
        }
        if (!projects.isEmpty()) cmbProjects.select(0);

        Label lblInfo = new Label(grp1, SWT.WRAP);
        lblInfo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        if (projects.isEmpty()) {
            lblInfo.setText("No ABAP projects found in Eclipse workspaces.\nUse manual connection below.");
            lblInfo.setForeground(new Color(dialog.getDisplay(), 180, 0, 0));
        } else {
            ProjectInfo first = projects.get(0);
            lblInfo.setText(formatProjectInfo(first));
        }

        Button btnConnect1 = new Button(grp1, SWT.PUSH);
        btnConnect1.setText("Connect (still needs password below)");
        btnConnect1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        btnConnect1.setEnabled(!projects.isEmpty());

        Label lblStatus1 = new Label(grp1, SWT.NONE);
        lblStatus1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        // ===== SEPARATOR =====
        new Label(dialog, SWT.SEPARATOR | SWT.HORIZONTAL)
            .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // ===== MANUAL / AUTO-FILLED CONNECTION =====
        Group grp2 = new Group(dialog, SWT.NONE);
        grp2.setText("Connection Details");
        grp2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        grp2.setLayout(new GridLayout(2, false));

        new Label(grp2, SWT.NONE).setText("System URL:");
        txtUrl = new Text(grp2, SWT.BORDER);
        txtUrl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtUrl.setMessage("https://hostname:44300");

        new Label(grp2, SWT.NONE).setText("Client:");
        txtClient = new Text(grp2, SWT.BORDER);
        txtClient.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtClient.setText("100");

        new Label(grp2, SWT.NONE).setText("Username:");
        txtUser = new Text(grp2, SWT.BORDER);
        txtUser.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(grp2, SWT.NONE).setText("Password:");
        txtPass = new Text(grp2, SWT.BORDER | SWT.PASSWORD);
        txtPass.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Auto-fill from first project
        if (!projects.isEmpty()) {
            autoFillFromProject(projects.get(0));
        }

        // Project selection auto-fills the fields
        cmbProjects.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int idx = cmbProjects.getSelectionIndex();
                if (idx >= 0 && idx < projects.size()) {
                    ProjectInfo p = projects.get(idx);
                    lblInfo.setText(formatProjectInfo(p));
                    autoFillFromProject(p);
                }
            }
        });

        // Connect button for project (uses auto-filled URL + typed password)
        btnConnect1.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doConnect(dialog, lblStatus1);
            }
        });

        // Direct connect button
        Button btnConnect2 = new Button(grp2, SWT.PUSH);
        btnConnect2.setText("Connect");
        btnConnect2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Label lblStatus2 = new Label(grp2, SWT.NONE);
        lblStatus2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        btnConnect2.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doConnect(dialog, lblStatus2);
            }
        });

        // Cancel
        Button btnCancel = new Button(dialog, SWT.PUSH);
        btnCancel.setText("Cancel");
        btnCancel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) { dialog.close(); }
        });

        dialog.open();
        Display display = parent.getDisplay();
        while (!dialog.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        return connected;
    }

    private void autoFillFromProject(ProjectInfo p) {
        if (p.url != null) txtUrl.setText(p.url);
        if (p.client != null) txtClient.setText(p.client);
        if (p.user != null) txtUser.setText(p.user);
    }

    private String formatProjectInfo(ProjectInfo p) {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(p.systemId);
        sb.append(" | Client: ").append(p.client);
        sb.append(" | User: ").append(p.user);
        if (p.url != null) sb.append("\nURL: ").append(p.url);
        else if (p.server != null) sb.append("\nServer: ").append(p.server);
        return sb.toString();
    }

    private void doConnect(Shell dialog, Label lblStatus) {
        String url = txtUrl.getText().trim();
        String client = txtClient.getText().trim();
        String user = txtUser.getText().trim();
        String pass = txtPass.getText();

        if (url.isEmpty()) {
            lblStatus.setText("Enter System URL.");
            lblStatus.setForeground(new Color(dialog.getDisplay(), 180, 0, 0));
            return;
        }
        if (pass.isEmpty()) {
            lblStatus.setText("Enter your SAP password.");
            lblStatus.setForeground(new Color(dialog.getDisplay(), 180, 0, 0));
            return;
        }

        lblStatus.setText("Connecting...");
        lblStatus.setForeground(null);

        boolean ok = AdtConnectionService.getInstance().connectManual(url, client, user, pass);
        if (ok) {
            connected = true;
            lblStatus.setText("Connected!");
            lblStatus.setForeground(new Color(dialog.getDisplay(), 0, 128, 0));
            dialog.getDisplay().timerExec(700, () -> {
                if (!dialog.isDisposed()) dialog.close();
            });
        } else {
            lblStatus.setText("Connection failed. Check URL/password. Try port 443 or 8443.");
            lblStatus.setForeground(new Color(dialog.getDisplay(), 180, 0, 0));
        }
    }
}
