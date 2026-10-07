package pl.edu.parallelization.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.edu.parallelization.input.InputValidationException;
import pl.edu.parallelization.output.ParallelizationOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParallelizationApplicationTest {
    @TempDir
    Path directory;
    private final ParallelizationApplication app = new ParallelizationApplication();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void managerExamplePassesThroughJsonMapperEngineAndOutput() throws Exception {
        Path input = directory.resolve("input.json");
        Path output = directory.resolve("output.json");
        Files.copy(Path.of("input.json"), input);
        ParallelizationOutput result = app.run(input, output);
        assertEquals(ParallelizationOutput.Status.OK, result.status());
        JsonNode actual = json.readTree(output.toFile());
        JsonNode expected = json.readTree("""
                {
                  "status":"OK",
                  "normalizedConstraints":[
                    {"type":"BEFORE","first":"A","second":"C","source":"LOGICAL"},
                    {"type":"BEFORE","first":"C","second":"D","source":"LEGAL"},
                    {"type":"NOT_WITH","first":"A","second":"B","source":"RESOURCE"},
                    {"type":"NOT_WITH","first":"C","second":"D","source":"RESOURCE"},
                    {"type":"BEFORE","first":"A","second":"D","source":"FUNDING"},
                    {"type":"BEFORE","first":"B","second":"D","source":"FUNDING"}
                  ],
                  "optimalGroupCount":3,
                  "solutionCount":1,
                  "solutions":[{"groups":[
                    {"groupNumber":1,"stages":["A"]},
                    {"groupNumber":2,"stages":["B","C"]},
                    {"groupNumber":3,"stages":["D"]}
                  ]}]
                }
                """);
        assertEquals(expected, actual);
    }

    @Test
    void sharedResourceExampleReturnsExactlyTwoOptimalPartitions() throws Exception {
        Path output = directory.resolve("output.json");
        ParallelizationOutput result = app.run(Path.of("examples/shared-resources.json"), output);
        assertEquals(2, result.optimalGroupCount());
        assertEquals(2, result.solutionCount());
        Set<Set<Set<String>>> partitions = new HashSet<>();
        for (JsonNode solution : json.readTree(output.toFile()).get("solutions")) {
            Set<Set<String>> groups = new HashSet<>();
            for (JsonNode group : solution.get("groups")) {
                Set<String> ids = new HashSet<>();
                group.get("stages").forEach(stage -> ids.add(stage.asText()));
                groups.add(ids);
            }
            partitions.add(groups);
        }
        assertEquals(Set.of(Set.of(Set.of("A", "C"), Set.of("B", "D")),
                Set.of(Set.of("A", "D"), Set.of("B", "C"))), partitions);
        assertEquals(2, result.normalizedConstraints().size());
    }

    @Test
    void fundingConflictProducesNoSolutionReportRatherThanValidationError() throws Exception {
        Path input = writeInput("""
                {"stages":[
                  {"id":"A","name":"A","funded":true,"resources":[]},
                  {"id":"B","name":"B","funded":false,"resources":[]}
                ],"legalDependencies":[{"before":"B","after":"A"}]}
                """);
        Path output = directory.resolve("output.json");
        ParallelizationOutput result = app.run(input, output);
        assertEquals(ParallelizationOutput.Status.NO_SOLUTION, result.status());
        JsonNode report = json.readTree(output.toFile());
        assertEquals("NO_SOLUTION", report.get("status").asText());
        assertTrue(report.get("optimalGroupCount").isNull());
        assertEquals(0, report.get("solutionCount").asInt());
        assertTrue(report.get("solutions").isEmpty());
        assertEquals(2, report.get("normalizedConstraints").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}", "{\"stages\":null}",
            "{\"stages\":[{\"id\":\"A\",\"name\":\"A\",\"funded\":\"true\"}]}",
            "{\"stages\":[{\"id\":\"\",\"name\":\"A\",\"funded\":true}]}",
            "{\"stages\":[]} {}", "{\"stages\":[],\"constraints\":[]}"})
    void invalidJsonOrDataGiveReadableErrorsAndRemoveOldOutput(String content) throws Exception {
        Path input = writeInput(content);
        Path output = directory.resolve("output.json");
        Files.writeString(output, "stary wynik");
        InputValidationException error = assertThrows(InputValidationException.class, () -> app.run(input, output));
        assertFalse(error.getMessage().isBlank());
        assertFalse(Files.exists(output));
    }

    @Test
    void missingInputRemovesOldOutput() throws Exception {
        Path output = directory.resolve("output.json");
        Files.writeString(output, "stary wynik");
        assertThrows(IOException.class, () -> app.run(directory.resolve("missing.json"), output));
        assertFalse(Files.exists(output));
    }

    @Test
    void doesNotOverwriteInputWhenPathsAreEqual() throws Exception {
        Path input = writeInput("{\"stages\":[]}");
        assertThrows(InputValidationException.class, () -> app.run(input, input));
        assertEquals("{\"stages\":[]}", Files.readString(input));
    }

    @Test
    void emptyProjectProducesOneEmptySolutionWithZeroGroups() throws Exception {
        ParallelizationOutput result = app.run(writeInput("{\"stages\":[]}"), directory.resolve("output.json"));
        assertEquals(ParallelizationOutput.Status.OK, result.status());
        assertEquals(0, result.optimalGroupCount());
        assertEquals(1, result.solutionCount());
    }

    private Path writeInput(String content) throws IOException {
        Path input = directory.resolve("input.json");
        Files.writeString(input, content);
        return input;
    }
}
