package dev.agi.mcbot.net;

import com.fasterxml.jackson.databind.*;
import dev.agi.mcbot.AgiMcBotMod;
import dev.agi.mcbot.config.ModConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.*;

/**
 * HTTP JSON-RPC 服务。
 *
 * 端点：
 *   POST /rpc   JSON-RPC 2.0
 *   GET  /health 健康检查（无需鉴权）
 *
 * 鉴权：Header "Authorization: Bearer <token>"
 */
public class HttpServer {
    private final ModConfig config;
    private final MinecraftServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RpcHandler handler;
    private final RateLimiter rateLimiter;

    private Javalin app;

    public HttpServer(ModConfig config, MinecraftServer server) {
        this.config = config;
        this.server = server;
        this.handler = new RpcHandler(server);
        this.rateLimiter = new RateLimiter(config.getRateLimit());
    }

    public void start() {
        app = Javalin.create(cfg -> {
                    cfg.showJavalinBanner = false;
                })
                .before(ctx -> {
                    // 健康检查跳过鉴权
                    if (ctx.path().equals("/health")) return;

                    String auth = ctx.header("Authorization");
                    if (auth == null || !auth.equals("Bearer " + config.getAuthToken())) {
                        ctx.status(401).json(Map.of(
                                "jsonrpc", "2.0",
                                "error", Map.of("code", -32001, "message", "Unauthorized"),
                                "id", (Object) null
                        ));
                        return;
                    }

                    // 速率限制
                    if (!rateLimiter.tryAcquire(ctx.ip())) {
                        ctx.status(429).json(Map.of(
                                "jsonrpc", "2.0",
                                "error", Map.of("code", -32002, "message", "Rate limit exceeded"),
                                "id", (Object) null
                        ));
                    }
                })
                .get("/health", ctx -> ctx.json(Map.of("status", "ok")))
                .post("/rpc", this::handleRpc)
                .start(config.getBindAddress(), config.getBindPort());
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    private void handleRpc(Context ctx) {
        try {
            JsonNode req = mapper.readTree(ctx.body());
            String method = req.path("method").asText();
            JsonNode params = req.path("params");
            JsonNode id = req.get("id");

            Object result = handler.dispatch(method, params);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jsonrpc", "2.0");
            resp.put("result", result);
            resp.put("id", id);
            ctx.json(resp);

        } catch (RpcException e) {
            sendError(ctx, ctx.body().isEmpty() ? null : safeId(ctx.body()), e.getCode(), e.getMessage());
        } catch (Exception e) {
            AgiMcBotMod.LOGGER.error("[AGI-MC] RPC 处理异常", e);
            sendError(ctx, null, -32603, "Internal error: " + e.getMessage());
        }
    }

    private Object safeId(String body) {
        try {
            return mapper.readTree(body).get("id");
        } catch (Exception e) {
            return null;
        }
    }

    private void sendError(Context ctx, Object id, int code, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("error", Map.of("code", code, "message", message));
        resp.put("id", id);
        ctx.json(resp);
    }

    /**
     * 简单令牌桶速率限制器。
     */
    static class RateLimiter {
        private final int maxPerSecond;
        private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

        RateLimiter(int maxPerSecond) {
            this.maxPerSecond = maxPerSecond;
        }

        boolean tryAcquire(String key) {
            long now = System.currentTimeMillis();
            Deque<Long> q = buckets.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
            synchronized (q) {
                while (!q.isEmpty() && now - q.peekFirst() > 1000) {
                    q.pollFirst();
                }
                if (q.size() >= maxPerSecond) return false;
                q.addLast(now);
                return true;
            }
        }
    }
}
