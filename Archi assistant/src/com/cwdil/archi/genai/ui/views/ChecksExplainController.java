package com.cwdil.archi.genai.ui.views;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;

import com.cwdil.archi.genai.ui.explain.GeminiClient;

/**
 * On-demand AI explanation/suggestions for validation findings shown in the
 * Workspace Signals "Checks" detail modal.
 *
 * Uses the same Gemini client/config as the Explain feature.
 */
public class ChecksExplainController {

    private static final int MAX_ISSUES_CHARS = 7000;
    private static final int MAX_PROMPT_CHARS = 12000;
    private static final int MAX_OUTPUT_CHARS = 16000;

    private final Browser browser;
    private final GeminiClient client;
    private final ExecutorService executor;

    private volatile boolean pageLoaded;
    private volatile boolean requestInFlight;
    private volatile String inFlightKey;
    private volatile String lastKey;
    private volatile String lastOutput;

    public ChecksExplainController(Browser browser) {
        this.browser = browser;
        this.client = new GeminiClient();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Archi-Checks-Explain-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void setPageLoaded(boolean pageLoaded) {
        this.pageLoaded = pageLoaded;
    }

    public void dispose() {
        executor.shutdownNow();
    }

    public void requestAsync(long requestId,
                             String activeView,
                             int selectedCount,
                             String checksSummary,
                             String issuesText,
                             String languageCode) {
        if(!isReady()) {
            return;
        }

        String issues = trimToLimit(normalize(issuesText), MAX_ISSUES_CHARS);
        if(issues.isBlank()) {
            sendUpdate(requestId, "idle", "No validation issues to explain.", "");
            return;
        }

        String key = buildKey(activeView, selectedCount, checksSummary, issues, languageCode);
        if(key.equals(lastKey) && lastOutput != null && !lastOutput.isBlank()) {
            sendUpdate(requestId, "ready", "AI suggestions ready.", lastOutput);
            return;
        }

        if(requestInFlight && key.equals(inFlightKey)) {
            sendUpdate(requestId, "loading", "Generating AI suggestions...", "");
            return;
        }

        requestInFlight = true;
        inFlightKey = key;
        sendUpdate(requestId, "loading", "Generating AI suggestions...", "");

        executor.submit(() -> {
            String output = null;
            String error = null;
            try {
                String prompt = buildPrompt(activeView, selectedCount, checksSummary, issues, languageCode);
                output = client.generateContent(prompt);
            }
            catch(Exception ex) {
                error = ex.getMessage();
            }

            requestInFlight = false;
            inFlightKey = null;

            if(!isReady()) {
                return;
            }

            if(error != null) {
                sendUpdate(requestId, "error", "AI error: " + error, "");
                return;
            }

            String normalizedOutput = trimToLimit(normalize(output), MAX_OUTPUT_CHARS);
            lastKey = key;
            lastOutput = normalizedOutput;
            sendUpdate(requestId, "ready", "AI suggestions ready.", normalizedOutput);
        });
    }

    private boolean isReady() {
        return pageLoaded && browser != null && !browser.isDisposed();
    }

    private String buildKey(String activeView,
                            int selectedCount,
                            String checksSummary,
                            String issues,
                            String languageCode) {
        return normalize(languageCode) + "|" +
                normalize(activeView) + "|" +
                selectedCount + "|" +
                normalize(checksSummary) + "|" +
                issues;
    }

    private String buildPrompt(String activeView,
                               int selectedCount,
                               String checksSummary,
                               String issues,
                               String languageCode) {
        String language = (languageCode == null || languageCode.isBlank()) ? "en" : languageCode.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert ArchiMate modeling assistant.\n");
        sb.append("These findings come from Archi Hammer validation (rules-based).\n");
        sb.append("Explain what they mean and suggest concrete fixes.\n\n");

        sb.append("Constraints:\n");
        sb.append("- Do NOT invent issues that are not listed.\n");
        sb.append("- Keep it concise and actionable.\n");
        sb.append("- If a finding is ambiguous, ask a short clarifying question.\n\n");

        sb.append("Reply language: ").append(language).append("\n\n");

        sb.append("Context:\n");
        sb.append("- Active view: ").append(safeLine(activeView)).append("\n");
        sb.append("- Selected count: ").append(selectedCount).append("\n");
        sb.append("- Summary: ").append(safeLine(checksSummary)).append("\n\n");

        sb.append("Findings:\n");
        sb.append(issues).append("\n\n");

        sb.append("Output format:\n");
        sb.append("1) Short summary (1-2 sentences)\n");
        sb.append("2) Suggested fixes (bullets)\n");
        sb.append("3) Optional: prevention tips (bullets)\n");

        return trimToLimit(sb.toString(), MAX_PROMPT_CHARS);
    }

    private void sendUpdate(long requestId, String state, String message, String content) {
        if(browser == null || browser.isDisposed()) {
            return;
        }
        Display display = browser.getDisplay();
        if(display == null) {
            return;
        }

        String safeState = JsTextEscaper.escape(normalize(state));
        String safeMessage = JsTextEscaper.escape(normalize(message));
        String safeContent = JsTextEscaper.escape(normalize(content));
        String script = "if(window.updateChecksAiExplanation) window.updateChecksAiExplanation({"
                + "requestId:" + requestId + ","
                + "state:\"" + safeState + "\","
                + "message:\"" + safeMessage + "\","
                + "content:\"" + safeContent + "\""
                + "});";

        display.asyncExec(() -> {
            if(!isReady()) {
                return;
            }
            browser.execute(script);
        });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeLine(String value) {
        String v = normalize(value);
        if(v.isBlank()) {
            return "(unknown)";
        }
        return v.replace('\n', ' ').replace('\r', ' ');
    }

    private String trimToLimit(String value, int maxChars) {
        if(value == null) {
            return "";
        }
        if(value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars));
    }
}

