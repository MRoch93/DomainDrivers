package pl.edu.parallelization.application.selection;

import pl.edu.parallelization.domain.Solution;
import java.util.List;

/** Selects an existing CSP candidate; never modifies assignments or constraints. */
public final class DeterministicSolutionSelector {
    private final List<SolutionSelectionRule> rules;
    public DeterministicSolutionSelector() { this(List.of(new ResourceCountRule(), new StageIdRule())); }
    public DeterministicSolutionSelector(List<SolutionSelectionRule> rules) {
        this.rules = List.copyOf(rules);
        if (rules.isEmpty()) throw new IllegalArgumentException("Selection requires rules");
    }
    public List<String> ruleNames() { return rules.stream().map(SolutionSelectionRule::name).toList(); }
    public Solution select(List<Solution> candidates, SolutionSelectionContext context) {
        return candidates.stream().min((first, second) -> {
            for (SolutionSelectionRule rule : rules) {
                int comparison = rule.compare(first, second, context);
                if (comparison != 0) return comparison;
            }
            return 0;
        }).orElse(null);
    }
}
