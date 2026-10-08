package pl.edu.parallelization.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.edu.parallelization.application.ParallelizationApplication;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ParallelizationHttpServerTest {
    private ParallelizationHttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String base;
    @TempDir Path directory;

    @BeforeEach
    void start() throws Exception {
        server = new ParallelizationHttpServer(0);
        server.start();
        base = "http://" + (server.address().getAddress().getHostAddress().contains(":")
                ? "[" + server.address().getAddress().getHostAddress() + "]"
                : server.address().getAddress().getHostAddress()) + ":" + server.address().getPort();
        assertTrue(server.address().getAddress().isLoopbackAddress());
    }

    @AfterEach
    void stop() {
        server.close();
        client.close();
    }

    @Test
    void health() throws Exception {
        var response = request("GET", "/api/health", "");
        assertEquals(200, response.statusCode());
        assertEquals("UP", json.readTree(response.body()).get("status").asText());
        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"input.json", "examples/shared-resources.json"})
    void httpReturnsExactlyTheFileInterfaceReport(String input) throws Exception {
        Path output = directory.resolve("output.json");
        new ParallelizationApplication().run(Path.of(input), output);
        var response = request("POST", "/api/parallelization", Files.readString(Path.of(input)));
        assertEquals(200, response.statusCode());
        JsonNode report = json.readTree(response.body());
        assertEquals(json.readTree(output.toFile()), report);
        for (String field : new String[]{"status", "normalizedConstraints", "optimalGroupCount", "solutionCount", "solutions"}) {
            assertTrue(report.has(field), field);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "[]", "{}", "{\"stages\":null}",
            "{\"stages\":[]} {}", "{\"stages\":[],\"constraints\":[]}",
            "{\"stages\":[{\"id\":\"A\",\"name\":\"A\",\"funded\":\"true\"}]}",
            "{\"stages\":[{\"id\":\"A\",\"name\":\"A\",\"funded\":true},{\"id\":\"A\",\"name\":\"B\",\"funded\":true}]}",
            "{\"stages\":[],\"legalDependencies\":[{\"before\":\"A\",\"after\":\"B\"}]}"})
    void badInputReturnsReadableJson(String body) throws Exception {
        var response = request("POST", "/api/parallelization", body);
        assertEquals(400, response.statusCode());
        JsonNode error = json.readTree(response.body());
        assertEquals(1, error.size());
        assertFalse(error.get("error").asText().isBlank());
        assertFalse(response.body().contains("stackTrace"));
        assertEquals(200, request("GET", "/api/health", "").statusCode());
    }

    @Test
    void contradictoryConstraintsRemainASuccessfulComputation() throws Exception {
        var response = request("POST", "/api/parallelization", """
                {"stages":[{"id":"A","name":"A","funded":true},
                {"id":"B","name":"B","funded":false}],
                "legalDependencies":[{"before":"B","after":"A"}]}
                """);
        assertEquals(200, response.statusCode());
        JsonNode report = json.readTree(response.body());
        assertEquals("NO_SOLUTION", report.get("status").asText());
        assertEquals(2, report.get("normalizedConstraints").size());
        assertTrue(report.get("optimalGroupCount").isNull());
        assertEquals(0, report.get("solutionCount").asInt());
        assertTrue(report.get("solutions").isEmpty());
    }

    @Test
    void unknownEndpointsAndMethods() throws Exception {
        for (String path : new String[]{"/", "/api/unknown", "/api/health/extra", "/api/parallelization/extra"}) {
            var response = request("GET", path, "");
            assertEquals(404, response.statusCode());
            assertTrue(json.readTree(response.body()).has("error"));
        }
        for (String method : new String[]{"GET", "PUT", "DELETE", "OPTIONS"}) {
            var response = request(method, "/api/parallelization", "");
            assertEquals(405, response.statusCode());
            assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
            assertTrue(json.readTree(response.body()).has("error"));
        }
        assertEquals(405, request("POST", "/api/health", "{}").statusCode());
        var head = request("HEAD", "/api/health", "");
        assertEquals(405, head.statusCode());
        assertEquals("", head.body());
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
