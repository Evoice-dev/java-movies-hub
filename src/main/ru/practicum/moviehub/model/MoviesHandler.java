package ru.practicum.moviehub.model;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.http.BaseHttpHandler;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if (method.equalsIgnoreCase("GET") && isBasePath(path)) {
            handleGetAll(ex);
            return;
        }

        if (method.equalsIgnoreCase("POST") && isBasePath(path)) {
            handlePost(ex);
            return;
        }

        if ((method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("DELETE")) && path.startsWith("/movies/")) {
            Long id = extractIdFromPath(path);
            if (id == null) {
                sendError(ex, 400, "Некорректный ID");
                return;
            }
            if (method.equalsIgnoreCase("GET")) {
                handleGetById(ex, id);
            } else {
                handleDelete(ex, id);
            }
            return;
        }

        ex.sendResponseHeaders(405, -1);
        try (var os = ex.getResponseBody()) { /* пусто */ }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        String yearParam = getQueryParam(ex, "year");
        if (yearParam != null) {
            handleGetByYear(ex, yearParam);
            return;
        } else if (query != null && !query.isEmpty()) {
            sendError(ex, 400, "Некорректный параметр запроса - только 'year'");
            return;
        }

        List<Movie> movies = store.getAll();
        String json = gson.toJson(movies);
        sendJson(ex, 200, json);
    }

    private void handleGetByYear(HttpExchange ex, String yearParam) throws IOException {
        try {
            int year = Integer.parseInt(yearParam);
            List<Movie> filtered = store.getAll().stream()
                    .filter(m -> m.getYear() == year)
                    .collect(Collectors.toList());
            String json = gson.toJson(filtered);
            sendJson(ex, 200, json);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        if (!isJsonContentType(ex)) {
            sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        String body = readRequestBody(ex);
        Movie incoming;
        try {
            incoming = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }

        List<String> errors = new ArrayList<>();
        if (incoming.getTitle() == null || incoming.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (incoming.getTitle().length() > 100) {
            errors.add("название должно быть не длиннее 100 символов");
        }

        int year = incoming.getYear();
        if (!isValidYear(year)) {
            int currentYear = Year.now().getValue();
            errors.add("год должен быть между " + MIN_YEAR + " и " + (currentYear + 1));
        }

        if (!errors.isEmpty()) {
            sendValidationError(ex, "Ошибка валидации", errors);
            return;
        }

        Movie saved = store.add(incoming);
        String json = gson.toJson(saved);
        sendJson(ex, 201, json);
    }

    private void handleGetById(HttpExchange ex, long id) throws IOException {
        Movie movie = store.getById(id);
        if (movie == null) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }
        String json = gson.toJson(movie);
        sendJson(ex, 200, json);
    }

    private void handleDelete(HttpExchange ex, long id) throws IOException {
        boolean deleted = store.delete(id);
        if (!deleted) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendNoContent(ex);
    }
}