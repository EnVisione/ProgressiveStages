import { useEffect, useId, useRef, useState } from "react";
import { useEditor } from "../store/EditorContext";
import { Icon } from "./Icon";

function actionHelp(children: React.ReactNode, accessibleName?: string): string | undefined {
  const label = (typeof children === "string" ? children : accessibleName || "").trim();
  if (!label) return undefined;
  if (label === "Cancel") return "Close this form without saving its edits to the draft.";
  if (label === "Close" || label === "Close dialog") return "Close this panel and return to the editor.";
  if (label === "Browse") return "Search the server registry and choose a matching identifier.";
  if (label === "Browse server registry") return "Open the registry picker for IDs, tags, and installed mods.";
  if (label === "Hide registry") return "Close the registry picker and return to this form.";
  if (label === "Copy") return "Copy the shown text to the clipboard.";
  if (label === "Try again") return "Retry the editor connection without changing stage files.";
  if (label === "Validate settings") return "Check the draft settings for errors before applying them to the server.";
  if (label === "Roll back this transaction") return "Restore the server files changed by this editor transaction.";
  if (label === "Confirm and apply to server") return "Apply the reviewed draft to the server after validation.";
  if (label === "Applied to server") return "This reviewed draft has already been applied to the server.";
  if (label === "Move package") return "Choose another stage folder. Review the move before applying it to the server.";
  if (label === "Import package") return "Add a stage package to the draft from a local file.";
  if (label === "Archive") return "Hide this stage from the active draft while keeping it available for restoration.";
  if (label === "Restore stage") return "Return this archived stage to the active draft.";
  if (label === "Delete from draft") return "Remove this stage package from the draft. Review before applying.";
  if (label === "Armor tag") return "Select the armor tag suggested for this rule.";
  if (label === "All items") return "Select every item in the current registry view.";
  if (label === "Classic stage tools") return "Open the older stage tools for this package.";
  if (label === "Choose abilities") return "Choose which player abilities this stage restricts until it is owned.";
  if (label === "Stage attribute") return "Add an attribute change granted by this stage.";
  if (label === "Item modifier") return "Add a modifier that changes a matching item's behavior.";
  if (label === "Run simulation" || label === "Simulate") return "Preview the outcome with sample data. This does not change the draft or a player's progress.";
  if (label === "Discard") return "Discard the current unsaved draft changes.";
  if (label.startsWith("Remove") || label.startsWith("Clear")) return "Remove this entry from the draft. Review and apply before the server changes.";
  if (label.startsWith("Edit") || label.startsWith("Configure")) return "Open the nearby settings for this entry. Changes stay in the draft until you review and apply.";
  if (label.startsWith("Save") || label.startsWith("Update") || label.startsWith("Keep")) return "Save this form to the draft. Review and apply to update the server.";
  if (label.startsWith("Add") || label.startsWith("Create") || label.startsWith("Duplicate")) return "Add this entry to the draft. Review and apply to update the server.";
  return undefined;
}

export function Button({ children, tone = "neutral", icon, help, className = "", ...props }:
  React.ButtonHTMLAttributes<HTMLButtonElement> & { tone?: "neutral" | "primary" | "danger" | "quiet"; icon?: string; help?: string }) {
  const helpId = useId();
  const resolvedHelp = help || props.title || actionHelp(children, props["aria-label"]);
  return <button {...props} title={resolvedHelp} aria-describedby={resolvedHelp ? helpId : props["aria-describedby"]}
    className={`button button-${tone} ${className}`.trim()}>
    {icon ? <Icon name={icon} size={17}/> : null}<span>{children}</span>
    {resolvedHelp ? <span id={helpId} role="tooltip" aria-hidden="true" className="button-help">{resolvedHelp}</span> : null}
  </button>;
}

