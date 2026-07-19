import { useEffect, useState } from "react";
import { Badge, Button, EmptyState, Field } from "../components/ui";
import { Icon } from "../components/Icon";
import { useEditor } from "../store/EditorContext";
import type { CatalogEntry } from "../types";

export function RegistryPage() {
  const { boot, catalog, notify } = useEditor();
  const [catalogId, setCatalogId] = useState(boot?.catalog.ids[0] || "items");
  const [mode, setMode] = useState("id");
  const [query, setQuery] = useState("");
  const [mod, setMod] = useState("");
  const [entries, setEntries] = useState<CatalogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const timer = window.setTimeout(() => void catalog(catalogId, "registry.browser", mode, query, mod ? { mod } : {}, "", 100).then(result => { if (!cancelled) { setEntries(result.entries); setTotal(result.totalMatches); } }).finally(() => { if (!cancelled) setLoading(false); }), 180);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [catalog, catalogId, mode, mod, query]);
  const mods = [...new Map(entries.filter(entry => entry.modId).map(entry => [entry.modId, entry.modName || entry.modId])).entries()];
  return <div className="registry-page"><header className="page-heading"><div><h1>Registry</h1><p>Search installed mods, identifiers, names, and tags before adding them to a stage.</p></div></header>
    <section className="registry-toolbar"><Field label="Content type"><select value={catalogId} onChange={event => { setCatalogId(event.target.value); setMod(""); }}>{boot?.catalog.ids.map(id => <option key={id} value={id}>{id.replace(/^progressivestages:/, "")}</option>)}</select></Field><Field label="Selector mode"><select value={mode} onChange={event => setMode(event.target.value)}><option value="all">Everything</option><option value="id">Exact identifier</option><option value="mod">Whole mod</option><option value="tag">Tag</option><option value="name">Name match</option></select></Field><Field label="Search"><div className="input-icon"><Icon name="search" size={16}/><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Diamond, minecraft, c:ores"/></div></Field><Field label="Installed mod"><select value={mod} onChange={event => setMod(event.target.value)}><option value="">Every mod</option>{mods.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></Field></section>
    <div className="registry-summary"><span>{loading ? "Searching the live registry" : `${entries.length} of ${total} matching entries`}</span><code>{mode}:{query || "..."}</code></div>
    {entries.length ? <div className="registry-grid">{entries.map(entry => <article key={`${entry.catalogId}:${entry.key}`}><span className="catalog-cube">◆</span><div><strong>{entry.label}</strong><code>{entry.key}</code><p>{entry.modName || entry.modId || entry.namespace}</p><div className="chip-row">{entry.tags.slice(0, 3).map(tag => <Badge key={tag}>{tag}</Badge>)}</div></div><Button tone="quiet" onClick={() => void navigator.clipboard.writeText(entry.key).then(() => notify("success", "Selector copied", entry.key))}>Copy</Button></article>)}</div> : <EmptyState icon="search" title={loading ? "Searching the registry" : "No registry matches"} description="Change the content type, selector mode, mod filter, or search terms."/>}
  </div>;
}
