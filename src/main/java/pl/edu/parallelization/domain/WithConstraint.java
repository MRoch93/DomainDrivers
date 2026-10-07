package pl.edu.parallelization.domain;

import java.util.Objects;

/** group(first) == group(second). Etapy muszą być w tej samej grupie. */
public record WithConstraint(Stage first, Stage second, ConstraintSource source) implements Constraint {
    public WithConstraint {
        Objects.requireNonNull(first, "Pierwszy etap jest wymagany");
        Objects.requireNonNull(second, "Drugi etap jest wymagany");
    }

    public WithConstraint(Stage first, Stage second) {
        this(first, second, null);
    }
}
