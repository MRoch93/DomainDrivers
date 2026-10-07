package pl.edu.parallelization.input;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Walidacja struktury i odwołań. Sprzeczności reguł rozstrzyga później CSP. */
public final class ManagerInputValidator {
    public void validate(ManagerInput input) {
        require(input != null, "Wejście musi być obiektem JSON");
        require(input.stages() != null, "Pole stages jest wymagane");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < input.stages().size(); i++) {
            ManagerStage stage = input.stages().get(i);
            String path = "stages[" + i + "]";
            require(stage != null, path + " nie może być null");
            requireText(stage.id(), path + ".id");
            require(ids.add(stage.id()), "Powtórzony Stage.id: " + stage.id());
            requireText(stage.name(), path + ".name");
            require(stage.funded() != null, path + ".funded jest wymagane (true lub false)");
            // Brak resources oznacza brak zasobów; [] jest również poprawne.
            if (stage.resources() != null) {
                for (int r = 0; r < stage.resources().size(); r++) {
                    requireText(stage.resources().get(r), path + ".resources[" + r + "]");
                }
            }
        }
        validateDependencies(input.logicalDependencies(), "logicalDependencies", ids);
        validateDependencies(input.legalDependencies(), "legalDependencies", ids);
    }

    private void validateDependencies(List<ManagerDependency> dependencies, String field, Set<String> ids) {
        if (dependencies == null) {
            return;
        }
        for (int i = 0; i < dependencies.size(); i++) {
            ManagerDependency dependency = dependencies.get(i);
            String path = field + "[" + i + "]";
            require(dependency != null, path + " nie może być null");
            requireText(dependency.before(), path + ".before");
            requireText(dependency.after(), path + ".after");
            require(ids.contains(dependency.before()), path + ".before: nieistniejący Stage " + dependency.before());
            require(ids.contains(dependency.after()), path + ".after: nieistniejący Stage " + dependency.after());
            require(!dependency.before().equals(dependency.after()), path + ": before i after muszą być różnymi etapami");
        }
    }

    private void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " musi być niepustym tekstem");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InputValidationException(message);
        }
    }
}
