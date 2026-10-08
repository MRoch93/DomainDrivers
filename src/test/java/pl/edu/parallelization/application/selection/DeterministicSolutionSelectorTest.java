package pl.edu.parallelization.application.selection;

import org.junit.jupiter.api.Test;
import pl.edu.parallelization.domain.*;
import pl.edu.parallelization.engine.ParallelizationEngine;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicSolutionSelectorTest {
    private final Stage a = new Stage("A"), b = new Stage("B"), c = new Stage("C");
    private Solution candidate(Stage first, Stage second) {
        return new Solution(List.of(new ParallelGroup(1, List.of(first)), new ParallelGroup(2, List.of(second, c))));
    }
    @Test void resourcesPreferEarlierStage() {
        var earlierA = candidate(a, b);
        var earlierB = candidate(b, a);
        var context = new SolutionSelectionContext(Map.of("A", 3, "B", 1, "C", 0));
        assertEquals(5, new ResourceCountRule().score(earlierA, context));
        assertEquals(7, new ResourceCountRule().score(earlierB, context));
        var selector = new DeterministicSolutionSelector();
        assertSame(earlierA, selector.select(List.of(earlierB, earlierA), context));
        assertSame(earlierA, selector.select(List.of(earlierA, earlierB), context));
    }
    @Test void stageIdBreaksTiesRegardlessOfCandidateOrder() {
        var earlierA = candidate(a, b);
        var earlierB = candidate(b, a);
        var context = new SolutionSelectionContext(Map.of("A", 1, "B", 1, "C", 0));
        var selector = new DeterministicSolutionSelector();
        assertSame(earlierA, selector.select(List.of(earlierB, earlierA), context));
        assertSame(earlierA, selector.select(List.of(earlierA, earlierB), context));
        var unordered = new Solution(List.of(new ParallelGroup(2, List.of(c, b)), new ParallelGroup(1, List.of(a))));
        assertEquals("A|B,C", new StageIdRule().canonical(unordered));
    }
    @Test void selectedCandidatePreservesAllConstraintTypes() {
        Stage d = new Stage("D");
        var candidates = new ParallelizationEngine().solve(List.of(a,b,c,d), List.of(
                new BeforeConstraint(a,c), new NotWithConstraint(a,b), new WithConstraint(c,d)));
        assertFalse(candidates.isEmpty());
        var selected = new DeterministicSolutionSelector().select(candidates,
                new SolutionSelectionContext(Map.of("A", 0, "B", 5, "C", 1, "D", 0)));
        assertTrue(candidates.stream().anyMatch(candidate -> candidate == selected));
        Map<String,Integer> groups = new java.util.HashMap<>();
        selected.groups().forEach(group -> group.stages().forEach(stage -> groups.put(stage.id(), group.groupNumber())));
        assertTrue(groups.get("A") < groups.get("C"));
        assertNotEquals(groups.get("A"), groups.get("B"));
        assertEquals(groups.get("C"), groups.get("D"));
    }
    @Test void noCandidatesMeansNoSelection() {
        assertNull(new DeterministicSolutionSelector().select(List.of(), new SolutionSelectionContext(Map.of())));
    }
}
