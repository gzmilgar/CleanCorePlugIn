package com.sap.cleancore.analyzer.effort;

import com.sap.cleancore.analyzer.model.MigrationItem;
import com.sap.cleancore.analyzer.model.inventory.InventoryCategory;
import com.sap.cleancore.analyzer.model.inventory.InventoryItem;
import com.sap.cleancore.analyzer.utils.ResourceLoader;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.util.EnumMap;
import java.util.Map;

/**
 * Assigns an effort category + man-days to integration {@link InventoryItem}s,
 * based on the {@code integration} block of object_rules.json and the active
 * scenario effort multiplier (from {@link EffortRules}). Unused items
 * (usageCount == 0) are treated as retire candidates (≈0 MD).
 */
public class InventoryEffortEstimator {

    private static class Base {
        MigrationItem.EffortCategory cat;
        double md;
        Base(MigrationItem.EffortCategory cat, double md) { this.cat = cat; this.md = md; }
    }

    private final EnumMap<InventoryCategory, Base> table = new EnumMap<>(InventoryCategory.class);
    private boolean loaded;

    @SuppressWarnings("unchecked")
    private synchronized void loadIfNeeded() {
        if (loaded) return;
        try {
            String text = ResourceLoader.readBundleText(null, "resources/mapping/object_rules.json");
            Map<String, Object> root = (Map<String, Object>) SimpleJsonParser.parse(text);
            Map<String, Object> integ = SimpleJsonParser.obj(root, "integration");
            for (Map.Entry<String, Object> e : integ.entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                Map<String, Object> v = (Map<String, Object>) e.getValue();
                InventoryCategory cat;
                try { cat = InventoryCategory.valueOf(e.getKey()); }
                catch (IllegalArgumentException ignored) { continue; } // skip _comment etc.
                MigrationItem.EffortCategory ec = parseCat(SimpleJsonParser.str(v, "category", "M"));
                double md = SimpleJsonParser.dbl(v, "md", 1.0);
                table.put(cat, new Base(ec, md));
            }
        } catch (Exception e) {
            // defaults applied lazily in baseFor()
        }
        loaded = true;
    }

    public void estimate(InventoryItem item) {
        if (item == null) return;
        loadIfNeeded();

        // Unused → retire, ~0 effort.
        if (item.getUsageCount() == 0) {
            item.setEffortCategory(MigrationItem.EffortCategory.S);
            item.setEstimatedMD(0.0);
            item.setRisk("LOW");
            if (item.getMigrationNote() == null) {
                item.setMigrationNote("Unused (usage=0) — decommission / out of scope.");
            }
            return;
        }

        Base b = baseFor(item.getCategory());
        double mult = EffortRules.getInstance().activeMultiplier();
        item.setEffortCategory(b.cat);
        item.setEstimatedMD(Math.round(b.md * mult * 100.0) / 100.0);
        item.setRisk(riskFor(b.cat));
    }

    private Base baseFor(InventoryCategory cat) {
        Base b = table.get(cat != null ? cat : InventoryCategory.UNKNOWN);
        if (b != null) return b;
        // Safe defaults if config missing.
        switch (cat != null ? cat : InventoryCategory.UNKNOWN) {
            case RFC_DESTINATION: case SICF_SERVICE: return new Base(MigrationItem.EffortCategory.S, 0.5);
            case PI_PO_INTERFACE: case BTP_INTEGRATION: return new Base(MigrationItem.EffortCategory.L, 4.0);
            default: return new Base(MigrationItem.EffortCategory.M, 1.5);
        }
    }

    private MigrationItem.EffortCategory parseCat(String s) {
        try { return MigrationItem.EffortCategory.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return MigrationItem.EffortCategory.M; }
    }

    private String riskFor(MigrationItem.EffortCategory cat) {
        if (cat == MigrationItem.EffortCategory.XL || cat == MigrationItem.EffortCategory.L) return "HIGH";
        if (cat == MigrationItem.EffortCategory.M) return "MEDIUM";
        return "LOW";
    }
}
