package pl.edu.parallelization.input;

/** Zależność managera odwołuje się do identyfikatorów etapów. */
public record ManagerDependency(String before, String after) {
}