const BADGE_HELP: Record<string, string> = {
  Valid: "The reviewed draft passed server validation and can be applied.",
  Blocked: "The reviewed draft has errors. Correct them before applying.",
  ADDED: "This file will be created when you apply the draft.",
  MODIFIED: "This existing file will change when you apply the draft.",
  DELETED: "This file will be removed when you apply the draft.",
  "Always available": "This external grant route can be used without a purchase trigger.",
  "Unsaved source": "The source text differs from the current draft. Save it to the draft before review.",
  "Draft synchronized": "The source text matches the current draft.",
  "Connection problem": "The editor cannot currently reach the connected server. Retry after the connection returns; unsaved changes cannot be applied while offline.",
  Inbound: "This mapping receives a stage change from an external source.",
  Tree: "This mapping includes descendants of the selected node.",
  Literal: "This mapping matches only the exact selected node."
};

export function Badge({ children, tone = "neutral", help, focusable = true }: { children: React.ReactNode; tone?: string; help?: string; focusable?: boolean }) {
  const id = useId();
  const label = typeof children === "string" || typeof children === "number" ? String(children) : "";
  const resolvedHelp = help || BADGE_HELP[label]
    || (label.startsWith("Priority ") || label.endsWith(" priority")
      ? "This number controls which overlapping rule wins. Higher numbers are considered first."
      : label.startsWith("Maximum level ")
        ? "This is the highest enchantment level this rule permits."
        : label.startsWith("Roll weight ")
          ? "This number controls how often this entry is selected when several choices are available."
          : `Current value: ${label || "see adjacent details"}. Read the surrounding card for when it applies.`);
  return <span className={`badge badge-${tone}`} tabIndex={focusable ? 0 : -1} aria-describedby={id} title={resolvedHelp}>
    {children}<span id={id} role="tooltip" aria-hidden="true" className="badge-help">{resolvedHelp}</span>
  </span>;
}

export function Hint({ text, children }: { text: string; children: React.ReactNode }) {
  const id = useId();
  return <span className="hint-wrap" tabIndex={0} aria-describedby={id} title={text}>
    {children}<span id={id} role="tooltip" className="hint-popover">{text}</span>
  </span>;
}

