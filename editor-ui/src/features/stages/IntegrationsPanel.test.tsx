// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { discoverStages } from "../../lib/model";
import { parseInteractions, parseLuckPerms, parseCommandPermissions } from "../../lib/integrations";
import { IntegrationsPanel } from "./IntegrationsPanel";
import fixtureText from "../../lib/fixtures/toml-preservation.json?raw";

const editor = vi.hoisted(() => ({
  boot: { draft: { files: {} as Record<string, string> } },
  mutateFile: vi.fn(async (_path: string, _content: string | null | ((current: string) => string), _message?: string) => {}),
  openDialog: vi.fn(),
  closeDialog: vi.fn()
}));

vi.mock("../../store/EditorContext", () => ({ useEditor: () => editor }));

const stagePath = "stages/chef/stage.toml";
const rulesPath = "stages/chef/rules.toml";
const original = '# Preserve this condition.\n[[interactions]]\ntype = "item_into_inventory"\nheld_item = "id:minecraft:carrot"\ntarget_kind = "block"\ntarget = "id:minecraft:chest"\n[interactions.while]\ntype = "dimension"\nid = "minecraft:the_end"\n';

beforeEach(() => {
  vi.clearAllMocks();
  editor.boot.draft.files = {
    [stagePath]: '[stage]\nid = "chef"\nname = "Chef"\n',
    [rulesPath]: original
  };
});

afterEach(cleanup);

function openInteraction() {
  render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
  fireEvent.click(screen.getByRole("button", { name: "Add interaction" }));
  render(editor.openDialog.mock.calls[0][0].content);
}

