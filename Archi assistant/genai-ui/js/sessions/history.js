
/* global window */
(function() {
    window.GenAI = window.GenAI || {};
    var utils = window.GenAI.utils || {};

    var forEach = utils.forEach || function(list, callback) {
        for (var i = 0; i < list.length; i++) {
            callback(list[i]);
        }
    };

    var generateId = utils.generateId || function() {
        return "id-" + Date.now() + "-" + Math.floor(Math.random() * 1000000);
    };

    var state = {
        dom: {},
        supabaseConfig: {},
        authApi: null,
        storageMode: "cloud",
        sessions: [],
        sessionById: {},
        sessionsCursor: null,
        sessionsHasMore: true,
        sessionsLoading: false,
        sessionsInitialized: false,
        pendingSessionPromise: null,
        renameTargetSessionId: null,
        deleteTargetSessionId: null,
        localHistoryMigrationAttempted: false,
        localHistoryMigrationPromise: null,
        localHistoryUserId: null,
        callbacks: {},
        stateAccess: {}
    };

    function getCurrentSessionId() {
        return state.stateAccess.getCurrentSessionId ? state.stateAccess.getCurrentSessionId() : null;
    }

    function setCurrentSessionId(value) {
        if (state.stateAccess.setCurrentSessionId) {
            state.stateAccess.setCurrentSessionId(value);
        }
    }

    function getCurrentMessages() {
        return state.stateAccess.getCurrentMessages ? state.stateAccess.getCurrentMessages() : [];
    }

    function setCurrentMessages(messages) {
        if (state.stateAccess.setCurrentMessages) {
            state.stateAccess.setCurrentMessages(messages || []);
        }
    }

    function setSessionListLoading(isLoading) {
        if (!state.dom.sessionListLoading) {
            return;
        }
        if (isLoading) {
            state.dom.sessionListLoading.removeAttribute('hidden');
        } else {
            state.dom.sessionListLoading.setAttribute('hidden', '');
        }
    }

    function setSessionListEmptyMessage(message) {
        if (!state.dom.sessionListEmpty) {
            return;
        }
        if (!message) {
            state.dom.sessionListEmpty.setAttribute('hidden', '');
            state.dom.sessionListEmpty.textContent = '';
            return;
        }
        state.dom.sessionListEmpty.textContent = message;
        state.dom.sessionListEmpty.removeAttribute('hidden');
    }

    function formatHistoryError(err, fallback) {
        if (!err) {
            return fallback || "Failed to sync history.";
        }
        var message = err.message || err.error_description || String(err);
        return message || fallback || "Failed to sync history.";
    }

    function reportHistoryError(err, fallback) {
        var message = formatHistoryError(err, fallback);
        console.error(message, err || "");
        setSessionListEmptyMessage(message);
    }

    function resetSessionState() {
        state.sessions = [];
        state.sessionById = {};
        state.sessionsCursor = null;
        state.sessionsHasMore = true;
        state.sessionsLoading = false;
        setCurrentSessionId(null);
        setCurrentMessages([]);
        state.pendingSessionPromise = null;
        if (state.dom.sessionList) {
            state.dom.sessionList.innerHTML = "";
        }
        setSessionListLoading(false);
        setSessionListEmptyMessage("No history yet.");
        if (state.dom.renameChatBtn) {
            state.dom.renameChatBtn.disabled = true;
        }
    }
    function renderSessionItem(session, prepend) {
        if (!state.dom.sessionList || !session) {
            return null;
        }
        var item = document.createElement('button');
        item.className = 'session-item';
        item.setAttribute('data-session-id', session.id);

        var title = document.createElement('span');
        title.className = 'session-title';
        title.textContent = session.title || 'New chat';

        var actions = document.createElement('span');
        actions.className = 'session-actions';

        var edit = document.createElement('button');
        edit.className = 'session-rename';
        edit.setAttribute('type', 'button');
        edit.setAttribute('title', 'Rename');
        edit.setAttribute('aria-label', 'Rename session');
        edit.innerHTML = '<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M4 20h4l10-10-4-4-10 10v4z\"/><path d=\"M13.5 5.5l4 4\"/></svg>';

        var del = document.createElement('button');
        del.className = 'session-delete';
        del.setAttribute('type', 'button');
        del.setAttribute('title', 'Delete');
        del.setAttribute('aria-label', 'Delete session');
        del.innerHTML = '<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M6 7h12\"/><path d=\"M9 7V5h6v2\"/><path d=\"M8 7l1 12h6l1-12\"/></svg>';

        actions.appendChild(edit);
        actions.appendChild(del);

        item.appendChild(title);
        item.appendChild(actions);

        if (prepend && state.dom.sessionList.firstChild) {
            state.dom.sessionList.insertBefore(item, state.dom.sessionList.firstChild);
        } else {
            state.dom.sessionList.appendChild(item);
        }
        return item;
    }

    function updateSessionItem(session) {
        if (!state.dom.sessionList || !session) {
            return;
        }
        var item = state.dom.sessionList.querySelector('[data-session-id="' + session.id + '"]');
        if (!item) {
            return;
        }
        var title = item.querySelector('.session-title');
        if (title) {
            title.textContent = session.title || 'New chat';
        }
    }

    function setActiveSessionItem(sessionId) {
        if (!state.dom.sessionList) {
            return;
        }
        var items = state.dom.sessionList.querySelectorAll('[data-session-id]');
        forEach(items, function(item) {
            if (item.getAttribute('data-session-id') === sessionId) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
        if (state.dom.renameChatBtn) {
            state.dom.renameChatBtn.disabled = !sessionId;
        }
    }

    function findSessionItem(target) {
        if (!target) {
            return null;
        }
        if (target.getAttribute && target.getAttribute('data-session-id')) {
            return target;
        }
        if (target.closest) {
            return target.closest('[data-session-id]');
        }
        return null;
    }

    function isSessionListNearBottom() {
        if (!state.dom.sessionList) {
            return false;
        }
        return (state.dom.sessionList.scrollTop + state.dom.sessionList.clientHeight + 40) >= state.dom.sessionList.scrollHeight;
    }

    function loadInitialSessions() {
        if (state.sessionsLoading) {
            return;
        }
        setSessionListEmptyMessage(null);
        state.sessions = [];
        state.sessionById = {};
        state.sessionsCursor = null;
        state.sessionsHasMore = true;
        if (state.dom.sessionList) {
            state.dom.sessionList.innerHTML = '';
        }
        loadMoreSessions()
            .then(function(sessions) {
                if (!sessions || !sessions.length) {
                    if (state.callbacks.syncJavaHistory) {
                        state.callbacks.syncJavaHistory([]);
                    }
                    setSessionListEmptyMessage("No history yet.");
                    return;
                }
                var currentSessionId = getCurrentSessionId();
                if (!currentSessionId && sessions[0] && sessions[0].id) {
                    loadSessionById(sessions[0].id);
                }
            })
            .catch(function(err) {
                console.error("Initial session load failed:", err);
                setSessionListEmptyMessage("Failed to load history.");
            });
    }

    function loadMoreSessions() {
        if (!state.sessionsHasMore || state.sessionsLoading) {
            return Promise.resolve([]);
        }
        state.sessionsLoading = true;
        setSessionListLoading(true);
        return storageListSessions({
            limit: 15,
            cursor: state.sessionsCursor
        })
            .then(function(res) {
                state.sessionsLoading = false;
                setSessionListLoading(false);
                var sessions = res && res.sessions ? res.sessions : [];
                var nextCursor = res && res.next_cursor ? res.next_cursor : null;
                var hasMore = res && res.has_more;

                state.sessionsCursor = nextCursor;
                state.sessionsHasMore = !!hasMore;

                forEach(sessions, function(session) {
                    if (!session || !session.id) {
                        return;
                    }
                    if (!state.sessionById[session.id]) {
                        state.sessionById[session.id] = session;
                        state.sessions.push(session);
                        renderSessionItem(session, false);
                    }
                });

                if (!state.sessions.length) {
                    setSessionListEmptyMessage("No history yet.");
                } else {
                    setSessionListEmptyMessage(null);
                }
                return sessions;
            })
            .catch(function(err) {
                state.sessionsLoading = false;
                setSessionListLoading(false);
                reportHistoryError(err, "Failed to load history.");
                return [];
            });
    }

    function startNewSession() {
        var previousSessionId = getCurrentSessionId();
        var previousSession = previousSessionId ? state.sessionById[previousSessionId] : null;
        var previousMessages = getCurrentMessages().slice();

        setCurrentSessionId(null);
        setCurrentMessages([]);
        if (state.callbacks.updateChatTitle) {
            state.callbacks.updateChatTitle(null);
        }
        setActiveSessionItem(null);
        if (state.callbacks.renderEmptyChat) {
            state.callbacks.renderEmptyChat();
        }
        if (state.callbacks.syncJavaHistory) {
            state.callbacks.syncJavaHistory([]);
        }

        ensureActiveSession().catch(function(err) {
            console.error("Failed to start session:", err);
            if (!getCurrentMessages().length && previousSessionId && previousSession) {
                setCurrentSessionId(previousSessionId);
                setCurrentMessages(previousMessages);
                if (state.callbacks.updateChatTitle) {
                    state.callbacks.updateChatTitle(previousSession);
                }
                setActiveSessionItem(previousSessionId);
                if (state.callbacks.renderChatHistory) {
                    state.callbacks.renderChatHistory(previousMessages);
                }
                if (state.callbacks.syncJavaHistory) {
                    state.callbacks.syncJavaHistory(previousMessages);
                }
            }
        });
    }

    function ensureActiveSession() {
        var currentSessionId = getCurrentSessionId();
        if (currentSessionId && state.sessionById[currentSessionId]) {
            return Promise.resolve(state.sessionById[currentSessionId]);
        }
        if (state.pendingSessionPromise) {
            return state.pendingSessionPromise;
        }
        state.pendingSessionPromise = storageCreateSession("")
            .then(function(session) {
                state.pendingSessionPromise = null;
                if (!session || !session.id) {
                    throw new Error("Invalid session.");
                }
                state.sessionById[session.id] = session;
                state.sessions.unshift(session);
                renderSessionItem(session, true);
                setSessionListEmptyMessage(null);
                setCurrentSessionId(session.id);
                if (state.callbacks.flushPendingStopped) {
                    state.callbacks.flushPendingStopped(session.id);
                }
                if (state.callbacks.updateChatTitle) {
                    state.callbacks.updateChatTitle(session);
                }
                setActiveSessionItem(session.id);
                return session;
            })
            .catch(function(err) {
                state.pendingSessionPromise = null;
                reportHistoryError(err, "Unable to start a new chat.");
                throw err;
            });
        return state.pendingSessionPromise;
    }

    function loadSessionById(sessionId) {
        if (!sessionId || !state.sessionById[sessionId]) {
            return;
        }
        var session = state.sessionById[sessionId];
        setCurrentSessionId(sessionId);
        if (state.callbacks.flushPendingStopped) {
            state.callbacks.flushPendingStopped(sessionId);
        }
        setCurrentMessages([]);
        if (state.callbacks.updateChatTitle) {
            state.callbacks.updateChatTitle(session);
        }
        setActiveSessionItem(sessionId);
        if (state.callbacks.switchMode) {
            state.callbacks.switchMode('generate');
        }
        if (state.callbacks.renderLoadingChat) {
            state.callbacks.renderLoadingChat();
        }
        storageGetMessages(sessionId)
            .then(function(messages) {
                var normalized = normalizeMessages(messages);
                if (state.callbacks.applyStoppedCache) {
                    normalized = state.callbacks.applyStoppedCache(sessionId, normalized);
                }
                setCurrentMessages(normalized);
                if (state.callbacks.renderChatHistory) {
                    state.callbacks.renderChatHistory(normalized);
                }
                if (state.callbacks.syncJavaHistory) {
                    state.callbacks.syncJavaHistory(normalized);
                }
            })
            .catch(function(err) {
                console.error("Failed to load messages:", err);
                if (state.callbacks.renderEmptyChat) {
                    state.callbacks.renderEmptyChat();
                }
                if (state.callbacks.syncJavaHistory) {
                    state.callbacks.syncJavaHistory([]);
                }
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

    function autoTitleSession(session, text) {
        if (!session || session.title) {
            return;
        }
        if (!state.callbacks.deriveTitleFromMessage) {
            return;
        }
        var title = state.callbacks.deriveTitleFromMessage(text);
        if (!title) {
            return;
        }
        renameSessionTitle(session.id, title, { silent: true });
    }

    function persistMessage(sessionId, message) {
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
                var session = updated || state.sessionById[sessionId];
                if (!session) {
                    return;
                }
                session.title = cleaned;
                state.sessionById[sessionId] = session;
                forEach(state.sessions, function(item, index) {
                    if (item && item.id === sessionId) {
                        state.sessions[index] = session;
                    }
                });
                updateSessionItem(session);
                if (getCurrentSessionId() === sessionId && state.callbacks.updateChatTitle) {
                    state.callbacks.updateChatTitle(session);
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
                var wasActive = getCurrentSessionId() === sessionId;
                delete state.sessionById[sessionId];
                state.sessions = state.sessions.filter(function(item) {
                    return item && item.id !== sessionId;
                });
                if (state.dom.sessionList) {
                    var item = state.dom.sessionList.querySelector('[data-session-id="' + sessionId + '"]');
                    if (item && item.parentNode) {
                        item.parentNode.removeChild(item);
                    }
                }
                if (wasActive) {
                    setCurrentSessionId(null);
                    setCurrentMessages([]);
                    if (state.callbacks.updateChatTitle) {
                        state.callbacks.updateChatTitle(null);
                    }
                    setActiveSessionItem(null);
                    if (state.callbacks.renderEmptyChat) {
                        state.callbacks.renderEmptyChat();
                    }
                    if (state.callbacks.syncJavaHistory) {
                        state.callbacks.syncJavaHistory([]);
                    }
                    if (state.sessions.length) {
                        loadSessionById(state.sessions[0].id);
                    }
                }
                if (!state.sessions.length) {
                    setSessionListEmptyMessage("No history yet.");
                } else {
                    setSessionListEmptyMessage(null);
                }
            })
            .catch(function(err) {
                console.error("Delete failed:", err);
                throw err;
            });
    }

    function openRenameModal(sessionId) {
        state.renameTargetSessionId = sessionId || getCurrentSessionId();
        if (!state.dom.renameModal) {
            return;
        }
        if (state.dom.renameChatInput) {
            var session = state.sessionById[state.renameTargetSessionId];
            state.dom.renameChatInput.value = session && session.title ? session.title : '';
            state.dom.renameChatInput.focus();
        }
        setRenameMessage("", "");
        state.dom.renameModal.removeAttribute('hidden');
        state.dom.renameModal.style.display = 'flex';
    }

    function closeRenameModal() {
        if (!state.dom.renameModal) {
            return;
        }
        state.dom.renameModal.setAttribute('hidden', '');
        state.dom.renameModal.style.display = 'none';
        setRenameMessage("", "");
    }

    function setRenameMessage(message, type) {
        if (!state.dom.renameChatMessage) {
            return;
        }
        if (!message) {
            state.dom.renameChatMessage.textContent = "";
            state.dom.renameChatMessage.setAttribute("hidden", "");
            state.dom.renameChatMessage.className = "auth-message modal-message";
            return;
        }
        state.dom.renameChatMessage.textContent = message;
        state.dom.renameChatMessage.className = "auth-message modal-message" + (type ? " " + type : "");
        state.dom.renameChatMessage.removeAttribute("hidden");
    }

    function openDeleteModal(sessionId) {
        state.deleteTargetSessionId = sessionId || getCurrentSessionId();
        if (!state.dom.deleteModal) {
            return;
        }
        setDeleteMessage("", "");
        state.dom.deleteModal.removeAttribute('hidden');
        state.dom.deleteModal.style.display = 'flex';
    }

    function closeDeleteModal() {
        if (!state.dom.deleteModal) {
            return;
        }
        state.dom.deleteModal.setAttribute('hidden', '');
        state.dom.deleteModal.style.display = 'none';
        setDeleteMessage("", "");
    }

    function setDeleteMessage(message, type) {
        if (!state.dom.deleteChatMessage) {
            return;
        }
        if (!message) {
            state.dom.deleteChatMessage.textContent = "";
            state.dom.deleteChatMessage.setAttribute("hidden", "");
            state.dom.deleteChatMessage.className = "auth-message modal-message";
            return;
        }
        state.dom.deleteChatMessage.textContent = message;
        state.dom.deleteChatMessage.className = "auth-message modal-message" + (type ? " " + type : "");
        state.dom.deleteChatMessage.removeAttribute("hidden");
    }

    function confirmDelete() {
        var targetId = state.deleteTargetSessionId || getCurrentSessionId();
        if (!targetId) {
            return;
        }
        if (state.dom.deleteChatConfirmBtn) {
            state.dom.deleteChatConfirmBtn.disabled = true;
        }
        if (state.dom.deleteChatCancelBtn) {
            state.dom.deleteChatCancelBtn.disabled = true;
        }
        deleteSession(targetId)
            .then(function() {
                closeDeleteModal();
            })
            .catch(function(err) {
                console.error("Delete failed:", err);
                if (state.dom.deleteChatConfirmBtn) {
                    state.dom.deleteChatConfirmBtn.disabled = false;
                }
                if (state.dom.deleteChatCancelBtn) {
                    state.dom.deleteChatCancelBtn.disabled = false;
                }
                setDeleteMessage("Delete failed. Try again.", "error");
            });
    }

    function saveRename() {
        var targetId = state.renameTargetSessionId || getCurrentSessionId();
        if (!targetId || !state.dom.renameChatInput) {
            return;
        }
        var title = state.dom.renameChatInput.value.trim();
        if (!title) {
            setRenameMessage("Enter a title.", "error");
            return;
        }
        renameSessionTitle(targetId, title, { silent: false });
    }
    function createLocalHistoryId() {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID();
        }
        return "id-" + Date.now() + "-" + Math.floor(Math.random() * 1000000);
    }

    function getOrCreateLocalHistoryUserId() {
        if (state.localHistoryUserId) {
            return state.localHistoryUserId;
        }
        var stored = "";
        try {
            stored = localStorage.getItem(state.localHistoryUserIdKey) || "";
        } catch (err) {
            stored = "";
        }
        if (stored) {
            state.localHistoryUserId = stored;
            return stored;
        }
        var generated = createLocalHistoryId();
        state.localHistoryUserId = generated;
        try {
            localStorage.setItem(state.localHistoryUserIdKey, generated);
        } catch (err) {
            /* ignore */
        }
        return generated;
    }

    function getLocalHistoryUserId() {
        if (state.callbacks.getCurrentUser) {
            var user = state.callbacks.getCurrentUser();
            if (user && user.id) {
                return String(user.id);
            }
            if (user && user.email) {
                return String(user.email);
            }
        }
        return getOrCreateLocalHistoryUserId();
    }

    function callLocalHistory(payload) {
        if (!window.localHistoryBridge) {
            return null;
        }
        var request = payload ? Object.assign({}, payload) : {};
        if (!request.user_id) {
            var userId = getLocalHistoryUserId();
            if (userId) {
                request.user_id = userId;
            }
        }
        try {
            var raw = window.localHistoryBridge(JSON.stringify(request));
            if (!raw) {
                return null;
            }
            if (typeof raw === "string") {
                return raw ? JSON.parse(raw) : null;
            }
            if (typeof raw === "object") {
                return raw;
            }
        } catch (err) {
            console.error("Local history call failed:", err);
        }
        return null;
    }

    function invokeLocalHistory(payload) {
        var data = callLocalHistory(payload);
        if (!data) {
            return Promise.reject(new Error("Local history unavailable."));
        }
        if (data && data.error) {
            return Promise.reject(new Error(data.error));
        }
        return Promise.resolve(data);
    }

    function normalizeStoragePreference(value) {
        if (value === "local" || value === "cloud") {
            return value;
        }
        return null;
    }

    function getStoredStoragePreference() {
        var userId = getLocalHistoryUserId();
        if (!userId) {
            return null;
        }
        var result = callLocalHistory({ action: "get_storage_preference", user_id: userId });
        return normalizeStoragePreference(result && result.storage_preference ? result.storage_preference : null);
    }

    function storeStoragePreference(value) {
        var userId = getLocalHistoryUserId();
        if (!userId) {
            return;
        }
        callLocalHistory({ action: "set_storage_preference", user_id: userId, storage_preference: value });
    }

    function resolveStoragePreference(user) {
        var meta = user && user.user_metadata ? user.user_metadata.storage_preference : null;
        var normalized = normalizeStoragePreference(meta);
        if (normalized) {
            return normalized;
        }
        var stored = getStoredStoragePreference();
        return stored || "cloud";
    }

    function applyStoragePreference(value, options) {
        var normalized = normalizeStoragePreference(value) || "cloud";
        state.storageMode = normalized;
        if (state.dom.storagePreferenceSelect) {
            state.dom.storagePreferenceSelect.value = normalized;
        }
        if (!options || options.persistLocal) {
            storeStoragePreference(normalized);
        }
        if (options && options.reloadSessions) {
            resetSessionState();
            loadInitialSessions();
        }
    }

    function updateStoragePreference(value) {
        var normalized = normalizeStoragePreference(value);
        if (!normalized) {
            return;
        }
        var shouldReload = normalized !== state.storageMode;
        applyStoragePreference(normalized, { persistLocal: true, reloadSessions: shouldReload });
        if (!state.callbacks.getCurrentUser) {
            return;
        }
        var currentUser = state.callbacks.getCurrentUser();
        if (!currentUser) {
            return;
        }
        var client = state.authApi && state.authApi.getClient ? state.authApi.getClient() : null;
        if (!client) {
            return;
        }
        client.auth.updateUser({ data: { storage_preference: normalized } })
            .then(function(res) {
                if (res.error) {
                    console.error(res.error);
                    return;
                }
                if (currentUser && currentUser.user_metadata) {
                    currentUser.user_metadata.storage_preference = normalized;
                }
            })
            .catch(function(err) {
                console.error("Storage preference update failed:", err);
            });
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
            return normalizeStoragePreference(localStorage.getItem(state.storagePreferenceKey));
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
        removeLegacyStorage(state.storagePreferenceKey);
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
        if (state.localHistoryMigrationAttempted) {
            return state.localHistoryMigrationPromise || Promise.resolve();
        }
        state.localHistoryMigrationAttempted = true;
        state.localHistoryMigrationPromise = migrateLegacyStorage().then(function(result) {
            state.localHistoryMigrationPromise = null;
            return result;
        });
        return state.localHistoryMigrationPromise;
    }
    function getHistoryFunctionName() {
        return state.supabaseConfig.historyFunctionName || state.supabaseConfig.historyFunction || "chat-history";
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
        var base = state.supabaseConfig.url || "";
        if (!base) {
            return "";
        }
        if (base.charAt(base.length - 1) === "/") {
            base = base.slice(0, -1);
        }
        return base + "/functions/v1/" + getHistoryFunctionName();
    }

    function invokeHistoryFunctionSupabase(client, payload) {
        return state.authApi.getAuthToken(client)
            .then(function(token) {
                var body = payload || {};
                body.access_token = token;
                return client.functions.invoke(getHistoryFunctionName(), {
                    body: body,
                    headers: {
                        Authorization: "Bearer " + token,
                        apikey: state.supabaseConfig.anonKey || ""
                    }
                });
            })
            .then(function(res) {
                if (res.error) {
                    if (state.authApi.isAuthError(res.error)) {
                        state.authApi.handleAuthFailure(res.error);
                    }
                    throw res.error;
                }
                return res.data || {};
            })
            .catch(function(err) {
                state.authApi.handleAuthFailure(err);
                throw err;
            });
    }

    function invokeHistoryFunctionFetch(client, payload) {
        var url = buildHistoryFunctionUrl();
        if (!url) {
            return Promise.reject(new Error("Supabase config missing."));
        }
        return state.authApi.getAuthToken(client)
            .then(function(token) {
                var body = payload || {};
                body.access_token = token;
                var headers = {
                    "Content-Type": "application/json",
                    Authorization: "Bearer " + token
                };
                if (state.supabaseConfig.anonKey) {
                    headers.apikey = state.supabaseConfig.anonKey;
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
                state.authApi.handleAuthFailure(err);
                throw err;
            });
    }

    function invokeHistoryFunction(payload) {
        var client = state.authApi && state.authApi.getClient ? state.authApi.getClient() : null;
        if (!client || !client.functions || !client.functions.invoke) {
            return Promise.reject(new Error("Supabase functions unavailable."));
        }
        var mode = String(state.supabaseConfig.historyInvokeMode || "").toLowerCase();
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
        var limit = options && options.limit ? options.limit : 15;
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
        var limit = options && options.limit ? options.limit : 15;
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

    function storageListSessions(options) {
        return state.storageMode === "cloud"
            ? listSessionsCloud(options)
            : ensureLocalHistoryReady().then(function() { return listSessionsLocal(options); });
    }

    function storageCreateSession(title) {
        return state.storageMode === "cloud"
            ? createSessionCloud(title)
            : ensureLocalHistoryReady().then(function() { return createSessionLocal(title); });
    }

    function storageGetMessages(sessionId) {
        return state.storageMode === "cloud"
            ? getMessagesCloud(sessionId)
            : ensureLocalHistoryReady().then(function() { return getMessagesLocal(sessionId); });
    }

    function storageAppendMessage(sessionId, message) {
        return state.storageMode === "cloud"
            ? appendMessageCloud(sessionId, message)
            : ensureLocalHistoryReady().then(function() { return appendMessageLocal(sessionId, message); });
    }

    function storageRenameSession(sessionId, title) {
        return state.storageMode === "cloud"
            ? renameSessionCloud(sessionId, title)
            : ensureLocalHistoryReady().then(function() { return renameSessionLocal(sessionId, title); });
    }

    function storageDeleteSession(sessionId) {
        return state.storageMode === "cloud"
            ? deleteSessionCloud(sessionId)
            : ensureLocalHistoryReady().then(function() { return deleteSessionLocal(sessionId); });
    }

    function init(options) {
        var opts = options || {};
        state.dom = opts.dom || {};
        state.supabaseConfig = opts.supabaseConfig || {};
        state.authApi = opts.authApi || null;
        state.callbacks = opts.callbacks || {};
        state.stateAccess = opts.stateAccess || {};
        state.storagePreferenceKey = opts.storagePreferenceKey || "genai.storagePreference";
        state.localHistoryUserIdKey = opts.localHistoryUserIdKey || "genai.localHistoryUserId";

        if (state.dom.sessionList) {
            state.dom.sessionList.addEventListener('scroll', function() {
                if (isSessionListNearBottom()) {
                    loadMoreSessions();
                }
            });

            state.dom.sessionList.addEventListener('click', function(e) {
                var actionBtn = e.target && e.target.closest
                    ? e.target.closest('.session-rename, .session-delete')
                    : null;
                if (actionBtn) {
                    return;
                }
                var item = findSessionItem(e.target);
                if (!item) {
                    return;
                }
                var sessionId = item.getAttribute('data-session-id');
                if (sessionId) {
                    loadSessionById(sessionId);
                    if (state.callbacks.closeHistoryPanel) {
                        state.callbacks.closeHistoryPanel();
                    }
                }
            });

            state.dom.sessionList.addEventListener('keydown', function(e) {
                if (e.key !== 'Enter') {
                    return;
                }
                var item = findSessionItem(e.target);
                if (!item) {
                    return;
                }
                var sessionId = item.getAttribute('data-session-id');
                if (sessionId) {
                    loadSessionById(sessionId);
                }
            });
        }

        if (state.dom.renameChatBtn) {
            state.dom.renameChatBtn.addEventListener('click', function() {
                openRenameModal(getCurrentSessionId());
            });
        }
        if (state.dom.renameChatSaveBtn) {
            state.dom.renameChatSaveBtn.addEventListener('click', function() {
                saveRename();
            });
        }
        if (state.dom.renameChatCancelBtn) {
            state.dom.renameChatCancelBtn.addEventListener('click', function() {
                closeRenameModal();
            });
        }
        if (state.dom.renameChatCloseBtn) {
            state.dom.renameChatCloseBtn.addEventListener('click', function() {
                closeRenameModal();
            });
        }
        if (state.dom.renameChatInput) {
            state.dom.renameChatInput.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    saveRename();
                } else if (e.key === 'Escape') {
                    closeRenameModal();
                }
            });
        }
        if (state.dom.renameModal) {
            state.dom.renameModal.addEventListener('click', function(e) {
                if (e.target === state.dom.renameModal) {
                    closeRenameModal();
                }
            });
        }
        if (state.dom.deleteChatConfirmBtn) {
            state.dom.deleteChatConfirmBtn.addEventListener('click', function() {
                confirmDelete();
            });
        }
        if (state.dom.deleteChatCancelBtn) {
            state.dom.deleteChatCancelBtn.addEventListener('click', function() {
                closeDeleteModal();
            });
        }
        if (state.dom.deleteChatCloseBtn) {
            state.dom.deleteChatCloseBtn.addEventListener('click', function() {
                closeDeleteModal();
            });
        }
        if (state.dom.deleteModal) {
            state.dom.deleteModal.addEventListener('click', function(e) {
                if (e.target === state.dom.deleteModal) {
                    closeDeleteModal();
                }
            });
        }

        if (state.dom.sessionList) {
            state.dom.sessionList.addEventListener('click', function(e) {
                var actionBtn = e.target && e.target.closest
                    ? e.target.closest('.session-rename, .session-delete')
                    : null;
                if (!actionBtn) {
                    return;
                }
                var item = findSessionItem(actionBtn);
                if (!item) {
                    return;
                }
                var sessionId = item.getAttribute('data-session-id');
                if (!sessionId) {
                    return;
                }
                if (actionBtn.classList.contains('session-rename')) {
                    openRenameModal(sessionId);
                } else if (actionBtn.classList.contains('session-delete')) {
                    openDeleteModal(sessionId);
                }
            });
        }
    }

    window.GenAI.history = {
        init: init,
        resetSessionState: resetSessionState,
        loadInitialSessions: loadInitialSessions,
        loadMoreSessions: loadMoreSessions,
        startNewSession: startNewSession,
        ensureActiveSession: ensureActiveSession,
        loadSessionById: loadSessionById,
        autoTitleSession: autoTitleSession,
        persistMessage: persistMessage,
        renameSessionTitle: renameSessionTitle,
        deleteSession: deleteSession,
        openRenameModal: openRenameModal,
        closeRenameModal: closeRenameModal,
        saveRename: saveRename,
        confirmDelete: confirmDelete,
        applyStoragePreference: applyStoragePreference,
        resolveStoragePreference: resolveStoragePreference,
        updateStoragePreference: updateStoragePreference,
        getStorageMode: function() { return state.storageMode; }
    };
})();
