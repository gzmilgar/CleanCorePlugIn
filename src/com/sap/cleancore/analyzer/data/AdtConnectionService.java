package com.sap.cleancore.analyzer.data;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * ADT (ABAP Development Tools) connection. Uses an Eclipse ABAP project's
 * existing session when possible, otherwise falls back to direct REST against
 * /sap/bc/adt with user-supplied credentials.
 *
 * Standard ADT connection pattern -- here we focus on
 * the set of endpoints we need for Clean Core scanning:
 *   /sap/bc/adt/repository/nodestructure          (package tree)
 *   /sap/bc/adt/programs/programs/<name>/source/main
 *   /sap/bc/adt/functions/groups/<grp>/fmodules/<fm>/source/main
 *   /sap/bc/adt/oo/classes/<cls>/source/main
 *   /sap/bc/adt/atc/runs                          (ATC run)
 *   /sap/bc/adt/atc/worklists                     (ATC results)
 */
public class AdtConnectionService {

    private static AdtConnectionService instance;

    private IProject adtProject;
    private String systemId;
    private String client = "100";
    private String username;
    private String password;
    private String systemUrl;
    private String csrfToken;
    private String sessionCookie;
    private boolean connected;

    private AdtConnectionService() {}

    public static synchronized AdtConnectionService getInstance() {
        if (instance == null) instance = new AdtConnectionService();
        return instance;
    }

    public boolean isConnected() { return connected; }
    public String getSystemId() { return systemId; }
    public String getDisplayName() {
        if (systemId != null) return systemId + " (" + client + ")";
        if (systemUrl != null) return systemUrl;
        return "Not connected";
    }
    public String getSystemUrl() { return systemUrl; }
    public IProject getAdtProject() { return adtProject; }

    public void connectViaProject(IProject project, String systemId, String client, String user) {
        this.adtProject = project;
        this.systemId = systemId;
        this.client = client != null ? client : "100";
        this.username = user;
        this.connected = true;
        // Strategy 1: ADT reflection API (works when project has live IAbapProject adapter).
        tryDeriveUrlFromProject();
        // Strategy 2: read the workspace's .destination.properties cache file
        // (works even when ADT reflection fails — server/port/router stored on disk).
        if (this.systemUrl == null) {
            tryLoadDestinationFile(project);
        }
    }

