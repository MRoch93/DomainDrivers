package pl.edu.parallelization.api;

import pl.edu.parallelization.input.ManagerInput;

/** Response preference belongs to HTTP, not to ManagerInput. */
public record ParallelizationApiRequest(boolean returnAllSolutions, ManagerInput managerInput) {}
