package com.cwdil.archi.genai.ui.explain;

public class ExplainResult {

    public final String simplified;
    public final String detailed;
    public final String summary;

    public ExplainResult(String simplified, String detailed, String summary) {
        this.simplified = simplified;
        this.detailed = detailed;
        this.summary = summary;
    }
}
