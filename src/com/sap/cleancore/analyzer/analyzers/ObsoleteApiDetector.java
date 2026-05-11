package com.sap.cleancore.analyzer.analyzers;

import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.model.MappingEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans source for CALL FUNCTION '...' / class method calls and matches them
 * against the mapping repository. Each match produces a Finding tagged
 * OBSOLETE_API with the proposed modern replacement attached as the message.
 */
public class ObsoleteApiDetector {

    private static final Pattern CALL_FUNCTION =
            Pattern.compile("CALL\\s+FUNCTION\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_METHOD =
            Pattern.compile("\\b(CL_[A-Z0-9_]+|/[A-Z0-9_]+/CL_[A-Z0-9_]+)\\s*(=>|->)", Pattern.CASE_INSENSITIVE);

    public Result analyze(String source) {
        Result r = new Result();
        if (source == null || source.isEmpty()) return r;

        MappingRepository repo = MappingRepository.getInstance();
        Set<String> seenFunctions = new HashSet<>();
        Set<String> seenClasses = new HashSet<>();

        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]);
            if (line.isEmpty()) continue;

            Matcher mf = CALL_FUNCTION.matcher(line);
            while (mf.find()) {
                String fmName = mf.group(1).toUpperCase();
                if (!seenFunctions.add(fmName)) continue;
                MappingEntry mapping = repo.find(MappingEntry.LegacyType.FM, fmName);
                if (mapping == null) mapping = repo.find(MappingEntry.LegacyType.BAPI, fmName);
                if (mapping == null) continue;
                r.matchedMappings.add(mapping);
                if (mapping.getReleaseState() == MappingEntry.ReleaseState.RELEASED
                        && mapping.getModernType() == MappingEntry.ModernType.KEEP_AS_IS) {
                    continue; // safe to keep
                }
                Finding.Severity sev = severityFor(mapping);
                Finding f = new Finding("CC_OBSOLETE_API", buildMsg(mapping), sev, Finding.Source.OBSOLETE_API);
                f.setCategory("Released API");
                f.setLine(i + 1);
                r.findings.add(f);
            }

            Matcher mc = CLASS_METHOD.matcher(line);
            while (mc.find()) {
                String cls = mc.group(1).toUpperCase();
                if (!seenClasses.add(cls)) continue;
                MappingEntry mapping = repo.find(MappingEntry.LegacyType.CLASS, cls);
                if (mapping == null) continue;
                if (mapping.getModernType() == MappingEntry.ModernType.KEEP_AS_IS
                        && mapping.getReleaseState() == MappingEntry.ReleaseState.RELEASED) continue;
                r.matchedMappings.add(mapping);
                Finding f = new Finding("CC_OBSOLETE_CLASS", buildMsg(mapping),
                        severityFor(mapping), Finding.Source.OBSOLETE_API);
                f.setCategory("Released API");
                f.setLine(i + 1);
                r.findings.add(f);
            }
        }
        return r;
    }

    private Finding.Severity severityFor(MappingEntry m) {
        if (m.getReleaseState() == MappingEntry.ReleaseState.REMOVED) return Finding.Severity.ERROR;
        if (m.getReleaseState() == MappingEntry.ReleaseState.DEPRECATED) return Finding.Severity.ERROR;
        if (m.getReleaseState() == MappingEntry.ReleaseState.NOT_RELEASED) return Finding.Severity.WARNING;
        return Finding.Severity.INFO;
    }

    private String buildMsg(MappingEntry m) {
        return "Uses " + m.getLegacyType() + " '" + m.getLegacyName() + "' (" + m.getReleaseState()
                + "). Replace with " + m.getModernType() + " '" + m.getModernName() + "'.";
    }

    private String stripComment(String line) {
        if (line.trim().startsWith("*")) return "";
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') inStr = !inStr;
            else if (c == '"' && !inStr) return line.substring(0, i);
        }
        return line;
    }

    public static class Result {
        public final List<Finding> findings = new ArrayList<>();
        public final List<MappingEntry> matchedMappings = new ArrayList<>();
    }
}
