package com.sap.cleancore.analyzer.effort;

import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.ZObjectType;
import com.sap.cleancore.analyzer.utils.ResourceLoader;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads effort coefficients (S/M/L/XL -> MD) per Z object type from
 * resources/mapping/object_rules.json. Allows runtime overrides from preferences.
 */
public class EffortRules {

    public static class Coefficients {
        public double s, m, l, xl;
        public Coefficients(double s, double m, double l, double xl) {
            this.s = s; this.m = m; this.l = l; this.xl = xl;
        }
        public double get(MigrationItem.EffortCategory cat) {
            switch (cat) { case S: return s; case M: return m; case L: return l; case XL: return xl; }
            return s;
        }
    }

    public static class Thresholds {
        public int locS = 100;
        public int locM = 500;
        public int locL = 2000;
        public int errS = 0;
        public int errM = 2;
        public int errL = 5;
    }

    private static EffortRules instance;

    private final EnumMap<ZObjectType, Coefficients> coefficients = new EnumMap<>(ZObjectType.class);
    private Thresholds thresholds = new Thresholds();
    private boolean loaded;

    public static synchronized EffortRules getInstance() {
        if (instance == null) instance = new EffortRules();
        return instance;
    }

    private EffortRules() {}

    @SuppressWarnings("unchecked")
    public synchronized void loadIfNeeded() {
        if (loaded) return;
        try {
            String text = ResourceLoader.readBundleText(null, "resources/mapping/object_rules.json");
            Map<String, Object> root = (Map<String, Object>) SimpleJsonParser.parse(text);
            Map<String, Object> rules = SimpleJsonParser.obj(root, "rules");
            for (Map.Entry<String, Object> e : rules.entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                Coefficients c = new Coefficients(
                        SimpleJsonParser.dbl(v, "S", 0.5),
                        SimpleJsonParser.dbl(v, "M", 1.5),
                        SimpleJsonParser.dbl(v, "L", 3.0),
                        SimpleJsonParser.dbl(v, "XL", 5.0));
                try {
                    coefficients.put(ZObjectType.valueOf(e.getKey()), c);
                } catch (IllegalArgumentException ignored) {
                    // unknown key -> skip
                }
            }
            Map<String, Object> th = SimpleJsonParser.obj(root, "categoryThresholds");
            Map<String, Object> locTh = SimpleJsonParser.obj(th, "loc");
            thresholds.locS = (int) SimpleJsonParser.dbl(locTh, "S", 100);
            thresholds.locM = (int) SimpleJsonParser.dbl(locTh, "M", 500);
            thresholds.locL = (int) SimpleJsonParser.dbl(locTh, "L", 2000);
            Map<String, Object> errTh = SimpleJsonParser.obj(th, "errorFindings");
            thresholds.errS = (int) SimpleJsonParser.dbl(errTh, "S", 0);
            thresholds.errM = (int) SimpleJsonParser.dbl(errTh, "M", 2);
            thresholds.errL = (int) SimpleJsonParser.dbl(errTh, "L", 5);
        } catch (Exception e) {
            // Fall back to defaults if file is unreadable.
            defaults();
        }
        loaded = true;
    }

    private void defaults() {
        for (ZObjectType t : ZObjectType.values()) {
            coefficients.put(t, new Coefficients(0.5, 1.5, 3.0, 5.0));
        }
    }

    public Coefficients coefficientsFor(ZObjectType t) {
        loadIfNeeded();
        Coefficients c = coefficients.get(t);
        return c != null ? c : new Coefficients(0.5, 1.5, 3.0, 5.0);
    }

    public Thresholds thresholds() {
        loadIfNeeded();
        return thresholds;
    }

    /** Exposed for the preference page. */
    public Map<ZObjectType, Coefficients> snapshot() {
        loadIfNeeded();
        return new LinkedHashMap<>(coefficients);
    }

    public void override(ZObjectType t, Coefficients c) {
        loadIfNeeded();
        coefficients.put(t, c);
    }

    public void overrideThresholds(Thresholds t) {
        loadIfNeeded();
        this.thresholds = t;
    }

    /** Reset to in-bundle JSON defaults. */
    public synchronized void reset() {
        coefficients.clear();
        loaded = false;
        loadIfNeeded();
    }

    // Convenience for tests/headless
    public List<ZObjectType> knownTypes() {
        loadIfNeeded();
        return new java.util.ArrayList<>(coefficients.keySet());
    }
}
