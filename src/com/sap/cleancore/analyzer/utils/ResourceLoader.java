package com.sap.cleancore.analyzer.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads bundled resources (resources/mapping/*.json) via the plugin's
 * bundle classloader. No direct OSGi imports — Eclipse 2026 PDE Enterprise
 * does not transitively expose org.osgi.framework / FileLocator to the
 * workspace classpath, so we stay on plain Java APIs.
 *
 * At runtime inside Eclipse, this class's ClassLoader IS the bundle's
 * classloader; getResourceAsStream resolves paths relative to the bundle root,
 * so "resources/mapping/object_rules.json" still works exactly as before.
 */
public class ResourceLoader {

    /**
     * First parameter kept for backwards compatibility with existing call sites
     * (MappingRepository, EffortRules). Its value is ignored — we always go via
     * the classloader.
     */
    public static String readBundleText(Object ignored, String pathInBundle) throws Exception {
        InputStream is = ResourceLoader.class.getClassLoader().getResourceAsStream(pathInBundle);
        if (is == null) throw new IOException("Resource not found: " + pathInBundle);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }
}
