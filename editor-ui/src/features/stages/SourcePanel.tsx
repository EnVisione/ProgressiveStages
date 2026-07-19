import { useEffect, useState } from "react";
import { Badge, Button, Section } from "../../components/ui";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";

export function SourcePanel({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, notify } = useEditor();
  const paths = stage.legacy ? [stage.stagePath] : [stage.stagePath, stage.rulesPath, stage.progressionPath];
  const [path, setPath] = useState(paths[0]);
  const live = boot?.draft.files[path] || "";
  const [source, setSource] = useState(live);
  const dirty = source !== live;
  useEffect(() => setSource(live), [live, path]);
  const switchPath = (next: string) => {
    if (dirty) {
      notify("warning", "Save or discard the current source first", "This prevents an unfinished edit from being lost.");
      return;
    }
    setPath(next);
  };
  return <Section title="TOML source" description="Optional exact control for experienced pack authors. Every visual editor feature writes these same files." action={<Badge tone={dirty ? "warning" : "success"}>{dirty ? "Unsaved source" : "Draft synchronized"}</Badge>}>
    <div className="source-tabs">{paths.map(value => <button key={value} className={path === value ? "active" : ""} onClick={() => switchPath(value)}>{value.split("/").pop()}</button>)}</div>
    <div className="source-editor-shell"><header><code>{path}</code><span>{source.split(/\r?\n/).length} lines</span></header><textarea spellCheck={false} aria-label={`TOML source for ${path}`} value={source} onChange={event => setSource(event.target.value)}/></div>
    <div className="source-footer"><p>Unknown sections, comments, extensions, and KubeJS values remain intact unless you edit or replace them here.</p><div><Button tone="quiet" disabled={!dirty} onClick={() => setSource(live)}>Discard</Button><Button tone="primary" disabled={!dirty} onClick={() => void mutateFile(path, source, `${path.split("/").pop()} saved`)}>Save source to draft</Button></div></div>
  </Section>;
}
