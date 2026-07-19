# ProgressiveStages React Editor Rehaul

## Purpose

The localhost editor must feel like a deliberate ProgressiveStages product. It must make a large
and powerful configuration system understandable without hiding advanced control. It must also
remain fully local, operator only, server authoritative, and compatible with existing stage files.

This document is the implementation and acceptance contract for the React editor. A feature is not
finished merely because a control exists. It is finished when a new user can discover it, understand
its effect, save it into the draft, validate it, apply it, and verify the result without opening a
configuration file.

## Current audit

The previous frontend used a small Preact shell and a large imperative controller.

| Surface | Previous size | Main problem |
|---|---:|---|
| Application shell | 69 lines | It supplied empty containers instead of owning product behavior. |
| Imperative controller | 2,438 lines | Rendering, state, parsing, network requests, modals, graph movement, and mutations lived together. |
| Style sheet | 241 lines | Many unrelated workflows reused the same generic cards and modal layout. |

This arrangement produced several visible problems.

1. Every feature looked equally important.
2. Stage identity, rules, progression, rewards, and advanced features formed one long page.
3. Important status lived in small text instead of a predictable status area.
4. The inspector was visually detached from the control it explained.
5. Empty states, add actions, edit actions, and destructive actions did not share one pattern.
6. Graph editing looked like a separate tool instead of another view of the same stage model.
7. The mod logo was absent, so the editor had little connection to ProgressiveStages or Minecraft.
8. The code structure made small interface improvements risky because every render rebuilt raw HTML.

## Product principles

### One stage, one workspace

Selecting a stage opens one stable workspace with focused sections. The user should never need to
understand `stage.toml`, `rules.toml`, and `progression.toml` unless they intentionally open source
mode.

### Plain language first

Controls explain what the player can do, when a rule is active, who owns progress, and what wins
when multiple rules match. Registry identifiers remain visible because they are important, but they
are presented beside readable names and explanations.

### Progressive disclosure

The normal workflow exposes identity, rules, progression, rewards, and layout. Detailed lifecycle,
formula, profile, template, variable, modifier, challenge, extension, and exact TOML controls remain
one click away in the same workspace.

### Server authority remains visible

The interface distinguishes saved draft state from live server state. Every mutation updates the
draft revision. Review shows the exact file changes. Apply validates, writes, reloads, synchronizes,
and reports the resulting server revision.

### No feature loss

The React rehaul must preserve every server request and every editing capability that existed before
the rehaul. It must also add the missing navigation, dashboard, contextual help, responsive behavior,
and direct feature discoverability described below.

## Visual direction

The editor uses a dark charcoal surface with warm gold accents taken from the current mod logo.
Gold identifies primary actions, selected stages, active navigation, and progression connections.
Green means valid, added, synchronized, or connected. Yellow means modified or awaiting review. Red
means removed, destructive, invalid, or blocked.

The actual 512 pixel ProgressiveStages logo appears in the application header and welcome state. A
small lock mark may be used for compact navigation, but it must not replace the logo in the product
identity area.

Cards use one radius system, one spacing scale, and one shadow scale. Controls use at least 40 pixel
targets. Text uses a clear hierarchy with one page title, one section title size, one body size, and
one metadata size. Motion is short and functional. Reduced motion settings disable nonessential
animation.

Minecraft influence comes from the lock imagery, progression connectors, item identifiers, subtle
pixel texture, advancement frame language, and warm mineral colors. The editor must not imitate the
Minecraft menu so closely that dense authoring becomes difficult.

## Information architecture

### Application header

The header contains the mod logo, product name, connection state, draft revision, live server
revision, undo, redo, validation, and review actions. These controls stay in the same place on every
screen.

### Primary navigation

The primary navigation contains these destinations.

1. `Overview` shows draft health, counts, recent stages, unsaved file changes, server connection, and
   a clear create stage action.
2. `Stages` opens the stage workspace and searchable stage library.
3. `Player layout` opens the complete progression graph with placement and branch editing.
4. `Main settings` renders the authoritative server setting schema.
5. `Registry` searches every catalog exposed by the running server.
6. `Extensions` lists Java and KubeJS registrations and capabilities.

### Stage workspace

The stage workspace uses focused tabs.

