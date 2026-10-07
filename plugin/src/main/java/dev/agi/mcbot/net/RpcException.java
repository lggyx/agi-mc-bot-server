package dev.agi.mcbot.net;

/**
 * RPC 业务异常，携带 JSON-RPC 错误码。
 */
public class RpcException extends RuntimeException {
    private final int code;

    public RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
