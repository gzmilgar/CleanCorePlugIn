package com.sap.cleancore.analyzer.mapping;

import com.sap.cleancore.Activator;
import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.utils.JsonWriter;
import com.sap.cleancore.analyzer.utils.ResourceLoader;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory mapping store backed by:
 *   1. Bundled baseline JSON (resources/mapping/{fm,bapi}_mapping.json)
 *   2. User overrides file (workspace .metadata/.plugins/com.sap.cleancore.analyzer/user_mapping.json)
 *   3. api.sap.com sync (merged in by SapApiHubClient)
 *
 * Conflict rule: USER overrides API_HUB overrides BASELINE.
 */
public class MappingRepository {

    private static MappingRepository instance;

    /** Keyed by MappingEntry.key() (e.g. "BAPI::BAPI_SALESORDER_CREATEFROMDAT2"). */
    private final Map<String, MappingEntry> entries = new LinkedHashMap<>();

    private boolean loaded;

    public static synchronized MappingRepository getInstance() {
        if (instance == null) instance = new MappingRepository();
        return instance;
    }

    private MappingRepository() {}

    public synchronized void loadIfNeeded() {
        if (loaded) return;
        try {
            mergeFromBundleJson("resources/mapping/fm_mapping.json", MappingEntry.Source.BASELINE);
            mergeFromBundleJson("resources/mapping/bapi_mapping.json", MappingEntry.Source.BASELINE);
            File userFile = userOverridesFile();
            if (userFile != null && userFile.exists()) {
                String json = readFile(userFile);
                mergeFromJsonString(json, MappingEntry.Source.USER);
            }
        } catch (Exception e) {
            log("Mapping load failed: " + e.getMessage());
        }
        loaded = true;
    }

    public synchronized void reload() {
        entries.clear();
        loaded = false;
        loadIfNeeded();
    }

    @SuppressWarnings("unchecked")
    private void mergeFromBundleJson(String pathInBundle, MappingEntry.Source defaultSource) throws Exception {
        String text;
        try { text = ResourceLoader.readBundleText(null, pathInBundle); }
        catch (Exception e) { return; }
        mergeFromJsonString(text, defaultSource);
    }

    @SuppressWarnings("unchecked")
    private void mergeFromJsonString(String text, MappingEntry.Source defaultSource) {
        if (text == null || text.trim().isEmpty()) return;
        Map<String, Object> root = (Map<String, Object>) SimpleJsonParser.parse(text);
        List<Map<String, Object>> list = SimpleJsonParser.arr(root, "entries");
        for (Map<String, Object> row : list) {
            MappingEntry e = readEntry(row, defaultSource);
            if (e == null) continue;
            MappingEntry existing = entries.get(e.key());
            if (existing == null || !existing.isUserOverride()) {
                // Only overwrite if existing is not a user override.
                entries.put(e.key(), e);
            }
        }
    }

    private MappingEntry readEntry(Map<String, Object> row, MappingEntry.Source defaultSource) {
        String legacyName = SimpleJsonParser.str(row, "legacyName");
        if (legacyName == null) return null;
        MappingEntry e = new MappingEntry();
        e.setLegacyName(legacyName);
        e.setLegacyType(parseLegacy(SimpleJsonParser.str(row, "legacyType")));
        e.setModernName(SimpleJsonParser.str(row, "modernName"));
        e.setModernType(parseModern(SimpleJsonParser.str(row, "modernType")));
        e.setReleaseState(parseRelease(SimpleJsonParser.str(row, "releaseState")));
        e.setNotes(SimpleJsonParser.str(row, "notes"));
        e.setDocumentationUrl(SimpleJsonParser.str(row, "documentationUrl"));
        String srcStr = SimpleJsonParser.str(row, "source");
        MappingEntry.Source src = parseSource(srcStr);
        e.setSource(src != null ? src : defaultSource);
        e.setUserOverride(SimpleJsonParser.bool(row, "userOverride", src == MappingEntry.Source.USER));
        return e;
    }

    public List<MappingEntry> all() {
        loadIfNeeded();
        return new ArrayList<>(entries.values());
    }

