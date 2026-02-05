package com.cwdil.archi.genai.ui.views;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPart;

import com.cwdil.archi.genai.services.WorkspaceSignalService;
import com.cwdil.archi.genai.services.WorkspaceSignalService.SignalData;

public class WorkspaceSignalsController {

    private static final String STATUS_ANALYZING = "Analyzing...";
    private static final String STATUS_READY = "Ready";
    private static final String STATUS_ERROR = "Analysis failed";

    private final WorkspaceSignalService signalService;
    private final Browser browser;
    private volatile SignalData lastSignalData = new SignalData("None", 0, "No checks", "idle", "");
    private volatile boolean analysisInFlight;

    public WorkspaceSignalsController(WorkspaceSignalService signalService, Browser browser) {
        this.signalService = signalService;
        this.browser = browser;
        this.lastSignalData = new SignalData("None", 0, "No checks", "idle", "");
    }

    public void updateAsync(boolean pageLoaded, IWorkbenchPart activePart, ISelection selection) {
        if(!pageLoaded || browser == null || browser.isDisposed()) {
            return;
        }
        Display display = browser.getDisplay();
        if(display == null) {
            return;
        }

        // Push an immediate "analyzing" state to the UI.
        sendToBrowser(display, lastSignalData, STATUS_ANALYZING, "loading");

        if(analysisInFlight) {
            return;
        }

        analysisInFlight = true;
        try {
            SignalData data = signalService.getSignals(activePart, selection);
            lastSignalData = data;
            sendToBrowser(display, data, STATUS_READY, "ready");
        }
        catch(Exception ex) {
            ex.printStackTrace();
            sendToBrowser(display, lastSignalData, STATUS_ERROR, "error");
        }
        finally {
            analysisInFlight = false;
        }
    }

    private void sendToBrowser(Display display, SignalData data, String statusText, String statusState) {
        if(display == null) {
            return;
        }
        display.asyncExec(() -> {
            if(browser == null || browser.isDisposed() || data == null) {
                return;
            }
            String script = buildUpdateScript(data, statusText, statusState);
            browser.execute(script);
        });
    }

    private String buildUpdateScript(SignalData data, String statusText, String statusState) {
        // checksDetail is already HTML, so we need to properly escape it for JavaScript string
        String checksDetail = "";
        if(data.checksDetail != null && !data.checksDetail.isEmpty()) {
            // Escape for JavaScript string: \ " ' \n \r etc
            checksDetail = "\"" + JsTextEscaper.escape(data.checksDetail) + "\"";
        } else {
            checksDetail = "\"\"";
        }
        return "if(window.updateWorkspaceSignals) window.updateWorkspaceSignals({" +
                "activeView:\"" + JsTextEscaper.escape(data.activeView) + "\"," +
                "selectedCount:" + data.selectedCount + "," +
                "checksText:\"" + JsTextEscaper.escape(data.checksText) + "\"," +
                "checksState:\"" + JsTextEscaper.escape(data.checksState) + "\"," +
                "checksDetail:" + checksDetail + "," +
                "statusText:\"" + JsTextEscaper.escape(statusText) + "\"," +
                "statusState:\"" + JsTextEscaper.escape(statusState) + "\"" +
                "});";
    }
}
