package pl.edu.parallelization.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.edu.parallelization.domain.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagerInputMapperTest {
    private final ManagerInputMapper mapper = new ManagerInputMapper();
    private final Stage a = new Stage("A");
    private final Stage b = new Stage("B");

    @Test
    void mapsLogicalDependencyToBefore() {
        ParallelizationProblem problem = mapper.map(input(List.of(stage("A", true), stage("B", true)),
                List.of(new ManagerDependency("A", "B")), List.of()));
        assertEquals(List.of(a, b), problem.stages());
        assertEquals(List.of(new BeforeConstraint(a, b, ConstraintSource.LOGICAL)), problem.constraints());
    }

    @Test
    void mapsLegalDependencyToBefore() {
        assertEquals(List.of(new BeforeConstraint(a, b, ConstraintSource.LEGAL)), mapper.map(input(
                List.of(stage("A", true), stage("B", true)), List.of(),
                List.of(new ManagerDependency("A", "B")))).constraints());
    }

    @Test
    void sharedResourcesCreateOneNotWithEvenWhenSeveralResourcesOverlap() {
        assertEquals(List.of(new NotWithConstraint(a, b, ConstraintSource.RESOURCE)), mapper.map(input(
                List.of(stage("A", true, "Koparka", "Dzwig", "Koparka"),
                        stage("B", true, "Koparka", "Dzwig")), List.of(), List.of())).constraints());
    }

    @Test
    void disjointAndEmptyResourcesCreateNoNotWith() {
        assertTrue(mapper.map(input(List.of(stage("A", true, "Koparka"), stage("B", true, "Dzwig"),
                stage("C", true)), List.of(), List.of())).constraints().isEmpty());
    }

    @Test
    void fundingCreatesBeforeForEveryFundedNonFundedPairRegardlessOfInputOrder() {
        Stage c = new Stage("C");
        Stage d = new Stage("D");
        assertEquals(List.of(new BeforeConstraint(a, c, ConstraintSource.FUNDING),
                new BeforeConstraint(a, d, ConstraintSource.FUNDING),
                new BeforeConstraint(b, c, ConstraintSource.FUNDING),
                new BeforeConstraint(b, d, ConstraintSource.FUNDING)), mapper.map(input(
                List.of(stage("C", false), stage("A", true), stage("D", false), stage("B", true)),
                List.of(), List.of())).constraints());
    }

    @Test
    void deduplicatesSameRelationAcrossSourcesButKeepsDifferentRelationTypes() {
        ParallelizationProblem problem = mapper.map(input(List.of(stage("A", true, "Koparka"),
                stage("B", false, "Koparka")),
                List.of(new ManagerDependency("A", "B"), new ManagerDependency("A", "B")),
                List.of(new ManagerDependency("A", "B"))));
        assertEquals(List.of(new BeforeConstraint(a, b, ConstraintSource.LOGICAL),
                new NotWithConstraint(a, b, ConstraintSource.RESOURCE)), problem.constraints());
    }

    @Test
    void bothDependencyListsRejectUnknownEndpoints() {
        for (ManagerDependency dependency : List.of(new ManagerDependency("X", "A"),
                new ManagerDependency("A", "X"))) {
            InputValidationException logical = assertThrows(InputValidationException.class, () -> mapper.map(
                    input(List.of(stage("A", true)), List.of(dependency), List.of())));
            InputValidationException legal = assertThrows(InputValidationException.class, () -> mapper.map(
                    input(List.of(stage("A", true)), List.of(), List.of(dependency))));
            assertTrue(logical.getMessage().contains("logicalDependencies[0]"));
            assertTrue(legal.getMessage().contains("legalDependencies[0]"));
            assertTrue(logical.getMessage().contains("X"));
        }
    }

    @Test
    void rejectsDuplicateStageIds() {
        InputValidationException error = assertThrows(InputValidationException.class, () -> mapper.map(input(
                List.of(stage("A", true), stage("A", false)), List.of(), List.of())));
        assertTrue(error.getMessage().contains("Powtórzony Stage.id: A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankIds(String id) {
        InputValidationException error = assertThrows(InputValidationException.class, () -> mapper.map(input(
                List.of(stage(id, true)), List.of(), List.of())));
        assertTrue(error.getMessage().contains("stages[0].id"));
    }

    @Test
    void rejectsSelfDependencies() {
        for (boolean legal : List.of(false, true)) {
            List<ManagerDependency> dependencies = List.of(new ManagerDependency("A", "A"));
            assertThrows(InputValidationException.class, () -> mapper.map(input(List.of(stage("A", true)),
                    legal ? List.of() : dependencies, legal ? dependencies : List.of())));
        }
    }

    @Test
    void missingOptionalListsMeanEmptyLists() {
        assertTrue(mapper.map(new ManagerInput(List.of(new ManagerStage("A", "Analiza", true, null)),
                null, null)).constraints().isEmpty());
    }

    @Test
    void rejectsMissingFundingNameAndStages() {
        assertThrows(InputValidationException.class, () -> mapper.map(new ManagerInput(null, null, null)));
        assertThrows(InputValidationException.class, () -> mapper.map(input(
                List.of(new ManagerStage("A", "Analiza", null, List.of())), List.of(), List.of())));
        assertThrows(InputValidationException.class, () -> mapper.map(input(
                List.of(new ManagerStage("A", " ", true, List.of())), List.of(), List.of())));
    }

    private ManagerInput input(List<ManagerStage> stages, List<ManagerDependency> logical,
                               List<ManagerDependency> legal) {
        return new ManagerInput(stages, logical, legal);
    }

    private ManagerStage stage(String id, boolean funded, String... resources) {
        return new ManagerStage(id, "Etap " + id, funded, List.of(resources));
    }
}
