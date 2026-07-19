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
  const parts = path.split(".");
  const key = parts.pop() || "";
  const section = parts.join(".");
  const lines = text.split(/\r?\n/);
  let active = "";
  for (let index = 0; index < lines.length; index++) {
    const header = lines[index].match(/^\s*\[([^\]]+)\]/);
    if (header) { active = header[1]; continue; }
    if (active !== section) continue;
    const match = lines[index].match(new RegExp(`^\\s*${escapeRegex(key)}\\s*=\\s*(.*)$`));
    if (!match) continue;
    let value = match[1].trim();
    while (tomlBalance(value) > 0 && index + 1 < lines.length) value += `\n${lines[++index].trim()}`;
    return value;
  }
  return "";
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
  const parts = path.split(".");
  const key = parts.pop() || "";
  const section = parts.join(".");
  const encoded = encodeToml(value);
  const lines = text.split(/\r?\n/);
  let start = section ? -1 : -2;
  let end = lines.length;
  for (let index = 0; index < lines.length; index++) {
    const header = lines[index].match(/^\s*\[\[?([^\]]+)/);
    if (!header) continue;
    if (start >= 0) { end = index; break; }
    if (section && header[1] === section) start = index;
    if (!section && start === -2) { end = index; break; }
  }
  if (section && start < 0) {
    if (lines.at(-1) !== "") lines.push("");
    lines.push(`[${section}]`, `${key} = ${encoded}`);
    return lines.join("\n");
  }
  const from = section ? start + 1 : 0;
  for (let index = from; index < end; index++) {
    if (!new RegExp(`^\\s*${escapeRegex(key)}\\s*=`).test(lines[index])) continue;
    let last = index;
    let balance = tomlBalance(lines[index].slice(lines[index].indexOf("=") + 1));
    while (balance > 0 && last + 1 < end) {
      last++;
      balance = tomlBalance(lines.slice(index, last + 1).join("\n"));
    }
    lines.splice(index, last - index + 1, `${key} = ${encoded}`);
    return lines.join("\n");
  }
  lines.splice(end, 0, `${key} = ${encoded}`);
  return lines.join("\n");
}

