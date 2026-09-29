import { Button, EmptyState, Hint } from "../../components/ui";
import { ValidationMessages } from "../../components/ValidationMessages";
import { Icon } from "../../components/Icon";
import { useEditor } from "../../store/EditorContext";
import type { StageTab } from "../../types";
import { AdvancedPanel } from "./AdvancedPanel";
import { EffectsPanel } from "./EffectsPanel";
import { EssentialsPanel } from "./EssentialsPanel";
import { IntegrationsPanel } from "./IntegrationsPanel";
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
  { id: "integrations", label: "Access", icon: "extensions" },
  { id: "source", label: "Source", icon: "code" }
];

export function StageWorkspace() {
  const { selectedStage: stage, stageTab, setStageTab, openDialog, validationResult } = useEditor();
  if (!stage) return <div className="workspace-empty"><img src="/favicon.svg" alt="ProgressiveStages lock logo"/><EmptyState icon="stages" title="Build your first progression stage" description="Create a class, age, quest gate, one time upgrade, temporary power, or hidden milestone. The editor writes every file for you." action={<Button tone="primary" icon="plus" onClick={() => openDialog({ title: "Create a new stage", description: "Only the player facing name and namespace are required.", content: <IdentityForm mode="create"/> })}>Create stage</Button>}/></div>;
  return <div className="stage-workspace">
    <header className="stage-hero-new"><div><h1>{stage.name}</h1><div className="stage-meta"><Hint text="The stable identifier used in stage files, commands, scripts, and integrations."><code>{stage.id}</code></Hint><Hint text="The category groups this stage in the editor and player map."><span>{stage.category}</span></Hint>{stage.hidden ? <Hint text="Hidden stages are omitted from normal player discovery until their reveal rules allow them."><span>Hidden</span></Hint> : null}</div><p>{stage.description || "Add a description so players understand what this stage changes."}</p></div><StageActions stage={stage}/></header>
    <nav className="stage-tabs" aria-label="Stage editor sections">{TABS.map(tab => <button key={tab.id} title={`Edit ${tab.label.toLowerCase()} for this stage`} className={stageTab === tab.id ? "active" : ""} onClick={() => setStageTab(tab.id)}><Icon name={tab.icon} size={17}/><span>{tab.label}</span>{tab.id === "rules" && stage.ruleCount ? <em title="Number of active rule entries">{stage.ruleCount}</em> : null}{tab.id === "progression" && stage.grantCount + stage.revokeCount ? <em title="Number of grant and revoke triggers">{stage.grantCount + stage.revokeCount}</em> : null}</button>)}</nav>
    {validationResult && (!validationResult.valid || validationResult.warnings.length || validationResult.diagnostics?.length) ?
      <details className="draft-validation"><summary>Draft validation. {validationResult.errors.length} {validationResult.errors.length === 1 ? "error" : "errors"}, {validationResult.warnings.length} {validationResult.warnings.length === 1 ? "warning" : "warnings"}.</summary><ValidationMessages validation={validationResult}/></details> : null}
    <div className="stage-tab-content">{stageTab === "essentials" ? <EssentialsPanel stage={stage}/> : stageTab === "rules" ? <RulesPanel stage={stage}/> : stageTab === "progression" ? <ProgressionPanel stage={stage}/> : stageTab === "effects" ? <EffectsPanel stage={stage}/> : stageTab === "advanced" ? <AdvancedPanel stage={stage}/> : stageTab === "integrations" ? <IntegrationsPanel stage={stage}/> : <SourcePanel stage={stage}/>}</div>
  </div>;
}
