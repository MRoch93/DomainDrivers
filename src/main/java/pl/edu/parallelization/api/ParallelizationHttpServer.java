package pl.edu.parallelization.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import pl.edu.parallelization.application.ParallelizationApplication;
import pl.edu.parallelization.input.InputValidationException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lokalny adapter HTTP; obliczenia pozostają w warstwie aplikacyjnej. */
public final class ParallelizationHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ParallelizationApplication application = new ParallelizationApplication();
    private final ObjectMapper json = new ObjectMapper();

    public ParallelizationHttpServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = switch (path) {
                case "/api/health" -> "GET";
                case "/api/parallelization" -> "POST";
                default -> null;
            };
            if (method == null) {
                send(exchange, 404, Map.of("error", "Nieznany endpoint"));
                return;
            }
            if (!method.equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", method);
                send(exchange, 405, Map.of("error", "Dozwolona metoda: " + method));
                return;
            }
            Object response;
            try {
                response = path.equals("/api/health")
                        ? Map.of("status", "UP")
                        : application.process(application.readInput(exchange.getRequestBody()));
            } catch (InputValidationException exception) {
                send(exchange, 400, Map.of("error", exception.getMessage()));
                return;
            } catch (RuntimeException exception) {
                System.err.println("Błąd obsługi API: " + exception);
                send(exchange, 500, Map.of("error", "Wewnętrzny błąd aplikacji"));
                return;
            }
            send(exchange, 200, response);
        }
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // HEAD nie ma treści odpowiedzi, także przy błędzie 405.
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
