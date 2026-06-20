package com.sap.cleancore.analyzer.ui;

import com.sap.cleancore.analyzer.model.AnalysisFilter;
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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pre-analysis filter dialog. Lets the user pick:
 *   - Scan mode (full / package prefix / single object)
 *   - Which TADIR object types to include
 *   - Whether to include Y* in addition to Z*
 *   - Max search results
 *
 * Returns an AnalysisFilter consumed by AnalysisService.
 */
public class AnalysisWizardDialog extends Dialog {

    private static final Map<String, String> OBJECT_TYPE_LABELS = new LinkedHashMap<>();
    static {
        OBJECT_TYPE_LABELS.put("PROG/P",  "Reports (PROG)");
        OBJECT_TYPE_LABELS.put("FUGR/F",  "Function Groups / FMs (FUGR)");
        OBJECT_TYPE_LABELS.put("CLAS/OC", "Classes (CLAS)");
        OBJECT_TYPE_LABELS.put("INTF/OI", "Interfaces (INTF)");
        OBJECT_TYPE_LABELS.put("TABL/DT", "DDIC Tables (TABL)");
        OBJECT_TYPE_LABELS.put("DDLS/DF", "CDS Views (DDLS)");
        OBJECT_TYPE_LABELS.put("ENHO/XHE","Enhancements (ENHO)");
        OBJECT_TYPE_LABELS.put("SXCI/SXC","BAdI Implementations (SXCI)");
    }

    private Button modeFull, modePkg, modeSingle;
    private Text pkgText;
    private Combo singleTypeCombo;
    private Text singleNameText;
    private Map<String, Button> typeChecks = new LinkedHashMap<>();
    private Button includeYBtn;
    private Text maxResultsText;
    private Combo scenarioCombo;
    private java.util.List<TransformationScenario> scenarioList = new java.util.ArrayList<>();

    private AnalysisFilter result;
    private TransformationScenario scenarioResult;

    public AnalysisWizardDialog(Shell parent) { super(parent); }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Run Analysis — choose scope");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(1, false));
        ((GridData) root.getLayoutData()).widthHint = 520;

        // --- Transformation scenario group ---
        Group scenarioGrp = new Group(root, SWT.NONE);
        scenarioGrp.setText("Transformation scenario");
        scenarioGrp.setLayout(new GridLayout(2, false));
        scenarioGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(scenarioGrp, SWT.NONE).setText("Source → Target:");
        scenarioCombo = new Combo(scenarioGrp, SWT.READ_ONLY);
        scenarioCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scenarioCombo.setToolTipText(
                "Selects the old→new transformation (e.g. ECC→S/4HANA on-prem,\n"
              + "ECC→RISE/S4HC, R/3→RISE). Drives the effort profile, rule packs,\n"
              + "integration collectors and optional analyzers.");
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

        // --- Mode group ---
        Group modeGrp = new Group(root, SWT.NONE);
        modeGrp.setText("Scope");
        modeGrp.setLayout(new GridLayout(2, false));
        modeGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        modeFull = new Button(modeGrp, SWT.RADIO);
        modeFull.setText("Full Z*/Y* scan (entire custom code)");
        GridData gd = new GridData();
        gd.horizontalSpan = 2;
        modeFull.setLayoutData(gd);
        modeFull.setSelection(true);

        modePkg = new Button(modeGrp, SWT.RADIO);
        modePkg.setText("Filter by package prefix (e.g. ZFI_*, ZMM_*):");
        gd = new GridData();
        gd.horizontalSpan = 2;
        modePkg.setLayoutData(gd);

        new Label(modeGrp, SWT.NONE).setText("  Prefix(es) comma-separated:");
        pkgText = new Text(modeGrp, SWT.BORDER);
        pkgText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        pkgText.setEnabled(false);
        pkgText.setMessage("e.g. ZNT_*  or  ZNT_*,ZFI_*");
        pkgText.setToolTipText(
                "Prefix matched against object NAME (case-insensitive, startsWith).\n"
              + "  • Use * as trailing wildcard, e.g. ZNT_* matches ZNT_000_CL_001\n"
              + "  • Comma-separate multiple prefixes: ZNT_*, ZFI_*\n"
              + "  • A lone * means 'match everything'.\n"
              + "Note: open editors are scanned (no HTTP), so the editor file must\n"
              + "be visible in the workbench for the analyzer to see it.");

        Label pkgHint = new Label(modeGrp, SWT.WRAP);
        pkgHint.setText(
                "Note: this plug-in can only see what ADT has already cached. To scan "
              + "a whole package, first expand it in Project Explorer, OR right-click "
              + "the package \u2192 Clean Core \u2192 Analyze Selected Package.");
        GridData pkgHintGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pkgHintGd.horizontalSpan = 2;
        pkgHintGd.widthHint = 480;
        pkgHint.setLayoutData(pkgHintGd);

