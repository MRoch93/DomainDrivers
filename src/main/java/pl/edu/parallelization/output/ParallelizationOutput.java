package pl.edu.parallelization.output;

import pl.edu.parallelization.domain.Constraint;
import pl.edu.parallelization.domain.ParallelizationProblem;
import pl.edu.parallelization.domain.Solution;

import java.util.List;

/** Raport zawierający interpretację wejścia i wyniki obliczeń. */
public record ParallelizationOutput(Status status, List<Constraint> normalizedConstraints,
                                    Integer optimalGroupCount, int solutionCount, List<Solution> solutions) {
    public enum Status { OK, NO_SOLUTION }

    public ParallelizationOutput {
        normalizedConstraints = List.copyOf(normalizedConstraints);
        solutions = List.copyOf(solutions);
    }

    public static ParallelizationOutput from(ParallelizationProblem problem, List<Solution> solutions) {
        return new ParallelizationOutput(solutions.isEmpty() ? Status.NO_SOLUTION : Status.OK,
                problem.constraints(), solutions.isEmpty() ? null : solutions.getFirst().groups().size(),
                solutions.size(), solutions);
    }
}
