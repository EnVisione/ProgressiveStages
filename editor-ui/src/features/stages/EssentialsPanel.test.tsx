// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { discoverStages } from "../../lib/model";
import { GUIDE_PLACEHOLDER_VALUES } from "../../lib/guide";
import { EssentialsPanel } from "./EssentialsPanel";

const editor = vi.hoisted(() => ({
  boot: { draft: { files: {} as Record<string, string> } },
  stages: [] as ReturnType<typeof discoverStages>,
  mutateFile: vi.fn(async (..._args: unknown[]) => {}),
  mutateFiles: vi.fn(async (..._args: unknown[]) => {}),
  openDialog: vi.fn(),
  closeDialog: vi.fn()
}));
vi.mock("../../store/EditorContext", () => ({ useEditor: () => editor }));
vi.mock("../../components/CatalogPicker", () => ({ CatalogPicker: () => null }));
afterEach(cleanup);

it("edits the authored what to do next guide fields", async () => {
  vi.clearAllMocks();
  const stagePath = "stages/guide/stage.toml";
  const files = { [stagePath]: '[stage]\nid = "pack:guide"\ndisplay_name = "Guide"\n' };
  editor.boot.draft.files = files;
  editor.stages = discoverStages(files);
  render(<EssentialsPanel stage={editor.stages[0]}/>);

  fireEvent.change(screen.getByLabelText(/How to unlock/), { target: { value: "Mine the 🪨 vein.\nReturn to camp." } });
  fireEvent.blur(screen.getByLabelText(/How to unlock/));
  fireEvent.change(screen.getByLabelText(/Guide recommendation/), { target: { value: "include" } });

  await waitFor(() => expect(editor.mutateFile).toHaveBeenCalled());
  const updates = editor.mutateFile.mock.calls.map(call => call[1]).filter((value): value is (source: string) => string => typeof value === "function");
  const edited = updates.reduce((source, update) => update(source), files[stagePath]);
  expect(edited).toContain("how_to_unlock");
  expect(edited).toContain('recommendation = "include"');
});

it("defaults each stage guide to on and saves an individual opt out", async () => {
  vi.clearAllMocks();
  const stagePath = "stages/guide/stage.toml";
  const files = { [stagePath]: '[stage]\nid = "pack:guide"\ndisplay_name = "Guide"\n' };
  editor.boot.draft.files = files;
  editor.stages = discoverStages(files);
  render(<EssentialsPanel stage={editor.stages[0]}/>);

  const toggle = screen.getByRole("checkbox", { name: "Show this stage's player guide" });
  expect((toggle as HTMLInputElement).checked).toBe(true);
  fireEvent.click(toggle);
  await waitFor(() => expect(editor.mutateFile).toHaveBeenCalled());
  const update = editor.mutateFile.mock.calls[0][1] as (source: string) => string;
  expect(update(files[stagePath])).toContain("enabled = false");
});

it("offers inert guide placeholders and a rendered preview", async () => {
  vi.clearAllMocks();
  const stagePath = "stages/guide/stage.toml";
  const files = { [stagePath]: '[stage]\nid = "pack:guide"\ndisplay_name = "Guide"\n' };
  editor.boot.draft.files = files;
  editor.stages = discoverStages(files);
  render(<EssentialsPanel stage={editor.stages[0]}/>);

  const howToUnlock = screen.getByLabelText(/How to unlock/) as HTMLTextAreaElement;
  fireEvent.change(howToUnlock, { target: { value: "Find {stage_name}. " } });
  howToUnlock.setSelectionRange(howToUnlock.value.length, howToUnlock.value.length);
  fireEvent.click(screen.getAllByRole("button", { name: "Insert placeholder" })[0]);
  expect(screen.getByRole("group", { name: "Guide placeholders" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Unlock summary" }));
  expect(howToUnlock.value).toBe("Find {stage_name}. {stage_requirements}");
  fireEvent.blur(howToUnlock);
  expect(screen.getByText("Example guide preview")).toBeTruthy();
  expect(screen.getByText(`Example preview. Find Guide. ${GUIDE_PLACEHOLDER_VALUES.stage_requirements}`)).toBeTruthy();
  await waitFor(() => expect(editor.mutateFile).toHaveBeenCalled());
});

it("uses the same full example in the overall guide preview", () => {
  vi.clearAllMocks();
  const stagePath = "stages/guide/stage.toml";
  const files = { [stagePath]: '[stage]\nid = "pack:guide"\ndisplay_name = "Guide"\n[guide]\nhow_to_unlock = "{stage_requirements}"\n' };
  editor.boot.draft.files = files;
  editor.stages = discoverStages(files);
  render(<EssentialsPanel stage={editor.stages[0]}/>);

  expect(screen.getByText("Example guide preview")).toBeTruthy();
  expect(screen.getByText(GUIDE_PLACEHOLDER_VALUES.stage_requirements)).toBeTruthy();
});

it("warns when the full unlock summary is embedded in author text without changing it", () => {
  vi.clearAllMocks();
  const stagePath = "stages/guide/stage.toml";
  const authoredText = "Collect {stage_requirements}.";
  const files = { [stagePath]: `[stage]\nid = "pack:guide"\ndisplay_name = "Guide"\n[guide]\nhow_to_unlock = "${authoredText}"\n` };
  editor.boot.draft.files = files;
  editor.stages = discoverStages(files);
  render(<EssentialsPanel stage={editor.stages[0]}/>);

  expect(screen.getByText(/Put \{stage_requirements\} on its own line/)).toBeTruthy();
  expect((screen.getByLabelText(/How to unlock/) as HTMLTextAreaElement).value).toBe(authoredText);
});
