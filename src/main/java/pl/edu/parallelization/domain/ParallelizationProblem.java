package pl.edu.parallelization.domain;

import java.util.List;

/** Znormalizowane dane wejściowe silnika: etapy i ograniczenia. */
public record ParallelizationProblem(List<Stage> stages, List<Constraint> constraints) {
    public ParallelizationProblem {
        stages = List.copyOf(stages);
        constraints = List.copyOf(constraints);
    }
}
