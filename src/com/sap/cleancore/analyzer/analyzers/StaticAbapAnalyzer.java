package com.sap.cleancore.analyzer.analyzers;

import com.sap.cleancore.analyzer.model.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R/3-compatible bundled rules. These mirror the spirit of S4HANA_READINESS ATC
 * checks but run locally on source text, so customers without ATC can still get
 * meaningful findings. Each rule emits a Finding tagged Source.STATIC.
 */
public class StaticAbapAnalyzer {

    private static class Rule {
        final String id;
        final Pattern pattern;
        final Finding.Severity severity;
        final String category;
        final String message;
        Rule(String id, String regex, Finding.Severity severity, String category, String message) {
            this.id = id;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.severity = severity;
            this.category = category;
            this.message = message;
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();
    static {
        RULES.add(new Rule("CC_SELECT_STAR",
                "\\bSELECT\\s+\\*",
                Finding.Severity.WARNING, "SQL",
                "SELECT * is forbidden in clean-core: name fields explicitly."));
        RULES.add(new Rule("CC_NATIVE_SQL",
                "\\bEXEC\\s+SQL\\b",
                Finding.Severity.ERROR, "SQL",
                "Native EXEC SQL is not allowed in S/4HANA / Cloud."));
        RULES.add(new Rule("CC_DB_DIRECT_TABLE",
                "\\b(BSEG|BKPF|VBAK|VBAP|MARA|MARC|KNA1|LFA1)\\b",
                Finding.Severity.WARNING, "SQL",
                "Direct access to a simplified table. Use the released CDS view in S/4HANA."));
        RULES.add(new Rule("CC_INTO_TABLE_NO_FIELDS",
                "\\bINTO\\s+TABLE\\b",
                Finding.Severity.INFO, "SQL",
                "Consider INTO CORRESPONDING FIELDS OF TABLE for forward-compatibility."));
        RULES.add(new Rule("CC_NO_INTO_CORRESPONDING",
                "\\bMOVE-CORRESPONDING\\b",
                Finding.Severity.INFO, "Style",
                "MOVE-CORRESPONDING is risky after structure changes; prefer explicit mapping."));
        RULES.add(new Rule("CC_PARAMETER_PNNNN",
                "\\bPARAMETER\\s+ID\\b",
                Finding.Severity.WARNING, "SPA/GPA",
                "SPA/GPA parameters are not supported in Fiori; redesign."));
        RULES.add(new Rule("CC_CALL_SCREEN",
                "\\bCALL\\s+SCREEN\\b",
                Finding.Severity.ERROR, "Dynpro",
                "Classic Dynpro is not allowed in Cloud/Fiori. Replace with Fiori app or RAP-driven UI."));
        RULES.add(new Rule("CC_GUI_STATUS",
                "\\bSET\\s+PF-STATUS\\b",
                Finding.Severity.ERROR, "Dynpro",
                "GUI status is Dynpro-only; redesign for Fiori."));
        RULES.add(new Rule("CC_DYNAMIC_GENERATE",
                "\\bGENERATE\\s+SUBROUTINE\\b",
                Finding.Severity.ERROR, "Dynamic ABAP",
                "Dynamic code generation is not allowed."));
        RULES.add(new Rule("CC_FIELD_SYMBOLS_GENERIC",
                "\\bFIELD-SYMBOLS\\b.*\\bTYPE\\s+ANY\\b",
                Finding.Severity.WARNING, "Dynamic ABAP",
                "Generic FIELD-SYMBOLS TYPE ANY indicates dynamic code; review for cloud compatibility."));
        RULES.add(new Rule("CC_AUTH_CHECK_HARDCODED",
                "\\bAUTHORITY-CHECK\\s+OBJECT\\s+'Z",
                Finding.Severity.WARNING, "Security",
                "Z-authorization object - validate it still applies in S/4."));
        RULES.add(new Rule("CC_WRITE_LIST",
                "(^|\\s)WRITE(?!:?\\s+TO)\\b",
                Finding.Severity.WARNING, "List Processing",
                "Classic list output (WRITE statement) - replace with ALV or Fiori."));
        RULES.add(new Rule("CC_OBSOLETE_CONDENSE",
                "\\bCONDENSE\\b.*NO-GAPS",
                Finding.Severity.INFO, "Style",
                "Consider modern string templates instead of CONDENSE NO-GAPS."));
        RULES.add(new Rule("CC_OBSOLETE_OCCURS",
                "\\bOCCURS\\s+\\d+",
                Finding.Severity.WARNING, "Obsolete syntax",
                "OCCURS clause is obsolete; use TYPE STANDARD TABLE OF."));
        RULES.add(new Rule("CC_OBSOLETE_TABLES",
                "\\bTABLES\\s*:",
                Finding.Severity.INFO, "Obsolete syntax",
                "TABLES statement is obsolete in modern ABAP."));
    }

    public List<Finding> analyze(String source) {
        List<Finding> out = new ArrayList<>();
        if (source == null || source.isEmpty()) return out;
        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = stripComment(line).trim();
            if (stripped.isEmpty()) continue;
            for (Rule r : RULES) {
                Matcher m = r.pattern.matcher(stripped);
                if (m.find()) {
                    Finding f = new Finding(r.id, r.message, r.severity, Finding.Source.STATIC);
                    f.setCategory(r.category);
                    f.setLine(i + 1);
                    out.add(f);
                }
            }
        }
        return out;
    }

    private String stripComment(String line) {
        if (line.trim().startsWith("*")) return "";
        int idx = line.indexOf('"');
        if (idx < 0) return line;
        // Naive: ignore quotes inside string literals
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') inStr = !inStr;
            else if (c == '"' && !inStr) return line.substring(0, i);
        }
        return line;
    }
}
