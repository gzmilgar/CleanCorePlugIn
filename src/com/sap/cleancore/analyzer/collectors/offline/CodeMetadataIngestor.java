package com.sap.cleancore.analyzer.collectors.offline;

import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optional offline metadata enrichment. Reads a {@code metadata.json} sidecar
 * (see {@code resources/extract/code_metadata_schema.json}) sitting next to the
 * dumped ABAP source files and enriches the already-built {@link ZObject}s with
 * data ADT would normally supply: real TADIR type, development package
 * (devClass — enables modification detection), author, created-on date, and
 * ABAP Call Monitor usage count (enables usage-based RETIRE).
 *
 * <p>Best-effort and dependency-free: a missing/malformed file is a no-op
 * (objects keep their file-derived defaults). Matching is by object name
 * (case-insensitive), so source files and metadata can be in any order.
 */
public class CodeMetadataIngestor {

    /** Enrich the given objects from {@code <folder>/metadata.json} if present. */
    public void enrichFromFolder(File folder, List<ZObject> objects) {
        if (folder == null || objects == null || objects.isEmpty()) return;
        File meta = new File(folder, "metadata.json");
        if (!meta.isFile()) return;
        enrich(meta, objects);
    }

    @SuppressWarnings("unchecked")
    public void enrich(File metadataFile, List<ZObject> objects) {
        if (metadataFile == null || !metadataFile.isFile() || objects == null) return;
        Map<String, Object> byName = new LinkedHashMap<>();
        try {
            Object parsed = SimpleJsonParser.parse(read(metadataFile));
            if (!(parsed instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) parsed;
            for (Map<String, Object> o : SimpleJsonParser.arr(root, "objects")) {
                String name = SimpleJsonParser.str(o, "name", null);
                if (name != null) byName.put(name.toUpperCase(Locale.ROOT), o);
            }
        } catch (Exception e) {
            return; // graceful: leave objects as-is
        }
        if (byName.isEmpty()) return;

        for (ZObject z : objects) {
            if (z == null || z.getName() == null) continue;
            Object mo = byName.get(z.getName().toUpperCase(Locale.ROOT));
            if (!(mo instanceof Map)) continue;
            applyTo(z, (Map<String, Object>) mo);
        }
    }

    private void applyTo(ZObject z, Map<String, Object> m) {
        String tadir = SimpleJsonParser.str(m, "tadirType", null);
        if (tadir != null) {
            ZObjectType t = ZObjectType.fromTadir(tadir);
            if (t != ZObjectType.UNKNOWN) z.setType(t);
        }
        String devClass = SimpleJsonParser.str(m, "devClass", null);
        if (devClass != null && !devClass.isEmpty()) z.setDevClass(devClass);
        String author = SimpleJsonParser.str(m, "author", null);
        if (author != null) z.setAuthor(author);
        String createdOn = SimpleJsonParser.str(m, "createdOn", null);
        if (createdOn != null) z.setCreatedOn(createdOn);
        if (m.containsKey("usageCount")) {
            z.setUsageCount((long) SimpleJsonParser.dbl(m, "usageCount", -1));
        }
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
