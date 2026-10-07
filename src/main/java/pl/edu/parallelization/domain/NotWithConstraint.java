package pl.edu.parallelization.domain;

import java.util.Objects;

/** group(first) != group(second). Etapy muszą być w różnych grupach. */
public record NotWithConstraint(Stage first, Stage second, ConstraintSource source) implements Constraint {
    public NotWithConstraint {
        Objects.requireNonNull(first, "Pierwszy etap jest wymagany");
        Objects.requireNonNull(second, "Drugi etap jest wymagany");
    }

    public NotWithConstraint(Stage first, Stage second) {
        this(first, second, null);
    }
}
