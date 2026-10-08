package dev.agi.mcbot.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import dev.agi.mcbot.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP JSON-RPC service backed by the JDK built-in HttpServer.
 *
 * Using the JDK server instead of a third-party framework keeps the mod jar
 * free of Kotlin/Jetty/SLF4J transitive dependencies.
 *
 * Endpoints:
 *   POST /rpc     JSON-RPC 2.0
 *   GET  /health  health check (no auth)
 *
 * Auth: Header "Authorization: Bearer <token>"
 */
public class HttpServer {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ModConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RpcHandler handler;
    private final RateLimiter rateLimiter;

    private com.sun.net.httpserver.HttpServer httpd;
    private ScheduledExecutorService scheduler;

    public HttpServer(ModConfig config, MinecraftServer server) {
        this.config = config;
        this.handler = new RpcHandler(server);
        this.rateLimiter = new RateLimiter(config.getRateLimit());
    }

    public void start() throws IOException {
        httpd = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress(config.getBindAddress(), config.getBindPort()), 0);

        httpd.createContext("/rpc", exchange -> {
            if (!authorize(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, errorBody(null, -32600, "Method Not Allowed").toString());
                return;
            }
            handleRpc(exchange);
        });

        httpd.createContext("/health", exchange -> {
            Map<String, String> health = new HashMap<>();
            health.put("status", "ok");
            send(exchange, 200, mapper.valueToTree(health).toString());
        });

        httpd.createContext("/", exchange ->
                send(exchange, 404, errorBody(null, -32601, "Not Found").toString()));

        httpd.setExecutor(Executors.newFixedThreadPool(4));
        httpd.start();

        // periodically clean up expired rate limit buckets
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(rateLimiter::cleanup, 1, 1, TimeUnit.MINUTES);

        LOGGER.info("[AGI-MC] HTTP service started on {}:{}",
                config.getBindAddress(), config.getBindPort());
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (httpd != null) {
            httpd.stop(0);
            httpd = null;
        }
        LOGGER.info("[AGI-MC] HTTP service stopped");
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.equals("Bearer " + config.getAuthToken())) {
            send(exchange, 401, errorBody(null, -32001, "Unauthorized").toString());
            return false;
        }

        String ip = clientIp(exchange);
        if (!rateLimiter.tryAcquire(ip)) {
            send(exchange, 429, errorBody(null, -32000, "Rate limit exceeded").toString());
            return false;
        }
        return true;
    }

    private void handleRpc(HttpExchange exchange) throws IOException {
        JsonNode req;
        try {
            req = mapper.readTree(readAll(exchange.getRequestBody()));
        } catch (Exception e) {
            send(exchange, 400, errorBody(null, -32700, "Parse error").toString());
            return;
        }

        String method = req.path("method").asText(null);
        JsonNode params = req.path("params");
        JsonNode id = req.get("id");

        if (method == null) {
            send(exchange, 400, errorBody(id, -32600, "Invalid Request").toString());
            return;
        }

        try {
            Object result = handler.dispatch(method, params);
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            if (id != null) resp.set("id", id);
            resp.set("result", mapper.valueToTree(result));
            send(exchange, 200, resp.toString());
        } catch (RpcException e) {
            send(exchange, 200, errorBody(id, e.getCode(), e.getMessage()).toString());
        } catch (Exception e) {
            LOGGER.error("[AGI-MC] RPC failed: " + method, e);
            send(exchange, 200, errorBody(id, -32603, "Internal error: " + e.getMessage()).toString());
        }
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private ObjectNode errorBody(JsonNode id, int code, String message) {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return resp;
    }

    private static String clientIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() == null
                || exchange.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}
