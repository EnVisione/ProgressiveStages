import { useMemo, useState } from "react";
import { InlineCatalogSearch } from "../../components/CatalogPicker";
import { Badge, Button, EmptyState, Field, Section, Toggle } from "../../components/ui";
import { CONDITIONS } from "../../data";
import { featureCounts, selectorMode, title } from "../../lib/model";
import {
  appendTomlBlock, conditionToml, encodeToml, extractArrayGroups, lineValues, parseSimpleArray, readBlockValue,
  readTomlValue, removeTomlSection, replaceArrayGroups, stringValue, upsertToml
} from "../../lib/toml";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";

const ABILITIES = [
  ["jump", "Jumping"], ["sprint", "Sprinting"], ["swim", "Swimming"],
  ["climb", "Climbing"], ["elytra", "Elytra flight"]
] as const;

function RewardsForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.progressionPath] || "";
  const [items, setItems] = useState(parseSimpleArray(readTomlValue(content, "rewards.items")).join("\n"));
  const [effects, setEffects] = useState(parseSimpleArray(readTomlValue(content, "rewards.effects")).join("\n"));
  const [commands, setCommands] = useState(parseSimpleArray(readTomlValue(content, "rewards.commands")).join("\n"));
  const [levels, setLevels] = useState(Number(stringValue(readTomlValue(content, "rewards.xp_levels"))) || 0);
  const [points, setPoints] = useState(Number(stringValue(readTomlValue(content, "rewards.xp_points"))) || 0);
  const [teleport, setTeleport] = useState(stringValue(readTomlValue(content, "rewards.teleport")));
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    let updated = content;
    updated = upsertToml(updated, "rewards.items", lineValues(items));
    updated = upsertToml(updated, "rewards.effects", lineValues(effects));
    updated = upsertToml(updated, "rewards.commands", commands.split(/\r?\n/).map(value => value.trim()).filter(Boolean));
    updated = upsertToml(updated, "rewards.xp_levels", Math.max(0, levels));
    updated = upsertToml(updated, "rewards.xp_points", Math.max(0, points));
    updated = upsertToml(updated, "rewards.teleport", teleport.trim());
    await mutateFile(stage.progressionPath, updated, "Stage rewards saved");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}>
    <div className="form-grid">
      <Field wide label="Items and amounts" help="One per line. Use minecraft:diamond:5 for five diamonds."><textarea rows={5} value={items} onChange={event => setItems(event.target.value)} placeholder="minecraft:diamond:5"/></Field>
      <Field wide label="Status effects" help="One per line using effect, duration, and amplifier."><textarea rows={4} value={effects} onChange={event => setEffects(event.target.value)} placeholder="minecraft:strength:60:1"/></Field>
      <Field wide label="Server commands" help="One command per line. The player placeholder is supported by the runtime."><textarea rows={4} value={commands} onChange={event => setCommands(event.target.value)} placeholder="give {player} minecraft:cake 1"/></Field>
      <Field label="Experience levels"><input type="number" min={0} value={levels} onChange={event => setLevels(Number(event.target.value))}/></Field>
      <Field label="Experience points"><input type="number" min={0} value={points} onChange={event => setPoints(Number(event.target.value))}/></Field>
      <Field wide label="Teleport destination" help="Dimension followed by coordinates."><input value={teleport} onChange={event => setTeleport(event.target.value)} placeholder="minecraft:the_end 0 80 0"/></Field>
    </div>
    <footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Save rewards</Button></footer>
  </form>;
}

function AbilitiesForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const existing = parseSimpleArray(readTomlValue(content, "abilities.locked"));
  const [selected, setSelected] = useState(new Set(existing));
  const [custom, setCustom] = useState(existing.filter(value => !ABILITIES.some(([id]) => id === value)).join("\n"));
  const save = async () => {
    const values = [...selected].filter(value => ABILITIES.some(([id]) => id === value)).concat(lineValues(custom));
    await mutateFile(stage.rulesPath, upsertToml(content, "abilities.locked", [...new Set(values)]), "Ability restrictions saved");
    closeDialog();
  };
  return <div className="dialog-form">
    <div className="choice-grid">{ABILITIES.map(([id, label]) => <Toggle key={id} label={`Lock ${label.toLowerCase()}`} help={`Players need ${stage.name} before ${label.toLowerCase()} is permitted.`} checked={selected.has(id)} onChange={checked => setSelected(current => { const next = new Set(current); if (checked) next.add(id); else next.delete(id); return next; })}/>)}</div>
    <Field wide label="Custom registered abilities" help="One identifier per line for abilities supplied by extensions or KubeJS."><textarea rows={4} value={custom} onChange={event => setCustom(event.target.value)}/></Field>
    <footer className="dialog-actions"><Button tone="quiet" onClick={closeDialog}>Cancel</Button><Button tone="primary" onClick={() => void save()}>Save abilities</Button></footer>
  </div>;
}

function AttributeForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [id, setId] = useState("minecraft:generic.max_health");
  const [amount, setAmount] = useState(2);
  const [operation, setOperation] = useState("add_value");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const block = [`[[attribute]]`, `id = ${encodeToml(id.trim())}`, `amount = ${amount}`, `operation = ${encodeToml(operation)}`].join("\n");
    await mutateFile(stage.stagePath, appendTomlBlock(boot?.draft.files[stage.stagePath] || "", block), "Stage attribute added");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid">
    <Field label="Attribute identifier"><input value={id} onChange={event => setId(event.target.value)} required/></Field>
    <Field label="Amount"><input type="number" step="any" value={amount} onChange={event => setAmount(Number(event.target.value))}/></Field>
    <Field label="Operation"><select value={operation} onChange={event => setOperation(event.target.value)}><option value="add_value">Add a flat value</option><option value="add_multiplied_base">Multiply the base value</option><option value="add_multiplied_total">Multiply the final value</option></select></Field>
  </div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add attribute</Button></footer></form>;
}

function ItemModifierForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [item, setItem] = useState("id:minecraft:diamond_sword");
  const [context, setContext] = useState("either_hand");
  const [kind, setKind] = useState("attribute");
  const [id, setId] = useState("minecraft:generic.attack_damage");
  const [amount, setAmount] = useState(1);
  const [operation, setOperation] = useState("add_value");
  const [duration, setDuration] = useState(40);
  const [priority, setPriority] = useState(0);
  const [aggregation, setAggregation] = useState("once");
  const [cap, setCap] = useState(1);
  const [withStages, setWithStages] = useState("");
  const [withoutStages, setWithoutStages] = useState("");
  const [conditionType, setConditionType] = useState("none");
  const [conditionTarget, setConditionTarget] = useState("");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const lines = ["[[item_modifiers]]", `id = ${encodeToml(`${stage.id}/modifier_${Date.now().toString(36)}`)}`, `items = ${encodeToml([item])}`, `contexts = ${encodeToml([context])}`, `priority = ${priority}`, `aggregation = ${encodeToml(aggregation)}`, `cap = ${Math.max(1, cap)}`];
    if (lineValues(withStages).length) lines.push(`with_stages = ${encodeToml(lineValues(withStages))}`);
    if (lineValues(withoutStages).length) lines.push(`without_stages = ${encodeToml(lineValues(withoutStages))}`);
    if (conditionType !== "none") lines.push(`condition = ${conditionToml(conditionType, conditionTarget, 1)}`);
    if (kind === "attribute") lines.push("", "[[item_modifiers.attributes]]", `id = ${encodeToml(id)}`, `amount = ${amount}`, `operation = ${encodeToml(operation)}`);
    else lines.push("", "[[item_modifiers.effects]]", `id = ${encodeToml(id)}`, `amplifier = ${Math.max(0, amount)}`, `duration_ticks = ${Math.max(1, duration)}`);
    await mutateFile(stage.rulesPath, appendTomlBlock(boot?.draft.files[stage.rulesPath] || "", lines.join("\n")), "Item modifier added");
    closeDialog();
  };
  return <form className="dialog-form" onSubmit={save}><div className="form-grid">
    <Field wide label="Item selector"><input value={item} onChange={event => setItem(event.target.value)} required/></Field>
    <div className="field-wide"><InlineCatalogSearch catalogId="items" mode={selectorMode(item)} onPick={setItem}/></div>
    <Field label="When the item is"><select value={context} onChange={event => setContext(event.target.value)}><option value="either_hand">Held in either hand</option><option value="main_hand">In the main hand</option><option value="off_hand">In the off hand</option><option value="selected_hotbar">Selected in the hotbar</option><option value="inventory">Anywhere in inventory</option><option value="equipment">Worn as equipment</option><option value="curios">In a Curios slot</option><option value="use">Being used</option><option value="attack">Attacking</option></select></Field>
    <Field label="Modifier kind"><select value={kind} onChange={event => setKind(event.target.value)}><option value="attribute">Attribute</option><option value="effect">Status effect</option></select></Field>
    <Field label={kind === "attribute" ? "Attribute identifier" : "Effect identifier"}><input value={id} onChange={event => setId(event.target.value)} required/></Field>
    <Field label={kind === "attribute" ? "Amount" : "Amplifier"}><input type="number" step="any" value={amount} onChange={event => setAmount(Number(event.target.value))}/></Field>
    {kind === "attribute" ? <Field label="Operation"><select value={operation} onChange={event => setOperation(event.target.value)}><option value="add_value">Add a flat value</option><option value="add_multiplied_base">Multiply the base</option><option value="add_multiplied_total">Multiply the total</option></select></Field> : <Field label="Duration in ticks"><input type="number" min={1} value={duration} onChange={event => setDuration(Number(event.target.value))}/></Field>}
    <Field label="Priority"><input type="number" value={priority} onChange={event => setPriority(Number(event.target.value))}/></Field>
    <Field label="Stacking behavior"><select value={aggregation} onChange={event => setAggregation(event.target.value)}><option value="once">Apply once</option><option value="per_item">Apply for every matching item</option><option value="highest">Use highest matching value</option><option value="sum">Combine matching values</option></select></Field>
    <Field label="Maximum stacks"><input type="number" min={1} value={cap} onChange={event => setCap(Number(event.target.value))}/></Field>
    <Field wide label="Extra stages that must be owned"><textarea rows={2} value={withStages} onChange={event => setWithStages(event.target.value)} placeholder="One stage identifier per line"/></Field>
    <Field wide label="Stages that must be missing"><textarea rows={2} value={withoutStages} onChange={event => setWithoutStages(event.target.value)} placeholder="One stage identifier per line"/></Field>
    <Field label="Additional condition"><select value={conditionType} onChange={event => setConditionType(event.target.value)}>{CONDITIONS.map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field>
    {conditionType !== "none" ? <Field label="Condition target"><input value={conditionTarget} onChange={event => setConditionTarget(event.target.value)}/></Field> : null}
  </div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add modifier</Button></footer></form>;
}

