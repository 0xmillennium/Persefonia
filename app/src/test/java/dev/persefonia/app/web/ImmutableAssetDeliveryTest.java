package dev.persefonia.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import dev.persefonia.app.assets.ViteAssetResolver;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(PublicHomeTestConfiguration.class)
@ActiveProfiles({"test", "public-home-mvc-test"})
class ImmutableAssetDeliveryTest {
    @Autowired MockMvc mvc;
    @Autowired ViteAssetResolver assets;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void productionCompression(DynamicPropertyRegistry registry) throws Exception {
        var source = new YamlPropertySourceLoader().load("production-compression",
                new ClassPathResource("application-prod.yml")).getFirst();
        for (String property : List.of("server.compression.enabled", "server.compression.min-response-size",
                "server.compression.mime-types")) {
            registry.add(property, () -> source.getProperty(property));
        }
    }

    @Test
    void productionTextCompressionPreservesNativeVideoRanges() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var script = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + assets.scriptPath("src/home.ts"))).header("Accept-Encoding", "gzip").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            org.junit.jupiter.api.Assertions.assertEquals(200, script.statusCode());
            org.junit.jupiter.api.Assertions.assertEquals("gzip", script.headers().firstValue("Content-Encoding").orElseThrow());
            org.junit.jupiter.api.Assertions.assertEquals(0x1f, script.body()[0] & 0xff);
            org.junit.jupiter.api.Assertions.assertEquals(0x8b, script.body()[1] & 0xff);
            List<String> paths;
            try (var input = new ClassPathResource("static/assets/.vite/immutable-assets.json").getInputStream()) {
                paths = mapper.readValue(input, new TypeReference<List<String>>() {});
            }
            String video = paths.stream().filter(path -> path.endsWith("/960/tree.mp4")).findFirst().orElseThrow();
            var range = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + video))
                    .header("Accept-Encoding", "gzip").header("Range", "bytes=0-15").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            org.junit.jupiter.api.Assertions.assertEquals(206, range.statusCode());
            org.junit.jupiter.api.Assertions.assertEquals(16, range.body().length);
            org.junit.jupiter.api.Assertions.assertTrue(range.headers().firstValue("Content-Encoding").isEmpty());
        }
    }

    @Test
    void cachesOnlyQualifiedReleaseAssetsWithoutWeakeningMutableOrErrorResponses() throws Exception {
        mvc.perform(get(assets.scriptPath("src/home.ts")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().exists("Content-Security-Policy"));
        for (String path : List.of("/assets/home-mark.svg", "/theme.css")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
        }
        for (String path : List.of("/assets/not-a-release-12345678.js",
                "/assets/home-videos/manifest.json", "/assets/.vite/immutable-assets.json")) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
        }
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, no-cache, must-revalidate"));
    }

    @Test
    void immutableVideoHeadReturnsTheSameMetadataWithoutAResponseBody() throws Exception {
        List<String> paths;
        try (var input = new ClassPathResource("static/assets/.vite/immutable-assets.json").getInputStream()) {
            paths = mapper.readValue(input, new TypeReference<List<String>>() {});
        }
        String video = paths.stream().filter(path -> path.endsWith("/960/tree.mp4")).findFirst().orElseThrow();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + video))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode());
            org.junit.jupiter.api.Assertions.assertEquals(0, response.body().length);
            org.junit.jupiter.api.Assertions.assertEquals("video/mp4", response.headers().firstValue("Content-Type").orElseThrow());
            org.junit.jupiter.api.Assertions.assertEquals("bytes", response.headers().firstValue("Accept-Ranges").orElseThrow());
            org.junit.jupiter.api.Assertions.assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("immutable"));
            org.junit.jupiter.api.Assertions.assertEquals(new ClassPathResource("static" + video).contentLength(),
                    response.headers().firstValueAsLong("Content-Length").orElseThrow());
        }
    }

    @Test
    void immutableVideoDeliveryRetainsNativeByteRanges() throws Exception {
        List<String> paths;
        try (var input = new ClassPathResource("static/assets/.vite/immutable-assets.json").getInputStream()) {
            paths = mapper.readValue(input, new TypeReference<List<String>>() {});
        }
        String video = paths.stream().filter(path -> path.endsWith("/960/tree.mp4")).findFirst().orElseThrow();
        mvc.perform(get(video).header("Range", "bytes=0-15"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", "16"))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }
}
