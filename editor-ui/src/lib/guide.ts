export type GuidePlaceholder = { token: string; label: string; example: string; help: string };

export const GUIDE_CODE_POINT_LIMIT = 2048;
export const GUIDE_BYTE_LIMIT = 8 * 1024;

function placeholder(name: string, label: string, example: string, help: string): GuidePlaceholder {
  return { token: `{${name}}`, label, example, help };
}

export const GUIDE_PLACEHOLDERS: GuidePlaceholder[] = [
  placeholder("stage_name", "Stage name", "Miner", "The name players see for this stage."),
  placeholder("stage_requirements", "Unlock summary", "No required stages. Purchase option: 16x Lapis Lazuli, 2s cooldown. Stage group Beginner Paths: 0 of 2 slots are active. When full: Deny.", "Inserts the full visible unlock summary, including routes, costs, cooldowns, and group slots. Put it on its own line: it replaces the separate summary under How to unlock and may contain complete sentences. Do not put words like Complete before it. It says Unavailable when the server cannot safely confirm the route."),
  placeholder("remaining_requirements", "Remaining requirements", "Trigger route: mine 10 stone; Purchase route: need 4 Iron Ingots", "Shows missing checks separately for each trigger or purchase route, including stage-group limits and purchase cooldowns. Says Unavailable when the server cannot confirm them."),
  placeholder("stage_progress", "Stage progress", "Trigger route: 50%; purchase not ready", "Shows trigger progress for that route or whether a purchase is ready. A trigger percentage is not overall readiness.")
];

export const GUIDE_PLACEHOLDER_VALUES = {
  stage_name: "Miner",
  stage_requirements: "No required stages. Purchase option: 16x Lapis Lazuli, 2s cooldown. Stage group Beginner Paths: 0 of 2 slots are active. When full: Deny.",
  remaining_requirements: "Purchase route: need 16 Lapis Lazuli. Stage group Beginner Paths: 0 of 2 slots are active.",
  stage_progress: "Trigger route: 50%; purchase not ready"
};

export function guideTextMetrics(value: string) {
  const codePoints = Array.from(value).length;
  const bytes = new TextEncoder().encode(value).length;
  return { codePoints, bytes, valid: codePoints <= GUIDE_CODE_POINT_LIMIT && bytes <= GUIDE_BYTE_LIMIT };
}

export function truncateGuideText(value: string) {
  let result = "";
  for (const character of value) {
    const next = result + character;
    const metrics = guideTextMetrics(next);
    if (metrics.codePoints > GUIDE_CODE_POINT_LIMIT || metrics.bytes > GUIDE_BYTE_LIMIT) break;
    result = next;
  }
  return result;
}

export function expandGuideTemplate(template: string, values: Record<string, string> = GUIDE_PLACEHOLDER_VALUES) {
  let output = "";
  const unknown = new Set<string>();
  for (let index = 0; index < template.length;) {
    const current = template[index];
    if (current === "{" && template[index + 1] === "{") { output += "{"; index += 2; continue; }
    if (current === "}" && template[index + 1] === "}") { output += "}"; index += 2; continue; }
    if (current === "{") {
      const end = template.indexOf("}", index + 1);
      if (end >= 0) {
        const token = template.slice(index + 1, end);
        if (Object.prototype.hasOwnProperty.call(values, token)) output += values[token] || "";
        else { output += template.slice(index, end + 1); if (token.trim()) unknown.add(token); }
        index = end + 1;
        continue;
      }
    }
    output += current;
    index++;
  }
  return { text: output, unknown: [...unknown] };
}

export function hasInlineStageRequirements(template: string) {
  return template.split(/\r?\n/).some(line => {
    let index = 0;
    let hasToken = false;
    let surroundingText = "";
    while (index < line.length) {
      const current = line[index];
      if (current === "{" && line[index + 1] === "{") {
        surroundingText += "{";
        index += 2;
        continue;
      }
      if (current === "}" && line[index + 1] === "}") {
        surroundingText += "}";
        index += 2;
        continue;
      }
      if (current === "{") {
        const end = line.indexOf("}", index + 1);
        if (end >= 0) {
          if (line.slice(index + 1, end) === "stage_requirements") hasToken = true;
          else surroundingText += line.slice(index, end + 1);
          index = end + 1;
          continue;
        }
      }
      surroundingText += current;
      index++;
    }
    return hasToken && surroundingText.trim() !== "";
  });
}
