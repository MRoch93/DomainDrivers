package pl.edu.parallelization.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/** Etap identyfikowany nazwą, bez przypisanej grupy. */
public record Stage(@JsonValue String id) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Stage {
        Objects.requireNonNull(id, "Identyfikator etapu jest wymagany");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Identyfikator etapu nie może być pusty");
        }
    }
}
