package com.rap.generator.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class AbapTypeRegistry {

    private static final Map<String, String> TYPES = new LinkedHashMap<>();

    static {
        // Character types
        TYPES.put("abap.char(10)", "Character (10)");
        TYPES.put("abap.char(20)", "Character (20)");
        TYPES.put("abap.char(30)", "Character (30)");
        TYPES.put("abap.char(40)", "Character (40)");
        TYPES.put("abap.char(50)", "Character (50)");
        TYPES.put("abap.char(100)", "Character (100)");
        TYPES.put("abap.char(255)", "Character (255)");
        TYPES.put("abap.string", "String (unlimited)");
        TYPES.put("abap.sstring(256)", "Short String (256)");
        TYPES.put("abap.sstring(1024)", "Short String (1024)");

        // Numeric types
        TYPES.put("abap.int1", "Integer 1 byte");
        TYPES.put("abap.int2", "Integer 2 bytes");
        TYPES.put("abap.int4", "Integer 4 bytes");
        TYPES.put("abap.int8", "Integer 8 bytes");
        TYPES.put("abap.numc(4)", "Numeric Character (4)");
        TYPES.put("abap.numc(8)", "Numeric Character (8)");
        TYPES.put("abap.numc(10)", "Numeric Character (10)");
        TYPES.put("abap.dec(11,2)", "Decimal (11,2)");
        TYPES.put("abap.dec(15,2)", "Decimal (15,2)");
        TYPES.put("abap.dec(23,2)", "Decimal (23,2)");
        TYPES.put("abap.curr(15,2)", "Currency (15,2)");
        TYPES.put("abap.curr(23,2)", "Currency (23,2)");
        TYPES.put("abap.quan(13,3)", "Quantity (13,3)");
        TYPES.put("abap.fltp", "Floating Point");

        // Date/Time types
        TYPES.put("abap.dats", "Date (YYYYMMDD)");
        TYPES.put("abap.tims", "Time (HHMMSS)");
        TYPES.put("abap.utclong", "UTC Timestamp");
        TYPES.put("abap.datn", "Date (native)");
        TYPES.put("abap.timn", "Time (native)");

        // Boolean / Flag
        TYPES.put("abap_boolean", "Boolean (X/ )");
        TYPES.put("abap.char(1)", "Character (1) / Flag");

        // Special
        TYPES.put("abap.clnt", "Client");
        TYPES.put("abap.lang", "Language");
        TYPES.put("abap.cuky", "Currency Key");
        TYPES.put("abap.unit", "Unit of Measure");
        TYPES.put("abap.raw(16)", "Raw / UUID (16)");
        TYPES.put("sysuuid_x16", "System UUID");
    }

    public static String[] getTypeNames() {
        return TYPES.keySet().toArray(new String[0]);
    }

    public static String[] getTypeLabels() {
        return TYPES.values().toArray(new String[0]);
    }

    public static String getLabel(String typeName) {
        return TYPES.getOrDefault(typeName, typeName);
    }

    public static Set<String> getAllTypes() {
        return TYPES.keySet();
    }

    public static String getDefaultType() {
        return "abap.char(40)";
    }
}
