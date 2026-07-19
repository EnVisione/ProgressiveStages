import { useMemo, useState } from "react";
import { ACTION_LABELS, CATEGORIES, CONDITIONS, EFFECTS } from "../../data";
import { InlineCatalogSearch } from "../../components/CatalogPicker";
import { Badge, Button, EmptyState, Field, Section, Toggle } from "../../components/ui";
import { Icon } from "../../components/Icon";
import { ruleModels, selectorMode, title } from "../../lib/model";
import { appendTomlBlock, conditionToml, encodeToml, parseSimpleArray, readTomlValue, upsertToml } from "../../lib/toml";
import { useEditor } from "../../store/EditorContext";
import type { RuleModel, StagePackage } from "../../types";

interface RuleDraft {
  category: string;
  action: string;
  effect: string;
  selector: string;
  mode: string;
  priority: number;
  viewer: string;
  lifetime: string;
  duration: string;
  conditionType: string;
  conditionTarget: string;
  count: number;
  exception: string;
  exceptionPriority: number;
}

function effectLabel(category: string, effect: string, action = ""): string {
  if (category === "structures") {
    if (effect === "allow" || effect === "unlock") return "Allow access to structure";
    if (effect === "lock" || effect === "deny") return "Deny access to structure";
  }
  if (category === "abilities") {
    if (effect === "allow" || effect === "unlock") return "Allow this ability";
    if (effect === "lock" || effect === "deny") return "Block this ability";
  }
  if (category === "entities" && action === "presence") {
    if (effect === "allow" || effect === "unlock") return "Allow the entity to exist for this player";
    if (effect === "lock" || effect === "deny") return "Hide the entity and make it pacifist";
  }
  return EFFECTS.find(value => value.value === effect)?.label || title(effect);
}

function ruleGroups(text: string, table: "rules" | "temporary_rules"): Array<{ start: number; end: number; text: string }> {
  const lines = text.split(/\r?\n/);
  const starts: number[] = [];
  const header = new RegExp(`^\\s*\\[\\[${table}\\]\\]\\s*(?:#.*)?$`);
  lines.forEach((line, index) => { if (header.test(line)) starts.push(index); });
  return starts.map((start, index) => {
    let end = index + 1 < starts.length ? starts[index + 1] : lines.length;
    if (index + 1 >= starts.length) {
      for (let cursor = start + 1; cursor < lines.length; cursor++) {
        const match = lines[cursor].match(/^\s*\[\[([^\]]+)\]\]/);
        if (match && match[1] !== `${table}.exceptions` && match[1] !== table) { end = cursor; break; }
      }
    }
    return { start, end, text: lines.slice(start, end).join("\n").trimEnd() };
  });
}

function replaceRuleGroup(text: string, table: "rules" | "temporary_rules", index: number, replacement: string | null): string {
  const lines = text.split(/\r?\n/);
  const group = ruleGroups(text, table)[index];
  if (!group) throw new Error("The rule changed in another edit. Reopen it and try again.");
  lines.splice(group.start, group.end - group.start, ...(replacement ? replacement.trim().split("\n") : []));
  return lines.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd() + "\n";
}

function removeClassicRule(text: string, rule: RuleModel): string {
  for (const field of ["locked", "allowed", "always_unlocked"]) {
    const path = `${rule.category}.${field}`;
    const values = parseSimpleArray(readTomlValue(text, path));
    const index = values.findIndex(value => value.replace(/\|priority=-?\d+$/, "") === rule.selector);
    if (index >= 0) {
      values.splice(index, 1);
      return upsertToml(text, path, values);
    }
  }
  return text;
}

