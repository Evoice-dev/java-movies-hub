package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {
    private static final String BASE_URL = "http://localhost:8080";
    private static final Gson gson = new Gson();
    private static final ListOfMoviesTypeToken LIST_TYPE_TOKEN = new ListOfMoviesTypeToken();
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() throws Exception {
        server = new MoviesServer();
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @BeforeEach
    void setUp() {
        server.getStore().clear();
    }

    @AfterAll
    static void afterAll() throws Exception {
        if (server != null) {
            server.stop();
        }
        client = null;
    }

    @Test
    @DisplayName("GET /movies при пустом хранилище возвращает пустой массив")
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue);

        List<Movie> movies = gson.fromJson(resp.body(), LIST_TYPE_TOKEN.getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    @DisplayName("GET /movies возвращает список ранее добавленных фильмов")
    void getMovies_whenHasMovies_returnsList() throws Exception {
        Movie movie = new Movie("Inception", 2010);
        server.getStore().add(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        List<Movie> movies = gson.fromJson(resp.body(), LIST_TYPE_TOKEN.getType());
        assertEquals(1, movies.size());
        Movie actual = movies.getFirst();
        assertEquals("Inception", actual.getTitle());
        assertEquals(2010, actual.getYear());
        assertTrue(actual.getId() > 0);
    }

    @Test
    @DisplayName("POST /movies с корректными данными возвращает 201 и созданный фильм")
    void postMovie_valid_returns201() throws Exception {
        Movie movie = new Movie("The Matrix", 1999);
        String json = gson.toJson(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode());
        assertEquals("application/json; charset=UTF-8", resp.headers().firstValue("Content-Type").orElse(""));
        Movie created = gson.fromJson(resp.body(), Movie.class);
        assertEquals("The Matrix", created.getTitle());
        assertEquals(1999, created.getYear());
        assertTrue(created.getId() > 0);
    }

    @Test
    @DisplayName("POST /movies с пустым title возвращает 422")
    void postMovie_emptyTitle_returns422() throws Exception {
        String json = "{\"title\":\"\",\"year\":2000}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());
        JsonObject errorObj = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", errorObj.get("error").getAsString());
        JsonArray details = errorObj.get("details").getAsJsonArray();
        assertTrue(details.size() > 0);
        assertTrue(details.toString().contains("название не должно быть пустым"));
    }

    @Test
    @DisplayName("POST /movies с названием длиннее 100 символов возвращает 422")
    void postMovie_titleTooLong_returns422() throws Exception {
        String longTitle = "a".repeat(101);
        String json = "{\"title\":\"" + longTitle + "\",\"year\":2000}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());
        JsonObject errorObj = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray details = errorObj.get("details").getAsJsonArray();
        assertTrue(details.toString().contains("название должно быть не длиннее 100 символов"));
    }

    @Test
    @DisplayName("POST /movies с неверным годом возвращает 422")
    void postMovie_invalidYear_returns422() throws Exception {
        int currentYear = Year.now().getValue();

        String jsonLow = "{\"title\":\"Old\",\"year\":1800}";
        HttpRequest reqLow = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonLow, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respLow = client.send(reqLow, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, respLow.statusCode());
        JsonObject errorLow = JsonParser.parseString(respLow.body()).getAsJsonObject();
        assertTrue(errorLow.get("details").getAsJsonArray().toString().contains("год должен быть между 1888 и " + (currentYear + 1)));

        String jsonHigh = "{\"title\":\"Future\",\"year\":" + (currentYear + 2) + "}";
        HttpRequest reqHigh = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonHigh, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respHigh = client.send(reqHigh, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, respHigh.statusCode());
        JsonObject errorHigh = JsonParser.parseString(respHigh.body()).getAsJsonObject();
        assertTrue(errorHigh.get("details").getAsJsonArray().toString().contains("год должен быть между 1888 и " + (currentYear + 1)));
    }

    @Test
    @DisplayName("POST /movies с неправильным Content-Type возвращает 415")
    void postMovie_wrongContentType_returns415() throws Exception {
        String json = "{\"title\":\"Test\",\"year\":2000}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode());
        assertTrue(resp.body().contains("Unsupported Media Type"));
    }

    @Test
    @DisplayName("POST /movies без Content-Type возвращает 415")
    void postMovie_noContentType_returns415() throws Exception {
        String json = "{\"title\":\"Test\",\"year\":2000}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(415, resp.statusCode());
        assertTrue(resp.body().contains("Unsupported Media Type"));
    }

    @Test
    @DisplayName("POST /movies с некорректным JSON возвращает 400")
    void postMovie_malformedJson_returns400() throws Exception {
        String json = "{\"title\":\"Bad\",\"year\":2000";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный JSON"));
    }

    @Test
    @DisplayName("GET /movies/{id} по существующему id возвращает фильм")
    void getMovieById_existing_returns200() throws Exception {
        Movie added = addMovie("Interstellar", 2014);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + added.getId()))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertEquals(added.getId(), movie.getId());
        assertEquals("Interstellar", movie.getTitle());
        assertEquals(2014, movie.getYear());
    }

    @Test
    @DisplayName("GET /movies/{id} с несуществующим id возвращает 404")
    void getMovieById_notFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/9999"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    @DisplayName("GET /movies/{id} с некорректным id возвращает 400")
    void getMovieById_invalidId_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/abc"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    @DisplayName("DELETE /movies/{id} по существующему id удаляет фильм и возвращает 204")
    void deleteMovie_existing_returns204() throws Exception {
        Movie added = addMovie("Dunkirk", 2017);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + added.getId()))
                .DELETE()
                .build();
        HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());

        assertEquals(204, resp.statusCode());

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + added.getId()))
                .GET()
                .build();
        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, getResp.statusCode());
    }

    @Test
    @DisplayName("DELETE /movies/{id} с несуществующим id возвращает 404")
    void deleteMovie_notFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/9999"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    @DisplayName("DELETE /movies/{id} с некорректным id возвращает 400")
    void deleteMovie_invalidId_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/abc"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    @DisplayName("GET /movies?year=YYYY возвращает фильмы указанного года")
    void getMoviesByYear_valid_returnsFilteredList() throws Exception {
        addMovie("Movie 1", 2000);
        addMovie("Movie 2", 2001);
        addMovie("Movie 3", 2000);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=2000"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        List<Movie> movies = gson.fromJson(resp.body(), LIST_TYPE_TOKEN.getType());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2000));
    }

    @Test
    @DisplayName("GET /movies?year=YYYY возвращает пустой список, если фильмов с таким годом нет")
    void getMoviesByYear_noMatches_returnsEmptyArray() throws Exception {
        addMovie("Movie", 1999);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=2020"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        List<Movie> movies = gson.fromJson(resp.body(), LIST_TYPE_TOKEN.getType());
        assertTrue(movies.isEmpty());
    }

    @Test
    @DisplayName("GET /movies?year=YYYY с нечисловым значением возвращает 400")
    void getMoviesByYear_invalidYearParam_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies?year=abc"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        JsonObject errorObj = JsonParser.parseString(resp.body()).getAsJsonObject();
        String errorMessage = errorObj.get("error").getAsString();
        assertTrue(errorMessage.contains("Некорректный параметр запроса -"));
    }

    private Movie addMovie(String title, int year) throws Exception {
        Movie movie = new Movie(title, year);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, resp.statusCode());
        return gson.fromJson(resp.body(), Movie.class);
    }
}