function DropModifierForm({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile, closeDialog } = useEditor();
  const [block, setBlock] = useState("id:minecraft:diamond_ore");
  const [drop, setDrop] = useState("id:minecraft:diamond");
  const [tool, setTool] = useState("tag:minecraft:mineable/pickaxe");
  const [enchantment, setEnchantment] = useState("minecraft:fortune");
  const [level, setLevel] = useState(1);
  const [multiply, setMultiply] = useState(2);
  const [add, setAdd] = useState(0);
  const [priority, setPriority] = useState(500);
  const [exclusive, setExclusive] = useState(true);
  const [minimum, setMinimum] = useState(0);
  const [maximum, setMaximum] = useState(64);
  const [withStages, setWithStages] = useState(stage.id);
  const [withoutStages, setWithoutStages] = useState("");
  const [conditionType, setConditionType] = useState("none");
  const [conditionTarget, setConditionTarget] = useState("");
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const lines = ["[[drop_modifiers]]", `id = ${encodeToml(`${stage.id}/drop_${Date.now().toString(36)}`)}`, `blocks = ${encodeToml([block])}`, `drops = ${encodeToml([drop])}`];
    if (tool.trim()) lines.push(`tools = ${encodeToml([tool])}`);
    if (enchantment.trim()) lines.push(`required_enchantment = ${encodeToml(enchantment.trim())}`, `minimum_enchantment_level = ${Math.max(0, level)}`);
    if (lineValues(withStages).length) lines.push(`with_stages = ${encodeToml(lineValues(withStages))}`);
    if (lineValues(withoutStages).length) lines.push(`without_stages = ${encodeToml(lineValues(withoutStages))}`);
    if (conditionType !== "none") lines.push(`condition = ${conditionToml(conditionType, conditionTarget, 1)}`);
    lines.push(`add = ${add}`, `multiply = ${Math.max(0, multiply)}`, `minimum = ${Math.max(0, minimum)}`, `maximum = ${Math.max(minimum, maximum)}`, `priority = ${priority}`, `exclusive = ${exclusive}`);
    await mutateFile(stage.rulesPath, appendTomlBlock(boot?.draft.files[stage.rulesPath] || "", lines.join("\n")), "Targeted drop modifier added");
    closeDialog();
  };
  const selector = (label: string, value: string, setValue: (value: string) => void, catalog: string) => <><Field wide label={label}><input value={value} onChange={event => setValue(event.target.value)} required/></Field><div className="field-wide"><InlineCatalogSearch catalogId={catalog} mode={selectorMode(value)} onPick={setValue}/></div></>;
  return <form className="dialog-form" onSubmit={save}><div className="form-grid">
    {selector("Source block", block, setBlock, "blocks")}{selector("Output item", drop, setDrop, "items")}{selector("Required tool", tool, setTool, "items")}
    <Field label="Required enchantment"><input value={enchantment} onChange={event => setEnchantment(event.target.value)}/></Field>
    <Field label="Minimum enchantment level"><input type="number" min={0} value={level} onChange={event => setLevel(Number(event.target.value))}/></Field>
    <Field label="Add before multiplying"><input type="number" value={add} onChange={event => setAdd(Number(event.target.value))}/></Field>
    <Field label="Multiply final drops by"><input type="number" min={0} step="0.1" value={multiply} onChange={event => setMultiply(Number(event.target.value))}/></Field>
    <Field label="Minimum final count"><input type="number" min={0} value={minimum} onChange={event => setMinimum(Number(event.target.value))}/></Field>
    <Field label="Maximum final count"><input type="number" min={0} value={maximum} onChange={event => setMaximum(Number(event.target.value))}/></Field>
    <Field wide label="Stages that must be owned"><textarea rows={2} value={withStages} onChange={event => setWithStages(event.target.value)}/></Field>
    <Field wide label="Stages that must be missing"><textarea rows={2} value={withoutStages} onChange={event => setWithoutStages(event.target.value)}/></Field>
    <Field label="Additional condition"><select value={conditionType} onChange={event => setConditionType(event.target.value)}>{CONDITIONS.map(value => <option key={value.id} value={value.id}>{value.label}</option>)}</select></Field>
    {conditionType !== "none" ? <Field label="Condition target"><input value={conditionTarget} onChange={event => setConditionTarget(event.target.value)}/></Field> : null}
    <Field label="Priority"><input type="number" value={priority} onChange={event => setPriority(Number(event.target.value))}/></Field>
    <Toggle label="Stop lower priority bonuses" checked={exclusive} onChange={setExclusive}/>
  </div><footer className="dialog-actions"><Button type="button" tone="quiet" onClick={closeDialog}>Cancel</Button><Button type="submit" tone="primary">Add mining bonus</Button></footer></form>;
}

function ConfiguredBlocks({ stage, table, titleText }: { stage: StagePackage; table: string; titleText: string }) {
  const { boot, mutateFile } = useEditor();
  const content = boot?.draft.files[stage.rulesPath] || "";
  const blocks = useMemo(() => extractArrayGroups(content, table), [content, table]);
  if (!blocks.length) return null;
  return <div className="configured-list">{blocks.map((block, index) => <article key={`${table}:${index}`}><div><Badge tone="gold">{titleText}</Badge><strong>{stringValue(readBlockValue(block.text, "id")) || `${titleText} ${index + 1}`}</strong><code>{parseSimpleArray(readBlockValue(block.text, table === "drop_modifiers" ? "blocks" : "items")).join(", ")}</code></div><Button tone="danger" onClick={() => { const next = blocks.map(value => value.text); next.splice(index, 1); void mutateFile(stage.rulesPath, replaceArrayGroups(content, table, next), `${titleText} removed`); }}>Remove</Button></article>)}</div>;
}

