import { useMemo, useState } from "react";
import { stageIdentity, slug } from "../../lib/model";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";
import { Button, Field } from "../../components/ui";

export function IdentityForm({ mode, stage }:
  { mode: "create" | "duplicate" | "rename"; stage?: StagePackage | null }) {
  const { runDraftAction, closeDialog, selectStage, notify } = useEditor();
  const [name, setName] = useState(mode === "duplicate" ? `${stage?.name || "Stage"} Copy` : mode === "rename" ? stage?.name || "" : "");
  const [namespace, setNamespace] = useState(stage?.id.split(":")[0] || "pack");
  const [submitting, setSubmitting] = useState(false);
  const identity = useMemo(() => stageIdentity(name || "new stage", namespace), [name, namespace]);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!identity.path) return;
    setSubmitting(true);
    try {
      if (mode === "create") {
        await runDraftAction({ action: "scaffold", stage: identity.id, displayName: identity.name, icon: "minecraft:stone" });
      } else if (mode === "duplicate" && stage) {
        await runDraftAction({ action: "duplicate_stage", source: stage.folder, stage: identity.id });
      } else if (stage) {
        await runDraftAction({ action: "rename_stage", folder: stage.folder, stage: identity.id });
      }
      closeDialog();
      selectStage(`stages/${identity.id.replace(/[:/]/g, "_")}/`);
      notify("success", mode === "create" ? `${identity.name} is ready to build`
        : mode === "duplicate" ? "The stage was duplicated" : "The stage and its references were renamed",
      identity.id);
    } finally {
      setSubmitting(false);
    }
  };
  return <form className="dialog-form" onSubmit={submit}>
    <div className="form-grid">
      <Field label="Player facing name" help="Use words and spaces. The editor creates the safe identifier.">
        <input value={name} onChange={event => setName(event.target.value)} autoFocus required placeholder="Wizard"/>
      </Field>
      <Field label="Namespace" help="Keep related paths together, such as wizard:wizard and wizard:warlock.">
        <input value={namespace} onChange={event => setNamespace(event.target.value)} pattern="[a-z0-9_.-]+" required/>
      </Field>
    </div>
    <div className="identity-preview"><span>Stage identifier</span><strong>{identity.id}</strong></div>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary" disabled={!identity.path || submitting}>{submitting ? "Saving" : mode === "create" ? "Create stage" : mode === "duplicate" ? "Duplicate stage" : "Rename stage"}</Button></footer>
  </form>;
}

export function MoveStageForm({ stage }: { stage: StagePackage }) {
  const { runDraftAction, closeDialog, selectStage } = useEditor();
  const [destination, setDestination] = useState(`categories/${slug(stage.name)}`);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    await runDraftAction({ action: "move_stage", folder: stage.folder, destination }, "The stage package was reorganized");
    closeDialog();
    selectStage(`stages/${destination.replace(/^stages\//, "").replace(/\/$/, "")}/`);
  };
  return <form className="dialog-form" onSubmit={submit}>
    <Field label="Folder below stages" help="This only changes file organization. The stage identifier stays the same." wide>
      <input value={destination} onChange={event => setDestination(event.target.value)} autoFocus required/>
    </Field>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Move package</Button></footer>
  </form>;
}

export function ImportStageForm() {
  const { runDraftAction, closeDialog } = useEditor();
  const [value, setValue] = useState("");
  const [destination, setDestination] = useState("imported_stage");
  const [error, setError] = useState("");
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const parsed = JSON.parse(value) as { files?: Record<string, string> } | Record<string, string>;
      const files = "files" in parsed && parsed.files ? parsed.files : parsed;
      await runDraftAction({ action: "import_stage", destination, files }, "The stage package was imported");
      closeDialog();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The package is not valid JSON.");
    }
  };
  return <form className="dialog-form" onSubmit={submit}>
    <Field label="Exported stage package" help="Paste the complete ProgressiveStages export." wide>
      <textarea rows={10} value={value} onChange={event => setValue(event.target.value)} autoFocus required/>
    </Field>
    <Field label="Folder below stages" wide><input value={destination} onChange={event => setDestination(event.target.value)} required/></Field>
    {error ? <div className="inline-error">{error}</div> : null}
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Import package</Button></footer>
  </form>;
}

export function ConfirmStageAction({ stage, action }:
  { stage: StagePackage; action: "delete" | "archive" | "restore" }) {
  const { runDraftAction, closeDialog, notify } = useEditor();
  const execute = async () => {
    if (action === "delete") await runDraftAction({ action: "delete_stage", folder: stage.folder });
    else await runDraftAction({ action: action === "restore" ? "restore_stage" : "archive_stage", folder: stage.folder });
    closeDialog();
    notify(action === "delete" ? "warning" : "success",
      action === "delete" ? "The stage was removed from the draft" : action === "restore" ? "The stage was restored" : "The stage was archived");
  };
  return <div className="confirmation">
    <div className={`confirmation-mark ${action === "delete" ? "danger" : "warning"}`}>{action === "delete" ? "×" : "◆"}</div>
    <p>{action === "delete" ? `This removes all files for ${stage.name} from the draft. Undo remains available until apply.`
      : action === "restore" ? `${stage.name} will return to the active stage library.`
      : `${stage.name} will stop compiling and move into the editor archive.`}</p>
    <footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Cancel</Button><Button tone={action === "delete" ? "danger" : "primary"} onClick={execute}>{action === "delete" ? "Delete from draft" : action === "restore" ? "Restore stage" : "Archive stage"}</Button></footer>
  </div>;
}

export function CollaboratorForm() {
  const { boot, runDraftAction, closeDialog } = useEditor();
  const [player, setPlayer] = useState("");
  const [mode, setMode] = useState<"add" | "remove">("add");
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    await runDraftAction({ action: mode === "add" ? "collaborator_add" : "collaborator_remove", player },
      mode === "add" ? "The collaborator was added" : "The collaborator was removed");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={submit}>
    <div className="form-grid">
      <Field label="Action"><select value={mode} onChange={event => setMode(event.target.value as "add" | "remove")}><option value="add">Add collaborator</option><option value="remove">Remove collaborator</option></select></Field>
      <Field label="Player UUID" help="Use the UUID of an operator allowed to share this draft."><input value={player} onChange={event => setPlayer(event.target.value)} required autoFocus/></Field>
    </div>
    <div className="collaborator-list"><span>Current collaborators</span><strong>{boot?.draft.collaborators.length ? boot.draft.collaborators.join(", ") : "Only the draft owner"}</strong></div>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Save access</Button></footer>
  </form>;
}
