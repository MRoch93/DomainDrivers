package pl.edu.parallelization.application.selection;

import pl.edu.parallelization.domain.Solution;

/** Ordered tie-breaker: a negative comparison prefers the first candidate. */
public interface SolutionSelectionRule {
    String name();
    int compare(Solution first, Solution second, SolutionSelectionContext context);
}
