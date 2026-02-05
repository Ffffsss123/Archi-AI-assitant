package com.cwdil.archi.genai.auth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class LocalAuthCallbackServer {

    public static final String CALLBACK_PATH = "/auth/callback";

    public static final class CallbackData {
        private final String fullUrl;
        private final String rawQuery;
        private final Map<String, String> params;

        private CallbackData(String fullUrl, String rawQuery, Map<String, String> params) {
            this.fullUrl = fullUrl;
            this.rawQuery = rawQuery;
            this.params = params;
        }

        public String getFullUrl() {
            return fullUrl;
        }

        public String getRawQuery() {
            return rawQuery;
        }

        public Map<String, String> getParams() {
            return params;
        }
    }

    public interface CallbackListener {
        void onCallback(CallbackData data);
    }

    private static final LocalAuthCallbackServer INSTANCE = new LocalAuthCallbackServer();

    private final List<CallbackListener> listeners = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private ExecutorService executor;
    private volatile CallbackData lastCallback;

    private LocalAuthCallbackServer() {}

    public static LocalAuthCallbackServer getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if(server != null) {
            return;
        }
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
            server = HttpServer.create(address, 0);
            executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "GenAI-Auth-Callback");
                    thread.setDaemon(true);
                    return thread;
                }
            });
            server.setExecutor(executor);
            server.createContext(CALLBACK_PATH, this::handleCallback);
            server.start();
            System.out.println("Local auth callback server listening on " + getRedirectUrl());
        }
        catch(IOException ex) {
            System.err.println("Failed to start local auth callback server.");
            ex.printStackTrace();
            stop();
        }
    }

    public synchronized void stop() {
        if(server != null) {
            server.stop(0);
            server = null;
        }
        if(executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        lastCallback = null;
    }

    public String getRedirectUrl() {
        int port = getPort();
        if(port <= 0) {
            return "";
        }
        return "http://127.0.0.1:" + port + CALLBACK_PATH;
    }

    public int getPort() {
        if(server == null) {
            return -1;
        }
        return server.getAddress().getPort();
    }

    public void addListener(CallbackListener listener) {
        if(listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(CallbackListener listener) {
        if(listener != null) {
            listeners.remove(listener);
        }
    }

    public CallbackData consumeLastCallback() {
        CallbackData data = lastCallback;
        lastCallback = null;
        return data;
    }

    public void clearLastCallback(CallbackData data) {
        if(data != null && lastCallback == data) {
            lastCallback = null;
        }
    }

    private void handleCallback(HttpExchange exchange) throws IOException {
        URI requestUri = exchange.getRequestURI();
        String rawQuery = requestUri.getRawQuery();
        Map<String, String> params = parseQuery(rawQuery);
        String fullUrl = "http://127.0.0.1:" + getPort() + requestUri.toString();
        System.out.println("Auth callback received: " + requestUri + " params=" + params);

        CallbackData data = new CallbackData(fullUrl, rawQuery, params);
        lastCallback = data;
        notifyListeners(data);

        byte[] body = buildResponseBody(params).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try(OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void notifyListeners(CallbackData data) {
        for(CallbackListener listener : listeners) {
            try {
                listener.onCallback(data);
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        if(rawQuery == null || rawQuery.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        String[] pairs = rawQuery.split("&");
        for(String pair : pairs) {
            if(pair.isEmpty()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String key = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String value = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        catch(Exception ex) {
            return value;
        }
    }

    private String buildResponseBody(Map<String, String> params) {
        String error = params.get("error");
        if(error != null && !error.isEmpty()) {
            return "<!doctype html><html><head><meta charset=\"utf-8\">" +
                    "<title>Sign-in failed</title></head><body>" +
                    "<h2>Sign-in failed</h2><p>You can close this window.</p></body></html>";
        }
        String code = params.get("code");
        if(code == null || code.isEmpty()) {
            return "<!doctype html><html><head><meta charset=\"utf-8\">" +
                    "<title>Missing auth code</title></head><body>" +
                    "<h2>Missing auth code</h2><p>Check OAuth flow configuration.</p></body></html>";
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
                "<title>Sign-in complete</title></head><body>" +
                "<h2>Sign-in complete</h2><p>You can close this window.</p></body></html>";
    }
}