    /**
     * Reads workspace .metadata/.plugins/org.eclipse.core.resources.semantic/.cache/{projectName}/.destination.properties
     * and rebuilds the HTTPS URL: scheme = https, host = server, port = 44300 + systemNumber.
     * Supports SAPRouter strings as well (router=/H/host).
     */
    private void tryLoadDestinationFile(IProject project) {
        try {
            String workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
            // Eclipse workspace root is project dir parent; .metadata sits next to projects.
            java.io.File metaCacheDir = new java.io.File(workspaceRoot,
                    ".metadata/.plugins/org.eclipse.core.resources.semantic/.cache/" + project.getName());
            java.io.File destFile = new java.io.File(metaCacheDir, ".destination.properties");
            if (!destFile.exists()) return;
            java.util.Properties props = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(destFile)) {
                props.load(fis);
            }
            String server = props.getProperty("server");
            String sysNum = props.getProperty("systemNumber", "00");
            String cli = props.getProperty("client");
            String usr = props.getProperty("user");
            if (server != null && !server.isEmpty()) {
                int port = 44300;
                try { port = 44300 + Integer.parseInt(sysNum); } catch (Exception ignored) {}
                this.systemUrl = "https://" + server + ":" + port;
            }
            if (cli != null) this.client = cli;
            if (usr != null) this.username = usr;
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    public boolean connectManual(String url, String client, String user, String pass) {
        this.systemUrl = url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.client = client != null ? client : "100";
        this.username = user;
        this.password = pass;
        this.systemId = this.systemUrl;
        try {
            HttpURLConnection conn = openRest("/sap/bc/adt/discovery", "GET");
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

    /**
     * GET text from an ADT path. Returns body string or throws.
     *
     * Uses plain Java HTTP. This works only when the SAP system is directly
     * reachable from the plug-in (VPN, accessible cloud, etc.). Systems
     * accessible only via SAProuter cannot be reached without ADT's internal
     * JCo stack — exceptions propagate so the caller can surface a useful
     * diagnostic to the user instead of silently returning empty results.
     */
    public String get(String path, String accept) throws Exception {
        HttpURLConnection conn = openRest(path, "GET");
        if (accept != null) conn.setRequestProperty("Accept", accept);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("HTTP " + code + " for " + path);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }

    /** POST with optional body. Returns response text. */
    public String post(String path, String contentType, String body) throws Exception {
        HttpURLConnection conn = openRest(path, "POST");
        if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
        if (csrfToken != null) conn.setRequestProperty("X-CSRF-Token", csrfToken);
        conn.setDoOutput(true);
        if (body != null) {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            }
        }
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + " for " + path + ": " + sb);
        return sb.toString();
    }

    private HttpURLConnection openRest(String path, String method) throws Exception {
        if (systemUrl == null) throw new RuntimeException("No system URL configured.");
        String full = systemUrl + path;
        if (client != null && !client.isEmpty()) {
            full += (full.contains("?") ? "&" : "?") + "sap-client=" + client;
        }

        java.net.URL targetUrl = new URL(full);

        // 1) Eclipse Network Connections proxy — read what ADT already uses.
        java.net.Proxy proxy = resolveEclipseProxy(targetUrl);

        HttpURLConnection conn = (HttpURLConnection)
                (proxy != null ? targetUrl.openConnection(proxy) : targetUrl.openConnection());
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        // 2) SSL trust — accept self-signed / internal CAs that ADT trusts but
        //    that aren't in the JVM default truststore. Toggleable via prefs.
        if (conn instanceof javax.net.ssl.HttpsURLConnection) {
            applySslTrust((javax.net.ssl.HttpsURLConnection) conn);
        }

        if (password != null && username != null) {
            String auth = username + ":" + password;
            conn.setRequestProperty("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        if (sessionCookie != null) conn.setRequestProperty("Cookie", sessionCookie);
        return conn;
    }

    /**
     * Asks Eclipse's IProxyService (org.eclipse.core.net) for a proxy that
     * applies to the given URL. Returns null when no proxy is configured or
     * when the proxy bundle is not available at runtime.
     *
     * Uses pure reflection so we don't require org.eclipse.core.net as a
     * Require-Bundle dependency — it's part of Eclipse base, present in
     * practice, but this stays defensive.
     */
    private java.net.Proxy resolveEclipseProxy(java.net.URL url) {
        try {
            org.osgi.framework.Bundle bundle = org.osgi.framework.FrameworkUtil
                    .getBundle(AdtConnectionService.class);
            if (bundle == null) return null;
            org.osgi.framework.BundleContext ctx = bundle.getBundleContext();
            if (ctx == null) return null;

            org.osgi.framework.ServiceReference<?> ref = ctx
                    .getServiceReference("org.eclipse.core.net.proxy.IProxyService");
            if (ref == null) return null;
            Object svc = ctx.getService(ref);
            if (svc == null) return null;

            Object[] entries = (Object[]) svc.getClass()
                    .getMethod("select", java.net.URI.class).invoke(svc, url.toURI());
            ctx.ungetService(ref);
            if (entries == null || entries.length == 0) return null;

            Object entry = entries[0];
            String host = (String) entry.getClass().getMethod("getHost").invoke(entry);
            Integer port = (Integer) entry.getClass().getMethod("getPort").invoke(entry);
            if (host == null) return null;
            return new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress(host, port != null ? port : 8080));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Configures the HTTPS connection to accept self-signed certificates when
     * the preference is enabled. Most customer dev/test ABAP systems use
     * non-public CAs that ADT already trusts via Eclipse's truststore — the
     * plug-in's plain HttpsURLConnection does not see that chain, so without
     * this we fail with PKIX handshake errors.
     */
    private void applySslTrust(javax.net.ssl.HttpsURLConnection https) {
        if (!com.sap.cleancore.analyzer.preferences.CleanCorePreferences.isAllowSelfSignedCerts()) {
            return; // user opted out — keep strict JVM defaults
        }
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) {}
                }
            }, new java.security.SecureRandom());
            https.setSSLSocketFactory(ctx.getSocketFactory());
            https.setHostnameVerifier((host, session) -> true);
        } catch (Exception ignored) {
            // fall back to JVM default trust — handshake may still fail with PKIX,
            // but CapabilityDetector now categorises that into a clear message.
        }
    }

    private void tryDeriveUrlFromProject() {
        if (adtProject == null) return;
        try {
            Object abapProject = adtProject.getAdapter(
                    Class.forName("com.sap.adt.project.IAbapProject"));
            if (abapProject == null) return;
            Object destData = invoke(abapProject, "getDestinationData");
            if (destData == null) destData = invoke(abapProject, "getDestination");
            if (destData == null) return;
            String host = (String) invoke(destData, "getHost");
            if (host == null) host = (String) invoke(destData, "getSystemHost");
            Object port = invoke(destData, "getPort");
            if (port == null) port = invoke(destData, "getSystemPort");
            String scheme = (String) invoke(destData, "getScheme");
            if (scheme == null) scheme = "https";
            if (host != null) {
                this.systemUrl = scheme + "://" + host + (port != null ? ":" + port : "");
            }
            String cli = (String) invoke(destData, "getClient");
            if (cli != null) this.client = cli;

            // Cookie extraction — ADT versions name this method inconsistently.
            // Probe a known set of candidates on both the destination and the
            // project, take the first non-null result.
            String[] cookieMethods = {
                    "getSessionCookie", "getCookie", "getSession",
                    "getAuthenticationCookie", "getAuthCookie"
            };
            for (String mname : cookieMethods) {
                Object c = invoke(destData, mname);
                if (c != null) { this.sessionCookie = c.toString(); break; }
            }
            if (this.sessionCookie == null) {
                for (String mname : cookieMethods) {
                    Object c = invoke(abapProject, mname);
                    if (c != null) { this.sessionCookie = c.toString(); break; }
                }
            }
        } catch (Throwable ignore) {
            // ADT classes not present (non-ADT Eclipse) — fall back to manual mode
        }
    }

    private Object invoke(Object o, String name) {
        try {
            Method m = o.getClass().getMethod(name);
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Workspace-wide list of ABAP projects.
     *  Detection chain (any match qualifies):
     *    1. ADT-specific natures (com.sap.adt.project.nature / com.sap.adt.core.adtproject)
     *    2. Any nature whose ID starts with "com.sap.adt" (newer ADT versions)
     *    3. Project name follows the ADT default pattern SYSTEM_CLIENT_USER
     *       e.g. "NS4_100_gilgar_en"
     */
    public static List<IProject> listAbapProjects() {
        List<IProject> out = new ArrayList<>();
        try {
            for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                if (!p.isOpen()) continue;
                boolean abap = false;
                try { abap = p.hasNature("com.sap.adt.project.nature"); } catch (Exception e) {}
                if (!abap) try { abap = p.hasNature("com.sap.adt.core.adtproject"); } catch (Exception e) {}
                if (!abap) try {
                    for (String n : p.getDescription().getNatureIds()) {
                        if (n != null && n.startsWith("com.sap.adt")) { abap = true; break; }
                    }
                } catch (Exception e) {}
                if (!abap && p.getName().matches("^[A-Z0-9]+_[0-9]{3}_.+")) {
                    abap = true;
                }
                if (abap) out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
