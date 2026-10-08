package pl.edu.parallelization.api;

import pl.edu.parallelization.application.SelectedParallelizationResult;
import pl.edu.parallelization.domain.Constraint;
import pl.edu.parallelization.domain.Solution;
import pl.edu.parallelization.output.ParallelizationOutput;
import java.util.List;

public record ParallelizationApiResponse(ParallelizationOutput.Status status, List<Constraint> normalizedConstraints,
        Integer optimalGroupCount, int totalSolutionCount, int returnedSolutionCount,
        Solution selectedSolution, List<Solution> allSolutions, SelectionPolicy selectionPolicy) {
    public record SelectionPolicy(String name, List<String> rules) {}
    public static ParallelizationApiResponse from(SelectedParallelizationResult result, boolean returnAll) {
        var report = result.candidates();
        List<Solution> returned = returnAll ? report.solutions()
                : result.selectedSolution() == null ? List.of() : List.of(result.selectedSolution());
        return new ParallelizationApiResponse(report.status(), report.normalizedConstraints(), report.optimalGroupCount(),
                report.solutionCount(), returned.size(), result.selectedSolution(), returned,
                new SelectionPolicy("DETERMINISTIC_DEFAULT", result.selectionRules()));
    }
}
