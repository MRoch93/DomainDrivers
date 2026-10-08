package pl.edu.parallelization.application;

import pl.edu.parallelization.domain.Solution;
import pl.edu.parallelization.output.ParallelizationOutput;
import java.util.List;

public record SelectedParallelizationResult(ParallelizationOutput candidates, Solution selectedSolution,
                                            List<String> selectionRules) {
    public SelectedParallelizationResult { selectionRules = List.copyOf(selectionRules); }
}
