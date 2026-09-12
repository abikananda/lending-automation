package com.abika.migration;

import com.abika.services.DBService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class BorrowerMigrationApi {
    private static final String API_PREFIX = "/api/migration/borrowers";
    private final BorrowerMigrationRepository borrowers;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final byte[] apiKey;

    BorrowerMigrationApi(BorrowerMigrationRepository borrowers, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("BORROWER_MIGRATION_API_KEY is required");
        }
        this.borrowers = borrowers;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        String key = System.getenv("BORROWER_MIGRATION_API_KEY");
        int port = Integer.parseInt(System.getenv().getOrDefault("BORROWER_MIGRATION_API_PORT", "8090"));
        BorrowerMigrationApi api = new BorrowerMigrationApi(
                new BorrowerMigrationRepository(new DBService()), key);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext(API_PREFIX, api::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.printf("Borrower migration API listening on http://127.0.0.1:%d%s%n", port, API_PREFIX);
    }

    void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) {
                send(exchange, 401, Map.of("message", "Unauthorized"));
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Allow", "GET");
                send(exchange, 405, Map.of("message", "Method not allowed"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ((API_PREFIX + "/ids").equals(path)) {
                ids(exchange);
            } else if (path.startsWith(API_PREFIX + "/")) {
                borrower(exchange, path);
            } else {
                send(exchange, 404, Map.of("message", "Not found"));
            }
        } catch (IllegalArgumentException ex) {
            send(exchange, 400, Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            send(exchange, 500, Map.of("message", "Unable to read borrower migration data"));
        } finally {
            exchange.close();
        }
    }

    private void ids(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        long afterId = Math.max(0, Long.parseLong(query.getOrDefault("afterId", "0")));
        int limit = Math.min(500, Math.max(1, Integer.parseInt(query.getOrDefault("limit", "100"))));
        List<Long> ids = borrowers.findIdsAfter(afterId, limit);
        long nextCursor = ids.isEmpty() ? afterId : ids.get(ids.size() - 1);
        send(exchange, 200, new IdPage(ids, nextCursor, ids.size() == limit));
    }

    private void borrower(HttpExchange exchange, String path) throws IOException {
        String value = path.substring((API_PREFIX + "/").length());
        if (value.isBlank() || value.contains("/")) throw new IllegalArgumentException("Invalid borrower ID");
        var borrower = borrowers.findById(Long.parseLong(value));
        if (borrower.isEmpty()) {
            send(exchange, 404, Map.of("message", "Borrower not found"));
            return;
        }
        send(exchange, 200, borrower.get());
    }

    private boolean authorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst("X-Migration-Key");
        return supplied != null && MessageDigest.isEqual(apiKey, supplied.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return values;
        Arrays.stream(raw.split("&")).map(value -> value.split("=", 2))
                .filter(pair -> pair.length == 2).forEach(pair -> values.put(pair[0], pair[1]));
        return values;
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    record IdPage(List<Long> ids, long nextCursor, boolean hasMore) { }
}
