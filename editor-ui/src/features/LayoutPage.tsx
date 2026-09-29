import { useEffect, useMemo, useRef, useState } from "react";
import { Button, EmptyState, Field } from "../components/ui";
import { Icon } from "../components/Icon";
import { expandGuideTemplate } from "../lib/guide";
import { stageDependsOn } from "../lib/model";
import { playerPreviewRequirementSummary, simulatedOwnedStageIds, stagePrerequisitesSatisfied } from "../lib/player-preview";
import type { PlayerPreviewScenario } from "../lib/player-preview";
import { booleanValue, numberValue, readTomlValue, removeTomlValue, stringValue, upsertToml } from "../lib/toml";
import { useEditor } from "../store/EditorContext";
import type { StagePackage, StageTab } from "../types";
import { ConfirmStageAction, IdentityForm } from "./stages/StageDialogs";

interface Point { x: number; y: number }
interface Camera { x: number; y: number; width: number; height: number }
const NODE_WIDTH = 188;
const NODE_HEIGHT = 58;
const WORLD_WIDTH = 1800;
const WORLD_HEIGHT = 1100;

function PreviewInspector({ stage, stages, scenario, ownedStageIds, visibleStageIds, content, configContent, helpOpen, onClose, onHelp, onEdit }: {
  stage: StagePackage | undefined;
  stages: StagePackage[];
  scenario: PlayerPreviewScenario;
  ownedStageIds: ReadonlySet<string>;
  visibleStageIds: ReadonlySet<string>;
  content: string;
  configContent: string;
  helpOpen: boolean;
  onClose: () => void;
  onHelp: () => void;
  onEdit: (tab: StageTab) => void;
}) {
  if (!stage) return <aside className="preview-inspector" aria-label="Player preview inspector"><div className="preview-inspector-empty"><strong>Select a stage</strong><p>Choose a visible node to inspect its player view.</p></div></aside>;
  const field = (path: string) => stringValue(readTomlValue(content, path));
  const globalGuideEnabled = booleanValue(readTomlValue(configContent, "general.enable_stage_guide") || "true");
  const guideEnabled = globalGuideEnabled && booleanValue(readTomlValue(content, "guide.enabled") || "true");
  const simulatedOwned = ownedStageIds.has(stage.id);
  const prerequisitesMet = stagePrerequisitesSatisfied(stage, ownedStageIds);
  const requirements = playerPreviewRequirementSummary(stages, stage, visibleStageIds);
  const status = simulatedOwned ? "Owned (simulated)"
    : prerequisitesMet ? "Prerequisites met (simulated)" : "Required stages missing (simulated)";
  const otherChecks = "Trigger, cost, cooldown, and world checks are not evaluated in this preview.";
  const values = {
    stage_name: stage.name,
    stage_requirements: requirements,
    remaining_requirements: simulatedOwned
      ? "Preview only: this stage is marked as owned."
      : prerequisitesMet
        ? `Preview only: required stages are met. ${otherChecks}`
        : `Preview only: required stages are not met. ${otherChecks}`,
    stage_progress: "Preview only: trigger progress is not evaluated."
  };
  const guide = ["guide.how_to_unlock", "guide.next_steps", "guide.where_to_find"]
    .map(path => ({ path, text: expandGuideTemplate(field(path), values).text }))
    .filter(entry => entry.text.trim());
  return <aside className="preview-inspector" aria-label="Player preview inspector">
    <header className="preview-inspector-header">
        <div><span className="preview-inspector-icon" role="img" aria-label={`Stage icon ${stage.icon}`} title={`Stage icon ${stage.icon}`}>◆</span><div><strong>{stage.name}</strong><small title={otherChecks}>{status}</small></div></div>
      <div className="preview-inspector-actions">
        {guideEnabled && !simulatedOwned ? <Button tone="quiet" aria-label={`How to unlock ${stage.name}`} help={`How to unlock ${stage.name}`} onClick={onHelp}>?</Button> : null}
        <Button tone="quiet" aria-label="Close stage preview" help="Close stage preview" onClick={onClose}>×</Button>
      </div>
    </header>
    <div className="preview-inspector-body">
      {stage.description ? <p className="preview-description">{stage.description}</p> : null}
      <section><h3>Required stages</h3><p>{requirements}</p><p className="preview-requirement-status" title={otherChecks}>{status}. {otherChecks}</p><Button tone="quiet" help="Open this stage's required stages settings." onClick={() => onEdit("essentials")}>Edit requirements</Button></section>
      {guideEnabled && helpOpen && !simulatedOwned ? <section className="preview-guide-help"><h3>How to unlock {stage.name}</h3>{guide.length ? guide.map(entry => <p key={entry.path}>{entry.text}</p>) : <p>No unlock instructions have been added yet.</p>}<Button tone="quiet" help="Open this stage's What to do next fields." onClick={() => onEdit("essentials")}>Edit guide</Button></section> : null}
      {guideEnabled && !simulatedOwned && !helpOpen ? <p className="preview-help-hint">Select the gold ? to show unlock instructions.</p> : null}
    </div>
    <footer><div className="preview-inspector-links"><Button tone="quiet" onClick={() => onEdit("essentials")} help="Open names, icons, visibility, map, and guide fields.">Setup</Button><Button tone="quiet" onClick={() => onEdit("rules")} help="Open this stage's rules and conditions.">Rules</Button><Button tone="quiet" onClick={() => onEdit("progression")} help="Open ways players obtain or lose this stage.">Progression</Button><Button tone="quiet" onClick={() => onEdit("effects")} help="Open rewards and effects for this stage.">Rewards</Button><Button tone="primary" onClick={() => onEdit("essentials")} help="Open this stage in the editor. The preview remains read only.">Edit stage fields</Button></div></footer>
  </aside>;
}

