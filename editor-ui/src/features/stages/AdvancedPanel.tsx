import { useMemo, useState } from "react";
import { InlineCatalogSearch } from "../../components/CatalogPicker";
import { Badge, Button, EmptyState, Field, Section, Toggle } from "../../components/ui";
import { CONDITIONS } from "../../data";
import { featureCounts, slug, title } from "../../lib/model";
import {
  appendTomlBlock, conditionToml, encodeToml, extractArrayGroups, lineValues, parseSimpleArray,
  readBlockValue, readTomlValue, replaceArrayGroups, stringValue, upsertToml
} from "../../lib/toml";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";

function ChallengeForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [name, setName] = useState("Wither trial");
  const [startType, setStartType] = useState("boss_session");
  const [startTarget, setStartTarget] = useState("minecraft:wither");
  const [successType, setSuccessType] = useState("kill");
  const [successTarget, setSuccessTarget] = useState("minecraft:wither");
  const [endType, setEndType] = useState("none");
  const [endTarget, setEndTarget] = useState("");
  const [boss, setBoss] = useState("minecraft:wither");
  const [hits, setHits] = useState(0);
  const [timeout, setTimeoutValue] = useState("5m");
  const [retries, setRetries] = useState(0);
  const [scope, setScope] = useState("player");
  const [placement, setPlacement] = useState("top");
  const [hudScale, setHudScale] = useState(1);
  const [hudColor, setHudColor] = useState("gold");
  const [hudIcon, setHudIcon] = useState("minecraft:nether_star");
  const [hudAnimation, setHudAnimation] = useState("pulse");
  const [hudCompact, setHudCompact] = useState(false);
  const [hideInactive, setHideInactive] = useState(true);
  const [secretValues, setSecretValues] = useState(false);
  const [budgetEnabled, setBudgetEnabled] = useState(false);
  const [budgetMeasure, setBudgetMeasure] = useState("hits_taken");
  const [budgetMode, setBudgetMode] = useState("maximum");
  const [budgetMinimum, setBudgetMinimum] = useState(0);
  const [budgetMaximum, setBudgetMaximum] = useState(3);
  const [budgetRegeneration, setBudgetRegeneration] = useState(0);
  const [stepEnabled, setStepEnabled] = useState(false);
  const [stepType, setStepType] = useState("kill");
  const [stepTarget, setStepTarget] = useState("minecraft:wither_skeleton");
  const [stepTimeout, setStepTimeout] = useState("");
  const [stepReset, setStepReset] = useState(false);
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const challengeId = `${stage.id}/challenge_${slug(name) || Date.now().toString(36)}`;
    const lines = ["[[challenges]]", `id = ${encodeToml(challengeId)}`, `title = ${encodeToml(name)}`, `scope = ${encodeToml(scope)}`, `start_when = ${conditionToml(startType, startTarget, 1)}`, `success_when = ${conditionToml(successType, successTarget, 1)}`, `retries = ${Math.max(0, retries)}`];
    if (endType !== "none") lines.push(`end_when = ${conditionToml(endType, endTarget, 1)}`);
    if (hits > 0) lines.push(`max_hits = ${hits}`);
    if (boss.trim()) lines.push(`boss = ${encodeToml(boss.trim())}`);
    if (timeout.trim()) lines.push(`timeout = ${encodeToml(timeout.trim())}`);
    if (budgetEnabled) lines.push("", "[[challenges.budgets]]", `id = ${encodeToml(`${challengeId}/budget_1`)}`, `measure = ${encodeToml(budgetMeasure)}`, `mode = ${encodeToml(budgetMode)}`, `minimum = ${budgetMinimum}`, `maximum = ${budgetMaximum}`, `regeneration_per_second = ${budgetRegeneration}`);
    if (stepEnabled) lines.push("", "[[challenges.steps]]", `id = ${encodeToml(`${challengeId}/step_1`)}`, `condition = ${conditionToml(stepType, stepTarget, 1)}`, `reset_on_failure = ${stepReset}`, ...(stepTimeout.trim() ? [`timeout = ${encodeToml(stepTimeout.trim())}`] : []));
    lines.push("", "[challenges.hud]", "enabled = true", `placement = ${encodeToml(placement)}`, `scale = ${hudScale}`, `color = ${encodeToml(hudColor)}`, `icon = ${encodeToml(hudIcon)}`, `animation = ${encodeToml(hudAnimation)}`, `compact = ${hudCompact}`, `hide_when_inactive = ${hideInactive}`, `values_secret = ${secretValues}`);
    await mutateFile(stage.progressionPath, appendTomlBlock(boot?.draft.files[stage.progressionPath] || "", lines.join("\n")), "Challenge added");
    closeDialog();
  };
  const targetSearch = (type: string, setValue: (value: string) => void) => {
    const condition = CONDITIONS.find(value => value.id === type);
    return condition?.catalog ? <div className="field-wide"><InlineCatalogSearch catalogId={condition.catalog} mode="id" onPick={value => setValue(value.replace(/^id:/, ""))}/></div> : null;
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid">
    <Field wide label="Challenge name"><input value={name} onChange={event => setName(event.target.value)} required/></Field>
    <Field label="Start when"><select value={startType} onChange={event => setStartType(event.target.value)}>{CONDITIONS.filter(value => value.id !== "none").map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field>
    <Field label="Start target"><input value={startTarget} onChange={event => setStartTarget(event.target.value)}/></Field>{targetSearch(startType, setStartTarget)}
    <Field label="Succeed when"><select value={successType} onChange={event => setSuccessType(event.target.value)}>{CONDITIONS.filter(value => value.id !== "none").map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field>
    <Field label="Success target"><input value={successTarget} onChange={event => setSuccessTarget(event.target.value)}/></Field>{targetSearch(successType, setSuccessTarget)}
    <Field label="End without success when"><select value={endType} onChange={event => setEndType(event.target.value)}>{CONDITIONS.map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field>
    {endType !== "none" ? <Field label="End target"><input value={endTarget} onChange={event => setEndTarget(event.target.value)}/></Field> : null}
    <Field label="Boss entity filter"><input value={boss} onChange={event => setBoss(event.target.value)}/></Field>
    <Field label="Maximum hits taken" help="Zero means no hit limit."><input type="number" min={0} value={hits} onChange={event => setHits(Number(event.target.value))}/></Field>
    <Field label="Time limit"><input value={timeout} onChange={event => setTimeoutValue(event.target.value)} placeholder="5m"/></Field>
    <Field label="Retries"><input type="number" min={0} value={retries} onChange={event => setRetries(Number(event.target.value))}/></Field>
    <Field label="Progress owner"><select value={scope} onChange={event => setScope(event.target.value)}><option value="player">Each player</option><option value="team">Whole team</option><option value="server">Whole server</option></select></Field>
    <Field label="HUD placement"><select value={placement} onChange={event => setPlacement(event.target.value)}><option value="top">Top</option><option value="bottom">Bottom</option><option value="left">Left</option><option value="right">Right</option></select></Field>
    <Field label="HUD scale"><input type="number" min="0.25" step="0.05" value={hudScale} onChange={event => setHudScale(Number(event.target.value))}/></Field>
    <Field label="HUD color"><input value={hudColor} onChange={event => setHudColor(event.target.value)} placeholder="gold or #e3aa32"/></Field>
    <Field label="HUD item icon"><input value={hudIcon} onChange={event => setHudIcon(event.target.value)}/></Field>
    <Field label="HUD animation"><select value={hudAnimation} onChange={event => setHudAnimation(event.target.value)}><option value="none">None</option><option value="pulse">Pulse</option><option value="flash">Flash</option><option value="shake">Shake</option></select></Field>
    <Toggle label="Compact HUD" checked={hudCompact} onChange={setHudCompact}/><Toggle label="Hide HUD while inactive" checked={hideInactive} onChange={setHideInactive}/><Toggle label="Keep measured values secret" checked={secretValues} onChange={setSecretValues}/>
    <Toggle label="Add a measured budget" help="Limit or require a runtime measure such as hits taken, damage, time, or a registered measure." checked={budgetEnabled} onChange={setBudgetEnabled}/>
    {budgetEnabled ? <><Field label="Budget measure"><input value={budgetMeasure} onChange={event => setBudgetMeasure(event.target.value)}/></Field><Field label="Budget behavior"><select value={budgetMode} onChange={event => setBudgetMode(event.target.value)}><option value="maximum">Must stay below maximum</option><option value="minimum">Must reach minimum</option><option value="range">Must stay inside range</option></select></Field><Field label="Budget minimum"><input type="number" step="any" value={budgetMinimum} onChange={event => setBudgetMinimum(Number(event.target.value))}/></Field><Field label="Budget maximum"><input type="number" step="any" value={budgetMaximum} onChange={event => setBudgetMaximum(Number(event.target.value))}/></Field><Field label="Regeneration each second"><input type="number" step="any" value={budgetRegeneration} onChange={event => setBudgetRegeneration(Number(event.target.value))}/></Field></> : null}
    <Toggle label="Add an ordered challenge step" help="The challenge cannot complete until this step condition succeeds." checked={stepEnabled} onChange={setStepEnabled}/>
    {stepEnabled ? <><Field label="Step condition"><select value={stepType} onChange={event => setStepType(event.target.value)}>{CONDITIONS.filter(value => value.id !== "none").map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field><Field label="Step target"><input value={stepTarget} onChange={event => setStepTarget(event.target.value)}/></Field><Field label="Step timeout"><input value={stepTimeout} onChange={event => setStepTimeout(event.target.value)} placeholder="Optional, such as 2m"/></Field><Toggle label="Reset challenge if step fails" checked={stepReset} onChange={setStepReset}/></> : null}
  </div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add challenge</Button></footer></form>;
}

function VariableForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [name, setName] = useState("quest_points");
  const [type, setType] = useState("integer");
  const [scope, setScope] = useState("player");
  const [initial, setInitial] = useState("0");
  const [minimum, setMinimum] = useState("");
  const [maximum, setMaximum] = useState("");
  const [persistent, setPersistent] = useState(true);
  const [visible, setVisible] = useState(false);
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const clean = slug(name);
    let defaultValue: unknown = initial;
    if (["integer", "decimal", "currency", "counter"].includes(type)) defaultValue = Number(initial || 0);
    if (type === "boolean") defaultValue = initial.toLowerCase() === "true";
    const lines = ["[[variables]]", `id = ${encodeToml(`${stage.id}/variable_${clean}`)}`, `type = ${encodeToml(type)}`, `scope = ${encodeToml(scope)}`, `default = ${encodeToml(defaultValue)}`, `persistent = ${persistent}`, `sync_visible = ${visible}`];
    if (minimum !== "") lines.push(`minimum = ${Number(minimum)}`);
    if (maximum !== "") lines.push(`maximum = ${Number(maximum)}`);
    await mutateFile(stage.progressionPath, appendTomlBlock(boot?.draft.files[stage.progressionPath] || "", lines.join("\n")), "Variable added");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid">
    <Field label="Variable name"><input value={name} onChange={event => setName(event.target.value)} required/></Field>
    <Field label="Value type"><select value={type} onChange={event => setType(event.target.value)}><option value="integer">Whole number</option><option value="decimal">Decimal number</option><option value="boolean">Yes or no</option><option value="string">Text</option><option value="currency">Currency</option><option value="counter">Counter</option></select></Field>
    <Field label="Owner"><select value={scope} onChange={event => setScope(event.target.value)}><option value="player">Each player</option><option value="team">Whole team</option><option value="server">Whole server</option></select></Field>
    <Field label="Starting value"><input value={initial} onChange={event => setInitial(event.target.value)}/></Field>
    <Field label="Minimum"><input type="number" value={minimum} onChange={event => setMinimum(event.target.value)} placeholder="No minimum"/></Field>
    <Field label="Maximum"><input type="number" value={maximum} onChange={event => setMaximum(event.target.value)} placeholder="No maximum"/></Field>
    <Toggle label="Save across restarts" checked={persistent} onChange={setPersistent}/><Toggle label="Synchronize for player UI" checked={visible} onChange={setVisible}/>
  </div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add variable</Button></footer></form>;
}

function FormulaForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [name, setName] = useState("combat_score");
  const [expression, setExpression] = useState("kills * 2 + quest_points");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    await mutateFile(stage.progressionPath, upsertToml(boot?.draft.files[stage.progressionPath] || "", `formulas.${slug(name)}`, expression.trim()), "Formula saved");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid"><Field label="Formula name"><input value={name} onChange={event => setName(event.target.value)} required/></Field><Field wide label="Safe calculation"><input value={expression} onChange={event => setExpression(event.target.value)} required/></Field></div><div className="explanation-card"><strong>Event driven evaluation</strong><p>Formulas are compiled and reevaluated only when their referenced values change. Arbitrary expressions are not run every tick.</p></div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Save formula</Button></footer></form>;
}

function StatesForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.progressionPath] || "";
  const [values, setValues] = useState(parseSimpleArray(readTomlValue(content, "states.values")).join("\n") || "missing\navailable\nowned\ncompleted");
  const [owned, setOwned] = useState(parseSimpleArray(readTomlValue(content, "states.ownership_states")).join("\n") || "owned\ncompleted");
  const [initial, setInitial] = useState(stringValue(readTomlValue(content, "states.initial")) || "missing");
  const save = async () => {
    let updated = upsertToml(content, "states.values", lineValues(values));
    updated = upsertToml(updated, "states.ownership_states", lineValues(owned));
    updated = upsertToml(updated, "states.initial", initial);
    await mutateFile(stage.progressionPath, updated, "Stage lifecycle states saved");
    closeDialog();
  };
  return <div className="dialog-form"><div className="form-grid"><Field wide label="Available state names"><textarea rows={6} value={values} onChange={event => setValues(event.target.value)}/></Field><Field wide label="States that count as owned"><textarea rows={4} value={owned} onChange={event => setOwned(event.target.value)}/></Field><Field label="Starting state"><input value={initial} onChange={event => setInitial(event.target.value)}/></Field></div><footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Cancel</Button><Button tone="primary" onClick={() => void save()}>Save lifecycle</Button></footer></div>;
}

function ProfileForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [name, setName] = useState("sword_mastery");
  const [content, setContent] = useState("tag:c:swords");
  const [variable, setVariable] = useState("sword_xp");
  const [level, setLevel] = useState("trained");
  const [minimum, setMinimum] = useState(10);
  const [effect, setEffect] = useState("strengthen");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const lines = ["[[profiles]]", `id = ${encodeToml(`${stage.id}/profile_${slug(name)}`)}`, `content = ${encodeToml([content])}`, `proficiency = ${encodeToml(variable)}`, "", "[[profiles.levels]]", `id = ${encodeToml(slug(level) || "trained")}`, `minimum = ${minimum}`, `effect = ${encodeToml(effect)}`];
    await mutateFile(stage.progressionPath, appendTomlBlock(boot?.draft.files[stage.progressionPath] || "", lines.join("\n")), "Affinity profile added");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid"><Field label="Profile name"><input value={name} onChange={event => setName(event.target.value)} required/></Field><Field label="Content selector"><input value={content} onChange={event => setContent(event.target.value)} required/></Field><Field label="Proficiency variable"><input value={variable} onChange={event => setVariable(event.target.value)} required/></Field><Field label="Level name"><input value={level} onChange={event => setLevel(event.target.value)}/></Field><Field label="Minimum value"><input type="number" value={minimum} onChange={event => setMinimum(Number(event.target.value))}/></Field><Field label="Behavior"><select value={effect} onChange={event => setEffect(event.target.value)}><option value="normal">Normal</option><option value="deny">Deny</option><option value="weaken">Weaken</option><option value="strengthen">Strengthen</option><option value="increase_cost">Increase cost</option><option value="change_cooldown">Change cooldown</option><option value="replace_behavior">Replace behavior</option></select></Field></div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add profile</Button></footer></form>;
}

function TemplateForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [name, setName] = useState("common_combat");
  const [includes, setIncludes] = useState("");
  const [merge, setMerge] = useState("deep_merge");
  const [label, setLabel] = useState("Reusable combat bundle");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const lines = ["[[templates]]", `id = ${encodeToml(`${stage.id}/template_${slug(name)}`)}`, `includes = ${encodeToml(lineValues(includes))}`, `merge = ${encodeToml(merge)}`, `fragment = ${encodeToml({ label })}`];
    await mutateFile(stage.progressionPath, appendTomlBlock(boot?.draft.files[stage.progressionPath] || "", lines.join("\n")), "Template added");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid"><Field label="Template name"><input value={name} onChange={event => setName(event.target.value)} required/></Field><Field wide label="Included templates"><textarea rows={3} value={includes} onChange={event => setIncludes(event.target.value)} placeholder="pack:base_rules"/></Field><Field label="Merge behavior"><select value={merge} onChange={event => setMerge(event.target.value)}><option value="deep_merge">Combine nested values</option><option value="replace">Replace earlier values</option><option value="append">Append lists</option></select></Field><Field label="Readable label"><input value={label} onChange={event => setLabel(event.target.value)}/></Field></div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add template</Button></footer></form>;
}

function ConfiguredAdvanced({ stage, table }: { stage: StagePackage; table: string }) {
  const { boot, mutateFile } = useEditor();
  const content = boot?.draft.files[stage.progressionPath] || "";
  const groups = useMemo(() => extractArrayGroups(content, table), [content, table]);
  if (!groups.length) return null;
  return <div className="configured-list">{groups.map((group, index) => <article key={`${table}:${index}`}><div><Badge tone="gold">{title(table.replace(/s$/, ""))}</Badge><strong>{stringValue(readBlockValue(group.text, "title")) || stringValue(readBlockValue(group.text, "id")) || `${title(table)} ${index + 1}`}</strong></div><Button tone="danger" onClick={() => { const next = groups.map(value => value.text); next.splice(index, 1); void mutateFile(stage.progressionPath, replaceArrayGroups(content, table, next), `${title(table)} entry removed`); }}>Remove</Button></article>)}</div>;
}

