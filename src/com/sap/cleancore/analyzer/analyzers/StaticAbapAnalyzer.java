package com.sap.cleancore.analyzer.analyzers;

import com.sap.cleancore.analyzer.model.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R/3-compatible bundled rules. Mirrors S4HANA_READINESS ATC checks but runs
 * locally on raw ABAP source — works on systems that don't ship the formal
 * Clean Core ATC variants.
 *
 * Rule IDs follow the CC### convention so they line up with the standard
 * Clean Core ruleset and the reference plug-in (gzmilgar/PlugIn) the analyst
 * was already familiar with:
 *
 *   CC001 — Direct SAP table SELECT          (Database Access)
 *   CC020 — Classic ALV (REUSE_ALV*)         (User Interface)
 *   CC022 — Classic Dynpro (CALL SCREEN/PF)  (User Interface)
 *   CC040 — Dynamic ASSIGN / FS TYPE ANY     (Architecture)
 *   CC050 — Native SQL (EXEC SQL)            (Database Access)
 *   CC060 — SPA/GPA parameters               (Architecture)
 *   CC070 — Obsolete OCCURS / TABLES         (Style)
 *   CC080 — Classic WRITE list               (User Interface)
 *   CC090 — Z* authorization object          (Security)
 *   CC100 — Style / housekeeping             (Style)
 */
public class StaticAbapAnalyzer {

    private static class Rule {
        final String id;
        final String name;
        final Pattern pattern;
        final Finding.Severity severity;
        final String category;
        final String suggestion;
        final String message;

