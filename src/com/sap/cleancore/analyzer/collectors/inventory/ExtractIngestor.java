package com.sap.cleancore.analyzer.collectors.inventory;

import com.sap.cleancore.analyzer.model.inventory.InventoryCategory;
import com.sap.cleancore.analyzer.model.inventory.InventoryItem;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Primary, offline integration-inventory source: reads a JSON file produced by
 * the bundled ABAP extract report ({@code Z_TRANSFORM_INVENTORY}) and converts
 * it into {@link InventoryItem}s. Works on SAProuter-only / locked systems
 * where ADT REST cannot reach the Basis config tables.
 *
 * <p>Best-effort and dependency-free: a missing/malformed file yields an empty
 * list (never throws to the caller via {@link #ingestQuietly}).
 *
 * <p>Expected JSON shape (see {@code resources/extract/extract_schema.json}):
 * <pre>
 * { "extractVersion": "1.0", "system": "...",
 *   "items": [ { "category": "RFC_DESTINATION", "name": "...", "target": "...",
 *               "protocol": "...", "direction": "OUTBOUND", "usageCount": 12,
 *               "migrationNote": "...", "attributes": { "k": "v" } } ] }
 * </pre>
 */
public class ExtractIngestor {

    @SuppressWarnings("unchecked")
    public List<InventoryItem> ingest(File extractFile) throws Exception {
        List<InventoryItem> out = new ArrayList<>();
        if (extractFile == null || !extractFile.isFile()) return out;
        String text = read(extractFile);
        Object parsed = SimpleJsonParser.parse(text);
        if (!(parsed instanceof Map)) return out;
        Map<String, Object> root = (Map<String, Object>) parsed;

        for (Map<String, Object> o : SimpleJsonParser.arr(root, "items")) {
            InventoryItem it = fromJson(o);
            if (it != null && it.getName() != null) out.add(it);
        }
        return out;
    }

    /** Never throws — returns empty list on any failure (graceful degradation). */
    public List<InventoryItem> ingestQuietly(File extractFile) {
        try { return ingest(extractFile); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    @SuppressWarnings("unchecked")
    private InventoryItem fromJson(Map<String, Object> o) {
        if (o == null) return null;
        InventoryItem it = new InventoryItem();
        it.setCategory(InventoryCategory.fromString(SimpleJsonParser.str(o, "category", null)));
        it.setName(SimpleJsonParser.str(o, "name", null));
        it.setTarget(SimpleJsonParser.str(o, "target", null));
        it.setProtocol(SimpleJsonParser.str(o, "protocol", null));
        it.setDirection(SimpleJsonParser.str(o, "direction", null));
        it.setUsageCount((long) SimpleJsonParser.dbl(o, "usageCount", -1));
        it.setMigrationNote(SimpleJsonParser.str(o, "migrationNote", null));
        Object attrs = o.get("attributes");
        if (attrs instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) attrs).entrySet()) {
                it.putAttribute(e.getKey(), e.getValue() != null ? e.getValue().toString() : null);
            }
        }
        return it;
    }

    private String read(File f) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }
}
