package com.rap.generator.ui.panels;

import com.rap.generator.generators.GeneratorEngine.GeneratedArtifact;
import com.rap.generator.ui.dialogs.DeployDialog;
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

        DeployDialog dlg = new DeployDialog();
        if (!dlg.open(container.getShell())) return;

        String packageName = dlg.getPackageName();
        String transportRequest = dlg.getTransportRequest();
        String dir = dlg.getExportDirectory();

        int count = 0;
        try {
            // Create src/ directory (abapGit flat structure)
            File srcDir = new File(dir, "src");
            srcDir.mkdirs();

            for (Map.Entry<String, GeneratedArtifact> entry : currentArtifacts.entrySet()) {
                GeneratedArtifact artifact = entry.getValue();
                String fileName = artifact.getFileName();

                // Write the source file directly into src/ (flat structure)
                File file = new File(srcDir, fileName.toLowerCase());
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(artifact.getSourceCode());
                    count++;
                }
            }

            // Generate .abapgit.xml at the repository root
            File abapgitXml = new File(dir, ".abapgit.xml");
            try (FileWriter writer = new FileWriter(abapgitXml)) {
                writer.write(buildAbapGitXml(packageName, transportRequest));
            }

            showInfoMessage("Exported " + count + " files in abapGit structure to:\n" + dir
                + "\n\nPackage: " + packageName
                + (transportRequest != null ? "\nTransport: " + transportRequest : "")
                + "\n\nTo import into SAP:\n"
                + "1. Open abapGit in your SAP system\n"
                + "2. Create a new offline repository pointing to this directory\n"
                + "3. Pull the repository to import all objects");
        } catch (IOException ex) {
            showErrorMessage("Export failed: " + ex.getMessage());
        }
    }

    /**
     * Builds the .abapgit.xml content that configures the abapGit repository.
     * This file tells abapGit which package to place objects into and
     * optionally which transport request to use.
     */
    private String buildAbapGitXml(String packageName, String transportRequest) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<asx:abap xmlns:asx=\"http://www.sap.com/abapxml\" version=\"1.0\">\n");
        sb.append(" <asx:values>\n");
        sb.append("  <DATA>\n");
        sb.append("   <MASTER_LANGUAGE>E</MASTER_LANGUAGE>\n");
        sb.append("   <STARTING_FOLDER>/src/</STARTING_FOLDER>\n");
        sb.append("   <FOLDER_LOGIC>FLAT</FOLDER_LOGIC>\n");
        sb.append("   <IGNORE>\n");
        sb.append("    <item>/.gitignore</item>\n");
        sb.append("    <item>/LICENSE</item>\n");
        sb.append("    <item>/README.md</item>\n");
        sb.append("    <item>/package.json</item>\n");
        sb.append("    <item>/.travis.yml</item>\n");
        sb.append("   </IGNORE>\n");
        sb.append("   <REQUIREMENTS/>\n");
        sb.append("  </DATA>\n");
        sb.append(" </asx:values>\n");
        sb.append("</asx:abap>\n");
        return sb.toString();
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
