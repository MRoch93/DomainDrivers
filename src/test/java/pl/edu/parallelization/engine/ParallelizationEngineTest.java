package pl.edu.parallelization.engine;

import org.junit.jupiter.api.Test;
import pl.edu.parallelization.domain.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ParallelizationEngineTest {
    private final ParallelizationEngine engine = new ParallelizationEngine();
    private final Stage a = new Stage("A");
    private final Stage b = new Stage("B");
    private final Stage c = new Stage("C");
    private final Stage d = new Stage("D");

    @Test
    void noConstraintsUsesOneGroup() {
        assertEquals(List.of(new Solution(List.of(new ParallelGroup(1, List.of(a, b))))),
                engine.solve(List.of(a, b), List.of()));
    }

    @Test
    void beforeReturnsBothOptimalPlacementsOfIndependentStage() {
        List<Constraint> constraints = List.of(new BeforeConstraint(a, c));
        List<Solution> results = engine.solve(List.of(a, b, c), constraints);
        assertEquals(Set.of(Set.of(Set.of("A", "B"), Set.of("C")),
                Set.of(Set.of("A"), Set.of("B", "C"))), partitions(results));
        assertEquals(2, results.size());
        assertValid(results, List.of(a, b, c), constraints, 2);
    }

    @Test
    void notWithReturnsTwoGroupsWithoutTheirReversal() {
        List<Constraint> constraints = List.of(new NotWithConstraint(a, b));
        List<Solution> results = engine.solve(List.of(a, b), constraints);
        assertEquals(List.of(new Solution(List.of(new ParallelGroup(1, List.of(a)),
                new ParallelGroup(2, List.of(b))))), results);
        assertValid(results, List.of(a, b), constraints, 2);
    }

    @Test
    void independentNotWithConstraintsReturnExactlyTwoOptimalPartitions() {
        List<Constraint> constraints = List.of(new NotWithConstraint(a, b), new NotWithConstraint(c, d));
        List<Solution> results = engine.solve(List.of(a, b, c, d), constraints);
        assertEquals(Set.of(Set.of(Set.of("A", "C"), Set.of("B", "D")),
                Set.of(Set.of("A", "D"), Set.of("B", "C"))), partitions(results));
        assertEquals(2, results.size());
        assertValid(results, List.of(a, b, c, d), constraints, 2);
    }

    @Test
    void withKeepsStagesTogetherEvenWhenMoreGroupsAreRequired() {
        List<Constraint> constraints = List.of(new WithConstraint(a, b), new NotWithConstraint(b, c));
        List<Solution> results = engine.solve(List.of(a, b, c), constraints);
        assertEquals(Set.of(Set.of(Set.of("A", "B"), Set.of("C"))), partitions(results));
        assertEquals(1, results.size());
        assertValid(results, List.of(a, b, c), constraints, 2);
        assertEquals(1, engine.solve(List.of(a, b), List.of(new WithConstraint(a, b)))
                .getFirst().groups().size());
    }

    @Test
    void contradictoryWithAndBeforeHaveNoSolution() {
        assertTrue(engine.solve(List.of(a, b, c), List.of(new WithConstraint(a, b),
                new BeforeConstraint(a, c), new BeforeConstraint(c, b))).isEmpty());
    }

    @Test
    void mixedConstraintsFindThreeGroupsInsteadOfValidFourGroupResult() {
        List<Constraint> constraints = List.of(new BeforeConstraint(a, c), new BeforeConstraint(c, d),
                new BeforeConstraint(b, d), new NotWithConstraint(a, b));
        List<Solution> results = engine.solve(List.of(a, b, c, d), constraints);
        assertEquals(List.of(new Solution(List.of(new ParallelGroup(1, List.of(a)),
                new ParallelGroup(2, List.of(b, c)), new ParallelGroup(3, List.of(d))))), results);
        assertValid(results, List.of(a, b, c, d), constraints, 3);
    }

    @Test
    void beforeDoesNotDependOnInputOrderOrCreateGaps() {
        List<Constraint> constraints = List.of(new BeforeConstraint(c, b), new BeforeConstraint(b, a));
        List<Solution> results = engine.solve(List.of(a, b, c), constraints);
        assertEquals(List.of(new Solution(List.of(new ParallelGroup(1, List.of(c)),
                new ParallelGroup(2, List.of(b)), new ParallelGroup(3, List.of(a))))), results);
        assertValid(results, List.of(a, b, c), constraints, 3);
    }

    @Test
    void deduplicatesRenumberedPartitionsAlsoWithBefore() {
        List<Constraint> constraints = List.of(new BeforeConstraint(a, b),
                new NotWithConstraint(a, c), new NotWithConstraint(b, c));
        List<Solution> results = engine.solve(List.of(a, b, c), constraints);
        assertEquals(1, results.size());
        assertValid(results, List.of(a, b, c), constraints, 3);
    }

    @Test
    void sourceDoesNotAffectSolving() {
        List<Solution> expected = engine.solve(List.of(a, b), List.of(new NotWithConstraint(a, b)));
        for (ConstraintSource source : ConstraintSource.values()) {
            assertEquals(expected, engine.solve(List.of(a, b), List.of(new NotWithConstraint(a, b, source))));
        }
    }

    @Test
    void handlesEmptyInputAndSelfConstraints() {
        assertEquals(List.of(new Solution(List.of())), engine.solve(List.of(), List.of()));
        assertTrue(engine.solve(List.of(a), List.of(new BeforeConstraint(a, a))).isEmpty());
        assertTrue(engine.solve(List.of(a), List.of(new NotWithConstraint(a, a))).isEmpty());
        assertEquals(1, engine.solve(List.of(a), List.of(new WithConstraint(a, a))).size());
    }

    @Test
    void rejectsDuplicateStagesAndUnknownConstraintEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> engine.solve(List.of(a, new Stage("A")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> engine.solve(List.of(a), List.of(new WithConstraint(a, b))));
    }

    @Test
    void cyclicBeforeHasNoSolution() {
        assertTrue(engine.solve(List.of(a, b), List.of(new BeforeConstraint(a, b),
                new BeforeConstraint(b, a))).isEmpty());
    }

    private Set<Set<Set<String>>> partitions(List<Solution> solutions) {
        return solutions.stream().map(solution -> solution.groups().stream()
                .map(group -> group.stages().stream().map(Stage::id).collect(Collectors.toSet()))
                .collect(Collectors.toSet())).collect(Collectors.toSet());
    }

    private void assertValid(List<Solution> results, List<Stage> stages,
                             List<Constraint> constraints, int expectedGroupCount) {
        assertFalse(results.isEmpty());
        for (Solution solution : results) {
            assertEquals(expectedGroupCount, solution.groups().size());
            Map<Stage, Integer> numbers = new HashMap<>();
            for (int i = 0; i < solution.groups().size(); i++) {
                ParallelGroup group = solution.groups().get(i);
                assertEquals(i + 1, group.groupNumber());
                for (Stage stage : group.stages()) {
                    assertNull(numbers.put(stage, group.groupNumber()), "Etap występuje tylko raz");
                }
            }
            assertEquals(new HashSet<>(stages), numbers.keySet());
            for (Constraint constraint : constraints) {
                int first = numbers.get(constraint.first());
                int second = numbers.get(constraint.second());
                assertTrue(switch (constraint) {
                    case BeforeConstraint ignored -> first < second;
                    case NotWithConstraint ignored -> first != second;
                    case WithConstraint ignored -> first == second;
                });
            }
        }
    }
}
