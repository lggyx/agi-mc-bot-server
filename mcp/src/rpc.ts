/**
 * JSON-RPC 客户端：MCP Server -> Fabric Mod HTTP 服务。
 */

export interface RpcConfig {
  host: string;
  port: number;
  token: string;
  timeoutMs: number;
}

export class RpcError extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message);
    this.name = 'RpcError';
  }
}

export class RpcClient {
  private idCounter = 0;
  private readonly baseUrl: string;

  constructor(private readonly config: RpcConfig) {
    this.baseUrl = `http://${config.host}:${config.port}/rpc`;
  }

  async call<T = unknown>(method: string, params: unknown = {}): Promise<T> {
    const id = ++this.idCounter;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.config.timeoutMs);

    try {
      const res = await fetch(this.baseUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.config.token}`,
        },
        body: JSON.stringify({ jsonrpc: '2.0', method, params, id }),
        signal: controller.signal,
      });

      if (res.status === 401) throw new RpcError(-32001, '鉴权失败，请检查 token');
      if (res.status === 429) throw new RpcError(-32002, '请求过于频繁');

      const body = (await res.json()) as {
        result?: T;
        error?: { code: number; message: string };
        id: number;
      };

      if (body.error) {
        throw new RpcError(body.error.code, body.error.message);
      }
      return body.result as T;
    } catch (e) {
      if (e instanceof RpcError) throw e;
      if ((e as Error).name === 'AbortError') {
        throw new RpcError(-32003, `RPC 超时 (${this.config.timeoutMs}ms)`);
      }
      throw new RpcError(-32000, `网络错误: ${(e as Error).message}`);
    } finally {
      clearTimeout(timer);
    }
  }

  async health(): Promise<boolean> {
    try {
      const res = await fetch(`http://${this.config.host}:${this.config.port}/health`);
      return res.ok;
    } catch {
      return false;
    }
  }
}
