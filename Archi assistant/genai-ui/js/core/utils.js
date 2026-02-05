/* global window, document */
(function() {
    window.GenAI = window.GenAI || {};
    var utils = window.GenAI.utils || {};

    utils.forEach = function(list, callback) {
        for (var i = 0; i < list.length; i++) {
            callback(list[i]);
        }
    };

    utils.decodeParam = function(value) {
        try {
            return decodeURIComponent(value);
        } catch (err) {
            return value;
        }
    };

    utils.escapeHtml = function(value) {
        var div = document.createElement("div");
        div.textContent = value;
        return div.innerHTML;
    };

    utils.generateId = function() {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID();
        }
        return "id-" + Date.now() + "-" + Math.floor(Math.random() * 1000000);
    };

    utils.buildMessage = function(role, content) {
        return {
            id: utils.generateId(),
            role: role,
            content: content,
            created_at: new Date().toISOString()
        };
    };

    window.GenAI.utils = utils;
})();
