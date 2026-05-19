package com.sap.cleancore.analyzer.preferences;

import com.sap.cleancore.Activator;
import com.sap.cleancore.analyzer.effort.EffortRules;
import com.sap.cleancore.analyzer.model.ZObjectType;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import java.util.EnumMap;
import java.util.Map;

public class CleanCorePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

    private Text atcVariantText;
    private Text apiHubUrlText;
    private Text locSText, locMText, locLText;
    private org.eclipse.swt.widgets.Button allowSelfSignedBtn;

    private final EnumMap<ZObjectType, Text[]> coeffFields = new EnumMap<>(ZObjectType.class);

    public CleanCorePreferencePage() {
        setTitle("Clean Core Analyzer");
        setDescription("Configure ATC variant, api.sap.com URL and S/M/L/XL effort coefficients.");
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
    }

    @Override
    public void init(IWorkbench workbench) {}

    @Override
    protected Control createContents(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(2, false));
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        new Label(root, SWT.NONE).setText("ATC check variant:");
        atcVariantText = new Text(root, SWT.BORDER);
        atcVariantText.setText(CleanCorePreferences.getAtcVariant());
        atcVariantText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(root, SWT.NONE).setText("api.sap.com URL:");
        apiHubUrlText = new Text(root, SWT.BORDER);
        apiHubUrlText.setText(CleanCorePreferences.getApiHubUrl());
        apiHubUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Self-signed certificate toggle
        new Label(root, SWT.NONE).setText("");
        allowSelfSignedBtn = new org.eclipse.swt.widgets.Button(root, SWT.CHECK);
        allowSelfSignedBtn.setText("Accept self-signed / internal SAP certificates (recommended for customer systems)");
        allowSelfSignedBtn.setSelection(CleanCorePreferences.isAllowSelfSignedCerts());
        GridData ssgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        ssgd.horizontalSpan = 1;
        allowSelfSignedBtn.setLayoutData(ssgd);

        Group thresh = new Group(root, SWT.NONE);
        thresh.setText("Size thresholds (LOC)");
        thresh.setLayout(new GridLayout(6, false));
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 2;
        thresh.setLayoutData(gd);

        EffortRules.Thresholds th = EffortRules.getInstance().thresholds();
        new Label(thresh, SWT.NONE).setText("S <");
        locSText = new Text(thresh, SWT.BORDER); locSText.setText(String.valueOf(th.locS));
        new Label(thresh, SWT.NONE).setText(" M <");
        locMText = new Text(thresh, SWT.BORDER); locMText.setText(String.valueOf(th.locM));
        new Label(thresh, SWT.NONE).setText(" L <");
        locLText = new Text(thresh, SWT.BORDER); locLText.setText(String.valueOf(th.locL));

        Group coeffs = new Group(root, SWT.NONE);
        coeffs.setText("MD coefficients per object type (S / M / L / XL)");
        coeffs.setLayout(new GridLayout(5, false));
        GridData cgd = new GridData(SWT.FILL, SWT.FILL, true, true);
        cgd.horizontalSpan = 2;
        coeffs.setLayoutData(cgd);

        // Header
        new Label(coeffs, SWT.NONE).setText("Type");
        new Label(coeffs, SWT.NONE).setText("S");
        new Label(coeffs, SWT.NONE).setText("M");
        new Label(coeffs, SWT.NONE).setText("L");
        new Label(coeffs, SWT.NONE).setText("XL");

        Map<ZObjectType, EffortRules.Coefficients> snap = EffortRules.getInstance().snapshot();
        for (Map.Entry<ZObjectType, EffortRules.Coefficients> e : snap.entrySet()) {
            new Label(coeffs, SWT.NONE).setText(e.getKey().name());
            Text s = mdInput(coeffs, e.getValue().s);
            Text m = mdInput(coeffs, e.getValue().m);
            Text l = mdInput(coeffs, e.getValue().l);
            Text xl = mdInput(coeffs, e.getValue().xl);
            coeffFields.put(e.getKey(), new Text[] { s, m, l, xl });
        }

        return root;
    }

    private Text mdInput(Composite parent, double initial) {
        Text t = new Text(parent, SWT.BORDER);
        t.setText(String.valueOf(initial));
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 60;
        t.setLayoutData(gd);
        return t;
    }

    @Override
    protected void performDefaults() {
        EffortRules.getInstance().reset();
        atcVariantText.setText(CleanCorePreferences.DEFAULT_ATC_VARIANT);
        apiHubUrlText.setText(CleanCorePreferences.DEFAULT_API_HUB_URL);
        EffortRules.Thresholds th = EffortRules.getInstance().thresholds();
        locSText.setText(String.valueOf(th.locS));
        locMText.setText(String.valueOf(th.locM));
        locLText.setText(String.valueOf(th.locL));
        allowSelfSignedBtn.setSelection(true);
        for (Map.Entry<ZObjectType, Text[]> e : coeffFields.entrySet()) {
            EffortRules.Coefficients c = EffortRules.getInstance().coefficientsFor(e.getKey());
            e.getValue()[0].setText(String.valueOf(c.s));
            e.getValue()[1].setText(String.valueOf(c.m));
            e.getValue()[2].setText(String.valueOf(c.l));
            e.getValue()[3].setText(String.valueOf(c.xl));
        }
        super.performDefaults();
    }

    @Override
    public boolean performOk() {
        try {
            CleanCorePreferences.setAtcVariant(atcVariantText.getText().trim());
            CleanCorePreferences.setApiHubUrl(apiHubUrlText.getText().trim());
            CleanCorePreferences.setAllowSelfSignedCerts(allowSelfSignedBtn.getSelection());

            EffortRules.Thresholds th = new EffortRules.Thresholds();
            th.locS = Integer.parseInt(locSText.getText().trim());
            th.locM = Integer.parseInt(locMText.getText().trim());
            th.locL = Integer.parseInt(locLText.getText().trim());
            EffortRules.getInstance().overrideThresholds(th);

            for (Map.Entry<ZObjectType, Text[]> e : coeffFields.entrySet()) {
                double s = Double.parseDouble(e.getValue()[0].getText().trim());
                double m = Double.parseDouble(e.getValue()[1].getText().trim());
                double l = Double.parseDouble(e.getValue()[2].getText().trim());
                double xl = Double.parseDouble(e.getValue()[3].getText().trim());
                EffortRules.getInstance().override(e.getKey(), new EffortRules.Coefficients(s, m, l, xl));
            }
        } catch (NumberFormatException nfe) {
            setErrorMessage("Numeric value expected: " + nfe.getMessage());
            return false;
        }
        return super.performOk();
    }
}
