package com.cwdil.archi.genai.ui.views;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.part.ViewPart;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.cwdil.archi.aitranslate.commands.TranslateAllViewsHandler;
import com.cwdil.archi.aitranslate.commands.TranslateViewHandler;
import com.cwdil.archi.aitranslate.commands.UndoTranslationHandler;
import com.cwdil.archi.sequence.commands.GenerateSequenceDiagramHandler;
import com.cwdil.archi.genai.auth.LocalAuthCallbackServer;
import com.cwdil.archi.genai.services.GenAIService;
import com.cwdil.archi.genai.services.WorkspaceSignalService;
import com.cwdil.archi.genai.storage.LocalChatHistoryStore;
import com.cwdil.archi.genai.util.SimpleJsonParser;
import com.cwdil.archi.genai.util.SimpleJsonParser.ParseException;

public class GenAIAssistantView extends ViewPart implements ISelectionListener, IPartListener2 {

    public static final String ID = "Archi_UI.genaiAssistantView";
    private static final String PAGE_PATH = "genai-ui/index.html";
    private static final int MIN_VIEW_WIDTH = 520;
    private static final int PREFERRED_VIEW_WIDTH = 760;
    private static final double PREFERRED_VIEW_RATIO = 0.36;
    private static final int MIN_SIBLING_WIDTH = 200;
    private static final int EXPAND_RETRY_DELAY_MS = 160;
    private static final int MAX_EXPAND_ATTEMPTS = 3;
    private static final int OPEN_GRACE_DELAY_MS = 900;

    private Browser browser;
    private boolean pageLoaded;
    private boolean sizeGuardInitialized;
    private boolean expandScheduled;
    private boolean hasMetMinimumWidth;
    private int expandAttempts;
    private long viewOpenedAt;

    private GenAIChatController chatController;
    private WorkspaceSignalsController workspaceSignalsController;
    private ExplainController explainController;
    private ChecksExplainController checksExplainController;
    private LocalChatHistoryStore localHistoryStore;
    private LocalAuthCallbackServer authCallbackServer;
    private LocalAuthCallbackServer.CallbackListener authCallbackListener;
    private volatile LocalAuthCallbackServer.CallbackData pendingAuthCallback;
    
    // Cache the last non-plugin part and selection
    private IWorkbenchPart lastNonPluginPart;
    private ISelection lastSelection;