function serializeRule(stage: StagePackage, draft: RuleDraft, table: "rules" | "temporary_rules", previous?: RuleModel): string {
  const previousId = previous?.sourceText.match(/^\s*id\s*=\s*["']([^"']+)/m)?.[1];
  const id = previousId || `${stage.id}/${table === "rules" ? "rule" : "temporary_rule"}_${Date.now().toString(36)}`;
  const lines = [
    `[[${table}]]`,
    `id = ${encodeToml(id)}`,
    `effect = ${encodeToml(draft.effect)}`,
    `priority = ${draft.priority}`,
    `action = ${encodeToml(draft.action)}`,
    `targets.${draft.category} = ${encodeToml([draft.selector])}`
  ];
  if (table === "temporary_rules") lines.push(`lifetime = ${encodeToml(draft.lifetime)}`);
  if (draft.duration) lines.push(`duration = ${encodeToml(draft.duration)}`);
  if (draft.conditionType !== "none") lines.push(`while = ${conditionToml(draft.conditionType, draft.conditionTarget, draft.count)}`);
  if (draft.viewer !== "inherit") {
    lines.push(`presentation.jei = ${encodeToml(draft.viewer)}`);
    lines.push(`presentation.emi = ${encodeToml(draft.viewer)}`);
  }
  if (draft.exception) {
    lines.push("", `[[${table}.exceptions]]`, `effect = "exclude"`,
      `priority = ${draft.exceptionPriority}`, `targets.${draft.category} = ${encodeToml([draft.exception])}`);
  }
  return lines.join("\n");
}

function RuleForm({ stage, rule }: { stage: StagePackage; rule?: RuleModel }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const initialCategory = rule?.category || "items";
  const [draft, setDraft] = useState<RuleDraft>({
    category: initialCategory,
    action: rule?.action || CATEGORIES[initialCategory].actions[0],
    effect: rule?.effect || "lock",
    selector: rule?.selector || "",
    mode: selectorMode(rule?.selector || ""),
    priority: rule?.priority ?? 100,
    viewer: rule?.viewer || "inherit",
    lifetime: rule?.lifetime || "permanent",
    duration: rule?.duration || "",
    conditionType: rule?.conditionType || "none",
    conditionTarget: rule?.conditionTarget || "",
    count: rule?.count || 1,
    exception: rule?.exception || "",
    exceptionPriority: rule?.exceptionPriority || (rule?.priority ?? 100) + 1
  });
  const update = <K extends keyof RuleDraft>(key: K, value: RuleDraft[K]) => setDraft(current => ({ ...current, [key]: value }));
  const category = CATEGORIES[draft.category];
  const condition = CONDITIONS.find(entry => entry.id === draft.conditionType);
  const temporary = draft.lifetime !== "permanent" || draft.conditionType !== "none";
  const selectsEverything = draft.mode === "all";
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!draft.selector) return;
    let content = boot?.draft.files[stage.rulesPath] || "";
    const table: "rules" | "temporary_rules" = temporary ? "temporary_rules" : "rules";
    const block = serializeRule(stage, draft, table, rule);
    if (rule?.table === "classic") content = removeClassicRule(content, rule);
    else if (rule && rule.table !== table) {
      content = replaceRuleGroup(content, rule.table as "rules" | "temporary_rules", rule.tableIndex, null);
      content = appendTomlBlock(content, block);
    } else if (rule) content = replaceRuleGroup(content, table, rule.tableIndex, block);
    else if (stage.legacy) {
      if (temporary) throw new Error("Temporary rules need a three file stage package.");
      const field = ["allow", "unlock"].includes(draft.effect) ? "always_unlocked" : "locked";
      const values = parseSimpleArray(readTomlValue(content, `${draft.category}.${field}`));
      values.push(`${draft.selector}${draft.priority ? `|priority=${draft.priority}` : ""}`);
      content = upsertToml(content, `${draft.category}.${field}`, values);
    } else content = appendTomlBlock(content, block);
    await mutateFile(stage.rulesPath, content, "Rule saved to the draft");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}>
    <div className="rule-sentence"><span>When a player tries to</span><strong>{ACTION_LABELS[draft.action] || title(draft.action)}</strong><span>the server will</span><strong>{effectLabel(draft.category, draft.effect, draft.action)}</strong></div>
    <div className="form-grid">
      <Field label="Rule category"><select value={draft.category} onChange={event => {
        const next = event.target.value;
        setDraft(current => ({ ...current, category: next, action: CATEGORIES[next].actions[0], selector: current.mode === "all" ? "all:*" : "" }));
      }}>{Object.entries(CATEGORIES).map(([id, value]) => <option key={id} value={id}>{value.label}</option>)}</select></Field>
      <Field label="Player action"><select value={draft.action} onChange={event => update("action", event.target.value)}>{category.actions.map(action => <option key={action} value={action}>{ACTION_LABELS[action] || title(action)}</option>)}</select></Field>
      <Field label="Result"><select value={draft.effect} onChange={event => update("effect", event.target.value)}>{EFFECTS.filter(effect => effect.value !== "replace" || ["mobs", "ores"].includes(draft.category)).filter(effect => effect.value !== "present" || ["recipes", "advancements", "ores"].includes(draft.category)).map(effect => <option key={effect.value} value={effect.value}>{effectLabel(draft.category, effect.value, draft.action)}</option>)}</select></Field>
      <Field label="Priority" help="A larger number wins when rules overlap."><input type="number" value={draft.priority} onChange={event => update("priority", Number(event.target.value))}/></Field>
    </div>
    <section className="dialog-section"><header><span className="step-number">1</span><div><h3>Choose the target</h3><p>The registry only shows content valid for {category.label.toLowerCase()}.</p></div></header><div className="form-grid">
      <Field label="Selection method"><select value={draft.mode} onChange={event => {
        const mode = event.target.value;
        setDraft(current => ({ ...current, mode, selector: mode === "all" ? "all:*" : current.mode === "all" ? "" : current.selector }));
      }}><option value="all">Everything in this category</option><option value="id">One exact identifier</option><option value="mod">Everything from a mod</option><option value="tag">Everything in a tag</option><option value="name">Anything with a matching name</option></select></Field>
      <Field label="Selected target" help={selectsEverything ? `This matches every registered ${category.label.toLowerCase()}. Add a higher priority exception to allow selected content.` : undefined}><input value={draft.selector} onChange={event => update("selector", event.target.value)} placeholder="id:minecraft:diamond_sword" readOnly={selectsEverything} required/></Field>
      {!selectsEverything ? <div className="field-wide"><InlineCatalogSearch catalogId={category.catalog} mode={draft.mode} onPick={value => update("selector", value)}/></div> : null}
    </div></section>
    <section className="dialog-section"><header><span className="step-number">2</span><div><h3>Choose when it participates</h3><p>Permanent rules follow stage ownership. Conditional rules can follow locations, events, sessions, and scripts.</p></div></header><div className="form-grid">
      <Field label="Activation condition"><select value={draft.conditionType} onChange={event => update("conditionType", event.target.value)}>{CONDITIONS.map(entry => <option key={entry.id} value={entry.id}>{entry.label}</option>)}</select></Field>
      <Field label="Condition target" help={condition?.help}><input value={draft.conditionTarget} onChange={event => update("conditionTarget", event.target.value)} placeholder={condition?.catalog ? "Choose a registered identifier" : "Optional value"}/></Field>
      {condition?.catalog ? <div className="field-wide"><InlineCatalogSearch catalogId={condition.catalog} mode="id" onPick={value => update("conditionTarget", value.replace(/^id:/, ""))}/></div> : null}
      <Field label="Required amount"><input type="number" min={1} value={draft.count} onChange={event => update("count", Number(event.target.value))}/></Field>
      <Field label="Lifetime"><select value={draft.lifetime} onChange={event => update("lifetime", event.target.value)}><option value="permanent">Permanent stage rule</option><option value="live">Only while the condition is true</option><option value="duration">Timed after the trigger</option><option value="session">Current session</option><option value="latched">Active until reset</option><option value="schedule">Scheduled lifetime</option></select></Field>
      {draft.lifetime === "duration" || draft.lifetime === "schedule" ? <Field label="Duration or schedule"><input value={draft.duration} onChange={event => update("duration", event.target.value)} placeholder="30s or 5m"/></Field> : null}
    </div></section>
    <section className="dialog-section"><header><span className="step-number">3</span><div><h3>Presentation and exception</h3><p>An exception normally needs a larger priority than the broader rule.</p></div></header><div className="form-grid">
      <Field label="JEI and EMI"><select value={draft.viewer} onChange={event => update("viewer", event.target.value)}><option value="inherit">Follow normal policy</option><option value="show">Always show</option><option value="hide">Hide</option><option value="overlay">Show with a locked overlay</option></select></Field>
      <Field label="Optional exception selector"><input value={draft.exception} onChange={event => update("exception", event.target.value)} placeholder="tag:c:swords"/></Field>
      <Field label="Exception priority"><input type="number" value={draft.exceptionPriority} onChange={event => update("exceptionPriority", Number(event.target.value))}/></Field>
    </div></section>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary" disabled={!draft.selector}>{rule ? "Update rule" : "Add rule"}</Button></footer>
  </form>;
}