export function removeTomlValue(text: string, path: string): string {
  const parts = path.split(".");
  const key = parts.pop() || "";
  const section = parts.join(".");
  const lines = text.split(/\r?\n/);
  let active = "";
  for (let index = 0; index < lines.length; index++) {
    const header = lines[index].match(/^\s*\[([^\]]+)\]\s*(?:#.*)?$/);
    if (header) { active = header[1]; continue; }
    if (active !== section || !new RegExp(`^\\s*${escapeRegex(key)}\\s*=`).test(lines[index])) continue;
    let last = index;
    let balance = tomlBalance(lines[index].slice(lines[index].indexOf("=") + 1));
    while (balance > 0 && last + 1 < lines.length) {
      last++;
      balance = tomlBalance(lines.slice(index, last + 1).join("\n"));
    }
    lines.splice(index, last - index + 1);
    return lines.join("\n");
  }
  return text;
}

export function removeTomlSection(text: string, section: string): string {
  const lines = text.split(/\r?\n/);
  let start = -1;
  let end = lines.length;
  for (let index = 0; index < lines.length; index++) {
    const header = lines[index].match(/^\s*\[([^\]]+)\]\s*(?:#.*)?$/);
    if (!header) continue;
    if (start >= 0) { end = index; break; }
    if (header[1] === section) start = index;
  }
  if (start < 0) return text;
  lines.splice(start, end - start);
  return lines.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd() + "\n";
}

export interface ArrayBlock {
  table: string;
  index: number;
  start: number;
  end: number;
  text: string;
}

export function extractArrayBlocks(text: string, table: string): ArrayBlock[] {
  const lines = text.split(/\r?\n/);
  const blocks: ArrayBlock[] = [];
  const header = new RegExp(`^\\s*\\[\\[${escapeRegex(table)}\\]\\]\\s*(?:#.*)?$`);
  for (let index = 0; index < lines.length; index++) {
    if (!header.test(lines[index])) continue;
    let end = index + 1;
    while (end < lines.length && !/^\s*\[\[?[^\]]+\]\]?\s*(?:#.*)?$/.test(lines[end])) end++;
    blocks.push({ table, index: blocks.length, start: index, end, text: lines.slice(index, end).join("\n").trimEnd() });
    index = end - 1;
  }
  return blocks;
}

export function extractArrayGroups(text: string, table: string): ArrayBlock[] {
  const lines = text.split(/\r?\n/);
  const blocks: ArrayBlock[] = [];
  const parent = new RegExp(`^\\s*\\[\\[${escapeRegex(table)}\\]\\]\\s*(?:#.*)?$`);
  for (let index = 0; index < lines.length; index++) {
    if (!parent.test(lines[index])) continue;
    let end = index + 1;
    while (end < lines.length) {
      const header = lines[end].match(/^\s*\[\[?([^\]]+)\]\]?\s*(?:#.*)?$/);
      if (header && header[1] === table) break;
      if (header && !header[1].startsWith(`${table}.`)) break;
      end++;
    }
    blocks.push({ table, index: blocks.length, start: index, end, text: lines.slice(index, end).join("\n").trimEnd() });
    index = end - 1;
  }
  return blocks;
}

export function replaceArrayGroups(text: string, table: string, replacements: string[]): string {
  const blocks = extractArrayGroups(text, table);
  const lines = text.split(/\r?\n/);
  for (let index = blocks.length - 1; index >= 0; index--) {
    const block = blocks[index];
    const replacement = replacements[index];
    lines.splice(block.start, block.end - block.start, ...(replacement ? replacement.split("\n") : []));
  }
  if (replacements.length > blocks.length) {
    for (const replacement of replacements.slice(blocks.length)) {
      if (lines.at(-1)?.trim()) lines.push("");
      lines.push(...replacement.split("\n"));
    }
  }
  return lines.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd() + "\n";
}

export function replaceArrayBlocks(text: string, table: string, replacements: string[]): string {
  const blocks = extractArrayBlocks(text, table);
  const lines = text.split(/\r?\n/);
  for (let index = blocks.length - 1; index >= 0; index--) {
    const block = blocks[index];
    const replacement = replacements[index];
    lines.splice(block.start, block.end - block.start, ...(replacement ? replacement.split("\n") : []));
  }
  if (replacements.length > blocks.length) {
    for (const replacement of replacements.slice(blocks.length)) {
      if (lines.at(-1)?.trim()) lines.push("");
      lines.push(...replacement.split("\n"));
    }
  }
  return lines.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd() + "\n";
}

export function appendTomlBlock(text: string, block: string): string {
  return `${text.replace(/\s*$/, "")}\n\n${block.trim()}\n`;
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
  if (!value) return "";
  if (value.startsWith('"')) {
    try { return JSON.parse(value); } catch { return value.slice(1, -1); }
  }
  if (value.startsWith("'")) return value.slice(1, -1);
  return value.replace(/\s+#.*$/, "").trim();
}

export function booleanValue(raw: string): boolean {
  return raw.trim().toLowerCase() === "true";
}

export function numberValue(raw: string, fallback = 0): number {
  const value = Number(stringValue(raw));
  return Number.isFinite(value) ? value : fallback;
}

export function lineValues(value: string): string[] {
  return value.split(/\r?\n|,/).map(entry => entry.trim()).filter(Boolean);
}

export function readBlockValue(block: string, key: string): string {
  const match = block.match(new RegExp(`^\\s*${escapeRegex(key)}\\s*=\\s*(.+)$`, "m"));
  return match?.[1]?.trim() || "";
}

export function inlineObjectValue(raw: string, key: string): string {
  const match = raw.match(new RegExp(`(?:^|[,\\s{])${escapeRegex(key)}\\s*=\\s*("(?:\\\\.|[^"\\\\])*"|'[^']*'|[^,}]+)`));
  return match ? stringValue(match[1]) : "";
}

export function conditionToml(type: string, target: string, count: number): string {
  const result: Record<string, unknown> = { type };
  if (target.trim()) result.id = target.trim();
  if (count > 1) result.count = count;
  return encodeToml(result);
}
