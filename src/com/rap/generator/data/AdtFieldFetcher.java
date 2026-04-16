package com.rap.generator.data;

import com.rap.generator.model.FieldDefinition;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Fetches CDS view and table fields from Eclipse ADT's local cache.
 * ADT stores opened sources at:
 * {workspace}/.metadata/.plugins/org.eclipse.core.resources.semantic/.cache/{project}/.adt/ddic/ddlsources/{name}/{name}.asddls
 */
public class AdtFieldFetcher {

    private static final String ABAP_NATURE = "com.sap.adt.abapnature";
    private static AdtFieldFetcher instance;

    private AdtFieldFetcher() {}

    public static synchronized AdtFieldFetcher getInstance() {
        if (instance == null) {
            instance = new AdtFieldFetcher();
        }
        return instance;
    }

    public List<IProject> findAbapProjects() {
        List<IProject> result = new ArrayList<>();
        try {
            for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                if (!project.isOpen()) continue;
                try {
                    if (project.hasNature(ABAP_NATURE)) {
                        result.add(project);
                    }
                } catch (Exception e) {
                    if (project.getName().matches("^[A-Z0-9]+_\\d{3}_.*")) {
                        result.add(project);
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return result;
    }

    /**
     * Fetch CDS view fields by reading from ADT's local semantic filesystem cache.
     */
    public List<FieldDefinition> fetchCdsFields(IProject project, String cdsViewName) throws Exception {
        // Find all possible cache directories
        List<Path> cacheDirs = findSemanticCacheDirs(project.getName());

        String nameLower = cdsViewName.toLowerCase();
        StringBuilder tried = new StringBuilder();

        for (Path cacheDir : cacheDirs) {
            // ADT cache structure: .adt/ddic/ddlsources/{name}/{name}.asddls
            Path asddls = cacheDir.resolve(".adt/ddic/ddlsources/" + nameLower + "/" + nameLower + ".asddls");
            tried.append("  ").append(asddls).append(Files.exists(asddls) ? " [EXISTS]" : " [NOT FOUND]").append("\n");

            if (Files.exists(asddls)) {
                String source = Files.readString(asddls, StandardCharsets.UTF_8);
                List<FieldDefinition> fields = parseCdsSource(source);
                if (!fields.isEmpty()) return fields;
            }

            // Also try .apddls
            Path apddls = cacheDir.resolve(".adt/ddic/ddlsources/" + nameLower + "/" + nameLower + ".apddls");
            if (Files.exists(apddls)) {
                String source = Files.readString(apddls, StandardCharsets.UTF_8);
                List<FieldDefinition> fields = parseCdsSource(source);
                if (!fields.isEmpty()) return fields;
            }

            // Try uppercase
            String nameUpper = cdsViewName.toUpperCase();
            Path asddlsUpper = cacheDir.resolve(".adt/ddic/ddlsources/" + nameUpper + "/" + nameUpper + ".asddls");
            if (Files.exists(asddlsUpper)) {
                String source = Files.readString(asddlsUpper, StandardCharsets.UTF_8);
                List<FieldDefinition> fields = parseCdsSource(source);
                if (!fields.isEmpty()) return fields;
            }

            // Search recursively for any matching file
            try (Stream<Path> walk = Files.walk(cacheDir, 6)) {
                Optional<Path> found = walk
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(nameLower))
                    .filter(p -> p.toString().endsWith(".asddls") || p.toString().endsWith(".apddls"))
                    .findFirst();
                if (found.isPresent()) {
                    tried.append("  FOUND: ").append(found.get()).append("\n");
                    String source = Files.readString(found.get(), StandardCharsets.UTF_8);
                    List<FieldDefinition> fields = parseCdsSource(source);
                    if (!fields.isEmpty()) return fields;
                }
            } catch (Exception e) { /* ignore walk errors */ }
        }

        throw new Exception("CDS '" + cdsViewName + "' not found in local cache.\n\n"
            + "Please open '" + cdsViewName + "' once in the ADT editor\n"
            + "so it gets cached locally, then try again.\n\n"
            + "Cache dirs searched: " + cacheDirs.size() + "\n"
            + "Paths tried:\n" + tried.toString());
    }

    /**
     * Fetch table fields from ADT cache.
     */
    public List<FieldDefinition> fetchTableFields(IProject project, String tableName) throws Exception {
        List<Path> cacheDirs = findSemanticCacheDirs(project.getName());
        String nameLower = tableName.toLowerCase();

        for (Path cacheDir : cacheDirs) {
            // Table structures: .adt/ddic/tables/{name}/{name}.*
            try (Stream<Path> walk = Files.walk(cacheDir, 6)) {
                Optional<Path> found = walk
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(nameLower))
                    .filter(p -> {
                        String fn = p.toString().toLowerCase();
                        return fn.endsWith(".xml") || fn.endsWith(".tabl") || fn.endsWith(".astabl");
                    })
                    .findFirst();
                if (found.isPresent()) {
                    String content = Files.readString(found.get(), StandardCharsets.UTF_8);
                    List<FieldDefinition> fields = parseTableXml(content);
                    if (!fields.isEmpty()) return fields;
                }
            } catch (Exception e) { /* ignore */ }
        }

        throw new Exception("Table '" + tableName + "' not found in local cache.\n\n"
            + "Please open '" + tableName + "' once in ADT,\n"
            + "then try again.\n\n"
            + "Or use 'Import from Clipboard'.");
    }

    /**
     * Find all semantic filesystem cache directories for the given project.
     * Scans common workspace locations on disk.
     */
    private List<Path> findSemanticCacheDirs(String projectName) {
        List<Path> result = new ArrayList<>();
        String home = System.getProperty("user.home");

        // Known workspace locations to scan
        String[] workspaces = {
            home + "/eclipse-workspace",
            home + "/workspace",
            home + "/Documents/workspace",
            home + "/ABAP_workspace",
        };

        // Also try osgi.instance.area
        try {
            String instanceArea = System.getProperty("osgi.instance.area");
            if (instanceArea != null) {
                String wsPath = instanceArea.replace("file:", "").replace("file://", "");
                if (wsPath.endsWith("/")) wsPath = wsPath.substring(0, wsPath.length() - 1);
                Path cacheDir = Path.of(wsPath, ".metadata", ".plugins",
                    "org.eclipse.core.resources.semantic", ".cache", projectName);
                if (Files.exists(cacheDir)) {
                    result.add(cacheDir);
                }
            }
        } catch (Exception e) { /* ignore */ }

        for (String ws : workspaces) {
            Path cacheDir = Path.of(ws, ".metadata", ".plugins",
                "org.eclipse.core.resources.semantic", ".cache", projectName);
            if (Files.exists(cacheDir) && !result.contains(cacheDir)) {
                result.add(cacheDir);
            }
        }

        // Also scan for any workspace that has this project cached
        try {
            Path homeDir = Path.of(home);
            try (Stream<Path> dirs = Files.list(homeDir)) {
                dirs.filter(p -> {
                        Path cache = p.resolve(".metadata/.plugins/org.eclipse.core.resources.semantic/.cache/" + projectName);
                        return Files.exists(cache);
                    })
                    .forEach(p -> {
                        Path cache = p.resolve(".metadata/.plugins/org.eclipse.core.resources.semantic/.cache/" + projectName);
                        if (!result.contains(cache)) result.add(cache);
                    });
            }
        } catch (Exception e) { /* ignore */ }

        return result;
    }

    // ===== CDS SOURCE PARSER =====

    List<FieldDefinition> parseCdsSource(String source) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (source == null) return fields;

        // Find the actual select list - it comes after "define view" ... "{"
        // Skip annotation blocks that also use { }
        boolean foundDefine = false;
        boolean inSelectList = false;
        int braceDepth = 0;

        for (String line : source.split("\n")) {
            String trimmed = line.trim();

            // Detect "define view" or "define root view entity"
            if (!foundDefine && (trimmed.startsWith("define ") || trimmed.startsWith("define\t"))) {
                foundDefine = true;
            }

            // Before "define view", skip everything (annotations with their own { })
            if (!foundDefine) continue;

            // After "define view", the first { starts the select list
            if (foundDefine && !inSelectList) {
                if (trimmed.equals("{")) {
                    inSelectList = true;
                    continue;
                }
                continue; // Skip "as select from ..." lines
            }

            // Track brace depth for nested expressions
            if (trimmed.equals("}")) {
                if (braceDepth > 0) {
                    braceDepth--;
                    continue;
                }
                break; // End of select list
            }

            if (!inSelectList) continue;

            if (trimmed.startsWith("//") || trimmed.startsWith("@") || trimmed.isEmpty()
                || trimmed.startsWith("association") || trimmed.startsWith("composition")
                || trimmed.startsWith("where ") || trimmed.startsWith("on ")
                || trimmed.startsWith("left ") || trimmed.startsWith("inner ")
                || trimmed.startsWith("--") || trimmed.startsWith("*")) continue;

            String fieldLine = trimmed.replaceAll(",$", "").trim();
            boolean isKey = fieldLine.startsWith("key ");
            if (isKey) fieldLine = fieldLine.substring(4).trim();

            // Handle cast() expressions
            if (fieldLine.startsWith("cast(") || fieldLine.startsWith("CAST(")) {
                // cast(SalesDocument as vdm_sales_order_p...) -> extract alias after "as" outside cast
                int lastAs = fieldLine.lastIndexOf(" as ");
                if (lastAs > 0) {
                    fieldLine = fieldLine.substring(lastAs + 4).trim();
                } else {
                    continue;
                }
            }

            String fieldName, alias;
            if (fieldLine.contains(" as ")) {
                String[] parts = fieldLine.split("\\s+as\\s+");
                fieldName = parts[0].trim();
                alias = parts.length > 1 ? parts[1].trim().split("\\s+")[0] : fieldName;
            } else {
                fieldName = fieldLine.split("\\s+")[0].trim();
                alias = fieldName;
            }

            // Clean up
            alias = alias.replaceAll("[,;]$", "").trim();
            fieldName = fieldName.replaceAll("[,;]$", "").trim();

            if (alias.startsWith("_") || alias.isEmpty()) continue;
            if (alias.startsWith("@") || alias.contains("(") || alias.contains(")")) continue;
            if (isReservedWord(alias)) continue;

            FieldDefinition field = new FieldDefinition();
            field.setName(alias);
            field.setAbapName(fieldName.toLowerCase().replace(".", "_"));
            field.setLabel(camelToLabel(alias));
            field.setKey(isKey);
            field.setAbapType("abap.char(40)");
            fields.add(field);
        }
        return fields;
    }

