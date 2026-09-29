import { isInlineRow, inlineRowSource, comments, findValue, inlineValues, retainValueComments, newline, quoted, replaceValue, requireEditable, samePath, scanToml, type TableSpan } from "./tomlSource";

export function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function tomlBalance(value: string): number {
  let square = 0;
  let curly = 0;
  let quote = "";
  let escaped = false;
  for (const character of value) {
    if (escaped) { escaped = false; continue; }
    if (character === "\\" && quote === '"') { escaped = true; continue; }
    if (quote) { if (character === quote) quote = ""; continue; }
    if (character === '"' || character === "'") { quote = character; continue; }
    if (character === "[") square++;
    else if (character === "]") square--;
    else if (character === "{") curly++;
    else if (character === "}") curly--;
  }
  return Math.max(0, square + curly);
}

export function readTomlValue(text: string, path: string): string {
  try {
    const value = findValue(scanToml(text), path.split("."));
    return value ? text.slice(value.valueStart, value.valueEnd) : "";
  } catch { return ""; }
}

export function encodeToml(value: unknown): string {
  if (typeof value === "boolean" || typeof value === "number") return String(value);
  if (Array.isArray(value)) return `[${value.map(encodeToml).join(", ")}]`;
  if (value && typeof value === "object") {
    return `{ ${Object.entries(value as Record<string, unknown>).map(([key, entry]) => `${key} = ${encodeToml(entry)}`).join(", ")} }`;
  }
  return JSON.stringify(String(value ?? ""));
}

export function upsertToml(text: string, path: string, value: unknown): string {
  return upsertTomlEncoded(text, path, encodeToml(value));
}

export function upsertTomlEncoded(text: string, path: string, encoded: string): string {
  const parts = path.split(".");
  const source = scanToml(text);
  requireEditable(source);
  const existing = findValue(source, parts);
  if (existing) return replaceValue(text, existing, encoded);
  const section = parts.slice(0, -1);
  const table = source.tables.find(entry => !entry.array && samePath(entry.path, section));
  const eol = newline(text);
  for (let length = parts.length - 1; length > 0; length--) {
    const parent = findValue(source, parts.slice(0, length));
    if (!parent) continue;
    const entries = inlineValues(text, parent);
    const last = entries.at(-1);
    const position = last?.valueEnd ?? parent.valueStart + 1;
    const field = parts.slice(length).join(".");
    return text.slice(0, position) + `${last ? ", " : " "}${field} = ${encoded}`
      + (text[position] === "}" ? " " : "") + text.slice(position);
  }
  const dotted = table ? undefined : source.values.find(entry => {
    const parent = entry.table?.path || [];
    const remaining = section.slice(parent.length);
    return !entry.table?.array && remaining.length > 0 && samePath(parent, section.slice(0, parent.length))
      && entry.key.length > remaining.length && samePath(entry.key.slice(0, remaining.length), remaining);
  });
  if (section.length && !table && !dotted) return appendTomlBlock(text, `[${section.join(".")}]${eol}${parts.at(-1)} = ${encoded}`);
  const parent = table || dotted?.table;
  const key = dotted ? parts.slice(parent?.path.length || 0).join(".") : parts.at(-1);
  const end = source.tables.find(entry => entry.start >= (parent?.end || 0))?.start ?? text.length;
  return text.slice(0, end) + (end && text[end - 1] !== "\n" ? eol : "")
    + `${key} = ${encoded}${eol}` + text.slice(end);
}

export function removeTomlValue(text: string, path: string): string {
  const source = scanToml(text);
  requireEditable(source);
  const value = findValue(source, path.split("."));
  if (!value) return text;
  if (value.inline) {
    const entries = inlineValues(text, value.inline);
    const index = entries.findIndex(entry => entry.start === value.start);
    const start = index ? entries[index - 1].valueEnd : value.start;
    const end = index === 0 && entries.length > 1 ? entries[1].start : value.valueEnd;
    return retainValueComments(text, value, text.slice(0, start) + text.slice(end));
  }
  const notes = comments(text.slice(value.valueStart, value.end));
  return text.slice(0, value.start) + (notes.length ? notes.join(newline(text)) + newline(text) : "") + text.slice(value.end);
}

export function removeTomlSection(text: string, section: string): string {
  const source = scanToml(text);
  requireEditable(source);
  const index = source.tables.findIndex(table => !table.array && samePath(table.path, section.split(".")));
  if (index < 0) return removeTomlValue(text, section);
  return text.slice(0, source.tables[index].start) + text.slice(source.tables[index + 1]?.start ?? text.length);
}

export interface ArrayBlock {
  table: string;
  index: number;
  start: number;
  end: number;
  text: string;
  startOffset: number;
  endOffset: number;
}

export function extractArrayBlocks(text: string, table: string): ArrayBlock[] {
  return arrayBlocks(text, table, false);
}

