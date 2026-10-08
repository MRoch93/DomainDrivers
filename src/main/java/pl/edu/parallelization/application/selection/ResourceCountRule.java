package pl.edu.parallelization.application.selection;

import pl.edu.parallelization.domain.Solution;

public final class ResourceCountRule implements SolutionSelectionRule {
    public String name() { return "RESOURCE_COUNT_EARLIER"; }
    public long score(Solution solution, SolutionSelectionContext context) {
        return solution.groups().stream().mapToLong(group -> group.stages().stream()
                .mapToLong(stage -> (long) context.resourceCounts().get(stage.id()) * group.groupNumber()).sum()).sum();
    }
    public int compare(Solution first, Solution second, SolutionSelectionContext context) {
        return Long.compare(score(first, context), score(second, context));
    }
}