describe("guided selective insertion", () => {
  it("saves player chat masking through Access without changing other stage fields", async () => {
    editor.boot.draft.files[stagePath] = '[stage]\nid = "chef"\n# keep this field\nicon = "minecraft:bread"\n';
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    fireEvent.click(screen.getByRole("checkbox", { name: "Require this stage for player chat" }));
    await waitFor(() => expect(editor.mutateFile).toHaveBeenCalledWith(stagePath,
      expect.any(Function), "Chat access saved"));
    fireEvent.change(screen.getByRole("textbox", { name: "Obfuscate these terms until owned" }), { target: { value: "Create\nMechanical Press" } });
    fireEvent.blur(screen.getByRole("textbox", { name: "Obfuscate these terms until owned" }));
    await waitFor(() => expect(editor.mutateFile).toHaveBeenCalledWith(stagePath,
      expect.any(Function), "Chat mask terms saved"));
    const first = editor.mutateFile.mock.calls[0][1] as (current: string) => string;
    const second = editor.mutateFile.mock.calls[1][1] as (current: string) => string;
    const saved = second(first(editor.boot.draft.files[stagePath]));
    expect(saved).toContain("lock_access = true");
    expect(saved).toContain('mask_terms = ["Create", "Mechanical Press"]');
    expect(saved).toContain('# keep this field\nicon = "minecraft:bread"');
  });

  it("makes game mode grants personal and saves the chosen mode in one draft update", async () => {
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    fireEvent.click(screen.getByRole("checkbox", { name: "Creative" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Spectator" }));
    await waitFor(() => expect(editor.mutateFile).toHaveBeenCalledTimes(2));
    const [path, update] = editor.mutateFile.mock.calls[0] as unknown as [string, (current: string) => string];
    const second = editor.mutateFile.mock.calls[1][1] as (current: string) => string;
    const source = second(update(editor.boot.draft.files[stagePath]));
    expect(path).toBe(stagePath);
    expect(source).toContain("team_stage = false");
    expect(source).toContain('grant_in = ["creative", "spectator"]');
  });

  it("recognizes mixed-case game mode names and removes a toggled mode", async () => {
    editor.boot.draft.files[stagePath] += '[gamemode]\ngrant_in = [" Creative ", "SPECTATOR"]\n';
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    expect(screen.getByRole("checkbox", { name: "Creative" })).toHaveProperty("checked", true);
    fireEvent.click(screen.getByRole("checkbox", { name: "Creative" }));
    const update = editor.mutateFile.mock.calls[0][1] as (current: string) => string;
    expect(update(editor.boot.draft.files[stagePath])).toContain('grant_in = ["spectator"]');
  });

  it("keeps invalid duplicate chat terms in the editor with a visible error", () => {
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    const field = screen.getByRole("textbox", { name: "Obfuscate these terms until owned" });
    fireEvent.change(field, { target: { value: "Create\ncreate" } });
    fireEvent.blur(field);
    expect(screen.getByRole("alert").textContent).toContain("distinct terms");
    expect(editor.mutateFile).not.toHaveBeenCalled();
  });
  it("rejects unicode control characters before saving chat terms", () => {
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    const field = screen.getByRole("textbox", { name: "Obfuscate these terms until owned" });
    fireEvent.change(field, { target: { value: "Create\u0085" } });
    fireEvent.blur(field);
    expect(screen.getByRole("alert").textContent).toContain("control characters");
    expect(editor.mutateFile).not.toHaveBeenCalled();
  });
  it("adds the Selling Bin pair inside an existing inline array", async () => {
    editor.boot.draft.files[rulesPath] = "interactions=[] # Keep.\n[extension]\nvalue='keep'\n";
    openInteraction();
    fireEvent.change(screen.getByLabelText(/Held item selector/), { target: { value: "id:minecraft:bread" } });
    fireEvent.change(screen.getByLabelText(/Target block selector/), { target: { value: "id:selling_bin:selling_bin" } });
    fireEvent.click(screen.getByRole("checkbox", { name: /Also restrict GUI insertion/ }));
    fireEvent.click(screen.getByRole("button", { name: "Save interaction" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(parseInteractions(source).map(row => row.type)).toEqual(["item_on_block", "item_into_inventory"]);
    expect(source).not.toContain("[[interactions]]");
    expect(source).toContain("# Keep.\n[extension]\nvalue='keep'\n");
  });

  it("opens and saves an inline inbound mapping with its existing context values", async () => {
    editor.boot.draft.files[stagePath] = 'stage={id="chef"}\nluckperms={inbound=[{id="chef_rank",groups=["chef"],contexts={"server.name"=["local"]},extra="keep"}]}\n';
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    fireEvent.click(within(screen.getByText("chef_rank").closest("article")!).getByRole("button", { name: "Edit" }));
    render(editor.openDialog.mock.calls[0][0].content);
    expect(screen.getByLabelText("Context 1 key")).toHaveProperty("value", "server.name");
    fireEvent.change(screen.getByLabelText("Context 1 value 1"), { target: { value: "remote" } });
    fireEvent.click(screen.getByRole("button", { name: "Save inbound mapping" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(parseLuckPerms(source).inbound[0].contexts).toEqual({ "server.name": ["remote"] });
    expect(source).toContain('extra="keep"');
    expect(source).not.toContain("permissions");
    expect(source).not.toContain("match");
  });

  it("changes ownership and retention through the controls for inline stage source", async () => {
    const source = "stage={id='chef', team_stage=false}\nluckperms={enabled=false, inbound_mode='permanent', extension={note='keep'}} # Keep.\n";
    editor.boot.draft.files[stagePath] = source;
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    expect(screen.getByLabelText(/Stage ownership/)).toHaveProperty("value", "personal");
    expect(screen.getByLabelText(/Inbound retention/)).toHaveProperty("value", "permanent");
    fireEvent.change(screen.getByLabelText(/Stage ownership/), { target: { value: "team" } });
    await waitFor(() => expect(editor.mutateFile).toHaveBeenCalledWith(stagePath, source.replace("team_stage=false", "team_stage=true"), "Stage ownership saved"));
    fireEvent.change(screen.getByLabelText(/Inbound retention/), { target: { value: "synchronized" } });
    await waitFor(() => expect(editor.mutateFile).toHaveBeenCalledWith(stagePath, source.replace("inbound_mode='permanent'", 'inbound_mode="synchronized"'), "LuckPerms retention saved"));
  });

  it("edits a quoted interaction without touching multiline text or its activation", async () => {
    const fixture = JSON.parse(fixtureText).find((entry: { operation: string }) => entry.operation === "interaction");
    editor.boot.draft.files[rulesPath] = fixture.before;
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    render(editor.openDialog.mock.calls[0][0].content);
    expect(screen.getByLabelText(/Held item selector/)).toHaveProperty("value", "id:minecraft:bread");
    fireEvent.change(screen.getByLabelText(/Held item selector/), { target: { value: "id:minecraft:carrot" } });
    fireEvent.click(screen.getByRole("button", { name: "Save interaction" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [path, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(path).toBe(rulesPath);
    expect(source).toBe(fixture.after);
  });

  it("saves both bread restrictions in one draft mutation while preserving existing source", async () => {
    openInteraction();
    fireEvent.change(screen.getByLabelText(/Held item selector/), { target: { value: "id:minecraft:bread" } });
    fireEvent.change(screen.getByLabelText(/Target block selector/), { target: { value: "id:selling_bin:selling_bin" } });
    fireEvent.click(screen.getByRole("checkbox", { name: /Also restrict GUI insertion/ }));
    fireEvent.change(screen.getByLabelText(/Inventory rule priority/), { target: { value: "150" } });
    fireEvent.click(screen.getByRole("button", { name: "Save interaction" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    expect(editor.mutateFile).toHaveBeenCalledOnce();
    const [path, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(path).toBe(rulesPath);
    expect(source.startsWith(original)).toBe(true);
    expect(parseInteractions(source).slice(1)).toMatchObject([
      { type: "item_on_block", heldItem: "id:minecraft:bread", targetBlock: "id:selling_bin:selling_bin" },
      { type: "item_into_inventory", heldItem: "id:minecraft:bread", targetKind: "block", target: "id:selling_bin:selling_bin", priority: 150 }
    ]);
    expect(source).not.toContain("block_right_click");
    expect(source).not.toContain("all:*");
  });

  it("keeps the original wildcard preset a standalone held item rule", async () => {
    openInteraction();
    fireEvent.click(screen.getByRole("button", { name: "All items" }));
    expect(screen.getByText(/whole block/).textContent).toContain("all:*");
    expect(screen.getByText(/whole block/).textContent).toContain("empty hand menu open");
    fireEvent.click(screen.getByRole("button", { name: "Save interaction" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    const rows = parseInteractions(source);
    expect(rows).toHaveLength(2);
    expect(rows[1]).toMatchObject({ type: "item_on_block", heldItem: "all:*", targetBlock: "id:selling_bin:selling_bin" });
  });

  it("discards the insertion pair when the dialog is canceled", () => {
    openInteraction();
    fireEvent.click(screen.getByRole("checkbox", { name: /Also restrict GUI insertion/ }));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(editor.closeDialog).toHaveBeenCalledOnce();
    expect(editor.mutateFile).not.toHaveBeenCalled();
  });
});


describe.each(["inbound", "outbound"] as const)("guided %s contexts", direction => {
  function openMapping() {
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    fireEvent.click(screen.getByRole("button", { name: `Add ${direction} mapping` }));
    render(editor.openDialog.mock.calls[0][0].content);
    fireEvent.change(screen.getByLabelText(direction === "inbound" ? /LuckPerms groups/ : /Permission node/), { target: { value: direction === "inbound" ? "chef" : "profession.chef" } });
    fireEvent.click(screen.getByRole("button", { name: "Add context" }));
    fireEvent.change(screen.getByLabelText("Context 1 key"), { target: { value: "server.name" } });
    fireEvent.change(screen.getByLabelText("Context 1 value 1"), { target: { value: "first,second\nthird" } });
  }

  it("saves individual values without treating commas or newlines as separators", async () => {
    openMapping();
    fireEvent.click(screen.getByRole("button", { name: "Add value to context 1" }));
    fireEvent.change(screen.getByLabelText("Context 1 value 2"), { target: { value: "other" } });
    fireEvent.click(screen.getByRole("button", { name: `Save ${direction} mapping` }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [path, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(path).toBe(stagePath);
    expect(parseLuckPerms(source)[direction][0].contexts).toEqual({ "server.name": ["first,second\nthird", "other"] });
  });

  it("retains duplicate rows until the author corrects them", async () => {
    openMapping();
    fireEvent.click(screen.getByRole("button", { name: "Add context" }));
    fireEvent.change(screen.getByLabelText("Context 2 key"), { target: { value: "server.name" } });
    fireEvent.change(screen.getByLabelText("Context 2 value 1"), { target: { value: "duplicate" } });
    fireEvent.click(screen.getByRole("button", { name: `Save ${direction} mapping` }));
    expect(await screen.findByRole("alert")).toHaveProperty("textContent", expect.stringContaining("appears more than once"));
    expect(editor.mutateFile).not.toHaveBeenCalled();
    expect(editor.closeDialog).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Context 2 value 1")).toHaveProperty("value", "duplicate");
    fireEvent.click(screen.getByRole("button", { name: "Remove context 2" }));
    fireEvent.click(screen.getByRole("button", { name: `Save ${direction} mapping` }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
  });

  it("preserves input after a rejected save and supports retry", async () => {
    openMapping();
    editor.mutateFile.mockRejectedValueOnce(new Error("The draft revision changed. Reopen the draft before retrying."));
    fireEvent.click(screen.getByRole("button", { name: `Save ${direction} mapping` }));
    expect(await screen.findByRole("alert")).toHaveProperty("textContent", expect.stringContaining("draft revision changed"));
    expect(editor.closeDialog).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Context 1 value 1")).toHaveProperty("value", "first,second\nthird");
    fireEvent.click(screen.getByRole("button", { name: `Save ${direction} mapping` }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
  });

  it("allows removing individual values and cancels without changing source", () => {
    openMapping();
    fireEvent.click(screen.getByRole("button", { name: "Add value to context 1" }));
    fireEvent.change(screen.getByLabelText("Context 1 value 2"), { target: { value: "keep" } });
    fireEvent.click(screen.getByRole("button", { name: "Remove context 1 value 1" }));
    expect(screen.getByLabelText("Context 1 value 1")).toHaveProperty("value", "keep");
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(editor.mutateFile).not.toHaveBeenCalled();
    expect(editor.closeDialog).toHaveBeenCalledOnce();
  });
});


describe("guided command and interaction edits", () => {
  function openCommand(existing = false) {
    if (existing) editor.boot.draft.files[stagePath] += "[[command_permissions]]\nid = 'home_gate' # Keep this note.\npath = 'sethome'\n";
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    if (existing) fireEvent.click(within(screen.getByText("home_gate").closest("article")!).getByRole("button", { name: "Edit" }));
    else fireEvent.click(screen.getByRole("button", { name: "Add command gate" }));
    render(editor.openDialog.mock.calls[0][0].content);
  }

  it("defaults new command gates to descendants and saves an explicit opt out", async () => {
    openCommand();
    const toggle = screen.getByRole("checkbox", { name: /Gate descendants/ });
    expect(toggle).toHaveProperty("checked", true);
    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole("button", { name: "Save command gate" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(parseCommandPermissions(source)[0].descendants).toBe(false);
  });

  it("edits an existing command path without inserting an omitted default", async () => {
    openCommand(true);
    expect(screen.getByRole("checkbox", { name: /Gate descendants/ })).toHaveProperty("checked", true);
    fireEvent.change(screen.getByLabelText(/Command literal path/), { target: { value: "home" } });
    fireEvent.click(screen.getByRole("button", { name: "Save command gate" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(parseCommandPermissions(source)[0]).toMatchObject({ id: "home_gate", path: "home", descendants: true });
    expect(source).not.toContain("descendants =");
    expect(source).toContain("# Keep this note.");
  });

  it("keeps command input available after a failed save", async () => {
    openCommand(true);
    editor.mutateFile.mockRejectedValueOnce(new Error("The draft revision changed."));
    fireEvent.change(screen.getByLabelText(/Command literal path/), { target: { value: "home" } });
    fireEvent.click(screen.getByRole("button", { name: "Save command gate" }));
    expect(await screen.findByRole("alert")).toHaveProperty("textContent", "The draft revision changed.");
    expect(editor.closeDialog).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Command literal path/)).toHaveProperty("value", "home");
  });

  it("edits inventory priority while preserving conditional activation", async () => {
    render(<IntegrationsPanel stage={discoverStages(editor.boot.draft.files)[0]}/>);
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    render(editor.openDialog.mock.calls[0][0].content);
    fireEvent.change(screen.getByLabelText("Priority"), { target: { value: "150" } });
    fireEvent.click(screen.getByRole("button", { name: "Save interaction" }));
    await waitFor(() => expect(editor.closeDialog).toHaveBeenCalledOnce());
    const [, source] = editor.mutateFile.mock.calls[0] as unknown as [string, string];
    expect(parseInteractions(source)[0]).toMatchObject({ type: "item_into_inventory", priority: 150 });
    expect(source).toContain('[interactions.while]\ntype = "dimension"\nid = "minecraft:the_end"');
  });

  it("keeps interaction input available after a failed save", async () => {
    openInteraction();
    fireEvent.click(screen.getByRole("button", { name: "Armor tag" }));
    editor.mutateFile.mockRejectedValueOnce(new Error("The draft revision changed."));
    fireEvent.click(screen.getByRole("button", { name: "Save interaction" }));
    expect(await screen.findByRole("alert")).toHaveProperty("textContent", "The draft revision changed.");
    expect(editor.closeDialog).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Held item selector/)).toHaveProperty("value", "tag:c:armors");
  });
});
