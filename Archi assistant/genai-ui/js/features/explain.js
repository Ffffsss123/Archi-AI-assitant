/* global window, document */
(function() {
    window.GenAI = window.GenAI || {};
    var utils = window.GenAI.utils || {};

    var forEach = utils.forEach || function(list, callback) {
        for (var i = 0; i < list.length; i++) {
            callback(list[i]);
        }
    };

    var escapeHtml = utils.escapeHtml || function(value) {
        var div = document.createElement("div");
        div.textContent = value;
        return div.innerHTML;
    };

    var state = {
        mode: "simplified",
        summary: "",
        simplified: "",
        detailed: "",
        dom: {}
    };

    function renderExplainText(target, text) {
        if (!target) {
            return;
        }
        var safe = escapeHtml(text || "");
        var lines = safe.split(/\n+/).filter(Boolean);
        if (!lines.length) {
            target.textContent = "No content.";
            return;
        }

        var listItems = lines.filter(function(line) {
            return line.indexOf("- ") === 0 || line.indexOf("* ") === 0;
        });
        if (listItems.length === lines.length) {
            var items = listItems
                .map(function(line) { return "<li>" + line.slice(2) + "</li>"; })
                .join("");
            target.innerHTML = "<ul>" + items + "</ul>";
            return;
        }

        target.innerHTML = lines.map(function(line) { return "<p>" + line + "</p>"; }).join("");
    }

    function renderExplainCurrent() {
        var showingSimplified = state.mode === "simplified";
        var title = showingSimplified ? "Simplified" : "Detailed";
        var body = showingSimplified ? state.simplified : state.detailed;
        if (state.dom.panelTitle) {
            state.dom.panelTitle.textContent = title;
        }
        renderExplainText(state.dom.panelBody, body || "No content.");
    }

    function setExplainMode(mode) {
        state.mode = mode === "detailed" ? "detailed" : "simplified";
        forEach(state.dom.modeButtons || [], function(btn) {
            var btnMode = btn.getAttribute("data-explain-mode");
            if (btnMode === state.mode) {
                btn.classList.add("active");
            } else {
                btn.classList.remove("active");
            }
        });
        renderExplainCurrent();
    }

    function copyExplain(text) {
        if (window.copyExplainText) {
            window.copyExplainText(text || "");
        }
    }

    function init(options) {
        var opts = options || {};
        state.dom = opts.dom || {};
        if (opts.mode) {
            state.mode = opts.mode === "detailed" ? "detailed" : "simplified";
        }

        if (state.dom.languageSelect) {
            state.dom.languageSelect.addEventListener("change", function() {
                var value = state.dom.languageSelect.value;
                if (window.setExplainLanguage) {
                    window.setExplainLanguage(value);
                } else if (window.javaBridge) {
                    window.javaBridge("SYSTEM_CMD:EXPLAIN_LANGUAGE:" + value);
                }
            });
        }

        if (state.dom.copySummary) {
            state.dom.copySummary.addEventListener("click", function() {
                copyExplain(state.summary);
            });
        }

        if (state.dom.copyCurrent) {
            state.dom.copyCurrent.addEventListener("click", function() {
                var text = state.mode === "simplified" ? state.simplified : state.detailed;
                copyExplain(text);
            });
        }

        forEach(state.dom.modeButtons || [], function(btn) {
            btn.addEventListener("click", function() {
                setExplainMode(btn.getAttribute("data-explain-mode"));
            });
        });

        setExplainMode(state.mode);
    }

    window.updateExplainContext = function(data) {
        if (!data) {
            return;
        }
        if (state.dom.contextLine) {
            var selectionLabel = data.hasSelection
                ? data.selectedCount + " selected"
                : "no selection";
            state.dom.contextLine.textContent = (data.viewName || "Unknown view") +
                " (" + (data.viewType || "Unknown") + ") - " + selectionLabel;
        }
        if (state.dom.languageSelect && data.language) {
            state.dom.languageSelect.value = data.language;
        }
    };

    window.updateExplainResult = function(data) {
        if (!data) {
            return;
        }
        state.simplified = data.simplified || "";
        state.detailed = data.detailed || "";
        state.summary = data.summary || "";

        if (state.dom.summaryText) {
            state.dom.summaryText.textContent = state.summary || "No summary yet.";
        }
        renderExplainCurrent();
    };

    window.updateExplainState = function(data) {
        if (!data || !state.dom.stateLine) {
            return;
        }
        state.dom.stateLine.textContent = data.message || "Idle";
        state.dom.stateLine.classList.remove("idle", "loading", "error", "ready");
        if (data.state) {
            state.dom.stateLine.classList.add(data.state);
        } else {
            state.dom.stateLine.classList.add("idle");
        }
    };

    window.GenAI.explain = {
        init: init,
        setMode: setExplainMode,
        render: renderExplainCurrent
    };
})();
