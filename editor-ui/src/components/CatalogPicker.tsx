import { useEffect, useMemo, useState } from "react";
import { normalizeSelector } from "../lib/model";
import { useEditor } from "../store/EditorContext";
import type { CatalogEntry } from "../types";
import { Badge, Button, EmptyState, Field } from "./ui";

export function CatalogPicker({ catalogId, selected = "", allowPrefixes = true, onPick }:
  { catalogId: string; selected?: string; allowPrefixes?: boolean; onPick: (value: string, entry?: CatalogEntry) => void }) {
  const { catalog, closeDialog } = useEditor();
  const [mode, setMode] = useState("id");
  const [search, setSearch] = useState("");
  const [mod, setMod] = useState("");
  const [entries, setEntries] = useState<CatalogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const mods = useMemo(() => [...new Map(entries.filter(entry => entry.modId).map(entry => [entry.modId, entry.modName || entry.modId])).entries()], [entries]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const result = await catalog(catalogId, "picker", mode, search, mod ? { mod } : {}, "", 60);
        if (!controller.signal.aborted) setEntries(result.entries);
      } catch (failure) {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : String(failure));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [catalog, catalogId, mode, mod, search]);

  const choose = (entry: CatalogEntry) => {
    const value = allowPrefixes ? normalizeSelector(mode, entry.key, search) : entry.key.replace(/^id:/, "");
    onPick(value, entry);
    closeDialog();
  };

  return <div className="catalog-picker">
    <div className="catalog-toolbar">
      {allowPrefixes ? <Field label="Selection method"><select value={mode} onChange={event => setMode(event.target.value)}><option value="all">Everything in this category</option><option value="id">Exact identifier</option><option value="mod">Whole mod</option><option value="tag">Content tag</option><option value="name">Name contains text</option></select></Field> : null}
      <Field label="Search"><input value={search} onChange={event => setSearch(event.target.value)} autoFocus placeholder="Start typing an item or mod name"/></Field>
      <Field label="Installed mod"><select value={mod} onChange={event => setMod(event.target.value)}><option value="">Every installed mod</option>{mods.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></Field>
    </div>
    {selected ? <div className="current-selection"><span>Current selection</span><code>{selected}</code></div> : null}
    <div className="catalog-result-list" aria-live="polite">
      {loading ? <div className="catalog-loading"><span/><p>Searching the running server</p></div>
        : error ? <EmptyState icon="warning" title="Registry search failed" description={error}/>
        : entries.length ? entries.map(entry => <button key={`${entry.key}:${entry.sourceType}`} className="catalog-result" onClick={() => choose(entry)}>
          <span className="catalog-cube">◆</span><span><strong>{entry.label || entry.key}</strong><code>{entry.key}</code></span><span className="catalog-meta"><Badge>{entry.modName || entry.modId || entry.namespace}</Badge><small>{entry.sourceType}</small></span>
        </button>)
        : <EmptyState icon="search" title="No matching registry entries" description="Try a broader search or remove the mod filter."/>}
    </div>
    <footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Cancel</Button></footer>
  </div>;
}

export function InlineCatalogSearch({ catalogId, mode, onPick }:
  { catalogId: string; mode: string; onPick: (value: string, entry: CatalogEntry) => void }) {
  const { catalog } = useEditor();
  const [search, setSearch] = useState("");
  const [mod, setMod] = useState("");
  const [entries, setEntries] = useState<CatalogEntry[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!open) return;
    let active = true;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        const result = await catalog(catalogId, "inline_picker", mode, search, mod ? { mod } : {}, "", 30);
        if (active) setEntries(result.entries);
      } finally {
        if (active) setLoading(false);
      }
    }, 180);
    return () => { active = false; window.clearTimeout(timer); };
  }, [catalog, catalogId, mode, mod, open, search]);
  const mods = [...new Map(entries.filter(entry => entry.modId).map(entry => [entry.modId, entry.modName || entry.modId])).entries()];
  return <div className="inline-catalog">
    <Button type="button" tone="quiet" icon="search" onClick={() => setOpen(value => !value)}>{open ? "Hide registry" : "Browse server registry"}</Button>
    {open ? <div className="inline-catalog-panel"><div className="inline-catalog-tools"><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search registered content"/><select value={mod} onChange={event => setMod(event.target.value)}><option value="">Every mod</option>{mods.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></div><div className="inline-catalog-results">{loading ? <span className="muted">Searching the server registry</span> : entries.map(entry => <button type="button" key={`${entry.key}:${entry.sourceType}`} onClick={() => { onPick(normalizeSelector(mode, entry.key, search), entry); setOpen(false); }}><span><strong>{entry.label}</strong><code>{entry.key}</code></span><small>{entry.modName || entry.modId}</small></button>)}</div></div> : null}
  </div>;
}
