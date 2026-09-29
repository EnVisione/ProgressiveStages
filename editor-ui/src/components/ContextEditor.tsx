import { useEditor } from "../store/EditorContext";
import { Button, Field } from "./ui";
import type { ContextRow } from "../lib/contexts";

export function ContextEditor({ rows, onChange, sourceError }: {
  rows: ContextRow[]; onChange: (rows: ContextRow[]) => void; sourceError?: string;
}) {
  const { boot } = useEditor();
  const limits = boot?.schemas?.find(field => field.path === "luckperms.inbound[].contexts")?.controlHints;
  const update = (index: number, row: ContextRow) => onChange(rows.map((current, position) => position === index ? row : current));
  return <fieldset className="context-editor">
    <legend>Contexts</legend>
    <p className="muted">All keys must match. Any listed value may match within a key. Each value is entered separately; commas and line breaks stay part of that value.</p>
    {limits ? <p className="muted">Server limits. {String(limits.maxKeys)} keys, {String(limits.maxValues)} values per key, and {String(limits.maxCombinations)} combinations. Validation checks these limits before apply.</p> : null}
    {sourceError ? <p role="alert">Existing context source. {sourceError} The source is preserved. Correct it in Source before changing these contexts.</p> : null}
    {rows.map((row, index) => <fieldset key={index} className="context-entry">
      <legend>Context {index + 1}</legend>
      <Field label={`Context ${index + 1} key`} help="Name the context value another rule will read. Keep this key stable when rules already use it."><input value={row.key} onChange={event => update(index, { ...row, key: event.target.value })}/></Field>
      {row.values.map((value, valueIndex) => <div key={valueIndex} className="context-value">
        <Field label={`Context ${index + 1} value ${valueIndex + 1}`} help="Add one value this context key may match. Use another row for another value."><textarea rows={1} value={value} onChange={event => update(index, { ...row, values: row.values.map((current, position) => position === valueIndex ? event.target.value : current) })}/></Field>
        <Button type="button" tone="quiet" aria-label={`Remove context ${index + 1} value ${valueIndex + 1}`} onClick={() => update(index, { ...row, values: row.values.filter((_, position) => position !== valueIndex) })}>Remove value</Button>
      </div>)}
      <div className="rule-card-actions"><Button type="button" onClick={() => update(index, { ...row, values: [...row.values, ""] })}>Add value to context {index + 1}</Button><Button type="button" tone="danger" onClick={() => onChange(rows.filter((_, position) => position !== index))}>Remove context {index + 1}</Button></div>
    </fieldset>)}
    <Button type="button" onClick={() => onChange([...rows, { key: "", values: [""] }])}>Add context</Button>
  </fieldset>;
}
