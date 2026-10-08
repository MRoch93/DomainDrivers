package pl.edu.parallelization.api;

import java.io.IOException;

public final class ApiMain {
    public static void main(String[] args) {
        try {
            ParallelizationHttpServer server = new ParallelizationHttpServer(8080);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            server.start();
            System.out.println("API działa lokalnie: http://localhost:8080 (Ctrl+C kończy pracę)");
        } catch (IOException exception) {
            System.err.println("Nie można uruchomić API: " + exception.getMessage());
            System.exit(1);
        }
    }
}
