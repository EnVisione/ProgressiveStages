import { Button, EmptyState } from "../../components/ui";
import { Icon } from "../../components/Icon";
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
  { id: "essentials", label: "Setup", icon: "stages" },
  { id: "rules", label: "Rules", icon: "rules" },
  { id: "progression", label: "Progression", icon: "progression" },
  { id: "effects", label: "Rewards", icon: "gift" },
  { id: "advanced", label: "Advanced", icon: "extensions" },
  { id: "source", label: "Source", icon: "code" }
];

export function StageWorkspace() {
  const { selectedStage: stage, stageTab, setStageTab, openDialog } = useEditor();
  if (!stage) return <div className="workspace-empty"><img src="/logo.png" alt="ProgressiveStages lock logo"/><EmptyState icon="stages" title="Build your first progression stage" description="Create a class, age, quest gate, one time upgrade, temporary power, or hidden milestone. The editor writes every file for you." action={<Button tone="primary" icon="plus" onClick={() => openDialog({ title: "Create a new stage", description: "Only the player facing name and namespace are required.", content: <IdentityForm mode="create"/> })}>Create stage</Button>}/></div>;
  return <div className="stage-workspace">
    <header className="stage-hero-new"><div><h1>{stage.name}</h1><div className="stage-meta"><code>{stage.id}</code><span>{stage.category}</span>{stage.hidden ? <span>Hidden</span> : null}</div><p>{stage.description || "Add a description so players understand what this stage changes."}</p></div><StageActions stage={stage}/></header>
    <nav className="stage-tabs" aria-label="Stage editor sections">{TABS.map(tab => <button key={tab.id} className={stageTab === tab.id ? "active" : ""} onClick={() => setStageTab(tab.id)}><Icon name={tab.icon} size={17}/><span>{tab.label}</span>{tab.id === "rules" && stage.ruleCount ? <em>{stage.ruleCount}</em> : null}{tab.id === "progression" && stage.grantCount + stage.revokeCount ? <em>{stage.grantCount + stage.revokeCount}</em> : null}</button>)}</nav>
    <div className="stage-tab-content">{stageTab === "essentials" ? <EssentialsPanel stage={stage}/> : stageTab === "rules" ? <RulesPanel stage={stage}/> : stageTab === "progression" ? <ProgressionPanel stage={stage}/> : stageTab === "effects" ? <EffectsPanel stage={stage}/> : stageTab === "advanced" ? <AdvancedPanel stage={stage}/> : <SourcePanel stage={stage}/>}</div>
  </div>;
}
