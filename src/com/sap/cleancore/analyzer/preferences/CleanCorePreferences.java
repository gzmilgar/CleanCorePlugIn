package com.sap.cleancore.analyzer.preferences;

import com.rap.generator.Activator;
import org.eclipse.jface.preference.IPreferenceStore;

/**
 * Typed accessors for the plugin's preference store.
 */
public class CleanCorePreferences {

    public static final String ATC_VARIANT       = "ccAnalyzer.atcVariant";
    public static final String API_HUB_URL       = "ccAnalyzer.apiHubUrl";
    public static final String EFFORT_PREFIX     = "ccAnalyzer.effort.";  // + Z_TYPE.SIZE
    public static final String LOC_S             = "ccAnalyzer.locS";
    public static final String LOC_M             = "ccAnalyzer.locM";
    public static final String LOC_L             = "ccAnalyzer.locL";
    public static final String ALLOW_SELF_SIGNED = "ccAnalyzer.allowSelfSigned";

    public static final String DEFAULT_ATC_VARIANT = "S4HANA_READINESS_REMOTE";
    public static final String DEFAULT_API_HUB_URL = "https://api.sap.com/odata/1.0/catalog.svc/APIs";

    public static IPreferenceStore store() {
        return Activator.getDefault().getPreferenceStore();
    }

    public static String getAtcVariant() {
        String v = store().getString(ATC_VARIANT);
        return (v == null || v.isEmpty()) ? DEFAULT_ATC_VARIANT : v;
    }

    public static void setAtcVariant(String v) { store().setValue(ATC_VARIANT, v); }

    public static String getApiHubUrl() {
        String v = store().getString(API_HUB_URL);
        return (v == null || v.isEmpty()) ? DEFAULT_API_HUB_URL : v;
    }

    public static void setApiHubUrl(String v) { store().setValue(API_HUB_URL, v); }

    /**
     * When true, the plug-in's HTTPS client accepts self-signed / internal
     * certificates from the customer SAP system. Many on-prem and dev ABAP
     * systems use non-public CAs, so we default this to TRUE — analysts can
     * disable it via preferences if their security policy requires.
     */
    public static boolean isAllowSelfSignedCerts() {
        // store().contains() returns false for unset keys; default to true.
        if (!store().contains(ALLOW_SELF_SIGNED)) return true;
        return store().getBoolean(ALLOW_SELF_SIGNED);
    }

    public static void setAllowSelfSignedCerts(boolean b) {
        store().setValue(ALLOW_SELF_SIGNED, b);
    }
}
