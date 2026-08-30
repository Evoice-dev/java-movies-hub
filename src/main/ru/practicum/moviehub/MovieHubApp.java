package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;

import java.io.IOException;

public class MovieHubApp {
    public static void main(String[] args) throws IOException {
        MoviesServer server = new MoviesServer();
        server.start();
    }
}