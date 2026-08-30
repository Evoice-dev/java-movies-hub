package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpHandler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] respBytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, respBytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(respBytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        try (OutputStream os = ex.getResponseBody()) {
            // пусто
        }
    }

    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        String json = "{\"error\":\"" + escapeJson(message) + "\"}";
        sendJson(ex, status, json);
    }

    protected void sendValidationError(HttpExchange ex, String error, java.util.List<String> details) throws IOException {
        String detailsJson = details.stream()
                .map(s -> "\"" + escapeJson(s) + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");
        String json = "{\"error\":\"" + escapeJson(error) + "\",\"details\":" + detailsJson + "}";
        sendJson(ex, 422, json);
    }

    protected String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    protected String readRequestBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    protected boolean isJsonContentType(HttpExchange ex) {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        return contentType != null && contentType.toLowerCase().startsWith("application/json");
    }

    protected boolean isValidYear(int year) {
        int currentYear = Year.now().getValue();
        return year >= 1888 && year <= currentYear + 1;
    }

    protected Long extractIdFromPath(String path) {
        String[] segments = path.split("/");
        if (segments.length < 2) return null;
        String last = segments[segments.length - 1];
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected boolean isBasePath(String path) {
        return path.equals("/movies") || path.equals("/movies/");
    }
}