        Rule(String id, String name, String regex, Finding.Severity severity,
             String category, String suggestion, String message) {
            this.id = id;
            this.name = name;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.severity = severity;
            this.category = category;
            this.suggestion = suggestion;
            this.message = message;
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();
    static {
        RULES.add(new Rule("CC001", "SELECT * forbidden",
                "\\bSELECT\\s+\\*",
                Finding.Severity.WARNING, "Database Access",
                "Name fields explicitly; prefer CDS view projection",
                "SELECT * is forbidden in clean-core. Name fields explicitly or use a released CDS view."));
        RULES.add(new Rule("CC001", "Direct SAP Table SELECT",
                "\\b(BSEG|BKPF|VBAK|VBAP|MARA|MARC|KNA1|LFA1|EKKO|EKPO|MKPF|MSEG|LIKP|LIPS|VBRK|VBRP|T134T|T001|T001W)\\b",
                Finding.Severity.WARNING, "Database Access",
                "Use released CDS Views (I_* / C_*) or released APIs",
                "Direct access to a simplified table. Use the released CDS view in S/4HANA."));

        RULES.add(new Rule("CC020", "Classic ALV (REUSE_ALV)",
                "\\bREUSE_ALV_(GRID_DISPLAY|FIELDCATALOG_MERGE|LIST_DISPLAY|HIERSEQ_LIST_DISPLAY)\\b",
                Finding.Severity.WARNING, "User Interface",
                "Adopt CL_SALV_TABLE or Fiori Elements",
                "Classic REUSE_ALV is deprecated. Use cl_salv_table / cl_salv_hierseq_table."));
        RULES.add(new Rule("CC020", "ALV_GRID_DISPLAY",
                "\\bALV_GRID_DISPLAY\\b",
                Finding.Severity.WARNING, "User Interface",
                "Replace with CL_SALV_TABLE",
                "ALV_GRID_DISPLAY is the classic ALV. Use cl_salv_table=>factory."));

        RULES.add(new Rule("CC022", "Classic Dynpro (CALL SCREEN)",
                "\\bCALL\\s+SCREEN\\b",
                Finding.Severity.ERROR, "User Interface",
                "Replace with RAP + Fiori Elements application",
                "Classic Dynpro is not allowed in Cloud/Fiori. Redesign as Fiori app or RAP-driven UI."));
        RULES.add(new Rule("CC022", "GUI Status",
                "\\bSET\\s+PF-STATUS\\b",
                Finding.Severity.ERROR, "User Interface",
                "Replace with RAP + Fiori Elements application",
                "GUI status is Dynpro-only; redesign for Fiori."));

        RULES.add(new Rule("CC040", "Dynamic ASSIGN",
                "\\bASSIGN\\s+\\(",
                Finding.Severity.INFO, "Architecture",
                "Review for ABAP Cloud compatibility",
                "Dynamic ASSIGN with parenthesised target is restricted in ABAP Cloud."));
        RULES.add(new Rule("CC040", "Generic FIELD-SYMBOLS",
                "\\bFIELD-SYMBOLS\\b[^.]*\\bTYPE\\s+ANY\\b",
                Finding.Severity.WARNING, "Architecture",
                "Replace generic type ANY with a concrete data type",
                "Generic FIELD-SYMBOLS TYPE ANY indicates dynamic code; review for cloud compatibility."));
        RULES.add(new Rule("CC040", "Dynamic GENERATE",
                "\\bGENERATE\\s+SUBROUTINE\\b",
                Finding.Severity.ERROR, "Dynamic ABAP",
                "Forbidden — refactor to static dispatch",
                "Dynamic code generation is not allowed."));

        RULES.add(new Rule("CC050", "Native SQL",
                "\\bEXEC\\s+SQL\\b",
                Finding.Severity.ERROR, "Database Access",
                "Forbidden in S/4HANA Cloud — use ABAP SQL",
                "Native EXEC SQL is not allowed in S/4HANA / Cloud."));

        RULES.add(new Rule("CC060", "SPA/GPA Parameter",
                "\\bPARAMETER\\s+ID\\b",
                Finding.Severity.WARNING, "Architecture",
                "Replace with explicit parameter passing",
                "SPA/GPA parameters are not supported in Fiori; redesign."));

        RULES.add(new Rule("CC070", "OCCURS clause",
                "\\bOCCURS\\s+\\d+",
                Finding.Severity.WARNING, "Style",
                "Use TYPE STANDARD TABLE OF",
                "OCCURS clause is obsolete; use TYPE STANDARD TABLE OF."));
        RULES.add(new Rule("CC070", "TABLES statement",
                "(?m)^\\s*TABLES\\s*:",
                Finding.Severity.INFO, "Style",
                "Remove header-line declarations",
                "TABLES statement is obsolete in modern ABAP."));

        RULES.add(new Rule("CC080", "Classic WRITE list",
                "(^|\\s)WRITE(?!:?\\s+TO)\\b",
                Finding.Severity.WARNING, "User Interface",
                "Replace with ALV / Fiori",
                "Classic list output (WRITE statement) — replace with ALV or Fiori."));

        RULES.add(new Rule("CC090", "Z* authorization object",
                "\\bAUTHORITY-CHECK\\s+OBJECT\\s+'Z",
                Finding.Severity.WARNING, "Security",
                "Validate the Z auth object still applies in S/4",
                "Z-authorization object — confirm it still applies in S/4."));

        RULES.add(new Rule("CC100", "MOVE-CORRESPONDING",
                "\\bMOVE-CORRESPONDING\\b",
                Finding.Severity.INFO, "Style",
                "Prefer explicit field mapping",
                "MOVE-CORRESPONDING is risky after structure changes; prefer explicit mapping."));
        RULES.add(new Rule("CC100", "CONDENSE NO-GAPS",
                "\\bCONDENSE\\b.*NO-GAPS",
                Finding.Severity.INFO, "Style",
                "Use modern string templates",
                "Consider modern string templates instead of CONDENSE NO-GAPS."));
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
                    f.setRuleName(r.name);
                    f.setSuggestion(r.suggestion);
                    f.setMatchedCode(stripped.length() > 200 ? stripped.substring(0, 197) + "..." : stripped);
                    out.add(f);
                }
            }
        }
        return out;
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
}
