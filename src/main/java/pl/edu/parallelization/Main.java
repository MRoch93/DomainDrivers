package pl.edu.parallelization;

import pl.edu.parallelization.application.ParallelizationApplication;
import pl.edu.parallelization.output.ParallelizationOutput;

import java.io.IOException;
import java.nio.file.Path;

/** Punkt startowy: zero argumentów albo ścieżki input.json i output.json. */
public final class Main {
    public static void main(String[] args) {
        if (args.length != 0 && args.length != 2) {
            System.err.println("Użycie: java -jar target/parallelization-engine-1.0-SNAPSHOT.jar [input.json output.json]");
            System.exit(1);
        }
        try {
            Path input = Path.of(args.length == 0 ? "input.json" : args[0]);
            Path output = Path.of(args.length == 0 ? "output.json" : args[1]);
            ParallelizationOutput result = new ParallelizationApplication().run(input, output);
            System.out.println("Status: " + result.status() + ", rozwiązań: " + result.solutionCount()
                    + ". Zapisano: " + output.toAbsolutePath().normalize());
        } catch (IOException | RuntimeException exception) {
            System.err.println("Błąd: " + exception.getMessage());
            System.exit(1);
        }
    }
}