    // ===== TABLE XML PARSER =====

    List<FieldDefinition> parseTableXml(String xml) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (xml == null) return fields;

        String[] segments = xml.split("<DD03P>");
        for (int i = 1; i < segments.length; i++) {
            String seg = segments[i];
            String name = xmlVal(seg, "FIELDNAME");
            String type = xmlVal(seg, "DATATYPE");
            String len = xmlVal(seg, "LENG");
            String dec = xmlVal(seg, "DECIMALS");
            String key = xmlVal(seg, "KEYFLAG");
            String text = xmlVal(seg, "DDTEXT");

            if (name == null || name.startsWith(".") || "MANDT".equals(name)) continue;

            FieldDefinition field = new FieldDefinition();
            field.setName(toCamelCase(name));
            field.setAbapName(name.toLowerCase());
            field.setAbapType(mapType(type, len, dec));
            field.setLabel(text != null ? text : camelToLabel(toCamelCase(name)));
            field.setKey("X".equals(key));
            fields.add(field);
        }
        return fields;
    }

    // ===== HELPERS =====

    private boolean isReservedWord(String word) {
        String lower = word.toLowerCase();
        return Set.of("select", "from", "where", "group", "order", "having",
            "union", "join", "on", "and", "or", "not", "as", "case", "when",
            "then", "else", "end", "true", "false", "null").contains(lower);
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
            case "INT1": return "abap.int1";
            case "INT2": return "abap.int2";
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
}
