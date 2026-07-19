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
  let value = "";
  try {
    value = decodeURIComponent(window.location.hash.slice(1));
    if (value) window.sessionStorage.setItem("progressivestages.editor.secret", value);
    else value = window.sessionStorage.getItem("progressivestages.editor.secret") || "";
  } catch {
    value = window.location.hash.slice(1);
  }
  if (window.location.hash) window.history.replaceState(null, "", window.location.pathname);
  return value;
}

export class EditorApi {
  private readonly secret: string;

  constructor(secret = consumeSecret()) {
    this.secret = secret;
  }

  async request<T>(payload: Record<string, unknown>, signal?: AbortSignal): Promise<T> {
    if (!this.secret) throw new EditorApiError("missing_session",
      "This tab does not have a Minecraft editor session. Close it, return to Minecraft, and open the editor again.", {});
    let response: Response;
    try {
      response = await fetch("/api/request", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.secret}`,
          "X-ProgressiveStages-Token": this.secret,
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
      const fallback = code === "forbidden"
        ? "This editor tab could not prove that Minecraft opened it. Close this tab, return to Minecraft, and open the editor again."
        : "The server rejected the request.";
      throw new EditorApiError(code, String(data.explanation || fallback), data);
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
