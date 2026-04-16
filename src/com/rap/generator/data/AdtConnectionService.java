package com.rap.generator.data;

import com.rap.generator.model.FieldDefinition;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Connects to SAP system to fetch CDS view / table metadata.
 *
 * Strategy:
 * 1. First tries native ADT API (com.sap.adt.* packages) - uses existing logged-in session
 * 2. Falls back to direct REST API with basic auth
 * 3. Last resort: clipboard import (no connection needed)
 */
public class AdtConnectionService {

    private static AdtConnectionService instance;

    // Connection state
    private IProject adtProject;
    private String systemId;
    private String client;
    private String username;
    private boolean connected = false;

    // For manual REST connection (fallback)
    private String systemUrl;
    private String password;
    private String csrfToken;
    private String sessionCookie;

    private AdtConnectionService() {}

    public static synchronized AdtConnectionService getInstance() {
        if (instance == null) {
            instance = new AdtConnectionService();
        }
        return instance;
    }

    public boolean isConnected() { return connected; }
    public String getSystemId() { return systemId; }
    public String getDisplayName() {
        if (systemId != null) return systemId + " (" + client + ")";
        if (systemUrl != null) return systemUrl;
        return "Not connected";
    }

    // ===== CONNECTION METHODS =====

    /**
     * Connect using an existing Eclipse ADT ABAP project.
     * No password needed - uses the project's existing authenticated session.
     */
    public void connectViaProject(IProject project, String systemId, String client, String user) {
        this.adtProject = project;
        this.systemId = systemId;
        this.client = client;
        this.username = user;
        this.connected = true;
    }

