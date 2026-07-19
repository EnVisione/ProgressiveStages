import { useMemo, useState } from "react";
import { Button, EmptyState } from "../../components/ui";
import { Icon } from "../../components/Icon";
import { useEditor } from "../../store/EditorContext";
import { IdentityForm, ImportStageForm } from "./StageDialogs";

export function StageSidebar() {
  const { stages, selectedStageKey, selectStage, openDialog } = useEditor();
  const [search, setSearch] = useState("");
  const [showArchived, setShowArchived] = useState(false);
  const filtered = useMemo(() => stages.filter(stage => {
    if (!showArchived && stage.archived) return false;
    const query = search.trim().toLowerCase();
    return !query || `${stage.name} ${stage.id} ${stage.description} ${stage.category}`.toLowerCase().includes(query);
  }), [search, showArchived, stages]);
  const groups = useMemo(() => {
    const result = new Map<string, typeof filtered>();
    for (const stage of filtered) {
      const group = stage.archived ? "Archived" : stage.category || "Uncategorized";
      result.set(group, [...(result.get(group) || []), stage]);
    }
    return result;
  }, [filtered]);
  return <aside className="stage-sidebar">
    <header className="stage-sidebar-header"><div><h2>Stages</h2><span>{stages.filter(stage => !stage.archived).length} total</span></div><Button tone="primary" icon="plus" aria-label="Create a stage" onClick={() => openDialog({ title: "Create a new stage", description: "Start with a name. Every backing file is created for you.", content: <IdentityForm mode="create"/> })}>New</Button></header>
    <div className="stage-search"><Icon name="search" size={17}/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search stages" aria-label="Search stages"/></div>
    <nav className="stage-list" aria-label="Stage library">
      {filtered.length ? [...groups.entries()].map(([group, entries]) => <section key={group} className="stage-group"><header><span>{group}</span><span>{entries.length}</span></header>{entries.map(stage =>
        <button key={stage.key} className={`stage-list-item ${selectedStageKey === stage.key ? "active" : ""}`} onClick={() => selectStage(stage.key)}>
          <span><strong>{stage.name}</strong><small>{stage.id}</small></span>
          {stage.ruleCount ? <span className="stage-rule-count">{stage.ruleCount} rule{stage.ruleCount === 1 ? "" : "s"}</span> : null}
        </button>)}</section>)
        : <EmptyState icon="search" title="No matching stages" description="Try another search or create a new stage."/>}
    </nav>
    <footer className="stage-sidebar-footer"><label className="archive-toggle"><input type="checkbox" checked={showArchived} onChange={event => setShowArchived(event.target.checked)}/>Show archived</label><Button tone="quiet" icon="file" onClick={() => openDialog({ title: "Import a stage package", description: "Import a previous ProgressiveStages editor export.", content: <ImportStageForm/>, width: "wide" })}>Import</Button></footer>
  </aside>;
}
