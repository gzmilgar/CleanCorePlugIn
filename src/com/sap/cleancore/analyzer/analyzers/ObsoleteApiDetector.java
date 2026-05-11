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
 * Scans source for:
 *   - CALL FUNCTION '...' / class method calls    → checks against MappingRepository
 *   - SELECT/JOIN/UPDATE/INSERT/MODIFY/DELETE table accesses
 *     → looks up table in MappingRepository (TABLE LegacyType) to surface the
 *       corresponding S/4 CDS view (e.g. MARA → I_Product, BSEG → I_OperationalAcctgDocItem).
 *
 * Each match produces a Finding tagged OBSOLETE_API with the proposed modern
 * replacement attached as the suggestion (so the "Current File" results view
 * can display "MARA → I_Product" in the Suggestion column).
 */
public class ObsoleteApiDetector {

    private static final Pattern CALL_FUNCTION =
            Pattern.compile("CALL\\s+FUNCTION\\s+'([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_METHOD =
            Pattern.compile("\\b(CL_[A-Z0-9_]+|/[A-Z0-9_]+/CL_[A-Z0-9_]+)\\s*(=>|->)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_ACCESS =
            Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE|DELETE\\s+FROM|MODIFY|INSERT\\s+INTO)\\s+([A-Z][A-Z0-9_]{1,29})\\b",
                    Pattern.CASE_INSENSITIVE);

    public Result analyze(String source) {
        Result r = new Result();
        if (source == null || source.isEmpty()) return r;

        MappingRepository repo = MappingRepository.getInstance();
        Set<String> seenFunctions = new HashSet<>();
        Set<String> seenClasses = new HashSet<>();
        Set<String> seenTables = new HashSet<>();

        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i];
            String line = stripComment(rawLine);
            if (line.isEmpty()) continue;
            String trimmed = line.trim();
            String snippet = trimmed.length() > 200 ? trimmed.substring(0, 197) + "..." : trimmed;

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
                    continue;
                }
                Finding f = makeFinding("CC_OBSOLETE_API", "Obsolete FM/BAPI",
                        "Released API", mapping, snippet, i + 1);
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
                Finding f = makeFinding("CC_OBSOLETE_CLASS", "Obsolete class",
                        "Released API", mapping, snippet, i + 1);
                r.findings.add(f);
            }

            // ----- Table → CDS lookup (CC001 augmentation) -----
            Matcher mt = TABLE_ACCESS.matcher(line);
            while (mt.find()) {
                String tbl = mt.group(1).toUpperCase();
                String key = i + ":" + tbl;
                if (!seenTables.add(key)) continue;
                // Skip obvious non-table tokens
                if (isStopword(tbl)) continue;
                MappingEntry mapping = repo.find(MappingEntry.LegacyType.TABLE, tbl);
                if (mapping == null) continue;
                r.matchedMappings.add(mapping);
                Finding f = new Finding("CC001",
                        "Direct access to SAP standard table " + tbl
                                + ". Released successor: " + mapping.getModernName() + ".",
                        severityFor(mapping), Finding.Source.OBSOLETE_API);
                f.setCategory("Database Access");
                f.setLine(i + 1);
                f.setRuleName("Direct SAP Table SELECT");
                f.setSuggestion(tbl + " → " + mapping.getModernName());
                f.setMatchedCode(snippet);
                f.setCleanCoreApi(mapping.getModernName());
                r.findings.add(f);
            }
        }
        return r;
    }

    private Finding makeFinding(String checkId, String ruleName, String category,
                                MappingEntry mapping, String snippet, int line) {
        Finding f = new Finding(checkId, buildMsg(mapping), severityFor(mapping), Finding.Source.OBSOLETE_API);
        f.setCategory(category);
        f.setLine(line);
        f.setRuleName(ruleName);
        f.setMatchedCode(snippet);
        f.setSuggestion(mapping.getLegacyName() + " → " + mapping.getModernName());
        f.setCleanCoreApi(mapping.getModernName());
        return f;
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

    private boolean isStopword(String token) {
        // Filter out common ABAP keywords or local variable names that match
        // the TABLE_ACCESS pattern but aren't real DDIC tables. The mapping
        // repository will return null for them anyway — this just trims noise.
        switch (token) {
            case "TABLE": case "LT": case "LS": case "GT": case "GS":
            case "MEMORY": case "PARAMETER": case "RESULT": case "OUTPUT":
                return true;
            default:
                return token.startsWith("LT_") || token.startsWith("LS_")
                    || token.startsWith("GT_") || token.startsWith("GS_")
                    || token.startsWith("IT_") || token.startsWith("IS_");
        }
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
