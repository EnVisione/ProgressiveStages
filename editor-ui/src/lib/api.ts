import type { Bootstrap, CatalogPage } from "../types";

export class EditorApiError extends Error {
  readonly code: string;
  readonly data: Record<string, unknown>;

  constructor(code: string, message: string, data: Record<string, unknown>) {
    super(message);
    this.name = "EditorApiError";
    this.code = code;
    this.data = data;
  }
}

function consumeSecret(): string {
  const value = decodeURIComponent(window.location.hash.slice(1));
  window.history.replaceState(null, "", window.location.pathname);
  return value;
}

export class EditorApi {
  private readonly secret: string;

  constructor(secret = consumeSecret()) {
    this.secret = secret;
  }

  async request<T>(payload: Record<string, unknown>, signal?: AbortSignal): Promise<T> {
    let response: Response;
    try {
      response = await fetch("/api/request", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.secret}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload),
        signal: signal || AbortSignal.timeout(30_000)
      });
    } catch (failure) {
      const timedOut = failure instanceof DOMException && failure.name === "TimeoutError";
      throw new EditorApiError(timedOut ? "timeout" : "network_error",
        timedOut ? "Minecraft did not answer within thirty seconds." : "The local Minecraft editor bridge could not be reached.", {});
    }
    let data: Record<string, unknown>;
    try {
      data = await response.json() as Record<string, unknown>;
    } catch {
      throw new EditorApiError("invalid_response", "The editor bridge returned an unreadable response.", {});
    }
    if (!response.ok || data.error) {
      const code = String(data.error || `http_${response.status}`);
      throw new EditorApiError(code, String(data.explanation || "The server rejected the request."), data);
    }
    return data as T;
  }

  bootstrap(): Promise<Bootstrap> {
    return this.request<Bootstrap>({ action: "bootstrap" });
  }

  catalog(catalog: string, field: string, mode: string, text: string, revision: number,
          filters: Record<string, string> = {}, cursor = "", pageSize = 50): Promise<CatalogPage> {
    return this.request<CatalogPage>({
      action: "catalog",
      catalog: catalog.includes(":") ? catalog : `progressivestages:${catalog}`,
      field,
      mode,
      text,
      filters,
      cursor,
      pageSize,
      catalogRevision: revision
    });
  }
}
