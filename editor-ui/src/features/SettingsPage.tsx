import { useEffect, useMemo, useState } from "react";
import { Button, Field, Section, Toggle } from "../components/ui";
import { title } from "../lib/model";
import { booleanValue, lineValues, parseSimpleArray, readTomlValue, stringValue, upsertToml } from "../lib/toml";
import { useEditor } from "../store/EditorContext";
import type { FieldSchema } from "../types";

function SettingControl({ schema }: { schema: FieldSchema }) {
  const { boot, mutateFile, setLocalError } = useEditor();
  const content = boot?.draft.files["progressivestages.toml"] || "";
  const raw = readTomlValue(content, schema.path);
  const initial = raw || String(schema.defaultValue ?? "");
  const [value, setValue] = useState(schema.type === "LIST" ? parseSimpleArray(raw).join("\n") : stringValue(initial));
  const [error, setError] = useState("");
  useEffect(() => {
    setValue(schema.type === "LIST" ? parseSimpleArray(raw).join("\n") : stringValue(initial));
    setError("");
    setLocalError(schema.id, null);
  }, [initial, raw, schema.id, schema.type, setLocalError]);
  const minimum = typeof schema.controlHints.min === "number" ? schema.controlHints.min : undefined;
  const maximum = typeof schema.controlHints.max === "number" ? schema.controlHints.max : undefined;
  const validateInput = (next: string) => {
    if (schema.type === "INTEGER" && !/^-?\d+$/.test(next.trim())) return "Enter a whole number, or restore the default.";
    if (schema.type === "DECIMAL" && (!next.trim() || !Number.isFinite(Number(next)))) return "Enter a number, or restore the default.";
    if (schema.type === "INTEGER" || schema.type === "DECIMAL") {
      const number = Number(next);
      if (minimum !== undefined && number < minimum) return `Use a value of at least ${minimum}.`;
      if (maximum !== undefined && number > maximum) return `Use a value of at most ${maximum}.`;
    }
    if (schema.type === "ENUM" && !schema.enumValues.includes(next)) return "Choose one of the listed values.";
    return "";
  };
  const save = async (next = value) => {
    const problem = validateInput(next);
    if (problem) { setError(problem); setLocalError(schema.id, problem); return; }
    setError("");
    setLocalError(schema.id, null);
    let typed: unknown = next;
    if (schema.type === "BOOLEAN") typed = next === "true";
    else if (schema.type === "INTEGER") typed = Number(next);
    else if (schema.type === "DECIMAL") typed = Number(next);
    else if (schema.type === "LIST") typed = lineValues(next);
    await mutateFile("progressivestages.toml", current => upsertToml(current, schema.path, typed), `${schema.label} saved`);
  };
  if (schema.type === "BOOLEAN") return <Toggle label={schema.label} help={schema.help} checked={booleanValue(raw || String(schema.defaultValue))} onChange={checked => void save(String(checked))}/>;
  return <Field label={schema.label} help={`${schema.help}${schema.restartRequirement && schema.restartRequirement !== "NONE" ? ` Restart requirement. ${schema.restartRequirement}.` : ""}`} wide={schema.type === "LIST"}>
    {schema.type === "ENUM" ? <select value={value} onChange={event => { setValue(event.target.value); void save(event.target.value); }}>{schema.enumValues.map(option => <option key={option} value={option}>{title(option)}</option>)}</select> : schema.type === "LIST" ? <textarea rows={4} value={value} onChange={event => setValue(event.target.value)} onBlur={() => void save()}/> : <input type={schema.type === "INTEGER" || schema.type === "DECIMAL" ? "number" : "text"} step={schema.type === "DECIMAL" ? "any" : schema.type === "INTEGER" ? 1 : undefined} min={minimum} max={maximum} value={value} onChange={event => { setValue(event.target.value); if (error) { const nextError = validateInput(event.target.value); setError(nextError); setLocalError(schema.id, nextError || null); } }} onBlur={() => void save()}/>}
    {error ? <span className="setting-error" role="alert">{error}</span> : null}
  </Field>;
}

export function SettingsPage() {
  const { boot, hasLocalErrors, setLocalError, validate } = useEditor();
  const schemas = boot?.schemas.filter(schema => schema.file === "progressivestages.toml") || [];
  const groups = useMemo(() => { const result = new Map<string, FieldSchema[]>(); for (const schema of schemas) { const group = schema.path.split(".")[0] || "general"; result.set(group, [...(result.get(group) || []), schema]); } return result; }, [schemas]);
  const schemaIds = schemas.map(schema => schema.id).join("|");
  useEffect(() => () => schemaIds.split("|").filter(Boolean).forEach(id => setLocalError(id, null)), [schemaIds, setLocalError]);
  return <div className="page-stack"><header className="page-heading"><div><h1>Settings</h1><p>Edit the connected server settings. Changes stay in the draft until you review and apply them.</p></div><div><Button disabled={hasLocalErrors} onClick={() => void validate()}>Validate settings</Button></div></header>{[...groups.entries()].map(([group, fields]) => <Section key={group} title={title(group)} description="These controls use the server schema. Hover the question mark for an example and restart details."><div className="form-grid">{fields.map(schema => <SettingControl key={schema.id} schema={schema}/>)}</div></Section>)}</div>;
}