const FIELD_HELP: Record<string, string> = {
  "Search": "Type part of a name or identifier to narrow the list. This only filters what you see.",
  "Find a stage": "Search for a stage by its name or ID, then select it from the results. Searching does not change the draft.",
  "Challenge name": "The name players see for this challenge. Use a short phrase that describes the goal.",
  "Start when": "Choose the event that begins this challenge.",
  "Start target": "Limit the start event to this target. Leave it empty when any matching event should count.",
  "Succeed when": "Choose the event that completes this challenge.",
  "Success target": "Limit the success event to this target. Leave it empty when any matching event should count.",
  "End without success when": "Choose an event that stops the challenge without awarding success.",
  "End target": "Limit the unsuccessful end event to this target.",
  "Boss entity filter": "Match a specific boss entity instead of every entity. Use an entity ID or supported selector.",
  "Retries": "How many new attempts players may make after this challenge fails.",
  "HUD placement": "Choose where the challenge progress appears on the player's screen.",
  "HUD color": "Choose the color used for the challenge progress display.",
  "HUD item icon": "Show this item beside the challenge display. Use an item ID such as minecraft:compass.",
  "HUD animation": "Choose how the challenge display changes when its progress updates.",
  "Budget measure": "The named value this challenge tracks against its minimum or maximum.",
  "Step condition": "Choose the event or state that completes this step.",
  "Step target": "Limit this step to a specific event target.",
  "Step timeout": "The time allowed for this step before it fails. Use a value such as 30s.",
  "Variable name": "The identifier other rules use to read this saved value. Keep it stable after linking rules.",
  "Value type": "Choose whether this variable stores a number, true or false, or text.",
  "Owner": "Choose whether each player, a team, or the whole server shares this value.",
  "Starting value": "The value used before any event changes this variable.",
  "Minimum": "The lowest value this variable may hold.",
  "Maximum": "The highest value this variable may hold.",
  "Formula name": "The identifier other rules use to read this calculated value.",
  "Safe calculation": "Calculate a value from named variables using the supported expression syntax. Example: kills * 2 + quest_points.",
  "Available state names": "List the state names this stage may use. Other rules can refer to these exact names.",
  "States that count as owned": "A player counts as owning this stage only while it is in one of these states.",
  "Starting state": "The state a player receives before any transition changes this stage.",
  "Profile name": "The name of this reusable rule profile.",
  "Content selector": "Choose the blocks, items, or other content this profile covers.",
  "Proficiency variable": "Choose the saved variable that tracks the player's level in this profile.",
  "Level name": "The name players see for this proficiency level.",
  "Minimum value": "The saved value needed to reach this level.",
  "Behavior": "Choose what this proficiency level changes, such as denying use, changing cost, or changing cooldown.",
  "Template name": "A name used to refer to this reusable template.",
  "Included templates": "List other templates to apply along with this one.",
  "Merge behavior": "Choose how values from multiple templates combine when they overlap.",
  "Readable label": "The short name shown to pack authors for this configuration entry.",
  "Experience levels": "Whole experience levels to give or take. This differs from raw experience points.",
  "Experience points": "Raw experience points to give or take, independent of whole levels.",
  "Attribute identifier": "The registered attribute to change, such as minecraft:generic.max_health.",
  "Effect identifier": "The registered status effect to apply, such as minecraft:speed.",
  "Amount": "How much this entry gives, costs, or changes. Check the surrounding section for its unit.",
  "Amplifier": "The strength of a status effect; zero is level I, one is level II.",
  "Operation": "Choose whether to add a flat amount or multiply the base or final value.",
  "Item selector": "Choose the item or group of items this modifier affects.",
  "Source block": "Choose the block that normally produces the drop. Use its registered ID, a mod selector, or a block tag.",
  "Output item": "Choose the item this drop rule produces. Use a registered item ID.",
  "Required tool": "Limit this drop rule to a matching tool. Leave it blank to allow any valid tool.",
  "Extra stages that must be owned": "This modifier runs only while the player owns all stages listed here.",
  "Stages that must be owned": "This entry runs only while the player owns all stages listed here.",
  "Stages that must be missing": "This entry runs only while the player does not own any stage listed here.",
  "Additional condition": "Choose another event or state that must be true before this entry runs.",
  "Condition target": "Limit the additional condition to this target.",
  "Required enchantment": "Choose the enchantment this rule checks on the item.",
  "Add before multiplying": "Add this number to the normal drop count before applying the multiplier.",
  "Multiply final drops by": "Multiply the resulting item count by this value.",
  "Vanilla advancement frame": "Choose the border style used when this stage is shown like an advancement.",
  "Mapping id": "A stable identifier for this external mapping. Other rules may refer to it.",
  "Output kind": "Choose what kind of result this mapping sends to another system.",
  "Rule id": "A stable identifier for this rule within its integration.",
  "Target entity selector": "Choose the entity or entity group this interaction rule matches.",
  "Result": "Choose what happens when this entry matches. Read the choices before saving.",
  "Description": "Explain what this rule or mapping does for other pack authors.",
  "Extra XP levels": "Additional whole experience levels required for this purchase.",
  "Extra XP points": "Additional raw experience points required for this purchase.",
  "Reset target": "Limit the reset event to this target.",
  "Reset amount": "How many reset events must happen before this rule resets.",
  "JEI and EMI": "Choose how recipe viewers show content controlled by this rule.",
  "Optional exception selector": "Content matching this selector is exempt from the broader rule.",
  "Exception priority": "Choose which exception wins if several match. Higher numbers are considered first.",
  "Category": "Choose the kind of rule or content this entry belongs to.",
  "Action": "Choose the action this entry takes when its conditions match.",
  "Target identifier": "Enter the exact registered ID of the target content.",
  "Folder below stages": "Choose a subfolder under the stages directory for this stage package.",
  "Required amount": "How many matching events must happen before this condition is met. For example, 3 means three matches.",
  "Progress owner": "Choose whose progress is counted: each player, the whole team, or everyone on the server.",
  "Priority": "Controls which matching entry is considered first when entries overlap. A larger number has higher priority.",
  "Cooldown": "How long to wait before this trigger can count again. Use a time such as 30s; leave blank for no cooldown.",
  "Purchase cooldown": "How long a player must wait between purchase attempts. Use a time such as 2s.",
  "Time limit": "How long the challenge may run before it ends. Use a time such as 5m.",
  "Duration in ticks": "How long this effect lasts. Minecraft runs 20 ticks each second, so 40 ticks lasts 2 seconds.",
  "Lifetime": "Choose whether this rule lasts forever, only while its condition is true, for a timed period, or until reset.",
  "Duration or schedule": "Set the time for a timed rule, such as 30s or 5m. Scheduled rules use this value to decide when they are active.",
  "When the group is full": "Choose what happens when a player gains another stage after every slot in this group is occupied.",
  "How selected paths join": "Choose whether a player needs every selected path or only some of them before this stage can unlock.",
  "Minimum paths": "The number of selected paths that must be owned when the path rule allows a minimum count.",
  "When the stage appears": "Choose when this stage becomes visible to players. Hidden stages can still be edited here.",
  "Selection method": "Choose whether to match one exact identifier, a whole mod, or a tag containing several entries.",
  "Item selection method": "Choose how to match items: an exact item, a mod, or a tag. The preview shows what the selector covers.",
  "Destination selection method": "Choose how the destination is matched. Use a specific identifier when only one target should count.",
  "Selector mode": "Choose how the registry search interprets the value: exact ID, mod, or tag.",
  "Installed mod": "Select a mod installed on this server. A mod selector can match many entries, so review its match count.",
  "Content type": "Choose the kind of server content to search, such as blocks or items. Results use the current registry.",
  "Trigger": "Choose the event that can move this stage forward. The required amount decides how many times it must happen.",
  "Repeat policy": "Choose whether this trigger can count once or again after the stage changes.",
  "When the item is": "Choose the item action that must happen before this modifier can apply.",
  "Modifier kind": "Choose whether this entry changes an attribute or applies a status effect.",
  "Stacking behavior": "Choose how this modifier combines with another matching modifier.",
  "Maximum stacks": "The most copies of this modifier that may apply at once. One prevents additional stacks.",
  "Minimum enchantment level": "The lowest level of the selected enchantment that can satisfy this rule.",
  "Minimum final count": "The smallest number of items allowed after this drop modifier runs.",
  "Maximum final count": "The largest number of items allowed after this drop modifier runs.",
  "HUD scale": "Changes the challenge display size. 1 is normal size; 0.5 is half size.",
  "Regeneration each second": "How much of the challenge budget returns each second while its rules allow regeneration.",
  "Budget behavior": "Choose whether the budget must stay below a maximum, reach a minimum, or remain inside a range.",
  "Budget minimum": "The lowest permitted budget value when the selected behavior uses a lower bound.",
  "Budget maximum": "The highest permitted budget value when the selected behavior uses an upper bound.",
  "Rule category": "Choose which gameplay action this rule controls. Only matching actions are affected.",
  "Activation condition": "Choose the event or state that makes this rule active.",
  "Reset condition": "Choose the event that ends a latched rule and lets it start again.",
  "Match policy": "Choose how many of the listed matches are needed before this integration rule applies.",
  "Interaction type": "Choose the player action that this integration rule watches.",
  "Destination type": "Choose whether the target is a container block, an open menu, or an inventory owner.",
  "Destination selector": "Choose the exact destination, mod, or tag this integration rule matches.",
  "Inbound retention": "Choose how long a stage granted by an external source remains owned after that source changes."
};