export function EffectsPanel({ stage }: { stage: StagePackage }) {
  const { boot, openDialog, mutateFile } = useEditor();
  const files = boot?.draft.files || {};
  const counts = featureCounts(stage, files);
  const progression = files[stage.progressionPath] || "";
  const rewardCount = parseSimpleArray(readTomlValue(progression, "rewards.items")).length
    + parseSimpleArray(readTomlValue(progression, "rewards.effects")).length
    + parseSimpleArray(readTomlValue(progression, "rewards.commands")).length;
  const open = (titleText: string, description: string, content: React.ReactNode) => openDialog({ title: titleText, description, content, width: "wide" });
  return <div className="stage-panel-stack">
    <Section title="Rewards" description="Give items, effects, experience, commands, or a teleport when the stage is granted." action={<Button tone="primary" icon="gift" onClick={() => open("Stage rewards", "Rewards run only after a genuine server authoritative grant.", <RewardsForm stage={stage}/>)}>Configure rewards</Button>}>
      <div className="feature-summary"><span className="feature-symbol">✦</span><div><strong>{rewardCount ? `${rewardCount} reward action${rewardCount === 1 ? "" : "s"} configured` : "No grant rewards configured"}</strong><p>Item amounts, effects, commands, experience, and teleport destination remain independently editable.</p></div>{rewardCount ? <Button tone="danger" onClick={() => void mutateFile(stage.progressionPath, removeTomlSection(progression, "rewards"), "Stage rewards removed")}>Clear rewards</Button> : null}</div>
    </Section>
    <Section title="Player abilities" description="Server enforced movement and action restrictions that remain synchronized with the client." action={<Button onClick={() => open("Ability restrictions", "Choose every player ability that remains locked until this stage is owned.", <AbilitiesForm stage={stage}/>)}>Choose abilities</Button>}>
      <div className="chip-row">{parseSimpleArray(readTomlValue(files[stage.rulesPath] || "", "abilities.locked")).map(value => <Badge key={value} tone="gold">{title(value)}</Badge>)}{counts.abilities === 0 ? <span className="muted-copy">No abilities are gated by this stage.</span> : null}</div>
    </Section>
    <Section title="Attributes and contextual modifiers" description="Apply a permanent stage attribute or a bonus tied to a matching held, worn, or carried item." action={<div className="section-actions"><Button onClick={() => open("Add a stage attribute", "This attribute applies while the player owns the stage.", <AttributeForm stage={stage}/>)}>Stage attribute</Button><Button tone="primary" onClick={() => open("Add an item modifier", "Combine a selector, inventory context, attribute or status effect, and priority.", <ItemModifierForm stage={stage}/>)}>Item modifier</Button></div>}>
      <div className="metric-strip"><div><strong>{counts.attributes}</strong><span>Stage attributes</span></div><div><strong>{counts.modifiers}</strong><span>Item modifiers</span></div></div>
      <ConfiguredBlocks stage={stage} table="item_modifiers" titleText="Item modifier"/>
    </Section>
    <Section title="Targeted drops" description="Create Fortune style bonuses that match the source block, output, tool, enchantment, and stage." action={<Button tone="primary" icon="plus" onClick={() => open("Add a targeted mining bonus", "The final item count changes only when every configured selector matches.", <DropModifierForm stage={stage}/>)}>Add drop modifier</Button>}>
      {counts.dropModifiers ? <ConfiguredBlocks stage={stage} table="drop_modifiers" titleText="Drop modifier"/> : <EmptyState icon="spark" title="No targeted drop modifiers" description="Use this for upgrades such as Diamond Engineer without changing unrelated block drops."/>}
    </Section>
  </div>;
}
