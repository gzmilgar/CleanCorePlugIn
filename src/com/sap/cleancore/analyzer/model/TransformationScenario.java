package com.sap.cleancore.analyzer.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A pluggable "old system → new system" transformation scenario (e.g.
 * ECC 6.x → S/4HANA on-premise, ECC → RISE/S4HC, R/3 → RISE). It binds a
 * source release and a target platform to a rule-pack selection, an effort
 * profile, a set of enabled integration-inventory collectors, and feature
 * flags that gate optional analyzers.
 *
 * <p>Scenarios are configuration, loaded by
 * {@link com.sap.cleancore.analyzer.scenario.ScenarioRegistry} from
 * {@code resources/scenario/scenarios.json}. This class is a plain data
 * holder (no Eclipse / OSGi dependencies) so it can be exercised headlessly.
 */
public class TransformationScenario {

    /** Source system release family. */
    public enum SourceRelease {
        R3_47, ECC6, ERP_EHP8, S4_ANY, UNKNOWN;
        public static SourceRelease fromString(String s) {
            if (s == null) return UNKNOWN;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return UNKNOWN; }
        }
    }

    /** Target platform of the transformation. */
    public enum TargetPlatform {
        S4_ONPREM, RISE_PRIVATE, S4HC_PRIVATE, BTP_ABAP, UNKNOWN;
        public static TargetPlatform fromString(String s) {
            if (s == null) return UNKNOWN;
            try { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return UNKNOWN; }
        }
    }

    private String id;
    private String displayName;
    private SourceRelease sourceRelease = SourceRelease.UNKNOWN;
    private TargetPlatform targetPlatform = TargetPlatform.UNKNOWN;
    private List<String> rulePackIds = new ArrayList<>();
    private String effortProfileId = "default";
    private List<String> enabledInventoryCollectors = new ArrayList<>();
    private Map<String, Boolean> flags = new LinkedHashMap<>();

    public TransformationScenario() {}

    public TransformationScenario(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public SourceRelease getSourceRelease() { return sourceRelease; }
    public void setSourceRelease(SourceRelease sourceRelease) {
        this.sourceRelease = (sourceRelease != null ? sourceRelease : SourceRelease.UNKNOWN);
    }

    public TargetPlatform getTargetPlatform() { return targetPlatform; }
    public void setTargetPlatform(TargetPlatform targetPlatform) {
        this.targetPlatform = (targetPlatform != null ? targetPlatform : TargetPlatform.UNKNOWN);
    }

    public List<String> getRulePackIds() { return rulePackIds; }
    public void setRulePackIds(List<String> rulePackIds) {
        this.rulePackIds = (rulePackIds != null ? rulePackIds : new ArrayList<>());
    }

    public String getEffortProfileId() { return effortProfileId; }
    public void setEffortProfileId(String effortProfileId) {
        this.effortProfileId = (effortProfileId != null && !effortProfileId.isEmpty())
                ? effortProfileId : "default";
    }

    public List<String> getEnabledInventoryCollectors() { return enabledInventoryCollectors; }
    public void setEnabledInventoryCollectors(List<String> enabledInventoryCollectors) {
        this.enabledInventoryCollectors =
                (enabledInventoryCollectors != null ? enabledInventoryCollectors : new ArrayList<>());
    }

    public Map<String, Boolean> getFlags() { return flags; }
    public void setFlags(Map<String, Boolean> flags) {
        this.flags = (flags != null ? flags : new LinkedHashMap<>());
    }

    /** True only when the named flag is present and set to true. */
    public boolean hasFlag(String flag) {
        Boolean v = flags.get(flag);
        return v != null && v;
    }

    /** True when the named rule-pack is active. Empty rulePackIds = all packs. */
    public boolean hasRulePack(String packId) {
        return rulePackIds.isEmpty() || rulePackIds.contains(packId);
    }

    /** True when the named inventory collector is enabled for this scenario. */
    public boolean isCollectorEnabled(String collectorId) {
        return enabledInventoryCollectors.contains(collectorId);
    }

    @Override
    public String toString() {
        return (displayName != null ? displayName : id);
    }
}
