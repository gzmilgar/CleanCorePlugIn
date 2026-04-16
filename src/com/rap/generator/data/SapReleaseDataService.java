package com.rap.generator.data;

import com.rap.generator.utils.SimpleJsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SapReleaseDataService {

    private static final String RELEASE_DATA_URL =
        "https://raw.githubusercontent.com/SAP/abap-atc-cr-cv-s4hc/main/src/objectReleaseInfoLatest.json";
    private static final String CACHE_FILE_NAME = "sap-release-data.json";

    private static SapReleaseDataService instance;

    private List<ReleaseObject> allObjects = new ArrayList<>();
    private Map<String, List<ReleaseObject>> byType = new HashMap<>();
    private Map<String, ReleaseObject> byKey = new HashMap<>();
    private boolean loaded = false;
    private Date lastUpdated;

    private SapReleaseDataService() {}

    public static synchronized SapReleaseDataService getInstance() {
        if (instance == null) {
            instance = new SapReleaseDataService();
        }
        return instance;
    }

    public boolean isLoaded() { return loaded; }
    public Date getLastUpdated() { return lastUpdated; }
    public int getObjectCount() { return allObjects.size(); }

    public boolean loadFromCache() {
        Path cachePath = getCachePath();
        if (cachePath == null || !Files.exists(cachePath)) return false;
        try {
            String json = new String(Files.readAllBytes(cachePath), StandardCharsets.UTF_8);
            parseAndIndex(json);
            lastUpdated = new Date(Files.getLastModifiedTime(cachePath).toMillis());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Download data in a background thread. Calls onComplete on the SWT UI thread when done.
     */
    public void downloadDataAsync(Runnable onComplete) {
        Thread thread = new Thread(() -> {
            try {
                String json = downloadJson(RELEASE_DATA_URL);
                parseAndIndex(json);

                Path cachePath = getCachePath();
                if (cachePath != null) {
                    Files.createDirectories(cachePath.getParent());
                    Files.write(cachePath, json.getBytes(StandardCharsets.UTF_8));
                }
                lastUpdated = new Date();

                if (onComplete != null) {
                    org.eclipse.swt.widgets.Display.getDefault().asyncExec(onComplete);
                }
            } catch (Exception e) {
                org.eclipse.swt.widgets.Display.getDefault().asyncExec(() -> {
                    // Show error on UI thread if needed
                });
            }
        }, "SAP-Release-Data-Download");
        thread.setDaemon(true);
        thread.start();
    }

    public List<ReleaseObject> search(String query, String objectType, String state, int limit) {
        if (!loaded) return Collections.emptyList();
        String q = query != null ? query.toUpperCase() : "";
        List<ReleaseObject> source = (objectType != null && !objectType.isEmpty() && !"*".equals(objectType))
            ? byType.getOrDefault(objectType, Collections.emptyList())
            : allObjects;

        return source.stream()
            .filter(obj -> {
                if (!q.isEmpty()) {
                    boolean match = obj.getTadirObjName().toUpperCase().contains(q)
                        || (obj.getApplicationComponent() != null
                            && obj.getApplicationComponent().toUpperCase().contains(q));
                    if (!match) return false;
                }
                if (state != null && !state.isEmpty() && !state.equals(obj.getState())) return false;
                return true;
            })
            .limit(limit > 0 ? limit : 200)
            .collect(Collectors.toList());
    }

    public ReleaseCheckResult checkRelease(String objectType, String objectName) {
        if (!loaded) return new ReleaseCheckResult(false, null, null);
        String key = objectType + ":" + objectName;
        ReleaseObject obj = byKey.get(key);
        if (obj == null) return new ReleaseCheckResult(false, null, null);
        return new ReleaseCheckResult(true, obj.getState(), obj.getSuccessors());
    }

    public Set<String> getAvailableObjectTypes() { return byType.keySet(); }

    @SuppressWarnings("unchecked")
    private void parseAndIndex(String json) {
        allObjects.clear();
        byType.clear();
        byKey.clear();

        SimpleJsonParser parser = new SimpleJsonParser(json);
        Map<String, Object> root = parser.parseObject();
        List<Map<String, Object>> items = SimpleJsonParser.getArray(root, "objectReleaseInfo");

        for (Map<String, Object> item : items) {
            ReleaseObject obj = new ReleaseObject();
            obj.setTadirObject(SimpleJsonParser.getString(item, "tadirObject"));
            obj.setTadirObjName(SimpleJsonParser.getString(item, "tadirObjName"));
            obj.setObjectType(SimpleJsonParser.getString(item, "objectType"));
            obj.setObjectKey(SimpleJsonParser.getString(item, "objectKey"));
            obj.setSoftwareComponent(SimpleJsonParser.getString(item, "softwareComponent"));
            obj.setApplicationComponent(SimpleJsonParser.getString(item, "applicationComponent"));
            obj.setState(SimpleJsonParser.getString(item, "state"));

            Object successorsRaw = item.get("successors");
            if (successorsRaw instanceof List) {
                List<ReleaseObject.Successor> successors = new ArrayList<>();
                for (Object sRaw : (List<?>) successorsRaw) {
                    if (sRaw instanceof Map) {
                        Map<String, Object> sMap = (Map<String, Object>) sRaw;
                        ReleaseObject.Successor s = new ReleaseObject.Successor();
                        s.setTadirObject(SimpleJsonParser.getString(sMap, "tadirObject"));
                        s.setTadirObjName(SimpleJsonParser.getString(sMap, "tadirObjName"));
                        successors.add(s);
                    }
                }
                obj.setSuccessors(successors);
            }

            allObjects.add(obj);
            byType.computeIfAbsent(obj.getTadirObject(), k -> new ArrayList<>()).add(obj);
            byKey.put(obj.getTadirObject() + ":" + obj.getTadirObjName(), obj);
        }
        loaded = true;
    }

    private String downloadJson(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private Path getCachePath() {
        return Path.of(System.getProperty("user.home"), ".rap-generator", CACHE_FILE_NAME);
    }

    public static class ReleaseCheckResult {
        private final boolean found;
        private final String state;
        private final List<ReleaseObject.Successor> successors;

        public ReleaseCheckResult(boolean found, String state, List<ReleaseObject.Successor> successors) {
            this.found = found;
            this.state = state;
            this.successors = successors;
        }

        public boolean isFound() { return found; }
        public String getState() { return state; }
        public List<ReleaseObject.Successor> getSuccessors() { return successors; }
        public boolean isReleased() { return "released".equals(state); }
    }
}
