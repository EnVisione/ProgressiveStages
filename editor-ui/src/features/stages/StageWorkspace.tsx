import { Badge, Button, EmptyState } from "../../components/ui";
import { Icon } from "../../components/Icon";
import { title } from "../../lib/model";
import { useEditor } from "../../store/EditorContext";
import type { StageTab } from "../../types";
import { AdvancedPanel } from "./AdvancedPanel";
import { EffectsPanel } from "./EffectsPanel";
import { EssentialsPanel } from "./EssentialsPanel";
import { ProgressionPanel } from "./ProgressionPanel";
import { RulesPanel } from "./RulesPanel";
import { SourcePanel } from "./SourcePanel";
import { StageActions } from "./StageActions";
import { IdentityForm } from "./StageDialogs";

const TABS: Array<{ id: StageTab; label: string; icon: string }> = [
  { id: "essentials", label: "Essentials", icon: "stages" },
  { id: "rules", label: "Rules", icon: "rules" },
  { id: "progression", label: "Progression", icon: "progression" },
  { id: "effects", label: "Rewards and effects", icon: "gift" },
  { id: "advanced", label: "Advanced", icon: "extensions" },
  { id: "source", label: "TOML source", icon: "code" }
];

export function StageWorkspace() {
  const { selectedStage: stage, stageTab, setStageTab, openDialog, setPage, boot } = useEditor();
  if (!stage) return <div className="workspace-empty"><img src="/logo.png" alt="ProgressiveStages lock logo"/><EmptyState icon="stages" title="Build your first progression stage" description="Create a class, age, quest gate, one time upgrade, temporary power, or hidden milestone. The editor writes every file for you." action={<Button tone="primary" icon="plus" onClick={() => openDialog({ title: "Create a new stage", description: "Only the player facing name and namespace are required.", content: <IdentityForm mode="create"/> })}>Create stage</Button>}/></div>;
  return <div className="stage-workspace">
    <header className="stage-hero-new"><div className="stage-hero-icon">◆</div><div><div className="eyebrow">{stage.category}</div><h1>{stage.name}</h1><p>{stage.description || "Add a description so players understand what this stage changes."}</p><div className="hero-badges"><Badge tone="gold">{stage.id}</Badge><Badge>{stage.dependencies.length ? `${stage.dependencies.length} prerequisite path${stage.dependencies.length === 1 ? "" : "s"}` : "Beginner path"}</Badge>{stage.hidden ? <Badge tone="warning">Hidden</Badge> : null}</div></div><StageActions stage={stage}/></header>
    <nav className="stage-tabs" aria-label="Stage editor sections">{TABS.map(tab => <button key={tab.id} className={stageTab === tab.id ? "active" : ""} onClick={() => setStageTab(tab.id)}><Icon name={tab.icon} size={17}/><span>{tab.label}</span>{tab.id === "rules" && stage.ruleCount ? <em>{stage.ruleCount}</em> : null}{tab.id === "progression" && stage.grantCount + stage.revokeCount ? <em>{stage.grantCount + stage.revokeCount}</em> : null}</button>)}</nav>
    <div className="stage-edit-grid"><div className="stage-tab-content">{stageTab === "essentials" ? <EssentialsPanel stage={stage}/> : stageTab === "rules" ? <RulesPanel stage={stage}/> : stageTab === "progression" ? <ProgressionPanel stage={stage}/> : stageTab === "effects" ? <EffectsPanel stage={stage}/> : stageTab === "advanced" ? <AdvancedPanel stage={stage}/> : <SourcePanel stage={stage}/>}</div><aside className="context-inspector"><header><span><Icon name="spark" size={17}/></span><div><strong>Inspector</strong><small>Help for {TABS.find(tab => tab.id === stageTab)?.label}</small></div></header><div className="inspector-help"><strong>{stageTab === "essentials" ? "Shape the stage first" : stageTab === "rules" ? "Highest matching priority wins" : stageTab === "progression" ? "Ownership can change in both directions" : stageTab === "effects" ? "Effects are server authoritative" : stageTab === "advanced" ? "Deep systems stay event driven" : "Source is optional"}</strong><p>{stageTab === "essentials" ? "Identity, dependencies, stacking, and map appearance explain where this stage belongs." : stageTab === "rules" ? "Broad locks can be carved out by a higher priority allow rule or exception. Simulate uncertain combinations before applying." : stageTab === "progression" ? "Grant and revoke triggers share the same condition library. Commands, quests, Java, and KubeJS remain available." : stageTab === "effects" ? "Rewards run on genuine grants. Attributes, abilities, item modifiers, and drops are checked by the server." : stageTab === "advanced" ? "Challenges, variables, formulas, profiles, states, and templates are compiled and subscribed to relevant events." : "Visual controls preserve unknown TOML. Use source only when an extension or exact value needs it."}</p></div><div className="inspector-stats"><div><span>Rules</span><strong>{stage.ruleCount}</strong></div><div><span>Obtain</span><strong>{stage.grantCount}</strong></div><div><span>Revoke</span><strong>{stage.revokeCount}</strong></div><div><span>Files</span><strong>{stage.legacy ? 1 : 3}</strong></div></div><div className="inspector-links"><button onClick={() => setPage("layout")}><Icon name="layout" size={16}/><span><strong>Player layout</strong><small>Place icons and edit branches.</small></span></button><button onClick={() => setPage("registry")}><Icon name="search" size={16}/><span><strong>Live registry</strong><small>{boot?.catalog.ids.length || 0} content catalogs.</small></span></button><button onClick={() => setPage("extensions")}><Icon name="extensions" size={16}/><span><strong>Extensions</strong><small>Java and KubeJS capabilities.</small></span></button></div></aside></div>
    <footer className="stage-file-footnote"><Icon name="file" size={16}/><span>{stage.legacy ? "Legacy single file stage" : "Three file stage package"}</span><code>{stage.folder || stage.stagePath}</code><span>{title(stage.dependencyMode)} dependency policy</span></footer>
  </div>;
}