function RuleCard({ stage, rule, index, total, onEdit, onDelete, onMove }:
  { stage: StagePackage; rule: RuleModel; index: number; total: number; onEdit: () => void; onDelete: () => void; onMove: (direction: number) => void }) {
  const category = CATEGORIES[rule.category];
  return <article className="rule-card-new">
    <div className="rule-card-icon"><Icon name="rules" size={19}/></div>
    <div className="rule-card-main"><div className="rule-card-title"><strong>{category?.label || title(rule.category)}</strong><Badge tone={rule.effect === "allow" || rule.effect === "unlock" ? "success" : "danger"}>{title(rule.effect)}</Badge><Badge>Priority {rule.priority}</Badge></div><code>{rule.selector}</code><p>{ACTION_LABELS[rule.action] || title(rule.action)}. {rule.conditionType === "none" ? "Follows stage ownership." : `Active during ${CONDITIONS.find(value => value.id === rule.conditionType)?.label.toLowerCase() || title(rule.conditionType)}.`}</p></div>
    <div className="rule-card-actions"><button aria-label="Move rule up" disabled={index === 0 || rule.table === "classic"} onClick={() => onMove(-1)}>↑</button><button aria-label="Move rule down" disabled={index === total - 1 || rule.table === "classic"} onClick={() => onMove(1)}>↓</button><Button tone="quiet" onClick={onEdit}>Edit</Button><Button tone="danger" onClick={onDelete}>Remove</Button></div>
  </article>;
}