        modeSingle = new Button(modeGrp, SWT.RADIO);
        modeSingle.setText("Single object (fast — analyze just one Z object)");
        gd = new GridData();
        gd.horizontalSpan = 2;
        modeSingle.setLayoutData(gd);

        new Label(modeGrp, SWT.NONE).setText("  Type:");
        singleTypeCombo = new Combo(modeGrp, SWT.READ_ONLY);
        for (Map.Entry<String, String> e : OBJECT_TYPE_LABELS.entrySet()) {
            singleTypeCombo.add(e.getValue());
            singleTypeCombo.setData(e.getValue(), e.getKey());
        }
        singleTypeCombo.select(0);
        singleTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        singleTypeCombo.setEnabled(false);

        new Label(modeGrp, SWT.NONE).setText("  Object name:");
        singleNameText = new Text(modeGrp, SWT.BORDER);
        singleNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        singleNameText.setEnabled(false);
        singleNameText.setMessage("ZFI_INVOICE01");

        SelectionAdapter modeChange = new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                pkgText.setEnabled(modePkg.getSelection());
                singleTypeCombo.setEnabled(modeSingle.getSelection());
                singleNameText.setEnabled(modeSingle.getSelection());
            }
        };
        modeFull.addSelectionListener(modeChange);
        modePkg.addSelectionListener(modeChange);
        modeSingle.addSelectionListener(modeChange);

        // --- Object types ---
        Group typeGrp = new Group(root, SWT.NONE);
        typeGrp.setText("Object types to scan");
        typeGrp.setLayout(new GridLayout(2, true));
        typeGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        for (Map.Entry<String, String> e : OBJECT_TYPE_LABELS.entrySet()) {
            Button b = new Button(typeGrp, SWT.CHECK);
            b.setText(e.getValue());
            b.setSelection(true);
            typeChecks.put(e.getKey(), b);
        }

        // --- Other options ---
        Composite optsGrp = new Composite(root, SWT.NONE);
        optsGrp.setLayout(new GridLayout(2, false));
        optsGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        includeYBtn = new Button(optsGrp, SWT.CHECK);
        includeYBtn.setText("Include Y* namespace (in addition to Z*)");
        includeYBtn.setSelection(true);
        gd = new GridData();
        gd.horizontalSpan = 2;
        includeYBtn.setLayoutData(gd);

        new Label(optsGrp, SWT.NONE).setText("Max results per object type:");
        maxResultsText = new Text(optsGrp, SWT.BORDER);
        maxResultsText.setText("1000");
        GridData mgd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        mgd.widthHint = 80;
        maxResultsText.setLayoutData(mgd);

        return root;
    }

    @Override
    protected void okPressed() {
        AnalysisFilter f = new AnalysisFilter();

        if (modePkg.getSelection()) {
            f.setMode(AnalysisFilter.Mode.PACKAGE_PREFIX);
            java.util.List<String> prefixes = new java.util.ArrayList<>();
            for (String p : pkgText.getText().split(",")) {
                String t = p.trim();
                if (!t.isEmpty()) prefixes.add(t);
            }
            f.setPackagePrefixes(prefixes);
        } else if (modeSingle.getSelection()) {
            f.setMode(AnalysisFilter.Mode.SINGLE_OBJECT);
            String selLabel = singleTypeCombo.getText();
            String typeCode = (String) singleTypeCombo.getData(selLabel);
            f.setSingleObjectType(typeCode);
            f.setSingleObjectName(singleNameText.getText().trim().toUpperCase());
        } else {
            f.setMode(AnalysisFilter.Mode.FULL);
        }

        Set<String> types = new LinkedHashSet<>();
        for (Map.Entry<String, Button> e : typeChecks.entrySet()) {
            if (e.getValue().getSelection()) types.add(e.getKey());
        }
        if (types.isEmpty()) {
            for (String t : AnalysisFilter.ALL_OBJECT_TYPES) types.add(t);
        }
        f.setIncludedObjectTypes(types);
        f.setIncludeY(includeYBtn.getSelection());

        int max = 1000;
        try { max = Integer.parseInt(maxResultsText.getText().trim()); }
        catch (Exception ignored) {}
        f.setMaxResults(max);

        // Resolve the chosen transformation scenario and persist it.
        if (scenarioCombo != null) {
            int idx = scenarioCombo.getSelectionIndex();
            if (idx >= 0 && idx < scenarioList.size()) {
                scenarioResult = scenarioList.get(idx);
                CleanCorePreferences.setScenarioId(scenarioResult.getId());
            }
        }
        if (scenarioResult == null) {
            scenarioResult = ScenarioRegistry.getInstance().getDefault();
        }

        this.result = f;
        super.okPressed();
    }

    public AnalysisFilter getResult() { return result; }

    /** The transformation scenario chosen by the user (never null after OK). */
    public TransformationScenario getScenario() { return scenarioResult; }
}
