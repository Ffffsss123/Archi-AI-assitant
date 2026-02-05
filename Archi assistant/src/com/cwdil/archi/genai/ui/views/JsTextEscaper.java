package com.cwdil.archi.genai.ui.views;

public final class JsTextEscaper {

    private JsTextEscaper() {}

    public static String escape(String value) {
        if(value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
