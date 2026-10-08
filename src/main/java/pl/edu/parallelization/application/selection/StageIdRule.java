package pl.edu.parallelization.application.selection;

import pl.edu.parallelization.domain.Solution;
import pl.edu.parallelization.domain.ParallelGroup;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class StageIdRule implements SolutionSelectionRule {
    public String name() { return "STAGE_ID"; }
    public String canonical(Solution solution) {
        return solution.groups().stream().sorted(Comparator.comparingInt(ParallelGroup::groupNumber))
                .map(group -> group.stages().stream().map(stage -> stage.id()).sorted()
                        .collect(Collectors.joining(","))).collect(Collectors.joining("|"));
    }
    public int compare(Solution first, Solution second, SolutionSelectionContext context) {
        return canonical(first).compareTo(canonical(second));
    }
}
