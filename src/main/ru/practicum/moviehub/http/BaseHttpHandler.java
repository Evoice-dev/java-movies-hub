package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final int MIN_YEAR = 1888;
    protected final Gson gson = new Gson();

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
        ErrorResponse errorResponse = new ErrorResponse(message);
        String json = gson.toJson(errorResponse);
        sendJson(ex, status, json);
    }

    protected void sendValidationError(HttpExchange ex, String error, List<String> details) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(error, details);
        String json = gson.toJson(errorResponse);
        sendJson(ex, 422, json);
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
        return year >= MIN_YEAR && year <= currentYear + 1;
    }

    protected Long extractIdFromPath(String path) {
        String trimmed = path.replaceAll("^/|/$", "");
        String[] segments = trimmed.split("/");
        if (segments.length != 2) return null;
        if (!"movies".equals(segments[0])) return null;
        try {
            return Long.parseLong(segments[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected boolean isBasePath(String path) {
        return path.equals("/movies") || path.equals("/movies/");
    }

    protected String getQueryParam(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) return null;
        String[] params = query.split("&");
        for (String param : params) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }
}