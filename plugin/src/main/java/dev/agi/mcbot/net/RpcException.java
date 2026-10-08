package dev.agi.mcbot.net;

/**
 * RPC exception carrying a JSON-RPC error code.
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