function automaticPositions(stages: StagePackage[]): Record<string, Point> {
  const byId = new Map(stages.map(stage => [stage.id, stage]));
  const depthMemo = new Map<string, number>();
  const depth = (stage: StagePackage, visiting = new Set<string>()): number => {
    if (depthMemo.has(stage.id)) return depthMemo.get(stage.id)!;
    if (visiting.has(stage.id)) return 0;
    const next = new Set(visiting).add(stage.id);
    const parents = stage.dependencies.map(id => byId.get(id)).filter((value): value is StagePackage => Boolean(value));
    const value = parents.length ? Math.max(...parents.map(parent => depth(parent, next))) + 1 : 0;
    depthMemo.set(stage.id, value);
    return value;
  };
  const levels = new Map<number, StagePackage[]>();
  for (const stage of stages) { const value = depth(stage); levels.set(value, [...(levels.get(value) || []), stage]); }
  const maxDepth = Math.max(0, ...levels.keys());
  const output: Record<string, Point> = {};
  for (const [level, entries] of levels) {
    entries.sort((left, right) => left.category.localeCompare(right.category) || left.name.localeCompare(right.name));
    const gap = WORLD_WIDTH / (entries.length + 1);
    entries.forEach((stage, index) => { output[stage.id] = { x: gap * (index + 1) - NODE_WIDTH / 2, y: WORLD_HEIGHT - 130 - (level * Math.max(150, 780 / Math.max(1, maxDepth))) }; });
  }
  return output;
}

