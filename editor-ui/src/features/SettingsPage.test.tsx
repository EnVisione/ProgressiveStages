// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SettingsPage } from "./SettingsPage";
import type { FieldSchema } from "../types";

const editor = vi.hoisted(() => ({
  boot: {
    draft: { files: { "progressivestages.toml": "[enforcement]\nregion_tick_frequency = 5\n" } },
    schemas: [{
      id: "progressivestages:settings/enforcement/region_tick_frequency",
      file: "progressivestages.toml",
      path: "enforcement.region_tick_frequency",
      label: "Region tick frequency",
      help: "How often regions are checked.",
      type: "INTEGER",
      defaultValue: 20,
      required: false,
      prefixModes: [],
      enumValues: [],
      capabilities: [],
      restartRequirement: "NONE",
      controlHints: { min: 1, max: 100, generated: true }
    }] as FieldSchema[]
  },
  hasLocalErrors: false,
  setLocalError: vi.fn(),
  validate: vi.fn(),
  mutateFile: vi.fn(async (..._args: unknown[]) => {})
}));

vi.mock("../store/EditorContext", () => ({ useEditor: () => editor }));

beforeEach(() => {
  vi.clearAllMocks();
  editor.mutateFile.mockResolvedValue(undefined);
});
afterEach(cleanup);

describe("settings range controls", () => {
  it("rejects values outside the schema range before mutating the draft", async () => {
    render(<SettingsPage />);
    const input = screen.getByDisplayValue("5");
    fireEvent.change(input, { target: { value: "0" } });
    fireEvent.blur(input);
    expect((await screen.findByRole("alert")).textContent).toContain("at least 1");
    expect(editor.mutateFile).not.toHaveBeenCalled();

    fireEvent.change(input, { target: { value: "8" } });
    fireEvent.blur(input);
    await waitFor(() => expect(editor.mutateFile).toHaveBeenCalledOnce());
  });
});

describe("boolean settings", () => {
  it("preserves quick changes to separate settings in the latest TOML draft", async () => {
    const source = "[general]\nenable_stage_guide = true\n[interface]\ncompact_mode = true\n";
    editor.boot.draft.files["progressivestages.toml"] = source;
    editor.boot.schemas = [{
      id: "progressivestages:settings/general/enable_stage_guide",
      file: "progressivestages.toml",
      path: "general.enable_stage_guide",
      label: "Enable stage guide",
      help: "Show the guide in the stages menu.",
      type: "BOOLEAN",
      defaultValue: true,
      required: false,
      prefixModes: [],
      enumValues: [],
      capabilities: [],
      restartRequirement: "NONE",
      controlHints: { min: 0, max: 1, generated: true }
    }, {
      id: "progressivestages:settings/interface/compact_mode",
      file: "progressivestages.toml",
      path: "interface.compact_mode",
      label: "Compact menu",
      help: "Use a compact menu.",
      type: "BOOLEAN",
      defaultValue: true,
      required: false,
      prefixModes: [],
      enumValues: [],
      capabilities: [],
      restartRequirement: "NONE",
      controlHints: { min: 0, max: 1, generated: true }
    }];

    render(<SettingsPage />);
    fireEvent.click(screen.getByRole("checkbox", { name: "Enable stage guide" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Compact menu" }));

    await waitFor(() => {
      expect(editor.mutateFile).toHaveBeenCalledTimes(2);
    });

    let content = source;
    for (const [path, update] of editor.mutateFile.mock.calls) {
      expect(path).toBe("progressivestages.toml");
      expect(typeof update).toBe("function");
      content = (update as (current: string) => string)(content);
    }
    expect(content).toContain("enable_stage_guide = false");
    expect(content).toContain("compact_mode = false");
  });
});
