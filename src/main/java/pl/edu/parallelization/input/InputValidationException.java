package pl.edu.parallelization.input;

/** Czytelny błąd danych managera, zanim zostanie uruchomiony solver. */
public final class InputValidationException extends IllegalArgumentException {
    public InputValidationException(String message) {
        super(message);
    }
}
