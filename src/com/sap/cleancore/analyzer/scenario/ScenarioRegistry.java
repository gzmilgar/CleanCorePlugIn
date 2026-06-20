package com.sap.cleancore.analyzer.scenario;

import com.sap.cleancore.analyzer.model.TransformationScenario;
import com.sap.cleancore.analyzer.model.TransformationScenario.SourceRelease;
import com.sap.cleancore.analyzer.model.TransformationScenario.TargetPlatform;
import com.sap.cleancore.analyzer.utils.ResourceLoader;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleton registry of {@link TransformationScenario}s, loaded from
 * {@code resources/scenario/scenarios.json}. Follows the same lazy-load +
 * graceful-degradation pattern as {@code EffortRules}: if the config file is
 * missing or malformed, a built-in default scenario set is used so a run is
 * never aborted.
 */
public class ScenarioRegistry {

    private static ScenarioRegistry instance;

    private final List<TransformationScenario> scenarios = new ArrayList<>();
    private String defaultScenarioId = "default";
    private boolean loaded;

    public static synchronized ScenarioRegistry getInstance() {
        if (instance == null) instance = new ScenarioRegistry();
        return instance;
    }

    private ScenarioRegistry() {}

    @SuppressWarnings("unchecked")
    public synchronized void loadIfNeeded() {
        if (loaded) return;
        try {
            String text = ResourceLoader.readBundleText(null, "resources/scenario/scenarios.json");
            Map<String, Object> root = (Map<String, Object>) SimpleJsonParser.parse(text);
            String defId = SimpleJsonParser.str(root, "defaultScenarioId", null);
            List<Map<String, Object>> arr = SimpleJsonParser.arr(root, "scenarios");
            List<TransformationScenario> parsed = new ArrayList<>();
            for (Map<String, Object> o : arr) {
                TransformationScenario s = fromJson(o);
                if (s != null && s.getId() != null) parsed.add(s);
            }
            if (parsed.isEmpty()) {
                builtinDefaults();
            } else {
                scenarios.clear();
                scenarios.addAll(parsed);
                defaultScenarioId = (defId != null ? defId : scenarios.get(0).getId());
            }
        } catch (Exception e) {
            // Unreadable / malformed config — never abort, fall back.
            builtinDefaults();
        }
        loaded = true;
    }

    @SuppressWarnings("unchecked")
    private TransformationScenario fromJson(Map<String, Object> o) {
        TransformationScenario s = new TransformationScenario();
        s.setId(SimpleJsonParser.str(o, "id", null));
        s.setDisplayName(SimpleJsonParser.str(o, "displayName", s.getId()));
        s.setSourceRelease(SourceRelease.fromString(SimpleJsonParser.str(o, "sourceRelease", null)));
        s.setTargetPlatform(TargetPlatform.fromString(SimpleJsonParser.str(o, "targetPlatform", null)));
        s.setEffortProfileId(SimpleJsonParser.str(o, "effortProfileId", "default"));
        s.setRulePackIds(strList(o.get("rulePackIds")));
        s.setEnabledInventoryCollectors(strList(o.get("enabledInventoryCollectors")));
        Map<String, Boolean> flags = new LinkedHashMap<>();
        Object fo = o.get("flags");
        if (fo instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) fo).entrySet()) {
                flags.put(e.getKey(), Boolean.TRUE.equals(e.getValue()));
            }
        }
        s.setFlags(flags);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List<Object>) v) {
                if (o != null) out.add(o.toString());
            }
        }
        return out;
    }

    private void builtinDefaults() {
        scenarios.clear();
        scenarios.add(makeDefault());
        defaultScenarioId = "default";
    }

    /** A minimal, dependency-free default scenario equivalent to today's tool. */
    public static TransformationScenario makeDefault() {
        TransformationScenario s = new TransformationScenario(
                "default", "Generic Clean Core readiness (custom code only)");
        s.setSourceRelease(SourceRelease.S4_ANY);
        s.setTargetPlatform(TargetPlatform.S4_ONPREM);
        s.setEffortProfileId("default");
        return s;
    }

    public synchronized List<TransformationScenario> getAll() {
        loadIfNeeded();
        return new ArrayList<>(scenarios);
    }

    public synchronized TransformationScenario byId(String id) {
        loadIfNeeded();
        if (id != null) {
            for (TransformationScenario s : scenarios) {
                if (id.equalsIgnoreCase(s.getId())) return s;
            }
        }
        return null;
    }

    public synchronized TransformationScenario getDefault() {
        loadIfNeeded();
        TransformationScenario s = byId(defaultScenarioId);
        if (s != null) return s;
        if (!scenarios.isEmpty()) return scenarios.get(0);
        return makeDefault();
    }

    /** Reset to in-bundle JSON (used by tests / preference reset). */
    public synchronized void reset() {
        scenarios.clear();
        loaded = false;
        loadIfNeeded();
    }

    // Convenience for headless tests
    public List<String> ids() {
        loadIfNeeded();
        List<String> out = new ArrayList<>();
        for (TransformationScenario s : scenarios) out.add(s.getId());
        return out;
    }

    /** Lower-cased lookup helper (not currently used by callers but handy). */
    String normalize(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT);
    }
}
