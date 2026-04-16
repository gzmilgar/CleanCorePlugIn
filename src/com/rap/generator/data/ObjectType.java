package com.rap.generator.data;

public enum ObjectType {

    DDLS("DDLS", "CDS View Definition"),
    TABL("TABL", "Database Table"),
    DTEL("DTEL", "Data Element"),
    DOMA("DOMA", "Domain"),
    BDEF("BDEF", "Behavior Definition"),
    CLAS("CLAS", "Class"),
    INTF("INTF", "Interface"),
    FUGR("FUGR", "Function Group"),
    FUNC("FUNC", "Function Module"),
    SRVD("SRVD", "Service Definition"),
    SRVB("SRVB", "Service Binding"),
    DDLX("DDLX", "Metadata Extension"),
    STRU("STRU", "Structure"),
    TTYP("TTYP", "Table Type"),
    DRTY("DRTY", "CDS Type Definition"),
    NONT("NONT", "Non-Object Type"),
    MSAG("MSAG", "Message Class"),
    ENHO("ENHO", "Enhancement"),
    ENHS("ENHS", "Enhancement Spot"),
    XSLT("XSLT", "Transformation"),
    PROG("PROG", "Program"),
    AUTH("AUTH", "Authorization Check"),
    CHKV("CHKV", "Check Variant"),
    ALL("*", "All Types");

    private final String code;
    private final String description;

    ObjectType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return code + " - " + description;
    }

    public static ObjectType fromCode(String code) {
        for (ObjectType type : values()) {
            if (type.code.equals(code)) return type;
        }
        return null;
    }

    public static String[] getCodes() {
        ObjectType[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }

    public static String[] getLabels() {
        ObjectType[] values = values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].toString();
        }
        return labels;
    }
}
