/* global window, document */
(function() {
    window.GenAI = window.GenAI || {};
    var utils = window.GenAI.utils || {};

    var forEach = utils.forEach || function(list, callback) {
        for (var i = 0; i < list.length; i++) {
            callback(list[i]);
        }
    };

    var decodeParam = utils.decodeParam || function(value) {
        try {
            return decodeURIComponent(value);
        } catch (err) {
            return value;
        }
    };

    var state = {
        supabaseConfig: {},
        supabaseClient: null,
        authSession: null,
        authAccessToken: null,
        dom: {},
        onLogin: null,
        onAuthExpired: null
    };

    function setAuthMessage(message, type) {
        var authMessage = state.dom.authMessage;
        if (!authMessage) {
            return;
        }
        if (!message) {
            authMessage.textContent = "";
            authMessage.setAttribute("hidden", "");
            authMessage.className = "auth-message";
            return;
        }
        authMessage.textContent = message;
        authMessage.className = "auth-message" + (type ? " " + type : "");
        authMessage.removeAttribute("hidden");
    }

    function setAuthBusy(isBusy) {
        var disabled = !!isBusy;
        var dom = state.dom;
        if (dom.doLoginBtn) dom.doLoginBtn.disabled = disabled;
        if (dom.doRegisterBtn) dom.doRegisterBtn.disabled = disabled;
        if (dom.googleLoginBtn) dom.googleLoginBtn.disabled = disabled;
        if (dom.loginEmail) dom.loginEmail.disabled = disabled;
        if (dom.loginPassword) dom.loginPassword.disabled = disabled;
        if (dom.regEmail) dom.regEmail.disabled = disabled;
        if (dom.regPassword) dom.regPassword.disabled = disabled;
        if (dom.regConfirm) dom.regConfirm.disabled = disabled;
    }

    function syncRedirectUrlFromHost() {
        if (!window.getAuthRedirectUrl) {
            return;
        }
        try {
            var url = window.getAuthRedirectUrl();
            if (url) {
                state.supabaseConfig.redirectUrl = url;
            }
        } catch (err) {
            /* ignore */
        }
    }

    function getSupabaseClient() {
        if (state.supabaseClient) {
            return state.supabaseClient;
        }
        if (!window.supabase || !window.supabase.createClient) {
            setAuthMessage("Supabase SDK not loaded. Check the script include.", "error");
            return null;
        }
        if (!state.supabaseConfig.url || !state.supabaseConfig.anonKey) {
            setAuthMessage("Supabase config missing. Update config.js.", "error");
            return null;
        }
        state.supabaseClient = window.supabase.createClient(state.supabaseConfig.url, state.supabaseConfig.anonKey, {
            auth: {
                detectSessionInUrl: false,
                flowType: "pkce",
                persistSession: true,
                autoRefreshToken: true
            }
        });
        return state.supabaseClient;
    }

    function setAuthSession(session) {
        state.authSession = session || null;
        state.authAccessToken = session && session.access_token ? session.access_token : null;
    }

    function isAuthError(err) {
        if (!err) {
            return false;
        }
        var status = err.status || (err.context && err.context.status);
        if (status === 401 || status === 403) {
            return true;
        }
        var message = err.message || err.error_description || String(err);
        return message.indexOf("Not authenticated") >= 0 ||
            message.indexOf("Unauthorized") >= 0 ||
            message.indexOf("JWT") >= 0;
    }

    function handleAuthFailure(err) {
        if (!isAuthError(err)) {
            return false;
        }
        setAuthSession(null);
        setAuthMessage("Session expired. Please sign in again.", "error");
        if (state.onAuthExpired) {
            state.onAuthExpired();
        }
        return true;
    }

    function getAuthToken(client) {
        if (!client || !client.auth) {
            return Promise.reject(new Error("Supabase auth unavailable."));
        }
        return client.auth.getSession()
            .then(function(res) {
                if (res && res.error) {
                    throw res.error;
                }
                var session = res && res.data ? res.data.session : null;
                if (session && session.access_token) {
                    setAuthSession(session);
                    return session.access_token;
                }
                if (state.authAccessToken) {
                    return state.authAccessToken;
                }
                throw new Error("Not authenticated.");
            })
            .catch(function(err) {
                if (state.authAccessToken) {
                    return state.authAccessToken;
                }
                throw err;
            });
    }

    function openExternalAuthUrl(url) {
        if (!url) {
            return false;
        }
        var openMode = (state.supabaseConfig.openExternalMode || "auto").toLowerCase();
        if (openMode !== "window" && window.javaBridge) {
            var command = state.supabaseConfig.openExternalCommand || "SYSTEM_CMD:OPEN_EXTERNAL_URL:";
            window.javaBridge(command + url);
            return true;
        }
        if (openMode === "javabridge") {
            return false;
        }
        try {
            window.open(url, "_blank");
            return true;
        } catch (err) {
            return false;
        }
    }

    function parseCallbackParams(payload) {
        if (!payload) {
            return {};
        }
        if (typeof payload === "object") {
            return payload;
        }
        var text = String(payload);
        var query = text;
        var questionIndex = query.indexOf("?");
        if (questionIndex >= 0) {
            query = query.slice(questionIndex + 1);
        }
        var hashIndex = query.indexOf("#");
        if (hashIndex >= 0) {
            query = query.slice(0, hashIndex);
        }
        var params = {};
        var pairs = query.split("&");
        for (var i = 0; i < pairs.length; i++) {
            var part = pairs[i];
            if (!part) continue;
            var parts = part.split("=");
            var key = decodeParam(parts[0] || "");
            var value = decodeParam(parts.slice(1).join("="));
            if (key) {
                params[key] = value;
            }
        }
        return params;
    }

    function exchangeCodeForSession(code) {
        var client = getSupabaseClient();
        if (!client) {
            return;
        }
        if (!code) {
            setAuthMessage("Missing auth code.", "error");
            return;
        }
        setAuthMessage("Completing sign-in...", "info");
        setAuthBusy(true);
        client.auth.exchangeCodeForSession(code)
            .then(function(res) {
                setAuthBusy(false);
                if (res.error) {
                    setAuthMessage(res.error.message || "OAuth exchange failed.", "error");
                    return;
                }
                if (res.data && res.data.session && res.data.session.user) {
                    setAuthSession(res.data.session);
                    setAuthMessage("", "");
                    if (state.onLogin) {
                        state.onLogin(res.data.session.user);
                    }
                    return;
                }
                setAuthMessage("Sign-in completed, but no session returned.", "error");
            })
            .catch(function(err) {
                setAuthBusy(false);
                setAuthMessage((err && err.message) || "OAuth exchange failed.", "error");
            });
    }

    function initAuth() {
        var dom = state.dom;

        if (dom.doLoginBtn) {
            dom.doLoginBtn.addEventListener('click', function() {
                setAuthMessage("", "");
                var client = getSupabaseClient();
                if (!client) return;
                var email = dom.loginEmail ? dom.loginEmail.value.trim() : "";
                var password = dom.loginPassword ? dom.loginPassword.value : "";
                if (!email || !password) {
                    setAuthMessage("Enter email and password.", "error");
                    return;
                }
                setAuthBusy(true);
                client.auth.signInWithPassword({ email: email, password: password })
                    .then(function(res) {
                        setAuthBusy(false);
                        if (res.error) {
                            setAuthMessage(res.error.message || "Sign-in failed.", "error");
                            return;
                        }
                        if (res.data && res.data.session) {
                            setAuthSession(res.data.session);
                        }
                        if (res.data && res.data.user) {
                            setAuthMessage("", "");
                            if (state.onLogin) {
                                state.onLogin(res.data.user);
                            }
                            return;
                        }
                        setAuthMessage("Sign-in succeeded, but no user returned.", "error");
                    })
                    .catch(function(err) {
                        setAuthBusy(false);
                        setAuthMessage((err && err.message) || "Sign-in failed.", "error");
                    });
            });
        }

        if (dom.doRegisterBtn) {
            dom.doRegisterBtn.addEventListener('click', function() {
                setAuthMessage("", "");
                var client = getSupabaseClient();
                if (!client) return;
                var email = dom.regEmail ? dom.regEmail.value.trim() : "";
                var password = dom.regPassword ? dom.regPassword.value : "";
                var confirm = dom.regConfirm ? dom.regConfirm.value : "";
                if (!email || !password) {
                    setAuthMessage("Enter email and password.", "error");
                    return;
                }
                if (password !== confirm) {
                    setAuthMessage("Passwords do not match.", "error");
                    return;
                }
                setAuthBusy(true);
                syncRedirectUrlFromHost();
                var signUpPayload = { email: email, password: password };
                if (state.supabaseConfig.redirectUrl) {
                    signUpPayload.options = { emailRedirectTo: state.supabaseConfig.redirectUrl };
                }
                client.auth.signUp(signUpPayload)
                    .then(function(res) {
                        setAuthBusy(false);
                        if (res.error) {
                            setAuthMessage(res.error.message || "Sign-up failed.", "error");
                            return;
                        }
                        if (res.data && res.data.session) {
                            setAuthSession(res.data.session);
                        }
                        if (res.data && res.data.session && res.data.session.user) {
                            setAuthMessage("", "");
                            if (state.onLogin) {
                                state.onLogin(res.data.session.user);
                            }
                            return;
                        }
                        setAuthMessage("Check your email to confirm your account.", "info");
                    })
                    .catch(function(err) {
                        setAuthBusy(false);
                        setAuthMessage((err && err.message) || "Sign-up failed.", "error");
                    });
            });
        }

        if (dom.googleLoginBtn) {
            dom.googleLoginBtn.addEventListener('click', function() {
                setAuthMessage("", "");
                var client = getSupabaseClient();
                if (!client) return;
                syncRedirectUrlFromHost();
                if (!state.supabaseConfig.redirectUrl) {
                    setAuthMessage("Missing redirect URL. Update config.js.", "error");
                    return;
                }
                setAuthBusy(true);
                setAuthMessage("Opening external browser for Google sign-in...", "info");
                client.auth.signInWithOAuth({
                    provider: "google",
                    options: {
                        redirectTo: state.supabaseConfig.redirectUrl,
                        skipBrowserRedirect: true
                    }
                })
                    .then(function(res) {
                        setAuthBusy(false);
                        if (res.error) {
                            setAuthMessage(res.error.message || "Google sign-in failed.", "error");
                            return;
                        }
                        if (res.data && res.data.url) {
                            if (!openExternalAuthUrl(res.data.url)) {
                                setAuthMessage("Unable to open external browser. Copy the URL and open it manually.", "error");
                            }
                            return;
                        }
                        setAuthMessage("Missing OAuth URL from Supabase.", "error");
                    })
                    .catch(function(err) {
                        setAuthBusy(false);
                        setAuthMessage((err && err.message) || "Google sign-in failed.", "error");
                    });
            });
        }

        var tabs = document.querySelectorAll('.tab-btn');
        var loginForm = document.getElementById('loginForm');
        var registerForm = document.getElementById('registerForm');

        forEach(tabs, function(tab) {
            tab.addEventListener('click', function() {
                forEach(tabs, function(t) { t.classList.remove('active'); });
                tab.classList.add('active');
                setAuthMessage("", "");

                var target = tab.getAttribute('data-tab');
                if(target === 'login') {
                    loginForm.removeAttribute('hidden');
                    loginForm.style.display = 'block';
                    registerForm.setAttribute('hidden', '');
                    registerForm.style.display = 'none';
                } else {
                    registerForm.removeAttribute('hidden');
                    registerForm.style.display = 'block';
                    loginForm.setAttribute('hidden', '');
                    loginForm.style.display = 'none';
                }
            });
        });
    }

    function initSupabaseAuth() {
        var client = getSupabaseClient();
        if (!client) {
            return;
        }
        syncRedirectUrlFromHost();
        client.auth.getSession()
            .then(function(res) {
                if (res.data && res.data.session) {
                    setAuthSession(res.data.session);
                }
                if (res.data && res.data.session && res.data.session.user) {
                    setAuthMessage("", "");
                    if (state.onLogin) {
                        state.onLogin(res.data.session.user);
                    }
                }
            })
            .catch(function() { /* ignore */ });
    }

    function signOut() {
        if (state.supabaseClient && state.supabaseClient.auth) {
            state.supabaseClient.auth.signOut();
        }
        setAuthSession(null);
    }

    function init(options) {
        var opts = options || {};
        state.supabaseConfig = opts.supabaseConfig || {};
        state.dom = opts.dom || {};
        state.onLogin = opts.onLogin || null;
        state.onAuthExpired = opts.onAuthExpired || null;

        window.handleSupabaseCallback = function(payload) {
            var params = parseCallbackParams(payload);
            if (params.error) {
                setAuthMessage(params.error_description || params.error, "error");
                return;
            }
            exchangeCodeForSession(params.code);
        };

        window.handleSupabaseCode = function(code) {
            exchangeCodeForSession(code);
        };
    }

    window.GenAI.auth = {
        init: init,
        initAuth: initAuth,
        initSupabaseAuth: initSupabaseAuth,
        getClient: getSupabaseClient,
        getAuthToken: getAuthToken,
        isAuthError: isAuthError,
        handleAuthFailure: handleAuthFailure,
        setAuthMessage: setAuthMessage,
        setAuthBusy: setAuthBusy,
        setSession: setAuthSession,
        signOut: signOut
    };
})();
