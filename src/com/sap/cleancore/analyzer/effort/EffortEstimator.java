package com.sap.cleancore.analyzer.effort;

import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;

/**
 * Decides S/M/L/XL category and the resulting MD for a MigrationItem,
 * given the Z object's metadata and the analyzer findings.
 *
 * Logic:
 *   1. Modification of SAP standard or XL-sized LOC -> XL.
 *   2. >= errL ERROR findings or LOC >= locL -> L.
 *   3. >= errM ERRORs or LOC >= locM, OR any obsolete-API finding -> M.
 *   4. else -> S.
 *
 * MD = coefficient[type][category]. Risk derived from category + findings count.
 */
public class EffortEstimator {

    public void estimate(MigrationItem item) {
        if (item == null || item.getzObject() == null) return;
        ZObject z = item.getzObject();
        ZObjectType t = z.getType() != null ? z.getType() : ZObjectType.UNKNOWN;

        EffortRules.Thresholds th = EffortRules.getInstance().thresholds();
        int errors = item.countSeverity(Finding.Severity.ERROR);
        int warnings = item.countSeverity(Finding.Severity.WARNING);
        boolean hasObsoleteApi = false;
        for (Finding f : item.getFindings()) {
            if (f.getSource() == Finding.Source.OBSOLETE_API) { hasObsoleteApi = true; break; }
        }

        MigrationItem.EffortCategory cat;
        StringBuilder why = new StringBuilder();

        if (z.isModification() || t == ZObjectType.MODIFICATION || z.getLoc() > 2000) {
            cat = MigrationItem.EffortCategory.XL;
            why.append("Modification or LOC>2000.");
        } else if (errors >= th.errL || z.getLoc() >= th.locL) {
            cat = MigrationItem.EffortCategory.L;
            why.append("Errors=").append(errors).append(" or LOC=").append(z.getLoc()).append(" >= L threshold.");
        } else if (errors >= th.errM || z.getLoc() >= th.locM || hasObsoleteApi) {
            cat = MigrationItem.EffortCategory.M;
            why.append("Errors=").append(errors).append(", LOC=").append(z.getLoc());
            if (hasObsoleteApi) why.append(", uses obsolete API");
            why.append(".");
        } else {
            cat = MigrationItem.EffortCategory.S;
            why.append("LOC=").append(z.getLoc()).append(", findings=").append(item.getFindings().size()).append(".");
        }

        EffortRules.Coefficients c = EffortRules.getInstance().coefficientsFor(t);
        double md = c.get(cat);

        item.setEffortCategory(cat);
        item.setEstimatedMD(md);
        item.setRisk(deriveRisk(cat, errors, warnings, z.isModification()));
        item.setReasoning(why.toString());
    }

    private String deriveRisk(MigrationItem.EffortCategory cat, int errors, int warnings, boolean modification) {
        if (modification || cat == MigrationItem.EffortCategory.XL) return "HIGH";
        if (cat == MigrationItem.EffortCategory.L || errors >= 3) return "HIGH";
        if (cat == MigrationItem.EffortCategory.M || errors >= 1 || warnings >= 3) return "MEDIUM";
        return "LOW";
    }
}
