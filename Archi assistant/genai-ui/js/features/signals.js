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
        compactRaf: null,
        lastChecksDetail: ""
    };

    function setSignalsButtonCompact(isCompact) {
        if (!state.dom.signalsBtn) {
            return;
        }
        if (isCompact) {
            state.dom.signalsBtn.classList.add('signals-toggle--icononly');
        } else {
            state.dom.signalsBtn.classList.remove('signals-toggle--icononly');
        }
    }

    function getSignalsColumnCount() {
        if (!state.dom.signalsGrid) {
            return 0;
        }
        var items = state.dom.signalsGrid.querySelectorAll('.signal');
        if (!items.length || state.dom.signalsGrid.offsetParent === null) {
            return 0;
        }
        var firstTop = items[0].offsetTop;
        var columns = 0;
        forEach(items, function(item) {
            if (Math.abs(item.offsetTop - firstTop) < 2) {
                columns += 1;
            }
        });
        return columns;
    }

    function estimateSignalsColumns() {
        var containerWidth = 0;
        if (state.dom.signalsGrid && state.dom.signalsGrid.clientWidth) {
            containerWidth = state.dom.signalsGrid.clientWidth;
        }
        if (!containerWidth && state.dom.signalsDrawer) {
            containerWidth = state.dom.signalsDrawer.clientWidth;
        }
        if (!containerWidth && state.dom.signalsPanel) {
            containerWidth = state.dom.signalsPanel.clientWidth;
        }
        if (!containerWidth) {
            return 0;
        }
        var minWidth = 140;
        var gap = 8;
        var columns = Math.floor((containerWidth + gap) / (minWidth + gap));
        return columns > 0 ? columns : 1;
    }

    function updateSignalsCompactMode() {
        if (!state.dom.signalsBtn) {
            return;
        }
        var columns = getSignalsColumnCount();
        if (!columns) {
            columns = estimateSignalsColumns();
        }
        if (!columns) {
            return;
        }
        setSignalsButtonCompact(columns <= 2);
    }

    function scheduleSignalsCompactUpdate() {
        if (!window.requestAnimationFrame) {
            updateSignalsCompactMode();
            return;
        }
        if (state.compactRaf) {
            return;
        }
        state.compactRaf = window.requestAnimationFrame(function() {
            state.compactRaf = null;
            updateSignalsCompactMode();
        });
    }

    function setSignalsExpanded(expanded) {
        if(!state.dom.signalsPanel) {
            return;
        }
        if(expanded) {
            state.dom.signalsPanel.removeAttribute('hidden');
        } else {
            state.dom.signalsPanel.setAttribute('hidden', '');
        }
        if(state.dom.signalsBtn) {
            state.dom.signalsBtn.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        }
        if(state.dom.signalsDrawer) {
            state.dom.signalsDrawer.setAttribute('data-collapsed', expanded ? 'false' : 'true');
        }
        scheduleSignalsCompactUpdate();
    }

    function toggleSignalsPanel() {
        if(!state.dom.signalsPanel) {
            return;
        }
        setSignalsExpanded(state.dom.signalsPanel.hasAttribute('hidden'));
    }

    function setWorkspaceSignalsVisible(visible) {
        setSignalsExpanded(visible);
    }

    function setChecksState(status) {
        if (!state.dom.wsChecks) return;
        state.dom.wsChecks.classList.remove("success", "warning", "error");
        if (status) {
            state.dom.wsChecks.classList.add(status);
        }
    }

    function setStatusState(status) {
        if (!state.dom.wsStatus) return;
        state.dom.wsStatus.classList.remove("idle", "loading", "error", "ready");
        state.dom.wsStatus.classList.add(status || "idle");
    }

    function setChecksDetailAvailability(isAvailable) {
        var enabled = !!isAvailable;
        if (state.dom.checksSignal) {
            if (enabled) {
                state.dom.checksSignal.classList.add("signal-clickable");
                state.dom.checksSignal.setAttribute("role", "button");
                state.dom.checksSignal.setAttribute("tabindex", "0");
                state.dom.checksSignal.setAttribute("aria-label", "View validation details");
            } else {
                state.dom.checksSignal.classList.remove("signal-clickable");
                state.dom.checksSignal.removeAttribute("role");
                state.dom.checksSignal.removeAttribute("tabindex");
                state.dom.checksSignal.removeAttribute("aria-label");
            }
        }
        if (state.dom.checksDetailLink) {
            state.dom.checksDetailLink.style.display = enabled ? "inline-flex" : "none";
        }
    }

    function init(options) {
        var opts = options || {};
        state.dom = opts.dom || {};

        if (state.dom.signalsPanel) {
            setSignalsExpanded(!state.dom.signalsPanel.hasAttribute('hidden'));
        }
        scheduleSignalsCompactUpdate();
        window.addEventListener('resize', scheduleSignalsCompactUpdate);

        setChecksDetailAvailability(false);
        if (state.dom.closeChecksDetailBtn) {
            state.dom.closeChecksDetailBtn.addEventListener('click', function() {
                if (state.dom.checksDetailModal) {
                    state.dom.checksDetailModal.setAttribute('hidden', '');
                    state.dom.checksDetailModal.style.display = 'none';
                }
            });
        }
        if (state.dom.closeChecksDetailBtn2) {
            state.dom.closeChecksDetailBtn2.addEventListener('click', function() {
                if (state.dom.checksDetailModal) {
                    state.dom.checksDetailModal.setAttribute('hidden', '');
                    state.dom.checksDetailModal.style.display = 'none';
                }
            });
        }
        if (state.dom.checksSignal) {
            state.dom.checksSignal.addEventListener('click', function() {
                if (state.dom.checksSignal.classList.contains("signal-clickable")) {
                    window.showChecksDetail();
                }
            });
            state.dom.checksSignal.addEventListener('keydown', function(event) {
                if (!state.dom.checksSignal.classList.contains("signal-clickable")) {
                    return;
                }
                if (event.key === "Enter" || event.key === " " || event.key === "Spacebar") {
                    event.preventDefault();
                    window.showChecksDetail();
                }
            });
        }
    }

    window.updateWorkspaceSignals = function(data) {
        if (!data) return;

        if (state.dom.wsActiveView) {
            state.dom.wsActiveView.textContent = data.activeView || "None";
        }
        if (state.dom.wsSelectedCount) {
            var label = data.selectedCount === 1 ? "1 node" : data.selectedCount + " nodes";
            state.dom.wsSelectedCount.textContent = label;
        }
        if (state.dom.wsChecks) {
            state.dom.wsChecks.textContent = data.checksText || "Unknown";
            setChecksState(data.checksState);
        }
        var showChecksDetail = !!(data.checksState && data.checksState !== "success");
        if (data.checksDetail) {
            state.lastChecksDetail = data.checksDetail;
        } else if (showChecksDetail) {
            state.lastChecksDetail = "";
        }
        setChecksDetailAvailability(showChecksDetail);
        if (state.dom.wsStatus) {
            state.dom.wsStatus.textContent = data.statusText || "Idle";
            setStatusState(data.statusState);
        }
    };

    window.showChecksDetail = function() {
        if (state.dom.checksDetailModal && state.dom.checksDetailContent) {
            if (state.lastChecksDetail && state.lastChecksDetail.trim().length > 0) {
                state.dom.checksDetailContent.innerHTML = state.lastChecksDetail;
            } else {
                state.dom.checksDetailContent.innerHTML = "<p>No issues found.</p>";
            }
            state.dom.checksDetailModal.removeAttribute('hidden');
            state.dom.checksDetailModal.style.display = 'flex';
        }
    };

    window.GenAI.signals = {
        init: init,
        setExpanded: setSignalsExpanded,
        toggle: toggleSignalsPanel,
        setVisible: setWorkspaceSignalsVisible,
        scheduleCompact: scheduleSignalsCompactUpdate
    };
})();