export function Field({ label, help, wide, children }: { label: string; help?: string; wide?: boolean; children: React.ReactNode }) {
  const labelId = useId();
  const helpId = useId();
  const [helpOpen, setHelpOpen] = useState(false);
  const resolvedHelp = help || FIELD_HELP[label] || `Set ${label.toLowerCase()}. The value stays in the draft until you review and apply it.`;
  return <div className={`field ${wide ? "field-wide" : ""}`}>
    <label className="field-input-label"><span id={labelId} className="field-label">{label}</span>{children}</label>
    <span className="field-help-trigger" role="button"
      aria-label="Show field explanation" aria-describedby={`${labelId} ${helpId}`} aria-expanded={helpOpen} tabIndex={0}
      onClick={event => { event.preventDefault(); event.stopPropagation(); setHelpOpen(open => !open); }}
      onKeyDown={event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); event.stopPropagation(); setHelpOpen(open => !open); } }}>?</span>
    <span id={helpId} role="tooltip" className={`field-help ${helpOpen ? "field-help-open" : ""}`}>{resolvedHelp}</span>
  </div>;
}

const TOGGLE_HELP: Record<string, string> = {
  "Compact HUD": "Use a smaller challenge display that takes less screen space.",
  "Hide HUD while inactive": "Keep the challenge display hidden until its challenge is running.",
  "Keep measured values secret": "Hide exact challenge values from players while the server still checks them.",
  "Reset challenge if step fails": "Return this challenge to its starting step when a step fails.",
  "Save across restarts": "Keep this challenge's progress when the server restarts.",
  "Synchronize for player UI": "Send this progress to the player's screen so the display stays current.",
  "Stop lower priority bonuses": "Do not apply lower priority mining bonuses after this one matches."
};

