// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Badge, Button, Field } from "./ui";

afterEach(cleanup);

it("shows stage-search help without changing the draft", () => {
  const onChange = vi.fn();
  render(<Field label="Find a stage"><input defaultValue="Mage" onChange={onChange}/></Field>);
  const trigger = screen.getByRole("button", { name: "Show field explanation" });
  const descriptionIds = trigger.getAttribute("aria-describedby")?.split(" ") || [];
  expect(descriptionIds).toHaveLength(2);
  expect(document.getElementById(descriptionIds[0])?.textContent).toBe("Find a stage");
  expect(document.getElementById(descriptionIds[1])?.textContent).toContain("Search for a stage by its name or ID");
  expect(trigger.getAttribute("aria-expanded")).toBe("false");
  fireEvent.click(trigger);
  expect(trigger.getAttribute("aria-expanded")).toBe("true");
  expect(screen.getByRole("tooltip").textContent).toContain("Search for a stage by its name or ID");
  fireEvent.keyDown(trigger, { key: "Enter" });
  expect(trigger.getAttribute("aria-expanded")).toBe("false");
  fireEvent.keyDown(trigger, { key: " " });
  expect(trigger.getAttribute("aria-expanded")).toBe("true");
  expect(onChange).not.toHaveBeenCalled();
  expect((screen.getByDisplayValue("Mage") as HTMLInputElement).value).toBe("Mage");
});

it("explains a cooldown with its unit and keeps the draft untouched", () => {
  const onChange = vi.fn();
  render(<Field label="Cooldown"><input defaultValue="30s" onChange={onChange}/></Field>);
  const trigger = screen.getByRole("button", { name: "Show field explanation" });
  fireEvent.click(trigger);
  expect(screen.getByRole("tooltip").textContent).toContain("30s");
  expect(screen.getByRole("tooltip").textContent).toContain("before this trigger can count again");
  expect(onChange).not.toHaveBeenCalled();
});

it("exposes action and status explanations on focus without activating an action", () => {
  const onClick = vi.fn();
  render(<><Button onClick={onClick}>Remove</Button><Badge tone="danger">Blocked</Badge></>);
  const action = screen.getByRole("button", { name: "Remove" });
  const badge = screen.getByText("Blocked");
  fireEvent.focus(action);
  expect(action.getAttribute("aria-describedby")).toBeTruthy();
  expect(screen.getByText(/Remove this entry from the draft/)).toBeTruthy();
  fireEvent.focus(badge);
  expect(badge.getAttribute("tabindex")).toBe("0");
  expect(screen.getByText(/Correct them before applying/)).toBeTruthy();
  expect(onClick).not.toHaveBeenCalled();
});
