import { useMemo, useState } from "react";
import { InlineCatalogSearch } from "../../components/CatalogPicker";
import { Badge, Button, EmptyState, Field, Section, Toggle } from "../../components/ui";
import { Icon } from "../../components/Icon";
import { CONDITIONS } from "../../data";
import { progressionModels, purchaseSummary, title } from "../../lib/model";
import { appendTomlBlock, conditionToml, encodeToml, extractArrayBlocks, readBlockValue, removeTomlSection, replaceArrayBlocks, stringValue, upsertToml } from "../../lib/toml";
import { useEditor } from "../../store/EditorContext";
import type { ProgressionModel, StagePackage } from "../../types";

function serializeProgression(stage: StagePackage, model: Omit<ProgressionModel, "tableIndex" | "sourceText">,
                              previous?: ProgressionModel): string {
  const oldId = previous ? stringValue(readBlockValue(previous.sourceText, "id")) : "";
  const id = oldId || `${stage.id}/${model.kind === "grants" ? "grant" : "revoke"}_${Date.now().toString(36)}`;
  const lines = [
    `[[${model.kind}]]`,
    `id = ${encodeToml(id)}`,
    `repeat = ${encodeToml(model.repeat)}`,
    `scope = ${encodeToml(model.scope)}`,
    `priority = ${model.priority}`,
    `condition = ${conditionToml(model.conditionType, model.conditionTarget, model.count)}`
  ];
  if (model.cooldown) lines.push(`cooldown = ${encodeToml(model.cooldown)}`);
  return lines.join("\n");
}

function ProgressionForm({ stage, entry, defaultKind }:
  { stage: StagePackage; entry?: ProgressionModel; defaultKind: "grants" | "revokes" }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [kind, setKind] = useState<"grants" | "revokes">(entry?.kind || defaultKind);
  const [conditionType, setConditionType] = useState(entry?.conditionType || (defaultKind === "revokes" ? "death" : "kill"));
  const [conditionTarget, setConditionTarget] = useState(entry?.conditionTarget || "");
  const [count, setCount] = useState(entry?.count || 1);
  const [repeat, setRepeat] = useState(entry?.repeat || "once");
  const [scope, setScope] = useState(entry?.scope || "player");
  const [priority, setPriority] = useState(entry?.priority || 0);
  const [cooldown, setCooldown] = useState(entry?.cooldown || "");
  const condition = CONDITIONS.find(value => value.id === conditionType);
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    let content = boot?.draft.files[stage.progressionPath] || "";
    const block = serializeProgression(stage, { kind, conditionType, conditionTarget, count, repeat, scope, priority, cooldown }, entry);
    if (entry) {
      const blocks = extractArrayBlocks(content, entry.kind).map(value => value.text);
      if (!blocks[entry.tableIndex]) throw new Error("This progression entry changed. Reopen it and try again.");
      blocks.splice(entry.tableIndex, 1);
      content = replaceArrayBlocks(content, entry.kind, blocks);
    }
    content = appendTomlBlock(content, block);
    await mutateFile(stage.progressionPath, content, kind === "grants" ? "The obtain trigger was saved" : "The revoke trigger was saved");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}>
    <div className="progression-direction"><button type="button" className={kind === "grants" ? "active" : ""} onClick={() => setKind("grants")}><span>↟</span><strong>Grant this stage</strong><small>The player gains ownership.</small></button><button type="button" className={kind === "revokes" ? "active" : ""} onClick={() => setKind("revokes")}><span>↡</span><strong>Revoke this stage</strong><small>The player loses ownership.</small></button></div>
    <section className="dialog-section"><header><span className="step-number">1</span><div><h3>Choose the trigger</h3><p>Grants and revokes use the same complete event and condition library.</p></div></header><div className="form-grid">
      <Field label="Trigger"><select value={conditionType} onChange={event => setConditionType(event.target.value)}>{CONDITIONS.filter(value => value.id !== "none").map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field>
      <Field label="Trigger target" help={condition?.help}><input value={conditionTarget} onChange={event => setConditionTarget(event.target.value)} placeholder={condition?.catalog ? "Registered identifier" : "Optional value"}/></Field>
      {condition?.catalog ? <div className="field-wide"><InlineCatalogSearch catalogId={condition.catalog} mode="id" onPick={value => setConditionTarget(value.replace(/^id:/, ""))}/></div> : null}
      <Field label="Required amount"><input type="number" min={1} value={count} onChange={event => setCount(Number(event.target.value))}/></Field>
    </div></section>
    <section className="dialog-section"><header><span className="step-number">2</span><div><h3>Progress and repetition</h3><p>Choose who owns the counter and how often the result may happen.</p></div></header><div className="form-grid">
      <Field label="Progress owner"><select value={scope} onChange={event => setScope(event.target.value)}><option value="player">Each player</option><option value="team">Whole team</option><option value="server">Whole server</option></select></Field>
      <Field label="Repeat policy"><select value={repeat} onChange={event => setRepeat(event.target.value)}><option value="once">Once</option><option value="edge">Whenever it becomes true</option><option value="always">Every successful evaluation</option></select></Field>
      <Field label="Priority"><input type="number" value={priority} onChange={event => setPriority(Number(event.target.value))}/></Field>
      <Field label="Cooldown"><input value={cooldown} onChange={event => setCooldown(event.target.value)} placeholder="Optional, such as 30s"/></Field>
    </div></section>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Save {kind === "grants" ? "obtain" : "revoke"} trigger</Button></footer>
  </form>;
}

