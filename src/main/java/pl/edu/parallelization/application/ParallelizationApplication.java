package pl.edu.parallelization.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import pl.edu.parallelization.domain.ParallelizationProblem;
import pl.edu.parallelization.engine.ParallelizationEngine;
import pl.edu.parallelization.input.InputValidationException;
import pl.edu.parallelization.input.ManagerInput;
import pl.edu.parallelization.input.ManagerInputMapper;
import pl.edu.parallelization.output.ParallelizationOutput;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Techniczna orkiestracja całego przepływu plikowego. */
public final class ParallelizationApplication {
    private final ObjectMapper json = JsonMapper.builder()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ParallelizationOutput process(ManagerInput input) {
        ParallelizationProblem problem = new ManagerInputMapper().map(input);
        return ParallelizationOutput.from(problem,
                new ParallelizationEngine().solve(problem.stages(), problem.constraints()));
    }

    public SelectedParallelizationResult processSelected(ManagerInput input) {
        ParallelizationOutput candidates = process(input);
        var selector = new pl.edu.parallelization.application.selection.DeterministicSolutionSelector();
        var context = pl.edu.parallelization.application.selection.SolutionSelectionContext.from(input);
        return new SelectedParallelizationResult(candidates, selector.select(candidates.solutions(), context), selector.ruleNames());
    }

    public <T> T readJson(InputStream input, Class<T> type) throws IOException {
        try {
            return json.readValue(input, type);
        } catch (JsonProcessingException exception) {
            throw new InputValidationException("Niepoprawny JSON wejściowy: " + exception.getOriginalMessage());
        }
    }

    public ManagerInput readInput(InputStream input) throws IOException {
        return readJson(input, ManagerInput.class);
    }

    public ParallelizationOutput run(Path input, Path output) throws IOException {
        input = input.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (input.equals(output) || (Files.exists(input) && Files.exists(output) && Files.isSameFile(input, output))) {
            throw new InputValidationException("Pliki wejściowy i wyjściowy muszą być różne");
        }
        // Usuwamy stary raport przed odczytem, także gdy nowe wejście jest błędne.
        Files.deleteIfExists(output);
        try {
            ManagerInput managerInput;
            try (InputStream stream = Files.newInputStream(input)) {
                managerInput = readInput(stream);
            }
            ParallelizationOutput result = process(managerInput);
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
            return result;
        } catch (IOException | RuntimeException exception) {
            // Również częściowo zapisany raport nie może wyglądać jak poprawny wynik.
            try {
                Files.deleteIfExists(output);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }
}
