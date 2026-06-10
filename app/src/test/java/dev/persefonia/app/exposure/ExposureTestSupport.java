package dev.persefonia.app.exposure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final class ExposureTestSupport {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private ExposureTestSupport() {
    }

    static HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static void assertStatus(int port, String path, int expectedStatus) throws IOException, InterruptedException {
        assertEquals(expectedStatus, get(port, path).statusCode(), path);
    }

    static void assertNotSuccessful(int port, String path) throws IOException, InterruptedException {
        int statusCode = get(port, path).statusCode();
        assertFalse(statusCode >= 200 && statusCode < 300, path);
    }
}
