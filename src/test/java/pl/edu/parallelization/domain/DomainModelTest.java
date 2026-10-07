package pl.edu.parallelization.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {
    private final Stage a = new Stage("A");
    private final Stage b = new Stage("B");

    @Test
    void stagesAreIdentifiedById() {
        assertEquals(a, new Stage("A"));
        assertNotEquals(a, b);
        assertEquals("A", a.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankStageIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> new Stage(id));
    }

    @Test
    void rejectsNullStageId() {
        assertThrows(NullPointerException.class, () -> new Stage(null));
    }

    @ParameterizedTest
    @EnumSource(ConstraintSource.class)
    void allConstraintTypesPreserveStagesAndInformationalSource(ConstraintSource source) {
        List<Constraint> constraints = List.of(
                new BeforeConstraint(a, b, source),
                new NotWithConstraint(a, b, source),
                new WithConstraint(a, b, source));
        for (Constraint constraint : constraints) {
            assertEquals(a, constraint.first());
            assertEquals(b, constraint.second());
            assertEquals(source, constraint.source());
        }
    }

    @Test
    void sourceIsOptionalForEveryConstraintType() {
        for (Constraint constraint : List.of(new BeforeConstraint(a, b),
                new NotWithConstraint(a, b), new WithConstraint(a, b))) {
            assertNull(constraint.source());
        }
    }

    @Test
    void beforePreservesDirection() {
        assertNotEquals(new BeforeConstraint(a, b), new BeforeConstraint(b, a));
    }

    @Test
    void constraintsRequireBothStages() {
        assertThrows(NullPointerException.class, () -> new BeforeConstraint(null, b));
        assertThrows(NullPointerException.class, () -> new BeforeConstraint(a, null));
        assertThrows(NullPointerException.class, () -> new NotWithConstraint(null, b));
        assertThrows(NullPointerException.class, () -> new NotWithConstraint(a, null));
        assertThrows(NullPointerException.class, () -> new WithConstraint(null, b));
        assertThrows(NullPointerException.class, () -> new WithConstraint(a, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void groupNumberMustBePositive(int number) {
        assertThrows(IllegalArgumentException.class, () -> new ParallelGroup(number, List.of(a)));
    }

    @Test
    void groupsRejectMissingStages() {
        assertThrows(IllegalArgumentException.class, () -> new ParallelGroup(1, List.of()));
        assertThrows(NullPointerException.class, () -> new ParallelGroup(1, null));
        List<Stage> stages = new ArrayList<>();
        stages.add(null);
        assertThrows(NullPointerException.class, () -> new ParallelGroup(1, stages));
    }

    @Test
    void resultDefensivelyCopiesListsAndExposesUnmodifiableLists() {
        List<Stage> stages = new ArrayList<>(List.of(a));
        ParallelGroup group = new ParallelGroup(1, stages);
        List<ParallelGroup> groups = new ArrayList<>(List.of(group));
        Solution solution = new Solution(groups);
        stages.add(b);
        groups.clear();
        assertEquals(List.of(a), group.stages());
        assertEquals(List.of(group), solution.groups());
        assertThrows(UnsupportedOperationException.class, () -> group.stages().add(b));
        assertThrows(UnsupportedOperationException.class, () -> solution.groups().clear());
    }

    @Test
    void solutionAllowsEmptyResultButRejectsNullGroups() {
        assertTrue(new Solution(List.of()).groups().isEmpty());
        assertThrows(NullPointerException.class, () -> new Solution(null));
        List<ParallelGroup> groups = new ArrayList<>();
        groups.add(null);
        assertThrows(NullPointerException.class, () -> new Solution(groups));
    }
}
