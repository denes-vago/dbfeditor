package com.vd.dbfeditor.ui;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

public final class CharsetRegistry {
    private static final List<String> SUPPORTED_CHARSETS = List.of(
        "IBM437",
        "IBM737",
        "IBM775",
        "IBM850",
        "IBM852",
        "IBM855",
        "IBM857",
        "IBM858",
        "IBM860",
        "IBM861",
        "IBM862",
        "IBM863",
        "IBM864",
        "IBM865",
        "IBM866",
        "IBM869",
        "ISO-8859-1",
        "ISO-8859-2",
        "ISO-8859-3",
        "ISO-8859-4",
        "ISO-8859-5",
        "ISO-8859-6",
        "ISO-8859-7",
        "ISO-8859-8",
        "ISO-8859-9",
        "ISO-8859-11",
        "ISO-8859-13",
        "ISO-8859-15",
        "ISO-8859-16",
        "UTF-8",
        "WINDOWS-874",
        "WINDOWS-1250",
        "WINDOWS-1251",
        "WINDOWS-1252",
        "WINDOWS-1253",
        "WINDOWS-1254",
        "WINDOWS-1255",
        "WINDOWS-1256",
        "WINDOWS-1257",
        "WINDOWS-1258"
    );

    private CharsetRegistry() {
    }

    public static String[] supportedDisplayNames() {
        return SUPPORTED_CHARSETS.toArray(String[]::new);
    }

    public static Charset forDisplayName(String displayName) {
        return Charset.forName(displayName);
    }

    public static String displayName(Charset charset) {
        for (String supportedCharset : SUPPORTED_CHARSETS) {
            if (Charset.forName(supportedCharset).equals(charset)) {
                return supportedCharset;
            }
        }
        return charset.name().toUpperCase(Locale.ROOT);
    }
}
