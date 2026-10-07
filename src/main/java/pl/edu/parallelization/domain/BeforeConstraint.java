package pl.edu.parallelization.domain;

import java.util.Objects;

/** group(first) < group(second). Kolejność etapów ma znaczenie. */
public record BeforeConstraint(Stage first, Stage second, ConstraintSource source) implements Constraint {
    public BeforeConstraint {
        Objects.requireNonNull(first, "Pierwszy etap jest wymagany");
        Objects.requireNonNull(second, "Drugi etap jest wymagany");
    }

    public BeforeConstraint(Stage first, Stage second) {
        this(first, second, null);
    }
}
