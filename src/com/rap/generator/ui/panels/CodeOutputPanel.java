package com.rap.generator.ui.panels;

import com.rap.generator.generators.GeneratorEngine.GeneratedArtifact;
import com.rap.generator.ui.widgets.AbapSourceViewer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class CodeOutputPanel {

    private Composite container;
    private CTabFolder codeTabs;
    private Map<String, GeneratedArtifact> currentArtifacts;

    public CodeOutputPanel(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));

        // Button bar
        Composite btnBar = new Composite(container, SWT.NONE);
        btnBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btnBar.setLayout(new GridLayout(4, false));

        Button btnCopyCurrent = new Button(btnBar, SWT.PUSH);
        btnCopyCurrent.setText("Copy Current Tab");
        btnCopyCurrent.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copyCurrentTab();
            }
        });

        Button btnCopyAll = new Button(btnBar, SWT.PUSH);
        btnCopyAll.setText("Copy All");
        btnCopyAll.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                copyAllArtifacts();
            }
        });

        Button btnExport = new Button(btnBar, SWT.PUSH);
        btnExport.setText("Export All to Directory");
        btnExport.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                exportToDirectory();
            }
        });

        Button btnPushAbapGit = new Button(btnBar, SWT.PUSH);
        btnPushAbapGit.setText("Export as abapGit");
        btnPushAbapGit.setToolTipText("Export in abapGit-compatible folder structure for import via abapGit");
        btnPushAbapGit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                exportAsAbapGit();
            }
        });

        // Code tabs
        codeTabs = new CTabFolder(container, SWT.BORDER | SWT.TOP);
        codeTabs.setSimple(false);
        codeTabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Initial empty state
        CTabItem emptyTab = new CTabItem(codeTabs, SWT.NONE);
        emptyTab.setText("No Code Generated");
        Label emptyLabel = new Label(codeTabs, SWT.CENTER);
        emptyLabel.setText("Click 'Generate Code' to see the generated ABAP artifacts here.");
        emptyTab.setControl(emptyLabel);
        codeTabs.setSelection(0);
    }

    public void displayArtifacts(Map<String, GeneratedArtifact> artifacts) {
        this.currentArtifacts = artifacts;

        // Clear existing tabs
        for (CTabItem item : codeTabs.getItems()) {
            item.dispose();
        }

        if (artifacts.isEmpty()) {
            CTabItem emptyTab = new CTabItem(codeTabs, SWT.NONE);
            emptyTab.setText("No Artifacts");
            Label emptyLabel = new Label(codeTabs, SWT.CENTER);
            emptyLabel.setText("No artifacts were generated.");
            emptyTab.setControl(emptyLabel);
            codeTabs.setSelection(0);
            return;
        }

        // Create a tab for each artifact
        for (Map.Entry<String, GeneratedArtifact> entry : artifacts.entrySet()) {
            CTabItem tab = new CTabItem(codeTabs, SWT.NONE);
            tab.setText(truncateTabName(entry.getKey()));
            tab.setToolTipText(entry.getKey());
            tab.setData(entry.getValue());

            AbapSourceViewer viewer = new AbapSourceViewer(codeTabs);
            viewer.setSource(entry.getValue().getSourceCode());
            tab.setControl(viewer.getControl());
        }

        codeTabs.setSelection(0);
    }

    private void copyCurrentTab() {
        CTabItem selected = codeTabs.getSelection();
        if (selected == null || selected.getData() == null) return;

        GeneratedArtifact artifact = (GeneratedArtifact) selected.getData();
        copyToClipboard(artifact.getSourceCode());

        showInfoMessage("Copied to clipboard: " + artifact.getObjectName());
    }

    private void copyAllArtifacts() {
        if (currentArtifacts == null || currentArtifacts.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, GeneratedArtifact> entry : currentArtifacts.entrySet()) {
            sb.append("*----------------------------------------------------------------------*\n");
            sb.append("* ").append(entry.getKey()).append("\n");
            sb.append("* File: ").append(entry.getValue().getFileName()).append("\n");
            sb.append("*----------------------------------------------------------------------*\n\n");
            sb.append(entry.getValue().getSourceCode());
            sb.append("\n\n");
        }

        copyToClipboard(sb.toString());
        showInfoMessage("All " + currentArtifacts.size() + " artifacts copied to clipboard.");
    }

    private void exportToDirectory() {
        if (currentArtifacts == null || currentArtifacts.isEmpty()) return;

        DirectoryDialog dlg = new DirectoryDialog(container.getShell(), SWT.SAVE);
        dlg.setText("Export Generated Code");
        dlg.setMessage("Select a directory to export all generated artifacts:");
        String dir = dlg.open();

        if (dir != null) {
            int count = 0;
            for (Map.Entry<String, GeneratedArtifact> entry : currentArtifacts.entrySet()) {
                GeneratedArtifact artifact = entry.getValue();
                File file = new File(dir, artifact.getFileName());
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(artifact.getSourceCode());
                    count++;
                } catch (IOException ex) {
                    showErrorMessage("Failed to write " + artifact.getFileName() + ": " + ex.getMessage());
                }
            }
            showInfoMessage("Exported " + count + " files to: " + dir);
        }
    }

    private void exportAsAbapGit() {
        if (currentArtifacts == null || currentArtifacts.isEmpty()) return;

        DirectoryDialog dlg = new DirectoryDialog(container.getShell(), SWT.SAVE);
        dlg.setText("Export as abapGit Structure");
        dlg.setMessage("Select a root directory for abapGit export:");
        String dir = dlg.open();

        if (dir != null) {
            int count = 0;
            try {
                // Create abapGit-compatible folder structure
                // src/<package_name>/
                File srcDir = new File(dir, "src");
                srcDir.mkdirs();

                for (Map.Entry<String, GeneratedArtifact> entry : currentArtifacts.entrySet()) {
                    GeneratedArtifact artifact = entry.getValue();
                    String fileName = artifact.getFileName();
                    File subDir;

                    switch (artifact.getObjectType()) {
                        case "DDLS":
                            subDir = new File(srcDir, artifact.getObjectName().toLowerCase() + ".ddls");
                            break;
                        case "DDLX":
                            subDir = new File(srcDir, artifact.getObjectName().toLowerCase() + ".ddlx");
                            break;
                        case "BDEF":
                            subDir = new File(srcDir, artifact.getObjectName().toLowerCase() + ".bdef");
                            break;
                        case "CLAS":
                            subDir = new File(srcDir, artifact.getObjectName().toLowerCase() + ".clas");
                            break;
                        case "SRVD":
                            subDir = new File(srcDir, artifact.getObjectName().toLowerCase() + ".srvd");
                            break;
                        default:
                            subDir = srcDir;
                    }

                    subDir.mkdirs();
                    File file = new File(subDir, fileName);
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(artifact.getSourceCode());
                        count++;
                    }
                }

                showInfoMessage("Exported " + count + " files in abapGit structure to:\n" + dir
                    + "\n\nYou can import this via abapGit in your SAP system.");
            } catch (IOException ex) {
                showErrorMessage("Export failed: " + ex.getMessage());
            }
        }
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = new Clipboard(container.getDisplay());
        clipboard.setContents(
            new Object[]{text},
            new Transfer[]{TextTransfer.getInstance()});
        clipboard.dispose();
    }

    private String truncateTabName(String name) {
        if (name.length() > 25) {
            return name.substring(0, 22) + "...";
        }
        return name;
    }

    private void showInfoMessage(String message) {
        MessageBox msgBox = new MessageBox(container.getShell(), SWT.ICON_INFORMATION | SWT.OK);
        msgBox.setText("Info");
        msgBox.setMessage(message);
        msgBox.open();
    }

    private void showErrorMessage(String message) {
        MessageBox msgBox = new MessageBox(container.getShell(), SWT.ICON_ERROR | SWT.OK);
        msgBox.setText("Error");
        msgBox.setMessage(message);
        msgBox.open();
    }

    public Control getControl() { return container; }
}