interface CostItem { id: string; count: number }

function parseCost(value: string): CostItem | null {
  const match = value.trim().match(/^(.*):(\d+)$/);
  const id = match ? match[1] : value.trim();
  return id.includes(":") ? { id, count: Math.max(1, Number(match?.[2] || 1)) } : null;
}

function PurchaseForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.progressionPath] || "";
  const current = purchaseSummary(content);
  const [items, setItems] = useState<CostItem[]>(() => current.items.map(parseCost).filter((value): value is CostItem => Boolean(value)));
  const [xpLevels, setXpLevels] = useState(current.xpLevels);
  const [xpPoints, setXpPoints] = useState(current.xpPoints);
  const [cooldown, setCooldown] = useState(current.cooldown || "2s");
  const [refund, setRefund] = useState(current.refund);
  const [bypass, setBypass] = useState(current.bypass);
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!items.length && !xpLevels && !xpPoints) return;
    let updated = content;
    updated = upsertToml(updated, "cost.items", items.map(item => `${item.id}:${Math.max(1, item.count)}`));
    updated = upsertToml(updated, "cost.xp_levels", Math.max(0, xpLevels));
    updated = upsertToml(updated, "cost.xp_points", Math.max(0, xpPoints));
    updated = upsertToml(updated, "cost.cooldown", cooldown);
    updated = upsertToml(updated, "cost.refund_percent", Math.min(100, Math.max(0, refund)));
    updated = upsertToml(updated, "cost.bypass_requirements", bypass);
    await mutateFile(stage.progressionPath, updated, `${stage.name} can now be purchased`);
    closeDialog();
  };
  const addItem = (value: string) => {
    const id = value.replace(/^id:/, "");
    setItems(currentItems => {
      const existing = currentItems.find(item => item.id === id);
      return existing ? currentItems.map(item => item.id === id ? { ...item, count: item.count + 1 } : item) : [...currentItems, { id, count: 1 }];
    });
  };
  return <form className="dialog-form" onSubmit={save}>
    <section className="dialog-section"><header><span className="step-number">1</span><div><h3>Item payment</h3><p>Players give these exact items to the authoritative server.</p></div></header>
      <div className="cost-items">{items.length ? items.map((item, index) => <div key={item.id} className="cost-item"><span className="catalog-cube">◆</span><span><strong>{title(item.id.split(":").pop() || item.id)}</strong><code>{item.id}</code></span><Field label="Amount"><input type="number" min={1} value={item.count} onChange={event => setItems(currentItems => currentItems.map((value, itemIndex) => itemIndex === index ? { ...value, count: Number(event.target.value) } : value))}/></Field><Button type="button" tone="danger" onClick={() => setItems(currentItems => currentItems.filter((_, itemIndex) => itemIndex !== index))}>Remove</Button></div>) : <EmptyState icon="gift" title="No item payment" description="Search the registry and select one or more payment items."/>}</div>
      <InlineCatalogSearch catalogId="items" mode="id" onPick={addItem}/>
    </section>
    <section className="dialog-section"><header><span className="step-number">2</span><div><h3>Purchase behavior</h3><p>Experience, cooldown, refunds, and prerequisites are checked by the server.</p></div></header><div className="form-grid">
      <Field label="Extra XP levels"><input type="number" min={0} value={xpLevels} onChange={event => setXpLevels(Number(event.target.value))}/></Field>
      <Field label="Extra XP points"><input type="number" min={0} value={xpPoints} onChange={event => setXpPoints(Number(event.target.value))}/></Field>
      <Field label="Purchase cooldown"><input value={cooldown} onChange={event => setCooldown(event.target.value)} placeholder="2s"/></Field>
      <Field label="Refund after revoke" help="Percent of item and experience payment returned."><input type="number" min={0} max={100} value={refund} onChange={event => setRefund(Number(event.target.value))}/></Field>
      <Toggle label="Allow payment before trigger requirements" help="Required stage dependencies still apply." checked={bypass} onChange={setBypass}/>
    </div></section>
    <footer className="dialog-actions">{current.enabled ? <Button type="button" tone="danger" onClick={async () => { await mutateFile(stage.progressionPath, removeTomlSection(content, "cost"), "The purchase option was removed"); closeDialog(); }}>Remove purchase</Button> : null}<span className="dialog-spacer"/><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary" disabled={!items.length && !xpLevels && !xpPoints}>Save purchase</Button></footer>
  </form>;
}