    /**
     * Connect manually via REST API with URL + credentials.
     */
    public boolean connectManual(String url, String client, String user, String pass) {
        this.systemUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.client = client;
        this.username = user;
        this.password = pass;
        this.systemId = url;

        try {
            HttpURLConnection conn = openRestConnection("/sap/bc/adt/discovery", "GET");
            conn.setRequestProperty("X-CSRF-Token", "Fetch");
            int code = conn.getResponseCode();
            if (code == 200 || code == 302) {
                csrfToken = conn.getHeaderField("X-CSRF-Token");
                List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
                if (cookies != null) sessionCookie = String.join("; ", cookies);
                connected = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void disconnect() {
        connected = false;
        adtProject = null;
        systemId = null;
        systemUrl = null;
        csrfToken = null;
        sessionCookie = null;
    }

    // ===== FIELD FETCHING =====

    /**
     * Fetch fields from a CDS view or table.
     * Automatically chooses the best available method.
     */
    public List<FieldDefinition> fetchFields(String objectName, boolean isCdsView) throws IOException {
        if (!connected) throw new IOException("Not connected to SAP system.");

        // Strategy 1: Use ADT native API (if ADT project is available)
        if (adtProject != null) {
            List<FieldDefinition> fields = fetchViaAdtNative(objectName, isCdsView);
            if (fields != null && !fields.isEmpty()) return fields;
        }

        // Strategy 2: Use direct REST API (if URL is available)
        if (systemUrl != null) {
            return fetchViaRest(objectName, isCdsView);
        }

        // Strategy 3: Explain workaround
        throw new IOException(
            "Could not fetch fields automatically.\n\n"
            + "Please use this workaround:\n"
            + "1. Open '" + objectName + "' in Eclipse ADT\n"
            + "2. Select all fields (Ctrl+A in the field list)\n"
            + "3. Copy (Ctrl+C)\n"
            + "4. Click 'Import from Clipboard' in the RAP Generator"
        );
    }

    // ===== STRATEGY 1: ADT Native API =====

    private List<FieldDefinition> fetchViaAdtNative(String objectName, boolean isCdsView) {
        try {
            // Get IAbapProject adapter from the Eclipse project
            Object abapProject = adtProject.getAdapter(
                Class.forName("com.sap.adt.project.IAbapProject"));

            if (abapProject == null) return null;

            // Get the destination (contains connection info)
            Method getDestination = abapProject.getClass().getMethod("getDestinationData");
            Object destData = getDestination.invoke(abapProject);

            if (destData == null) {
                // Try alternative method name
                getDestination = abapProject.getClass().getMethod("getDestination");
                destData = getDestination.invoke(abapProject);
            }

            if (destData == null) return null;

            // Extract host/port/scheme from destination
            String host = invokeStringMethod(destData, "getHost");
            if (host == null) host = invokeStringMethod(destData, "getSystemHost");

            Object port = null;
            try { port = destData.getClass().getMethod("getPort").invoke(destData); } catch (Exception e) {}
            try { if (port == null) port = destData.getClass().getMethod("getSystemPort").invoke(destData); } catch (Exception e) {}

            String scheme = invokeStringMethod(destData, "getScheme");
            if (scheme == null) scheme = "https";

            if (host != null) {
                this.systemUrl = scheme + "://" + host + (port != null ? ":" + port : "");

                // Try to get auth from the destination's session
                tryExtractSessionFromDestination(destData);

                // Now fetch via REST
                return fetchViaRest(objectName, isCdsView);
            }

        } catch (ClassNotFoundException e) {
            // ADT packages not available - normal for non-ADT Eclipse
        } catch (Exception e) {
            // Reflection error - API might have changed
        }
        return null;
    }

    private void tryExtractSessionFromDestination(Object destData) {
        try {
            // Try to get the user/client from destination
            String user = invokeStringMethod(destData, "getUser");
            if (user != null) this.username = user;

            String cli = invokeStringMethod(destData, "getClient");
            if (cli != null) this.client = cli;

            // Try to get session cookie
            String cookie = invokeStringMethod(destData, "getSessionCookie");
            if (cookie != null) this.sessionCookie = cookie;
        } catch (Exception e) {
            // Best effort
        }
    }

    private String invokeStringMethod(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== STRATEGY 2: Direct REST API =====

    private List<FieldDefinition> fetchViaRest(String objectName, boolean isCdsView) throws IOException {
        if (isCdsView) {
            String path = "/sap/bc/adt/ddic/ddl/sources/" + objectName.toLowerCase();
            String source = restGet(path, "text/plain");
            return parseCdsSource(source);
        } else {
            String path = "/sap/bc/adt/ddic/tables/" + objectName.toLowerCase() + "/content";
            String xml = restGet(path, "application/xml");
            return parseTableXml(xml);
        }
    }

    private String restGet(String path, String accept) throws IOException {
        HttpURLConnection conn = openRestConnection(path, "GET");
        conn.setRequestProperty("Accept", accept);

        int code = conn.getResponseCode();
        if (code == 401 || code == 403) {
            throw new IOException("Authentication failed (HTTP " + code + ").\n"
                + "Your ADT session may have expired.\n"
                + "Try: Disconnect and reconnect, or use manual connection with password.");
        }
        if (code != 200) {
            throw new IOException("HTTP " + code + " for " + path);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
            return sb.toString();
        }
    }

    private HttpURLConnection openRestConnection(String path, String method) throws IOException {
        String fullUrl = systemUrl + path;
        if (client != null && !client.isEmpty()) {
            fullUrl += (fullUrl.contains("?") ? "&" : "?") + "sap-client=" + client;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        // Auth
        if (password != null) {
            String auth = username + ":" + password;
            conn.setRequestProperty("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        if (sessionCookie != null) {
            conn.setRequestProperty("Cookie", sessionCookie);
        }
        if (csrfToken != null && ("POST".equals(method) || "PUT".equals(method))) {
            conn.setRequestProperty("X-CSRF-Token", csrfToken);
        }
        return conn;
    }

    // ===== CDS SOURCE PARSER =====

    private List<FieldDefinition> parseCdsSource(String source) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (source == null || source.isEmpty()) return fields;

        boolean inSelectList = false;
        for (String line : source.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.equals("{")) { inSelectList = true; continue; }
            if (trimmed.equals("}")) { inSelectList = false; continue; }
            if (!inSelectList) continue;

            // Skip non-field lines
            if (trimmed.startsWith("//") || trimmed.startsWith("@") || trimmed.isEmpty()
                || trimmed.startsWith("_") || trimmed.startsWith("association")
                || trimmed.startsWith("composition")) continue;

            String fieldLine = trimmed.replaceAll(",$", "").trim();
            boolean isKey = fieldLine.startsWith("key ");
            if (isKey) fieldLine = fieldLine.substring(4).trim();

            String fieldName, alias;
            if (fieldLine.contains(" as ")) {
                String[] parts = fieldLine.split("\\s+as\\s+");
                fieldName = parts[0].trim();
                alias = parts.length > 1 ? parts[1].trim() : fieldName;
            } else {
                fieldName = fieldLine.split("\\s+")[0].trim();
                alias = fieldName;
            }

            if (alias.startsWith("_") || alias.isEmpty()) continue;

            FieldDefinition field = new FieldDefinition();
            field.setName(alias);
            field.setAbapName(fieldName.toLowerCase());
            field.setLabel(camelToLabel(alias));
            field.setKey(isKey);
            field.setAbapType("abap.char(40)");
            fields.add(field);
        }
        return fields;
    }

    // ===== TABLE XML PARSER =====

    private List<FieldDefinition> parseTableXml(String xml) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return fields;

        String[] segments = xml.split("<DD03P>");
        for (int i = 1; i < segments.length; i++) {
            String seg = segments[i];
            String name = xmlVal(seg, "FIELDNAME");
            String type = xmlVal(seg, "DATATYPE");
            String len = xmlVal(seg, "LENG");
            String dec = xmlVal(seg, "DECIMALS");
            String key = xmlVal(seg, "KEYFLAG");
            String text = xmlVal(seg, "DDTEXT");
            String notNull = xmlVal(seg, "NOTNULL");

            if (name == null || name.startsWith(".") || "MANDT".equals(name) || "CLIENT".equals(name)) continue;

            FieldDefinition field = new FieldDefinition();
            field.setName(toCamelCase(name));
            field.setAbapName(name.toLowerCase());
            field.setAbapType(mapType(type, len, dec));
            field.setLabel(text != null ? text : camelToLabel(toCamelCase(name)));
            field.setKey("X".equals(key));
            field.setMandatory("X".equals(notNull));
            fields.add(field);
        }
        return fields;
    }

    // ===== STATIC HELPERS =====

    public static List<ProjectInfo> findAbapProjects() {
        List<ProjectInfo> result = new ArrayList<>();

        // Strategy 1: Current workspace projects
        try {
            for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                if (!project.isOpen()) continue;
                boolean isAbap = false;
                try { isAbap = project.hasNature("com.sap.adt.project.nature"); } catch (Exception e) {}
                if (!isAbap) {
                    try { isAbap = project.hasNature("com.sap.adt.core.adtproject"); } catch (Exception e) {}
                }
                if (!isAbap && project.getName().matches("^[A-Z0-9]+_\\d{3}_.*")) {
                    isAbap = true;
                }
                if (isAbap) {
                    ProjectInfo info = new ProjectInfo();
                    info.project = project;
                    info.displayName = project.getName();
                    String[] parts = project.getName().split("_");
                    if (parts.length >= 3) {
                        info.systemId = parts[0];
                        info.client = parts[1];
                        info.user = parts[2].toUpperCase();
                    } else {
                        info.systemId = project.getName();
                        info.client = "100";
                        info.user = "";
                    }
                    // Try to read .destination.properties
                    tryLoadDestinationProps(info);
                    result.add(info);
                }
            }
        } catch (Exception e) { /* workspace not available */ }

        // Strategy 2: Scan known Eclipse workspace locations for .destination.properties files
        if (result.isEmpty()) {
            String home = System.getProperty("user.home");
            String[] workspaceDirs = {
                home + "/eclipse-workspace",
                home + "/workspace",
                home + "/Documents/workspace",
                home + "/ABAP_workspace"
            };
            for (String wsDir : workspaceDirs) {
                scanWorkspaceForAbapProjects(wsDir, result);
            }
        }

        return result;
    }

    /**
     * Scan a workspace directory for ABAP project .destination.properties files.
     */
    private static void scanWorkspaceForAbapProjects(String workspaceDir, List<ProjectInfo> result) {
        try {
            java.io.File cacheDir = new java.io.File(workspaceDir,
                ".metadata/.plugins/org.eclipse.core.resources.semantic/.cache");
            if (!cacheDir.exists()) return;

            java.io.File[] dirs = cacheDir.listFiles(java.io.File::isDirectory);
            if (dirs == null) return;

            for (java.io.File projDir : dirs) {
                java.io.File destFile = new java.io.File(projDir, ".destination.properties");
                if (destFile.exists()) {
                    ProjectInfo info = parseDestinationFile(destFile, projDir.getName());
                    if (info != null) {
                        // Check not already in list
                        boolean exists = result.stream().anyMatch(p -> p.displayName.equals(info.displayName));
                        if (!exists) result.add(info);
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    /**
     * Parse a .destination.properties file to extract SAP system connection info.
     */
    private static ProjectInfo parseDestinationFile(java.io.File destFile, String projectName) {
        try {
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(destFile)) {
                props.load(fis);
            }

            ProjectInfo info = new ProjectInfo();
            info.displayName = projectName;
            info.systemId = props.getProperty("systemId", projectName);
            info.client = props.getProperty("client", "100");
            info.user = props.getProperty("user", "");
            info.server = props.getProperty("server");
            info.systemNumber = props.getProperty("systemNumber", "00");
            info.router = props.getProperty("router");

            // Build HTTPS URL from server + system number
            // SAP HTTPS port = 443 + systemNumber*100 (e.g., 00 -> 44300, 01 -> 44301)
            if (info.server != null && !info.server.isEmpty()) {
                int httpsPort = 44300 + Integer.parseInt(info.systemNumber) ;
                info.url = "https://" + info.server + ":" + httpsPort;
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Try to load .destination.properties from workspace cache for a project.
     */
    private static void tryLoadDestinationProps(ProjectInfo info) {
        if (info.project == null) return;
        try {
            String wsRoot = info.project.getWorkspace().getRoot().getLocation().toOSString();
            // Go up from workspace root to .metadata
            java.io.File destFile = new java.io.File(
                wsRoot + "/../.metadata/.plugins/org.eclipse.core.resources.semantic/.cache/"
                + info.displayName + "/.destination.properties");
            if (destFile.exists()) {
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(destFile)) {
                    props.load(fis);
                }
                info.server = props.getProperty("server");
                info.systemNumber = props.getProperty("systemNumber", "00");
                if (info.server != null) {
                    int httpsPort = 44300 + Integer.parseInt(info.systemNumber);
                    info.url = "https://" + info.server + ":" + httpsPort;
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    private String xmlVal(String xml, String tag) {
        int s = xml.indexOf("<" + tag + ">");
        if (s < 0) return null;
        s += tag.length() + 2;
        int e = xml.indexOf("</" + tag + ">", s);
        return e > s ? xml.substring(s, e).trim() : null;
    }

    private String mapType(String t, String l, String d) {
        if (t == null) return "abap.char(40)";
        switch (t.toUpperCase()) {
            case "CHAR": return "abap.char(" + (l != null ? l : "40") + ")";
            case "NUMC": return "abap.numc(" + (l != null ? l : "10") + ")";
            case "DATS": return "abap.dats";
            case "TIMS": return "abap.tims";
            case "INT4": return "abap.int4";
            case "INT8": return "abap.int8";
            case "DEC": case "CURR": case "QUAN":
                return "abap." + t.toLowerCase() + "(" + (l != null ? l : "15") + "," + (d != null ? d : "2") + ")";
            case "FLTP": return "abap.fltp";
            case "STRING": return "abap.string";
            case "RAW": return "abap.raw(" + (l != null ? l : "16") + ")";
            case "CLNT": return "abap.clnt";
            case "LANG": return "abap.lang";
            case "CUKY": return "abap.cuky";
            case "UNIT": return "abap.unit";
            case "UTCLONG": return "abap.utclong";
            default: return "abap.char(" + (l != null ? l : "40") + ")";
        }
    }

    private String toCamelCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : s.toLowerCase().toCharArray()) {
            if (c == '_') { up = true; }
            else { sb.append(up ? Character.toUpperCase(c) : c); up = false; }
        }
        return sb.toString();
    }

    private String camelToLabel(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }

    // ===== DATA CLASSES =====

    public static class ProjectInfo {
        public IProject project;
        public String displayName;
        public String systemId;
        public String client;
        public String user;
        public String server;
        public String systemNumber;
        public String router;
        public String url;
    }
}
