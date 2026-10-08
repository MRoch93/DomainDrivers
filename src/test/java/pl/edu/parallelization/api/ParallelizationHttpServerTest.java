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
    void httpReturnsTheSameCandidatesAsTheFileInterface(String input) throws Exception {
        Path output = directory.resolve("output.json");
        new ParallelizationApplication().run(Path.of(input), output);
        var response = request("POST", "/api/parallelization", "{\"returnAllSolutions\":true,\"managerInput\":" + Files.readString(Path.of(input)) + "}");
        assertEquals(200, response.statusCode());
        JsonNode report = json.readTree(response.body());
        JsonNode file = json.readTree(output.toFile());
        assertEquals(file.get("solutions"), report.get("allSolutions"));
        assertEquals(file.get("solutionCount"), report.get("totalSolutionCount"));
        assertEquals(file.get("normalizedConstraints"), report.get("normalizedConstraints"));
        for (String field : new String[]{"status", "normalizedConstraints", "optimalGroupCount", "totalSolutionCount", "returnedSolutionCount", "selectedSolution", "allSolutions", "selectionPolicy"}) {
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
        if (body.startsWith("{\"stages")) {
            assertEquals(400, request("POST", "/api/parallelization", "{\"managerInput\":" + body + "}").statusCode());
        }
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
                {"managerInput":{"stages":[{"id":"A","name":"A","funded":true},
                {"id":"B","name":"B","funded":false}],
                "legalDependencies":[{"before":"B","after":"A"}]}}
                """);
        assertEquals(200, response.statusCode());
        JsonNode report = json.readTree(response.body());
        assertEquals("NO_SOLUTION", report.get("status").asText());
        assertEquals(2, report.get("normalizedConstraints").size());
        assertTrue(report.get("optimalGroupCount").isNull());
        assertEquals(0, report.get("totalSolutionCount").asInt());
        assertTrue(report.get("allSolutions").isEmpty());
        assertTrue(report.get("selectedSolution").isNull());
        assertEquals(0, report.get("returnedSolutionCount").asInt());
    }

    @Test
    void staticFilesAreAvailableWithCorrectContentTypes() throws Exception {
        for (String path : new String[]{"/", "/index.html", "/styles.css", "/app.js"}) {
            var response = request("GET", path, "");
            assertEquals(200, response.statusCode(), path);
            String resource = path.equals("/") ? "/index.html" : path;
            try (var stream = getClass().getResourceAsStream("/static" + resource)) {
                assertNotNull(stream);
                assertEquals(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), response.body());
            }
            String type = path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : "text/html";
            assertEquals(type + "; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow());
            var invalidMethod = request("POST", path, "");
            assertEquals(405, invalidMethod.statusCode());
            assertEquals("GET", invalidMethod.headers().firstValue("Allow").orElseThrow());
        }
        String html = request("GET", "/", "").body();
        assertTrue(html.contains("src=\"/app.js\""));
        assertTrue(html.contains("href=\"/styles.css\""));
        assertTrue(html.contains("id=\"manager-form\""));
    }

    @Test
    void unknownEndpointsAndMethods() throws Exception {
        for (String path : new String[]{"/missing.html", "/api/unknown", "/api/health/extra", "/api/parallelization/extra", "/pom.xml", "/static/index.html"}) {
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

    @ParameterizedTest
    @ValueSource(strings = {"{\"returnAllSolutions\":\"true\",\"managerInput\":{\"stages\":[]}}",
            "{\"returnAllSolutions\":1,\"managerInput\":{\"stages\":[]}}",
            "{\"returnAllSolutions\":false}", "{\"managerInput\":null}"})
    void invalidWrapperReturnsBadRequest(String body) throws Exception {
        assertEquals(400, request("POST", "/api/parallelization", body).statusCode());
    }

    @Test
    void selectionAndResponsePreference() throws Exception {
        String manager = Files.readString(Path.of("examples/shared-resources.json"));
        JsonNode omitted = json.readTree(request("POST", "/api/parallelization", "{\"managerInput\":" + manager + "}").body());
        JsonNode single = json.readTree(request("POST", "/api/parallelization", "{\"returnAllSolutions\":false,\"managerInput\":" + manager + "}").body());
        JsonNode all = json.readTree(request("POST", "/api/parallelization", "{\"returnAllSolutions\":true,\"managerInput\":" + manager + "}").body());
        assertEquals(omitted, single);
        assertTrue(single.get("totalSolutionCount").asInt() > 1);
        assertEquals(1, single.get("returnedSolutionCount").asInt());
        assertEquals(1, single.get("allSolutions").size());
        assertEquals(single.get("selectedSolution"), single.get("allSolutions").get(0));
        assertEquals(single.get("selectedSolution"), all.get("selectedSolution"));
        assertEquals(all.get("totalSolutionCount"), all.get("returnedSolutionCount"));
        assertEquals(all.get("totalSolutionCount").asInt(), all.get("allSolutions").size());
        assertTrue(all.get("allSolutions").findValues("groups").contains(all.get("selectedSolution").get("groups")));
        String oneManager = Files.readString(Path.of("input.json"));
        for (boolean flag : new boolean[]{false, true}) {
            JsonNode one = json.readTree(request("POST", "/api/parallelization", "{\"returnAllSolutions\":" + flag + ",\"managerInput\":" + oneManager + "}").body());
            assertEquals(1, one.get("totalSolutionCount").asInt());
            assertEquals(1, one.get("returnedSolutionCount").asInt());
            assertEquals(one.get("selectedSolution"), one.get("allSolutions").get(0));
        }
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
