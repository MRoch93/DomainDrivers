package pl.edu.parallelization.input;

import java.util.List;

/** Dane managera, przed walidacją i tłumaczeniem na model CSP. */
public record ManagerInput(List<ManagerStage> stages,
                           List<ManagerDependency> logicalDependencies,
                           List<ManagerDependency> legalDependencies) {
}
