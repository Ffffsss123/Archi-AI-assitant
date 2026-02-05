/* global window */
(function() {
    // Views
    var loginView = document.getElementById('login-view');
    var appView = document.getElementById('app-view');

    var appShell = document.getElementById('assistantApp');
    var statusBadge = document.querySelector('.status-badge');
    var detailPanel = document.querySelector('.detail-panel');
    var backBtn = detailPanel ? detailPanel.querySelector('.back-btn') : null;
    var detailContents = document.querySelectorAll('.detail-content');
    var chatInitialized = false;

    // Controls
    var langBtn = document.querySelector('.lang-btn');
    var langMenu = document.querySelector('.tool-menu-container .dropdown-menu');
    var currentLangSpan = document.querySelector('.current-lang');
    var userBtn = document.querySelector('.user-btn');
    var userMenu = document.querySelector('.user-dropdown');
    var themeButtons = document.querySelectorAll('.theme-option');
    var userNameLabel = document.getElementById('userNameLabel');
    var userEmailLabel = document.getElementById('userEmailLabel');
    var settingsBtn = document.getElementById('settingsBtn');
    var historyBtn = document.getElementById('historyBtn');
    var signalsBtn = document.getElementById('signalsBtn');
    var historyPanel = document.getElementById('history-panel');
    var signalsPanel = document.getElementById('signals-panel');
    var signalsDrawer = document.getElementById('signals-drawer');
    var signalsGrid = document.querySelector('.signal-grid');
    // var modeBtn, modeMenu removed

    // Settings Modal
    var userNameModal = document.getElementById('user-name-modal');
    var displayNameInput = document.getElementById('displayNameInput');
    var displayNameHint = document.getElementById('displayNameHint');
    var displayNameMessage = document.getElementById('displayNameMessage');
    var storagePreferenceSelect = document.getElementById('storagePreferenceSelect');
    var saveNameBtn = document.getElementById('saveNameBtn');
    var skipNameBtn = document.getElementById('skipNameBtn');
    var closeNameBtn = userNameModal ? userNameModal.querySelector('.close-modal-btn') : null;

    // Rename Chat Modal
    var renameModal = document.getElementById('rename-modal');
    var renameChatInput = document.getElementById('renameChatInput');
    var renameChatMessage = document.getElementById('renameChatMessage');
    var renameChatSaveBtn = document.getElementById('renameChatSaveBtn');
    var renameChatCancelBtn = document.getElementById('renameChatCancelBtn');
    var renameChatCloseBtn = document.getElementById('renameChatCloseBtn');

    // Delete Chat Modal
    var deleteModal = document.getElementById('delete-modal');
    var deleteChatMessage = document.getElementById('deleteChatMessage');
    var deleteChatConfirmBtn = document.getElementById('deleteChatConfirmBtn');
    var deleteChatCancelBtn = document.getElementById('deleteChatCancelBtn');
    var deleteChatCloseBtn = document.getElementById('deleteChatCloseBtn');
    
    // Auth
    var doLoginBtn = document.getElementById('doLoginBtn');
    var loginEmail = document.getElementById('loginEmail');
    var loginPassword = document.getElementById('loginPassword');
    var doRegisterBtn = document.getElementById('doRegisterBtn');
    var regEmail = document.getElementById('regEmail');
    var regPassword = document.getElementById('regPassword');
    var regConfirm = document.getElementById('regConfirm');
    var googleLoginBtn = document.getElementById('googleLoginBtn');
    var authMessage = document.getElementById('authMessage');

    var appConfig = window.APP_CONFIG || {};
    var supabaseConfig = appConfig.supabase || {};
    var currentUser = null;

    // Signals Elements
    var wsActiveView = document.getElementById("wsActiveView");
    var wsSelectedCount = document.getElementById("wsSelectedCount");
    var wsChecks = document.getElementById("wsChecks");
    var wsStatus = document.getElementById("wsStatus");
    var workspaceInspector = document.querySelector(".inspector");
    var checksSignal = document.getElementById("checksSignal");
    var checksDetailLink = document.getElementById("checksDetailLink");
    var checksDetailModal = document.getElementById("checks-detail-modal");
    var checksDetailContent = document.getElementById("checksDetailContent");
    var closeChecksDetailBtn = document.getElementById("closeChecksDetailBtn");
    var closeChecksDetailBtn2 = document.getElementById("closeChecksDetailBtn2");
    var lastChecksDetail = "";
    var lastChecksSummary = "";
    var lastActiveViewTitle = "";
    var lastSelectedCount = 0;
    var lastChecksAiRequestId = 0;

    // Chat UI Elements
    var generateBtn = document.getElementById('generateBtn');
    var newChatBtn = document.getElementById('newChatBtn');
    var explainBtn = document.getElementById('explainBtn');
    var chatPanel = document.getElementById('chat-panel');
    var explainView = document.getElementById('explain-view');
    var chatHistory = document.getElementById('chat-history');
    var userInput = document.getElementById('user-input');
    var sendBtn = document.getElementById('send-btn');
    var chatTitle = document.getElementById('chatTitle');
    var renameChatBtn = document.getElementById('renameChatBtn');
    var sessionList = document.getElementById('sessionList');
    var sessionListLoading = document.getElementById('sessionListLoading');
    var sessionListEmpty = document.getElementById('sessionListEmpty');

    // Explain Elements
    var explainLanguageSelect = document.getElementById("explainLanguageSelect");
    var explainContextLine = document.getElementById("explainContextLine");
    var explainStateLine = document.getElementById("explainStateLine");
    var explainSummaryText = document.getElementById("explainSummaryText");
    var explainPanelTitle = document.getElementById("explainPanelTitle");
    var explainPanelBody = document.getElementById("explainPanelBody");
    var explainModeButtons = document.querySelectorAll("[data-explain-mode]");
    var copyExplainSummary = document.getElementById("copyExplainSummary");
    var copyExplainCurrent = document.getElementById("copyExplainCurrent");

    var CHAT_HISTORY_LIMIT = 12;
    var SESSION_PAGE_SIZE = 15;
    var storagePreferenceKey = "genai.storagePreference";
    var localHistoryUserIdKey = "genai.localHistoryUserId";
    var currentSessionId = null;
    var currentMessages = [];
    var sessionsInitialized = false;
    var currentMode = "generate";

    var GenAI = window.GenAI || {};
    var utils = GenAI.utils || {};
    var themeApi = GenAI.theme || null;
    var authApi = GenAI.auth || null;
    var explainApi = GenAI.explain || null;
    var signalsApi = GenAI.signals || null;
    var historyApi = GenAI.history || null;
    var chatApi = GenAI.chat || null;
    var forEach = utils.forEach;
    var generateId = utils.generateId;
    var buildMessage = utils.buildMessage;
    var toggleSignalsPanel = signalsApi && signalsApi.toggle ? signalsApi.toggle : function() {};
    var setSignalsExpanded = signalsApi && signalsApi.setExpanded ? signalsApi.setExpanded : function() {};
    var getSupabaseClient = authApi && authApi.getClient ? authApi.getClient : function() { return null; };
    var getAuthToken = authApi && authApi.getAuthToken ? authApi.getAuthToken : function() { return Promise.reject(new Error("Supabase auth unavailable.")); };
    var isAuthError = authApi && authApi.isAuthError ? authApi.isAuthError : function() { return false; };
    var handleAuthFailure = authApi && authApi.handleAuthFailure ? authApi.handleAuthFailure : function() { return false; };
    var setAuthMessage = authApi && authApi.setAuthMessage ? authApi.setAuthMessage : function() {};
    var setAuthSession = authApi && authApi.setSession ? authApi.setSession : function() {};
    var initAuth = authApi && authApi.initAuth ? authApi.initAuth : function() {};
    var initSupabaseAuth = authApi && authApi.initSupabaseAuth ? authApi.initSupabaseAuth : function() {};

    if (authApi && authApi.init) {
        authApi.init({
            supabaseConfig: supabaseConfig,
            dom: {
                doLoginBtn: doLoginBtn,
                loginEmail: loginEmail,
                loginPassword: loginPassword,
                doRegisterBtn: doRegisterBtn,
                regEmail: regEmail,
                regPassword: regPassword,
                regConfirm: regConfirm,
                googleLoginBtn: googleLoginBtn,
                authMessage: authMessage
            },
            onLogin: showAppView,
            onAuthExpired: showLoginView
        });
    }

    window.onerror = function(msg) {
        console.error(msg);
        return false;
    };

    function getEmailPrefix(user) {
        if (user && user.email) {
            return user.email.split("@")[0];
        }
        return "User";
    }

    function getUserLabel(user) {
        if (!user) {
            return "User";
        }
        if (user.user_metadata && user.user_metadata.display_name) {
            return user.user_metadata.display_name;
        }
        if (user.email) {
            return user.email.split("@")[0];
        }
        if (user.user_metadata && user.user_metadata.full_name) {
            return user.user_metadata.full_name;
        }
        return "User";
    }

    function setCurrentUser(user) {
        currentUser = user || null;
        if (userNameLabel) {
            userNameLabel.textContent = getUserLabel(user);
        }
        if (userEmailLabel) {
            userEmailLabel.textContent = (user && user.email) ? user.email : "Not signed in";
        }
    }

    function setNameMessage(message, type) {
        if (!displayNameMessage) {
            return;
        }
        if (!message) {
            displayNameMessage.textContent = "";
            displayNameMessage.setAttribute("hidden", "");
            displayNameMessage.className = "auth-message modal-message";
            return;
        }
        displayNameMessage.textContent = message;
        displayNameMessage.className = "auth-message modal-message" + (type ? " " + type : "");
        displayNameMessage.removeAttribute("hidden");
    }

    function setNameBusy(isBusy) {
        var disabled = !!isBusy;
        if (saveNameBtn) saveNameBtn.disabled = disabled;
        if (skipNameBtn) skipNameBtn.disabled = disabled;
        if (displayNameInput) displayNameInput.disabled = disabled;
        if (storagePreferenceSelect) storagePreferenceSelect.disabled = disabled;
        if (closeNameBtn) closeNameBtn.disabled = disabled;
    }

    function openNameModal(user) {
        if (!userNameModal) {
            return;
        }
        var fallback = getEmailPrefix(user);
        if (displayNameInput) {
            var currentName = user && user.user_metadata ? user.user_metadata.display_name : "";
            displayNameInput.value = currentName || "";
            displayNameInput.placeholder = "Defaults to " + fallback;
        }
        if (displayNameHint) {
            displayNameHint.textContent = "Default: " + fallback;
        }
        if (storagePreferenceSelect) {
            storagePreferenceSelect.value = historyApi && historyApi.resolveStoragePreference
                ? historyApi.resolveStoragePreference(user)
                : "cloud";
        }
        setNameMessage("", "");
        setNameBusy(false);
        userNameModal.removeAttribute('hidden');
        userNameModal.style.display = 'flex';
        setTimeout(function() {
            if (displayNameInput) {
                displayNameInput.focus();
            }
        }, 0);
    }

    function closeNameModal() {
        if (!userNameModal) {
            return;
        }
        userNameModal.setAttribute('hidden', '');
        userNameModal.style.display = 'none';
        setNameMessage("", "");
    }

    function maybePromptDisplayName(user) {
        if (!userNameModal || !user) {
            return;
        }
        if (user.user_metadata && user.user_metadata.display_name) {
            return;
        }
        openNameModal(user);
    }

    function saveSettings() {
        var client = getSupabaseClient();
        if (!client) {
            setNameMessage("Auth unavailable. Please sign in again.", "error");
            return;
        }
        var name = displayNameInput ? displayNameInput.value.trim() : "";
        var currentStorageMode = historyApi && historyApi.getStorageMode ? historyApi.getStorageMode() : "cloud";
        var preference = storagePreferenceSelect ? storagePreferenceSelect.value : currentStorageMode;
        var shouldReload = preference !== currentStorageMode;
        setNameBusy(true);
        var data = {};
        if (name) {
            data.display_name = name;
        } else {
            data.display_name = null;
        }
        if (preference) {
            data.storage_preference = preference;
        }
        client.auth.updateUser({ data: data })
            .then(function(res) {
                setNameBusy(false);
                if (res.error) {
                    setNameMessage(res.error.message || "Update failed.", "error");
                    return;
                }
                var user = res.data && res.data.user ? res.data.user : currentUser;
                if (user) {
                    user.user_metadata = user.user_metadata || {};
                    user.user_metadata.display_name = data.display_name || null;
                    if (data.storage_preference) {
                        user.user_metadata.storage_preference = data.storage_preference;
                    }
                }
                setCurrentUser(user);
                if (data.storage_preference && historyApi && historyApi.applyStoragePreference) {
                    historyApi.applyStoragePreference(data.storage_preference, { persistLocal: true, reloadSessions: shouldReload });
                }
                closeNameModal();
            })
            .catch(function(err) {
                setNameBusy(false);
                setNameMessage((err && err.message) || "Update failed.", "error");
            });
    }

    function showAppView(user) {
        setCurrentUser(user);
        if (loginView) {
            loginView.setAttribute("hidden", "");
        }
        if (appView) {
            appView.removeAttribute("hidden");
            initChat();
            initSessions();
        }
        switchMode('generate');
        if (historyApi && historyApi.applyStoragePreference && historyApi.resolveStoragePreference) {
            historyApi.applyStoragePreference(historyApi.resolveStoragePreference(user), { persistLocal: true, reloadSessions: true });
        }
        maybePromptDisplayName(user);
    }

    function showLoginView() {
        if (appView) {
            appView.setAttribute("hidden", "");
        }
        if (loginView) {
            loginView.removeAttribute("hidden");
        }
        setCurrentUser(null);
    }

    document.addEventListener('DOMContentLoaded', function() {
        if (themeApi) {
            themeApi.init({ buttons: themeButtons, preferenceKey: "genai.theme" });
            themeApi.apply(themeApi.loadPreference(), { persist: false });
        }
        if (explainApi && explainApi.init) {
            explainApi.init({
                dom: {
                    languageSelect: explainLanguageSelect,
                    contextLine: explainContextLine,
                    stateLine: explainStateLine,
                    summaryText: explainSummaryText,
                    panelTitle: explainPanelTitle,
                    panelBody: explainPanelBody,
                    modeButtons: explainModeButtons,
                    copySummary: copyExplainSummary,
                    copyCurrent: copyExplainCurrent
                },
                mode: "simplified"
            });
        }
        initControls();
        initAuth();
        initSupabaseAuth();
        initSessions();
        // Chat init is handled after login or if already visible
    });

    function closePanel(panel) {
        if(panel) {
            panel.setAttribute('hidden', '');
        }
    }

    function openPanel(panel) {
        closePanel(historyPanel);
        if(panel !== explainView) {
            closePanel(explainView);
        }
        if(panel) {
            panel.removeAttribute('hidden');
        }
    }

    function togglePanel(panel) {
        if(!panel) {
            return;
        }
        if(panel.hasAttribute('hidden')) {
            openPanel(panel);
        } else {
            closePanel(panel);
        }
    }

    function setExplainActive(active) {
        if(window.javaBridge) {
            window.javaBridge("SYSTEM_CMD:EXPLAIN_ACTIVE:" + (active ? "true" : "false"));
        }
    }

    function switchMode(mode) {
        currentMode = mode === 'explain' ? 'explain' : 'generate';
        if(currentMode === 'generate') {
            if(generateBtn) generateBtn.classList.add('active');
            if(explainBtn) explainBtn.classList.remove('active');
            setExplainActive(false);
            closePanel(explainView);
        } else {
            if(explainBtn) explainBtn.classList.add('active');
            if(generateBtn) generateBtn.classList.remove('active');
            setExplainActive(true);
            openPanel(explainView);
        }
        if (generateBtn) {
            generateBtn.setAttribute("aria-pressed", currentMode === "generate" ? "true" : "false");
        }
        if (explainBtn) {
            explainBtn.setAttribute("aria-pressed", currentMode === "explain" ? "true" : "false");
        }
        if (document.body) {
            document.body.setAttribute("data-mode", currentMode);
        }
    }

    function initControls() {
        // --- Shared Dropdown Logic for Mutual Exclusion ---
        function closeAllDropdowns() {
            if(langMenu) { langMenu.setAttribute('hidden', ''); langMenu.style.display = 'none'; }
            if(userMenu) { userMenu.setAttribute('hidden', ''); userMenu.style.display = 'none'; }
        }

        function toggleDropdown(menu, btn, e) {
            e.stopPropagation();
            var isHidden = menu.hasAttribute('hidden') || menu.style.display === 'none';
            closeAllDropdowns(); // Close others first
            if(isHidden) {
                menu.removeAttribute('hidden');
                menu.style.display = 'block';
            }
        }

        // Language Modal Logic
        var langModal = document.getElementById('language-modal');
        var closeLangBtn = document.getElementById('closeLangBtn');
        var closeLangModalBtn = langModal ? langModal.querySelector('.close-modal-btn') : null;
        var explainMenuBtn = document.getElementById('explainMenuBtn');
        var translateMenuBtn = document.getElementById('translateMenuBtn');
        var diagramMenuBtn = document.getElementById('diagramMenuBtn');
        var translateAllBtn = document.getElementById('translateAllBtn');
        var translateCurrentBtn = document.getElementById('translateCurrentBtn');
        var undoTranslateBtn = document.getElementById('undoTranslateBtn');

        if (langBtn && langMenu) {
            langBtn.addEventListener('click', function(e) {
                toggleDropdown(langMenu, langBtn, e);
            });
        }

        if (langMenu) {
            langMenu.addEventListener('click', function(e) {
                e.stopPropagation();
            });
        }

        function openLangModal() {
            if (!langModal) {
                return;
            }
            langModal.removeAttribute('hidden');
            langModal.style.display = 'flex';
        }

        function closeLangModal() {
            if (!langModal) {
                return;
            }
            langModal.setAttribute('hidden', '');
            langModal.style.display = 'none';
        }

        if (translateMenuBtn) {
            translateMenuBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                closeAllDropdowns();
                openLangModal();
            });
        }

        if (explainMenuBtn) {
            explainMenuBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                closeAllDropdowns();
                switchMode('explain');
            });
        }

        if (diagramMenuBtn) {
            diagramMenuBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                closeAllDropdowns();
                if (window.javaBridge) {
                    window.javaBridge("SYSTEM_CMD:SEQUENCE_DIAGRAM");
                }
            });
        }

        if (closeLangBtn) closeLangBtn.addEventListener('click', closeLangModal);
        if (closeLangModalBtn) closeLangModalBtn.addEventListener('click', closeLangModal);

        function runTranslate(which) {
            if(window.javaBridge) {
                window.javaBridge("SYSTEM_CMD:TRANSLATE:" + which);
            }
            closeLangModal();
        }

        if(translateAllBtn) {
            translateAllBtn.addEventListener('click', function() {
                runTranslate("ALL");
            });
        }
        if(translateCurrentBtn) {
            translateCurrentBtn.addEventListener('click', function() {
                runTranslate("CURRENT");
            });
        }
        if(undoTranslateBtn) {
            undoTranslateBtn.addEventListener('click', function() {
                runTranslate("UNDO");
            });
        }
        
        if(langModal) {
            // Close when clicking outside modal card
            langModal.addEventListener('click', function(e) {
                if(e.target === langModal) {
                    closeLangModal();
                }
            });
        }

        // Settings Modal Logic
        if (settingsBtn) {
            settingsBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                closeAllDropdowns();
                openNameModal(currentUser);
            });
        }
        if (saveNameBtn) {
            saveNameBtn.addEventListener('click', function() {
                saveSettings();
            });
        }
        if (skipNameBtn) {
            skipNameBtn.addEventListener('click', function() {
                closeNameModal();
            });
        }
        if (closeNameBtn) {
            closeNameBtn.addEventListener('click', function() {
                closeNameModal();
            });
        }
        if (storagePreferenceSelect) {
            storagePreferenceSelect.addEventListener('change', function() {
                if (historyApi && historyApi.updateStoragePreference) {
                    historyApi.updateStoragePreference(storagePreferenceSelect.value);
                }
            });
        }
        if (displayNameInput) {
            displayNameInput.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    saveSettings();
                } else if (e.key === 'Escape') {
                    closeNameModal();
                }
            });
        }
        if (userNameModal) {
            userNameModal.addEventListener('click', function(e) {
                if (e.target === userNameModal) {
                    closeNameModal();
                }
            });
        }

        if (themeButtons && themeButtons.length) {
            forEach(themeButtons, function(btn) {
                btn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    if (themeApi) {
                        themeApi.apply(btn.getAttribute("data-theme"));
                    }
                    closeAllDropdowns();
                });
            });
        }

        // User Dropdown
        if(userBtn && userMenu) {
            userBtn.addEventListener('click', function(e) { toggleDropdown(userMenu, userBtn, e); });
        }

        var logoutBtn = document.getElementById('logoutBtn');
        if(logoutBtn) {
            logoutBtn.addEventListener('click', function() {
                if (authApi && authApi.signOut) {
                    authApi.signOut();
                }
                setAuthSession(null);
                setAuthMessage("", "");
                closeNameModal();
                if(appView) appView.setAttribute('hidden', '');
                if(loginView) {
                    loginView.removeAttribute('hidden');
                    if(loginEmail) loginEmail.value = '';
                    var pwd = document.getElementById('loginPassword');
                    if(pwd) pwd.value = '';
                }
                setCurrentUser(null);
                resetSessionState();
                if (chatApi && chatApi.renderEmptyChat) {
                    chatApi.renderEmptyChat();
                }
                if (chatApi && chatApi.updateChatTitle) {
                    chatApi.updateChatTitle(null);
                }
                if(userInput) {
                    userInput.value = '';
                    userInput.style.height = 'auto';
                }

                // Reset Language to Default
                if(currentLangSpan) currentLangSpan.textContent = 'EN';
                if(window.javaBridge) {
                    window.javaBridge("SYSTEM_CMD:LANGUAGE_CHANGE:EN");
                }

                setExplainActive(false);
                closePanel(historyPanel);
                setSignalsExpanded(false);
                closePanel(explainView);
                closeAllDropdowns();
            });
        }

        if(historyBtn) {
            historyBtn.addEventListener('click', function() {
                if(currentMode === 'explain') {
                    switchMode('generate');
                }
                togglePanel(historyPanel);
            });
        }

        if(signalsBtn) {
            signalsBtn.addEventListener('click', function() {
                if(currentMode === 'explain') {
                    switchMode('generate');
                }
                toggleSignalsPanel();
            });
        }
        if (signalsApi && signalsApi.init) {
            signalsApi.init({
                dom: {
                    signalsBtn: signalsBtn,
                    signalsPanel: signalsPanel,
                    signalsDrawer: signalsDrawer,
                    signalsGrid: signalsGrid,
                    wsActiveView: wsActiveView,
                    wsSelectedCount: wsSelectedCount,
                    wsChecks: wsChecks,
                    wsStatus: wsStatus,
                    checksSignal: checksSignal,
                    checksDetailLink: checksDetailLink,
                    checksDetailModal: checksDetailModal,
                    checksDetailContent: checksDetailContent,
                    closeChecksDetailBtn: closeChecksDetailBtn,
                    closeChecksDetailBtn2: closeChecksDetailBtn2
                }
            });
        }

        var closePanelButtons = document.querySelectorAll('.close-panel-btn');
        forEach(closePanelButtons, function(btn) {
            btn.addEventListener('click', function() {
                var targetId = btn.getAttribute('data-close');
                if(targetId === 'explain-view') {
                    switchMode('generate');
                    return;
                }
                var panel = targetId ? document.getElementById(targetId) : null;
                closePanel(panel);
            });
        });

        if(historyPanel) {
            historyPanel.addEventListener('click', function(e) {
                if(e.target === historyPanel) {
                    closePanel(historyPanel);
                }
            });
        }

        if(explainView) {
            explainView.addEventListener('click', function(e) {
                if(e.target === explainView) {
                    switchMode('generate');
                }
            });
        }

        // Generate / Explain Mode Logic
        if(newChatBtn) {
            newChatBtn.addEventListener('click', function() {
                switchMode('generate');
                startNewSession();
            });
        }
        if(generateBtn) {
            generateBtn.addEventListener('click', function() {
                switchMode('generate');
            });
        }
        if(explainBtn) {
            explainBtn.addEventListener('click', function() { switchMode('explain'); });
        }

        // Click outside to close all
        document.addEventListener('click', function() {
            closeAllDropdowns();
        });

        document.addEventListener('keydown', function(e) {
            if(e.key === 'Escape') {
                closeAllDropdowns();
                closePanel(historyPanel);
                setSignalsExpanded(false);
                if(currentMode === 'explain') {
                    switchMode('generate');
                }
            }
        });
    }

    function initChat() {
        if (chatInitialized) {
            return;
        }
        if (chatApi && chatApi.init) {
            chatApi.init({
                dom: {
                    chatHistory: chatHistory,
                    userInput: userInput,
                    sendBtn: sendBtn,
                    chatTitle: chatTitle,
                    renameChatBtn: renameChatBtn
                },
                utils: {
                    buildMessage: buildMessage,
                    generateId: generateId
                },
                callbacks: {
                    ensureActiveSession: ensureActiveSession,
                    persistMessage: persistMessage,
                    autoTitleSession: autoTitleSession
                },
                stateAccess: {
                    getCurrentSessionId: function() { return currentSessionId; },
                    getCurrentMessages: function() { return currentMessages; },
                    setCurrentMessages: function(messages) { currentMessages = messages || []; }
                }
            });
            chatInitialized = true;
            return;
        }
    }

    function getSessionTitle(session) {
        if (!session) {
            return "New chat";
        }
        var title = (session.title || "").trim();
        return title ? title : "New chat";
    }

    function deriveTitleFromMessage(text) {
        var cleaned = String(text || "").replace(/\s+/g, " ").trim();
        if (!cleaned) {
            return "New chat";
        }
        if (cleaned.length > 48) {
            cleaned = cleaned.slice(0, 48).trim() + "...";
        }
        return cleaned;
    }


    function resetSessionState() {
        if (historyApi && historyApi.resetSessionState) {
            historyApi.resetSessionState();
            return;
        }
        sessions = [];
        sessionById = {};
        sessionsCursor = null;
        sessionsHasMore = true;
        sessionsLoading = false;
        currentSessionId = null;
        currentMessages = [];
        pendingSessionPromise = null;
        if (chatApi && chatApi.setSendButtonMode) {
            chatApi.setSendButtonMode("send");
        }
        if (sessionList) {
            sessionList.innerHTML = "";
        }
        if (sessionListLoading) {
            sessionListLoading.setAttribute('hidden', '');
        }
        if (sessionListEmpty) {
            sessionListEmpty.textContent = "No history yet.";
            sessionListEmpty.setAttribute('hidden', '');
        }
        if (renameChatBtn) {
            renameChatBtn.disabled = true;
        }
    }

    function setSessionListLoading(isLoading) {
        if (!sessionListLoading) {
            return;
        }
        if (isLoading) {
            sessionListLoading.removeAttribute('hidden');
        } else {
            sessionListLoading.setAttribute('hidden', '');
        }
    }

    function setSessionListEmptyMessage(message) {
        if (!sessionListEmpty) {
            return;
        }
        if (message) {
            sessionListEmpty.textContent = message;
            sessionListEmpty.removeAttribute('hidden');
        } else {
            sessionListEmpty.setAttribute('hidden', '');
        }
    }

    function formatHistoryError(err, fallback) {
        if (fallback) {
            return fallback;
        }
        if (isAuthError(err)) {
            return "Session expired. Please sign in again.";
        }
        return "Failed to sync history.";
    }

    function reportHistoryError(err, fallback) {
        var message = formatHistoryError(err, fallback);
        if (!message) {
            return;
        }
        setSessionListEmptyMessage(message);
        if (sessions.length) {
            setTimeout(function() {
                if (sessions.length) {
                    setSessionListEmptyMessage(null);
                }
            }, 4000);
        }
    }

    function renderSessionItem(session, prepend) {
        if (!sessionList || !session || !session.id) {
            return;
        }
        var item = document.createElement('div');
        item.className = 'session-item';
        item.setAttribute('role', 'button');
        item.setAttribute('tabindex', '0');
        item.setAttribute('data-session-id', session.id);

        var titleSpan = document.createElement('span');
        titleSpan.className = 'session-title';
        titleSpan.textContent = getSessionTitle(session);
        item.appendChild(titleSpan);

        var actions = document.createElement('div');
        actions.className = 'session-actions';

        var renameBtn = document.createElement('button');
        renameBtn.className = 'session-rename';
        renameBtn.type = 'button';
        renameBtn.setAttribute('aria-label', 'Rename chat');
        renameBtn.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
            '<path d="M16.5 3.5l4 4-11 11H5.5v-4.5l11-11zm-1.4 2.1l-8.6 8.6v1.8h1.8l8.6-8.6-1.8-1.8zm3.3-3.3l1.8 1.8c.4.4.4 1 0 1.4l-1.1 1.1-3.2-3.2 1.1-1.1c.4-.4 1-.4 1.4 0z"/>' +
            '</svg>';
        renameBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            if (currentSessionId !== session.id) {
                return;
            }
            openRenameModal(session.id);
        });
        actions.appendChild(renameBtn);

        var deleteBtn = document.createElement('button');
        deleteBtn.className = 'session-delete';
        deleteBtn.type = 'button';
        deleteBtn.setAttribute('aria-label', 'Delete chat');
        deleteBtn.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
            '<path d="M9 3h6l1 2h4v2H4V5h4l1-2zm1 6h2v9h-2V9zm4 0h2v9h-2V9zm-8 0h2v9H6V9z"/>' +
            '</svg>';
        deleteBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            if (currentSessionId !== session.id) {
                return;
            }
            openDeleteModal(session.id);
        });
        actions.appendChild(deleteBtn);

        item.appendChild(actions);

        if (currentSessionId === session.id) {
            item.classList.add('active');
            item.setAttribute('aria-current', 'true');
        }

        if (prepend && sessionList.firstChild) {
            sessionList.insertBefore(item, sessionList.firstChild);
        } else {
            sessionList.appendChild(item);
        }
    }

    function updateSessionItem(session) {
        if (!sessionList || !session || !session.id) {
            return;
        }
        var item = sessionList.querySelector('[data-session-id="' + session.id + '"]');
        if (!item) {
            return;
        }
        var title = item.querySelector('.session-title');
        if (title) {
            title.textContent = getSessionTitle(session);
        }
    }

    function setActiveSessionItem(sessionId) {
        if (!sessionList) {
            return;
        }
        var active = sessionList.querySelector('.session-item.active');
        if (active) {
            active.classList.remove('active');
            active.removeAttribute('aria-current');
        }
        if (!sessionId) {
            return;
        }
        var next = sessionList.querySelector('[data-session-id="' + sessionId + '"]');
        if (next) {
            next.classList.add('active');
            next.setAttribute('aria-current', 'true');
        }
    }

    function findSessionItem(target) {
        var node = target;
        while (node && node !== sessionList) {
            if (node.classList && node.classList.contains('session-item')) {
                return node;
            }
            node = node.parentNode;
        }
        return null;
    }

    function initSessions() {
        if (sessionsInitialized) {
            return;
        }
        sessionsInitialized = true;
        if (historyApi && historyApi.init) {
            historyApi.init({
                dom: {
                    sessionList: sessionList,
                    sessionListLoading: sessionListLoading,
                    sessionListEmpty: sessionListEmpty,
                    renameChatBtn: renameChatBtn,
                    renameModal: renameModal,
                    renameChatInput: renameChatInput,
                    renameChatMessage: renameChatMessage,
                    renameChatSaveBtn: renameChatSaveBtn,
                    renameChatCancelBtn: renameChatCancelBtn,
                    renameChatCloseBtn: renameChatCloseBtn,
                    deleteModal: deleteModal,
                    deleteChatMessage: deleteChatMessage,
                    deleteChatConfirmBtn: deleteChatConfirmBtn,
                    deleteChatCancelBtn: deleteChatCancelBtn,
                    deleteChatCloseBtn: deleteChatCloseBtn,
                    storagePreferenceSelect: storagePreferenceSelect
                },
                supabaseConfig: supabaseConfig,
                authApi: authApi,
                callbacks: {
                    renderEmptyChat: function() {
                        if (chatApi && chatApi.renderEmptyChat) {
                            chatApi.renderEmptyChat();
                        }
                    },
                    renderLoadingChat: function() {
                        if (chatApi && chatApi.renderLoadingChat) {
                            chatApi.renderLoadingChat();
                        }
                    },
                    renderChatHistory: function(messages) {
                        if (chatApi && chatApi.renderChatHistory) {
                            chatApi.renderChatHistory(messages);
                        }
                    },
                    updateChatTitle: function(session) {
                        if (chatApi && chatApi.updateChatTitle) {
                            chatApi.updateChatTitle(session);
                        }
                    },
                    switchMode: switchMode,
                    closeHistoryPanel: function() { closePanel(historyPanel); },
                    syncJavaHistory: syncJavaHistory,
                    applyStoppedCache: function(sessionId, messages) {
                        if (chatApi && chatApi.applyStoppedCache) {
                            return chatApi.applyStoppedCache(sessionId, messages);
                        }
                        return messages || [];
                    },
                    flushPendingStopped: function(sessionId) {
                        if (chatApi && chatApi.flushPendingStopped) {
                            chatApi.flushPendingStopped(sessionId);
                        }
                    },
                    deriveTitleFromMessage: deriveTitleFromMessage,
                    getCurrentUser: function() { return currentUser; }
                },
                stateAccess: {
                    getCurrentSessionId: function() { return currentSessionId; },
                    setCurrentSessionId: function(value) { currentSessionId = value; },
                    getCurrentMessages: function() { return currentMessages; },
                    setCurrentMessages: function(messages) { currentMessages = messages || []; }
                },
                storagePreferenceKey: storagePreferenceKey,
                localHistoryUserIdKey: localHistoryUserIdKey
            });
            historyApi.loadInitialSessions();
            return;
        }

        if (sessionList) {
            sessionList.addEventListener('scroll', function() {
                if (isSessionListNearBottom()) {
                    loadMoreSessions();
                }
            });
            sessionList.addEventListener('click', function(e) {
                var item = findSessionItem(e.target);
                if (!item) {
                    return;
                }
                var sessionId = item.getAttribute('data-session-id');
                if (sessionId) {
                    loadSessionById(sessionId);
                }
            });
            sessionList.addEventListener('keydown', function(e) {
                if (e.key !== 'Enter' && e.key !== ' ') {
                    return;
                }
                var item = findSessionItem(e.target);
                if (!item) {
                    return;
                }
                var sessionId = item.getAttribute('data-session-id');
                if (sessionId) {
                    e.preventDefault();
                    loadSessionById(sessionId);
                }
            });
        }

        if (renameChatBtn) {
            renameChatBtn.addEventListener('click', function() {
                openRenameModal(currentSessionId);
            });
        }
        if (renameChatSaveBtn) {
            renameChatSaveBtn.addEventListener('click', function() {
                saveRename();
            });
        }
        if (renameChatCancelBtn) {
            renameChatCancelBtn.addEventListener('click', function() {
                closeRenameModal();
            });
        }
        if (renameChatCloseBtn) {
            renameChatCloseBtn.addEventListener('click', function() {
                closeRenameModal();
            });
        }
        if (renameChatInput) {
            renameChatInput.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    saveRename();
                } else if (e.key === 'Escape') {
                    closeRenameModal();
                }
            });
        }
        if (renameModal) {
            renameModal.addEventListener('click', function(e) {
                if (e.target === renameModal) {
                    closeRenameModal();
                }
            });
        }

        if (deleteChatConfirmBtn) {
            deleteChatConfirmBtn.addEventListener('click', function() {
                confirmDelete();
            });
        }
        if (deleteChatCancelBtn) {
            deleteChatCancelBtn.addEventListener('click', function() {
                closeDeleteModal();
            });
        }
        if (deleteChatCloseBtn) {
            deleteChatCloseBtn.addEventListener('click', function() {
                closeDeleteModal();
            });
        }
        if (deleteModal) {
            deleteModal.addEventListener('click', function(e) {
                if (e.target === deleteModal) {
                    closeDeleteModal();
                }
            });
        }
    }

    function isSessionListNearBottom() {
        if (!sessionList) {
            return false;
        }
        try {
            var threshold = 24;
            return (sessionList.scrollHeight - (sessionList.scrollTop + sessionList.clientHeight)) < threshold;
        } catch (err) {
            return false;
        }
    }

    function loadInitialSessions() {
        if (!sessionList) {
            return;
        }
        setSessionListEmptyMessage(null);
        if (!currentSessionId && chatApi && chatApi.renderLoadingChat) {
            chatApi.renderLoadingChat();
        }
        loadMoreSessions()
            .then(function() {
                if (!currentSessionId) {
                    if (sessions.length) {
                        loadSessionById(sessions[0].id);
                    } else {
                        if (chatApi && chatApi.renderEmptyChat) {
                            chatApi.renderEmptyChat();
                        }
                        if (chatApi && chatApi.updateChatTitle) {
                            chatApi.updateChatTitle(null);
                        }
                        syncJavaHistory([]);
                        setSessionListEmptyMessage("No history yet.");
                    }
                }
            })
            .catch(function(err) {
                console.error("Session load failed:", err);
                setSessionListEmptyMessage("Failed to load history.");
            });
    }

    function loadMoreSessions() {
        if (sessionsLoading || !sessionsHasMore) {
            return Promise.resolve([]);
        }
        sessionsLoading = true;
        setSessionListLoading(true);

        return storageListSessions({
            limit: SESSION_PAGE_SIZE,
            cursor: sessionsCursor
        })
            .then(function(result) {
                sessionsLoading = false;
                setSessionListLoading(false);
                var page = result && result.sessions ? result.sessions : [];
                var hasMore = result && typeof result.has_more === "boolean"
                    ? result.has_more
                    : page.length === SESSION_PAGE_SIZE;
                sessionsHasMore = hasMore;
                if (result && result.next_cursor) {
                    sessionsCursor = result.next_cursor;
                } else if (page.length) {
                    sessionsCursor = page[page.length - 1].created_at;
                }
                forEach(page, function(session) {
                    if (!session || !session.id || sessionById[session.id]) {
                        return;
                    }
                    sessionById[session.id] = session;
                    sessions.push(session);
                    renderSessionItem(session, false);
                });
                if (!sessions.length) {
                    setSessionListEmptyMessage("No history yet.");
                } else {
                    setSessionListEmptyMessage(null);
                }
                return page;
            })
            .catch(function(err) {
                sessionsLoading = false;
                setSessionListLoading(false);
                console.error("Session load failed:", err);
                reportHistoryError(err, "Failed to load history.");
                return [];
            });
    }

    function startNewSession() {
        if (historyApi && historyApi.startNewSession) {
            historyApi.startNewSession();
            return;
        }
        var previousSessionId = currentSessionId;
        var previousSession = previousSessionId ? sessionById[previousSessionId] : null;
        var previousMessages = currentMessages.slice();
        currentSessionId = null;
        currentMessages = [];
        if (chatApi && chatApi.updateChatTitle) {
            chatApi.updateChatTitle(null);
        }
        setActiveSessionItem(null);
        if (chatApi && chatApi.renderEmptyChat) {
            chatApi.renderEmptyChat();
        }
        syncJavaHistory([]);
        ensureActiveSession().catch(function(err) {
            console.error("Failed to start session:", err);
            if (!currentMessages.length && previousSessionId && previousSession) {
                currentSessionId = previousSessionId;
                currentMessages = previousMessages;
                if (chatApi && chatApi.updateChatTitle) {
                    chatApi.updateChatTitle(previousSession);
                }
                setActiveSessionItem(previousSessionId);
                if (chatApi && chatApi.renderChatHistory) {
                    chatApi.renderChatHistory(previousMessages);
                }
                syncJavaHistory(previousMessages);
            }
        });
    }

    function ensureActiveSession() {
        if (historyApi && historyApi.ensureActiveSession) {
            return historyApi.ensureActiveSession();
        }
        if (currentSessionId && sessionById[currentSessionId]) {
            return Promise.resolve(sessionById[currentSessionId]);
        }
        if (pendingSessionPromise) {
            return pendingSessionPromise;
        }
        pendingSessionPromise = storageCreateSession("")
            .then(function(session) {
                pendingSessionPromise = null;
                if (!session || !session.id) {
                    throw new Error("Invalid session.");
                }
                sessionById[session.id] = session;
                sessions.unshift(session);
                if (sessionList) {
                    renderSessionItem(session, true);
                }
                setSessionListEmptyMessage(null);
                currentSessionId = session.id;
                if (chatApi && chatApi.flushPendingStopped) {
                    chatApi.flushPendingStopped(session.id);
                }
                if (chatApi && chatApi.updateChatTitle) {
                    chatApi.updateChatTitle(session);
                }
                setActiveSessionItem(session.id);
                return session;
            })
            .catch(function(err) {
                pendingSessionPromise = null;
                reportHistoryError(err, "Unable to start a new chat.");
                throw err;
            });
        return pendingSessionPromise;
    }

    function loadSessionById(sessionId) {
        if (historyApi && historyApi.loadSessionById) {
            historyApi.loadSessionById(sessionId);
            return;
        }
        if (!sessionId || !sessionById[sessionId]) {
            return;
        }
        var session = sessionById[sessionId];
        currentSessionId = sessionId;
        if (chatApi && chatApi.flushPendingStopped) {
            chatApi.flushPendingStopped(sessionId);
        }
        currentMessages = [];
        if (chatApi && chatApi.updateChatTitle) {
            chatApi.updateChatTitle(session);
        }
        setActiveSessionItem(sessionId);
        switchMode('generate');
        if (chatApi && chatApi.renderLoadingChat) {
            chatApi.renderLoadingChat();
        }
        storageGetMessages(sessionId)
            .then(function(messages) {
                currentMessages = normalizeMessages(messages);
                if (chatApi && chatApi.applyStoppedCache) {
                    currentMessages = chatApi.applyStoppedCache(sessionId, currentMessages);
                }
                if (chatApi && chatApi.renderChatHistory) {
                    chatApi.renderChatHistory(currentMessages);
                }
                syncJavaHistory(currentMessages);
            })
            .catch(function(err) {
                console.error("Failed to load messages:", err);
                if (chatApi && chatApi.renderEmptyChat) {
                    chatApi.renderEmptyChat();
                }
                syncJavaHistory([]);
            });
    }

    function normalizeMessages(messages) {
        if (!messages || !messages.length) {
            return [];
        }
        var normalized = [];
        forEach(messages, function(msg) {
            if (!msg || !msg.content) {
                return;
            }
            var role = msg.role === "assistant" ? "assistant" : "user";
            normalized.push({
                id: msg.id || generateId(),
                role: role,
                content: msg.content,
                created_at: msg.created_at || new Date().toISOString()
            });
        });
        return normalized;
    }

    function syncJavaHistory(messages) {
        if (!window.javaBridge) {
            return;
        }
        var payload = [];
        if (messages && messages.length) {
            var slice = messages.slice(-CHAT_HISTORY_LIMIT);
            forEach(slice, function(msg) {
                if (!msg || !msg.content) {
                    return;
                }
                payload.push({
                    role: msg.role,
                    content: msg.content
                });
            });
        }
        if (!payload.length) {
            window.javaBridge("SYSTEM_CMD:CHAT_HISTORY_RESET");
            return;
        }
        var encoded = encodeURIComponent(JSON.stringify(payload));
        window.javaBridge("SYSTEM_CMD:CHAT_HISTORY_SET:" + encoded);
    }

    function autoTitleSession(session, text) {
        if (historyApi && historyApi.autoTitleSession) {
            historyApi.autoTitleSession(session, text);
            return;
        }
        if (!session || session.title) {
            return;
        }
        var title = deriveTitleFromMessage(text);
        if (!title) {
            return;
        }
        renameSessionTitle(session.id, title, { silent: true });
    }

    function persistMessage(sessionId, message) {
        if (historyApi && historyApi.persistMessage) {
            historyApi.persistMessage(sessionId, message);
            return;
        }
        if (!sessionId || !message) {
            return;
        }
        storageAppendMessage(sessionId, message)
            .catch(function(err) {
                console.error("Message sync failed:", err);
                reportHistoryError(err, "Failed to save message.");
            });
    }

    function renameSessionTitle(sessionId, title, options) {
        if (!sessionId) {
            return;
        }
        var cleaned = String(title || "").trim();
        if (!cleaned) {
            return;
        }
        storageRenameSession(sessionId, cleaned)
            .then(function(updated) {
                var session = updated || sessionById[sessionId];
                if (!session) {
                    return;
                }
                session.title = cleaned;
                sessionById[sessionId] = session;
                forEach(sessions, function(item, index) {
                    if (item && item.id === sessionId) {
                        sessions[index] = session;
                    }
                });
                updateSessionItem(session);
                if (currentSessionId === sessionId && chatApi && chatApi.updateChatTitle) {
                    chatApi.updateChatTitle(session);
                }
                if (!options || !options.silent) {
                    closeRenameModal();
                }
            })
            .catch(function(err) {
                console.error("Rename failed:", err);
                if (!options || !options.silent) {
                    setRenameMessage("Rename failed. Try again.", "error");
                }
            });
    }

    function deleteSession(sessionId) {
        if (!sessionId) {
            return Promise.resolve();
        }
        return storageDeleteSession(sessionId)
            .then(function() {
                var wasActive = currentSessionId === sessionId;
                delete sessionById[sessionId];
                sessions = sessions.filter(function(item) {
                    return item && item.id !== sessionId;
                });
                if (sessionList) {
                    var item = sessionList.querySelector('[data-session-id="' + sessionId + '"]');
                    if (item && item.parentNode) {
                        item.parentNode.removeChild(item);
                    }
                }
                if (wasActive) {
                    currentSessionId = null;
                    currentMessages = [];
                    if (chatApi && chatApi.updateChatTitle) {
                        chatApi.updateChatTitle(null);
                    }
                    setActiveSessionItem(null);
                    if (chatApi && chatApi.renderEmptyChat) {
                        chatApi.renderEmptyChat();
                    }
                    syncJavaHistory([]);
                    if (sessions.length) {
                        loadSessionById(sessions[0].id);
                    }
                }
                if (!sessions.length) {
                    setSessionListEmptyMessage("No history yet.");
                } else {
                    setSessionListEmptyMessage(null);
                }
            });
    }

    function openRenameModal(sessionId) {
        var targetId = sessionId || currentSessionId;
        if (!renameModal || !targetId) {
            return;
        }
        renameTargetSessionId = targetId;
        var session = sessionById[targetId];
        if (renameChatInput) {
            renameChatInput.value = session ? (session.title || "") : "";
            renameChatInput.placeholder = "New chat title";
        }
        setRenameMessage("", "");
        renameModal.removeAttribute('hidden');
        renameModal.style.display = 'flex';
        setTimeout(function() {
            if (renameChatInput) {
                renameChatInput.focus();
            }
        }, 0);
    }

    function closeRenameModal() {
        if (!renameModal) {
            return;
        }
        renameModal.setAttribute('hidden', '');
        renameModal.style.display = 'none';
        setRenameMessage("", "");
        renameTargetSessionId = null;
    }

    function setRenameMessage(message, type) {
        if (!renameChatMessage) {
            return;
        }
        if (!message) {
            renameChatMessage.textContent = "";
            renameChatMessage.setAttribute("hidden", "");
            renameChatMessage.className = "auth-message modal-message";
            return;
        }
        renameChatMessage.textContent = message;
        renameChatMessage.className = "auth-message modal-message" + (type ? " " + type : "");
        renameChatMessage.removeAttribute("hidden");
    }

    function openDeleteModal(sessionId) {
        var targetId = sessionId || currentSessionId;
        if (!deleteModal || !targetId) {
            return;
        }
        deleteTargetSessionId = targetId;
        setDeleteMessage("", "");
        deleteModal.removeAttribute('hidden');
        deleteModal.style.display = 'flex';
    }

    function closeDeleteModal() {
        if (!deleteModal) {
            return;
        }
        deleteModal.setAttribute('hidden', '');
        deleteModal.style.display = 'none';
        setDeleteMessage("", "");
        deleteTargetSessionId = null;
        if (deleteChatConfirmBtn) {
            deleteChatConfirmBtn.disabled = false;
        }
        if (deleteChatCancelBtn) {
            deleteChatCancelBtn.disabled = false;
        }
    }

    function setDeleteMessage(message, type) {
        if (!deleteChatMessage) {
            return;
        }
        if (!message) {
            deleteChatMessage.textContent = "";
            deleteChatMessage.setAttribute("hidden", "");
            deleteChatMessage.className = "auth-message modal-message";
            return;
        }
        deleteChatMessage.textContent = message;
        deleteChatMessage.className = "auth-message modal-message" + (type ? " " + type : "");
        deleteChatMessage.removeAttribute("hidden");
    }

    function confirmDelete() {
        var targetId = deleteTargetSessionId || currentSessionId;
        if (!targetId) {
            return;
        }
        if (deleteChatConfirmBtn) {
            deleteChatConfirmBtn.disabled = true;
        }
        if (deleteChatCancelBtn) {
            deleteChatCancelBtn.disabled = true;
        }
        deleteSession(targetId)
            .then(function() {
                closeDeleteModal();
            })
            .catch(function(err) {
                console.error("Delete failed:", err);
                if (deleteChatConfirmBtn) {
                    deleteChatConfirmBtn.disabled = false;
                }
                if (deleteChatCancelBtn) {
                    deleteChatCancelBtn.disabled = false;
                }
                setDeleteMessage("Delete failed. Try again.", "error");
            });
    }

    function saveRename() {
        var targetId = renameTargetSessionId || currentSessionId;
        if (!targetId || !renameChatInput) {
            return;
        }
        var title = renameChatInput.value.trim();
        if (!title) {
            setRenameMessage("Enter a title.", "error");
            return;
        }
        renameSessionTitle(targetId, title, { silent: false });
    }

    function loadJsonFromLegacyStorage(key, fallback) {
        try {
            var raw = localStorage.getItem(key);
            if (!raw) {
                return fallback;
            }
            return JSON.parse(raw);
        } catch (err) {
            return fallback;
        }
    }

    function removeLegacyStorage(key) {
        try {
            localStorage.removeItem(key);
        } catch (err) {
            /* ignore */
        }
    }

    function getLegacyStoragePreference() {
        try {
            return normalizeStoragePreference(localStorage.getItem(storagePreferenceKey));
        } catch (err) {
            return null;
        }
    }

    function clearLegacyStorageData(sessions, preference) {
        removeLegacyStorage("genai.sessions");
        if (sessions && sessions.length) {
            forEach(sessions, function(session) {
                if (session && session.id) {
                    removeLegacyStorage("genai.messages." + session.id);
                }
            });
        }
        removeLegacyStorage(storagePreferenceKey);
    }

    function migrateLegacyStorage() {
        if (!window.localHistoryBridge) {
            return Promise.resolve();
        }
        var userId = getLocalHistoryUserId();
        if (!userId) {
            return Promise.resolve();
        }
        var sessions = loadJsonFromLegacyStorage("genai.sessions", []);
        var preference = getLegacyStoragePreference();
        var messagesBySession = {};
        var hasMessages = false;
        if (sessions && sessions.length) {
            forEach(sessions, function(session) {
                if (!session || !session.id) {
                    return;
                }
                var messages = loadJsonFromLegacyStorage("genai.messages." + session.id, []);
                if (messages && messages.length) {
                    messagesBySession[session.id] = messages;
                    hasMessages = true;
                }
            });
        }
        if ((!sessions || !sessions.length) && !preference && !hasMessages) {
            return Promise.resolve();
        }
        return invokeLocalHistory({
            action: "import_storage",
            user_id: userId,
            sessions: sessions || [],
            messages: messagesBySession,
            storage_preference: preference || null
        }).then(function() {
            clearLegacyStorageData(sessions, preference);
        }).catch(function(err) {
            console.error("Legacy history migration failed:", err);
        });
    }

    function ensureLocalHistoryReady() {
        if (localHistoryMigrationAttempted) {
            return localHistoryMigrationPromise || Promise.resolve();
        }
        localHistoryMigrationAttempted = true;
        localHistoryMigrationPromise = migrateLegacyStorage().then(function(result) {
            localHistoryMigrationPromise = null;
            return result;
        });
        return localHistoryMigrationPromise;
    }

    function storageListSessions(options) {
        return storageMode === "cloud"
            ? listSessionsCloud(options)
            : ensureLocalHistoryReady().then(function() { return listSessionsLocal(options); });
    }

    function storageCreateSession(title) {
        return storageMode === "cloud"
            ? createSessionCloud(title)
            : ensureLocalHistoryReady().then(function() { return createSessionLocal(title); });
    }

    function storageGetMessages(sessionId) {
        return storageMode === "cloud"
            ? getMessagesCloud(sessionId)
            : ensureLocalHistoryReady().then(function() { return getMessagesLocal(sessionId); });
    }

    function storageAppendMessage(sessionId, message) {
        return storageMode === "cloud"
            ? appendMessageCloud(sessionId, message)
            : ensureLocalHistoryReady().then(function() { return appendMessageLocal(sessionId, message); });
    }

    function storageRenameSession(sessionId, title) {
        return storageMode === "cloud"
            ? renameSessionCloud(sessionId, title)
            : ensureLocalHistoryReady().then(function() { return renameSessionLocal(sessionId, title); });
    }

    function storageDeleteSession(sessionId) {
        return storageMode === "cloud"
            ? deleteSessionCloud(sessionId)
            : ensureLocalHistoryReady().then(function() { return deleteSessionLocal(sessionId); });
    }

    function getHistoryFunctionName() {
        return supabaseConfig.historyFunctionName || supabaseConfig.historyFunction || "chat-history";
    }

    function shouldFallbackToFetch(err) {
        if (!err) {
            return false;
        }
        var message = err.message || err.error_description || String(err);
        return message.indexOf("x-supabase-client-platform") >= 0 ||
            message.indexOf("CORS") >= 0 ||
            message.indexOf("Failed to send a request") >= 0 ||
            message.indexOf("Failed to fetch") >= 0 ||
            message.indexOf("FunctionsFetchError") >= 0;
    }

    function buildHistoryFunctionUrl() {
        var base = supabaseConfig.url || "";
        if (!base) {
            return "";
        }
        if (base.charAt(base.length - 1) === "/") {
            base = base.slice(0, -1);
        }
        return base + "/functions/v1/" + getHistoryFunctionName();
    }

    function invokeHistoryFunctionSupabase(client, payload) {
        return getAuthToken(client)
            .then(function(token) {
                var body = payload || {};
                body.access_token = token;
                return client.functions.invoke(getHistoryFunctionName(), {
                    body: body,
                    headers: {
                        Authorization: "Bearer " + token,
                        apikey: supabaseConfig.anonKey || ""
                    }
                });
            })
            .then(function(res) {
                if (res.error) {
                    if (isAuthError(res.error)) {
                        handleAuthFailure(res.error);
                    }
                    throw res.error;
                }
                return res.data || {};
            })
            .catch(function(err) {
                handleAuthFailure(err);
                throw err;
            });
    }

    function invokeHistoryFunctionFetch(client, payload) {
        var url = buildHistoryFunctionUrl();
        if (!url) {
            return Promise.reject(new Error("Supabase config missing."));
        }
        return getAuthToken(client)
            .then(function(token) {
                var body = payload || {};
                body.access_token = token;
                var headers = {
                    "Content-Type": "application/json",
                    Authorization: "Bearer " + token
                };
                if (supabaseConfig.anonKey) {
                    headers.apikey = supabaseConfig.anonKey;
                }
                return fetch(url, {
                    method: "POST",
                    headers: headers,
                    body: JSON.stringify(body)
                });
            })
            .then(function(res) {
                if (!res.ok) {
                    return res.text().then(function(text) {
                        var message = text || res.statusText || "Request failed.";
                        throw new Error(message);
                    });
                }
                return res.json();
            })
            .catch(function(err) {
                handleAuthFailure(err);
                throw err;
            });
    }

    function invokeHistoryFunction(payload) {
        var client = getSupabaseClient();
        if (!client || !client.functions || !client.functions.invoke) {
            return Promise.reject(new Error("Supabase functions unavailable."));
        }
        var mode = String(supabaseConfig.historyInvokeMode || "").toLowerCase();
        if (mode === "fetch" || mode === "direct") {
            return invokeHistoryFunctionFetch(client, payload);
        }
        return invokeHistoryFunctionSupabase(client, payload)
            .catch(function(err) {
                if (shouldFallbackToFetch(err)) {
                    return invokeHistoryFunctionFetch(client, payload);
                }
                throw err;
            });
    }

    function listSessionsCloud(options) {
        var limit = options && options.limit ? options.limit : SESSION_PAGE_SIZE;
        var cursor = options && options.cursor ? options.cursor : null;
        return invokeHistoryFunction({
            action: "list_sessions",
            limit: limit,
            cursor: cursor
        });
    }

    function createSessionCloud(title) {
        return invokeHistoryFunction({
            action: "create_session",
            title: title || ""
        }).then(function(data) {
            return data.session || null;
        });
    }

    function getMessagesCloud(sessionId) {
        return invokeHistoryFunction({
            action: "get_messages",
            session_id: sessionId
        }).then(function(data) {
            return data.messages || [];
        });
    }

    function appendMessageCloud(sessionId, message) {
        return invokeHistoryFunction({
            action: "append_message",
            session_id: sessionId,
            role: message.role,
            content: message.content
        }).then(function(data) {
            return data.message || message;
        });
    }

    function renameSessionCloud(sessionId, title) {
        return invokeHistoryFunction({
            action: "rename_session",
            session_id: sessionId,
            title: title
        }).then(function(data) {
            return data.session || null;
        });
    }

    function deleteSessionCloud(sessionId) {
        return invokeHistoryFunction({
            action: "delete_session",
            session_id: sessionId
        }).then(function(data) {
            return data.session_id || sessionId;
        });
    }

    function listSessionsLocal(options) {
        var limit = options && options.limit ? options.limit : SESSION_PAGE_SIZE;
        var cursor = options && options.cursor ? options.cursor : null;
        return invokeLocalHistory({
            action: "list_sessions",
            user_id: getLocalHistoryUserId(),
            limit: limit,
            cursor: cursor
        }).then(function(data) {
            return {
                sessions: data.sessions || [],
                next_cursor: data.next_cursor || null,
                has_more: !!data.has_more
            };
        });
    }

    function createSessionLocal(title) {
        return invokeLocalHistory({
            action: "create_session",
            user_id: getLocalHistoryUserId(),
            title: title || ""
        }).then(function(data) {
            return data.session || null;
        });
    }

    function getMessagesLocal(sessionId) {
        return invokeLocalHistory({
            action: "get_messages",
            user_id: getLocalHistoryUserId(),
            session_id: sessionId
        }).then(function(data) {
            return data.messages || [];
        });
    }

    function appendMessageLocal(sessionId, message) {
        return invokeLocalHistory({
            action: "append_message",
            user_id: getLocalHistoryUserId(),
            session_id: sessionId,
            message: message
        }).then(function(data) {
            return data.message || message;
        });
    }

    function renameSessionLocal(sessionId, title) {
        return invokeLocalHistory({
            action: "rename_session",
            user_id: getLocalHistoryUserId(),
            session_id: sessionId,
            title: title
        }).then(function(data) {
            return data.session || null;
        });
    }

    function deleteSessionLocal(sessionId) {
        return invokeLocalHistory({
            action: "delete_session",
            user_id: getLocalHistoryUserId(),
            session_id: sessionId
        }).then(function(data) {
            return data.session_id || sessionId;
        });
    }

    /* --- Workspace Signals Logic --- */
    function setChecksState(state) {
        if (!wsChecks) return;
        wsChecks.classList.remove("success", "warning", "error");
        if (state) {
            wsChecks.classList.add(state);
        }
    }

    function setStatusState(state) {
        if (!wsStatus) return;
        wsStatus.classList.remove("idle", "loading", "error", "ready");
        wsStatus.classList.add(state || "idle");
    }

    function setChecksDetailAvailability(isAvailable) {
        var enabled = !!isAvailable;
        if (checksSignal) {
            if (enabled) {
                checksSignal.classList.add("signal-clickable");
                checksSignal.setAttribute("role", "button");
                checksSignal.setAttribute("tabindex", "0");
                checksSignal.setAttribute("aria-label", "View validation details");
            } else {
                checksSignal.classList.remove("signal-clickable");
                checksSignal.removeAttribute("role");
                checksSignal.removeAttribute("tabindex");
                checksSignal.removeAttribute("aria-label");
            }
        }
        if (checksDetailLink) {
            checksDetailLink.style.display = enabled ? "inline-flex" : "none";
        }
    }

    // Call this from Java to update signals
    window.updateWorkspaceSignals = function(data) {
        if (!data) return;
        
        if (wsActiveView) {
            wsActiveView.textContent = data.activeView || "None";
        }
        lastActiveViewTitle = data.activeView || "None";
        if (wsSelectedCount) {
            var label = data.selectedCount === 1 ? "1 node" : data.selectedCount + " nodes";
            wsSelectedCount.textContent = label;
        }
        lastSelectedCount = typeof data.selectedCount === "number" ? data.selectedCount : 0;
        if (wsChecks) {
            wsChecks.textContent = data.checksText || "Unknown";
            setChecksState(data.checksState);
        }
        lastChecksSummary = data.checksText || "Unknown";
        var showChecksDetail = !!(data.checksState && data.checksState !== "success");
        if (data.checksDetail) {
            lastChecksDetail = data.checksDetail;
        } else if (showChecksDetail) {
            lastChecksDetail = ""; // Clear the detail so it shows "No issues found"
        }
        setChecksDetailAvailability(showChecksDetail);
        if (wsStatus) {
            wsStatus.textContent = data.statusText || "Idle";
            setStatusState(data.statusState);
        }
    };

    function buildChecksAiSectionHtml() {
        return (
            "<div class='checks-ai-section' id='checksAiSection'>" +
                "<div class='checks-ai-title'>AI Explanation &amp; Suggestions</div>" +
                "<div class='checks-ai-status' id='checksAiStatus'>Waiting...</div>" +
                "<pre class='checks-ai-output' id='checksAiOutput'></pre>" +
            "</div>"
        );
    }

    function extractPlainTextFromHtml(html) {
        if (!html) return "";
        try {
            var temp = document.createElement("div");
            temp.innerHTML = html;
            var text = temp.textContent || temp.innerText || "";
            return String(text || "").replace(/\s+\n/g, "\n").replace(/\n\s+/g, "\n").trim();
        } catch (e) {
            return "";
        }
    }

    function getChecksAiLanguage() {
        if (explainLanguageSelect && explainLanguageSelect.value) {
            return explainLanguageSelect.value;
        }
        return "en";
    }

    function requestChecksAiExplanation() {
        var statusEl = document.getElementById("checksAiStatus");
        var outputEl = document.getElementById("checksAiOutput");
        if (!statusEl || !outputEl) {
            return;
        }

        var issuesText = extractPlainTextFromHtml(lastChecksDetail);
        if (!issuesText) {
            statusEl.textContent = "No validation issues available for AI explanation.";
            outputEl.textContent = "";
            return;
        }

        lastChecksAiRequestId = (lastChecksAiRequestId || 0) + 1;
        var requestId = lastChecksAiRequestId;
        statusEl.textContent = "Generating AI suggestions...";
        outputEl.textContent = "";

        if (!window.requestChecksAiExplanation) {
            statusEl.textContent = "AI suggestions are not available in this build.";
            return;
        }

        try {
            window.requestChecksAiExplanation(
                requestId,
                lastActiveViewTitle || (wsActiveView ? wsActiveView.textContent : ""),
                lastSelectedCount || 0,
                lastChecksSummary || (wsChecks ? wsChecks.textContent : ""),
                issuesText,
                getChecksAiLanguage()
            );
        } catch (err) {
            statusEl.textContent = "AI call failed: " + (err && err.message ? err.message : String(err));
        }
    }

    window.updateChecksAiExplanation = function(data) {
        if (!data) return;
        if (typeof data.requestId === "number" && data.requestId !== lastChecksAiRequestId) {
            return;
        }
        var statusEl = document.getElementById("checksAiStatus");
        var outputEl = document.getElementById("checksAiOutput");
        if (!statusEl || !outputEl) {
            return;
        }
        statusEl.textContent = data.message || "";
        if (data.state === "ready") {
            outputEl.textContent = data.content || "";
        } else if (data.state === "error") {
            outputEl.textContent = "";
        }
    };

    window.showChecksDetail = function() {
        if (checksDetailModal && checksDetailContent) {
            var html = "";
            if (lastChecksDetail && lastChecksDetail.trim().length > 0) {
                html += lastChecksDetail;
            } else {
                html += "<p style='padding:10px;margin:0;'>No issues found.</p>";
            }
            html += buildChecksAiSectionHtml();
            checksDetailContent.innerHTML = html;
            checksDetailModal.removeAttribute('hidden');
            checksDetailModal.style.display = 'flex';
            requestChecksAiExplanation();
        }
    };

    // Initialize checks detail signal handlers
    if (checksSignal) {
        checksSignal.addEventListener('click', function() {
            if (checksSignal.classList.contains("signal-clickable")) {
                window.showChecksDetail();
            }
        });
        checksSignal.addEventListener('keydown', function(event) {
            if (!checksSignal.classList.contains("signal-clickable")) {
                return;
            }
            if (event.key === "Enter" || event.key === " " || event.key === "Spacebar") {
                event.preventDefault();
                window.showChecksDetail();
            }
        });
    }
    setChecksDetailAvailability(false);
    if (closeChecksDetailBtn) {
        closeChecksDetailBtn.addEventListener('click', function() {
            if (checksDetailModal) {
                checksDetailModal.setAttribute('hidden', '');
                checksDetailModal.style.display = 'none';
            }
        });
    }
    if (closeChecksDetailBtn2) {
        closeChecksDetailBtn2.addEventListener('click', function() {
            if (checksDetailModal) {
                checksDetailModal.setAttribute('hidden', '');
                checksDetailModal.style.display = 'none';
            }
        });
    }

    window.updateExplainContext = function(data) {
        if (!data) {
            return;
        }
        if (explainContextLine) {
            var selectionLabel = data.hasSelection
                ? data.selectedCount + " selected"
                : "no selection";
            explainContextLine.textContent = (data.viewName || "Unknown view") +
                " (" + (data.viewType || "Unknown") + ") - " + selectionLabel;
        }
        if (explainLanguageSelect && data.language) {
            explainLanguageSelect.value = data.language;
        }
    };

    window.updateExplainResult = function(data) {
        if (!data) {
            return;
        }
        explainSimplified = data.simplified || "";
        explainDetailed = data.detailed || "";
        explainSummary = data.summary || "";

        if (explainSummaryText) {
            explainSummaryText.textContent = explainSummary || "No summary yet.";
        }
        renderExplainCurrent();
    };

    window.updateExplainState = function(data) {
        if (!data || !explainStateLine) {
            return;
        }
        explainStateLine.textContent = data.message || "Idle";
        explainStateLine.classList.remove("idle", "loading", "error", "ready");
        if (data.state) {
            explainStateLine.classList.add(data.state);
        } else {
            explainStateLine.classList.add("idle");
        }
    };

})();
