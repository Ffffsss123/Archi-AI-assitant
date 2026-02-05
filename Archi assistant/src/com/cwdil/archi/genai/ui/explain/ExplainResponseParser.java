package com.cwdil.archi.genai.ui.explain;

public class ExplainResponseParser {

    public ExplainResult parse(String text) {
        if(text == null) {
            return new ExplainResult("", "", "");
        }

        String normalized = text.replace("\r\n", "\n");
        String simplified = extractSection(normalized, "SIMPLIFIED:");
        String detailed = extractSection(normalized, "DETAILED:");
        String summary = extractSection(normalized, "SUMMARY:");

        if(simplified == null && detailed == null && summary == null) {
            String fallback = normalized.trim();
            return new ExplainResult(fallback, "", firstSentence(fallback));
        }

        if(simplified == null) {
            simplified = "";
        }
        if(detailed == null) {
            detailed = "";
        }
        if(summary == null) {
            summary = firstSentence(!simplified.isBlank() ? simplified : detailed);
        }

        return new ExplainResult(simplified.trim(), detailed.trim(), summary.trim());
    }

    private String extractSection(String text, String header) {
        int start = text.indexOf(header);
        if(start == -1) {
            return null;
        }
        start += header.length();

        int end = text.length();
        for(String other : new String[] { "SIMPLIFIED:", "DETAILED:", "SUMMARY:" }) {
            if(other.equals(header)) {
                continue;
            }
            int idx = text.indexOf(other, start);
            if(idx != -1 && idx < end) {
                end = idx;
            }
        }

        return text.substring(start, end).trim();
    }

    private String firstSentence(String text) {
        if(text == null) {
            return "";
        }
        String trimmed = text.trim();
        if(trimmed.isEmpty()) {
            return "";
        }
        int idx = trimmed.indexOf('.');
        if(idx == -1) {
            return trimmed;
        }
        return trimmed.substring(0, idx + 1);
    }
}
