import { Button } from "../../components/ui";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";
import { CollaboratorForm, ConfirmStageAction, IdentityForm, MoveStageForm } from "./StageDialogs";

export function StageActions({ stage }: { stage: StagePackage }) {
  const { api, openDialog, notify } = useEditor();
  const exportStage = async () => {
    const result = await api.request<{ folder: string; files: Record<string, string> }>({ action: "export_stage", folder: stage.folder });
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${stage.name.toLowerCase().replace(/[^a-z0-9]+/g, "_")}.progressivestages.json`;
    link.click();
    URL.revokeObjectURL(url);
    notify("success", "The stage package was exported");
  };
  if (stage.legacy) return <Button tone="quiet" disabled>Classic stage tools</Button>;
  return <details className="action-menu"><summary className="button button-neutral"><span>Stage actions</span><span>•••</span></summary><div className="action-menu-popover">
    <button onClick={() => openDialog({ title: "Duplicate stage", description: "Create an independent copy with a new identifier.", content: <IdentityForm mode="duplicate" stage={stage}/> })}>Duplicate</button>
    <button onClick={() => openDialog({ title: "Rename stage", description: "References throughout the draft are updated by the server.", content: <IdentityForm mode="rename" stage={stage}/> })}>Rename and update references</button>
    <button onClick={() => openDialog({ title: "Move stage package", description: "Reorganize files without changing the identifier.", content: <MoveStageForm stage={stage}/> })}>Move package</button>
    <button onClick={() => void exportStage()}>Export package</button>
    <button onClick={() => openDialog({ title: "Manage collaborators", description: "Share this server draft with another operator.", content: <CollaboratorForm/> })}>Manage collaborators</button>
    <hr/>
    <button onClick={() => openDialog({ title: stage.archived ? "Restore stage" : "Archive stage", content: <ConfirmStageAction stage={stage} action={stage.archived ? "restore" : "archive"}/> })}>{stage.archived ? "Restore stage" : "Archive stage"}</button>
    <button className="danger" onClick={() => openDialog({ title: `Delete ${stage.name}`, description: "Review this destructive draft change before continuing.", content: <ConfirmStageAction stage={stage} action="delete"/>, width: "compact" })}>Delete from draft</button>
  </div></details>;
}
