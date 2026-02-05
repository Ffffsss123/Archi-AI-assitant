package com.cwdil.archi.genai.ui.views;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;

import com.cwdil.archi.genai.ui.explain.ExplainContext;
import com.cwdil.archi.genai.ui.explain.ExplainContextBuilder;
import com.cwdil.archi.genai.ui.explain.ExplainPromptBuilder;
import com.cwdil.archi.genai.ui.explain.ExplainResponseParser;
import com.cwdil.archi.genai.ui.explain.ExplainResult;
import com.cwdil.archi.genai.ui.explain.GeminiClient;

public class ExplainController {

    private static final long UPDATE_DEBOUNCE_MS = 700;

    private final Browser browser;
    private final ExplainContextBuilder contextBuilder;
    private final ExplainPromptBuilder promptBuilder;
    private final ExplainResponseParser responseParser;
    private final GeminiClient client;
    private final ExecutorService executor;

    private boolean active;
    private boolean pageLoaded;
    private boolean requestInFlight;
    private boolean debounceScheduled;
    private long lastRequestTime;
    private String lastContextKey;
    private ExplainContext pendingContext;
    private String languageCode = "en";

    public ExplainController(Browser browser) {
        this.browser = browser;
        this.contextBuilder = new ExplainContextBuilder();
        this.promptBuilder = new ExplainPromptBuilder();
        this.responseParser = new ExplainResponseParser();
        this.client = new GeminiClient();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Archi-Explain-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void setActive(boolean active) {
        this.active = active;
        if(!active) {
            pendingContext = null;
        }
    }

    public void setLanguage(String language) {
        if(language != null && !language.isBlank()) {
            this.languageCode = language;
        }
    }

    public void updateAsync(boolean pageLoaded, IWorkbenchPage page, IWorkbenchPart activePart, ISelection selection) {
        this.pageLoaded = pageLoaded;
        if(!isReady()) {
            return;
        }
        Display display = browser.getDisplay();
        if(display == null) {
            return;
        }
        display.asyncExec(() -> update(page, activePart, selection));
    }

    public void dispose() {
        executor.shutdownNow();
    }

    private void update(IWorkbenchPage page, IWorkbenchPart activePart, ISelection selection) {
        if(!isReady()) {
            return;
        }

        ExplainContext context = contextBuilder.buildContext(page, activePart, selection);
        if(context == null) {
            updateExplainState("No active view.", "idle");
            return;
        }

        updateExplainContextUi(context);
        processContext(context);
    }

    private void processContext(ExplainContext context) {
        if(!isReady()) {
            return;
        }

        String key = context.contextKey(languageCode);
        if(!requestInFlight && key.equals(lastContextKey)) {
            return;
        }

        long now = System.currentTimeMillis();
        if(now - lastRequestTime < UPDATE_DEBOUNCE_MS) {
            pendingContext = context;
            scheduleDebounce(UPDATE_DEBOUNCE_MS - (now - lastRequestTime));
            return;
        }

        if(requestInFlight) {
            pendingContext = context;
            return;
        }

        requestInFlight = true;
        lastRequestTime = now;
        lastContextKey = key;
        updateExplainState("Explaining...", "loading");

        executor.submit(() -> {
            ExplainResult result = null;
            String errorMessage = null;
            try {
                String prompt = promptBuilder.buildPrompt(context, languageCode);
                String responseText = client.generateContent(prompt);
                result = responseParser.parse(responseText);
            }
            catch(Exception ex) {
                errorMessage = ex.getMessage();
            }

            Display display = browser.getDisplay();
            if(display == null) {
                return;
            }
            ExplainResult finalResult = result;
            String finalError = errorMessage;
            display.asyncExec(() -> {
                requestInFlight = false;
                if(!isReady()) {
                    return;
                }
                if(finalError != null) {
                    updateExplainState("Error: " + finalError, "error");
                }
                else if(finalResult != null) {
                    updateExplainResultUi(finalResult);
                    updateExplainState("Updated " + Instant.now(), "ready");
                }
                if(pendingContext != null) {
                    ExplainContext next = pendingContext;
                    pendingContext = null;
                    processContext(next);
                }
            });
        });
    }

    private void scheduleDebounce(long delayMs) {
        if(debounceScheduled) {
            return;
        }
        debounceScheduled = true;
        Display display = browser.getDisplay();
        if(display == null) {
            debounceScheduled = false;
            return;
        }
        display.timerExec((int)Math.max(0, delayMs), () -> {
            debounceScheduled = false;
            if(pendingContext != null) {
                ExplainContext next = pendingContext;
                pendingContext = null;
                processContext(next);
            }
        });
    }

    private void updateExplainContextUi(ExplainContext context) {
        String script = "window.updateExplainContext({" +
                "viewName:\"" + JsTextEscaper.escape(context.viewName) + "\"," +
                "viewType:\"" + JsTextEscaper.escape(context.viewType) + "\"," +
                "selectedCount:" + context.selectedCount + "," +
                "hasSelection:" + context.hasSelection + "," +
                "language:\"" + JsTextEscaper.escape(languageCode) + "\"" +
                "});";
        browser.execute(script);
    }

    private void updateExplainResultUi(ExplainResult result) {
        String script = "window.updateExplainResult({" +
                "simplified:\"" + JsTextEscaper.escape(result.simplified) + "\"," +
                "detailed:\"" + JsTextEscaper.escape(result.detailed) + "\"," +
                "summary:\"" + JsTextEscaper.escape(result.summary) + "\"" +
                "});";
        browser.execute(script);
    }

    private void updateExplainState(String message, String state) {
        String script = "window.updateExplainState({" +
                "message:\"" + JsTextEscaper.escape(message) + "\"," +
                "state:\"" + JsTextEscaper.escape(state) + "\"" +
                "});";
        browser.execute(script);
    }

    private boolean isReady() {
        return active && pageLoaded && browser != null && !browser.isDisposed();
    }
}
