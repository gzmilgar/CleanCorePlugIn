package com.sap.cleancore.analyzer.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Filter passed from AnalysisWizardDialog to ZObjectCollector.
 *
 * Three modes:
 *   FULL            — scan everything starting with Z* or Y*
 *   PACKAGE_PREFIX  — full scan + post-filter by package name prefix(es)
 *   SINGLE_OBJECT   — fetch one specific object by type + name
 *
 * ObjectTypes set restricts which TADIR types are queried (PROG, FUGR, CLAS, ...).
 */
public class AnalysisFilter {

    public enum Mode { FULL, PACKAGE_PREFIX, SINGLE_OBJECT }

    /** ADT TADIR codes paired with /<role> suffix used by the search endpoint. */
    public static final String[] ALL_OBJECT_TYPES = {
            "PROG/P",   // reports
            "FUGR/F",   // function groups
            "CLAS/OC",  // classes
            "INTF/OI",  // interfaces
            "TABL/DT",  // ddic tables
            "DDLS/DF",  // CDS
            "ENHO/XHE", // enhancement implementations
            "SXCI/SXC"  // BAdI impl
    };

    private Mode mode = Mode.FULL;
    private List<String> packagePrefixes = new ArrayList<>();
    private String singleObjectType;
    private String singleObjectName;
    private Set<String> includedObjectTypes = new LinkedHashSet<>();
    private int maxResults = 1000;
    private boolean includeY = true;

    public AnalysisFilter() {
        for (String t : ALL_OBJECT_TYPES) includedObjectTypes.add(t);
    }

    public static AnalysisFilter fullScan() {
        return new AnalysisFilter();
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<String> getPackagePrefixes() { return packagePrefixes; }
    public void setPackagePrefixes(List<String> p) { this.packagePrefixes = p; }

    public String getSingleObjectType() { return singleObjectType; }
    public void setSingleObjectType(String s) { this.singleObjectType = s; }

    public String getSingleObjectName() { return singleObjectName; }
    public void setSingleObjectName(String s) { this.singleObjectName = s; }

    public Set<String> getIncludedObjectTypes() { return includedObjectTypes; }
    public void setIncludedObjectTypes(Set<String> s) { this.includedObjectTypes = s; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int n) { this.maxResults = n; }

    public boolean isIncludeY() { return includeY; }
    public void setIncludeY(boolean b) { this.includeY = b; }
}
