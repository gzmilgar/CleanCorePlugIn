package com.sap.cleancore.analyzer.analyzers;

import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.ZObject;

import java.util.Locale;

/**
 * Flags Z* objects (or sub-objects) that are sitting in SAP-owned packages
 * (devClass not starting with Z/Y) as modifications. In real SAP they appear
 * in SMODILOG; here we infer from devClass + a 'modification' marker the
 * collector can set when SMODILOG is reachable.
 */
public class ModificationDetector {

    public Finding analyze(ZObject z) {
        if (z == null) return null;
        String pkg = z.getDevClass() != null ? z.getDevClass().toUpperCase(Locale.ROOT) : "";
        String name = z.getName() != null ? z.getName().toUpperCase(Locale.ROOT) : "";
        boolean inSapPackage = !pkg.isEmpty()
                && !pkg.startsWith("Z")
                && !pkg.startsWith("Y")
                && !pkg.startsWith("$"); // local
        boolean isStandardObjectName = !name.startsWith("Z") && !name.startsWith("Y");
        if (inSapPackage && isStandardObjectName) {
            z.setModification(true);
            Finding f = new Finding("CC_MODIFICATION",
                    "Object lives in SAP package " + pkg + " — looks like a modification of standard.",
                    Finding.Severity.ERROR, Finding.Source.MODIFICATION);
            f.setCategory("Modification");
            return f;
        }
        return null;
    }
}