export function AdvancedPanel({ stage }: { stage: StagePackage }) {
  const { boot, openDialog } = useEditor();
  const counts = featureCounts(stage, boot?.draft.files || {});
  const open = (titleText: string, description: string, content: React.ReactNode) => openDialog({ title: titleText, description, content, width: "wide" });
  const builders = [
    ["Challenge", "Timed, limited, and retryable encounters with HUD progress.", "challenges", <ChallengeForm stage={stage}/>],
    ["Variable", "Bounded player, team, or server values for conditions and formulas.", "variables", <VariableForm stage={stage}/>],
    ["Formula", "Named safe arithmetic evaluated from subscribed values.", "formulas", <FormulaForm stage={stage}/>],
    ["Lifecycle states", "Go beyond owned or not owned with custom stage states.", "states", <StatesForm stage={stage}/>],
    ["Affinity profile", "Change behavior as a proficiency value grows.", "profiles", <ProfileForm stage={stage}/>],
    ["Reusable template", "Combine shared rules and values with a clear merge policy.", "templates", <TemplateForm stage={stage}/>]
  ] as const;
  return <div className="stage-panel-stack">
    <Section title="Advanced systems" description="Guided builders for the deeper stage engine. Source editing remains available for exact control.">
      <div className="advanced-grid">{builders.map(([label, description, countKey, form]) => <button key={label} onClick={() => open(`Add ${label.toLowerCase()}`, description, form)}><span>✦</span><div><strong>{label}</strong><p>{description}</p></div><Badge tone={(counts[countKey] || 0) > 0 ? "success" : "neutral"}>{counts[countKey] || 0}</Badge></button>)}</div>
    </Section>
    <Section title="Configured challenges" description="Challenge sessions keep their progress, limits, success rules, and HUD configuration together.">
      {counts.challenges ? <ConfiguredAdvanced stage={stage} table="challenges"/> : <EmptyState icon="progression" title="No challenge sessions" description="Create a boss, survival, exploration, or KubeJS driven challenge."/>}
    </Section>
    <Section title="Configured data systems" description="Variables, profiles, templates, formulas, and lifecycle states are stored with this stage.">
      <div className="metric-strip"><div><strong>{counts.variables}</strong><span>Variables</span></div><div><strong>{counts.profiles}</strong><span>Profiles</span></div><div><strong>{counts.templates}</strong><span>Templates</span></div><div><strong>{counts.formulas}</strong><span>Formula values</span></div></div>
      <ConfiguredAdvanced stage={stage} table="variables"/><ConfiguredAdvanced stage={stage} table="profiles"/><ConfiguredAdvanced stage={stage} table="templates"/>
    </Section>
  </div>;
}
