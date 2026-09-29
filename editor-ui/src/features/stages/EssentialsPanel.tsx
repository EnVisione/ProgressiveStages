import { OwnershipFeedback } from "../../components/StageCapabilityFeedback";
import { FieldDiagnostics } from "../../components/ValidationMessages";
import { useEffect, useMemo, useRef, useState } from "react";
import { CatalogPicker } from "../../components/CatalogPicker";
import { Badge, Button, Field, Section, Toggle } from "../../components/ui";
import { GUIDE_BYTE_LIMIT, GUIDE_CODE_POINT_LIMIT, GUIDE_PLACEHOLDERS, GUIDE_PLACEHOLDER_VALUES, expandGuideTemplate, guideTextMetrics, hasInlineStageRequirements, truncateGuideText } from "../../lib/guide";
import { dependenciesFor, dependencySummary, stageDependsOn } from "../../lib/model";
import { parseOwnership, writeOwnership } from "../../lib/integrations";
import { booleanValue, lineValues, numberValue, parseSimpleArray, readTomlValue, removeTomlValue, stringValue, upsertToml } from "../../lib/toml";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";

const guidePreviewValues = (stageName: string) => ({ ...GUIDE_PLACEHOLDER_VALUES, stage_name: stageName });

function TextDraftField({ label, value, help, wide, multiline, type = "text", maxLength, onSave }:
  { label: string; value: string | number; help?: string; wide?: boolean; multiline?: boolean; type?: string; maxLength?: number; onSave: (value: string) => Promise<void> }) {
  const [draft, setDraft] = useState(String(value));
  const [saving, setSaving] = useState(false);
  useEffect(() => setDraft(String(value)), [value]);
  const save = async () => {
    if (draft === String(value)) return;
    setSaving(true);
    try { await onSave(draft); } finally { setSaving(false); }
  };
  return <Field label={label} help={saving ? "Saving to the server draft" : help} wide={wide}>
    {multiline ? <textarea rows={3} maxLength={maxLength} value={draft} onChange={event => setDraft(event.target.value)} onBlur={() => void save()}/>
      : <input type={type} maxLength={maxLength} value={draft} onChange={event => setDraft(event.target.value)} onBlur={() => void save()}/>}
  </Field>;
}

function GuideEditor({ content, save }: { stage: StagePackage; content: string; save: (path: string, value: unknown) => Promise<void> }) {
  const text = (path: string) => stringValue(readTomlValue(content, path));
  const guideEnabled = booleanValue(readTomlValue(content, "guide.enabled") || "true");
  const stageName = text("stage.display_name") || "This stage";
  const previewValues = guidePreviewValues(stageName);
  const field = (path: string, label: string, help: string) => <GuideTextField
    label={label}
    value={text(path)}
    help={help}
    stageName={stageName}
    onSave={value => save(path, value)}
  />;
  const previews = ["guide.how_to_unlock", "guide.next_steps", "guide.where_to_find"]
    .map(path => expandGuideTemplate(text(path), previewValues))
    .filter(value => value.text.trim());
  return <Section title="What to do next" description="Write the short instructions players see in the guide. Text is shown as written and never runs commands or grants stages.">
    <div className="form-grid">
      <Toggle label="Show this stage's player guide" help="When off, this stage has no in-game ? button and is skipped by the What to do next keybind. The global guide setting in Setup still controls every stage." checked={guideEnabled} onChange={value => void save("guide.enabled", value)}/>
      {field("guide.how_to_unlock", "How to unlock", "Explain how the player can earn this stage. Put the full unlock summary placeholder on its own line; it may include complete sentences about costs and group slots.")}
      {field("guide.next_steps", "Next steps", "Tell the player what to do after this stage.")}
      {field("guide.where_to_find", "Where to find it", "Point to a place, menu, quest, or nearby landmark.")}
      <Field label="Guide recommendation" help="Automatic suggests a visible stage when its prerequisites are ready and it has a trigger or guide text. Always include keeps it eligible. Never include removes it.">
        <select value={text("guide.recommendation") || "auto"} onChange={event => void save("guide.recommendation", event.target.value)}>
          <option value="auto">Automatic</option>
          <option value="include">Always include</option>
          <option value="exclude">Never include</option>
        </select>
      </Field>
    </div>
    <div className="guide-preview" aria-label="Example guide preview">
      <strong>Example guide preview</strong>
      <small>Placeholder values are examples. The game uses the server's current requirements.</small>
      {previews.length ? previews.map((value, index) => <p key={`${index}:${value.text}`}>{value.text}</p>) : <p className="muted">Add guide text to preview it here.</p>}
      {previews.some(value => value.unknown.length) ? <small className="setting-error">Unknown placeholders stay visible until corrected.</small> : null}
    </div>
  </Section>;
}

