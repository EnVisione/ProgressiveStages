import { useEffect, useMemo, useState } from "react";
import { Badge, Button, Field, Section, Toggle } from "../components/ui";
import { title } from "../lib/model";
import { booleanValue, lineValues, parseSimpleArray, readTomlValue, stringValue, upsertToml } from "../lib/toml";
import { useEditor } from "../store/EditorContext";
import type { FieldSchema } from "../types";

function SettingControl({ schema }: { schema: FieldSchema }) {
  const { boot, mutateFile } = useEditor();
  const content = boot?.draft.files["progressivestages.toml"] || "";
  const raw = readTomlValue(content, schema.path);
  const initial = raw || String(schema.defaultValue ?? "");
  const [value, setValue] = useState(schema.type === "LIST" ? parseSimpleArray(raw).join("\n") : stringValue(initial));
  useEffect(() => setValue(schema.type === "LIST" ? parseSimpleArray(raw).join("\n") : stringValue(initial)), [initial, raw, schema.type]);
  const save = async (next = value) => {
    let typed: unknown = next;
    if (schema.type === "BOOLEAN") typed = next === "true";
    else if (schema.type === "INTEGER" || schema.type === "DECIMAL") typed = Number(next || 0);
    else if (schema.type === "LIST") typed = lineValues(next);
    await mutateFile("progressivestages.toml", upsertToml(content, schema.path, typed), `${schema.label} saved`);
  };
  if (schema.type === "BOOLEAN") return <Toggle label={schema.label} help={schema.help} checked={booleanValue(raw || String(schema.defaultValue))} onChange={checked => void save(String(checked))}/>;
  return <Field label={schema.label} help={`${schema.help}${schema.restartRequirement && schema.restartRequirement !== "NONE" ? ` Restart requirement. ${schema.restartRequirement}.` : ""}`} wide={schema.type === "LIST"}>
    {schema.type === "ENUM" ? <select value={value} onChange={event => { setValue(event.target.value); void save(event.target.value); }}>{schema.enumValues.map(option => <option key={option} value={option}>{title(option)}</option>)}</select> : schema.type === "LIST" ? <textarea rows={4} value={value} onChange={event => setValue(event.target.value)} onBlur={() => void save()}/> : <input type={schema.type === "INTEGER" || schema.type === "DECIMAL" ? "number" : "text"} step={schema.type === "DECIMAL" ? "any" : undefined} value={value} onChange={event => setValue(event.target.value)} onBlur={() => void save()}/>}
  </Field>;
}

export function SettingsPage() {
  const { boot, validate } = useEditor();
  const schemas = boot?.schemas.filter(schema => schema.file === "progressivestages.toml") || [];
  const groups = useMemo(() => { const result = new Map<string, FieldSchema[]>(); for (const schema of schemas) { const group = schema.path.split(".")[0] || "general"; result.set(group, [...(result.get(group) || []), schema]); } return result; }, [schemas]);
  return <div className="page-stack"><header className="page-heading"><div><span className="eyebrow">Global configuration</span><h1>Main settings</h1><p>Controls are generated from the running server schema. Nothing in this page is a guessed or hardcoded config list.</p></div><div><Badge tone="gold">{schemas.length} registered options</Badge><Button onClick={() => void validate()}>Validate configuration</Button></div></header>{[...groups.entries()].map(([group, fields]) => <Section key={group} title={title(group)} description="Server configuration options supplied by the current runtime schema."><div className="form-grid">{fields.map(schema => <SettingControl key={schema.id} schema={schema}/>)}</div></Section>)}</div>;
}