export function Toggle({ label, help, checked, onChange, disabled }:
  { label: string; help?: string; checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean }) {
  const resolvedHelp = help || TOGGLE_HELP[label] || `Enable or disable ${label.toLowerCase()}. This setting stays in the draft until you review and apply it.`;
  return <label className="toggle-row" title={resolvedHelp}>
    <input type="checkbox" aria-label={label} checked={checked} onChange={event => onChange(event.target.checked)} disabled={disabled}/>
    <span className="toggle-control" aria-hidden="true"><span/></span>
    <span><strong>{label}</strong><small>{resolvedHelp}</small></span>
  </label>;
}

export function Section({ title, description, action, children, className = "" }:
  { title: string; description?: string; action?: React.ReactNode; children: React.ReactNode; className?: string }) {
  return <section className={`section-card ${className}`}>
    <header className="section-header"><div><h2 title={description || `Edit ${title.toLowerCase()} for the current stage.`}>{title}</h2>{description ? <p>{description}</p> : null}</div>{action}</header>
    <div className="section-content">{children}</div>
  </section>;
}

export function EmptyState({ icon = "spark", title, description, action }:
  { icon?: string; title: string; description: string; action?: React.ReactNode }) {
  return <div className="empty-state"><span className="empty-icon"><Icon name={icon} size={24}/></span><div><strong>{title}</strong><p>{description}</p></div>{action}</div>;
}

export function DialogHost() {
  const { dialog, closeDialog } = useEditor();
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useEffect(() => {
    if (!dialog) return;
    const previous = document.activeElement as HTMLElement | null;
    panel.current?.focus();
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") closeDialog(); };
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("keydown", escape); previous?.focus(); };
  }, [dialog, closeDialog]);
  if (!dialog) return null;
  return <div className="dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) closeDialog(); }}>
    <div ref={panel} tabIndex={-1} className={`dialog dialog-${dialog.width || "standard"}`} role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <header className="dialog-header"><div><h2 id={titleId}>{dialog.title}</h2>{dialog.description ? <p>{dialog.description}</p> : null}</div><Button tone="quiet" icon="close" aria-label="Close dialog" onClick={closeDialog}/></header>
      <div className="dialog-body">{dialog.content}</div>
    </div>
  </div>;
}

export function NoticeCenter() {
  const { notices, dismissNotice } = useEditor();
  return <div className="notice-center" aria-live="polite">{notices.map(notice =>
    <article key={notice.id} className={`notice notice-${notice.tone}`}>
      <span className="notice-mark"><Icon name={notice.tone === "danger" ? "warning" : "check"} size={17}/></span>
      <div><strong>{notice.title}</strong>{notice.message ? <p>{notice.message}</p> : null}</div>
      <button aria-label="Dismiss" onClick={() => dismissNotice(notice.id)}><Icon name="close" size={15}/></button>
    </article>)}
  </div>;
}

export function LoadingScreen({ message }: { message: string }) {
  return <main className="loading-screen"><img src="/favicon.svg" alt="ProgressiveStages lock logo"/><div className="loading-ring"/><h1>ProgressiveStages</h1><p>{message}</p></main>;
}

export function ErrorScreen({ message, retry }: { message: string; retry: () => void }) {
  return <main className="loading-screen error-screen"><img src="/favicon.svg" alt="ProgressiveStages lock logo"/><Badge tone="danger">Connection problem</Badge><h1>The editor could not start</h1><p>{message}</p><Button tone="primary" onClick={retry}>Try again</Button></main>;
}
