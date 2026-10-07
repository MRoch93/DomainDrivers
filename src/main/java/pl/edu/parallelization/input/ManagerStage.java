package pl.edu.parallelization.input;

import java.util.List;

/** Nazwa jest opisem; funded i resources posłużą do wyprowadzenia ograniczeń. */
public record ManagerStage(String id, String name, Boolean funded, List<String> resources) {
}
