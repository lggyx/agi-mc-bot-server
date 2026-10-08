package dev.agi.mcbot.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.agi.mcbot.config.ModConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP JSON-RPC service.
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
    private final MinecraftServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RpcHandler handler;
    private final RateLimiter rateLimiter;

    private Javalin app;
    private ScheduledExecutorService scheduler;

    public HttpServer(ModConfig config, MinecraftServer server) {
        this.config = config;
        this.server = server;
        this.handler = new RpcHandler(server);
        this.rateLimiter = new RateLimiter(config.getRateLimit());
    }

    public void start() {
        app = Javalin.create()
                .before(ctx -> {
                    if (ctx.path().equals("/health")) return;

                    String auth = ctx.header("Authorization");
                    if (auth == null || !auth.equals("Bearer " + config.getAuthToken())) {
                        ctx.status(401).json(errorBody(null, -32001, "Unauthorized"));
                        return;
                    }

                    if (!rateLimiter.tryAcquire(ctx.ip())) {
                        ctx.status(429).json(errorBody(null, -32000, "Rate limit exceeded"));
                    }
                })
                .post("/rpc", this::handleRpc)
                .get("/health", ctx -> {
                    Map<String, String> health = new java.util.HashMap<>();
                    health.put("status", "ok");
                    ctx.json(health);
                })
                .exception(Exception.class, (e, ctx) -> {
                    LOGGER.error("[AGI-MC] HTTP exception", e);
                    ctx.status(500).json(errorBody(null, -32603, "Internal error"));
                })
                .start(config.getBindAddress(), config.getBindPort());

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
        if (app != null) {
            app.stop();
            app = null;
        }
        LOGGER.info("[AGI-MC] HTTP service stopped");
    }

    private void handleRpc(Context ctx) {
        JsonNode req;
        try {
            req = mapper.readTree(ctx.body());
        } catch (Exception e) {
            ctx.json(errorBody(null, -32700, "Parse error"));
            return;
        }

        String method = req.path("method").asText(null);
        JsonNode params = req.path("params");
        JsonNode id = req.get("id");

        if (method == null) {
            ctx.json(errorBody(id, -32600, "Invalid Request"));
            return;
        }

        try {
            Object result = handler.dispatch(method, params);
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            if (id != null) resp.set("id", id);
            resp.set("result", mapper.valueToTree(result));
            ctx.json(resp);
        } catch (RpcException e) {
            ctx.json(errorBody(id, e.getCode(), e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("[AGI-MC] RPC failed: " + method, e);
            ctx.json(errorBody(id, -32603, "Internal error: " + e.getMessage()));
        }
    }

    private ObjectNode errorBody(JsonNode id, int code, String message) {
        ObjectNode resp = new ObjectMapper().createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return resp;
    }
}
