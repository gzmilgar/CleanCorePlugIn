package com.sap.cleancore.analyzer.model;

/**
 * SAP's canonical custom-code adaptation decision tree for an SAP S/4HANA /
 * ABAP Cloud transformation (see SAP "Custom Code Adaptation to SAP S/4HANA
 * and ABAP Cloud"). Every analyzed object/inventory item is classified into
 * exactly one disposition; this is the primary axis for effort and wave
 * planning.
 *
 * <ul>
 *   <li>{@link #RETIRE}   — not used any more (usage count 0) → decommission,
 *                           ~0 effort, removes it from migration scope.</li>
 *   <li>{@link #RETAIN}   — a standard S/4HANA feature now covers it → keep
 *                           with minimal/no change.</li>
 *   <li>{@link #ADAPT}    — must be adapted for S/4HANA (table/API/SQL fixes
 *                           against the Simplification Database).</li>
 *   <li>{@link #RENOVATE} — modernize towards ABAP Cloud / RAP / Fiori using
 *                           released public APIs (highest effort).</li>
 *   <li>{@link #UNDECIDED} — default; not yet classified (backward-compatible
 *                           value so legacy runs keep their existing
 *                           behaviour).</li>
 * </ul>
 */
public enum Disposition {
    RETIRE,
    RETAIN,
    ADAPT,
    RENOVATE,
    UNDECIDED
}