function ProgressionCard({ entry, onEdit, onDelete }: { entry: ProgressionModel; onEdit: () => void; onDelete: () => void }) {
  const definition = CONDITIONS.find(value => value.id === entry.conditionType);
  return <article className="progression-card-new"><span className={`direction-icon ${entry.kind}`}>{entry.kind === "grants" ? "↟" : "↡"}</span><div><div className="progression-title"><strong>{entry.kind === "grants" ? "Grant stage" : "Revoke stage"}</strong><Badge tone={entry.kind === "grants" ? "success" : "danger"}>{definition?.label || title(entry.conditionType)}</Badge></div><code>{entry.conditionTarget || "No target required"}</code><p>{entry.count} required. {title(entry.repeat)}. {title(entry.scope)} progress.</p></div><div className="rule-card-actions"><Button tone="quiet" onClick={onEdit}>Edit</Button><Button tone="danger" onClick={onDelete}>Remove</Button></div></article>;
}

export function ProgressionPanel({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, openDialog, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.progressionPath] || "";
  const entries = useMemo(() => progressionModels(content), [content]);
  const grants = entries.filter(entry => entry.kind === "grants");
  const revokes = entries.filter(entry => entry.kind === "revokes");
  const cost = purchaseSummary(content);
  const openEntry = (kind: "grants" | "revokes", entry?: ProgressionModel) => openDialog({
    title: entry ? "Edit progression trigger" : kind === "grants" ? "Add a way to obtain this stage" : "Add a way to lose this stage",
    description: "Choose the event, target, amount, owner, repeat policy, priority, and cooldown.",
    content: <ProgressionForm stage={stage} entry={entry} defaultKind={kind}/>, width: "wide"
  });
  const remove = (entry: ProgressionModel) => openDialog({ title: "Remove progression trigger", content: <div className="confirmation"><p>Remove the <strong>{CONDITIONS.find(value => value.id === entry.conditionType)?.label || entry.conditionType}</strong> trigger from this stage.</p><footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Keep trigger</Button><Button tone="danger" onClick={async () => {
    const blocks = extractArrayBlocks(content, entry.kind).map(block => block.text);
    blocks.splice(entry.tableIndex, 1);
    await mutateFile(stage.progressionPath, replaceArrayBlocks(content, entry.kind, blocks), "The progression trigger was removed"); closeDialog();
  }}>Remove trigger</Button></footer></div>, width: "compact" });
  return <div className="stage-panel-stack">
    <Section title="How players obtain this stage" description="Mix automatic gameplay, item purchase, quests, commands, APIs, and KubeJS." action={<Button tone="primary" icon="plus" onClick={() => openEntry("grants")}>Add obtain trigger</Button>}>
      <div className="acquisition-options">
        <button className={cost.enabled ? "configured" : ""} onClick={() => openDialog({ title: `Purchase ${stage.name}`, description: "Configure an atomic server authoritative payment.", content: <PurchaseForm stage={stage}/>, width: "wide" })}><span><Icon name="gift" size={21}/></span><div><strong>Buy with items or experience</strong><p>{cost.enabled ? `${cost.items.length} item cost${cost.items.length === 1 ? "" : "s"}${cost.cooldown ? ` with ${cost.cooldown} cooldown` : ""}.` : "Not configured. Add a searchable item payment."}</p></div><Badge tone={cost.enabled ? "success" : "neutral"}>{cost.enabled ? "Configured" : "Optional"}</Badge></button>
        <div><span><Icon name="progression" size={21}/></span><div><strong>Earn through gameplay</strong><p>{grants.length ? `${grants.length} automatic trigger${grants.length === 1 ? "" : "s"} configured.` : "Kills, mining, crafting, exploration, events, and scripts."}</p></div><Badge tone={grants.length ? "success" : "neutral"}>{grants.length}</Badge></div>
        <div><span><Icon name="code" size={21}/></span><div><strong>Quest, command, or API</strong><p>FTB Quests, `/pstages`, KubeJS, and the Java API can always grant it.</p></div><Badge>Always available</Badge></div>
      </div>
      {grants.length ? <div className="progression-list-new">{grants.map(entry => <ProgressionCard key={`${entry.kind}:${entry.tableIndex}`} entry={entry} onEdit={() => openEntry("grants", entry)} onDelete={() => remove(entry)}/>)}</div> : <EmptyState icon="progression" title="No automatic obtain triggers" description="Players can still receive the stage through commands, quests, scripts, or the API." action={<Button tone="primary" onClick={() => openEntry("grants")}>Add gameplay trigger</Button>}/>}
    </Section>
    <Section title="How players lose this stage" description="Revocation uses the same trigger library and can respond to death, events, structures, time, and scripts." action={<Button tone="primary" icon="plus" onClick={() => openEntry("revokes")}>Add revoke trigger</Button>}>
      {revokes.length ? <div className="progression-list-new">{revokes.map(entry => <ProgressionCard key={`${entry.kind}:${entry.tableIndex}`} entry={entry} onEdit={() => openEntry("revokes", entry)} onDelete={() => remove(entry)}/>)}</div> : <EmptyState icon="progression" title="This stage is never automatically revoked" description="Add a revoke trigger for temporary powers, session stages, death penalties, or changing world state." action={<Button onClick={() => openEntry("revokes")}>Add revoke trigger</Button>}/>}
    </Section>
  </div>;
}
