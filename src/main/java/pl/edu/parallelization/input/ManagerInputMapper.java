package pl.edu.parallelization.input;

import pl.edu.parallelization.domain.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Granica między językiem managera a Deep Model używanym przez CSP. */
public final class ManagerInputMapper {
    public ParallelizationProblem map(ManagerInput input) {
        new ManagerInputValidator().validate(input);
        Map<String, Stage> stages = new LinkedHashMap<>();
        for (ManagerStage stage : input.stages()) {
            stages.put(stage.id(), new Stage(stage.id()));
        }
        Map<ConstraintKey, Constraint> constraints = new LinkedHashMap<>();
        addDependencies(input.logicalDependencies(), ConstraintSource.LOGICAL, stages, constraints);
        addDependencies(input.legalDependencies(), ConstraintSource.LEGAL, stages, constraints);

        for (int i = 0; i < input.stages().size(); i++) {
            ManagerStage first = input.stages().get(i);
            for (int j = i + 1; j < input.stages().size(); j++) {
                ManagerStage second = input.stages().get(j);
                List<String> firstResources = first.resources() == null ? List.of() : first.resources();
                List<String> secondResources = second.resources() == null ? List.of() : second.resources();
                if (!Collections.disjoint(firstResources, secondResources)) {
                    add(new NotWithConstraint(stages.get(first.id()), stages.get(second.id()),
                            ConstraintSource.RESOURCE), constraints);
                }
            }
        }
        for (ManagerStage funded : input.stages()) {
            if (funded.funded()) {
                for (ManagerStage nonFunded : input.stages()) {
                    if (!nonFunded.funded()) {
                        add(new BeforeConstraint(stages.get(funded.id()), stages.get(nonFunded.id()),
                                ConstraintSource.FUNDING), constraints);
                    }
                }
            }
        }
        return new ParallelizationProblem(List.copyOf(stages.values()), List.copyOf(constraints.values()));
    }

    private void addDependencies(List<ManagerDependency> dependencies, ConstraintSource source,
                                 Map<String, Stage> stages, Map<ConstraintKey, Constraint> constraints) {
        if (dependencies != null) {
            for (ManagerDependency dependency : dependencies) {
                add(new BeforeConstraint(stages.get(dependency.before()), stages.get(dependency.after()), source), constraints);
            }
        }
    }

    private void add(Constraint constraint, Map<ConstraintKey, Constraint> constraints) {
        // Źródło nie zmienia relacji. Zachowujemy pierwsze: LOGICAL, LEGAL, RESOURCE, FUNDING.
        ConstraintKey key = new ConstraintKey(constraint.getClass(), constraint.first().id(), constraint.second().id());
        constraints.putIfAbsent(key, constraint);
    }

    private record ConstraintKey(Class<? extends Constraint> type, String first, String second) {
    }
}
