/* global window, document */
(function() {
    window.GenAI = window.GenAI || {};
    var theme = window.GenAI.theme || {};

    var state = {
        buttons: [],
        preferenceKey: "genai.theme",
        currentTheme: "light"
    };

    function normalizeThemePreference(value) {
        return value === "dark" ? "dark" : "light";
    }

    function loadThemePreference() {
        try {
            return normalizeThemePreference(localStorage.getItem(state.preferenceKey));
        } catch (err) {
            return "light";
        }
    }

    function storeThemePreference(value) {
        try {
            localStorage.setItem(state.preferenceKey, value);
        } catch (err) {
            /* ignore */
        }
    }

    function updateThemeButtons() {
        var buttons = state.buttons;
        if (!buttons || !buttons.length) {
            return;
        }
        for (var i = 0; i < buttons.length; i++) {
            var btn = buttons[i];
            var btnTheme = normalizeThemePreference(btn.getAttribute("data-theme"));
            var isActive = btnTheme === state.currentTheme;
            if (isActive) {
                btn.classList.add("is-active");
            } else {
                btn.classList.remove("is-active");
            }
            btn.setAttribute("aria-pressed", isActive ? "true" : "false");
        }
    }

    function applyTheme(value, options) {
        var normalized = normalizeThemePreference(value);
        state.currentTheme = normalized;
        if (document.body) {
            if (normalized === "dark") {
                document.body.classList.add("theme-dark");
            } else {
                document.body.classList.remove("theme-dark");
            }
        }
        updateThemeButtons();
        if (!options || options.persist !== false) {
            storeThemePreference(normalized);
        }
    }

    theme.init = function(options) {
        var opts = options || {};
        if (opts.buttons) {
            state.buttons = opts.buttons;
        }
        if (opts.preferenceKey) {
            state.preferenceKey = opts.preferenceKey;
        }
        if (opts.currentTheme) {
            state.currentTheme = normalizeThemePreference(opts.currentTheme);
        }
        updateThemeButtons();
    };

    theme.setButtons = function(buttons) {
        state.buttons = buttons || [];
        updateThemeButtons();
    };

    theme.normalize = normalizeThemePreference;
    theme.loadPreference = loadThemePreference;
    theme.apply = applyTheme;
    theme.getCurrent = function() {
        return state.currentTheme;
    };

    window.GenAI.theme = theme;
})();
