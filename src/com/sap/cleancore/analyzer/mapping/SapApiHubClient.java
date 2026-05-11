package com.sap.cleancore.analyzer.mapping;

import com.sap.cleancore.analyzer.model.MappingEntry;
import com.sap.cleancore.analyzer.utils.SimpleJsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pulls released-API metadata from api.sap.com and converts it into MappingEntry
 * rows that can be merged into the local MappingRepository.
 *
 * Caveat: api.sap.com's public catalog endpoints change over time. The base URL
 * and result-shape extraction live here so they can be swapped without touching
 * the repository. The 'targetUrl' is configurable in CleanCorePreferencePage.
 */
public class SapApiHubClient {

    private final String baseUrl;
    private final long cacheTtlMs;
    private long lastFetchAt = 0L;
    private List<MappingEntry> cached = new ArrayList<>();

    public SapApiHubClient(String baseUrl) {
        this(baseUrl, 24L * 60 * 60 * 1000); // 24h
    }

    public SapApiHubClient(String baseUrl, long cacheTtlMs) {
        this.baseUrl = baseUrl;
        this.cacheTtlMs = cacheTtlMs;
    }

    public synchronized List<MappingEntry> fetch() throws Exception {
        long now = System.currentTimeMillis();
        if (!cached.isEmpty() && (now - lastFetchAt) < cacheTtlMs) {
            return cached;
        }
        String body = httpGet(baseUrl);
        List<MappingEntry> out = parseCatalog(body);
        cached = out;
        lastFetchAt = now;
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<MappingEntry> parseCatalog(String json) {
        List<MappingEntry> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        Object parsed;
        try { parsed = SimpleJsonParser.parse(json); } catch (Exception e) { return out; }

        // We support two shapes:
        //   { "d": { "results": [ {...}, ... ] } }   (OData v2 style)
        //   { "results": [ ... ] }                    (custom)
        List<Map<String, Object>> rows = null;
        if (parsed instanceof Map) {
            Map<String, Object> top = (Map<String, Object>) parsed;
            if (top.containsKey("d") && top.get("d") instanceof Map) {
                rows = SimpleJsonParser.arr((Map<String, Object>) top.get("d"), "results");
            }
            if (rows == null || rows.isEmpty()) {
                rows = SimpleJsonParser.arr(top, "results");
            }
            if (rows == null || rows.isEmpty()) {
                rows = SimpleJsonParser.arr(top, "entries");
            }
        }
        if (rows == null) return out;

        for (Map<String, Object> r : rows) {
            String name = firstNonNull(
                    SimpleJsonParser.str(r, "Name"),
                    SimpleJsonParser.str(r, "name"),
                    SimpleJsonParser.str(r, "Title"),
                    SimpleJsonParser.str(r, "modernName"));
            if (name == null) continue;
            String legacy = firstNonNull(
                    SimpleJsonParser.str(r, "LegacyObject"),
                    SimpleJsonParser.str(r, "ReplacedObject"),
                    SimpleJsonParser.str(r, "legacyName"));
            if (legacy == null) continue;

            MappingEntry e = new MappingEntry();
            e.setLegacyName(legacy);
            e.setLegacyType(guessLegacyType(legacy));
            e.setModernName(name);
            e.setModernType(MappingEntry.ModernType.API_ODATA);
            e.setReleaseState(MappingEntry.ReleaseState.RELEASED);
            e.setSource(MappingEntry.Source.API_HUB);
            e.setDocumentationUrl(SimpleJsonParser.str(r, "Url"));
            out.add(e);
        }
        return out;
    }

    private static MappingEntry.LegacyType guessLegacyType(String legacy) {
        if (legacy == null) return MappingEntry.LegacyType.OTHER;
        String s = legacy.toUpperCase(Locale.ROOT);
        if (s.startsWith("BAPI_")) return MappingEntry.LegacyType.BAPI;
        if (s.startsWith("CL_")) return MappingEntry.LegacyType.CLASS;
        if (s.startsWith("IF_")) return MappingEntry.LegacyType.INTERFACE;
        if (s.startsWith("/") || s.contains("=")) return MappingEntry.LegacyType.CLASS;
        return MappingEntry.LegacyType.FM;
    }

    private static String firstNonNull(String... s) {
        for (String x : s) if (x != null && !x.isEmpty()) return x;
        return null;
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code + " from " + url);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }
}
