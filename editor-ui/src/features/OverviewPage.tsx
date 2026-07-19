import { Badge, Button, EmptyState } from "../components/ui";
import { Icon } from "../components/Icon";
import { useEditor } from "../store/EditorContext";
import { IdentityForm } from "./stages/StageDialogs";

export function OverviewPage() {
  const { boot, stages, selectStage, setPage, openDialog, validate } = useEditor();
  if (!boot) return null;
  const active = stages.filter(stage => !stage.archived);
  const rules = active.reduce((sum, stage) => sum + stage.ruleCount, 0);
  const progression = active.reduce((sum, stage) => sum + stage.grantCount + stage.revokeCount, 0);
  return <div className="overview-page">
    <section className="welcome-card"><div className="welcome-copy"><div className="eyebrow">Server authoritative stage studio</div><h1>Build progression without touching a config file.</h1><p>Create branching classes, ages, temporary powers, hidden milestones, purchases, movement gates, rewards, structures, and modded content through one guided editor.</p><div className="welcome-actions"><Button tone="primary" icon="plus" onClick={() => openDialog({ title: "Create a new stage", description: "Start with a name and namespace. The editor creates the entire package.", content: <IdentityForm mode="create"/> })}>Create a stage</Button><Button onClick={() => setPage("layout")} icon="layout">Open player layout</Button></div></div><div className="logo-showcase"><span className="logo-glow"/><img src="/logo.png" alt="ProgressiveStages lock surrounded by Minecraft blocks"/><Badge tone="gold">Version 3.0.1 editor</Badge></div></section>
    <section className="overview-metrics"><article><span><Icon name="stages"/></span><strong>{active.length}</strong><p>Active stages</p></article><article><span><Icon name="rules"/></span><strong>{rules}</strong><p>Rules and gates</p></article><article><span><Icon name="progression"/></span><strong>{progression}</strong><p>Grant and revoke triggers</p></article><article><span><Icon name="file"/></span><strong>{boot.draft.diff.length}</strong><p>Changed files in draft</p></article></section>
    <div className="overview-columns">
      <section className="dashboard-card"><header><div><h2>Draft health</h2><p>Changes remain isolated until an operator reviews and applies them.</p></div><Badge tone={boot.draft.diff.length ? "warning" : "success"}>{boot.draft.diff.length ? `${boot.draft.diff.length} pending` : "Synchronized"}</Badge></header>
        {boot.draft.diff.length ? <div className="diff-mini-list">{boot.draft.diff.slice(0, 6).map(entry => <button key={entry.path} onClick={() => { const stage = stages.find(value => [value.stagePath, value.rulesPath, value.progressionPath].includes(entry.path)); if (stage) selectStage(stage.key); }}><Badge tone={entry.change === "ADDED" ? "success" : entry.change === "DELETED" ? "danger" : "warning"}>{entry.change}</Badge><code>{entry.path}</code><span>{entry.beforeBytes} to {entry.afterBytes} bytes</span></button>)}</div> : <EmptyState icon="check" title="The draft matches the live server" description="Create or edit a stage to begin a safe server side draft."/>}
        <footer><Button onClick={() => void validate()}>Check every stage</Button><Button tone="primary" onClick={() => setPage("stages")}>Continue building</Button></footer>
      </section>
      <section className="dashboard-card"><header><div><h2>Stage library</h2><p>Recently authored progression choices.</p></div><Button tone="quiet" onClick={() => setPage("stages")}>View all</Button></header>
        <div className="recent-stages">{active.slice(0, 7).map(stage => <button key={stage.key} onClick={() => selectStage(stage.key)}><span className="stage-orb">◆</span><span><strong>{stage.name}</strong><small>{stage.id}</small></span><Badge>{stage.ruleCount} rules</Badge><Icon name="arrow" size={15}/></button>)}</div>
      </section>
    </div>
    {boot.catalog.providerErrors.length ? <section className="provider-warning"><Icon name="warning"/><div><strong>Some registry providers reported problems</strong><p>{boot.catalog.providerErrors.join(" ")}</p></div></section> : null}
  </div>;
}
