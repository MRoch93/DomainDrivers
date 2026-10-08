package pl.edu.parallelization.application.selection;

import pl.edu.parallelization.input.ManagerInput;
import java.util.Map;
import java.util.HashMap;

public record SolutionSelectionContext(Map<String, Integer> resourceCounts) {
    public SolutionSelectionContext { resourceCounts = Map.copyOf(resourceCounts); }
    public static SolutionSelectionContext from(ManagerInput input) {
        Map<String, Integer> counts = new HashMap<>();
        input.stages().forEach(stage -> counts.put(stage.id(), stage.resources() == null ? 0 : stage.resources().size()));
        return new SolutionSelectionContext(counts);
    }
}