    public GenAIAssistantView() {
    }

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());

        browser = createBrowser(parent);
        if(browser == null) {
            Label label = new Label(parent, SWT.WRAP);
            label.setText("Embedded browser not available."); //$NON-NLS-1$
            return;
        }
        viewOpenedAt = System.currentTimeMillis();
        installSizeGuard(parent);

        chatController = new GenAIChatController(new GenAIService(), browser);
        workspaceSignalsController = new WorkspaceSignalsController(new WorkspaceSignalService(), browser);
        explainController = new ExplainController(browser);
        checksExplainController = new ChecksExplainController(browser);
        authCallbackServer = LocalAuthCallbackServer.getInstance();
        authCallbackServer.start();
        authCallbackListener = new LocalAuthCallbackServer.CallbackListener() {
            @Override
            public void onCallback(LocalAuthCallbackServer.CallbackData data) {
                pendingAuthCallback = data;
                Display display = browser != null ? browser.getDisplay() : Display.getDefault();
                if(display != null && !display.isDisposed()) {
                    display.asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            flushPendingAuthCallback();
                        }
                    });
                }
            }
        };
        authCallbackServer.addListener(authCallbackListener);
        
        // Register Java Bridge for Chat
        new BrowserFunction(browser, "javaBridge") {
            @Override
            public Object function(Object[] arguments) {
                if(arguments != null && arguments.length > 0) {
                    Object msgObj = arguments[0];
                    if(msgObj instanceof String) {
                        String msg = (String)msgObj;
                        if(msg.startsWith("SYSTEM_CMD:")) {
                            handleSystemCommand(msg);
                        } else {
                            handleUserRequest(msg);
                        }
                    }
                }
                return null;
            }
        };

        new BrowserFunction(browser, "localHistoryBridge") {
            @Override
            public Object function(Object[] arguments) {
                String payload = "";
                if(arguments != null && arguments.length > 0 && arguments[0] != null) {
                    payload = String.valueOf(arguments[0]);
                }
                return handleLocalHistoryRequest(payload);
            }
        };

        new BrowserFunction(browser, "setExplainLanguage") {
            @Override
            public Object function(Object[] arguments) {
                if(arguments != null && arguments.length > 0 && arguments[0] instanceof String) {
                    explainController.setLanguage((String)arguments[0]);
                    updateExplainAsync();
                }
                return null;
            }
        };

        new BrowserFunction(browser, "requestChecksAiExplanation") {
            @Override
            public Object function(Object[] arguments) {
                if(checksExplainController == null) {
                    return null;
                }
                long requestId = longValue(arguments != null && arguments.length > 0 ? arguments[0] : null, 0L);
                String activeView = stringValue(arguments != null && arguments.length > 1 ? arguments[1] : null);
                int selectedCount = intValue(arguments != null && arguments.length > 2 ? arguments[2] : null, 0);
                String checksSummary = stringValue(arguments != null && arguments.length > 3 ? arguments[3] : null);
                String issuesText = stringValue(arguments != null && arguments.length > 4 ? arguments[4] : null);
                String languageCode = stringValue(arguments != null && arguments.length > 5 ? arguments[5] : null);
                checksExplainController.requestAsync(requestId, activeView, selectedCount, checksSummary, issuesText, languageCode);
                return null;
            }
        };

        new BrowserFunction(browser, "copyExplainText") {
            @Override
            public Object function(Object[] arguments) {
                if(arguments != null && arguments.length > 0 && arguments[0] instanceof String) {
                    copyToClipboard((String)arguments[0]);
                }
                return null;
            }
        };

        new BrowserFunction(browser, "getAuthRedirectUrl") {
            @Override
            public Object function(Object[] arguments) {
                if(authCallbackServer == null) {
                    authCallbackServer = LocalAuthCallbackServer.getInstance();
                    authCallbackServer.start();
                }
                return authCallbackServer.getRedirectUrl();
            }
        };

        browser.addProgressListener(new ProgressListener() {
            @Override
            public void completed(ProgressEvent event) {
                pageLoaded = true;
                if(checksExplainController != null) {
                    checksExplainController.setPageLoaded(true);
                }
                updateWorkspaceSignalsAsync();
                updateExplainAsync();
                flushPendingAuthCallback();
            }
            @Override
            public void changed(ProgressEvent event) {
                 // Pass
            }
        });

        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
        getSite().getWorkbenchWindow().getPartService().addPartListener(this);

        loadPage();
    }

    private void installSizeGuard(Composite container) {
        if(container == null) {
            return;
        }
        container.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                handleViewResized(container);
            }
        });
        Display display = container.getDisplay();
        if(display != null && !display.isDisposed()) {
            display.asyncExec(() -> handleViewResized(container));
        }
    }

    private void handleViewResized(Composite container) {
        if(container == null || container.isDisposed()) {
            return;
        }
        int width = container.getClientArea().width;
        if(width <= 0) {
            return;
        }
        if(width >= MIN_VIEW_WIDTH) {
            hasMetMinimumWidth = true;
        }
        if(!sizeGuardInitialized) {
            sizeGuardInitialized = true;
            scheduleExpandAttempt(container, PREFERRED_VIEW_WIDTH);
        }
        if(width < MIN_VIEW_WIDTH) {
            if(!hasMetMinimumWidth && !isPastOpenGracePeriod()) {
                if(expandAttempts < MAX_EXPAND_ATTEMPTS) {
                    scheduleExpandAttempt(container, Math.max(PREFERRED_VIEW_WIDTH, MIN_VIEW_WIDTH));
                }
            }
        }
    }

    private boolean isPastOpenGracePeriod() {
        if(viewOpenedAt <= 0) {
            return true;
        }
        return System.currentTimeMillis() - viewOpenedAt >= OPEN_GRACE_DELAY_MS;
    }

    private void scheduleExpandAttempt(Composite container, int targetWidth) {
        if(container == null || container.isDisposed() || expandScheduled) {
            return;
        }
        if(expandAttempts >= MAX_EXPAND_ATTEMPTS) {
            return;
        }
        Display display = container.getDisplay();
        if(display == null || display.isDisposed()) {
            return;
        }
        expandScheduled = true;
        display.timerExec(EXPAND_RETRY_DELAY_MS, () -> {
            expandScheduled = false;
            if(container.isDisposed()) {
                return;
            }
            expandAttempts++;
            tryExpandToWidth(container, targetWidth);
            handleViewResized(container);
        });
    }

    private void resetSizeGuard() {
        viewOpenedAt = System.currentTimeMillis();
        sizeGuardInitialized = false;
        expandAttempts = 0;
        hasMetMinimumWidth = false;
        if(browser == null || browser.isDisposed()) {
            return;
        }
        Display display = browser.getDisplay();
        if(display != null && !display.isDisposed()) {
            display.asyncExec(() -> {
                if(browser.isDisposed()) {
                    return;
                }
                Composite container = browser.getParent();
                if(container != null && !container.isDisposed()) {
                    handleViewResized(container);
                }
            });
        }
    }

    private boolean tryExpandToWidth(Composite container, int targetWidth) {
        SashForm sash = findParentSashForm(container);
        if(sash == null || sash.isDisposed() || sash.getOrientation() != SWT.HORIZONTAL) {
            return false;
        }
        int totalWidth = sash.getSize().x;
        if(totalWidth <= 0) {
            return false;
        }
        Control[] children = sash.getChildren();
        if(children == null || children.length < 2) {
            return false;
        }
        int index = findSashChildIndex(sash, container);
        if(index < 0) {
            return false;
        }
        int ratioWidth = (int)Math.round(totalWidth * PREFERRED_VIEW_RATIO);
        int desired = Math.max(targetWidth, ratioWidth);
        desired = Math.min(desired, totalWidth - MIN_SIBLING_WIDTH);
        if(desired < MIN_VIEW_WIDTH) {
            return false;
        }
        int remaining = Math.max(1, totalWidth - desired);
        int[] weights = new int[children.length];
        int sumOther = 0;
        for(int i = 0; i < children.length; i++) {
            if(i == index) {
                continue;
            }
            int width = children[i].getBounds().width;
            if(width <= 0) {
                width = 1;
            }
            weights[i] = width;
            sumOther += width;
        }
        if(sumOther <= 0) {
            sumOther = children.length - 1;
        }
        for(int i = 0; i < children.length; i++) {
            if(i == index) {
                continue;
            }
            double ratio = weights[i] / (double) sumOther;
            weights[i] = Math.max(1, (int)Math.round(remaining * ratio));
        }
        weights[index] = desired;
        sash.setWeights(weights);
        return true;
    }

    private SashForm findParentSashForm(Control control) {
        Composite parent = control.getParent();
        while(parent != null) {
            if(parent instanceof SashForm sash) {
                if(sash.getOrientation() == SWT.HORIZONTAL) {
                    return sash;
                }
            }
            parent = parent.getParent();
        }
        return null;
    }

    private int findSashChildIndex(SashForm sash, Control target) {
        Control[] children = sash.getChildren();
        for(int i = 0; i < children.length; i++) {
            if(isDescendant(children[i], target)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isDescendant(Control ancestor, Control target) {
        Control current = target;
        while(current != null) {
            if(current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void handleSystemCommand(String cmd) {
        // Format: SYSTEM_CMD:COMMAND:ARG
        String[] parts = cmd.split(":", 3);
        if(parts.length < 2) return;

        String action = parts[1];

        if("LANGUAGE_CHANGE".equals(action) && parts.length >= 3) {
            String countryCode = parts[2];
            System.out.println("System Command Request: Language change to " + countryCode);
        }
        else if("EXPLAIN_LANGUAGE".equals(action) && parts.length >= 3) {
            explainController.setLanguage(parts[2]);
            updateExplainAsync();
        }
        else if("EXPLAIN_ACTIVE".equals(action) && parts.length >= 3) {
            boolean active = Boolean.parseBoolean(parts[2]);
            explainController.setActive(active);
            if(active) {
                updateExplainAsync();
            }
        }
        else if("EXPLAIN".equals(action)) {
            explainController.setActive(true);
            updateExplainAsync();
        }
        else if("TRANSLATE".equals(action) && parts.length >= 3) {
            String which = parts[2];
            try {
                if("CURRENT".equalsIgnoreCase(which)) {
                    new TranslateViewHandler().execute(null);
                }
                else if("ALL".equalsIgnoreCase(which)) {
                    new TranslateAllViewsHandler().execute(null);
                }
                else if("UNDO".equalsIgnoreCase(which)) {
                    new UndoTranslationHandler().execute(null);
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if("SEQUENCE_DIAGRAM".equals(action)) {
            try {
                new GenerateSequenceDiagramHandler().execute(null);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if("CHAT_HISTORY_SET".equals(action) && parts.length >= 3) {
            String payload = decodeCommandPayload(parts[2]);
            if(chatController != null) {
                chatController.setHistoryFromJson(payload);
            }
        }
        else if("CHAT_HISTORY_RESET".equals(action)) {
            if(chatController != null) {
                chatController.resetHistory();
            }
        }
        else if("OPEN_EXTERNAL_URL".equals(action) && parts.length >= 3) {
            openExternalUrl(parts[2]);
        }
    }

    private String handleLocalHistoryRequest(String payload) {
        LocalChatHistoryStore store = getLocalHistoryStore();
        if(store == null) {
            return jsonError("Local history unavailable.");
        }
        Map<String, Object> request = parseJsonObject(payload);
        if(request == null) {
            return jsonError("Invalid request.");
        }
        String action = stringValue(request.get("action"));
        if(action == null || action.isBlank()) {
            return jsonError("Missing action.");
        }
        String userId = stringValue(request.get("user_id"));
        if(userId == null || userId.isBlank()) {
            return jsonError("Missing user_id.");
        }

        if("list_sessions".equals(action)) {
            int limit = intValue(request.get("limit"), 15);
            String cursor = stringValue(request.get("cursor"));
            LocalChatHistoryStore.ListResult result = store.listSessions(userId, limit, cursor);
            return jsonListSessions(result);
        }
        if("create_session".equals(action)) {
            String title = stringValue(request.get("title"));
            LocalChatHistoryStore.Session session = store.createSession(userId, title);
            return "{\"session\":" + jsonSession(session) + "}";
        }
        if("get_messages".equals(action)) {
            String sessionId = stringValue(request.get("session_id"));
            if(sessionId == null || sessionId.isBlank()) {
                return jsonError("Missing session_id.");
            }
            List<LocalChatHistoryStore.Message> messages = store.getMessages(userId, sessionId);
            return "{\"messages\":" + jsonMessages(messages) + "}";
        }
        if("append_message".equals(action)) {
            String sessionId = stringValue(request.get("session_id"));
            if(sessionId == null || sessionId.isBlank()) {
                return jsonError("Missing session_id.");
            }
            LocalChatHistoryStore.Message message = LocalChatHistoryStore.parseMessage(request.get("message"));
            if(message == null) {
                return jsonError("Missing message.");
            }
            LocalChatHistoryStore.Message saved = store.appendMessage(userId, sessionId, message);
            return "{\"message\":" + jsonMessage(saved) + "}";
        }
        if("rename_session".equals(action)) {
            String sessionId = stringValue(request.get("session_id"));
            String title = stringValue(request.get("title"));
            if(sessionId == null || sessionId.isBlank() || title == null || title.isBlank()) {
                return jsonError("Missing session_id or title.");
            }
            LocalChatHistoryStore.Session session = store.renameSession(userId, sessionId, title);
            return "{\"session\":" + jsonSession(session) + "}";
        }
        if("delete_session".equals(action)) {
            String sessionId = stringValue(request.get("session_id"));
            if(sessionId == null || sessionId.isBlank()) {
                return jsonError("Missing session_id.");
            }
            boolean deleted = store.deleteSession(userId, sessionId);
            return "{\"deleted\":" + deleted + ",\"session_id\":" + jsonNullable(sessionId) + "}";
        }
        if("get_storage_preference".equals(action)) {
            String preference = store.getStoragePreference(userId);
            return "{\"storage_preference\":" + jsonNullable(preference) + "}";
        }
        if("set_storage_preference".equals(action)) {
            String preference = stringValue(request.get("storage_preference"));
            if(preference == null || preference.isBlank()) {
                return jsonError("Missing storage_preference.");
            }
            String stored = store.setStoragePreference(userId, preference);
            return "{\"storage_preference\":" + jsonNullable(stored) + "}";
        }
        if("import_storage".equals(action)) {
            List<LocalChatHistoryStore.Session> sessions =
                    LocalChatHistoryStore.parseSessions(request.get("sessions"));
            Map<String, List<LocalChatHistoryStore.Message>> messages =
                    LocalChatHistoryStore.parseMessageMap(request.get("messages"));
            String preference = stringValue(request.get("storage_preference"));
            LocalChatHistoryStore.ImportResult result =
                    store.importStorage(userId, sessions, messages, preference);
            return "{\"imported\":true,"
                    + "\"sessions\":" + result.sessionsImported + ","
                    + "\"messages\":" + result.messagesImported
                    + "}";
        }
        return jsonError("Unsupported action.");
    }

    private String decodeCommandPayload(String payload) {
        if(payload == null) {
            return "";
        }
        try {
            return URLDecoder.decode(payload, StandardCharsets.UTF_8);
        }
        catch (Exception ex) {
            return payload;
        }
    }

    private void openExternalUrl(String url) {
        if(url == null || url.trim().isEmpty()) {
            return;
        }
        boolean launched = false;
        try {
            launched = Program.launch(url);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        if(!launched) {
            System.err.println("Unable to open external URL: " + url);
        }
    }

    private LocalChatHistoryStore getLocalHistoryStore() {
        if(localHistoryStore != null) {
            return localHistoryStore;
        }
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        if(bundle == null) {
            return null;
        }
        try {
            File stateRoot = Platform.getStateLocation(bundle).toFile();
            localHistoryStore = new LocalChatHistoryStore(stateRoot);
            return localHistoryStore;
        }
        catch(Exception ex) {
            System.err.println("Local history store unavailable: " + ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJsonObject(String payload) {
        if(payload == null || payload.isBlank()) {
            return null;
        }
        Object parsed;
        try {
            parsed = SimpleJsonParser.parse(payload);
        }
        catch(ParseException ex) {
            return null;
        }
        if(!(parsed instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
    }

    private String jsonListSessions(LocalChatHistoryStore.ListResult result) {
        if(result == null) {
            return "{\"sessions\":[],\"next_cursor\":null,\"has_more\":false}";
        }
        return "{"
                + "\"sessions\":" + jsonSessions(result.sessions) + ","
                + "\"next_cursor\":" + jsonNullable(result.nextCursor) + ","
                + "\"has_more\":" + result.hasMore
                + "}";
    }

    private String jsonSessions(List<LocalChatHistoryStore.Session> sessions) {
        if(sessions == null || sessions.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for(LocalChatHistoryStore.Session session : sessions) {
            if(!first) {
                sb.append(",");
            }
            first = false;
            sb.append(jsonSession(session));
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonMessages(List<LocalChatHistoryStore.Message> messages) {
        if(messages == null || messages.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for(LocalChatHistoryStore.Message message : messages) {
            if(!first) {
                sb.append(",");
            }
            first = false;
            sb.append(jsonMessage(message));
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonSession(LocalChatHistoryStore.Session session) {
        if(session == null) {
            return "null";
        }
        return "{"
                + "\"id\":" + jsonNullable(session.id) + ","
                + "\"title\":" + jsonNullable(session.title) + ","
                + "\"created_at\":" + jsonNullable(session.createdAt) + ","
                + "\"last_message_at\":" + jsonNullable(session.lastMessageAt)
                + "}";
    }

    private String jsonMessage(LocalChatHistoryStore.Message message) {
        if(message == null) {
            return "null";
        }
        return "{"
                + "\"id\":" + jsonNullable(message.id) + ","
                + "\"role\":" + jsonNullable(message.role) + ","
                + "\"content\":" + jsonNullable(message.content) + ","
                + "\"created_at\":" + jsonNullable(message.createdAt)
                + "}";
    }

    private String jsonError(String message) {
        return "{\"error\":" + jsonNullable(message) + "}";
    }

    private String jsonNullable(String value) {
        if(value == null) {
            return "null";
        }
        return jsonString(value);
    }

    private String jsonString(String value) {
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        if(value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch(c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if(c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String stringValue(Object value) {
        if(value == null) {
            return null;
        }
        if(value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if(value instanceof Number) {
            return ((Number) value).intValue();
        }
        if(value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            }
            catch(NumberFormatException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private long longValue(Object value, long fallback) {
        if(value instanceof Number) {
            return ((Number)value).longValue();
        }
        if(value instanceof String) {
            try {
                return Long.parseLong((String)value);
            }
            catch(NumberFormatException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private void handleUserRequest(String prompt) {
        System.out.println("AI Request: " + prompt);
        IWorkbenchPart activePart = getSite().getPage().getActivePart();
        ISelection selection = getSite().getWorkbenchWindow().getSelectionService().getSelection();
        chatController.handleUserRequest(prompt, activePart, selection);
    }

    @Override
    public void setFocus() {
        if(browser != null && !browser.isDisposed()) {
            browser.setFocus();
        }
    }

    @Override
    public void dispose() {
        if(getSite() != null && getSite().getWorkbenchWindow() != null) {
            getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
            getSite().getWorkbenchWindow().getPartService().removePartListener(this);
        }
        if(explainController != null) {
            explainController.dispose();
        }
        if(checksExplainController != null) {
            checksExplainController.dispose();
        }
        if(authCallbackServer != null && authCallbackListener != null) {
            authCallbackServer.removeListener(authCallbackListener);
        }
        super.dispose();
    }

    private void flushPendingAuthCallback() {
        if(!pageLoaded || browser == null || browser.isDisposed()) {
            return;
        }
        LocalAuthCallbackServer.CallbackData data = pendingAuthCallback;
        if(data == null && authCallbackServer != null) {
            data = authCallbackServer.consumeLastCallback();
        }
        if(data == null) {
            return;
        }
        pendingAuthCallback = null;
        sendAuthCallbackToBrowser(data);
        if(authCallbackServer != null) {
            authCallbackServer.clearLastCallback(data);
        }
    }

    private void sendAuthCallbackToBrowser(LocalAuthCallbackServer.CallbackData data) {
        if(data == null || browser == null || browser.isDisposed()) {
            return;
        }
        String payload = data.getFullUrl();
        if(payload == null || payload.isEmpty()) {
            payload = data.getRawQuery();
        }
        if(payload == null) {
            payload = "";
        }
        String safePayload = JsTextEscaper.escape(payload);
        String script = "if (window.handleSupabaseCallback) { window.handleSupabaseCallback(\""
                + safePayload + "\"); }";
        browser.execute(script);
    }

    private Browser createBrowser(Composite parent) {
        try {
            // Try Edge (WebView2) first for better rendering
            try {
                return new Browser(parent, SWT.EDGE);
            } catch (Throwable t) {
                return new Browser(parent, SWT.NONE);
            }
        }
        catch(SWTError ex) {
            System.err.println("Cannot create embedded browser: " + ex.getMessage()); 
            return null;
        }
    }

    private void loadPage() {
        try {
            URL pageUrl = resolvePageUrl();
            if(pageUrl == null) {
                return;
            }
            browser.setUrl(pageUrl.toExternalForm());
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private URL resolvePageUrl() throws IOException {
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        if(bundle == null) {
            return null;
        }

        File bundleFile = FileLocator.getBundleFile(bundle);
        if(bundleFile != null && bundleFile.isDirectory()) {
            File page = new File(bundleFile, PAGE_PATH);
            if(page.isFile()) {
                return page.toURI().toURL();
            }
        }

        File uiRoot = extractUiAssets(bundle);
        if(uiRoot != null) {
            File page = new File(uiRoot, PAGE_PATH);
            if(page.isFile()) {
                return page.toURI().toURL();
            }
        }

        URL url = FileLocator.find(bundle, new Path(PAGE_PATH), null);
        if(url == null) {
            return null;
        }
        return FileLocator.toFileURL(url);
    }

    private File extractUiAssets(Bundle bundle) throws IOException {
        File stateRoot;
        try {
            stateRoot = Platform.getStateLocation(bundle).toFile();
        }
        catch(IllegalStateException ex) {
            return null;
        }

        File uiRoot = new File(stateRoot, "ui-assets");
        File marker = new File(uiRoot, ".cache");
        long bundleStamp = bundle.getLastModified();

        boolean needsExtract = !marker.isFile() || marker.lastModified() != bundleStamp;
        if(!needsExtract) {
            if(new File(uiRoot, PAGE_PATH).isFile()
                    && new File(uiRoot, "genai-ui/style.css").isFile()
                    && new File(uiRoot, "genai-ui/script.js").isFile()
                    && new File(uiRoot, "images/pic.png").isFile()) {
                return uiRoot;
            }
            needsExtract = true;
        }

        if(needsExtract) {
            deleteRecursively(uiRoot);
            if(!uiRoot.mkdirs() && !uiRoot.isDirectory()) {
                return null;
            }

            extractBundleFolder(bundle, "genai-ui", uiRoot);
            extractBundleFolder(bundle, "images", uiRoot);

            if(!marker.exists()) {
                marker.createNewFile();
            }
            marker.setLastModified(bundleStamp);
        }

        return uiRoot;
    }

    private void extractBundleFolder(Bundle bundle, String folder, File targetRoot) throws IOException {
        Enumeration<URL> entries = bundle.findEntries(folder, "*", true);
        if(entries == null) {
            return;
        }

        String prefix = folder + "/";
        while(entries.hasMoreElements()) {
            URL entryUrl = entries.nextElement();
            String entryPath = entryUrl.getPath();
            int rootIndex = entryPath.indexOf(prefix);
            if(rootIndex < 0) {
                continue;
            }

            String relative = entryPath.substring(rootIndex);
            if(relative.isEmpty()) {
                continue;
            }

            if(relative.endsWith("/")) {
                File dir = new File(targetRoot, relative);
                if(!dir.exists() && !dir.mkdirs()) {
                    continue;
                }
                continue;
            }

            File outFile = new File(targetRoot, relative);
            File parent = outFile.getParentFile();
            if(parent != null && !parent.exists() && !parent.mkdirs()) {
                continue;
            }

            try(InputStream in = entryUrl.openStream();
                    FileOutputStream out = new FileOutputStream(outFile)) {
                in.transferTo(out);
            }
        }
    }

    private void deleteRecursively(File target) {
        if(target == null || !target.exists()) {
            return;
        }
        File[] children = target.listFiles();
        if(children != null) {
            for(File child : children) {
                deleteRecursively(child);
            }
        }
        target.delete();
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        // Cache selection from non-plugin parts
        if(part != null && !part.equals(this)) {
            lastSelection = selection;
        }
        handleContextChange();
    }

    // Part Listener Methods
    @Override public void partActivated(IWorkbenchPartReference partRef) { 
        // Cache the part if it's not this plugin
        IWorkbenchPart part = partRef.getPart(false);
        if(part != null && part.equals(this)) {
            resetSizeGuard();
        } else if(part != null) {
            lastNonPluginPart = part;
        }
        handleContextChange(); 
    }
    @Override public void partBroughtToTop(IWorkbenchPartReference partRef) { handleContextChange(); }
    @Override public void partClosed(IWorkbenchPartReference partRef) { handleContextChange(); }
    @Override public void partDeactivated(IWorkbenchPartReference partRef) { handleContextChange(); }
    @Override public void partOpened(IWorkbenchPartReference partRef) { handleContextChange(); }
    @Override public void partHidden(IWorkbenchPartReference partRef) {}
    @Override public void partVisible(IWorkbenchPartReference partRef) {
        if(partRef.getPart(false) == this) {
            resetSizeGuard();
        }
    }
    @Override public void partInputChanged(IWorkbenchPartReference partRef) { handleContextChange(); }

    private void handleContextChange() {
        updateWorkspaceSignalsAsync();
        updateExplainAsync();
    }

    private void updateWorkspaceSignalsAsync() {
        if(workspaceSignalsController == null || browser == null || browser.isDisposed()) {
            return;
        }
        
        // Use cached non-plugin part, or fallback to current active part
        IWorkbenchPage page = getSite().getPage();
        IWorkbenchPart activePart = lastNonPluginPart;
        if(activePart == null || activePart.equals(this)) {
            activePart = page != null ? page.getActivePart() : null;
        }
        // Only update if activePart is not this plugin
        if(activePart != null && activePart.equals(this)) {
            // If no cached part, use the previous one - don't update with plugin view
            activePart = lastNonPluginPart;
        }
        
        ISelection selection = lastSelection != null ? lastSelection : 
            (getSite().getWorkbenchWindow().getSelectionService().getSelection());
        
        workspaceSignalsController.updateAsync(pageLoaded, activePart, selection);
    }

    private void copyToClipboard(String text) {
        if(text == null || text.isBlank()) {
            return;
        }
        Display display = browser != null ? browser.getDisplay() : Display.getDefault();
        if(display == null) {
            return;
        }
        display.asyncExec(() -> {
            Clipboard clipboard = new Clipboard(display);
            try {
                clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
            }
            finally {
                clipboard.dispose();
            }
        });
    }

    private void updateExplainAsync() {
        if(explainController == null || browser == null || browser.isDisposed()) {
            return;
        }
        IWorkbenchPage page = getSite().getPage();
        
        // Use cached non-plugin part, or fallback to current active part
        IWorkbenchPart activePart = lastNonPluginPart;
        if(activePart == null || activePart.equals(this)) {
            activePart = page != null ? page.getActivePart() : null;
        }
        // Only update if activePart is not this plugin
        if(activePart != null && activePart.equals(this)) {
            // If no cached part, use the previous one - don't update with plugin view
            activePart = lastNonPluginPart;
        }
        
        ISelection selection = lastSelection != null ? lastSelection : 
            (getSite().getWorkbenchWindow().getSelectionService().getSelection());
        
        explainController.updateAsync(pageLoaded, page, activePart, selection);
    }
}
