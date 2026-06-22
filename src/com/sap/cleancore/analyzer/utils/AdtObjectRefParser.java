package com.sap.cleancore.analyzer.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Order-independent parser for ADT {@code <adtcore:objectReference .../>}
 * elements as returned by {@code /sap/bc/adt/repository/informationsystem/...}.
 *
 * <p>ADT does not guarantee a fixed attribute order — different endpoints and
 * releases emit {@code adtcore:type}, {@code adtcore:name} and
 * {@code adtcore:packageName} in different orders. A single positional regex
 * (name-then-type) silently fails on the common type-before-name shape, which
 * is why this parser extracts each attribute independently.
 *
 * <p>Pure Java (no Eclipse dependencies) so it can be unit-tested headlessly.
 */
public final class AdtObjectRefParser {

    /** A parsed object reference. Any field may be null/empty. */
    public static final class Ref {
        public final String name;
        public final String type;
        public final String pkg;
        public Ref(String name, String type, String pkg) {
            this.name = name;
            this.type = type;
            this.pkg = pkg;
        }
    }

    private static final Pattern TAG =
            Pattern.compile("<adtcore:objectReference\\b[^>]*?/?>", Pattern.DOTALL);

    private AdtObjectRefParser() {}

    public static List<Ref> parse(String xml) {
        List<Ref> out = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return out;
        Matcher m = TAG.matcher(xml);
        while (m.find()) {
            String tag = m.group();
            String name = attr(tag, "adtcore:name");
            if (name == null || name.isEmpty()) continue;
            out.add(new Ref(name, attr(tag, "adtcore:type"), attr(tag, "adtcore:packageName")));
        }
        return out;
    }

    private static String attr(String tag, String attrName) {
        Matcher m = Pattern.compile(Pattern.quote(attrName) + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }
}
