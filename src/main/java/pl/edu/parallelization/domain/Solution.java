package pl.edu.parallelization.domain;

import java.util.List;

/** Kontener przyszłego wyniku obliczeń; nie wykonuje obliczeń. */
public record Solution(List<ParallelGroup> groups) {
    public Solution {
        groups = List.copyOf(groups);
    }
}