function GuideTextField({ label, value, help, stageName, onSave }: { label: string; value: string; help: string; stageName: string; onSave: (value: string) => Promise<void> }) {
  const [draft, setDraft] = useState(value);
  const [chooserOpen, setChooserOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const area = useRef<HTMLTextAreaElement>(null);
  useEffect(() => setDraft(value), [value]);
  const saveDraft = async () => {
    if (draft === value) return;
    if (!guideTextMetrics(draft).valid) return;
    setSaving(true);
    try { await onSave(draft); } finally { setSaving(false); }
  };
  const insert = (token: string) => {
    const element = area.current;
    const start = element?.selectionStart ?? draft.length;
    const end = element?.selectionEnd ?? draft.length;
    const next = `${draft.slice(0, start)}${token}${draft.slice(end)}`;
    setDraft(truncateGuideText(next));
    setChooserOpen(false);
    requestAnimationFrame(() => { element?.focus(); });
  };
  const preview = expandGuideTemplate(draft, guidePreviewValues(stageName));
  const metrics = guideTextMetrics(draft);
  const limitMessage = `${metrics.codePoints}/${GUIDE_CODE_POINT_LIMIT} characters, ${metrics.bytes}/${GUIDE_BYTE_LIMIT} bytes.`;
  return <Field label={label} help={`${help} ${limitMessage}`} wide>
    <textarea ref={element => { area.current = element; }} rows={3} value={draft} onChange={event => setDraft(event.target.value)} onBlur={() => void saveDraft()}/>
    <div className="guide-placeholder-tools">
      <Button type="button" tone="quiet" onClick={() => setChooserOpen(open => !open)} help="Insert a fixed read only value into this guide field.">Insert placeholder</Button>
      {chooserOpen ? <div className="guide-placeholder-menu" role="group" aria-label="Guide placeholders">{GUIDE_PLACEHOLDERS.map(item => <Button key={item.token} type="button" tone="quiet" onClick={() => insert(item.token)} help={`${item.help} Example: ${item.example}.`}>{item.label}</Button>)}</div> : null}
    </div>
    {preview.text ? <small className="guide-inline-preview">Example preview. {preview.text}</small> : null}
    {hasInlineStageRequirements(draft) ? <small className="muted" role="note">The unlock summary already includes a full message. Put {'{stage_requirements}'} on its own line to avoid awkward wording.</small> : null}
    {preview.unknown.length ? <small className="setting-error">Unknown placeholders. {preview.unknown.join(", ")}</small> : null}
    {!metrics.valid ? <small className="setting-error" role="alert">This guide field is too long. Keep it within {GUIDE_CODE_POINT_LIMIT} characters and {GUIDE_BYTE_LIMIT} UTF 8 bytes.</small> : null}
    {saving ? <small className="muted">Saving to the draft.</small> : null}
  </Field>;
}

function DependencyEditor({ stage }: { stage: StagePackage }) {
  const { boot, stages, mutateFile, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.stagePath] || "";
  const [selected, setSelected] = useState(() => new Set(dependenciesFor(content, stage.legacy)));
  const [mode, setMode] = useState(stringValue(readTomlValue(content, "stage.dependency_mode")) || "all");
  const [count, setCount] = useState(Math.max(1, numberValue(readTomlValue(content, "stage.dependency_count"), 1)));
  const [search, setSearch] = useState("");
  const available = stages.filter(candidate => !candidate.archived && candidate.id !== stage.id
    && (!search || `${candidate.name} ${candidate.id}`.toLowerCase().includes(search.toLowerCase())));
  const save = async () => {
    const dependencies = [...selected];
    let updated = upsertToml(content, stage.legacy ? "stage.dependency" : "stage.dependencies", dependencies);
    updated = upsertToml(updated, "stage.dependency_mode", dependencies.length ? mode : "all");
    updated = upsertToml(updated, "stage.dependency_count", mode === "at_least" ? Math.min(Math.max(1, count), dependencies.length || 1) : 1);
    await mutateFile(stage.stagePath, updated, "Required stage paths saved");
    closeDialog();
  };
  return <div className="dialog-form">
    <div className="form-grid">
      <Field label="How selected paths join"><select value={mode} onChange={event => setMode(event.target.value)}><option value="all">Require every selected path</option><option value="any">Require any one selected path</option><option value="at_least">Require a minimum number</option></select></Field>
      {mode === "at_least" ? <Field label="Minimum paths"><input type="number" min={1} max={Math.max(1, selected.size)} value={count} onChange={event => setCount(Number(event.target.value))}/></Field> : <div className="dependency-policy"><span>Policy</span><strong>{mode === "all" ? "Every path must be complete" : "One path is enough"}</strong></div>}
      <Field label="Find a stage" wide><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Mage, Wizard, Knight"/></Field>
    </div>
    <div className="dependency-grid">{available.map(candidate => {
      const cycle = stageDependsOn(stages, candidate.id, stage.id);
      const checked = selected.has(candidate.id);
      return <label key={candidate.id} className={`dependency-option ${checked ? "selected" : ""} ${cycle && !checked ? "disabled" : ""}`}><input type="checkbox" checked={checked} disabled={cycle && !checked} onChange={event => setSelected(current => { const next = new Set(current); if (event.target.checked) next.add(candidate.id); else next.delete(candidate.id); return next; })}/><span className="stage-orb">◆</span><span><strong>{candidate.name}</strong><small>{cycle && !checked ? "Would create a progression loop" : candidate.dependencies.length ? `Evolves from ${candidate.dependencies.length} path${candidate.dependencies.length === 1 ? "" : "s"}` : "Beginner path"}</small></span></label>;
    })}</div>
    <div className="dependency-preview"><span>{stage.name}</span><strong>{selected.size ? `${selected.size} incoming path${selected.size === 1 ? "" : "s"}` : "Beginner stage"}</strong><div>{[...selected].map(id => <Badge key={id} tone="gold" help="Selected prerequisite path. The requirement policy decides how many selected stages a player needs.">{stages.find(candidate => candidate.id === id)?.name || id}</Badge>)}</div></div>
    <footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Cancel</Button><Button tone="primary" onClick={() => void save()}>Save paths</Button></footer>
  </div>;
}

function SlotEditor({ stage }: { stage: StagePackage }) {
  const { boot, stages, mutateFiles, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.stagePath] || "";
  const [group, setGroup] = useState(stringValue(readTomlValue(content, "stage.slot_group")));
  const [limit, setLimit] = useState(numberValue(readTomlValue(content, "stage.slot_limit")));
  const [policy, setPolicy] = useState(stringValue(readTomlValue(content, "stage.slot_policy")) || "deny");
  const [applyGroup, setApplyGroup] = useState(false);
  const knownGroups = useMemo(() => [...new Set(stages.map(candidate => stringValue(readTomlValue(boot?.draft.files[candidate.stagePath] || "", "stage.slot_group"))).filter(Boolean))], [boot?.draft.files, stages]);
  const save = async () => {
    const targets = applyGroup && group ? stages.filter(candidate => stringValue(readTomlValue(boot?.draft.files[candidate.stagePath] || "", "stage.slot_group")) === group) : [stage];
    const changes = targets.map(target => {
      let updated = boot?.draft.files[target.stagePath] || "";
      updated = upsertToml(updated, "stage.slot_group", group);
      updated = upsertToml(updated, "stage.slot_limit", Math.max(0, limit));
      updated = upsertToml(updated, "stage.slot_policy", policy);
      return { path: target.stagePath, content: updated };
    });
    await mutateFiles(changes, "Stage slot behavior saved");
    closeDialog();
  };
  return <div className="dialog-form">
    <div className="form-grid">
      <Field label="Slot group" help="Related choices share one limit."><input list="slot-groups" value={group} onChange={event => setGroup(event.target.value)} placeholder="beginner_classes"/><datalist id="slot-groups">{knownGroups.map(value => <option key={value} value={value}/>)}</datalist></Field>
      <Field label="Maximum active stages" help="Zero lets every stage in the group stack."><input type="number" min={0} value={limit} onChange={event => setLimit(Number(event.target.value))}/></Field>
      <Field label="When the group is full"><select value={policy} onChange={event => setPolicy(event.target.value)}><option value="deny">Block the new stage</option><option value="replace_oldest">Replace the oldest stage</option><option value="replace_lowest_priority">Replace the lowest priority stage</option><option value="replace_all">Replace every active stage</option></select></Field>
      <Toggle label="Apply to the existing group" help="Update every stage already assigned to this group." checked={applyGroup} onChange={setApplyGroup}/>
    </div>
    <div className="policy-summary"><strong>{limit <= 0 ? "Every stage stacks" : `${limit} stage${limit === 1 ? "" : "s"} may be active`}</strong><p>{limit <= 0 ? "Players keep all benefits from this group." : `When full, the server will ${policy.replaceAll("_", " ")}.`}</p></div>
    <footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Cancel</Button><Button tone="primary" onClick={() => void save()}>Save slot behavior</Button></footer>
  </div>;
}

export function EssentialsPanel({ stage }: { stage: StagePackage }) {
  const { boot, stages, mutateFile, openDialog } = useEditor();
  const content = boot?.draft.files[stage.stagePath] || "";
  const save = (path: string, value: unknown) => mutateFile(stage.stagePath, current => upsertToml(current, path, value));
  const icon = stringValue(readTomlValue(content, "stage.icon")) || stage.icon;
  const tags = parseSimpleArray(readTomlValue(content, "stage.tags"));
  const ownership = parseOwnership(content);
  const frame = stringValue(readTomlValue(content, "display.frame")) || "task";
  const reveal = stringValue(readTomlValue(content, "display.reveal")) || "dependencies";
  const background = stringValue(readTomlValue(content, "display.background")) || "minecraft:textures/gui/advancements/backgrounds/stone.png";
  const slotGroup = stringValue(readTomlValue(content, "stage.slot_group"));
  const slotLimit = numberValue(readTomlValue(content, "stage.slot_limit"));
  const xRaw = readTomlValue(content, "display.x");
  const yRaw = readTomlValue(content, "display.y");
  return <div className="stage-panel-stack">
    <Section title="Identity" description="The name, icon, and explanation players see everywhere." className="identity-section">
      <div className="form-grid">
        <TextDraftField label="Stage name" value={stage.name} onSave={value => save("stage.display_name", value)} help="The name players see in the stage menu. Example: Miner."/>
        <Field label="Stage icon" help="Choose the item players see beside this stage. Example: minecraft:iron_pickaxe."><div className="compound-input"><input value={icon} readOnly/><Button tone="quiet" onClick={() => openDialog({ title: "Choose the stage icon", description: "Results come from the installed server registry.", content: <CatalogPicker catalogId="items" selected={icon} allowPrefixes={false} onPick={value => void save("stage.icon", value)}/>, width: "wide" })}>Browse</Button></div></Field>
        <TextDraftField label="Description" value={stage.description} multiline wide onSave={value => save("stage.description", value)} help="Explain what changes when this stage is earned."/>
        <TextDraftField label="Tags" value={tags.join(", ")} wide onSave={value => save("stage.tags", lineValues(value))} help="Comma separated labels used by commands, scripts, and bulk operations."/>
      </div>
    </Section>
    <Section title="Required stages" description="Choose which stages must be complete before this stage can be earned." action={<Button tone="primary" onClick={() => openDialog({ title: `Required stages for ${stage.name}`, description: "Select prerequisites and choose how their branches join.", content: <DependencyEditor stage={stage}/>, width: "wide" })}>Edit required stages</Button>}>
      <div className="summary-row"><span className="summary-icon">↟</span><div><strong>{dependencySummary(stages, stage)}</strong><p>{stage.dependencies.length ? "These branches connect directly into this stage." : "This stage can begin a new progression path."}</p></div><div className="chip-row">{stage.dependencies.map(id => <Badge key={id} tone="gold" help="Configured prerequisite path. Check the requirement policy to see whether players need every path or only some of them.">{stages.find(candidate => candidate.id === id)?.name || id}</Badge>)}</div></div>
    </Section>
    <Section title="Ownership and stacking" description="Control who shares the stage and how related choices combine." action={<Button onClick={() => openDialog({ title: "Stage slots and stacking", description: "Limit mutually exclusive classes or let specialist buffs stack.", content: <SlotEditor stage={stage}/> })}>Configure slots</Button>}>
      <div className="form-grid">
        <Field label="Ownership" help="Personal stages use team_stage = false. Inherit keeps the global setting."><select value={ownership} onChange={event => void mutateFile(stage.stagePath, writeOwnership(content, event.target.value as Parameters<typeof writeOwnership>[1]), "Stage ownership saved")}><option value="inherit">Inherit global setting</option><option value="personal">Each player owns it</option><option value="team">The team shares it</option><option value="server">The whole server shares it</option></select><OwnershipFeedback source={content}/><FieldDiagnostics file={stage.stagePath} fields={["stage.scope", "stage.team_stage"]}/></Field>
        <div className="policy-summary compact"><span>Current slot behavior</span><strong>{slotGroup ? slotLimit > 0 ? `${slotLimit} active in ${slotGroup}` : `All ${slotGroup} stages stack` : "No slot limit"}</strong></div>
        <Toggle label="Hide this stage from players" help="Reveal policy still controls when hidden content becomes visible." checked={booleanValue(readTomlValue(content, "stage.hidden"))} onChange={value => void save("stage.hidden", value)}/>
      </div>
    </Section>
    <GuideEditor stage={stage} content={content} save={save}/>
    <Section title="Player map appearance" description="Use the advancement inspired presentation players see in game.">
      <div className="form-grid">
        <TextDraftField label="Map category" value={stringValue(readTomlValue(content, "stage.category"))} onSave={value => save("stage.category", value)} help="Categories become filters in the player screen."/>
        <TextDraftField label="Accent color" value={stringValue(readTomlValue(content, "stage.color")) || "#d9a83e"} onSave={value => save("stage.color", value)} help="Use a six digit hexadecimal color."/>
        <Field label="Vanilla advancement frame"><select value={frame} onChange={event => void save("display.frame", event.target.value)}><option value="task">Task</option><option value="goal">Goal</option><option value="challenge">Challenge</option></select></Field>
        <Field label="When the stage appears"><select value={reveal} onChange={event => void save("display.reveal", event.target.value)}><option value="always">Always visible</option><option value="dependencies">After prerequisites appear</option><option value="unlocked">Only after it is owned</option></select></Field>
        <TextDraftField label="Background texture" value={background} wide onSave={value => save("display.background", value)} help="A Minecraft texture identifier tiled behind this category."/>
        <TextDraftField label="Map X position" value={stringValue(xRaw)} type="number" onSave={async value => {
          const current = boot?.draft.files[stage.stagePath] || "";
          await mutateFile(stage.stagePath, value === "" ? removeTomlValue(current, "display.x") : upsertToml(current, "display.x", Number(value)));
        }} help="Leave blank for automatic layout."/>
        <TextDraftField label="Map Y position" value={stringValue(yRaw)} type="number" onSave={async value => {
          const current = boot?.draft.files[stage.stagePath] || "";
          await mutateFile(stage.stagePath, value === "" ? removeTomlValue(current, "display.y") : upsertToml(current, "display.y", Number(value)));
        }} help="Leave blank for automatic layout."/>
      </div>
    </Section>
  </div>;
}