export function RulesPanel({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, openDialog, closeDialog, runDraftAction } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const rules = useMemo(() => ruleModels(content), [content]);
  const openRule = (rule?: RuleModel) => openDialog({ title: rule ? "Edit rule" : "Create a rule", description: "Build one server decision in plain language.", content: <RuleForm stage={stage} rule={rule}/>, width: "wide" });
  const remove = (rule: RuleModel) => openDialog({ title: "Remove rule", description: "This change remains undoable until it is applied.", content: <div className="confirmation"><p>Remove the rule for <code>{rule.selector}</code> from this stage.</p><footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Keep rule</Button><Button tone="danger" onClick={async () => {
    let updated = content;
    if (rule.table === "classic") updated = removeClassicRule(content, rule);
    else updated = replaceRuleGroup(content, rule.table, rule.tableIndex, null);
    await mutateFile(stage.rulesPath, updated, "The rule was removed"); closeDialog();
  }}>Remove rule</Button></footer></div>, width: "compact" });
  const move = async (rule: RuleModel, direction: number) => {
    if (rule.table === "classic") return;
    const groups = ruleGroups(content, rule.table);
    const target = rule.tableIndex + direction;
    if (target < 0 || target >= groups.length) return;
    const values = groups.map(group => group.text);
    [values[rule.tableIndex], values[target]] = [values[target], values[rule.tableIndex]];
    let updated = content;
    for (let index = groups.length - 1; index >= 0; index--) updated = replaceRuleGroup(updated, rule.table, index, null);
    for (const value of values) updated = appendTomlBlock(updated, value);
    await mutateFile(stage.rulesPath, updated, "Rule order saved");
  };
  const simulate = () => openDialog({ title: "Simulate a candidate decision", description: "Ask the server how the current draft resolves a category and target.", content: <SimulationForm run={runDraftAction}/>, width: "standard" });
  return <div className="stage-panel-stack">
    <Section title="Rules" description={`${rules.length} active decision${rules.length === 1 ? "" : "s"}. Highest priority wins when several rules match.`} action={<div className="section-actions"><Button onClick={simulate}>Simulate</Button><Button tone="primary" icon="plus" onClick={() => openRule()}>Add rule</Button></div>}>
      <div className="rule-primer"><Icon name="spark" size={22}/><div><strong>Rules combine target, action, result, activation, and priority.</strong><p>Use an exception with a higher priority to carve content out of a broad lock. Temporary rules participate only while their condition and lifetime are active.</p></div></div>
      {rules.length ? <div className="rule-list-new">{rules.map((rule, index) => <RuleCard key={`${rule.table}:${rule.tableIndex}:${rule.selector}`} stage={stage} rule={rule} index={index} total={rules.length} onEdit={() => openRule(rule)} onDelete={() => remove(rule)} onMove={direction => void move(rule, direction)}/>)}</div>
        : <EmptyState icon="rules" title="No rules currently active" description="Add a rule, choose a category, then select content from the running server registry." action={<Button tone="primary" icon="plus" onClick={() => openRule()}>Create the first rule</Button>}/>}
    </Section>
  </div>;
}

function SimulationForm({ run }: { run: <T>(payload: Record<string, unknown>, message?: string) => Promise<T> }) {
  const { closeDialog, notify } = useEditor();
  const [category, setCategory] = useState("items");
  const [action, setAction] = useState("use");
  const [target, setTarget] = useState("minecraft:diamond");
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const result = await run<{ explanation: string }>({ action: "simulate", category: `${category}.${action}`, target });
    notify("info", "Candidate simulation", result.explanation);
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={submit}><div className="form-grid"><Field label="Category"><select value={category} onChange={event => { setCategory(event.target.value); setAction(CATEGORIES[event.target.value].actions[0]); }}>{Object.entries(CATEGORIES).map(([id, value]) => <option key={id} value={id}>{value.label}</option>)}</select></Field><Field label="Action"><select value={action} onChange={event => setAction(event.target.value)}>{CATEGORIES[category].actions.map(value => <option key={value} value={value}>{ACTION_LABELS[value] || title(value)}</option>)}</select></Field><Field label="Target identifier" wide><input value={target} onChange={event => setTarget(event.target.value)}/></Field></div><footer className="dialog-actions"><Button tone="quiet" type="button" onClick={closeDialog}>Cancel</Button><Button tone="primary" type="submit">Run simulation</Button></footer></form>;
}
