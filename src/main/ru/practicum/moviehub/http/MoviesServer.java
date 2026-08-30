package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.model.MoviesHandler;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    private final MoviesStore store;

    public MoviesServer() throws IOException {
        this.store = new MoviesStore();
        this.server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/movies", new MoviesHandler(store));
        server.setExecutor(null); // использовать стандартный Executor
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен на http://localhost:8080");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Сервер остановлен");
    }

    public MoviesStore getStore() {
        return store;
    }
}