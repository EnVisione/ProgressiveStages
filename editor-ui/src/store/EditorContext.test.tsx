// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { EditorApi, EditorApiError } from "../lib/api";
import type { Bootstrap, ReviewResult } from "../types";
import { EditorProvider, useEditor } from "./EditorContext";

const validation = { valid: true, errors: [], warnings: [], stages: 1, revision: 4 };
const bootstrap = {
  draft: { id: "draft", files: {}, revision: 9, canUndo: false, canRedo: false },
  catalog: { revision: 1 }, capabilities: []
} as unknown as Bootstrap;

function Controls() {
  const editor = useEditor();
  return <>
    <button onClick={() => void editor.openReview()}>Review</button>
    <button onClick={() => void editor.validate()}>Validate</button>
    <button onClick={() => void editor.mutateFile("stages/chef.toml", "new source")}>Edit</button>
    <button onClick={() => void editor.mutateFile("stages/guide/stage.toml", current => current + "how_to_unlock = \"unlock\"\n")}>Save how to unlock</button>
    <button onClick={() => void editor.mutateFile("stages/guide/stage.toml", current => current + "next_steps = \"next\"\n")}>Save next steps</button>
    <button onClick={() => void editor.mutateFile("stages/guide/stage.toml", current => current + "where_to_find = \"here\"\n")}>Save location</button>
    <output>{editor.boot ? `Draft ${editor.boot.draft.revision}` : "Loading"}</output>
    <output>{editor.validationResult ? `Validation ${editor.validationResult.validatedRevision}` : "No current validation"}</output>
    <button onClick={() => void editor.apply()}>Apply</button>
    <output>{editor.review ? `Review ${editor.review.revision}` : "No review"}</output>
    {editor.notices.map(notice => <p key={notice.id}>{notice.message}</p>)}
  </>;
}