export function extractArrayGroups(text: string, table: string): ArrayBlock[] {
  return arrayBlocks(text, table, true);
}

function arrayBlocks(text: string, table: string, children: boolean): ArrayBlock[] {
  const tables = scanToml(text).tables;
  const path = table.split(".");
  const blocks: ArrayBlock[] = [];
  const lineAt = (offset: number) => text.slice(0, offset).split("\n").length - 1;
  for (let index = 0; index < tables.length; index++) {
    const header = tables[index];
    if (!header.array || !samePath(header.path, path)) continue;
    const child = (entry: TableSpan) => entry.path.length > path.length && samePath(entry.path.slice(0, path.length), path);
    const next = tables.slice(index + 1).find(entry => !children || !child(entry));
    const boundary = next?.start ?? text.length;
    let end = boundary;
    if (next) {
      while (end > header.start) {
        const lineEnd = end > 0 && text[end - 1] === "\n" ? end - 1 : end;
        const lineStart = text.lastIndexOf("\n", lineEnd - 1) + 1;
        const line = text.slice(lineStart, lineEnd).trim();
        if (line && !line.startsWith("#")) break;
        end = lineStart;
      }
    }
    blocks.push({ table, index: blocks.length, start: lineAt(header.start), end: next ? lineAt(end) : text.split("\n").length,
      startOffset: header.start, endOffset: end, text: text.slice(header.start, end) });
  }
  return blocks;
}

function replaceBlocks(text: string, blocks: ArrayBlock[], replacements: string[]): string {
  requireEditable(scanToml(text));
  let result = text;
  for (let index = blocks.length - 1; index >= 0; index--) {
    const block = blocks[index];
    let replacement = replacements[index] || "";
    if (replacement === block.text) continue;
    if (replacement && block.endOffset < text.length && !replacement.endsWith("\n")) replacement += newline(text);
    result = result.slice(0, block.startOffset) + replacement + result.slice(block.endOffset);
  }
  for (const replacement of replacements.slice(blocks.length)) result = appendTomlBlock(result, replacement);
  return result;
}

export function replaceArrayGroups(text: string, table: string, replacements: string[]): string {
  return replaceBlocks(text, extractArrayGroups(text, table), replacements);
}

export function replaceArrayBlocks(text: string, table: string, replacements: string[]): string {
  return replaceBlocks(text, extractArrayBlocks(text, table), replacements);
}

export function appendTomlBlock(text: string, block: string): string {
  const eol = newline(text);
  const separator = !text || text.endsWith(eol + eol) ? "" : text.endsWith("\n") ? eol : eol + eol;
  return text + separator + block + (block.endsWith("\n") ? "" : eol);
}

export function parseSimpleArray(raw: string): string[] {
  const value = raw.trim();
  if (!value.startsWith("[") || !value.endsWith("]")) return [];
  const entries: string[] = [];
  const pattern = /"((?:\\.|[^"\\])*)"|'([^']*)'|([^,\[\]\s][^,\[\]]*)/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(value.slice(1, -1)))) {
    const entry = match[1] != null ? JSON.parse(`"${match[1]}"`) : match[2] != null ? match[2] : match[3].trim();
    if (entry !== "") entries.push(String(entry));
  }
  return entries;
}

export function stringValue(raw: string): string {
  const value = raw.trim();
  if (value.startsWith('"') || value.startsWith("'")) {
    try { return quoted(value, 0).value; } catch { return value; }
  }
  return value.split("#", 1)[0].trim();
}

export function booleanValue(raw: string): boolean {
  return raw.trim().toLowerCase() === "true";
}

export function numberValue(raw: string, fallback = 0): number {
  const scalar = stringValue(raw);
  if (!scalar) return fallback;
  const value = Number(scalar);
  return Number.isFinite(value) ? value : fallback;
}

export function lineValues(value: string): string[] {
  return value.split(/\r?\n|,/).map(entry => entry.trim()).filter(Boolean);
}

export function readBlockValue(block: string, key: string): string {
  if (isInlineRow(block)) {
    try {
      const { text, root } = inlineRowSource(block);
      const value = inlineValues(text, root).find(entry => samePath(entry.key, [key]));
      return value ? text.slice(value.valueStart, value.valueEnd) : "";
    } catch { return ""; }
  }
  const source = scanToml(block);
  const value = source.values.find(entry => entry.table === source.tables[0] && samePath(entry.key, [key]));
  return value ? block.slice(value.valueStart, value.valueEnd) : "";
}

export function inlineObjectValue(raw: string, key: string): string {
  return stringValue(readBlockValue(raw, key));
}

export function conditionToml(type: string, target: string, count: number): string {
  const result: Record<string, unknown> = { type };
  if (target.trim()) result[type === "weather" ? "value" : "id"] = target.trim();
  if (count > 1) result.count = count;
  return encodeToml(result);
}
