/* global window */
(function() {
    window.GenAI = window.GenAI || {};
    var utils = window.GenAI.utils || {};

    var forEach = utils.forEach || function(list, callback) {
        for (var i = 0; i < list.length; i++) {
            callback(list[i]);
        }
    };

    var state = {
        dom: {},
        utils: {},
        callbacks: {},
        stateAccess: {},
        chatInitialized: false,
        pendingAssistantRow: null,
        pendingAssistantContent: null,
        pendingAssistantTimer: null,
        pendingAssistantTick: 0,
        typingAssistantMessage: null,
        typingAssistantCommitted: false,
        typingAssistantRow: null,
        typingAssistantContent: null,
        typingAssistantTimer: null,
        typingAssistantIndex: 0,
        typingAssistantChars: null,
        typingAssistantText: "",
        typingAssistantRendered: "",
        typingAssistantStickToBottom: false,
        sendButtonMode: "send",
        suppressNextAssistantMessage: false,
        STOP_MARKER: "[stopped]",
        STOP_CACHE_KEY: "genai.stoppedCache",
        STOP_PENDING_KEY: "__pending__"
    };

    function getCurrentSessionId() {
        return state.stateAccess.getCurrentSessionId ? state.stateAccess.getCurrentSessionId() : null;
    }

    function getCurrentMessages() {
        return state.stateAccess.getCurrentMessages ? state.stateAccess.getCurrentMessages() : [];
    }

    function setCurrentMessages(messages) {
        if (state.stateAccess.setCurrentMessages) {
            state.stateAccess.setCurrentMessages(messages || []);
        }
    }

    function keepInputFocus() {
        if (!state.dom.userInput) {
            return;
        }
        try { state.dom.userInput.focus(); } catch (e) { /* ignore */ }
    }

    function updateSendButtonState() {
        if (!state.dom.sendBtn || !state.dom.userInput) {
            return;
        }
        if (state.sendButtonMode === "stop") {
            state.dom.sendBtn.disabled = false;
            state.dom.sendBtn.setAttribute("aria-disabled", "false");
            return;
        }
        var hasText = !!state.dom.userInput.value.trim();
        state.dom.sendBtn.disabled = !hasText;
        state.dom.sendBtn.setAttribute("aria-disabled", hasText ? "false" : "true");
    }

    function isChatNearBottom() {
        if (!state.dom.chatHistory) {
            return true;
        }
        try {
            var threshold = 48;
            return (state.dom.chatHistory.scrollHeight - (state.dom.chatHistory.scrollTop + state.dom.chatHistory.clientHeight)) < threshold;
        } catch (e) {
            return true;
        }
    }

    function scrollChatToBottom() {
        if (!state.dom.chatHistory) {
            return;
        }
        try { state.dom.chatHistory.scrollTop = state.dom.chatHistory.scrollHeight; } catch (e) { }
        setTimeout(function() {
            try { state.dom.chatHistory.scrollTop = state.dom.chatHistory.scrollHeight; } catch (e) { }
        }, 50);
    }

    function bindWheelScroll(el) {
        if (!el) {
            return;
        }
        var handler = function(e) {
            var delta = 0;
            if (typeof e.deltaY === 'number') delta = e.deltaY;
            else if (typeof e.wheelDelta === 'number') delta = -e.wheelDelta;

            if (delta) {
                el.scrollTop = el.scrollTop + delta;
                if (e.preventDefault) e.preventDefault();
                e.returnValue = false;
            }
        };
        try { el.addEventListener('wheel', handler, { passive: false }); }
        catch (err) { el.addEventListener('wheel', handler); }
        el.addEventListener('mousewheel', handler);
    }

    function getSessionTitle(session) {
        if (!session) {
            return "New chat";
        }
        var title = (session.title || "").trim();
        return title ? title : "New chat";
    }

    function updateChatTitle(session) {
        if (!state.dom.chatTitle) {
            return;
        }
        var title = session ? getSessionTitle(session) : "New chat";
        state.dom.chatTitle.textContent = title;
        if (state.dom.renameChatBtn) {
            state.dom.renameChatBtn.disabled = !session || !session.id;
        }
    }

    function renderEmptyChat() {
        if (!state.dom.chatHistory) {
            return;
        }
        clearTypingAssistant();
        clearPendingAssistant();
        setSendButtonMode("send");
        state.dom.chatHistory.innerHTML =
            '<div class="message-row ai">' +
            '  <div class="message ai">' +
            '    <div class="content">Hello! I\'m ready to help you Generate your ArchiMate models.</div>' +
            '  </div>' +
            '</div>';
        scrollChatToBottom();
        keepInputFocus();
        updateSendButtonState();
    }

    function renderLoadingChat() {
        if (!state.dom.chatHistory) {
            return;
        }
        clearTypingAssistant();
        clearPendingAssistant();
        setSendButtonMode("send");
        if (!getCurrentSessionId() && state.dom.chatTitle) {
            state.dom.chatTitle.textContent = "Loading";
        }
        state.dom.chatHistory.innerHTML =
            '<div class="message-row ai loading">' +
            '  <div class="message ai">' +
            '    <div class="content loading">Loading</div>' +
            '  </div>' +
            '</div>';
        scrollChatToBottom();
        keepInputFocus();
    }

    function appendMessageToDom(text, sender, options) {
        if (!state.dom.chatHistory) {
            return;
        }
        var row = document.createElement('div');
        row.className = 'message-row ' + sender;

        var msgDiv = document.createElement('div');
        msgDiv.className = 'message ' + sender;

        var contentDiv = document.createElement('div');
        contentDiv.className = 'content';
        if (sender === 'ai') {
            var parsed = parseStoppedContent(text);
            contentDiv.textContent = parsed.text;
            attachMessageToggle(row, msgDiv);
            applyStoppedState(row, msgDiv, parsed.stopped);
        } else {
            contentDiv.textContent = text;
        }

        msgDiv.appendChild(contentDiv);
        row.appendChild(msgDiv);

        state.dom.chatHistory.appendChild(row);

        if (!options || options.scroll !== false) {
            scrollChatToBottom();
        }
        if (!options || options.focus !== false) {
            keepInputFocus();
        }
    }

    function parseStoppedContent(text) {
        var raw = String(text || "");
        if (!raw) {
            return { text: raw, stopped: false };
        }
        var trimmed = raw.replace(/\s+$/, "");
        if (trimmed.slice(-state.STOP_MARKER.length) === state.STOP_MARKER) {
            return {
                text: trimmed.slice(0, -state.STOP_MARKER.length).replace(/\s+$/, ""),
                stopped: true
            };
        }
        return { text: raw, stopped: false };
    }

    function buildStoppedContent(text) {
        var base = String(text || "").replace(/\s+$/, "");
        if (!base) {
            return state.STOP_MARKER;
        }
        return base + " " + state.STOP_MARKER;
    }

    function loadStoppedCache() {
        try {
            var raw = localStorage.getItem(state.STOP_CACHE_KEY);
            return raw ? JSON.parse(raw) : {};
        } catch (err) {
            return {};
        }
    }

    function saveStoppedCache(cache) {
        try {
            localStorage.setItem(state.STOP_CACHE_KEY, JSON.stringify(cache || {}));
        } catch (err) {
            /* ignore */
        }
    }

    function rememberStoppedMessage(sessionId, message) {
        if (!message) {
            return;
        }
        var cache = loadStoppedCache();
        var bucketKey = sessionId || state.STOP_PENDING_KEY;
        var list = cache[bucketKey] || [];
        var parsed = parseStoppedContent(message.content || "");
        var content = parsed.stopped ? buildStoppedContent(parsed.text) : buildStoppedContent(message.content || "");
        var entry = {
            id: message.id || null,
            content: content || state.STOP_MARKER,
            base: parsed.text || "",
            ts: Date.now()
        };
        var replaced = false;
        for (var i = 0; i < list.length; i++) {
            if (!list[i]) {
                continue;
            }
            if (entry.id && list[i].id === entry.id) {
                list[i] = entry;
                replaced = true;
                break;
            }
            if (!entry.id && list[i].content === entry.content) {
                list[i] = entry;
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.push(entry);
        }
        if (list.length > 80) {
            list = list.slice(list.length - 80);
        }
        cache[bucketKey] = list;
        saveStoppedCache(cache);
    }

    function flushPendingStopped(sessionId) {
        if (!sessionId) {
            return;
        }
        var cache = loadStoppedCache();
        var pending = cache[state.STOP_PENDING_KEY] || [];
        if (!pending.length) {
            return;
        }
        var list = cache[sessionId] || [];
        forEach(pending, function(entry) {
            if (!entry) {
                return;
            }
            list.push(entry);
        });
        if (list.length > 80) {
            list = list.slice(list.length - 80);
        }
        cache[sessionId] = list;
        cache[state.STOP_PENDING_KEY] = [];
        saveStoppedCache(cache);
    }

    function applyStoppedCache(sessionId, messages) {
        if (!sessionId) {
            return messages || [];
        }
        var cache = loadStoppedCache();
        var list = cache[sessionId] || [];
        if (!list.length) {
            return messages || [];
        }
        messages = messages || [];
        var byId = {};
        var byContent = {};
        forEach(messages, function(msg) {
            if (!msg || !msg.content) {
                return;
            }
            if (msg.id) {
                byId[msg.id] = msg;
            }
            byContent[msg.content] = msg;
        });
        var appended = false;
        forEach(list, function(entry) {
            if (!entry) {
                return;
            }
            var content = entry.content || state.STOP_MARKER;
            var msg = entry.id ? byId[entry.id] : null;
            if (!msg && byContent[content]) {
                msg = byContent[content];
            }
            if (!msg && entry.base) {
                var base = String(entry.base || "").trim();
                if (base) {
                    forEach(messages, function(candidate) {
                        if (msg || !candidate || candidate.role !== "assistant" || !candidate.content) {
                            return;
                        }
                        var candidateText = String(candidate.content).trim();
                        if (candidateText.indexOf(base) === 0) {
                            msg = candidate;
                        }
                    });
                }
            }
            if (msg) {
                msg.content = content;
                return;
            }
            messages.push({
                id: entry.id || (state.utils.generateId ? state.utils.generateId() : null),
                role: "assistant",
                content: content,
                created_at: entry.ts ? new Date(entry.ts).toISOString() : new Date().toISOString()
            });
            appended = true;
        });
        if (appended) {
            messages.sort(function(a, b) {
                var aTime = a && a.created_at ? Date.parse(a.created_at) : 0;
                var bTime = b && b.created_at ? Date.parse(b.created_at) : 0;
                if (isNaN(aTime)) aTime = 0;
                if (isNaN(bTime)) bTime = 0;
                return aTime - bTime;
            });
        }
        return messages;
    }

    function attachMessageToggle(row, msgDiv) {
        if (!row || !msgDiv) {
            return;
        }
        if (msgDiv.querySelector('.message-toggle')) {
            return;
        }
        var toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'message-toggle';
        toggle.setAttribute('aria-expanded', 'true');
        toggle.setAttribute('title', 'Collapse');
        toggle.innerHTML =
            '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' +
            '  <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/>' +
            '</svg>';
        toggle.addEventListener('click', function(e) {
            e.stopPropagation();
            var collapsed = row.classList.toggle('is-collapsed');
            toggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
            toggle.setAttribute('title', collapsed ? 'Expand' : 'Collapse');
        });
        msgDiv.insertBefore(toggle, msgDiv.firstChild);
        row.classList.add('ai-collapsible');
    }

    function applyStoppedState(row, msgDiv, stopped) {
        if (!row || !msgDiv) {
            return;
        }
        var status = msgDiv.querySelector('.message-status--stopped');
        if (stopped) {
            row.classList.add('is-stopped');
            if (!status) {
                status = document.createElement('span');
                status.className = 'message-status message-status--stopped';
                status.textContent = 'Stopped';
                msgDiv.appendChild(status);
            }
        } else {
            row.classList.remove('is-stopped');
            if (status && status.parentNode) {
                status.parentNode.removeChild(status);
            }
        }
    }

    function setSendButtonMode(mode) {
        state.sendButtonMode = mode === "stop" ? "stop" : "send";
        if (!state.dom.sendBtn) {
            return;
        }
        if (state.sendButtonMode === "stop") {
            state.dom.sendBtn.classList.add('send-btn-stop');
            state.dom.sendBtn.innerHTML = '&#9632;';
            state.dom.sendBtn.setAttribute('aria-label', 'Stop');
            state.dom.sendBtn.setAttribute('title', 'Stop');
            state.dom.sendBtn.disabled = false;
            state.dom.sendBtn.setAttribute("aria-disabled", "false");
            return;
        }
        state.dom.sendBtn.classList.remove('send-btn-stop');
        state.dom.sendBtn.innerHTML = '&uarr;';
        state.dom.sendBtn.setAttribute('aria-label', 'Send');
        state.dom.sendBtn.setAttribute('title', 'Send');
        updateSendButtonState();
    }

    function stopAssistantReply() {
        if (state.typingAssistantTimer || state.typingAssistantRow) {
            var stoppedText = state.typingAssistantRendered || "";
            if (state.typingAssistantContent) {
                state.typingAssistantContent.textContent = stoppedText;
            }
            if (state.typingAssistantRow) {
                var msgNode = state.typingAssistantRow.querySelector('.message.ai');
                applyStoppedState(state.typingAssistantRow, msgNode, true);
            }
            commitTypingAssistant(buildStoppedContent(stoppedText));
            clearTypingAssistant();
            setSendButtonMode("send");
            return;
        }
        if (state.pendingAssistantRow || state.pendingAssistantTimer) {
            if (state.pendingAssistantTimer) {
                clearInterval(state.pendingAssistantTimer);
                state.pendingAssistantTimer = null;
            }
            state.pendingAssistantTick = 0;
            if (state.pendingAssistantRow && state.pendingAssistantContent) {
                var row = state.pendingAssistantRow;
                var content = state.pendingAssistantContent;
                var msgDiv = content.parentNode;
                row.className = 'message-row ai';
                content.className = 'content';
                content.textContent = '';
                attachMessageToggle(row, msgDiv);
                applyStoppedState(row, msgDiv, true);
                state.pendingAssistantRow = null;
                state.pendingAssistantContent = null;
                var stoppedMessage = state.utils.buildMessage("assistant", buildStoppedContent(""));
                var currentMessages = getCurrentMessages();
                currentMessages.push(stoppedMessage);
                setCurrentMessages(currentMessages);
                rememberStoppedMessage(getCurrentSessionId(), stoppedMessage);
                if (state.callbacks.ensureActiveSession) {
                    state.callbacks.ensureActiveSession()
                        .then(function(session) {
                            if (session && session.id) {
                                state.callbacks.persistMessage(session.id, stoppedMessage);
                                rememberStoppedMessage(session.id, stoppedMessage);
                            }
                        })
                        .catch(function(err) {
                            console.error("Session error:", err);
                        });
                }
            } else {
                clearPendingAssistant();
            }
            state.suppressNextAssistantMessage = true;
            setSendButtonMode("send");
        }
    }

    function clearPendingAssistant() {
        if (state.pendingAssistantTimer) {
            clearInterval(state.pendingAssistantTimer);
            state.pendingAssistantTimer = null;
        }
        state.pendingAssistantTick = 0;
        if (state.pendingAssistantRow && state.pendingAssistantRow.parentNode) {
            state.pendingAssistantRow.parentNode.removeChild(state.pendingAssistantRow);
        }
        state.pendingAssistantRow = null;
        state.pendingAssistantContent = null;
    }

    function clearTypingAssistant() {
        if (state.typingAssistantTimer) {
            clearInterval(state.typingAssistantTimer);
            state.typingAssistantTimer = null;
        }
        state.typingAssistantIndex = 0;
        state.typingAssistantChars = null;
        state.typingAssistantText = "";
        state.typingAssistantRendered = "";
        state.typingAssistantStickToBottom = false;
        state.typingAssistantMessage = null;
        state.typingAssistantCommitted = false;
        state.typingAssistantRow = null;
        state.typingAssistantContent = null;
    }

    function commitTypingAssistant(text) {
        if (!state.typingAssistantMessage || state.typingAssistantCommitted) {
            return;
        }
        state.typingAssistantCommitted = true;
        var content = text || "";
        state.typingAssistantMessage.content = content;
        var stopped = parseStoppedContent(content).stopped;
        if (stopped) {
            rememberStoppedMessage(getCurrentSessionId(), state.typingAssistantMessage);
        }
        if (!state.typingAssistantMessage._persisted && !state.typingAssistantMessage._persisting) {
            state.typingAssistantMessage._persisting = true;
            if (state.callbacks.ensureActiveSession) {
                state.callbacks.ensureActiveSession()
                    .then(function(session) {
                        if (session && session.id) {
                            state.callbacks.persistMessage(session.id, state.typingAssistantMessage);
                            if (stopped) {
                                rememberStoppedMessage(session.id, state.typingAssistantMessage);
                            }
                            state.typingAssistantMessage._persisted = true;
                        }
                    })
                    .catch(function(err) {
                        console.error("Session error:", err);
                    })
                    .then(function() {
                        state.typingAssistantMessage._persisting = false;
                    });
            }
        }
    }

    function finalizeTypingAssistant() {
        if (!state.typingAssistantTimer && !state.typingAssistantRow) {
            return;
        }
        if (state.typingAssistantTimer) {
            clearInterval(state.typingAssistantTimer);
            state.typingAssistantTimer = null;
        }
        if (state.typingAssistantContent) {
            state.typingAssistantContent.textContent = state.typingAssistantText || state.typingAssistantRendered || "";
        }
        commitTypingAssistant(state.typingAssistantText || state.typingAssistantRendered || "");
        clearTypingAssistant();
    }

    function prepareAssistantTypingRow() {
        if (!state.dom.chatHistory) {
            return null;
        }
        var row = null;
        var content = null;
        var msgDiv = null;
        if (state.pendingAssistantRow && state.pendingAssistantContent) {
            if (state.pendingAssistantTimer) {
                clearInterval(state.pendingAssistantTimer);
                state.pendingAssistantTimer = null;
            }
            state.pendingAssistantTick = 0;
            row = state.pendingAssistantRow;
            content = state.pendingAssistantContent;
            state.pendingAssistantRow = null;
            state.pendingAssistantContent = null;
            row.className = 'message-row ai';
            content.className = 'content';
            content.textContent = '';
            msgDiv = content.parentNode;
        }
        if (!row) {
            row = document.createElement('div');
            row.className = 'message-row ai';
            msgDiv = document.createElement('div');
            msgDiv.className = 'message ai';
            attachMessageToggle(row, msgDiv);
            content = document.createElement('div');
            content.className = 'content';
            msgDiv.appendChild(content);
            row.appendChild(msgDiv);
            state.dom.chatHistory.appendChild(row);
        }
        if (!msgDiv && row && row.querySelector) {
            msgDiv = row.querySelector('.message.ai');
        }
        if (msgDiv) {
            attachMessageToggle(row, msgDiv);
            applyStoppedState(row, msgDiv, false);
        }
        return { row: row, content: content };
    }

    function startAssistantTyping(text, message) {
        if (!text || !state.dom.chatHistory) {
            return;
        }
        finalizeTypingAssistant();
        var prepared = prepareAssistantTypingRow();
        if (!prepared) {
            return;
        }
        state.typingAssistantRow = prepared.row;
        state.typingAssistantContent = prepared.content;
        state.typingAssistantText = text;
        state.typingAssistantMessage = message || null;
        state.typingAssistantCommitted = false;
        state.typingAssistantChars = text.split('');
        state.typingAssistantIndex = 0;
        state.typingAssistantRendered = "";
        state.typingAssistantStickToBottom = isChatNearBottom();
        setSendButtonMode("stop");

        state.typingAssistantTimer = setInterval(function() {
            if (!state.typingAssistantContent || !state.typingAssistantChars) {
                clearTypingAssistant();
                return;
            }
            var nextIndex = state.typingAssistantIndex + 1;
            if (nextIndex > state.typingAssistantChars.length) {
                nextIndex = state.typingAssistantChars.length;
            }
            if (nextIndex > state.typingAssistantIndex) {
                state.typingAssistantRendered += state.typingAssistantChars.slice(state.typingAssistantIndex, nextIndex).join("");
                state.typingAssistantIndex = nextIndex;
                state.typingAssistantContent.textContent = state.typingAssistantRendered;
                if (state.typingAssistantStickToBottom) {
                    scrollChatToBottom();
                }
            }
            if (state.typingAssistantIndex >= state.typingAssistantChars.length) {
                commitTypingAssistant(state.typingAssistantText || "");
                clearTypingAssistant();
                setSendButtonMode("send");
            }
        }, 18);

        if (state.typingAssistantStickToBottom) {
            scrollChatToBottom();
        }
        keepInputFocus();
    }

    function showPendingAssistant() {
        if (!state.dom.chatHistory) {
            return;
        }
        finalizeTypingAssistant();
        clearPendingAssistant();
        setSendButtonMode("stop");

        var row = document.createElement('div');
        row.className = 'message-row ai pending';

        var msgDiv = document.createElement('div');
        msgDiv.className = 'message ai';

        var contentDiv = document.createElement('div');
        contentDiv.className = 'content pending';
        contentDiv.textContent = '.';

        msgDiv.appendChild(contentDiv);
        row.appendChild(msgDiv);
        state.dom.chatHistory.appendChild(row);

        state.pendingAssistantRow = row;
        state.pendingAssistantContent = contentDiv;
        state.pendingAssistantTick = 1;
        state.pendingAssistantTimer = setInterval(function() {
            state.pendingAssistantTick = (state.pendingAssistantTick % 3) + 1;
            if (state.pendingAssistantContent) {
                state.pendingAssistantContent.textContent = new Array(state.pendingAssistantTick + 1).join('.');
            }
        }, 450);

        scrollChatToBottom();
        keepInputFocus();
    }

    function renderChatHistory(messages) {
        if (!state.dom.chatHistory) {
            return;
        }
        clearTypingAssistant();
        clearPendingAssistant();
        setSendButtonMode("send");
        state.dom.chatHistory.innerHTML = "";
        if (!messages || !messages.length) {
            renderEmptyChat();
            return;
        }
        forEach(messages, function(message) {
            var sender = message.role === "assistant" ? "ai" : "user";
            appendMessageToDom(message.content || "", sender, { scroll: false, focus: false });
        });
        scrollChatToBottom();
    }

    function sendMessage() {
        if (!state.dom.userInput) {
            return;
        }
        var text = state.dom.userInput.value.trim();
        if (!text) return;

        var message = state.utils.buildMessage("user", text);
        var currentMessages = getCurrentMessages();
        currentMessages.push(message);
        setCurrentMessages(currentMessages);
        appendMessageToDom(text, 'user');
        state.dom.userInput.value = '';
        state.dom.userInput.style.height = 'auto';
        keepInputFocus();
        updateSendButtonState();
        showPendingAssistant();

        if (state.callbacks.ensureActiveSession) {
            state.callbacks.ensureActiveSession()
                .then(function(session) {
                    if (session && session.id) {
                        state.callbacks.persistMessage(session.id, message);
                        if (!session.title) {
                            state.callbacks.autoTitleSession(session, text);
                        }
                    }
                })
                .catch(function(err) {
                    console.error("Session error:", err);
                });
        }

        try {
            if (window.javaBridge) {
                window.javaBridge(text);
            } else {
                clearPendingAssistant();
                setSendButtonMode("send");
                console.log("Java Bridge not found");
            }
        } catch (err) {
            clearPendingAssistant();
            setSendButtonMode("send");
            appendMessageToDom("Error sending to Java: " + err.message, 'ai');
        }
    }

    function handleAssistantMessage(text) {
        var safeText = String(text || "").trim();
        if (!safeText) {
            return;
        }
        if (state.suppressNextAssistantMessage) {
            state.suppressNextAssistantMessage = false;
            if (!state.pendingAssistantRow && !state.typingAssistantTimer && !state.typingAssistantRow) {
                setSendButtonMode("send");
            }
            return;
        }
        var message = state.utils.buildMessage("assistant", safeText);
        var currentMessages = getCurrentMessages();
        currentMessages.push(message);
        setCurrentMessages(currentMessages);
        persistAssistantMessage(message);
        startAssistantTyping(safeText, message);
    }

    function persistAssistantMessage(message) {
        if (!message || message._persisted || message._persisting) {
            return;
        }
        message._persisting = true;
        if (state.callbacks.ensureActiveSession) {
            state.callbacks.ensureActiveSession()
                .then(function(session) {
                    if (session && session.id) {
                        state.callbacks.persistMessage(session.id, message);
                        message._persisted = true;
                    }
                })
                .catch(function(err) {
                    console.error("Session error:", err);
                })
                .then(function() {
                    message._persisting = false;
                });
        }
    }

    function init(options) {
        var opts = options || {};
        state.dom = opts.dom || {};
        state.utils = opts.utils || {};
        state.callbacks = opts.callbacks || {};
        state.stateAccess = opts.stateAccess || {};

        if (state.chatInitialized) {
            return;
        }
        if (!state.dom.sendBtn || !state.dom.userInput || !state.dom.chatHistory) {
            return;
        }
        state.chatInitialized = true;

        bindWheelScroll(state.dom.chatHistory);
        scrollChatToBottom();
        keepInputFocus();

        var autoResize = function() {
            var wasNearBottom = isChatNearBottom();
            state.dom.userInput.style.height = 'auto';
            var nextHeight = state.dom.userInput.scrollHeight;
            if (nextHeight < 24) nextHeight = 24;
            if (nextHeight > 140) nextHeight = 140;
            state.dom.userInput.style.height = nextHeight + 'px';
            if (wasNearBottom) scrollChatToBottom();
        };

        state.dom.userInput.addEventListener('input', function() {
            autoResize();
            updateSendButtonState();
        });
        state.dom.userInput.addEventListener('keydown', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (state.sendButtonMode === "stop") {
                    stopAssistantReply();
                } else {
                    sendMessage();
                }
            }
        });
        state.dom.sendBtn.addEventListener('click', function() {
            if (state.sendButtonMode === "stop") {
                stopAssistantReply();
                return;
            }
            sendMessage();
        });
        updateSendButtonState();

        window.receiveMessage = function(text) {
            handleAssistantMessage(text);
        };
    }

    window.GenAI.chat = {
        init: init,
        renderEmptyChat: renderEmptyChat,
        renderLoadingChat: renderLoadingChat,
        renderChatHistory: renderChatHistory,
        appendMessageToDom: appendMessageToDom,
        setSendButtonMode: setSendButtonMode,
        stopAssistantReply: stopAssistantReply,
        applyStoppedCache: applyStoppedCache,
        flushPendingStopped: flushPendingStopped,
        updateChatTitle: updateChatTitle
    };
})();
