package com.sap.cleancore.analyzer.collectors.inventory;

import com.sap.cleancore.analyzer.effort.InventoryEffortEstimator;
import com.sap.cleancore.analyzer.model.TransformationScenario;
import com.sap.cleancore.analyzer.model.inventory.InventoryCategory;
import com.sap.cleancore.analyzer.model.inventory.InventoryItem;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates integration-inventory collection for a transformation
 * assessment. Follows the same best-effort, never-abort pattern as
 * {@code AnalysisService}: the extract file is the primary, reliable, offline
 * source; live ADT/HTTP collectors can be attached later as a secondary,
 * best-effort tier.
 *
 * <p>Only categories enabled by the scenario's
 * {@code enabledInventoryCollectors} are kept; each surviving item gets an
 * effort estimate. If the scenario enables no collectors, an empty list is
 * returned (inventory is simply not part of that scenario).
 */
public class IntegrationInventoryService {

    private final ExtractIngestor ingestor = new ExtractIngestor();
    private final InventoryEffortEstimator effort = new InventoryEffortEstimator();

    /**
     * @param scenario     drives which categories are in scope (may be null → none)
     * @param extractFile  optional Z_TRANSFORM_INVENTORY JSON extract (may be null)
     */
    public List<InventoryItem> collect(TransformationScenario scenario, File extractFile) {
        List<InventoryItem> out = new ArrayList<>();
        Set<InventoryCategory> enabled = enabledCategories(scenario);
        if (enabled.isEmpty()) return out;

        // Primary source: extract file (best-effort, never throws).
        for (InventoryItem it : ingestor.ingestQuietly(extractFile)) {
            if (it.getCategory() != null && enabled.contains(it.getCategory())) {
                effort.estimate(it);
                out.add(it);
            }
        }

        // Secondary tier (live ADT/HTTP collectors) would be invoked here,
        // each wrapped in try/catch and de-duplicated against `out`. Left as a
        // future extension since those Basis tables are not exposed by ADT REST.

        return out;
    }

    /** Maps the scenario's collector ids to inventory categories. */
    private Set<InventoryCategory> enabledCategories(TransformationScenario scenario) {
        Set<InventoryCategory> set = EnumSet.noneOf(InventoryCategory.class);
        if (scenario == null) return set;
        for (String id : scenario.getEnabledInventoryCollectors()) {
            if (id == null) continue;
            switch (id.trim().toUpperCase()) {
                case "RFC":        set.add(InventoryCategory.RFC_DESTINATION); break;
                case "IDOC":       set.add(InventoryCategory.IDOC_PARTNER); break;
                case "SICF":       set.add(InventoryCategory.SICF_SERVICE);
                                   set.add(InventoryCategory.ODATA_SERVICE); break;
                case "WEBSERVICE": set.add(InventoryCategory.WEB_SERVICE); break;
                case "PIPO":       set.add(InventoryCategory.PI_PO_INTERFACE); break;
                case "BTP":        set.add(InventoryCategory.BTP_INTEGRATION); break;
                default: break;
            }
        }
        return set;
    }
}
