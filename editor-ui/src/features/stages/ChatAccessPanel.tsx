import { useEffect, useState } from "react";
import { FieldDiagnostics } from "../../components/ValidationMessages";
import { Field, Section, Toggle } from "../../components/ui";
import { writeOwnership } from "../../lib/integrations";
import { booleanValue, parseSimpleArray, readTomlValue, upsertToml } from "../../lib/toml";
import { useEditor } from "../../store/EditorContext";
import type { StagePackage } from "../../types";

const MODES = ["survival", "creative", "adventure", "spectator"];
const normalizeMode = (value: string) => value.trim().toLowerCase();

export function ChatAccessPanel({ stage }: { stage: StagePackage }) {
  const { boot, mutateFile } = useEditor();
  const source = boot?.draft.files[stage.stagePath] || "";
  const locked = booleanValue(readTomlValue(source, "chat.lock_access"));
  const terms = parseSimpleArray(readTomlValue(source, "chat.mask_terms"));
  const modes = parseSimpleArray(readTomlValue(source, "gamemode.grant_in")).map(normalizeMode);
  const [termsDraft, setTermsDraft] = useState(terms.join("\n"));
  const [termsError, setTermsError] = useState("");
  useEffect(() => setTermsDraft(terms.join("\n")), [source, stage.stagePath]);
  const save = (update: (current: string) => string, message: string) => mutateFile(stage.stagePath, update, message);
  const saveTerms = async () => {
    const next = termsDraft.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
    if (next.length > 64 || next.some(value => value.length > 80 || /[\x00-\x1f\x7f-\x9f]/.test(value))
        || new Set(next.map(value => value.toLowerCase())).size !== next.length) {
      setTermsError("Use at most 64 distinct terms, each at most 80 characters, with no control characters.");
      return;
    }
    setTermsError("");
    if (JSON.stringify(next) !== JSON.stringify(terms)) await save(current => upsertToml(current, "chat.mask_terms", next), "Chat mask terms saved");
  };
  const setMode = (mode: string, checked: boolean) => {
    void save(current => {
      const currentModes = parseSimpleArray(readTomlValue(current, "gamemode.grant_in")).map(normalizeMode);
      const next = checked ? [...new Set([...currentModes, mode])] : currentModes.filter(value => value !== mode);
      return upsertToml(next.length ? writeOwnership(current, "personal") : current,
        "gamemode.grant_in", next);
    }, "Game mode stage grant saved");
  };
  return <>
    <Section title="Player chat access" description="Use this stage to unlock normal player chat or reveal words and phrases in it.">
      <div className="form-grid">
        <Toggle label="Require this stage for player chat" help="Players without this stage cannot send or read normal player messages. Commands and system messages remain available." checked={locked} onChange={value => void save(current => upsertToml(current, "chat.lock_access", value), "Chat access saved")}/>
        <Field label="Obfuscate these terms until owned" help="One whole word or phrase per line, case insensitive. Owners see the original text. This changes the recipient's display, not server logs or chat reports." wide>
          <textarea aria-label="Obfuscate these terms until owned" rows={4} value={termsDraft} onChange={event => setTermsDraft(event.target.value)} onBlur={() => void saveTerms()} placeholder={"Create\nMechanical Press"}/>
          <small>{termsDraft.split(/\r?\n/).filter(Boolean).length}/64 terms. Each term may be up to 80 characters. Save by leaving this field.</small>
          {termsError ? <small role="alert" className="setting-error">{termsError}</small> : null}
        </Field>
        <FieldDiagnostics file={stage.stagePath} fields={["chat.lock_access", "chat.mask_terms"]}/>
      </div>
    </Section>
    <Section title="Game mode grants" description="Grant this personal stage while a player is in any selected game mode. Leaving those modes removes only the game mode grant; other grants remain.">
      <div className="form-grid">
        {MODES.map(mode => <Toggle key={mode} label={mode[0].toUpperCase() + mode.slice(1)} help={`Grant this stage to the player while in ${mode} mode.`} checked={modes.includes(mode)} onChange={checked => setMode(mode, checked)}/>)}
        <FieldDiagnostics file={stage.stagePath} fields={["gamemode.grant_in", "stage.team_stage"]}/>
      </div>
    </Section>
  </>;
}
