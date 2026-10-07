package pl.edu.parallelization.domain;

import java.util.List;

/** Grupa etapów, które w przyszłym wyniku mogą wykonywać się równolegle. */
public record ParallelGroup(int groupNumber, List<Stage> stages) {
    public ParallelGroup {
        if (groupNumber < 1) {
            throw new IllegalArgumentException("Numer grupy musi być dodatni");
        }
        stages = List.copyOf(stages);
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Grupa musi zawierać etap");
        }
    }
}
