import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SquareServiceClientTest {

    private PrintStream originalOut;
    private HttpServer server;

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int status, JSONObject body)
            throws IOException {
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private String stubBaseUrl() {
        InetSocketAddress a = server.getAddress();
        String host = a.getHostString();
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + a.getPort();
    }

    private void startServer(HttpHandler health, HttpHandler square) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", health);
        server.createContext("/square", square);
        server.start();
        SquareServiceClient.setBaseUrlOverrideForTesting(stubBaseUrl());
    }

    /** Default {@code /square} rejects non-POST. */
    private static HttpHandler rejectingSquareHandler() {
        return exchange ->
                sendJson(
                        exchange,
                        405,
                        new JSONObject().put("error", "method not allowed"));
    }

    /** Default {@code /health} responds healthy — override via {@link #healthHandler(org.json.JSONObject)} etc. */
    private static HttpHandler healthyHealthHandler() {
        return exchange ->
                sendJson(
                        exchange,
                        200,
                        new JSONObject().put("status", "healthy"));
    }

    private static HttpHandler squareOkHandler() {
        return exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, new JSONObject());
                return;
            }
            JSONObject req = new JSONObject(readBody(exchange));
            int n = req.getInt("number");
            sendJson(
                    exchange,
                    200,
                    new JSONObject().put("number", n).put("square", n * n));
        };
    }

    private static HttpHandler square400Handler() {
        return exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            sendJson(
                    exchange,
                    400,
                    new JSONObject().put("error", "Parameter must be an integer"));
        };
    }

    private static HttpHandler healthWithBody(JSONObject body) {
        return exchange -> sendJson(exchange, 200, body);
    }

    @BeforeEach
    void muteStdOutAndResetClient() {
        originalOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        SquareServiceClient.setBaseUrlOverrideForTesting(null);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        SquareServiceClient.setBaseUrlOverrideForTesting(null);
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void checkServerHealth_trueWhenHealthy() throws Exception {
        startServer(healthyHealthHandler(), rejectingSquareHandler());
        assertTrue(SquareServiceClient.checkServerHealth());
    }

    @Test
    void checkServerHealth_falseWhenStatusNotHealthy() throws Exception {
        startServer(
                healthWithBody(new JSONObject().put("status", "degraded")),
                rejectingSquareHandler());
        assertFalse(SquareServiceClient.checkServerHealth());
    }

    @Test
    void squareNumber_returnsSquaredValue() throws Exception {
        startServer(healthyHealthHandler(), squareOkHandler());
        assertEquals(49, SquareServiceClient.squareNumber(7));
    }

    @Test
    void squareNumber_returnsMinusOneOnHttpError() throws Exception {
        startServer(healthyHealthHandler(), square400Handler());
        assertEquals(-1, SquareServiceClient.squareNumber(9));
    }
}