    public MappingEntry find(MappingEntry.LegacyType type, String name) {
        loadIfNeeded();
        if (name == null) return null;
        String key = (type != null ? type.name() : "?") + "::" + name.toUpperCase(Locale.ROOT);
        return entries.get(key);
    }

    public List<MappingEntry> search(String text) {
        loadIfNeeded();
        if (text == null || text.isEmpty()) return all();
        String q = text.toLowerCase(Locale.ROOT);
        List<MappingEntry> out = new ArrayList<>();
        for (MappingEntry e : entries.values()) {
            String hay = (safe(e.getLegacyName()) + " " + safe(e.getModernName()) + " "
                    + safe(e.getNotes()) + " " + (e.getLegacyType() != null ? e.getLegacyType().name() : "")
                    + " " + (e.getModernType() != null ? e.getModernType().name() : "")).toLowerCase(Locale.ROOT);
            if (hay.contains(q)) out.add(e);
        }
        return out;
    }

    public synchronized void upsert(MappingEntry e) {
        loadIfNeeded();
        e.setUserOverride(true);
        e.setSource(MappingEntry.Source.USER);
        entries.put(e.key(), e);
    }

    public synchronized void remove(MappingEntry e) {
        loadIfNeeded();
        entries.remove(e.key());
    }

    public synchronized void mergeRemote(Collection<MappingEntry> remote) {
        loadIfNeeded();
        for (MappingEntry r : remote) {
            MappingEntry existing = entries.get(r.key());
            if (existing != null && existing.isUserOverride()) continue;
            r.setSource(MappingEntry.Source.API_HUB);
            entries.put(r.key(), r);
        }
    }

    /** Persist user-overridden entries (only) to workspace file. */
    public synchronized void saveUserOverrides() throws Exception {
        File f = userOverridesFile();
        if (f == null) return;
        f.getParentFile().mkdirs();

        List<Object> list = new ArrayList<>();
        for (MappingEntry e : entries.values()) {
            if (!e.isUserOverride()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("legacyName", e.getLegacyName());
            row.put("legacyType", e.getLegacyType() != null ? e.getLegacyType().name() : null);
            row.put("modernName", e.getModernName());
            row.put("modernType", e.getModernType() != null ? e.getModernType().name() : null);
            row.put("releaseState", e.getReleaseState() != null ? e.getReleaseState().name() : null);
            row.put("notes", e.getNotes());
            row.put("documentationUrl", e.getDocumentationUrl());
            row.put("userOverride", true);
            row.put("source", "USER");
            list.add(row);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entries", list);

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8))) {
            w.write(JsonWriter.write(root));
        }
    }

    public File userOverridesFile() {
        try {
            String stateLoc;
            if (Activator.getDefault() != null) {
                stateLoc = Activator.getDefault().getStateLocation().toOSString();
            } else {
                stateLoc = System.getProperty("java.io.tmpdir");
            }
            return new File(stateLoc, "user_mapping.json");
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFile(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static MappingEntry.LegacyType parseLegacy(String s) {
        if (s == null) return MappingEntry.LegacyType.OTHER;
        try { return MappingEntry.LegacyType.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return MappingEntry.LegacyType.OTHER; }
    }

    private static MappingEntry.ModernType parseModern(String s) {
        if (s == null) return MappingEntry.ModernType.NO_REPLACEMENT;
        try { return MappingEntry.ModernType.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return MappingEntry.ModernType.NO_REPLACEMENT; }
    }

    private static MappingEntry.ReleaseState parseRelease(String s) {
        if (s == null) return MappingEntry.ReleaseState.UNKNOWN;
        try { return MappingEntry.ReleaseState.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return MappingEntry.ReleaseState.UNKNOWN; }
    }

    private static MappingEntry.Source parseSource(String s) {
        if (s == null) return null;
        try { return MappingEntry.Source.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return null; }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static void log(String msg) {
        // Avoid hard dependency on Eclipse logging during tests
        System.err.println("[MappingRepository] " + msg);
    }
}
