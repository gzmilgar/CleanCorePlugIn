package com.sap.cleancore.analyzer.utils;

import com.sap.cleancore.analyzer.model.ZObject;
import com.sap.cleancore.analyzer.model.ZObjectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free (java.* only) reader that turns local ABAP source files into
 * {@link ZObject}s with their source pre-loaded — the basis of the offline
 * (no-ADT) workflows. Shared by {@code AnalyzeDiskFilesHandler} (file picker)
 * and {@code AnalyzeFolderHandler} (recursive folder scan).
 *
 * <p>Each file becomes a synthetic ZObject: name = file stem (upper-case),
 * type inferred from extension, devClass = "DISK" (a {@code metadata.json}
 * sidecar can later override devClass/type/usage — see CodeMetadataIngestor).
 */
public class OfflineSourceReader {

    /** Recognised ABAP source extensions (lower-case, no dot). */
    public static final String[] ABAP_EXTENSIONS = {
            "abap", "clas", "aclass", "prog", "asprog", "intf", "asintf",
            "fugr", "asfunc", "asinc", "reps", "tabl", "asddic", "ddls",
            "asddls", "enho", "txt"
    };

    /** SWT FileDialog filter string for the recognised extensions. */
    public static final String FILE_DIALOG_FILTER =
            "*.abap;*.clas;*.aclass;*.prog;*.asprog;*.intf;*.asintf;"
          + "*.fugr;*.asfunc;*.asinc;*.reps;*.tabl;*.ddls;*.enho;*.txt";

    /** Result of a folder scan: the objects plus how many files were skipped. */
    public static class ScanResult {
        public final List<ZObject> objects = new ArrayList<>();
        public int skipped;
        public final List<String> skippedNames = new ArrayList<>();
    }

    /** Build a ZObject from a single file (source pre-loaded). null on failure/empty. */
    public ZObject readFile(File f) {
        if (f == null || !f.isFile()) return null;
        String content;
        try {
            content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
        if (content.isEmpty()) return null;
        ZObject z = new ZObject();
        z.setName(stemOf(f.getName()).toUpperCase(Locale.ROOT));
        z.setType(typeFor(f.getName()));
        z.setDevClass("DISK");
        z.setSource(content);
        return z;
    }

    /** Read an explicit list of files into ZObjects. */
    public ScanResult readFiles(List<File> files) {
        ScanResult r = new ScanResult();
        if (files == null) return r;
        for (File f : files) {
            ZObject z = readFile(f);
            if (z != null) r.objects.add(z);
            else { r.skipped++; if (f != null) addSkipped(r, f.getName()); }
        }
        return r;
    }

    /**
     * Recursively scan a folder for ABAP source files. {@code metadata.json}
     * itself is excluded (it is consumed by CodeMetadataIngestor, not analysed).
     */
    public ScanResult scanFolder(File root) {
        ScanResult r = new ScanResult();
        if (root == null || !root.isDirectory()) return r;
        try (java.util.stream.Stream<Path> walk = Files.walk(root.toPath())) {
            List<Path> paths = new ArrayList<>();
            walk.filter(Files::isRegularFile).forEach(paths::add);
            for (Path p : paths) {
                File f = p.toFile();
                String name = f.getName();
                if ("metadata.json".equalsIgnoreCase(name)) continue;
                if (!isAbapSource(name)) { continue; }
                ZObject z = readFile(f);
                if (z != null) r.objects.add(z);
                else { r.skipped++; addSkipped(r, name); }
            }
        } catch (IOException e) {
            // best-effort: return whatever was collected
        }
        return r;
    }

    private void addSkipped(ScanResult r, String name) {
        if (r.skippedNames.size() < 30) r.skippedNames.add(name);
    }

    public static boolean isAbapSource(String fileName) {
        return typeFor(fileName) != ZObjectType.UNKNOWN
                || ext(fileName).equals("txt");
    }

    public static String stemOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String ext(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    public static ZObjectType typeFor(String fileName) {
        switch (ext(fileName)) {
            case "aclass":
            case "clas":     return ZObjectType.Z_CLASS;
            case "asinc":
            case "reps":     return ZObjectType.Z_INCLUDE;
            case "prog":
            case "asprog":
            case "abap":     return ZObjectType.Z_REPORT;
            case "fugr":
            case "asfunc":   return ZObjectType.Z_FUNCTION_MODULE;
            case "intf":
            case "asintf":   return ZObjectType.Z_INTERFACE;
            case "tabl":
            case "asddic":   return ZObjectType.Z_DDIC_TABLE;
            case "ddls":
            case "asddls":   return ZObjectType.Z_CDS_VIEW;
            case "enho":     return ZObjectType.Z_ENHANCEMENT;
            default:         return ZObjectType.UNKNOWN;
        }
    }
}