1. `Essentials` edits name, description, icon, ownership, visibility, tags, category, color, frame,
   reveal policy, background, coordinates, prerequisites, dependency policy, and stage slot behavior.
2. `Rules` edits permanent, temporary, triggered, allow, deny, lock, unlock, replacement, exclusion,
   priority, viewer, lifetime, and conditional behavior.
3. `Progression` edits grants, revokes, purchases, triggers, repeat behavior, cooldowns, scope, and
   quest, command, API, or KubeJS acquisition paths.
4. `Rewards and effects` edits reward items, effects, commands, teleportation, experience, costs,
   refunds, abilities, attributes, modifiers, and targeted drop changes.
5. `Advanced` edits challenges, variables, formulas, states, profiles, templates, and registered
   extension data.
6. `Source` edits each backing TOML file with explicit save and dirty state protection.

## Complete feature parity matrix

| Capability | React destination | Required behavior |
|---|---|---|
| Stage list and search | Stages library | Search names, identifiers, descriptions, categories, active stages, and archived stages. |
| Create stage | Overview and Stages | Ask for a readable name and optional namespace, preview the identifier, scaffold all files, then select the result. |
| Duplicate, rename, move | Stage action menu | Use in page dialogs and retain dependency updates supplied by the server. |
| Archive, restore, delete | Stage action menu | Explain the effect, require confirmation, and clearly separate destructive actions. |
| Import and export | Stage action menu | Preserve all package files and show success or failure in the normal notification system. |
| Identity and display | Essentials | Cover all stage and display metadata, including exact map coordinates. |
| Dependencies | Essentials and Player layout | Support `all`, `any`, and `at_least`, show ancestry, prevent loops, draw branches, and remove branches. |
| Stage slots | Essentials | Cover group, limit, deny, oldest replacement, priority replacement, all replacement, and group application. |
| Permanent rules | Rules | Cover every target category, selector prefix, action, result, priority, viewer policy, and exception. |
| Temporary and triggered rules | Rules | Cover conditions, duration, session, schedule, latch, priority, exclusions, and conditional allow or deny behavior. |
| Registry autocomplete | Rules and Registry | Query the correct server catalog, support mod filters, prefixes, pagination, and understandable labels. |
| Grants and revokes | Progression | Use the complete condition library for both directions with target, count, repeat, scope, priority, and cooldown. |
| Purchases | Progression | Cover multiple item costs, experience, cooldown, refund, requirement bypass, readable summaries, and registry search. |
| Rewards | Rewards and effects | Cover items, status effects, commands, teleportation, experience levels, and experience points. |
| Ability gates | Rewards and effects | Cover jump, elytra, sprint, swim, climb, and registered abilities. |
| Attributes and item modifiers | Rewards and effects | Cover context, selector, attribute, operation, amount, effects, duration, priority, and stacking behavior. |
| Drop modifiers | Rewards and effects | Cover source blocks, output items, tools, enchantments, owned stages, missing stages, conditions, multiplier, addition, priority, bounds, and exclusivity. |
| Challenges | Advanced | Cover sessions, steps, success, failure, attempts, budgets, timeouts, HUD placement, scale, color, icon, and animation. |
| Variables and formulas | Advanced | Cover scope, initial value, bounds, named formulas, and safe expression text. |
| States, profiles, templates | Advanced | Cover ownership states, proficiency thresholds, effects, includes, fragments, and merge behavior. |
| Main settings | Main settings | Render every schema supplied by the server with type correct controls, help, default, and restart information. |
| Stage graph | Player layout | Support category filtering, search, panning, zooming, fit, automatic layout, saved layout, direct branch creation, and direct branch removal. |
| Inspector | Context panel | Show help for the focused control, selected registry result, extension metadata, and priority conflicts. |
| Source mode | Source | Preserve unknown values, warn about unsaved text, switch backing files, and save into the normal draft. |
| Undo and redo | Header | Use server revisions and refresh the complete draft after each operation. |
| Validation | Header and review panel | Show stage count, errors, warnings, and actionable file context. |
| Review and apply | Review panel | Show added, modified, and removed files, byte changes, validation, progress, result, transaction, and rollback. |
| Operator chat | Server apply result | Send color coded details only when files changed and only to operators. |
| Collaboration | Stage action area | Add and remove collaborators through the existing server contract. |
| Simulation | Rules and review | Run the server candidate simulation and show its explanation and diff. |

