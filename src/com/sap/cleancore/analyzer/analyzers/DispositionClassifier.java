package com.sap.cleancore.analyzer.analyzers;

import com.sap.cleancore.analyzer.model.Disposition;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.TransformationScenario;
import com.sap.cleancore.analyzer.model.ZObject;

/**
 * Classifies a {@link MigrationItem} into SAP's canonical custom-code
 * adaptation decision tree (RETIRE / RETAIN / ADAPT / RENOVATE), as described
 * in SAP "Custom Code Adaptation to SAP S/4HANA and ABAP Cloud".
 *
 * <p>Runs after the effort estimator (so findings and effort category are
 * available). Decision precedence:
 * <ol>
 *   <li><b>RETIRE</b> — ABAP Call Monitor usage is exactly 0 (unused) →
 *       decommission, effort forced to ~0 (removed from migration scope).</li>
 *   <li><b>RENOVATE</b> — the scenario targets cloud readiness AND the object
 *       is large (L/XL) with clean-core/obsolete-API findings worth
 *       modernizing to ABAP Cloud / RAP / released public APIs.</li>
 *   <li><b>ADAPT</b> — has findings (obsolete API, modification, errors,
 *       warnings) requiring adaptation for S/4HANA.</li>
 *   <li><b>RETAIN</b> — no findings → keep as-is with minimal/no change.</li>
 * </ol>
 *
 * <p>Backward compatible: when the scenario is the legacy "default" scenario
 * (generic Clean Core readiness) or null, the item is left
 * {@link Disposition#UNDECIDED} and its effort is untouched.
 */
public class DispositionClassifier {

    private static final String LEGACY_SCENARIO_ID = "default";

    public void classify(MigrationItem item, TransformationScenario scenario) {
        if (item == null || item.getzObject() == null) return;
        // Legacy behaviour: don't classify for the generic default scenario.
        if (scenario == null || LEGACY_SCENARIO_ID.equalsIgnoreCase(scenario.getId())) {
            item.setDisposition(Disposition.UNDECIDED);
            return;
        }

        // 1) RETIRE — usage data says it's unused (scopes it out of the project).
        if (item.getUsageCount() == 0) {
            item.setDisposition(Disposition.RETIRE);
            item.setEstimatedMD(0.0);
            item.setEffortCategory(MigrationItem.EffortCategory.S);
            item.setRisk("LOW");
            item.setReasoning(prefix("RETIRE")
                    + "Unused per ABAP Call Monitor (usage=0) — decommission, out of scope.");
            return;
        }

        ZObject z = item.getzObject();
        boolean hasObsolete = false;
        boolean hasErr = false;
        boolean hasWarn = false;
        boolean hasCleanCore = false; // bundled static clean-core rule hit
        for (Finding f : item.getFindings()) {
            if (f.getSource() == Finding.Source.OBSOLETE_API) hasObsolete = true;
            if (f.getSource() == Finding.Source.STATIC) hasCleanCore = true;
            if (f.getSeverity() == Finding.Severity.ERROR) hasErr = true;
            else if (f.getSeverity() == Finding.Severity.WARNING) hasWarn = true;
        }
        boolean isMod = z.isModification();
        boolean anyFindings = !item.getFindings().isEmpty();
        boolean cloud = scenario.hasFlag("cloudReadiness");
        MigrationItem.EffortCategory cat = item.getEffortCategory();
        boolean large = (cat == MigrationItem.EffortCategory.L || cat == MigrationItem.EffortCategory.XL);

        // 2) RENOVATE — worth modernizing toward ABAP Cloud.
        if (cloud && large && (hasObsolete || hasCleanCore || isMod)) {
            item.setDisposition(Disposition.RENOVATE);
            item.setReasoning(prefix("RENOVATE") + base(item)
                    + " Cloud-readiness scenario + large object with clean-core/obsolete-API"
                    + " findings → modernize (ABAP Cloud / RAP / released APIs).");
            return;
        }

        // 3) ADAPT — needs adaptation for S/4HANA.
        if (hasObsolete || hasErr || hasWarn || isMod || anyFindings) {
            item.setDisposition(Disposition.ADAPT);
            item.setReasoning(prefix("ADAPT") + base(item)
                    + " Adapt for S/4HANA (table/API/SQL/modification fixes).");
            return;
        }

        // 4) RETAIN — clean object, keep as-is.
        item.setDisposition(Disposition.RETAIN);
        item.setReasoning(prefix("RETAIN") + base(item)
                + " No findings — retain with minimal/no change.");
    }

    private String prefix(String d) {
        return "[" + d + "] ";
    }

    private String base(MigrationItem item) {
        String r = item.getReasoning();
        return (r != null && !r.isEmpty()) ? r : "";
    }
}
