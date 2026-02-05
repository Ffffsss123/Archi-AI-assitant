package com.cwdil.archi.genai.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class TestGeminiServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"text\": \"ok\"}";

    TestGeminiServer() throws IOException {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        server = HttpServer.create(address, 0);
        server.createContext("/v1beta/models/gemini-2.0-flash:generateContent", this::handle);
        server.start();
    }

    URI getEndpointUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/v1beta/models/gemini-2.0-flash:generateContent");
    }

    void setResponse(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    String getLastApiKey() {
        return lastApiKey.get();
    }

    String getLastRequestBody() {
        return lastRequestBody.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastApiKey.set(exchange.getRequestHeaders().getFirst("X-goog-api-key"));
        lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(responseStatus, body.length);
        try(OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