beforeEach(() => {
  vi.spyOn(EditorApi.prototype, "bootstrap").mockResolvedValue(bootstrap);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it("serializes quick field saves against the latest file and server revision", async () => {
  const guidePath = "stages/guide/stage.toml";
  vi.mocked(EditorApi.prototype.bootstrap).mockResolvedValue({
    ...bootstrap,
    draft: { ...bootstrap.draft, files: { [guidePath]: "[guide]\n" } }
  });
  const request = vi.spyOn(EditorApi.prototype, "request").mockImplementation(async payload => ({
    revision: Number(payload.revision) + 1, diff: [], canUndo: true, canRedo: false
  }));
  render(<EditorProvider><Controls/></EditorProvider>);
  await screen.findByText("Draft 9");

  fireEvent.click(screen.getByRole("button", { name: "Save how to unlock" }));
  fireEvent.click(screen.getByRole("button", { name: "Save next steps" }));
  fireEvent.click(screen.getByRole("button", { name: "Save location" }));

  await screen.findByText("Draft 12");
  const mutations = request.mock.calls.map(([payload]) => payload).filter(payload => payload.action === "mutate");
  expect(mutations.map(payload => payload.revision)).toEqual([9, 10, 11]);
  expect(mutations.map(payload => payload.content)).toEqual([
    "[guide]\nhow_to_unlock = \"unlock\"\n",
    "[guide]\nhow_to_unlock = \"unlock\"\nnext_steps = \"next\"\n",
    "[guide]\nhow_to_unlock = \"unlock\"\nnext_steps = \"next\"\nwhere_to_find = \"here\"\n"
  ]);
});

it("applies the displayed review revision and updates it when a new review opens", async () => {
  let revision = 4;
  const request = vi.spyOn(EditorApi.prototype, "request").mockImplementation(async payload => {
    if (payload.action === "review") return { revision, diff: [], validation } as ReviewResult;
    return { success: true, configurationRevision: 2 };
  });
  render(<EditorProvider><Controls/></EditorProvider>);
  fireEvent.click(screen.getByRole("button", { name: "Review" }));
  await screen.findByText("Review 4");
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  await waitFor(() => expect(request).toHaveBeenCalledWith({ action: "apply", revision: 4, confirmed: true }));
  revision = 5;
  fireEvent.click(screen.getByRole("button", { name: "Review" }));
  await screen.findByText("Review 5");
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  await waitFor(() => expect(request).toHaveBeenCalledWith({ action: "apply", revision: 5, confirmed: true }));
});

it("discards a rejected stale review and reloads the server draft before another apply", async () => {
  const request = vi.spyOn(EditorApi.prototype, "request").mockImplementation(async payload => {
    if (payload.action === "review") return { revision: 4, diff: [], validation };
    throw new EditorApiError("draft_conflict", "Review the current changes.", { currentRevision: 5 });
  });
  render(<EditorProvider><Controls/></EditorProvider>);
  fireEvent.click(screen.getByRole("button", { name: "Review" }));
  await screen.findByText("Review 4");
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  await screen.findByText("Review the current changes.");
  expect(screen.getByText("No review")).toBeTruthy();
  expect(EditorApi.prototype.bootstrap).toHaveBeenCalledTimes(2);
  request.mockClear();
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(request).not.toHaveBeenCalled();
});

it("hides validation after a draft mutation and ignores its delayed older response", async () => {
  let resolveValidation: (value: unknown) => void = () => {};
  const request = vi.spyOn(EditorApi.prototype, "request").mockImplementation(async payload => {
    if (payload.action === "validate") return new Promise(resolve => { resolveValidation = resolve; });
    if (payload.action === "mutate") return { revision: 10, diff: [], canUndo: true, canRedo: false };
    return {};
  });
  render(<EditorProvider><Controls/></EditorProvider>);
  await screen.findByText("Draft 9");
  fireEvent.click(screen.getByRole("button", { name: "Validate" }));
  await act(async () => resolveValidation({ ...validation, validatedRevision: 9 }));
  await screen.findByText("Validation 9");
  fireEvent.click(screen.getByRole("button", { name: "Validate" }));
  fireEvent.click(screen.getByRole("button", { name: "Edit" }));
  await screen.findByText("Draft 10");
  expect(screen.getByText("No current validation")).toBeTruthy();
  await act(async () => resolveValidation({ ...validation, validatedRevision: 9 }));
  expect(screen.getByText("No current validation")).toBeTruthy();
  expect(request).toHaveBeenCalledWith({ action: "mutate", path: "stages/chef.toml", content: "new source", revision: 9 });
});

it("retains field diagnostics from a failed apply and blocks the rejected review", async () => {
  const invalid = { ...validation, validatedRevision: 9, valid: false, errors: ["Invalid team override"],
    diagnostics: [{ severity: "ERROR", file: "stages/chef.toml", field: "stage.team_stage", code: "server_override", message: "Invalid team override" }] };
  vi.spyOn(EditorApi.prototype, "request").mockImplementation(async payload => payload.action === "review"
    ? { revision: 9, diff: [], validation }
    : { success: false, validation: invalid, explanation: "Correct the team override." });
  render(<EditorProvider><Controls/></EditorProvider>);
  await screen.findByText("Draft 9");
  fireEvent.click(screen.getByRole("button", { name: "Review" }));
  await screen.findByText("Review 9");
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  await screen.findByText("Correct the team override.");
  expect(screen.getByText("Validation 9")).toBeTruthy();
  vi.mocked(EditorApi.prototype.request).mockClear();
  fireEvent.click(screen.getByRole("button", { name: "Apply" }));
  expect(EditorApi.prototype.request).not.toHaveBeenCalled();
});

it("uses bootstrap validation only for its draft revision and refreshes capability warnings on validation", async () => {
  vi.mocked(EditorApi.prototype.bootstrap).mockResolvedValue({ ...bootstrap,
    validation: { ...validation, validatedRevision: 9, diagnostics: [], stageCapabilities: {
      teamProvider: "ABSENT", luckPerms: "ABSENT", supportedOwnership: [], supportedInboundModes: [],
      configuredGroupStatus: {}, configuredCommandStatus: {}, definitionRevision: 1
    } }
  });
  vi.spyOn(EditorApi.prototype, "request").mockResolvedValue({ revision: 10, diff: [], canUndo: true, canRedo: false });
  render(<EditorProvider><Controls/></EditorProvider>);
  await screen.findByText("Validation 9");
  fireEvent.click(screen.getByRole("button", { name: "Edit" }));
  await screen.findByText("Draft 10");
  expect(screen.getByText("No current validation")).toBeTruthy();
});