export function LayoutPage() {
  const { boot, stages: everyStage, mutateFile, mutateFiles, notify, selectStage, setStageTab, setPage, openDialog } = useEditor();
  const stages = useMemo(() => everyStage.filter(stage => !stage.archived), [everyStage]);
  const automatic = useMemo(() => automaticPositions(stages), [stages]);
  const [positions, setPositions] = useState<Record<string, Point>>({});
  const [camera, setCamera] = useState<Camera>({ x: 0, y: 0, width: WORLD_WIDTH, height: WORLD_HEIGHT });
  const [category, setCategory] = useState("");
  const [search, setSearch] = useState("");
  const [connectSource, setConnectSource] = useState("");
  const [connecting, setConnecting] = useState(false);
  const [drag, setDrag] = useState<{ id: string; offsetX: number; offsetY: number } | null>(null);
  const [pan, setPan] = useState<{ startX: number; startY: number; cameraX: number; cameraY: number } | null>(null);
  const [contextMenu, setContextMenu] = useState<{ stage: StagePackage; x: number; y: number } | null>(null);
  const [playerPreview, setPlayerPreview] = useState(false);
  const [previewScenario, setPreviewScenario] = useState<PlayerPreviewScenario>("ready");
  const [previewSelectedId, setPreviewSelectedId] = useState("");
  const [previewHelpOpen, setPreviewHelpOpen] = useState(false);
  const svg = useRef<SVGSVGElement>(null);
  const contextMenuRef = useRef<HTMLDivElement>(null);
  const contextTrigger = useRef<HTMLElement | null>(null);
  useEffect(() => {
    const next: Record<string, Point> = {};
    for (const stage of stages) {
      const text = boot?.draft.files[stage.stagePath] || "";
      const rawX = readTomlValue(text, "display.x");
      const rawY = readTomlValue(text, "display.y");
      next[stage.id] = rawX && rawY ? { x: numberValue(rawX), y: numberValue(rawY) } : automatic[stage.id];
    }
    setPositions(next);
  }, [automatic, boot?.draft.files, stages]);
  const previewDefinition = (stage: StagePackage) => {
    const text = boot?.draft.files[stage.stagePath] || "";
    return {
      hidden: stage.hidden || booleanValue(readTomlValue(text, "stage.hidden")),
      reveal: stringValue(readTomlValue(text, "display.reveal")) || "dependencies"
    };
  };
  const previewOwnedIds = useMemo(() => simulatedOwnedStageIds(stages, previewScenario), [stages, previewScenario]);
  const previewVisible = (stage: StagePackage) => {
    if (!playerPreview) return true;
    const definition = previewDefinition(stage);
    if (definition.hidden) return false;
    if (definition.reveal === "unlocked") return previewOwnedIds.has(stage.id);
    if (definition.reveal === "dependencies") return stagePrerequisitesSatisfied(stage, previewOwnedIds);
    return true;
  };
  const playerVisibleStages = useMemo(() => stages.filter(previewVisible),
    [boot?.draft.files, playerPreview, previewOwnedIds, stages]);
  const categories = [...new Set(playerVisibleStages.map(stage => stage.category).filter(Boolean))].sort();
  const previewState = (stage: StagePackage) => {
    if (!playerPreview) return "draft";
    if (previewOwnedIds.has(stage.id)) return "owned";
    return stagePrerequisitesSatisfied(stage, previewOwnedIds) ? "ready" : "locked";
  };
  const visible = useMemo(() => playerVisibleStages.filter(stage => (!category || stage.category === category)
    && (!search || `${stage.name} ${stage.id}`.toLowerCase().includes(search.toLowerCase()))),
  [category, playerVisibleStages, search]);
  const playerVisibleStageIds = useMemo(() => new Set(playerVisibleStages.map(stage => stage.id)), [playerVisibleStages]);
  const visibleStageIds = useMemo(() => new Set(visible.map(stage => stage.id)), [visible]);
  useEffect(() => {
    if (!playerPreview) {
      setPreviewSelectedId("");
      setPreviewHelpOpen(false);
      return;
    }
    if (!visible.some(stage => stage.id === previewSelectedId)) {
      setPreviewSelectedId(visible[0]?.id || "");
      setPreviewHelpOpen(false);
    }
  }, [playerPreview, previewSelectedId, visible]);
  useEffect(() => {
    setPreviewHelpOpen(false);
  }, [boot?.draft.files, previewScenario, previewSelectedId]);
  const previewSelected = visible.find(stage => stage.id === previewSelectedId);
  const visibleIds = new Set(visible.map(stage => stage.id));
  const edges = visible.flatMap(stage => stage.dependencies.filter(parent => visibleIds.has(parent)).map(parent => ({ parent, child: stage.id })));
  const eventPoint = (event: React.PointerEvent | React.WheelEvent): Point => {
    const rect = svg.current?.getBoundingClientRect();
    if (!rect) return { x: 0, y: 0 };
    return { x: camera.x + (event.clientX - rect.left) * camera.width / rect.width, y: camera.y + (event.clientY - rect.top) * camera.height / rect.height };
  };
  const fit = () => {
    const points = visible.map(stage => positions[stage.id]).filter(Boolean);
    if (!points.length) return;
    const minX = Math.min(...points.map(point => point.x)) - 140;
    const minY = Math.min(...points.map(point => point.y)) - 140;
    const maxX = Math.max(...points.map(point => point.x + NODE_WIDTH)) + 140;
    const maxY = Math.max(...points.map(point => point.y + NODE_HEIGHT)) + 140;
    setCamera({ x: minX, y: minY, width: Math.max(500, maxX - minX), height: Math.max(350, maxY - minY) });
  };
  const zoom = (factor: number, focus?: Point) => setCamera(current => {
    const nextWidth = Math.min(5000, Math.max(380, current.width * factor));
    const nextHeight = nextWidth * 0.61;
    const point = focus || { x: current.x + current.width / 2, y: current.y + current.height / 2 };
    const ratioX = (point.x - current.x) / current.width;
    const ratioY = (point.y - current.y) / current.height;
    return { x: point.x - nextWidth * ratioX, y: point.y - nextHeight * ratioY, width: nextWidth, height: nextHeight };
  });
  const savePosition = async (stage: StagePackage, point: Point) => {
    let text = boot?.draft.files[stage.stagePath] || "";
    text = upsertToml(text, "display.x", Math.round(point.x));
    text = upsertToml(text, "display.y", Math.round(point.y));
    await mutateFile(stage.stagePath, text, `${stage.name} map position saved`);
  };
  const activateNode = async (stage: StagePackage) => {
    if (playerPreview) {
      setPreviewSelectedId(stage.id);
      setPreviewHelpOpen(false);
      return;
    }
    if (!connecting) { selectStage(stage.key); return; }
    if (!connectSource) { setConnectSource(stage.id); notify("info", "Choose the stage this path should lead into", `${stage.name} is the prerequisite.`); return; }
    if (connectSource === stage.id) { setConnectSource(""); return; }
    const parent = stages.find(value => value.id === connectSource);
    if (!parent) return;
    if (stageDependsOn(stages, parent.id, stage.id)) { notify("danger", "That connection would create a loop", "Choose a different prerequisite or destination."); return; }
    if (!stage.dependencies.includes(parent.id)) {
      const text = upsertToml(boot?.draft.files[stage.stagePath] || "", stage.legacy ? "stage.dependency" : "stage.dependencies", [...stage.dependencies, parent.id]);
      await mutateFile(stage.stagePath, text, `${parent.name} now leads into ${stage.name}`);
    }
    setConnectSource("");
    setConnecting(false);
  };
  const removeEdge = async (parent: string, child: string) => {
    const stage = stages.find(value => value.id === child);
    if (!stage) return;
    const dependencies = stage.dependencies.filter(value => value !== parent);
    const text = upsertToml(boot?.draft.files[stage.stagePath] || "", stage.legacy ? "stage.dependency" : "stage.dependencies", dependencies);
    await mutateFile(stage.stagePath, text, "Progression branch removed");
  };
  const clearPositions = async () => {
    await mutateFiles(stages.map(stage => { let text = boot?.draft.files[stage.stagePath] || ""; text = removeTomlValue(removeTomlValue(text, "display.x"), "display.y"); return { path: stage.stagePath, content: text }; }), "Automatic layout restored");
  };
  const openContextMenu = (stage: StagePackage, x: number, y: number, trigger?: Element) => {
    contextTrigger.current = trigger as HTMLElement | null;
    setContextMenu({ stage, x: Math.max(8, Math.min(x, window.innerWidth - 228)), y: Math.max(8, Math.min(y, window.innerHeight - 250)) });
  };
  useEffect(() => {
    if (!contextMenu) return;
    const close = (event: PointerEvent) => {
      if (!contextMenuRef.current?.contains(event.target as Node)) {
        setContextMenu(null);
        contextTrigger.current?.focus();
      }
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setContextMenu(null);
        contextTrigger.current?.focus();
      }
    };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", escape);
    contextMenuRef.current?.querySelector<HTMLButtonElement>("button")?.focus();
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("keydown", escape);
    };
  }, [contextMenu]);
  const closeContextMenu = () => {
    setContextMenu(null);
    contextTrigger.current?.focus();
  };
  const graph = visible.length ? <svg ref={svg} className={`stage-graph ${connecting ? "connecting" : ""}`} viewBox={`${camera.x} ${camera.y} ${camera.width} ${camera.height}`} onWheel={event => { event.preventDefault(); zoom(event.deltaY > 0 ? 1.12 : 0.88, eventPoint(event)); }} onPointerDown={event => { const target = event.target as SVGElement; if (event.target !== event.currentTarget && !target.classList.contains("graph-grid")) return; event.currentTarget.setPointerCapture(event.pointerId); setPan({ startX: event.clientX, startY: event.clientY, cameraX: camera.x, cameraY: camera.y }); }} onPointerMove={event => {
      if (drag) { const point = eventPoint(event); setPositions(current => ({ ...current, [drag.id]: { x: point.x - drag.offsetX, y: point.y - drag.offsetY } })); }
      else if (pan && svg.current) { const rect = svg.current.getBoundingClientRect(); setCamera(current => ({ ...current, x: pan.cameraX - (event.clientX - pan.startX) * current.width / rect.width, y: pan.cameraY - (event.clientY - pan.startY) * current.height / rect.height })); }
    }} onPointerUp={event => { if (drag) { const stage = stages.find(value => value.id === drag.id); const point = positions[drag.id]; if (stage && point) void savePosition(stage, point); } setDrag(null); setPan(null); try { event.currentTarget.releasePointerCapture(event.pointerId); } catch { /* pointer capture can already be released */ } }}>
      <defs><pattern id="graph-grid" width="40" height="40" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.6"/></pattern></defs><rect className="graph-grid" x={-5000} y={-5000} width={10000} height={10000}/>
      <g className="graph-edges">{edges.map(edge => { const start = positions[edge.parent]; const end = positions[edge.child]; if (!start || !end) return null; const sx = start.x + NODE_WIDTH / 2; const sy = start.y; const ex = end.x + NODE_WIDTH / 2; const ey = end.y + NODE_HEIGHT; const middle = (sy + ey) / 2; const d = `M ${sx} ${sy} C ${sx} ${middle}, ${ex} ${middle}, ${ex} ${ey}`; const remove = () => void removeEdge(edge.parent, edge.child); return <g key={`${edge.parent}:${edge.child}`}><path d={d}/><path className="graph-edge-hit" d={d} role="button" tabIndex={0} aria-label={`Remove progression branch from ${edge.parent} to ${edge.child}`} onClick={event => { event.stopPropagation(); remove(); }} onKeyDown={event => { if (["Enter", " ", "Delete", "Backspace"].includes(event.key)) { event.preventDefault(); event.stopPropagation(); remove(); } }}/></g>; })}</g>
      <g className="graph-nodes">{visible.map(stage => { const point = positions[stage.id] || automatic[stage.id] || { x: 0, y: 0 }; const source = connectSource === stage.id; const state = previewState(stage); const text = boot?.draft.files[stage.stagePath] || ""; const guide = [stringValue(readTomlValue(text, "guide.how_to_unlock")), stringValue(readTomlValue(text, "guide.next_steps")), stringValue(readTomlValue(text, "guide.where_to_find"))].filter(Boolean).join(" "); const dependencyIds = stage.dependencies.filter(parent => !playerPreview || visibleIds.has(parent)); const requirements = dependencyIds.length ? ` Requires ${dependencyIds.join(", ")}.` : ""; return <g key={stage.id} className={`graph-node ${source ? "connection-source" : ""} ${playerPreview ? `preview-node preview-${state}` : ""}`} transform={`translate(${point.x} ${point.y})`} role="button" tabIndex={0} aria-label={`${stage.name}, ${stage.category}, ${state}`} onPointerDown={event => { if (playerPreview || event.button !== 0 || connecting) return; event.stopPropagation(); const world = eventPoint(event); svg.current?.setPointerCapture(event.pointerId); setDrag({ id: stage.id, offsetX: world.x - point.x, offsetY: world.y - point.y }); }} onClick={event => { event.stopPropagation(); if (!drag) void activateNode(stage); }} onContextMenu={event => { event.preventDefault(); event.stopPropagation(); if (!playerPreview) openContextMenu(stage, event.clientX, event.clientY, event.currentTarget); }} onKeyDown={event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); void activateNode(stage); } else if (!playerPreview && (event.key === "ContextMenu" || (event.key === "F10" && event.shiftKey))) { event.preventDefault(); const rect = event.currentTarget.getBoundingClientRect(); openContextMenu(stage, rect.left + rect.width / 2, rect.bottom, event.currentTarget); } }}><title>{stage.description}{requirements}{guide ? ` ${guide}` : ""}</title><rect width={NODE_WIDTH} height={NODE_HEIGHT} rx="5"/><path className="node-accent" d={`M 0 5 Q 0 0 5 0 H ${NODE_WIDTH - 5}`}/><path className="node-diamond" d="M 18 29 27 20 36 29Z"/><text className="node-title" x="47" y="25">{stage.name.slice(0, 23)}</text><text className="node-subtitle" x="47" y="43">{stage.category.slice(0, 27)}</text><text className="node-status" x="47" y="53">{state === "owned" ? "Owned (simulated)" : state === "ready" ? "Prerequisites met" : state === "locked" ? "Required stages missing" : "Draft"}</text>{dependencyIds.length ? <text className="node-count" x={NODE_WIDTH - 17} y="34">{dependencyIds.length}</text> : null}</g>; })}</g>
    </svg> : <EmptyState icon="layout" title="No stages match this view" description="Clear the category and search filters to restore the full player map."/>;
  return <div className={`layout-page${playerPreview ? " player-preview-page" : ""}`}><header className="page-heading"><div><h1>{playerPreview ? "Player preview" : "Player UI"}</h1><p>{playerPreview ? "Preview the connected stages screen without granting stages or applying the draft. Click a node to edit its fields." : "Drag stages to place them. Drag empty space to pan. Scroll to zoom. Draw or remove dependency lines directly."}</p></div><Button help={playerPreview ? "Return to the author map. Preview changes never grant stages or apply the draft." : "Preview the map using locked, requirements met, or owned simulated player states."} tone={playerPreview ? "primary" : "neutral"} onClick={() => { setPlayerPreview(value => !value); setConnecting(false); setConnectSource(""); }}>{playerPreview ? "Edit author map" : "Player preview"}</Button></header>
    <section className="layout-toolbar"><Field label="Category" help="Show one stage category at a time, or choose All categories to see the full map. This does not change the saved stage category."><select value={category} onChange={event => setCategory(event.target.value)}><option value="">All categories</option>{categories.map(value => <option key={value}>{value}</option>)}</select></Field><Field label="Find a stage" help="Filter the map by stage name or identifier. Clear this search to show every stage again."><div className="input-icon"><Icon name="search" size={16}/><input value={search} onChange={event => setSearch(event.target.value)}/></div></Field>{playerPreview ? <Field label="Simulated player" help="Choose a simulated ownership state. Requirements met marks prerequisite stages as owned for this preview only. It does not evaluate triggers, costs, cooldowns, or world checks, and never changes player data."><select value={previewScenario} onChange={event => setPreviewScenario(event.target.value as PlayerPreviewScenario)}><option value="locked">Locked</option><option value="ready">Requirements met</option><option value="unlocked">Unlocked</option></select></Field> : null}<div className="zoom-control" title="Change the map scale. This only changes the preview camera."><Button tone="quiet" aria-label="Zoom out" help="Zoom out" onClick={() => zoom(1.2)}>−</Button><span>{Math.round(WORLD_WIDTH / camera.width * 100)}%</span><Button tone="quiet" aria-label="Zoom in" help="Zoom in" onClick={() => zoom(0.82)}>+</Button></div><Button help="Fit every visible stage into the map viewport." onClick={fit}>Fit graph</Button>{playerPreview ? null : <><Button help="Choose two stages to create a prerequisite connection." tone={connecting ? "primary" : "neutral"} icon="layout" onClick={() => { setConnecting(value => !value); setConnectSource(""); }}>{connecting ? "Cancel connection" : "Connect stages"}</Button><Button help="Clear manual positions and use the automatic progression layout." onClick={() => void clearPositions()}>Use automatic layout</Button></>}</section>
    {playerPreview ? <div className="preview-banner"><strong>Draft player preview.</strong><span>Hidden stages stay out of the map. Reveal rules and prerequisites use the selected simulation. Conditions that need a live world are not evaluated here.</span><div className="preview-state-list"><span className="preview-state active">{previewScenario === "locked" ? "No requirements met" : previewScenario === "ready" ? "Prerequisites met" : "Stage owned"}</span><span>Read only</span></div></div> : null}
    <div className={`connection-help ${connecting ? "active" : ""}`}>{connecting ? connectSource ? <><strong>Choose the destination stage.</strong><span>The selected stage will become its prerequisite.</span></> : <><strong>Choose the prerequisite stage.</strong><span>Then choose the stage it should lead into.</span></> : <><strong>{playerPreview ? "Simulated stages screen." : "Interactive player map."}</strong><span>{playerPreview ? "Click a stage node to inspect it, then choose Edit stage fields." : "Click a branch line to remove that prerequisite."}</span></>}</div>
    {playerPreview ? <div className="player-preview-shell">{graph}<PreviewInspector stage={previewSelected} stages={stages} scenario={previewScenario} ownedStageIds={previewOwnedIds} visibleStageIds={playerVisibleStageIds} content={previewSelected ? boot?.draft.files[previewSelected.stagePath] || "" : ""} configContent={boot?.draft.files["progressivestages.toml"] || ""} helpOpen={previewHelpOpen} onClose={() => { setPreviewSelectedId(""); setPreviewHelpOpen(false); }} onHelp={() => setPreviewHelpOpen(value => !value)} onEdit={tab => { if (!previewSelected) return; selectStage(previewSelected.key); setStageTab(tab); setPage("stages"); }}/></div> : graph}
    {contextMenu ? <div ref={contextMenuRef} className="graph-context-menu" role="menu" style={{ left: contextMenu.x, top: contextMenu.y }} onPointerDown={event => event.stopPropagation()}>
      <strong>{contextMenu.stage.name}</strong>
      <button role="menuitem" title="Open this stage in the full editor" onClick={() => { const stage = contextMenu.stage; closeContextMenu(); setPage("stages"); selectStage(stage.key); }}>Edit stage</button>
      <button role="menuitem" title="Use this stage as the prerequisite in a new connection" onClick={() => { const stage = contextMenu.stage; closeContextMenu(); setConnecting(true); setConnectSource(stage.id); notify("info", "Choose the destination stage", `${stage.name} is the prerequisite.`); }}>Connect stage</button>
      {!contextMenu.stage.legacy ? <button role="menuitem" title="Create a separate draft copy with a new identifier" onClick={() => { const stage = contextMenu.stage; closeContextMenu(); openDialog({ title: "Duplicate stage", description: "Create an independent copy with a new identifier.", content: <IdentityForm mode="duplicate" stage={stage}/> }); }}>Duplicate</button> : null}
      {!contextMenu.stage.legacy ? <button role="menuitem" title="Remove this stage from the draft after review" className="danger" onClick={() => { const stage = contextMenu.stage; closeContextMenu(); openDialog({ title: `Delete ${stage.name}`, description: "Review this destructive draft change before continuing.", content: <ConfirmStageAction stage={stage} action="delete"/>, width: "compact" }); }}>Delete</button> : null}
    </div> : null}
  </div>;
}