## React architecture

The new frontend uses React, TypeScript, Vite, and CSS. It does not load an imperative controller.

| Module | Responsibility |
|---|---|
| `api` | Authenticated localhost requests, typed errors, timeouts, and server contract types. |
| `store` | Bootstrap state, selection, view state, draft mutations, undo, redo, validation, review, apply, and notifications. |
| `model` | Stage package discovery, readable summaries, dependencies, rules, progression, costs, and feature counts. |
| `toml` | Small preservation oriented TOML value and block editing helpers. |
| `components` | Reusable buttons, fields, cards, dialogs, panels, empty states, badges, and status feedback. |
| `features` | Overview, stage library, essentials, rules, progression, effects, advanced systems, graph, settings, registry, extensions, source, and review. |

React owns all rendering and event handling. State changes are immutable. Async actions expose busy,
success, and failure states. Dialogs have one implementation with focus restoration and Escape key
support. Destructive confirmations have one implementation. Toasts do not carry information that is
missing from the page itself.

## Server and security contract

The rehaul keeps the current private loopback model.

1. Minecraft starts the editor on `127.0.0.1` with a random port.
2. The URL fragment supplies the one time session secret to the application.
3. The application removes the fragment from browser history.
4. Every API request uses the bearer secret and same origin checks.
5. The server remains the only authority that validates and applies a draft.
6. Content Security Policy continues to reject inline scripts, inline styles, remote resources, and
   framing.
7. The application logo is served from the installed mod JAR by the same loopback server.

## Implementation sequence

### Checkpoint one, foundation

Replace Preact with React. Add typed API, store, model, TOML helpers, design tokens, logo delivery,
application shell, overview, navigation, notifications, dialogs, and error boundaries.

### Checkpoint two, primary stage authoring

Implement the stage library, create and management actions, Essentials, dependency policy, slots,
Rules, registry selection, Progression, costs, rewards, and source mode.

### Checkpoint three, complete power features

Implement ability and attribute controls, item modifiers, drop modifiers, challenges, variables,
formulas, states, profiles, templates, settings, registry explorer, extensions, simulation, and
collaboration.

### Checkpoint four, visual progression tools

Implement player layout filtering, search, pan, zoom, fit, drag placement, automatic arrangement,
saved arrangement, branch creation, branch removal, keyboard access, and clear connection states.

### Checkpoint five, release hardening

Remove the legacy runtime asset and route. Add architecture tests, packaged asset tests, feature
parity tests, Content Security Policy checks, type checks, production build checks, Java tests, a
clean build, and JAR content verification.

## Acceptance requirements

1. The production page renders through React and contains no Preact dependency.
2. The production page does not request or execute `legacy.js`.
3. The mod logo loads from the installed JAR without remote access or inline data.
4. Every row in the parity matrix has a visible destination and a functioning draft mutation path.
5. A user can create, configure, validate, review, apply, and roll back a stage without opening TOML.
6. Existing stage packages load without migration and unknown TOML remains preserved.
7. Browser refresh returns to a usable draft without corrupting server revision state.
8. The complete application supports keyboard navigation, visible focus, reduced motion, useful empty
   states, and readable errors.
9. Frontend type checking, production bundling, Java tests, and the clean Gradle build pass.
10. The JAR contains the logo, metadata, language data, and the complete React production bundle.

## Completion record

The rehaul was implemented on July 19, 2026. React owns the complete application shell, draft
state, dialogs, notifications, stage workspace, registry queries, settings, extension metadata,
review, and player layout. The earlier Preact shell and imperative `legacy.js` controller were
removed from source, production resources, the loopback route, and the final JAR.

Verification completed with `npm run check`, `npm test`, `npm run build`,
`npm audit --audit-level=high`, and `./gradlew clean test build --no-daemon`. The frontend suite ran
eight tests. The complete Java suite ran 141 tests with zero failures, zero errors, and zero skipped
tests. The final JAR contains the existing mod logo and the React `index.html`, `app.css`, `app.js`,
and `favicon.svg` assets. Exact artifact size and checksum are recorded in
`docs/verification/REHAUL_PHASES_2_TO_22.md`.
