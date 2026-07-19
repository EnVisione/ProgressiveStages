(() => {
  "use strict";

  const token = decodeURIComponent(location.hash.slice(1));
  history.replaceState(null, "", location.pathname);

  const CATEGORIES = {
    items: { label: "Items", catalog: "items", actions: ["use", "pickup", "inventory", "hotbar", "mouse_pickup", "drop"] },
    blocks: { label: "Blocks", catalog: "blocks", actions: ["place", "break", "interact"] },
    fluids: { label: "Fluids", catalog: "fluids", actions: ["pickup", "place", "flow", "submerge"] },
    recipes: { label: "Recipes", catalog: "recipes", actions: ["craft", "automate", "display"] },
    crops: { label: "Crops", catalog: "blocks", actions: ["plant", "grow", "bonemeal", "harvest"] },
    dimensions: { label: "Dimensions", catalog: "dimensions", actions: ["enter", "portal", "teleport"] },
    enchants: { label: "Enchantments", catalog: "enchantments", actions: ["table", "anvil", "trade", "hold"] },
    entities: { label: "Mobs and entities", catalog: "entities", actions: ["attack", "interact", "mount"] },
    interactions: { label: "Interactions", catalog: "items", actions: ["block_right_click", "item_on_block", "item_on_entity"] },
    loot: { label: "Loot", catalog: "loot_tables", actions: ["generate", "open", "drop"] },
    mobs: { label: "Mob spawning", catalog: "entities", actions: ["spawn", "replace"] },
    pets: { label: "Pets", catalog: "entities", actions: ["tame", "breed", "command", "ride"] },
    screens: { label: "Menus and screens", catalog: "menus", actions: ["open"] },
    trades: { label: "Villager trades", catalog: "items", actions: ["display", "purchase"] },
    professions: { label: "Villager professions", catalog: "professions", actions: ["trade"] },
    advancements: { label: "Advancements", catalog: "advancements", actions: ["display", "toast"] },
    structures: { label: "Structures", catalog: "structures", actions: ["enter", "break", "place", "open_container", "item_use", "interact_block", "interact_entity"] },
    regions: { label: "Regions", catalog: "dimensions", actions: ["enter", "break", "place", "explode", "spawn"] },
    curios: { label: "Curios slots", catalog: "curios_slots", actions: ["equip", "retain"] },
    ores: { label: "Ore visuals and drops", catalog: "blocks", actions: ["display", "drop"] },
    beacon: { label: "Beacon effects", catalog: "effects", actions: ["apply"] },
    brewing: { label: "Brewing", catalog: "potions", actions: ["brew", "take"] },
    abilities: { label: "Player abilities", catalog: "abilities", actions: ["use"] }
  };

  const ACTION_LABELS = {
    use: "Use the target", pickup: "Pick up the target", inventory: "Keep in inventory",
    hotbar: "Keep in hotbar", mouse_pickup: "Move with the cursor", drop: "Drop the target",
    place: "Place the target", break: "Break the target", interact: "Interact with the target",
    flow: "Allow fluid flow", submerge: "Enter the fluid", craft: "Craft the recipe",
    automate: "Craft through automation", display: "Show in a viewer", plant: "Plant the crop",
    grow: "Let the crop grow", bonemeal: "Use bonemeal", harvest: "Harvest the crop",
    enter: "Enter the target", leave: "Leave the target", stay: "Stay inside for more time",
    portal: "Use a portal", teleport: "Teleport there", table: "Use at an enchanting table",
    anvil: "Use in an anvil", trade: "Trade for the target", hold: "Keep the enchantment",
    attack: "Attack the entity", mount: "Mount the entity", block_right_click: "Right click a block",
    item_on_block: "Use an item on a block", item_on_entity: "Use an item on an entity",
    generate: "Generate the loot", open: "Open the target", spawn: "Spawn the entity",
    replace: "Replace the entity", tame: "Tame the pet", breed: "Breed the pet",
    command: "Command the pet", ride: "Ride the pet", purchase: "Purchase the trade",
    toast: "Show the toast", explode: "Damage with explosions", equip: "Equip the target",
    retain: "Keep the target equipped", apply: "Apply the effect", brew: "Brew the potion",
    take: "Take the brewed potion", perform: "Use the ability", open_container: "Open containers inside",
    item_use: "Use items inside", interact_block: "Interact with blocks inside", interact_entity: "Interact with entities inside"
  };

  const CONDITIONS = [
    ["none", "Always active", "", "This rule has no extra location or event requirement."],
    ["stage_owned", "Owns another stage", "stages", "The rule is active while the player owns the selected stage."],
    ["dimension", "Inside a dimension", "dimensions", "The rule follows the player into and out of the selected dimension."],
    ["biome", "Inside a biome", "biomes", "The rule is active only while the player is inside the selected biome."],
    ["structure", "Inside an assigned structure", "structures", "The rule is active while the player is inside an exact structure session supplied by a compatible mod."],
    ["weather", "During weather", "", "Use rain thunder or clear as the target value."],
    ["kill", "Kill a mob", "entities"], ["mine", "Mine a block", "blocks"],
    ["craft", "Craft an item", "items"], ["pickup", "Pick up an item", "items"],
    ["use", "Use an item", "items"], ["advancement", "Earn an advancement", "advancements"],
    ["item_possession", "Own an item", "items"], ["effect", "Have an effect", "effects"],
    ["enter_structure", "Enter an assigned structure", "structures", "Matches when the player enters the selected exact structure session."],
    ["leave_structure", "Leave an assigned structure", "structures", "Matches when the player leaves the selected exact structure session."],
    ["structure_time", "Stay inside an assigned structure", "structures", "Required amount is the number of seconds spent inside the selected exact structure session."],
    ["tame", "Tame a mob", "entities"],
    ["kill_with_item", "Kill with an item", "items"], ["fish", "Catch an item", "items"],
    ["sleep", "Sleep", ""], ["ride", "Ride an entity", "entities"],
    ["level", "Reach an XP level", ""], ["play_time", "Reach play time", ""],
    ["death", "Player dies", "", "Matches the selected player's own death count."],
    ["other_player_death", "Another player dies", "", "Matches when any other online player dies. The player does not need to be the killer."],
    ["respawn", "Player respawns", "", "Matches after the selected player returns after death."],
    ["player_kill", "Defeat another player", "", "Matches when the selected player defeats another player."],
    ["damage_taken", "Take damage", "", "Required amount is cumulative damage taken in the active counter window."],
    ["damage_dealt", "Deal damage", "", "Required amount is cumulative damage dealt in the active counter window."],
    ["hits_taken", "Be hit", "", "Required amount is the number of damaging hits received."],
    ["hits_dealt", "Hit an entity", "", "Required amount is the number of damaging hits dealt."],
    ["health_gained", "Recover health", "", "Required amount is the health recovered."],
    ["health_lost", "Lose health", "", "Required amount is the health lost."],
    ["no_damage_for", "Avoid damage for time", "", "Required amount is the number of milliseconds without taking damage."],
    ["current_health", "Reach a health value", "", "Required amount is the minimum current health."],
    ["food", "Reach a food value", "", "Required amount is the minimum food level."],
    ["altitude", "Reach an altitude", "", "Required amount is the minimum Y coordinate."],
    ["stage_count", "Own a number of stages", "", "Required amount is the number of stages currently owned."],
    ["online_team_size", "Have team members online", "", "Required amount is the number of online team members."],
    ["stage_held_for", "Own a stage for time", "stages", "Required amount is the number of milliseconds the selected stage has been owned."],
    ["boss_session", "During a boss fight", "entities"],
    ["combat_session", "During combat", "entities"], ["region_session", "During a region session", ""],
    ["kubejs", "KubeJS event", "events"], ["script", "Script callback", "events"]
  ];

  const state = {
    boot: null,
    stageKey: "",
    path: "",
    view: "form",
    sourceDirty: false,
    activeInput: null,
    modalCleanup: null,
    dragRule: null,
    graphZoom: 1,
    graphConnectFrom: ""
  };

  const GRAPH_LANE_X = 84;
  const GRAPH_LAYER_Y = 54;
  const GRAPH_PREVIEW_X = 215 / GRAPH_LANE_X;
  const GRAPH_PREVIEW_Y = 135 / GRAPH_LAYER_Y;
  const GRAPH_PREVIEW_LEFT = 35;
  const GRAPH_PREVIEW_TOP = 45;

  const $ = id => document.getElementById(id);
  const q = (selector, root = document) => root.querySelector(selector);
  const qa = (selector, root = document) => [...root.querySelectorAll(selector)];

  async function api(payload) {
    const response = await fetch("/api/request", {
      method: "POST",
      headers: { "Authorization": "Bearer " + token, "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await response.json();
    if (data.error) throw Object.assign(new Error(data.explanation || data.error), { data });
    return data;
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
    })[character]);
  }

  function toast(message) {
    const element = $("toast");
    element.textContent = message;
    element.classList.add("show");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => element.classList.remove("show"), 2800);
  }

  async function boot() {
    try {
      state.boot = await api({ action: "bootstrap" });
      $("connection").textContent = "Connected";
      $("connection").classList.remove("pending");
      const first = stagePackages().find(stage => !stage.archived) || stagePackages()[0];
      renderAll();
      if (first) selectStage(first.key);
      else renderEmptyWorkspace();
    } catch (error) {
      $("connection").textContent = "Connection failed";
      toast(error.message);
    }
  }

  function stagePackages() {
    if (!state.boot) return [];
    const files = state.boot.draft.files;
    const output = [];
    const packageStagePaths = Object.keys(files).filter(path => path.endsWith("/stage.toml"));
    for (const stagePath of packageStagePaths) {
      const folder = stagePath.slice(0, -"stage.toml".length);
      const content = files[stagePath];
      output.push(stageSummary(folder, stagePath, folder + "rules.toml", folder + "progression.toml", false));
    }
    for (const path of Object.keys(files)) {
      if (!path.startsWith("stages/") || !path.endsWith(".toml") || path.includes("/.editor-")) continue;
      if (packageStagePaths.some(stagePath => path.startsWith(stagePath.slice(0, -"stage.toml".length)))) continue;
      output.push(stageSummary(path, path, path, path, true));
    }
    return output.sort((left, right) => left.name.localeCompare(right.name));
  }

  function stageSummary(key, stagePath, rulesPath, progressionPath, legacy) {
    const files = state.boot.draft.files;
    const stageText = files[stagePath] || "";
    const id = stringValue(readTomlValue(stageText, "stage.id")) || stagePath.split("/").pop().replace(/\.toml$/, "");
    const name = stringValue(readTomlValue(stageText, "stage.display_name")) || title(id.split(":").pop());
    const description = stringValue(readTomlValue(stageText, "stage.description"));
    const icon = stringValue(readTomlValue(stageText, "stage.icon")) || "minecraft:stone";
    const rulesText = files[rulesPath] || "";
    const count = ruleModels(rulesText).length;
    return { key, folder: legacy ? "" : key, stagePath, rulesPath, progressionPath, legacy, id, name, description, icon, count, archived: key.includes("/.editor-archive/") };
  }

  function selectedStage() {
    return stagePackages().find(stage => stage.key === state.stageKey) || null;
  }

  function renderAll() {
    if (!state.boot) return;
    const draft = state.boot.draft;
    $("revision").textContent = `Draft ${draft.revision} · Server ${state.boot.session.baseConfigurationRevision}`;
    $("undo").disabled = !draft.canUndo;
    $("redo").disabled = !draft.canRedo;
    renderStages();
    renderGraph();
    renderExtensions();
    if (state.stageKey === "__settings__") renderSettings();
    else if (selectedStage()) renderBuilder();
  }

  function renderStages() {
    const search = $("fileSearch").value.trim().toLowerCase();
    const groups = { Active: [], Archived: [] };
    for (const stage of stagePackages()) {
      if (search && !`${stage.name} ${stage.id}`.toLowerCase().includes(search)) continue;
      groups[stage.archived ? "Archived" : "Active"].push(stage);
    }
    $("files").innerHTML = Object.entries(groups).filter(([, stages]) => stages.length).map(([label, stages]) =>
      `<div class="stage-group"><strong class="stage-group-title">${label}</strong>${stages.map(stage =>
        `<button class="stage-card ${stage.key === state.stageKey ? "active" : ""}" data-stage-key="${escapeHtml(stage.key)}">
          <span class="stage-card-icon">◆</span><span class="stage-card-copy"><strong>${escapeHtml(stage.name)}</strong><small>${escapeHtml(stage.id)}</small></span><span class="stage-count" title="Rules">${stage.count}</span>
        </button>`).join("")}</div>`).join("") || `<div class="empty-state"><span><strong>No stages found.</strong><br>Create one with the gold plus button.</span></div>`;
    qa("[data-stage-key]", $("files")).forEach(button => button.onclick = () => selectStage(button.dataset.stageKey));
  }

  function selectStage(key) {
    if (!discardSourceChanges()) return;
    const stage = stagePackages().find(value => value.key === key);
    if (!stage) return;
    state.stageKey = key;
    state.path = stage.stagePath;
    state.sourceDirty = false;
    switchView("form");
    updateSource(stage.stagePath);
    renderAll();
    $("breadcrumb").innerHTML = `<strong>${escapeHtml(stage.name)}</strong><span> · ${escapeHtml(stage.id)} · ${stage.legacy ? "Classic stage" : "Three file stage package"}</span>`;
  }

  function renderEmptyWorkspace() {
    $("breadcrumb").textContent = "Create your first stage";
    $("formView").innerHTML = `<div class="empty-state"><span><strong>No stages yet.</strong><br>Click the gold plus button. You only need to choose a name.</span></div>`;
  }

  function renderBuilder() {
    const stage = selectedStage();
    if (!stage) return renderEmptyWorkspace();
    const stageText = state.boot.draft.files[stage.stagePath] || "";
    const dependencies = dependenciesFor(stageText, stage.legacy);
    const dependencyMode = stringValue(readTomlValue(stageText, "stage.dependency_mode")) || "all";
    const dependencyCount = Math.max(1, Number(readTomlValue(stageText, "stage.dependency_count") || 1));
    const rules = ruleModels(state.boot.draft.files[stage.rulesPath] || "");
    const progressionText = state.boot.draft.files[stage.progressionPath] || "";
    const progression = progressionModels(progressionText);
    const grants = progression.filter(entry => entry.kind === "grants");
    const revokes = progression.filter(entry => entry.kind === "revokes");
    const purchase = purchaseModel(progressionText);
    const dropModifierCount = extractArrayBlocks(state.boot.draft.files[stage.rulesPath] || "", "drop_modifiers").length;
    const hidden = booleanValue(readTomlValue(stageText, "stage.hidden"));
    const scope = stringValue(readTomlValue(stageText, "stage.scope")) || "team";
    const frame = stringValue(readTomlValue(stageText, "display.frame")) || "task";
    const reveal = stringValue(readTomlValue(stageText, "display.reveal")) || "dependencies";
    const background = stringValue(readTomlValue(stageText, "display.background")) || "minecraft:textures/gui/advancements/backgrounds/stone.png";
    const category = stringValue(readTomlValue(stageText, "stage.category"));
    const color = stringValue(readTomlValue(stageText, "stage.color")) || "#e3aa32";
    const slotGroup = stringValue(readTomlValue(stageText, "stage.slot_group"));
    const slotLimit = Math.max(0, Number(readTomlValue(stageText, "stage.slot_limit") || 0));
    const slotPolicy = stringValue(readTomlValue(stageText, "stage.slot_policy")) || "deny";
    const mapXText = readTomlValue(stageText, "display.x").trim();
    const mapYText = readTomlValue(stageText, "display.y").trim();
    const hasMapPosition = mapXText !== "" && mapYText !== ""
      && Number.isFinite(Number(mapXText)) && Number.isFinite(Number(mapYText))
      && Number(mapXText) >= 0 && Number(mapYText) >= 0;
    const mapPosition = hasMapPosition ? `X ${Number(mapXText)} and Y ${Number(mapYText)}` : "Automatic dependency layout";
    $("formView").innerHTML = `
      <section class="stage-hero"><span class="hero-icon">◆</span><div><h1>${escapeHtml(stage.name)}</h1><p>${escapeHtml(stage.description || "Add a short explanation for players.")}</p></div><span class="stage-id">${escapeHtml(stage.id)}</span></section>
      ${stage.legacy ? `<div class="validation-ok"><strong>Classic stage detected.</strong><p>You can edit its details and normal lock lists here. Create or duplicate it as a three file stage to use temporary rules and the full progression builder.</p></div>` : ""}
      <article class="builder-section"><div class="builder-section-head"><div><h2>Stage details</h2><p>Name, appearance, prerequisites, and ownership.</p></div></div><div class="builder-section-body"><div class="details-grid">
        <label>Player facing name<input data-stage-field="stage.display_name" value="${escapeHtml(stage.name)}"></label>
        <label>Icon item<div class="input-with-button"><input data-stage-field="stage.icon" value="${escapeHtml(stage.icon)}"><button type="button" data-browse-field="stage.icon" data-catalog="items">Browse</button></div></label>
        <label class="wide-field">Description<textarea rows="2" data-stage-field="stage.description">${escapeHtml(stage.description)}</textarea></label>
        <div class="dependency-field"><span>Required stages</span><div class="dependency-summary"><div class="dependency-copy"><strong>${escapeHtml(dependencySummary(dependencies, dependencyMode, dependencyCount))}</strong><small>${dependencies.length ? "These branches flow upward into this stage." : "This is a beginner or root stage."}</small><div class="dependency-chips">${dependencies.map(id => `<span class="dependency-chip">${escapeHtml(stageName(id))}</span>`).join("")}</div></div><button type="button" id="editDependencies">Choose stages and paths</button></div></div>
        <div class="dependency-field"><span>Stage slots and stacking</span><div class="dependency-summary"><div class="dependency-copy"><strong>${escapeHtml(slotGroup ? slotLimit > 0 ? `${slotLimit} active in ${title(slotGroup)}` : `All ${title(slotGroup)} stages stack` : "No slot group. Always stacks.")}</strong><small>${slotGroup && slotLimit > 0 ? `When full the policy is ${slotPolicy.replaceAll("_", " ")}.` : "Use groups to limit beginner classes modes or specialist buffs."}</small></div><button type="button" id="editSlots">Configure slots</button></div></div>
        <label>Ownership<select data-stage-field="stage.scope"><option value="team" ${scope === "team" ? "selected" : ""}>Team shares it</option><option value="server" ${scope === "server" ? "selected" : ""}>Whole server shares it</option></select></label>
        <label>Map category<input data-stage-field="stage.category" value="${escapeHtml(category)}" placeholder="Main progression"></label>
        <label>Map color<input data-stage-field="stage.color" value="${escapeHtml(color)}" placeholder="#e3aa32"></label>
        <label>Vanilla frame<select data-stage-field="display.frame"><option ${frame === "task" ? "selected" : ""}>task</option><option ${frame === "goal" ? "selected" : ""}>goal</option><option ${frame === "challenge" ? "selected" : ""}>challenge</option></select></label>
        <label>Reveal stage<select data-stage-field="display.reveal"><option value="always" ${reveal === "always" ? "selected" : ""}>Always</option><option value="dependencies" ${reveal === "dependencies" ? "selected" : ""}>After prerequisites</option><option value="unlocked" ${reveal === "unlocked" ? "selected" : ""}>Only when unlocked</option></select></label>
        <label class="wide-field">Advancement background<input data-stage-field="display.background" value="${escapeHtml(background)}"></label>
        <div class="dependency-field"><span>Player progression UI position</span><div class="dependency-summary"><div class="dependency-copy"><strong>${escapeHtml(mapPosition)}</strong><small>Drag this stage in Player UI layout or enter exact map coordinates here.</small></div><button type="button" id="editMapPosition">Edit player UI position</button></div></div>
        <label class="toggle-line"><input type="checkbox" data-stage-field="stage.hidden" data-boolean="true" ${hidden ? "checked" : ""}>Hide this stage from players until it is revealed</label>
      </div></div></article>
      <article class="builder-section"><div class="builder-section-head"><div><h2>Rules</h2><p>${rules.length ? `${rules.length} currently active` : "None currently active"}. Each rule combines a target, player action, result, priority, activation condition, lifetime, and optional exception.</p></div><button id="addRule" class="primary" ${stage.archived ? "disabled" : ""}>＋ Add rule</button></div><div class="builder-section-body"><div class="rule-explanation"><strong>How rules work together.</strong> A rule first checks its target and player action. Its activation condition decides when it participates. If several rules match, the highest priority wins. Give an exception a higher priority than the broader lock it should override. A permanent lock normally protects content until this stage is owned. A temporary rule follows its condition and lifetime.</div>${rulesHtml(rules)}</div></article>
      <article class="builder-section"><div class="builder-section-head"><div><h2>How players obtain this stage</h2><p>Use an item purchase, automatic gameplay trigger, quest command, KubeJS event, or API hook.</p></div><button id="addProgression" class="primary" ${stage.legacy || stage.archived ? "disabled" : ""}>＋ Add way to obtain</button></div><div class="builder-section-body"><div class="acquisition-grid">
        <article class="acquisition-card ${purchase.enabled ? "active" : ""}"><span class="acquisition-icon">◆</span><div><strong>Buy with items</strong><small>${escapeHtml(purchase.enabled ? purchase.summary : "Not configured. Players can pay items and XP from the in game stage tree.")}</small></div><button id="editPurchase">${purchase.enabled ? "Edit purchase" : "Set up purchase"}</button></article>
        <article class="acquisition-card ${grants.length ? "active" : ""}"><span class="acquisition-icon">↟</span><div><strong>Earn through gameplay</strong><small>${grants.length ? `${grants.length} automatic ways to obtain this stage.` : "Add kills, mining, crafting, exploration, player events, or KubeJS events."}</small></div></article>
        <article class="acquisition-card"><span class="acquisition-icon">⌘</span><div><strong>Quest command or API</strong><small>Always available through PSTages FTB Quests KubeJS and the Java API.</small></div></article>
      </div>${progressionHtml(grants, "No automatic way to obtain this stage yet.")}</div></article>
      <article class="builder-section"><div class="builder-section-head"><div><h2>How players lose this stage</h2><p>Revoke it when the owner dies, another online player dies, the owner defeats another player, a structure session changes, damage occurs, exploration completes, KubeJS fires, or any other supported trigger matches.</p></div><button id="addRevoke" class="primary" ${stage.legacy || stage.archived ? "disabled" : ""}>＋ Add way to lose</button></div><div class="builder-section-body"><div class="rule-explanation"><strong>Stage loss is a trigger too.</strong> The same event and live condition library used for grants is available here. Choose Revoke this stage, then select exactly who owns the progress, how many events are required, whether it repeats, and any cooldown. Player dies means the stage owner dies. Another player dies means any other online player dies. Defeat another player means the stage owner wins a player fight.</div>${progressionHtml(revokes, "No automatic way to lose this stage yet.")}</div></article>
      <article class="builder-section"><div class="builder-section-head"><div><h2>More stage features</h2><p>Guided shortcuts for rewards, costs, challenges, variables, and advanced systems.</p></div></div><div class="builder-section-body"><div class="feature-grid">
        ${featureButton("rewards", "Rewards", "Items, effects, XP, commands, and teleport")}
        ${featureButton("cost", "Unlock cost", "Items, XP, cooldown, and refunds")}
        ${featureButton("challenges", "Challenges", "Boss sessions, budgets, timers, and failure")}
        ${featureButton("variables", "Variables", "Player, team, or server values")}
        ${featureButton("modifiers", "Modifiers", "Buff, debuff, and transform held items")}
        ${featureButton("drop_modifier", "Targeted mining bonus", `${dropModifierCount} configured. Change drops for selected blocks tools and enchantments`)}
        ${featureButton("advanced", "All advanced fields", "Profiles, formulas, states, templates, and extensions")}
      </div></div></article>`;
    bindBuilder(stage, rules, progression);
  }

  function featureButton(kind, label, description) {
    return `<button class="feature-button" data-feature="${kind}"><strong>${label}</strong><small>${description}</small></button>`;
  }

  function rulesHtml(rules) {
    if (!rules.length) return `<div class="empty-state"><span><strong>No rules currently active.</strong><br>Add one, choose a category, then pick from the live server registry.</span></div>`;
    return `<div id="ruleList" class="rule-list">${rules.map((rule, index) => `<article class="rule-card" ${rule.generic ? "draggable=\"true\"" : ""} data-rule-index="${index}">
      <span class="drag-handle" title="Drag to reorder">${rule.generic ? "⠿" : "·"}</span>
      <span class="rule-title"><strong>${escapeHtml(CATEGORIES[rule.category]?.label || title(rule.category))}</strong><small><span class="effect-chip ${escapeHtml(rule.effect)}">${escapeHtml(effectLabel(rule.effect))}</span> · ${escapeHtml(ACTION_LABELS[rule.action] || title(rule.action || "access"))} · priority ${rule.priority}</small></span>
      <span class="rule-target"><strong>${escapeHtml(rule.selector)}</strong><small>${rule.temporary ? `${title(rule.lifetime)} while ${conditionLabel(rule.conditionType)}` : rule.classic ? "Classic selector" : viewerLabel(rule)}</small></span>
      <span class="rule-actions"><button data-edit-rule="${index}">Edit</button><button data-delete-rule="${index}" class="danger" aria-label="Delete rule">×</button></span>
    </article>`).join("")}</div>`;
  }

  function progressionHtml(entries, emptyMessage) {
    if (!entries.length) return `<div class="empty-state"><span><strong>${escapeHtml(emptyMessage)}</strong><br>Click the gold button and choose a plain language trigger.</span></div>`;
    return `<div class="progression-list">${entries.map(entry => `<article class="progression-card"><span class="drag-handle">◆</span><span class="rule-title"><strong>${entry.kind === "grants" ? "Grant stage" : "Revoke stage"}</strong><small>${conditionLabel(entry.conditionType)} · ${escapeHtml(entry.repeat || "once")}</small></span><span class="rule-target"><strong>${escapeHtml(entry.conditionTarget || "No target needed")}</strong><small>Required amount ${entry.count}</small></span><span class="rule-actions"><button data-edit-progression-kind="${entry.kind}" data-edit-progression-table="${entry.tableIndex}">Edit</button><button data-delete-progression-kind="${entry.kind}" data-delete-progression-table="${entry.tableIndex}" class="danger">×</button></span></article>`).join("")}</div>`;
  }

  function bindBuilder(stage, rules, progression) {
    qa("[data-stage-field]", $("formView")).forEach(input => {
      input.onfocus = () => showFieldHelp(input.dataset.stageField);
      input.onchange = () => saveStageField(input).catch(error => toast(error.message));
    });
    qa("[data-browse-field]", $("formView")).forEach(button => button.onclick = () => openCatalogPicker({
      title: "Choose an icon item", catalog: button.dataset.catalog, mode: "id", selected: q(`[data-stage-field="${button.dataset.browseField}"]`).value,
      onPick: value => { const input = q(`[data-stage-field="${button.dataset.browseField}"]`); input.value = stripSelectorPrefix(value); input.dispatchEvent(new Event("change")); }
    }));
    $("editDependencies").onclick = openDependencyEditor;
    $("editSlots").onclick = () => openSlotEditor(stage);
    $("editMapPosition").onclick = () => openMapPositionEditor(stage);
    $("addRule").onclick = () => openRuleEditor(stage, null);
    qa("[data-edit-rule]", $("formView")).forEach(button => button.onclick = () => openRuleEditor(stage, rules[Number(button.dataset.editRule)]));
    qa("[data-delete-rule]", $("formView")).forEach(button => button.onclick = () => deleteRule(stage, rules[Number(button.dataset.deleteRule)]).catch(error => toast(error.message)));
    bindRuleDragging(stage, rules);
    $("addProgression").onclick = () => openProgressionEditor(stage, null, "grants");
    $("addRevoke").onclick = () => openProgressionEditor(stage, null, "revokes");
    $("editPurchase").onclick = () => openPurchaseEditor(stage);
    qa("[data-edit-progression-kind]", $("formView")).forEach(button => button.onclick = () => openProgressionEditor(stage,
      progression.find(entry => entry.kind === button.dataset.editProgressionKind && entry.tableIndex === Number(button.dataset.editProgressionTable))));
    qa("[data-delete-progression-kind]", $("formView")).forEach(button => button.onclick = () => deleteProgression(stage,
      progression.find(entry => entry.kind === button.dataset.deleteProgressionKind && entry.tableIndex === Number(button.dataset.deleteProgressionTable))).catch(error => toast(error.message)));
    qa("[data-feature]", $("formView")).forEach(button => button.onclick = () => openFeatureEditor(stage, button.dataset.feature));
  }

  async function saveStageField(input) {
    const stage = selectedStage();
    let value;
    if (input.dataset.boolean) value = input.checked;
    else if (input.dataset.list) value = input.value.split(",").map(item => item.trim()).filter(Boolean);
    else value = input.value;
    const content = upsertToml(state.boot.draft.files[stage.stagePath] || "", input.dataset.stageField, value);
    await mutate(stage.stagePath, content);
    toast("Stage details saved to the draft");
  }

  function openDependencyEditor() {
    const stage = selectedStage();
    if (!stage) return;
    const content = state.boot.draft.files[stage.stagePath] || "";
    const selected = new Set(dependenciesFor(content, stage.legacy));
    const policy = {
      mode: stringValue(readTomlValue(content, "stage.dependency_mode")) || "all",
      count: Math.max(1, Number(readTomlValue(content, "stage.dependency_count") || 1))
    };
    const available = stagePackages().filter(candidate => !candidate.archived && candidate.id !== stage.id);
    openModal(`<h2 id="modalTitle">Build the path into ${escapeHtml(stage.name)}</h2><p>Select earlier stages and choose how their paths join. Beginner stages should have nothing selected.</p><form id="dependencyForm"><div class="modal-grid">
      <label>How paths join<select id="dependencyMode"><option value="all">Require every selected path</option><option value="any">Require any one selected path</option><option value="at_least">Require a minimum number</option></select></label>
      <label id="dependencyCountLabel">Minimum paths<input id="dependencyCount" type="number" min="1" value="${policy.count}"></label>
      <div id="dependencyModeHelp" class="dependency-mode-help"></div>
      <label class="wide-field">Find a stage<input id="dependencySearch" placeholder="Mage, Wizard, Knight"></label>
      <div id="dependencyOptions" class="dependency-options wide-field"></div>
      <section class="modal-section"><h3>Evolution preview</h3><div id="dependencyPreview" class="dependency-visualizer"></div></section>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save required stages</button></div></form>`, () => {
      $("dependencyMode").value = policy.mode;
      const renderOptions = () => {
        const search = $("dependencySearch").value.trim().toLowerCase();
        const shown = available.filter(candidate => !search || `${candidate.name} ${candidate.id}`.toLowerCase().includes(search));
        $("dependencyOptions").innerHTML = shown.map(candidate => {
          const cycle = stageDependsOn(candidate.id, stage.id);
          const blocked = cycle && !selected.has(candidate.id);
          const parents = stageParents(candidate);
          const explanation = blocked ? "Cannot select because it creates a loop" : cycle ? "Remove this selection because it creates a loop" : parents.length ? `Evolves from ${escapeHtml(parents.map(stageName).join(" and "))}` : "Beginner path";
          return `<label class="dependency-option"><input type="checkbox" data-dependency-id="${escapeHtml(candidate.id)}" ${selected.has(candidate.id) ? "checked" : ""} ${blocked ? "disabled" : ""}><span><strong>${escapeHtml(candidate.name)}</strong><small>${explanation}</small></span></label>`;
        }).join("") || `<div class="empty-state"><span>No matching stages.</span></div>`;
        qa("[data-dependency-id]", $("dependencyOptions")).forEach(input => input.onchange = () => {
          if (input.checked) selected.add(input.dataset.dependencyId);
          else selected.delete(input.dataset.dependencyId);
          normalizeDependencyCount(selected, policy);
          renderPolicy();
        });
      };
      const renderPolicy = () => {
        policy.mode = $("dependencyMode").value;
        policy.count = Math.max(1, Number($("dependencyCount").value || 1));
        normalizeDependencyCount(selected, policy);
        $("dependencyCount").value = policy.count;
        $("dependencyCountLabel").classList.toggle("hidden", policy.mode !== "at_least");
        $("dependencyModeHelp").textContent = dependencyPolicyHelp(selected.size, policy.mode, policy.count);
        $("dependencyPreview").innerHTML = dependencyPreviewHtml(stage, [...selected], policy.mode, policy.count);
      };
      $("dependencySearch").oninput = renderOptions;
      $("dependencyMode").onchange = renderPolicy;
      $("dependencyCount").oninput = renderPolicy;
      $("dependencyForm").onsubmit = async event => {
        event.preventDefault();
        const dependencies = [...selected];
        normalizeDependencyCount(selected, policy);
        let updated = state.boot.draft.files[stage.stagePath] || "";
        updated = upsertToml(updated, stage.legacy ? "stage.dependency" : "stage.dependencies", dependencies);
        updated = upsertToml(updated, "stage.dependency_mode", dependencies.length ? policy.mode : "all");
        updated = upsertToml(updated, "stage.dependency_count", dependencies.length ? policy.count : 1);
        try { await mutate(stage.stagePath, updated); closeModal(); toast("Required stage paths saved to the draft"); }
        catch (error) { toast(error.message); }
      };
      renderOptions();
      renderPolicy();
    });
  }

  function openSlotEditor(stage) {
    const content = state.boot.draft.files[stage.stagePath] || "";
    const currentGroup = stringValue(readTomlValue(content, "stage.slot_group"));
    const currentLimit = Math.max(0, Number(readTomlValue(content, "stage.slot_limit") || 0));
    const currentPolicy = stringValue(readTomlValue(content, "stage.slot_policy")) || "deny";
    const knownGroups = [...new Set(stagePackages().map(candidate => stringValue(readTomlValue(
      state.boot.draft.files[candidate.stagePath] || "", "stage.slot_group"))).filter(Boolean))].sort();
    openModal(`<h2 id="modalTitle">Configure stage slots and stacking</h2><p>Put related stages in one group. Zero means every stage in the group stays active and all of their buffs stack.</p><form id="slotForm"><div class="modal-grid">
      <label class="wide-field">Group name<input id="slotGroup" list="slotGroups" value="${escapeHtml(currentGroup)}" placeholder="beginner_classes"><datalist id="slotGroups">${knownGroups.map(group => `<option value="${escapeHtml(group)}">${escapeHtml(title(group))}</option>`).join("")}</datalist><small>Leave blank when this stage should never consume a slot.</small></label>
      <label>Maximum active stages<input id="slotLimit" type="number" min="0" max="1024" value="${currentLimit}"><small>Zero allows every stage in this group to stack.</small></label>
      <label>When the group is full<select id="slotPolicy"><option value="deny">Block the new stage</option><option value="replace_oldest">Replace the oldest stage</option><option value="replace_lowest_priority">Replace the lowest priority stage</option><option value="replace_all">Replace every active stage</option></select></label>
      <div id="slotExplanation" class="dependency-mode-help"></div>
      <label class="toggle-line"><input id="slotApplyGroup" type="checkbox" ${currentGroup ? "checked" : ""}>Apply these settings to every stage already using this group</label>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save slot behavior</button></div></form>`, () => {
      $("slotPolicy").value = currentPolicy;
      const explain = () => {
        const group = slug($("slotGroup").value);
        const limit = Math.max(0, Number($("slotLimit").value || 0));
        $("slotPolicy").disabled = !group || limit === 0;
        $("slotExplanation").textContent = !group ? `${stage.name} has no slot group and always stacks.`
          : limit === 0 ? `Every ${title(group)} stage can remain active. Their compatible buffs stack.`
          : `A player can own ${limit} ${title(group)} stage${limit === 1 ? "" : "s"} at once.`;
      };
      $("slotGroup").oninput = explain;
      $("slotLimit").oninput = explain;
      $("slotPolicy").onchange = explain;
      $("slotForm").onsubmit = async event => {
        event.preventDefault();
        const group = slug($("slotGroup").value);
        const limit = group ? Math.max(0, Math.min(1024, Number($("slotLimit").value || 0))) : 0;
        const policy = limit > 0 ? $("slotPolicy").value : "deny";
        const applyGroup = $("slotApplyGroup").checked;
        const targets = stagePackages().filter(candidate => {
          if (candidate.key === stage.key) return true;
          if (!applyGroup) return false;
          const candidateGroup = stringValue(readTomlValue(
            state.boot.draft.files[candidate.stagePath] || "", "stage.slot_group"));
          return candidateGroup && (candidateGroup === currentGroup || candidateGroup === group);
        });
        try {
          for (const target of targets) {
            let updated = state.boot.draft.files[target.stagePath] || "";
            updated = upsertToml(updated, "stage.slot_group", group);
            updated = upsertToml(updated, "stage.slot_limit", limit);
            updated = upsertToml(updated, "stage.slot_policy", policy);
            await mutate(target.stagePath, updated);
          }
          closeModal();
          toast(`Slot behavior saved for ${targets.length} stage${targets.length === 1 ? "" : "s"}`);
        } catch (error) { toast(error.message); }
      };
      explain();
    });
  }

  function openMapPositionEditor(stage) {
    const content = state.boot.draft.files[stage.stagePath] || "";
    const rawX = readTomlValue(content, "display.x").trim();
    const rawY = readTomlValue(content, "display.y").trim();
    const hasPosition = rawX !== "" && rawY !== "" && Number.isFinite(Number(rawX))
      && Number.isFinite(Number(rawY)) && Number(rawX) >= 0 && Number(rawY) >= 0;
    openModal(`<h2 id="modalTitle">Place ${escapeHtml(stage.name)} in the player UI</h2><p>These coordinates control the icon in Minecraft. X moves left and right. Y moves up and down. You can also drag the stage in Player UI layout.</p><form id="mapPositionForm"><div class="modal-grid">
      <label>Map X<input id="mapPositionX" type="number" min="0" step="1" value="${hasPosition ? escapeHtml(Number(rawX)) : 0}"></label>
      <label>Map Y<input id="mapPositionY" type="number" min="0" step="1" value="${hasPosition ? escapeHtml(Number(rawY)) : 0}"></label>
      <div class="dependency-mode-help">${hasPosition ? `This stage has a custom position at X ${escapeHtml(Number(rawX))} and Y ${escapeHtml(Number(rawY))}.` : "This stage currently follows the automatic dependency layout."}</div>
    </div><div class="modal-actions"><button type="button" id="automaticMapPosition" class="ghost">Use automatic position</button><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save position</button></div></form>`, () => {
      $("automaticMapPosition").onclick = async () => {
        try {
          let updated = removeTomlValue(content, "display.x");
          updated = removeTomlValue(updated, "display.y");
          await mutate(stage.stagePath, updated);
          closeModal();
          toast("Automatic player UI position restored");
        } catch (error) { toast(error.message); }
      };
      $("mapPositionForm").onsubmit = async event => {
        event.preventDefault();
        const x = Math.round(Number($("mapPositionX").value));
        const y = Math.round(Number($("mapPositionY").value));
        if (!Number.isFinite(x) || !Number.isFinite(y) || x < 0 || y < 0) return toast("Enter two nonnegative whole map coordinates");
        try {
          let updated = upsertToml(content, "display.x", x);
          updated = upsertToml(updated, "display.y", y);
          await mutate(stage.stagePath, updated);
          closeModal();
          toast("Player UI position saved to the draft");
        } catch (error) { toast(error.message); }
      };
    });
  }

  function dependencyPreviewHtml(stage, dependencyIds, mode, count) {
    if (!dependencyIds.length) return `<div class="empty-state"><span><strong>${escapeHtml(stage.name)} is a beginner stage.</strong><br>It starts its own independent path.</span></div>`;
    const dependencies = dependencyIds.map(id => stagePackages().find(value => value.id === id)).filter(Boolean);
    const width = Math.max(520, dependencies.length * 190);
    const targetX = width / 2;
    const positions = dependencies.map((dependency, index) => ({ dependency, x: width * (index + 1) / (dependencies.length + 1) }));
    const paths = positions.map(position => `<path d="M ${position.x} 205 C ${position.x} 145, ${targetX} 130, ${targetX} 68"/>`).join("");
    return `<svg class="lineage-canvas" width="${width}" height="250" viewBox="0 0 ${width} 250" aria-label="Required stage path preview"><g class="lineage-lines">${paths}</g><foreignObject x="${targetX - 90}" y="10" width="180" height="58"><div xmlns="http://www.w3.org/1999/xhtml" class="lineage-target"><strong>${escapeHtml(stage.name)}</strong><small>${escapeHtml(dependencySummary(dependencyIds, mode, count))}</small></div></foreignObject>${positions.map(position => `<foreignObject x="${position.x - 85}" y="185" width="170" height="58"><div xmlns="http://www.w3.org/1999/xhtml" class="lineage-source"><strong>${escapeHtml(position.dependency.name)}</strong><small>${escapeHtml(stageParents(position.dependency).length ? `from ${stageParents(position.dependency).map(stageName).join(" and ")}` : "beginner path")}</small></div></foreignObject>`).join("")}</svg>`;
  }

  function normalizeDependencyCount(selected, policy) {
    const maximum = Math.max(1, selected.size);
    policy.count = Math.min(maximum, Math.max(1, Number(policy.count || 1)));
  }

  function dependencyPolicyHelp(total, mode, count) {
    if (!total) return "No prerequisite is selected. This stage begins an independent play style.";
    if (mode === "any") return `Players need any one of the ${total} selected paths.`;
    if (mode === "at_least") return `Players need at least ${Math.min(total, count)} of the ${total} selected paths.`;
    return `Players need all ${total} selected paths. Use this for combinations such as Wizard Knight.`;
  }

  function dependencySummary(dependencies, mode, count) {
    if (!dependencies.length) return "No required stages";
    const names = dependencies.map(stageName);
    if (mode === "any") return `Any one of ${names.join(", ")}`;
    if (mode === "at_least") return `${Math.min(names.length, Math.max(1, count))} of ${names.join(", ")}`;
    return names.length === 1 ? `Requires ${names[0]}` : `Requires ${names.join(" and ")}`;
  }

  function stageName(id) {
    return stagePackages().find(stage => stage.id === id)?.name || title(String(id).split(":").pop());
  }

  function stageParents(stage) {
    return dependenciesFor(state.boot.draft.files[stage.stagePath] || "", stage.legacy);
  }

  function stageDependsOn(startId, targetId, visited = new Set()) {
    if (startId === targetId) return true;
    if (visited.has(startId)) return false;
    visited.add(startId);
    const stage = stagePackages().find(value => value.id === startId);
    if (!stage) return false;
    return stageParents(stage).some(parent => stageDependsOn(parent, targetId, visited));
  }

  function showFieldHelp(path) {
    const schema = state.boot.schemas.find(field => field.path === path && field.file === "stage.toml");
    $("helpPanel").innerHTML = schema
      ? `<h3>${escapeHtml(schema.label)}</h3><p>${escapeHtml(schema.help)}</p><dl><dt>Saved in</dt><dd>${escapeHtml(schema.file)}</dd><dt>Apply</dt><dd>${escapeHtml(schema.restartRequirement)}</dd></dl>`
      : `<h3>${escapeHtml(title(path.split(".").pop()))}</h3><p>This change stays in the server draft until you review and apply it.</p>`;
    showInspector("help");
  }

  function ruleModels(text) {
    const models = [];
    for (const table of ["rules", "temporary_rules"]) {
      extractArrayBlocks(text, table).forEach((block, tableIndex) => models.push(parseRuleBlock(block, table, tableIndex)));
    }
    for (const [category] of Object.entries(CATEGORIES)) {
      for (const [field, effect] of [["locked", "lock"], ["allowed", "allow"], ["always_unlocked", "allow"]]) {
        const raw = readTomlValue(text, `${category}.${field}`);
        const selectors = parseSimpleArray(raw) || [];
        selectors.forEach((selector, selectorIndex) => {
          const priorityMatch = String(selector).match(/\|priority=(-?\d+)$/);
          const categoryPriority = Number(readTomlValue(text, `${category}.priority`) || 0);
          models.push({
            generic: false, classic: true, category, action: CATEGORIES[category].actions[0], effect,
            selector: String(selector).replace(/\|priority=-?\d+$/, ""), priority: priorityMatch ? Number(priorityMatch[1]) : categoryPriority,
            field, selectorIndex, rawSelector: String(selector), temporary: false, lifetime: "permanent", conditionType: "none"
          });
        });
      }
    }
    return models;
  }

  function parseRuleBlock(block, table, tableIndex) {
    const categoryMatch = block.text.match(/^\s*targets\.([A-Za-z0-9_.-]+)\s*=\s*([^\n]+)/m);
    const category = categoryMatch ? categoryMatch[1] : stringValue(readBlockValue(block.text, "category")) || "items";
    const selectorRaw = categoryMatch ? categoryMatch[2] : readBlockValue(block.text, "selector");
    const selector = (parseSimpleArray(selectorRaw) || [stringValue(selectorRaw)]).filter(Boolean)[0] || "id:minecraft:stone";
    const effect = stringValue(readBlockValue(block.text, "effect")) || "lock";
    const lifetime = stringValue(readBlockValue(block.text, "lifetime")) || (table === "temporary_rules" ? "live" : "permanent");
    const condition = readBlockValue(block.text, "while") || readBlockValue(block.text, "when") || readBlockValue(block.text, "condition");
    const conditionType = inlineObjectValue(condition, "type") || "none";
    const conditionTarget = inlineObjectValue(condition, "id") || inlineObjectValue(condition, "value") || inlineObjectValue(condition, "callback") || "";
    const count = Number(inlineObjectValue(condition, "count") || inlineObjectValue(condition, "amount") || inlineObjectValue(condition, "minimum") || 1);
    const exceptionMatch = block.text.match(/^\s*\[\[(?:rules|temporary_rules)\.exceptions\]\][\s\S]*?^\s*targets\.[A-Za-z0-9_.-]+\s*=\s*([^\n]+)/m);
    const exceptionSelector = exceptionMatch ? (parseSimpleArray(exceptionMatch[1]) || [""])[0] : "";
    return {
      generic: true, classic: false, table, tableIndex, raw: block.text, start: block.start, end: block.end,
      category, action: stringValue(readBlockValue(block.text, "action")) || CATEGORIES[category]?.actions[0] || "access",
      effect, selector, priority: Number(readBlockValue(block.text, "priority") || 0), temporary: table === "temporary_rules" || lifetime !== "permanent",
      lifetime, duration: stringValue(readBlockValue(block.text, "duration")), conditionType, conditionTarget, count,
      jei: stringValue(readBlockValue(block.text, "presentation.jei")) || "inherit", emi: stringValue(readBlockValue(block.text, "presentation.emi")) || "inherit",
      exceptionSelector, exceptionPriority: Number(readNestedBlockValue(block.text, `${table}.exceptions`, "priority") || 0)
    };
  }

  function openRuleEditor(stage, rule) {
    const current = rule || {
      category: "items", action: "use", effect: "lock", selector: "", priority: 100,
      temporary: false, lifetime: "live", duration: "", conditionType: "none", conditionTarget: "", count: 1,
      jei: "inherit", emi: "inherit", exceptionSelector: "", exceptionPriority: 101
    };
    openModal(`
      <h2 id="modalTitle">${rule ? "Edit rule" : "Add a rule"}</h2><p>Build one decision in plain language. The editor writes the target selector, exact action, ownership behavior, priority, activation condition, lifetime, viewer policy, and exception.</p>
      <form id="ruleForm"><div class="modal-grid">
        <label>What kind of thing?<select id="ruleCategory">${categoryOptions(current.category)}</select></label>
        <label>What player action?<select id="ruleAction"></select></label>
        <label>What should happen?<select id="ruleEffect"></select></label>
        <label>Priority<input id="rulePriority" type="number" value="${current.priority}"><small>Higher numbers win.</small></label>
        <div id="ruleExplanation" class="rule-explanation"></div>
        <section class="modal-section"><h3>Choose the target</h3><div class="modal-grid">
          <label>Selection method<select id="ruleMode"><option value="id">One exact ID</option><option value="mod">Everything from a mod</option><option value="tag">Everything in a tag</option><option value="name">Anything with a name</option></select></label>
          <label>Only show this mod<input id="ruleMod" list="ruleMods" placeholder="All mods"><datalist id="ruleMods"></datalist></label>
          <label class="wide-field">Search<input id="ruleSearch" autocomplete="off" placeholder="diamond, zombie, create"></label>
          <div id="ruleResults" class="picker-results"></div>
          <div id="ruleSelected" class="selected-target">${escapeHtml(current.selector || "Nothing selected yet")}</div>
        </div></section>
        <section class="modal-section"><h3>When is this rule active?</h3><div class="modal-grid">
          <label id="ruleConditionTargetLabel">Condition target<input id="ruleConditionTarget" list="ruleConditionTargets" value="${escapeHtml(current.conditionTarget)}" placeholder="Chosen ID or KubeJS event"><datalist id="ruleConditionTargets"></datalist></label>
          <label id="ruleCountLabel">Required amount<input id="ruleCount" type="number" min="1" value="${current.count || 1}"></label>
          <label>Activation<select id="ruleCondition">${conditionOptions(current.conditionType)}</select></label>
          <label>Lifetime<select id="ruleLifetime"><option value="permanent">Permanent stage rule</option><option value="live">Only while condition is true</option><option value="duration">Timed after trigger</option><option value="session">For the current session</option><option value="latched">Stay active until reset</option><option value="schedule">Scheduled lifetime</option></select></label>
          <label class="wide-field">Duration or schedule<input id="ruleDuration" value="${escapeHtml(current.duration)}" placeholder="30s, 5m, or a schedule value"></label>
          <div id="ruleConditionHelp" class="condition-help"></div>
        </div></section>
        <section class="modal-section"><h3>Recipe viewer and exception</h3><div class="modal-grid">
          <label>JEI<select id="ruleJei">${viewerOptions(current.jei)}</select></label><label>EMI<select id="ruleEmi">${viewerOptions(current.emi)}</select></label>
          <label class="wide-field">Optional exception selector<input id="ruleException" value="${escapeHtml(current.exceptionSelector)}" placeholder="Example. tag:c:swords"></label>
          <label>Exception priority<input id="ruleExceptionPriority" type="number" value="${current.exceptionPriority || Number(current.priority) + 1}"></label>
        </div></section>
      </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save rule</button></div></form>`, modal => {
      const selected = { value: current.selector || "" };
      const category = $("ruleCategory");
      const action = $("ruleAction");
      const effect = $("ruleEffect");
      const mode = $("ruleMode");
      mode.value = selectorMode(current.selector);
      $("ruleLifetime").value = current.temporary ? current.lifetime : "permanent";
      updateActionOptions(action, category.value, current.action);
      updateEffectOptions(effect, category.value, current.effect);
      const explain = () => updateRuleExplanation(category.value, action.value, effect.value,
        Number($("rulePriority").value || 0), $("ruleCondition").value, $("ruleLifetime").value,
        Number($("ruleExceptionPriority").value || 0));
      const refresh = debounce(async () => {
        try {
          const definition = CATEGORIES[category.value];
          const filters = $("ruleMod").value.trim() ? { mod: $("ruleMod").value.trim() } : {};
          const result = await api({ action: "catalog", catalog: `progressivestages:${definition.catalog}`, field: "rule.target", mode: mode.value, text: $("ruleSearch").value, filters, pageSize: 40, catalogRevision: state.boot.catalog.revision });
          $("ruleResults").innerHTML = result.entries.map(entry => `<button type="button" data-pick="${escapeHtml(entry.key)}"><strong>${escapeHtml(entry.label)}</strong><small>${escapeHtml(entry.key)} · ${escapeHtml(entry.modName || entry.sourceType)}</small></button>`).join("") || `<span class="muted">No matching ${escapeHtml(definition.label.toLowerCase())}.</span>`;
          qa("[data-pick]", $("ruleResults")).forEach(button => button.onclick = () => {
            selected.value = normalizeSelector(mode.value, button.dataset.pick, $("ruleSearch").value);
            $("ruleSelected").textContent = selected.value;
          });
        } catch (error) { $("ruleResults").innerHTML = `<span class="danger">${escapeHtml(error.message)}</span>`; }
      }, 180);
      loadModNames("ruleMods");
      bindConditionAutocomplete("ruleCondition", "ruleConditionTarget", "ruleConditionTargets");
      updateConditionHelp("ruleCondition", "ruleConditionTargetLabel", "ruleCountLabel", "ruleConditionHelp");
      category.onchange = () => { updateActionOptions(action, category.value); updateEffectOptions(effect, category.value); selected.value = ""; $("ruleSelected").textContent = "Choose a new target"; explain(); refresh(); };
      action.onchange = explain;
      effect.onchange = explain;
      $("rulePriority").oninput = explain;
      $("ruleExceptionPriority").oninput = explain;
      $("ruleLifetime").onchange = explain;
      $("ruleCondition").addEventListener("change", () => {
        updateConditionHelp("ruleCondition", "ruleConditionTargetLabel", "ruleCountLabel", "ruleConditionHelp");
        explain();
      });
      mode.onchange = refresh;
      $("ruleSearch").oninput = refresh;
      $("ruleMod").oninput = refresh;
      explain();
      refresh();
      $("ruleForm").onsubmit = async event => {
        event.preventDefault();
        if (!selected.value && mode.value === "name" && $("ruleSearch").value.trim()) selected.value = `name:${$("ruleSearch").value.trim()}`;
        if (!selected.value) return toast("Choose a target before saving the rule");
        const model = {
          ...current, category: category.value, action: action.value, effect: effect.value,
          selector: selected.value, priority: Number($("rulePriority").value || 0),
          lifetime: $("ruleLifetime").value, temporary: $("ruleLifetime").value !== "permanent",
          duration: $("ruleDuration").value.trim(), conditionType: $("ruleCondition").value,
          conditionTarget: $("ruleConditionTarget").value.trim(), count: Number($("ruleCount").value || 1),
          jei: $("ruleJei").value, emi: $("ruleEmi").value,
          exceptionSelector: $("ruleException").value.trim(), exceptionPriority: Number($("ruleExceptionPriority").value || 0)
        };
        try { await saveRule(stage, rule, model); closeModal(); toast("Rule saved to the server draft"); }
        catch (error) { toast(error.message); }
      };
    });
  }

  async function saveRule(stage, previous, model) {
    let content = state.boot.draft.files[stage.rulesPath] || "";
    if (stage.legacy) {
      if (model.temporary || model.conditionType !== "none") throw new Error("Temporary rules need a three file stage. Duplicate this stage first.");
      if (previous) content = removeClassicRule(content, previous);
      const field = ["allow", "unlock"].includes(model.effect) ? "always_unlocked" : "locked";
      const current = parseSimpleArray(readTomlValue(content, `${model.category}.${field}`)) || [];
      current.push(model.selector + (model.priority ? `|priority=${model.priority}` : ""));
      content = upsertToml(content, `${model.category}.${field}`, current);
      await mutate(stage.rulesPath, content);
      return;
    }
    if (previous?.classic) content = removeClassicRule(content, previous);
    const table = model.temporary ? "temporary_rules" : "rules";
    const block = serializeRule(stage, model, table, previous);
    if (previous?.generic) {
      const currentBlocks = extractArrayBlocks(content, previous.table);
      const found = currentBlocks[previous.tableIndex];
      if (!found) throw new Error("The rule changed in another edit. Reopen it and try again.");
      content = content.slice(0, found.start) + block + content.slice(found.end);
    } else {
      content = appendTomlBlock(content, block);
    }
    await mutate(stage.rulesPath, content);
  }

  function serializeRule(stage, model, table, previous) {
    const suffix = `${table.replace("temporary_", "")}_${Date.now().toString(36)}`;
    const id = previous?.generic ? stringValue(readBlockValue(previous.raw, "id")) || childId(stage.id, suffix) : childId(stage.id, suffix);
    const lines = [`[[${table}]]`, `id = ${encodeToml(id)}`, `effect = ${encodeToml(model.effect)}`, `priority = ${Number(model.priority || 0)}`, `action = ${encodeToml(model.action)}`, `targets.${model.category} = ${encodeToml([model.selector])}`];
    if (model.temporary) lines.push(`lifetime = ${encodeToml(model.lifetime)}`);
    if (model.duration) lines.push(`duration = ${encodeToml(model.duration)}`);
    if (model.conditionType && model.conditionType !== "none") lines.push(`while = ${conditionToml(model.conditionType, model.conditionTarget, model.count)}`);
    if (model.jei !== "inherit") lines.push(`presentation.jei = ${encodeToml(model.jei)}`);
    if (model.emi !== "inherit") lines.push(`presentation.emi = ${encodeToml(model.emi)}`);
    if (model.exceptionSelector) {
      lines.push("", `[[${table}.exceptions]]`, `effect = "exclude"`, `priority = ${Number(model.exceptionPriority || model.priority || 0)}`, `targets.${model.category} = ${encodeToml([model.exceptionSelector])}`);
    }
    return lines.join("\n") + "\n";
  }

  async function deleteRule(stage, rule) {
    if (!confirm("Delete this rule from the draft")) return;
    let content = state.boot.draft.files[stage.rulesPath] || "";
    if (rule.classic) content = removeClassicRule(content, rule);
    else {
      const found = extractArrayBlocks(content, rule.table)[rule.tableIndex];
      if (!found) throw new Error("The rule no longer exists");
      content = content.slice(0, found.start) + content.slice(found.end);
    }
    await mutate(stage.rulesPath, content);
    toast("Rule deleted from the draft");
  }

  function removeClassicRule(content, rule) {
    const values = parseSimpleArray(readTomlValue(content, `${rule.category}.${rule.field}`)) || [];
    values.splice(rule.selectorIndex, 1);
    return upsertToml(content, `${rule.category}.${rule.field}`, values);
  }

  function bindRuleDragging(stage, rules) {
    qa(".rule-card[draggable=true]", $("formView")).forEach(card => {
      card.ondragstart = () => { state.dragRule = Number(card.dataset.ruleIndex); card.classList.add("dragging"); };
      card.ondragend = () => { state.dragRule = null; card.classList.remove("dragging"); };
      card.ondragover = event => event.preventDefault();
      card.ondrop = event => {
        event.preventDefault();
        const from = rules[state.dragRule];
        const to = rules[Number(card.dataset.ruleIndex)];
        if (!from?.generic || !to?.generic || from.table !== to.table || from.tableIndex === to.tableIndex) return;
        reorderRules(stage, from.table, from.tableIndex, to.tableIndex).catch(error => toast(error.message));
      };
    });
  }

  async function reorderRules(stage, table, from, to) {
    let content = state.boot.draft.files[stage.rulesPath] || "";
    const blocks = extractArrayBlocks(content, table);
    const moved = blocks.splice(from, 1)[0];
    blocks.splice(to, 0, moved);
    content = replaceArrayBlocks(content, table, blocks.map(block => block.text));
    await mutate(stage.rulesPath, content);
    toast("Rule order saved to the draft");
  }

  function progressionModels(text) {
    const output = [];
    for (const kind of ["grants", "revokes"]) extractArrayBlocks(text, kind).forEach((block, tableIndex) => {
      const condition = readBlockValue(block.text, "condition") || readBlockValue(block.text, "when") || readBlockValue(block.text, "while");
      output.push({
        kind, tableIndex, raw: block.text, conditionType: inlineObjectValue(condition, "type") || "none",
        conditionTarget: inlineObjectValue(condition, "id") || inlineObjectValue(condition, "value") || "",
        count: Number(inlineObjectValue(condition, "count") || inlineObjectValue(condition, "amount") || 1),
        repeat: stringValue(readBlockValue(block.text, "repeat")) || "once",
        scope: stringValue(readBlockValue(block.text, "scope")) || "player",
        priority: Number(readBlockValue(block.text, "priority") || 0), cooldown: stringValue(readBlockValue(block.text, "cooldown"))
      });
    });
    return output;
  }

  function purchaseModel(text) {
    const enabled = /^\s*\[cost\]\s*(?:#.*)?$/m.test(text);
    const items = (parseSimpleArray(readTomlValue(text, "cost.items")) || []).map(parseCostItem).filter(Boolean);
    const xp = Math.max(0, Number(readTomlValue(text, "cost.xp_levels") || 0));
    const parts = items.map(item => `${item.count} ${title(item.id.split(":").pop())}`);
    if (xp) parts.push(`${xp} XP levels`);
    return { enabled, items, xp, summary: parts.length ? parts.join(" and ") : "Free purchase" };
  }

  function parseCostItem(value) {
    const match = String(value || "").trim().match(/^(.*):(\d+)$/);
    const id = match ? match[1] : String(value || "").trim();
    if (!id.includes(":")) return null;
    return { id, count: Math.max(1, Number(match?.[2] || 1)) };
  }

  function openProgressionEditor(stage, entry, defaultKind = "grants") {
    const current = entry || { kind: defaultKind, conditionType: defaultKind === "revokes" ? "death" : "kill", conditionTarget: "", count: 1, repeat: "once", scope: "player", priority: 0, cooldown: "" };
    openModal(`<h2 id="modalTitle">${entry ? "Edit progression" : current.kind === "revokes" ? "Add a way to lose this stage" : "Add a way to obtain this stage"}</h2><p>Every grant and revoke uses the same detailed trigger library. Choose the event, optional target, required amount, progress owner, repeat policy, priority, and cooldown.</p><form id="progressionForm"><div class="modal-grid">
      <label>Result<select id="progressionKind"><option value="grants">Grant this stage</option><option value="revokes">Revoke this stage</option></select></label>
      <label>Trigger<select id="progressionCondition">${conditionOptions(current.conditionType, true)}</select></label>
      <label id="progressionTargetLabel" class="wide-field">Target ID or event<input id="progressionTarget" list="progressionTargets" value="${escapeHtml(current.conditionTarget)}" placeholder="minecraft:wither or my_event"><datalist id="progressionTargets"></datalist></label>
      <label id="progressionCountLabel">Required amount<input id="progressionCount" type="number" min="1" value="${current.count}"></label>
      <label>Repeat policy<select id="progressionRepeat"><option value="once">Once</option><option value="edge">When it changes to true</option><option value="always">Every successful evaluation</option></select></label>
      <label>Who owns progress?<select id="progressionScope"><option value="player">Each player</option><option value="team">Whole team</option><option value="server">Whole server</option></select></label>
      <label>Priority<input id="progressionPriority" type="number" value="${current.priority}"></label>
      <label>Cooldown<input id="progressionCooldown" value="${escapeHtml(current.cooldown)}" placeholder="Optional. Example 30s"></label>
      <div id="progressionConditionHelp" class="condition-help"></div>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save progression</button></div></form>`, () => {
      $("progressionKind").value = current.kind;
      $("progressionRepeat").value = current.repeat;
      $("progressionScope").value = current.scope;
      bindConditionAutocomplete("progressionCondition", "progressionTarget", "progressionTargets");
      updateConditionHelp("progressionCondition", "progressionTargetLabel", "progressionCountLabel", "progressionConditionHelp");
      $("progressionCondition").addEventListener("change", () => updateConditionHelp(
        "progressionCondition", "progressionTargetLabel", "progressionCountLabel", "progressionConditionHelp"));
      $("progressionForm").onsubmit = async event => {
        event.preventDefault();
        const model = {
          ...current, kind: $("progressionKind").value, conditionType: $("progressionCondition").value,
          conditionTarget: $("progressionTarget").value.trim(), count: Number($("progressionCount").value || 1),
          repeat: $("progressionRepeat").value, scope: $("progressionScope").value,
          priority: Number($("progressionPriority").value || 0), cooldown: $("progressionCooldown").value.trim()
        };
        try { await saveProgression(stage, entry, model); closeModal(); toast("Progression saved to the draft"); }
        catch (error) { toast(error.message); }
      };
    });
  }

  async function saveProgression(stage, previous, model) {
    let content = state.boot.draft.files[stage.progressionPath] || "";
    const block = serializeProgression(stage, model, previous);
    if (previous) {
      const found = extractArrayBlocks(content, previous.kind)[previous.tableIndex];
      if (!found) throw new Error("This progression entry changed. Reopen it and try again.");
      content = content.slice(0, found.start) + content.slice(found.end);
    }
    content = appendTomlBlock(content, block);
    await mutate(stage.progressionPath, content);
  }

  function serializeProgression(stage, model, previous) {
    const oldId = previous ? stringValue(readBlockValue(previous.raw, "id")) : "";
    const id = oldId || childId(stage.id, `${model.kind === "grants" ? "grant" : "revoke"}_${Date.now().toString(36)}`);
    const lines = [`[[${model.kind}]]`, `id = ${encodeToml(id)}`, `repeat = ${encodeToml(model.repeat)}`, `scope = ${encodeToml(model.scope)}`, `priority = ${Number(model.priority || 0)}`, `condition = ${conditionToml(model.conditionType, model.conditionTarget, model.count)}`];
    if (model.cooldown) lines.push(`cooldown = ${encodeToml(model.cooldown)}`);
    return lines.join("\n") + "\n";
  }

  async function deleteProgression(stage, entry) {
    if (!confirm("Delete this progression entry from the draft")) return;
    let content = state.boot.draft.files[stage.progressionPath] || "";
    const found = extractArrayBlocks(content, entry.kind)[entry.tableIndex];
    if (!found) throw new Error("The progression entry no longer exists");
    content = content.slice(0, found.start) + content.slice(found.end);
    await mutate(stage.progressionPath, content);
    toast("Progression deleted from the draft");
  }

  function openFeatureEditor(stage, kind) {
    if (stage.legacy && kind !== "rewards" && kind !== "cost") return toast("Create a three file stage to use this guided advanced feature");
    if (kind === "rewards") return openRewardsEditor(stage);
    if (kind === "cost") return openPurchaseEditor(stage);
    if (kind === "challenges") return openChallengeEditor(stage);
    if (kind === "variables") return openVariableEditor(stage);
    if (kind === "modifiers") return openModifierEditor(stage);
    if (kind === "drop_modifier") return openDropModifierEditor(stage);
    openAdvancedHub(stage);
  }

  function openPurchaseEditor(stage) {
    const path = stage.progressionPath;
    const content = state.boot.draft.files[path] || "";
    const current = purchaseModel(content);
    const items = current.items.map(item => ({ ...item }));
    openModal(`<h2 id="modalTitle">Buy ${escapeHtml(stage.name)} with items</h2><p>Players give these items to the server from the in game progression tree. Payment prerequisite checks rewards cooldowns and refunds are server authoritative.</p><form id="purchaseForm"><div class="modal-grid">
      <section class="modal-section"><h3>Item payment</h3><div id="purchaseItems" class="purchase-items"></div><label class="wide-field">Find an item on this server<input id="purchaseSearch" placeholder="Diamond"></label><div id="purchaseResults" class="picker-results compact-results"></div></section>
      <label>Extra XP levels<input id="purchaseXp" type="number" min="0" value="${current.xp}"></label>
      <label>Purchase cooldown<input id="purchaseCooldown" value="${escapeHtml(stringValue(readTomlValue(content, "cost.cooldown")) || "2s")}" placeholder="2s"></label>
      <label>Refund after revoke<input id="purchaseRefund" type="number" min="0" max="100" value="${Math.max(0, Number(readTomlValue(content, "cost.refund_percent") || 0))}"><small>Percent of the payment returned.</small></label>
      <label class="toggle-line"><input id="purchaseBypass" type="checkbox" ${booleanValue(readTomlValue(content, "cost.bypass_requirements")) ? "checked" : ""}>Allow payment before trigger requirements are complete. Required stages still apply.</label>
    </div><div class="modal-actions">${current.enabled ? `<button type="button" id="removePurchase" class="danger">Remove purchase</button>` : ""}<button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save item purchase</button></div></form>`, () => {
      const renderItems = () => {
        $("purchaseItems").innerHTML = items.length ? items.map((item, index) => `<div class="purchase-item"><span><strong>${escapeHtml(title(item.id.split(":").pop()))}</strong><small>${escapeHtml(item.id)}</small></span><label>Amount<input data-purchase-count="${index}" type="number" min="1" value="${item.count}"></label><button type="button" data-remove-purchase-item="${index}" class="danger">×</button></div>`).join("") : `<div class="empty-state"><span>No item payment yet. Search below and click an item.</span></div>`;
        qa("[data-purchase-count]", $("purchaseItems")).forEach(input => input.oninput = () => items[Number(input.dataset.purchaseCount)].count = Math.max(1, Number(input.value || 1)));
        qa("[data-remove-purchase-item]", $("purchaseItems")).forEach(button => button.onclick = () => { items.splice(Number(button.dataset.removePurchaseItem), 1); renderItems(); });
      };
      const search = debounce(async () => {
        const result = await api({ action: "catalog", catalog: "progressivestages:items", field: "cost.items", mode: "id", text: $("purchaseSearch").value, pageSize: 30, catalogRevision: state.boot.catalog.revision });
        $("purchaseResults").innerHTML = result.entries.map(entry => `<button type="button" data-purchase-item="${escapeHtml(entry.key)}"><strong>${escapeHtml(entry.label)}</strong><small>${escapeHtml(entry.key)}</small></button>`).join("") || `<span class="muted">No matching items.</span>`;
        qa("[data-purchase-item]", $("purchaseResults")).forEach(button => button.onclick = () => {
          const id = stripSelectorPrefix(button.dataset.purchaseItem);
          const existing = items.find(item => item.id === id);
          if (existing) existing.count++;
          else items.push({ id, count: 1 });
          renderItems();
        });
      }, 180);
      $("purchaseSearch").oninput = search;
      $("purchaseForm").onsubmit = async event => {
        event.preventDefault();
        const xp = Math.max(0, Number($("purchaseXp").value || 0));
        if (!items.length && !xp) return toast("Add at least one item or an XP cost");
        let updated = state.boot.draft.files[path] || "";
        updated = upsertToml(updated, "cost.items", items.map(item => `${item.id}:${Math.max(1, Number(item.count || 1))}`));
        updated = upsertToml(updated, "cost.xp_levels", xp);
        updated = upsertToml(updated, "cost.cooldown", $("purchaseCooldown").value.trim());
        updated = upsertToml(updated, "cost.refund_percent", Math.max(0, Math.min(100, Number($("purchaseRefund").value || 0))));
        updated = upsertToml(updated, "cost.bypass_requirements", $("purchaseBypass").checked);
        try { await mutate(path, updated); closeModal(); toast(`${stage.name} can now be bought from the stage tree`); } catch (error) { toast(error.message); }
      };
      q("#removePurchase")?.addEventListener("click", async () => {
        if (!confirm(`Remove the purchase option from ${stage.name}`)) return;
        try { await mutate(path, removeTomlSection(state.boot.draft.files[path] || "", "cost")); closeModal(); toast("Purchase option removed"); } catch (error) { toast(error.message); }
      });
      renderItems();
      search();
    });
  }

  function openDropModifierEditor(stage) {
    openModal(`<h2 id="modalTitle">Add a targeted mining bonus</h2><p>Change the final item count only when the source block output item tool stage and enchantment all match.</p><form id="dropModifierForm"><div class="modal-grid">
      <label>Block selection<select id="dropBlockMode"><option value="id">Exact block</option><option value="mod">Whole mod</option><option value="tag">Block tag</option><option value="name">Name match</option></select></label>
      <label>Source block<input id="dropBlock" list="dropBlockResults" placeholder="minecraft:diamond_ore" required><datalist id="dropBlockResults"></datalist></label>
      <label>Output selection<select id="dropItemMode"><option value="id">Exact item</option><option value="mod">Whole mod</option><option value="tag">Item tag</option><option value="name">Name match</option></select></label>
      <label>Output item<input id="dropItem" list="dropItemResults" placeholder="minecraft:diamond" required><datalist id="dropItemResults"></datalist></label>
      <label>Tool selection<select id="dropToolMode"><option value="id">Exact tool</option><option value="mod">Whole mod</option><option value="tag" selected>Tool tag</option><option value="name">Name match</option></select></label>
      <label>Required tool<input id="dropTool" list="dropToolResults" placeholder="minecraft:pickaxes"><datalist id="dropToolResults"></datalist></label>
      <label>Required enchantment<input id="dropEnchantment" list="dropEnchantmentResults" placeholder="minecraft:fortune"><datalist id="dropEnchantmentResults"></datalist></label>
      <label>Minimum enchantment level<input id="dropEnchantmentLevel" type="number" min="0" value="1"></label>
      <label>Multiply final drops by<input id="dropMultiply" type="number" min="0" step="0.1" value="2"></label>
      <label>Add before multiplying<input id="dropAdd" type="number" step="1" value="0"></label>
      <label>Priority<input id="dropPriority" type="number" value="500"></label>
      <label class="toggle-line"><input id="dropExclusive" type="checkbox" checked>Stop lower priority drop bonuses after this one matches</label>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Add mining bonus</button></div></form>`, () => {
      bindCatalogAutocomplete("dropBlock", "dropBlockResults", "blocks");
      bindCatalogAutocomplete("dropItem", "dropItemResults", "items");
      bindCatalogAutocomplete("dropTool", "dropToolResults", "items");
      bindCatalogAutocomplete("dropEnchantment", "dropEnchantmentResults", "enchantments");
      $("dropModifierForm").onsubmit = async event => {
        event.preventDefault();
        const block = normalizeSelector($("dropBlockMode").value, $("dropBlock").value.trim(), $("dropBlock").value.trim());
        const item = normalizeSelector($("dropItemMode").value, $("dropItem").value.trim(), $("dropItem").value.trim());
        const toolValue = $("dropTool").value.trim();
        const lines = ["[[drop_modifiers]]", `id = ${encodeToml(childId(stage.id, `drop_${Date.now().toString(36)}`))}`, `blocks = ${encodeToml([block])}`, `drops = ${encodeToml([item])}`];
        if (toolValue) lines.push(`tools = ${encodeToml([normalizeSelector($("dropToolMode").value, toolValue, toolValue)])}`);
        if ($("dropEnchantment").value.trim()) lines.push(`required_enchantment = ${encodeToml($("dropEnchantment").value.trim())}`, `minimum_enchantment_level = ${Math.max(0, Number($("dropEnchantmentLevel").value || 0))}`);
        lines.push(`add = ${Number($("dropAdd").value || 0)}`, `multiply = ${Math.max(0, Number($("dropMultiply").value || 1))}`, `priority = ${Number($("dropPriority").value || 0)}`, `exclusive = ${$("dropExclusive").checked}`);
        try { await mutate(stage.rulesPath, appendTomlBlock(state.boot.draft.files[stage.rulesPath] || "", lines.join("\n"))); closeModal(); toast("Targeted mining bonus added to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openChallengeEditor(stage) {
    openModal(`<h2 id="modalTitle">Add a challenge</h2><p>Create a timed or limited encounter without writing a challenge object.</p><form id="challengeForm"><div class="modal-grid">
      <label class="wide-field">Challenge name<input id="challengeName" placeholder="Wither trial" required></label>
      <label>Start when<select id="challengeStart">${conditionOptions("boss_session", true)}</select></label>
      <label>Start target<input id="challengeStartTarget" list="challengeStartTargets" placeholder="minecraft:wither"><datalist id="challengeStartTargets"></datalist></label>
      <label>Succeed when<select id="challengeSuccess">${conditionOptions("kill", true)}</select></label>
      <label>Success target<input id="challengeSuccessTarget" list="challengeSuccessTargets" placeholder="minecraft:wither"><datalist id="challengeSuccessTargets"></datalist></label>
      <label>Maximum hits taken<input id="challengeHits" type="number" min="0" value="0"><small>Zero means no hit limit.</small></label>
      <label>Boss entity<input id="challengeBoss" placeholder="minecraft:wither"></label>
      <label>Time limit<input id="challengeTimeout" placeholder="5m"></label>
      <label>Retries<input id="challengeRetries" type="number" min="0" value="0"></label>
      <label>Progress belongs to<select id="challengeScope"><option value="player">Each player</option><option value="team">Whole team</option><option value="server">Whole server</option></select></label>
      <label>HUD location<select id="challengeHud"><option value="top">Top</option><option value="bottom">Bottom</option><option value="left">Left</option><option value="right">Right</option></select></label>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Add challenge</button></div></form>`, () => {
      bindConditionAutocomplete("challengeStart", "challengeStartTarget", "challengeStartTargets");
      bindConditionAutocomplete("challengeSuccess", "challengeSuccessTarget", "challengeSuccessTargets");
      $("challengeForm").onsubmit = async event => {
        event.preventDefault();
        const name = $("challengeName").value.trim();
        const lines = ["[[challenges]]", `id = ${encodeToml(childId(stage.id, `challenge_${slug(name) || Date.now().toString(36)}`))}`, `title = ${encodeToml(name)}`, `scope = ${encodeToml($("challengeScope").value)}`, `start_when = ${conditionToml($("challengeStart").value, $("challengeStartTarget").value.trim(), 1)}`, `success_when = ${conditionToml($("challengeSuccess").value, $("challengeSuccessTarget").value.trim(), 1)}`, `retries = ${Number($("challengeRetries").value || 0)}`, `hud.enabled = true`, `hud.placement = ${encodeToml($("challengeHud").value)}`];
        if (Number($("challengeHits").value) > 0) lines.push(`max_hits = ${Number($("challengeHits").value)}`);
        if ($("challengeBoss").value.trim()) lines.push(`boss = ${encodeToml($("challengeBoss").value.trim())}`);
        if ($("challengeTimeout").value.trim()) lines.push(`timeout = ${encodeToml($("challengeTimeout").value.trim())}`);
        try { await mutate(stage.progressionPath, appendTomlBlock(state.boot.draft.files[stage.progressionPath] || "", lines.join("\n"))); closeModal(); toast("Challenge added to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openVariableEditor(stage) {
    openModal(`<h2 id="modalTitle">Add a variable</h2><p>Variables store bounded player, team, or server values for conditions and formulas.</p><form id="variableForm"><div class="modal-grid">
      <label>Variable name<input id="variableName" placeholder="quest_points" required></label>
      <label>Value type<select id="variableType"><option value="integer">Whole number</option><option value="decimal">Decimal number</option><option value="boolean">Yes or no</option><option value="string">Text</option><option value="currency">Currency</option><option value="counter">Counter</option></select></label>
      <label>Owner<select id="variableScope"><option value="player">Each player</option><option value="team">Whole team</option><option value="server">Whole server</option></select></label>
      <label>Starting value<input id="variableDefault" value="0"></label>
      <label>Minimum<input id="variableMinimum" type="number" placeholder="No minimum"></label>
      <label>Maximum<input id="variableMaximum" type="number" placeholder="No maximum"></label>
      <label class="toggle-line"><input id="variablePersistent" type="checkbox" checked>Save this value across restarts</label>
      <label class="toggle-line"><input id="variableVisible" type="checkbox">Synchronize this value for player UI</label>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Add variable</button></div></form>`, () => {
      $("variableForm").onsubmit = async event => {
        event.preventDefault();
        const name = slug($("variableName").value);
        if (!name) return toast("Enter a variable name");
        const type = $("variableType").value;
        let defaultValue = $("variableDefault").value;
        if (["integer", "decimal", "currency", "counter"].includes(type)) defaultValue = Number(defaultValue || 0);
        if (type === "boolean") defaultValue = String(defaultValue).toLowerCase() === "true";
        const lines = ["[[variables]]", `id = ${encodeToml(childId(stage.id, `variable_${name}`))}`, `type = ${encodeToml(type)}`, `scope = ${encodeToml($("variableScope").value)}`, `default = ${encodeToml(defaultValue)}`, `persistent = ${$("variablePersistent").checked}`, `sync_visible = ${$("variableVisible").checked}`];
        if ($("variableMinimum").value !== "") lines.push(`minimum = ${Number($("variableMinimum").value)}`);
        if ($("variableMaximum").value !== "") lines.push(`maximum = ${Number($("variableMaximum").value)}`);
        try { await mutate(stage.progressionPath, appendTomlBlock(state.boot.draft.files[stage.progressionPath] || "", lines.join("\n"))); closeModal(); toast("Variable added to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openModifierEditor(stage) {
    openModal(`<h2 id="modalTitle">Add an item modifier</h2><p>Apply an attribute or status effect while a matching item is held, worn, used, or carried.</p><form id="modifierForm"><div class="modal-grid">
      <label class="wide-field">Item selector<input id="modifierItem" placeholder="id:minecraft:diamond_sword or tag:c:swords" required></label>
      <label>When item is<select id="modifierContext"><option value="either_hand">Held in either hand</option><option value="main_hand">In main hand</option><option value="off_hand">In off hand</option><option value="selected_hotbar">Selected in hotbar</option><option value="inventory">Anywhere in inventory</option><option value="equipment">Worn as equipment</option><option value="curios">In a Curios slot</option><option value="use">Being used</option><option value="attack">Attacking</option></select></label>
      <label>Change type<select id="modifierKind"><option value="attribute">Attribute</option><option value="effect">Status effect</option></select></label>
      <label>ID<input id="modifierValue" placeholder="minecraft:generic.attack_damage" required></label>
      <label>Amount or amplifier<input id="modifierAmount" type="number" step="any" value="1"></label>
      <label>Attribute operation<select id="modifierOperation"><option value="add_value">Add value</option><option value="add_multiplied_base">Multiply base</option><option value="add_multiplied_total">Multiply total</option></select></label>
      <label>Effect duration in ticks<input id="modifierDuration" type="number" min="1" value="40"></label>
      <label>Priority<input id="modifierPriority" type="number" value="0"></label>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Add modifier</button></div></form>`, () => {
      $("modifierForm").onsubmit = async event => {
        event.preventDefault();
        const item = $("modifierItem").value.trim();
        const kind = $("modifierKind").value;
        const lines = ["[[item_modifiers]]", `id = ${encodeToml(childId(stage.id, `modifier_${Date.now().toString(36)}`))}`, `items = ${encodeToml([item])}`, `contexts = ${encodeToml([$("modifierContext").value])}`, `priority = ${Number($("modifierPriority").value || 0)}`];
        if (kind === "attribute") lines.push("", "[[item_modifiers.attributes]]", `id = ${encodeToml($("modifierValue").value.trim())}`, `amount = ${Number($("modifierAmount").value || 0)}`, `operation = ${encodeToml($("modifierOperation").value)}`);
        else lines.push("", "[[item_modifiers.effects]]", `id = ${encodeToml($("modifierValue").value.trim())}`, `amplifier = ${Math.max(0, Number($("modifierAmount").value || 0))}`, `duration_ticks = ${Math.max(1, Number($("modifierDuration").value || 40))}`);
        try { await mutate(stage.rulesPath, appendTomlBlock(state.boot.draft.files[stage.rulesPath] || "", lines.join("\n"))); closeModal(); toast("Item modifier added to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openAdvancedHub(stage) {
    openModal(`<h2 id="modalTitle">Advanced stage features</h2><p>Choose a guided builder. Registered Java and KubeJS features remain visible in the Inspector.</p><div class="feature-grid"><button data-advanced="formula" class="feature-button"><strong>Formula</strong><small>Named safe arithmetic</small></button><button data-advanced="states" class="feature-button"><strong>Stage states</strong><small>Custom ownership lifecycle</small></button><button data-advanced="profile" class="feature-button"><strong>Affinity profile</strong><small>Proficiency based behavior</small></button><button data-advanced="template" class="feature-button"><strong>Template</strong><small>Reusable includes and merge policy</small></button><button data-advanced="extension" class="feature-button"><strong>Extensions</strong><small>Java and KubeJS registrations</small></button><button data-advanced="source" class="feature-button"><strong>Source</strong><small>Optional exact TOML control</small></button></div><div class="modal-actions"><button data-close-modal class="ghost">Close</button></div>`, () => {
      qa("[data-advanced]", $("modal")).forEach(button => button.onclick = () => {
        const kind = button.dataset.advanced;
        closeModal();
        if (kind === "formula") return openFormulaEditor(stage);
        if (kind === "states") return openStatesEditor(stage);
        if (kind === "profile") return openProfileEditor(stage);
        if (kind === "template") return openTemplateEditor(stage);
        if (kind === "extension") { showInspector("extensions"); return; }
        state.path = stage.progressionPath;
        updateSource(state.path);
        switchView("source");
      });
    });
  }

  function openFormulaEditor(stage) {
    openModal(`<h2 id="modalTitle">Add a formula</h2><p>Give a readable name to safe arithmetic that can reference variables and other formulas.</p><form id="formulaForm"><div class="modal-grid"><label>Formula name<input id="formulaName" placeholder="combat_score" required></label><label class="wide-field">Calculation<input id="formulaExpression" placeholder="kills * 2 + quest_points" required></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save formula</button></div></form>`, () => {
      $("formulaForm").onsubmit = async event => {
        event.preventDefault();
        const name = slug($("formulaName").value);
        if (!name) return toast("Enter a formula name");
        const content = upsertToml(state.boot.draft.files[stage.progressionPath] || "", `formulas.${name}`, $("formulaExpression").value.trim());
        try { await mutate(stage.progressionPath, content); closeModal(); toast("Formula saved to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openStatesEditor(stage) {
    openObjectSectionEditor(stage, "states", [["values", "Available state names", "list", "missing\navailable\nowned\ncompleted"], ["ownership_states", "States that count as owned", "list", "owned\ncompleted"], ["initial", "Starting state", "text", "missing"]]);
  }

  function openProfileEditor(stage) {
    openModal(`<h2 id="modalTitle">Add an affinity profile</h2><p>Change matching content after a proficiency value reaches a threshold.</p><form id="profileForm"><div class="modal-grid"><label>Profile name<input id="profileName" placeholder="sword_mastery" required></label><label>Content selector<input id="profileContent" placeholder="tag:c:swords" required></label><label>Proficiency variable<input id="profileVariable" placeholder="sword_xp" required></label><label>Level name<input id="profileLevel" value="trained"></label><label>Minimum value<input id="profileMinimum" type="number" value="10"></label><label>Effect<select id="profileEffect"><option value="normal">Normal</option><option value="deny">Deny</option><option value="weaken">Weaken</option><option value="strengthen">Strengthen</option><option value="increase_cost">Increase cost</option><option value="change_cooldown">Change cooldown</option><option value="replace_behavior">Replace behavior</option></select></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Add profile</button></div></form>`, () => {
      $("profileForm").onsubmit = async event => {
        event.preventDefault();
        const name = slug($("profileName").value);
        const lines = ["[[profiles]]", `id = ${encodeToml(childId(stage.id, `profile_${name}`))}`, `content = ${encodeToml([$("profileContent").value.trim()])}`, `proficiency = ${encodeToml($("profileVariable").value.trim())}`, "", "[[profiles.levels]]", `id = ${encodeToml(slug($("profileLevel").value) || "trained")}`, `minimum = ${Number($("profileMinimum").value || 0)}`, `effect = ${encodeToml($("profileEffect").value)}`];
        try { await mutate(stage.progressionPath, appendTomlBlock(state.boot.draft.files[stage.progressionPath] || "", lines.join("\n"))); closeModal(); toast("Affinity profile added to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openTemplateEditor(stage) {
    openModal(`<h2 id="modalTitle">Add a template</h2><p>Create a reusable named bundle and choose how it combines with included templates.</p><form id="templateForm"><div class="modal-grid"><label>Template name<input id="templateName" placeholder="common_combat" required></label><label>Included templates<input id="templateIncludes" placeholder="pack:base_rules, pack:rewards"></label><label>Merge behavior<select id="templateMerge"><option value="deep_merge">Combine nested values</option><option value="replace">Replace earlier values</option><option value="append">Append lists</option></select></label><label class="wide-field">Simple fragment label<input id="templateLabel" placeholder="Reusable combat bundle"></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Add template</button></div></form>`, () => {
      $("templateForm").onsubmit = async event => {
        event.preventDefault();
        const name = slug($("templateName").value);
        const lines = ["[[templates]]", `id = ${encodeToml(childId(stage.id, `template_${name}`))}`, `includes = ${encodeToml($("templateIncludes").value.split(",").map(value => value.trim()).filter(Boolean))}`, `merge = ${encodeToml($("templateMerge").value)}`, `fragment = ${encodeToml({ label: $("templateLabel").value.trim() })}`];
        try { await mutate(stage.progressionPath, appendTomlBlock(state.boot.draft.files[stage.progressionPath] || "", lines.join("\n"))); closeModal(); toast("Template added to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openRewardsEditor(stage) {
    const path = stage.progressionPath;
    const content = state.boot.draft.files[path] || "";
    const items = parseSimpleArray(readTomlValue(content, "rewards.items")) || [];
    const effects = parseSimpleArray(readTomlValue(content, "rewards.effects")) || [];
    const commands = parseSimpleArray(readTomlValue(content, "rewards.commands")) || [];
    openModal(`<h2 id="modalTitle">Stage rewards</h2><p>Give these rewards each time the stage is genuinely granted.</p><form id="rewardForm"><div class="modal-grid">
      <label class="wide-field">Items and amounts<textarea id="rewardItems" rows="3" placeholder="One per line. minecraft:diamond:5">${escapeHtml(items.join("\n"))}</textarea></label>
      <label class="wide-field">Effects<textarea id="rewardEffects" rows="3" placeholder="minecraft:strength:60:1">${escapeHtml(effects.join("\n"))}</textarea></label>
      <label class="wide-field">Commands<textarea id="rewardCommands" rows="3" placeholder="give {player} minecraft:cake 1">${escapeHtml(commands.join("\n"))}</textarea></label>
      <label>XP levels<input id="rewardLevels" type="number" min="0" value="${Number(readTomlValue(content, "rewards.xp_levels") || 0)}"></label>
      <label>XP points<input id="rewardPoints" type="number" min="0" value="${Number(readTomlValue(content, "rewards.xp_points") || 0)}"></label>
      <label class="wide-field">Teleport destination<input id="rewardTeleport" value="${escapeHtml(stringValue(readTomlValue(content, "rewards.teleport")))}" placeholder="minecraft:the_end 0 80 0"></label>
    </div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save rewards</button></div></form>`, () => {
      $("rewardForm").onsubmit = async event => {
        event.preventDefault();
        let updated = state.boot.draft.files[path] || "";
        updated = upsertToml(updated, "rewards.items", lineValues($("rewardItems").value));
        updated = upsertToml(updated, "rewards.effects", lineValues($("rewardEffects").value));
        updated = upsertToml(updated, "rewards.commands", lineValues($("rewardCommands").value));
        updated = upsertToml(updated, "rewards.xp_levels", Number($("rewardLevels").value || 0));
        updated = upsertToml(updated, "rewards.xp_points", Number($("rewardPoints").value || 0));
        updated = upsertToml(updated, "rewards.teleport", $("rewardTeleport").value.trim());
        try { await mutate(path, updated); closeModal(); toast("Rewards saved to the draft"); } catch (error) { toast(error.message); }
      };
    });
  }

  function openObjectSectionEditor(stage, section, fields) {
    const path = stage.progressionPath;
    const content = state.boot.draft.files[path] || "";
    openModal(`<h2 id="modalTitle">${title(section)}</h2><p>Fill only the options you want to use.</p><form id="objectSectionForm"><div class="modal-grid">${fields.map(([key, label, type, placeholder]) => {
      const raw = readTomlValue(content, `${section}.${key}`);
      const value = type === "list" ? (parseSimpleArray(raw) || []).join("\n") : type === "boolean" ? booleanValue(raw) : stringValue(raw) || raw;
      if (type === "list") return `<label class="wide-field">${label}<textarea data-object-key="${key}" data-object-type="list" placeholder="${escapeHtml(placeholder)}">${escapeHtml(value)}</textarea></label>`;
      if (type === "boolean") return `<label class="toggle-line"><input data-object-key="${key}" data-object-type="boolean" type="checkbox" ${value ? "checked" : ""}>${label}</label>`;
      return `<label>${label}<input data-object-key="${key}" data-object-type="${type}" ${type === "number" ? "type=\"number\"" : ""} value="${escapeHtml(value)}" placeholder="${escapeHtml(placeholder)}"></label>`;
    }).join("")}</div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save ${title(section).toLowerCase()}</button></div></form>`, () => {
      $("objectSectionForm").onsubmit = async event => {
        event.preventDefault();
        let updated = state.boot.draft.files[path] || "";
        qa("[data-object-key]", $("objectSectionForm")).forEach(input => {
          const value = input.dataset.objectType === "list" ? lineValues(input.value) : input.dataset.objectType === "boolean" ? input.checked : input.dataset.objectType === "number" ? Number(input.value || 0) : input.value;
          updated = upsertToml(updated, `${section}.${input.dataset.objectKey}`, value);
        });
        try { await mutate(path, updated); closeModal(); toast(`${title(section)} saved to the draft`); } catch (error) { toast(error.message); }
      };
    });
  }

  function renderSettings() {
    const schemas = state.boot.schemas.filter(schema => schema.file === "progressivestages.toml");
    const text = state.boot.draft.files["progressivestages.toml"] || "";
    const groups = {};
    schemas.forEach(schema => (groups[schema.path.split(".")[0]] ??= []).push(schema));
    $("formView").innerHTML = `<section class="stage-hero"><span class="hero-icon">⚙</span><div><h1>Main settings</h1><p>Generated from the running server configuration.</p></div><span class="stage-id">progressivestages.toml</span></section>` + Object.entries(groups).map(([group, fields]) => `<article class="builder-section"><div class="builder-section-head"><div><h2>${title(group)}</h2><p>Server configuration options.</p></div></div><div class="builder-section-body"><div class="details-grid">${fields.map(schema => settingControl(schema, text)).join("")}</div></div></article>`).join("");
    qa("[data-setting]", $("formView")).forEach(input => input.onchange = async () => {
      const schema = schemas.find(value => value.id === input.dataset.setting);
      const value = schema.type === "BOOLEAN" ? input.checked : ["INTEGER", "DECIMAL"].includes(schema.type) ? Number(input.value || 0) : schema.type === "LIST" ? lineValues(input.value) : input.value;
      await mutate("progressivestages.toml", upsertToml(state.boot.draft.files["progressivestages.toml"] || "", schema.path, value));
      toast("Setting saved to the draft");
    });
  }

  function settingControl(schema, text) {
    const raw = readTomlValue(text, schema.path);
    const value = editorValue(raw, schema);
    if (schema.type === "BOOLEAN") return `<label class="toggle-line"><input type="checkbox" data-setting="${schema.id}" ${String(value) === "true" ? "checked" : ""}>${escapeHtml(schema.label)}<small>${escapeHtml(schema.help)}</small></label>`;
    if (schema.type === "ENUM") return `<label>${escapeHtml(schema.label)}<select data-setting="${schema.id}">${schema.enumValues.map(option => `<option ${String(value) === String(option) ? "selected" : ""}>${escapeHtml(option)}</option>`).join("")}</select><small>${escapeHtml(schema.help)}</small></label>`;
    if (schema.type === "LIST") return `<label class="wide-field">${escapeHtml(schema.label)}<textarea data-setting="${schema.id}">${escapeHtml(value)}</textarea><small>${escapeHtml(schema.help)}</small></label>`;
    return `<label>${escapeHtml(schema.label)}<input data-setting="${schema.id}" value="${escapeHtml(value)}" ${["INTEGER", "DECIMAL"].includes(schema.type) ? "type=\"number\" step=\"any\"" : ""}><small>${escapeHtml(schema.help)}</small></label>`;
  }

  function selectSettings() {
    if (!discardSourceChanges()) return;
    state.stageKey = "__settings__";
    state.path = "progressivestages.toml";
    updateSource(state.path);
    switchView("form");
    renderAll();
    $("breadcrumb").textContent = "Main server settings";
  }

  function renderGraph() {
    if (!state.boot) return;
    const allStages = stagePackages().filter(stage => !stage.archived
      && !booleanValue(readTomlValue(state.boot.draft.files[stage.stagePath] || "", "stage.hidden")));
    const categorySelect = $("graphCategory");
    const graphCategories = [...new Set(allStages.map(stage => stringValue(readTomlValue(
      state.boot.draft.files[stage.stagePath] || "", "stage.category")).trim()).filter(Boolean))]
      .sort((left, right) => left.localeCompare(right));
    const selectedCategory = categorySelect?.value || "";
    if (categorySelect) {
      categorySelect.innerHTML = `<option value="">All categories</option>` + graphCategories.map(category =>
        `<option value="${escapeHtml(category)}" ${category === selectedCategory ? "selected" : ""}>${escapeHtml(category)}</option>`).join("");
    }
    const search = ($("graphSearch")?.value || "").trim().toLowerCase();
    const allById = Object.fromEntries(allStages.map(stage => [stage.id, stage]));
    const directIds = new Set(allStages.filter(stage => {
      const content = state.boot.draft.files[stage.stagePath] || "";
      const category = stringValue(readTomlValue(content, "stage.category")).trim();
      return (!selectedCategory || category === selectedCategory)
        && (!search || `${stage.name} ${stage.id} ${stage.description}`.toLowerCase().includes(search));
    }).map(stage => stage.id));
    const visibleIds = new Set(directIds);
    const includeAncestors = id => {
      const stage = allById[id];
      if (!stage) return;
      const content = state.boot.draft.files[stage.stagePath] || "";
      for (const dependency of dependenciesFor(content, stage.legacy)) {
        if (visibleIds.has(dependency)) continue;
        visibleIds.add(dependency);
        includeAncestors(dependency);
      }
    };
    [...visibleIds].forEach(includeAncestors);
    const stages = allStages.filter(stage => visibleIds.has(stage.id));
    const base = stages.map(stage => {
      const content = state.boot.draft.files[stage.stagePath] || "";
      const rawXText = readTomlValue(content, "display.x").trim();
      const rawYText = readTomlValue(content, "display.y").trim();
      const rawX = Number(rawXText);
      const rawY = Number(rawYText);
      return {
        stage, rawX, rawY, hasManual: Boolean(rawXText && rawYText),
        dependencies: dependenciesFor(content, stage.legacy),
        mode: stringValue(readTomlValue(content, "stage.dependency_mode")) || "all",
        count: Math.max(1, Number(readTomlValue(content, "stage.dependency_count") || 1))
      };
    });
    const baseById = Object.fromEntries(base.map(node => [node.stage.id, node]));
    const depths = {};
    const depthOf = (id, visiting = new Set()) => {
      if (depths[id] != null) return depths[id];
      if (visiting.has(id)) return 0;
      visiting.add(id);
      const node = baseById[id];
      const depth = !node?.dependencies.length ? 0 : 1 + Math.max(0, ...node.dependencies.map(parent => depthOf(parent, new Set(visiting))));
      depths[id] = depth;
      return depth;
    };
    base.forEach(node => depthOf(node.stage.id));
    const maxDepth = Math.max(0, ...Object.values(depths));
    const lanes = {};
    base.forEach(node => (lanes[depths[node.stage.id]] ??= []).push(node));
    Object.values(lanes).forEach(lane => lane.sort((left, right) => {
      const leftCategory = stringValue(readTomlValue(state.boot.draft.files[left.stage.stagePath] || "", "stage.category"));
      const rightCategory = stringValue(readTomlValue(state.boot.draft.files[right.stage.stagePath] || "", "stage.category"));
      return leftCategory.localeCompare(rightCategory) || left.stage.name.localeCompare(right.stage.name);
    }));
    const children = {};
    base.forEach(node => node.dependencies.forEach(parent => (children[parent] ??= []).push(node.stage.id)));
    const orderByNeighbors = (depth, neighborDepth, idsFor) => {
      const lane = lanes[depth] || [];
      const neighbor = lanes[neighborDepth] || [];
      const positions = Object.fromEntries(neighbor.map((node, index) => [node.stage.id, index]));
      const original = Object.fromEntries(lane.map((node, index) => [node.stage.id, index]));
      const score = node => {
        const values = idsFor(node).map(id => positions[id]).filter(value => value != null);
        return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length
          : original[node.stage.id];
      };
      lane.sort((left, right) => score(left) - score(right)
        || left.stage.name.localeCompare(right.stage.name));
    };
    for (let pass = 0; pass < 4; pass++) {
      for (let depth = 1; depth <= maxDepth; depth++) {
        orderByNeighbors(depth, depth - 1, node => node.dependencies);
      }
      for (let depth = maxDepth - 1; depth >= 0; depth--) {
        orderByNeighbors(depth, depth + 1, node => children[node.stage.id] || []);
      }
    }
    const widestLane = Math.max(1, ...Object.values(lanes).map(lane => lane.length));
    const viewportWidth = $("graphViewport")?.clientWidth || 0;
    const autoWidth = Math.max(820, viewportWidth - 8,
      GRAPH_PREVIEW_LEFT * 2 + Math.max(0, widestLane - 1) * GRAPH_LANE_X * GRAPH_PREVIEW_X + 220);
    const autoHeight = Math.max(520,
      GRAPH_PREVIEW_TOP * 2 + maxDepth * GRAPH_LAYER_Y * GRAPH_PREVIEW_Y + 130);
    const nodes = base.map(node => {
      const depth = depths[node.stage.id];
      const lane = lanes[depth];
      const index = lane.indexOf(node);
      const autoMapX = Math.round((widestLane - lane.length) * GRAPH_LANE_X / 2 + index * GRAPH_LANE_X);
      const autoMapY = (maxDepth - depth) * GRAPH_LAYER_Y;
      const manual = node.hasManual && Number.isFinite(node.rawX) && Number.isFinite(node.rawY)
        && node.rawX >= 0 && node.rawY >= 0;
      const mapX = manual ? node.rawX : autoMapX;
      const mapY = manual ? node.rawY : autoMapY;
      return {
        ...node, mapX, mapY,
        x: GRAPH_PREVIEW_LEFT + mapX * GRAPH_PREVIEW_X,
        y: GRAPH_PREVIEW_TOP + mapY * GRAPH_PREVIEW_Y
      };
    });
    const byId = Object.fromEntries(nodes.map(node => [node.stage.id, node]));
    const maxX = Math.max(autoWidth, ...nodes.map(node => node.x + 220));
    const maxY = Math.max(autoHeight, ...nodes.map(node => node.y + 130));
    const lines = nodes.flatMap(node => node.dependencies.map(id => byId[id]
      ? { from: byId[id], to: node } : null).filter(Boolean)).map(({ from, to }) =>
        graphPath(from.stage.key, to.stage.key, from.x + 89, from.y, to.x + 89, to.y + 52)).join("");
    $("graph").dataset.baseWidth = maxX;
    $("graph").dataset.baseHeight = maxY;
    $("graph").setAttribute("viewBox", `0 0 ${maxX} ${maxY}`);
    $("graph").innerHTML = `<g class="graph-lines">${lines}</g>` + nodes.map(node => `<foreignObject class="graph-node-shell ${node.stage.key === state.graphConnectFrom ? "graph-connect-source" : ""}" data-graph-stage="${escapeHtml(node.stage.key)}" data-map-x="${node.mapX}" data-map-y="${node.mapY}" x="${node.x}" y="${node.y}" width="178" height="52" tabindex="0" role="button" aria-label="${escapeHtml(node.stage.name)}. ${escapeHtml(graphDependencyLabel(node))}. Drag to change its position or press enter to edit it."><button xmlns="http://www.w3.org/1999/xhtml" class="graph-node" type="button" tabindex="-1"><span><strong>${escapeHtml(node.stage.name)}</strong><small>${escapeHtml(graphDependencyLabel(node))}</small></span></button></foreignObject>`).join("");
    applyGraphScale();
    updateGraphConnectionControls();
    if ($("graphStatus")) $("graphStatus").textContent = state.graphConnectFrom === "__choose_source__"
      ? "Select the prerequisite stage where the new branch starts."
      : state.graphConnectFrom
      ? `Select the stage that should require ${stagePackages().find(stage => stage.key === state.graphConnectFrom)?.name || "the selected stage"}.`
      : `${nodes.length} stages shown. ${directIds.size < nodes.length ? `${nodes.length - directIds.size} prerequisite paths included. ` : ""}Dragging saves player map coordinates. Connect stages draws a new prerequisite branch. Select a line to remove it.`;
    qa("[data-graph-from]", $("graph")).forEach(connection => {
      const remove = () => removeGraphConnection(connection.dataset.graphFrom, connection.dataset.graphTo);
      connection.onclick = event => { event.stopPropagation(); remove(); };
      connection.onpointerdown = event => event.stopPropagation();
      connection.onkeydown = event => {
        if (event.key !== "Enter" && event.key !== " " && event.key !== "Delete" && event.key !== "Backspace") return;
        event.preventDefault();
        remove();
      };
    });
    qa("[data-graph-stage]", $("graph")).forEach(node => {
      node.onclick = () => {
        if (node.dataset.dragged === "true") {
          node.dataset.dragged = "false";
          return;
        }
        if (state.graphConnectFrom) connectGraphStage(node.dataset.graphStage);
        else selectStage(node.dataset.graphStage);
      };
      node.onpointerdown = event => dragGraphNode(event, node);
      node.onkeydown = event => {
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        if (state.graphConnectFrom) connectGraphStage(node.dataset.graphStage);
        else selectStage(node.dataset.graphStage);
      };
    });
  }

  function graphPath(fromKey, toKey, x1, y1, x2, y2) {
    const middle = Math.round((y1 + y2) / 2);
    const fromName = stagePackages().find(stage => stage.key === fromKey)?.name || "prerequisite stage";
    const toName = stagePackages().find(stage => stage.key === toKey)?.name || "dependent stage";
    const path = `M ${x1} ${y1} C ${x1} ${middle}, ${x2} ${middle}, ${x2} ${y2}`;
    return `<g class="graph-connection" data-graph-from="${escapeHtml(fromKey)}" data-graph-to="${escapeHtml(toKey)}" tabindex="0" role="button" aria-label="Remove branch from ${escapeHtml(fromName)} to ${escapeHtml(toName)}"><path class="graph-line-hit" d="${path}"/><path class="graph-line-visible" d="${path}"/></g>`;
  }

  function updateGraphConnections() {
    qa("[data-graph-from]", $("graph")).forEach(connection => {
      const from = qa("[data-graph-stage]", $("graph")).find(node => node.dataset.graphStage === connection.dataset.graphFrom);
      const to = qa("[data-graph-stage]", $("graph")).find(node => node.dataset.graphStage === connection.dataset.graphTo);
      if (!from || !to) return;
      const x1 = Number(from.getAttribute("x")) + 89;
      const y1 = Number(from.getAttribute("y"));
      const x2 = Number(to.getAttribute("x")) + 89;
      const y2 = Number(to.getAttribute("y")) + 52;
      const middle = Math.round((y1 + y2) / 2);
      qa("path", connection).forEach(path => path.setAttribute("d", `M ${x1} ${y1} C ${x1} ${middle}, ${x2} ${middle}, ${x2} ${y2}`));
    });
  }

  function updateGraphConnectionControls() {
    const connecting = Boolean(state.graphConnectFrom);
    $("connectGraphStages")?.classList.toggle("active", connecting);
    $("cancelGraphConnection")?.classList.toggle("hidden", !connecting);
    if ($("connectGraphStages")) $("connectGraphStages").textContent = state.graphConnectFrom === "__choose_source__"
      ? "Choose prerequisite" : connecting ? "Choose destination" : "Connect stages";
  }

  function beginGraphConnection() {
    if (state.graphConnectFrom) {
      state.graphConnectFrom = "";
      renderGraph();
      return;
    }
    openModal(`<h2 id="modalTitle">Draw a progression branch</h2><p>First select the prerequisite stage. Then select the stage that requires it. The editor prevents duplicate branches and progression loops.</p><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button id="startGraphConnection" type="button" class="primary">Choose prerequisite stage</button></div>`, () => {
      $("startGraphConnection").onclick = () => {
        closeModal();
        state.graphConnectFrom = "__choose_source__";
        renderGraph();
      };
    });
  }

  function cancelGraphConnection() {
    state.graphConnectFrom = "";
    renderGraph();
  }

  async function connectGraphStage(stageKey) {
    const clicked = stagePackages().find(stage => stage.key === stageKey);
    if (!clicked) return;
    if (state.graphConnectFrom === "__choose_source__") {
      state.graphConnectFrom = clicked.key;
      renderGraph();
      return;
    }
    const source = stagePackages().find(stage => stage.key === state.graphConnectFrom);
    const target = clicked;
    if (!source) return cancelGraphConnection();
    if (source.id === target.id) return toast("A stage cannot require itself");
    const content = state.boot.draft.files[target.stagePath] || "";
    const dependencies = dependenciesFor(content, target.legacy);
    if (dependencies.includes(source.id)) return toast("That progression branch already exists");
    if (stageDependsOn(source.id, target.id)) return toast("That branch would create a progression loop");
    let updated = upsertToml(content, target.legacy ? "stage.dependency" : "stage.dependencies", [...dependencies, source.id]);
    if (!dependencies.length) {
      updated = upsertToml(updated, "stage.dependency_mode", "all");
      updated = upsertToml(updated, "stage.dependency_count", 1);
    }
    state.graphConnectFrom = "";
    try {
      await mutate(target.stagePath, updated);
      toast(`${target.name} now branches from ${source.name}`);
    } catch (error) {
      toast(`The branch was not saved. ${error.message}`);
    }
  }

  function removeGraphConnection(fromKey, toKey) {
    const source = stagePackages().find(stage => stage.key === fromKey);
    const target = stagePackages().find(stage => stage.key === toKey);
    if (!source || !target) return;
    openModal(`<h2 id="modalTitle">Remove progression branch</h2><p>Remove the branch from <strong>${escapeHtml(source.name)}</strong> into <strong>${escapeHtml(target.name)}</strong>. This changes the required stages for ${escapeHtml(target.name)}.</p><div class="modal-actions"><button type="button" data-close-modal class="ghost">Keep branch</button><button id="confirmRemoveGraphConnection" type="button" class="danger">Remove branch</button></div>`, () => {
      $("confirmRemoveGraphConnection").onclick = async () => {
        const content = state.boot.draft.files[target.stagePath] || "";
        const dependencies = dependenciesFor(content, target.legacy).filter(id => id !== source.id);
        let updated = upsertToml(content, target.legacy ? "stage.dependency" : "stage.dependencies", dependencies);
        const mode = stringValue(readTomlValue(updated, "stage.dependency_mode")) || "all";
        const count = mode === "at_least" ? Math.max(1, Math.min(dependencies.length || 1,
          Number(readTomlValue(updated, "stage.dependency_count") || 1))) : 1;
        updated = upsertToml(updated, "stage.dependency_mode", dependencies.length ? mode : "all");
        updated = upsertToml(updated, "stage.dependency_count", count);
        try {
          await mutate(target.stagePath, updated);
          closeModal();
          toast(`Branch removed from ${source.name} to ${target.name}`);
        } catch (error) {
          toast(`The branch was not removed. ${error.message}`);
        }
      };
    });
  }

  function fitGraphViewport() {
    const viewport = $("graphViewport");
    const graph = $("graph");
    if (!viewport || !graph) return;
    const width = Math.max(1, Number(graph.dataset.baseWidth) || 1);
    const height = Math.max(1, Number(graph.dataset.baseHeight) || 1);
    const scale = Math.min(1, (viewport.clientWidth - 24) / width, (viewport.clientHeight - 24) / height);
    setGraphZoom(scale);
    viewport.scrollLeft = 0;
    viewport.scrollTop = 0;
  }

  function setGraphZoom(value) {
    state.graphZoom = Math.max(0.2, Math.min(1.5, Math.round(value * 20) / 20));
    applyGraphScale();
    if ($("graphZoomValue")) $("graphZoomValue").textContent = `${Math.round(state.graphZoom * 100)}%`;
  }

  function applyGraphScale() {
    const graph = $("graph");
    if (!graph) return;
    const width = Math.max(1, Number(graph.dataset.baseWidth) || 1);
    const height = Math.max(1, Number(graph.dataset.baseHeight) || 1);
    graph.setAttribute("viewBox", `0 0 ${width} ${height}`);
    graph.setAttribute("width", Math.ceil(width * state.graphZoom));
    graph.setAttribute("height", Math.ceil(height * state.graphZoom));
  }

  function changeGraphZoom(amount) {
    setGraphZoom(state.graphZoom + amount);
  }

  function bindGraphViewport() {
    const viewport = $("graphViewport");
    if (!viewport) return;
    viewport.addEventListener("wheel", event => {
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      const bounds = viewport.getBoundingClientRect();
      const localX = event.clientX - bounds.left + viewport.scrollLeft;
      const localY = event.clientY - bounds.top + viewport.scrollTop;
      const oldZoom = state.graphZoom;
      setGraphZoom(oldZoom + (event.deltaY < 0 ? 0.1 : -0.1));
      const ratio = state.graphZoom / oldZoom;
      viewport.scrollLeft = localX * ratio - (event.clientX - bounds.left);
      viewport.scrollTop = localY * ratio - (event.clientY - bounds.top);
    }, { passive: false });
    viewport.onpointerdown = event => {
      if (event.button !== 0 || event.target.closest?.("[data-graph-stage], [data-graph-from]")) return;
      event.preventDefault();
      const startX = event.clientX;
      const startY = event.clientY;
      const left = viewport.scrollLeft;
      const top = viewport.scrollTop;
      viewport.setPointerCapture(event.pointerId);
      viewport.onpointermove = move => {
        viewport.scrollLeft = left - (move.clientX - startX);
        viewport.scrollTop = top - (move.clientY - startY);
      };
      viewport.onpointerup = viewport.onpointercancel = finish => {
        viewport.onpointermove = null;
        viewport.onpointerup = null;
        viewport.onpointercancel = null;
        if (viewport.hasPointerCapture(finish.pointerId)) viewport.releasePointerCapture(finish.pointerId);
      };
    };
  }

  function graphDependencyLabel(node) {
    if (!node.dependencies.length) return "Beginner path";
    if (node.dependencies.length === 1) return `From ${stageName(node.dependencies[0])}`;
    if (node.mode === "any") return `Any of ${node.dependencies.length} paths`;
    if (node.mode === "at_least") return `${Math.min(node.count, node.dependencies.length)} of ${node.dependencies.length} paths`;
    return `Joins all ${node.dependencies.length} paths`;
  }

  async function autoArrangeGraph() {
    const selectedCategory = $("graphCategory").value;
    const search = $("graphSearch").value;
    $("graphCategory").value = "";
    $("graphSearch").value = "";
    renderGraph();
    const positions = qa("[data-graph-stage]", $("graph")).map(node => ({
      key: node.dataset.graphStage,
      x: Number(node.dataset.mapX),
      y: Number(node.dataset.mapY)
    }));
    try {
      for (const position of positions) {
        const stage = stagePackages().find(value => value.key === position.key);
        if (!stage) continue;
        const content = state.boot.draft.files[stage.stagePath] || "";
        const updated = upsertToml(upsertToml(content, "display.x", position.x), "display.y", position.y);
        if (updated !== content) await mutate(stage.stagePath, updated);
      }
    } finally {
      $("graphCategory").value = selectedCategory;
      $("graphSearch").value = search;
      renderGraph();
      fitGraphViewport();
    }
    toast("Every player UI position was arranged and saved to the draft");
  }

  async function resetGraphLayout() {
    const stages = stagePackages().filter(stage => !stage.archived);
    for (const stage of stages) {
      const content = state.boot.draft.files[stage.stagePath] || "";
      let updated = removeTomlValue(content, "display.x");
      updated = removeTomlValue(updated, "display.y");
      if (updated !== content) await mutate(stage.stagePath, updated);
    }
    renderGraph();
    fitGraphViewport();
    toast("Every stage now follows the automatic player UI layout");
  }

  function dragGraphNode(event, node) {
    if (event.button !== 0) return;
    event.preventDefault();
    const startX = event.clientX;
    const startY = event.clientY;
    const left = Number(node.getAttribute("x"));
    const top = Number(node.getAttribute("y"));
    const scale = state.graphZoom || 1;
    node.dataset.dragged = "false";
    node.setPointerCapture(event.pointerId);
    node.onpointermove = move => {
      if (Math.abs(move.clientX - startX) >= 3 || Math.abs(move.clientY - startY) >= 3) {
        node.dataset.dragged = "true";
      }
      node.setAttribute("x", Math.max(0, Math.round(left + (move.clientX - startX) / scale)));
      node.setAttribute("y", Math.max(0, Math.round(top + (move.clientY - startY) / scale)));
      updateGraphConnections();
    };
    node.onpointerup = async move => {
      node.onpointermove = null;
      node.onpointerup = null;
      node.onpointercancel = null;
      if (node.hasPointerCapture(move.pointerId)) node.releasePointerCapture(move.pointerId);
      if (Math.abs(move.clientX - startX) < 3 && Math.abs(move.clientY - startY) < 3) return;
      const stage = stagePackages().find(value => value.key === node.dataset.graphStage);
      if (!stage) return;
      let content = state.boot.draft.files[stage.stagePath] || "";
      const mapX = Math.max(0, Math.round((Number(node.getAttribute("x")) - GRAPH_PREVIEW_LEFT) / GRAPH_PREVIEW_X));
      const mapY = Math.max(0, Math.round((Number(node.getAttribute("y")) - GRAPH_PREVIEW_TOP) / GRAPH_PREVIEW_Y));
      content = upsertToml(content, "display.x", mapX);
      content = upsertToml(content, "display.y", mapY);
      try {
        await mutate(stage.stagePath, content);
        toast(`Player UI position saved at X ${mapX} and Y ${mapY}`);
      } catch (error) {
        toast(`The player UI position was not saved. ${error.message}`);
      }
    };
    node.onpointercancel = cancel => {
      node.onpointermove = null;
      node.onpointerup = null;
      node.onpointercancel = null;
      node.setAttribute("x", left);
      node.setAttribute("y", top);
      updateGraphConnections();
      if (node.hasPointerCapture(cancel.pointerId)) node.releasePointerCapture(cancel.pointerId);
    };
  }

  function updateSource(path) {
    state.path = path;
    state.sourceDirty = false;
    $("source").value = state.boot.draft.files[path] || "";
    $("dirty").textContent = "";
    renderSourceTabs();
  }

  function renderSourceTabs() {
    const stage = selectedStage();
    const paths = state.stageKey === "__settings__" ? ["progressivestages.toml"] : stage ? [...new Set([stage.stagePath, stage.rulesPath, stage.progressionPath])].filter(path => state.boot.draft.files[path] != null) : [];
    $("sourceFiles").innerHTML = paths.map(path => `<button class="${path === state.path ? "active" : ""}" data-source-path="${escapeHtml(path)}">${escapeHtml(path.split("/").pop())}</button>`).join("");
    qa("[data-source-path]", $("sourceFiles")).forEach(button => button.onclick = () => {
      if (!discardSourceChanges()) return;
      updateSource(button.dataset.sourcePath);
    });
  }

  function switchView(view) {
    state.view = view;
    qa(".tabs button").forEach(button => button.classList.toggle("active", button.dataset.view === view));
    for (const name of ["form", "source", "graph"]) $(name + "View").classList.toggle("hidden", name !== view);
    if (view === "graph") {
      renderGraph();
      requestAnimationFrame(fitGraphViewport);
    }
    if (view === "source") renderSourceTabs();
  }

  async function mutate(path, content) {
    const result = await api({ action: "mutate", path, content, revision: state.boot.draft.revision });
    state.boot.draft.revision = result.revision;
    state.boot.draft.diff = result.diff;
    state.boot.draft.canUndo = result.canUndo;
    state.boot.draft.canRedo = result.canRedo;
    state.boot.draft.files[path] = content;
    if (state.path === path && !state.sourceDirty) $("source").value = content;
    renderAll();
  }

  async function createStage(event) {
    event.preventDefault();
    const identity = stageIdentity($("newStageName").value, $("newStageNamespace").value);
    if (!identity.name || !identity.path) return toast("Enter a stage name with at least one letter or number");
    try {
      const result = await api({ action: "scaffold", stage: identity.id, revision: state.boot.draft.revision });
      state.boot.draft.revision = result.revision;
      state.boot.draft.files = result.files;
      state.boot.draft.diff = result.diff;
      const created = stagePackages().find(stage => stage.id === identity.id);
      if (!created) throw new Error("The stage package was created but could not be selected");
      let content = state.boot.draft.files[created.stagePath];
      content = upsertToml(content, "stage.display_name", identity.name);
      content = upsertToml(content, "stage.icon", "minecraft:stone");
      await mutate(created.stagePath, content);
      $("newStagePanel").classList.add("hidden");
      $("newStageName").value = "";
      selectStage(created.key);
      toast(`${identity.name} was created as ${identity.id}. Add details and rules when you are ready.`);
    } catch (error) { toast(error.message); }
  }

  function updateNewStagePreview() {
    const identity = stageIdentity($("newStageName").value || "new stage", $("newStageNamespace").value);
    $("newStagePreview").textContent = `Saved as ${identity.id}`;
  }

  function currentFolder() {
    return selectedStage()?.folder || "";
  }

  async function replaceDraft(result, preferredId) {
    state.boot.draft = result;
    renderAll();
    const stage = stagePackages().find(value => value.id === preferredId) || stagePackages().find(value => !value.archived) || stagePackages()[0];
    if (stage) selectStage(stage.key);
  }

  function askForStageIdentity(titleText, defaultName, submitLabel, handler) {
    openModal(`<h2 id="modalTitle">${escapeHtml(titleText)}</h2><p>Choose any stage namespace. For example, namespace wizard can contain wizard:wizard and wizard:warlock. You may also type a complete ID in the stage name field.</p><form id="identityForm"><div class="modal-grid"><label>Stage name or complete ID<input id="identityName" value="${escapeHtml(defaultName)}" required></label><label>Stage namespace<input id="identityNamespace" value="${escapeHtml(selectedStage()?.id.split(":")[0] || "pack")}" required></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">${escapeHtml(submitLabel)}</button></div></form>`, () => {
      $("identityForm").onsubmit = async event => {
        event.preventDefault();
        const identity = stageIdentity($("identityName").value, $("identityNamespace").value);
        if (!identity.path) return toast("Enter a valid stage name");
        try { await handler(identity.id); closeModal(); } catch (error) { toast(error.message); }
      };
    });
  }

  function duplicateStage() {
    const stage = selectedStage();
    if (!stage?.folder) return toast("Select a three file stage package first");
    askForStageIdentity("Duplicate stage", `${stage.name} Copy`, "Duplicate", async id => {
      const result = await api({ action: "duplicate_stage", source: stage.folder, stage: id, revision: state.boot.draft.revision });
      await replaceDraft(result, id);
      toast("Stage duplicated in the draft");
    });
  }

  function renameStage() {
    const stage = selectedStage();
    if (!stage?.folder) return toast("Select a three file stage package first");
    askForStageIdentity("Rename stage", stage.name, "Rename", async id => {
      const result = await api({ action: "rename_stage", folder: stage.folder, stage: id, revision: state.boot.draft.revision });
      await replaceDraft(result, id);
      toast("Stage and references renamed in the draft");
    });
  }

  async function deleteStage() {
    const stage = selectedStage();
    if (!stage?.folder) return toast("Select a three file stage package first");
    if (!confirm(`Delete ${stage.name} from this draft`)) return;
    const result = await api({ action: "delete_stage", folder: stage.folder, revision: state.boot.draft.revision });
    state.stageKey = "";
    await replaceDraft(result, "");
    toast("Stage deleted from the draft");
  }

  function moveStage() {
    const stage = selectedStage();
    if (!stage?.folder) return toast("Select a three file stage package first");
    openModal(`<h2 id="modalTitle">Move stage</h2><p>This only changes how the files are organized. The stage ID stays the same.</p><form id="moveForm"><label>Folder below stages<input id="moveDestination" value="categories/${escapeHtml(slug(stage.name))}"></label><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Move</button></div></form>`, () => {
      $("moveForm").onsubmit = async event => {
        event.preventDefault();
        try {
          const result = await api({ action: "move_stage", folder: stage.folder, destination: $("moveDestination").value.trim(), revision: state.boot.draft.revision });
          await replaceDraft(result, stage.id);
          closeModal();
          toast("Stage moved in the draft");
        } catch (error) { toast(error.message); }
      };
    });
  }

  async function archiveStage() {
    const stage = selectedStage();
    if (!stage?.folder) return toast("Select a three file stage package first");
    const restoring = stage.archived;
    if (!confirm(restoring ? `Restore ${stage.name}` : `Archive ${stage.name}`)) return;
    const result = await api({ action: restoring ? "restore_stage" : "archive_stage", folder: stage.folder, revision: state.boot.draft.revision });
    await replaceDraft(result, stage.id);
    toast(restoring ? "Stage restored in the draft" : "Stage archived in the draft");
  }

  async function exportStage() {
    const stage = selectedStage();
    if (!stage?.folder) return toast("Select a three file stage package first");
    const result = await api({ action: "export_stage", folder: stage.folder });
    const blob = new Blob([JSON.stringify({ folder: result.folder, files: result.files }, null, 2)], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${slug(stage.name)}.progressivestages.json`;
    link.click();
    URL.revokeObjectURL(link.href);
    toast("Stage package exported");
  }

  function importStage() {
    openModal(`<h2 id="modalTitle">Import a stage</h2><p>Paste the text from a ProgressiveStages export and choose its folder.</p><form id="importForm"><div class="modal-grid"><label class="wide-field">Exported package<textarea id="importValue" rows="10" required></textarea></label><label class="wide-field">Folder below stages<input id="importDestination" value="imported_stage" required></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Import</button></div></form>`, () => {
      $("importForm").onsubmit = async event => {
        event.preventDefault();
        try {
          const value = JSON.parse($("importValue").value);
          const result = await api({ action: "import_stage", destination: $("importDestination").value.trim(), files: value.files || value, revision: state.boot.draft.revision });
          state.boot.draft = result;
          renderAll();
          closeModal();
          toast("Stage package imported into the draft");
        } catch (error) { toast(error.message || "The imported package is invalid"); }
      };
    });
  }

  function openCatalogPicker(options) {
    openModal(`<h2 id="modalTitle">${escapeHtml(options.title)}</h2><p>Results come from the registries on the running server.</p><div class="modal-grid"><label>Selection method<select id="pickerMode"><option value="id">Exact ID</option><option value="mod">Whole mod</option><option value="tag">Tag</option><option value="name">Name match</option></select></label><label>Search<input id="pickerSearch" autofocus></label><div id="pickerResults" class="picker-results"></div></div><div class="modal-actions"><button data-close-modal class="ghost">Cancel</button></div>`, () => {
      $("pickerMode").value = options.mode || "id";
      const search = debounce(async () => {
        const result = await api({ action: "catalog", catalog: `progressivestages:${options.catalog}`, field: "picker", mode: $("pickerMode").value, text: $("pickerSearch").value, pageSize: 50, catalogRevision: state.boot.catalog.revision });
        $("pickerResults").innerHTML = result.entries.map(entry => `<button data-picker-value="${escapeHtml(entry.key)}"><strong>${escapeHtml(entry.label)}</strong><small>${escapeHtml(entry.key)}</small></button>`).join("") || `<span class="muted">No matches.</span>`;
        qa("[data-picker-value]", $("pickerResults")).forEach(button => button.onclick = () => { options.onPick(normalizeSelector($("pickerMode").value, button.dataset.pickerValue, $("pickerSearch").value)); closeModal(); });
      }, 180);
      $("pickerSearch").oninput = search;
      $("pickerMode").onchange = search;
      search();
    });
  }

  function openModal(html, setup) {
    if (state.modalCleanup) state.modalCleanup();
    $("modal").innerHTML = html;
    $("modalBackdrop").classList.remove("hidden");
    qa("[data-close-modal]", $("modal")).forEach(button => button.onclick = closeModal);
    $("modalBackdrop").onclick = event => { if (event.target === $("modalBackdrop")) closeModal(); };
    document.addEventListener("keydown", escapeModal);
    state.modalCleanup = () => document.removeEventListener("keydown", escapeModal);
    setup?.($("modal"));
    q("input,select,textarea", $("modal"))?.focus();
  }

  function escapeModal(event) {
    if (event.key === "Escape") closeModal();
  }

  function closeModal() {
    state.modalCleanup?.();
    state.modalCleanup = null;
    $("modalBackdrop").classList.add("hidden");
    $("modal").innerHTML = "";
  }

  async function loadModNames(datalistId) {
    try {
      const result = await api({ action: "catalog", catalog: "progressivestages:mods", field: "mods", mode: "id", text: "", pageSize: 100, catalogRevision: state.boot.catalog.revision });
      $(datalistId).innerHTML = result.entries.map(entry => `<option value="${escapeHtml(entry.modId || entry.key)}">${escapeHtml(entry.label)}</option>`).join("");
    } catch (error) { toast("Mod filter could not be loaded. " + error.message); }
  }

  function bindConditionAutocomplete(selectId, inputId, datalistId) {
    const select = $(selectId);
    const input = $(inputId);
    const list = $(datalistId);
    if (!select || !input || !list) return;
    const refresh = debounce(async () => {
      const builtIn = CONDITIONS.find(([id]) => id === select.value);
      let catalog = builtIn?.[2] || "";
      if (!catalog) {
        const extension = (state.boot.extensions?.registrations || []).find(value => value.id === select.value);
        const argument = extension?.arguments?.find(value => value.catalog);
        catalog = argument?.catalog ? String(argument.catalog).replace(/^progressivestages:/, "") : "";
      }
      if (!catalog) { list.innerHTML = ""; return; }
      try {
        const result = await api({ action: "catalog", catalog: catalog.includes(":") ? catalog : `progressivestages:${catalog}`, field: "condition.target", mode: "id", text: input.value, pageSize: 40, catalogRevision: state.boot.catalog.revision });
        list.innerHTML = result.entries.map(entry => `<option value="${escapeHtml(entry.key)}">${escapeHtml(entry.label)}</option>`).join("");
      } catch (error) { list.innerHTML = ""; }
    }, 180);
    select.onchange = refresh;
    input.oninput = refresh;
    refresh();
  }

  function bindCatalogAutocomplete(inputId, datalistId, catalog) {
    const input = $(inputId);
    const list = $(datalistId);
    if (!input || !list) return;
    const refresh = debounce(async () => {
      try {
        const result = await api({ action: "catalog", catalog: `progressivestages:${catalog}`, field: inputId, mode: "id", text: input.value, pageSize: 40, catalogRevision: state.boot.catalog.revision });
        list.innerHTML = result.entries.map(entry => `<option value="${escapeHtml(stripSelectorPrefix(entry.key))}">${escapeHtml(entry.label)}</option>`).join("");
      } catch (error) { list.innerHTML = ""; }
    }, 180);
    input.oninput = refresh;
    refresh();
  }

  function showInspector(view) {
    qa(".inspector-tabs button").forEach(button => button.classList.toggle("active", button.dataset.inspector === view));
    $("helpPanel").classList.toggle("hidden", view !== "help");
    $("catalogPanel").classList.toggle("hidden", view !== "catalog");
    $("extensionsPanel").classList.toggle("hidden", view !== "extensions");
    $("conflictPanel").classList.toggle("hidden", view !== "conflicts");
  }

  function renderExtensions() {
    const registrations = state.boot?.extensions?.registrations || [];
    $("extensions").innerHTML = registrations.length ? registrations.map(extension => `<article class="extension-card"><strong>${escapeHtml(extension.title)}</strong><small>${escapeHtml(String(extension.kind).toLowerCase())} · ${escapeHtml(extension.id)}</small><p>${escapeHtml(extension.description || "No description supplied")}</p>${(extension.arguments || []).map(argument => `<label>${escapeHtml(argument.name)}<small>${escapeHtml(argument.type)}${argument.required ? " · required" : ""}</small><input value="${escapeHtml(formatDefault(argument.defaultValue))}" disabled></label>`).join("")}<button data-extension="${escapeHtml(extension.id)}" class="wide ghost">Copy registered ID</button></article>`).join("") : `<p class="muted">No Java or KubeJS extensions registered for this server revision.</p>`;
    qa("[data-extension]", $("extensions")).forEach(button => button.onclick = () => { navigator.clipboard?.writeText(button.dataset.extension); toast("Copied " + button.dataset.extension); });
  }

  async function searchCatalog() {
    const catalog = $("catalogSearch").dataset.catalog || "progressivestages:items";
    const result = await api({ action: "catalog", catalog, field: "inspector", mode: $("prefix").value, text: $("catalogSearch").value, pageSize: 30, catalogRevision: state.boot.catalog.revision });
    $("catalogResults").innerHTML = result.entries.map(entry => `<button class="catalog-entry" data-key="${escapeHtml(entry.key)}"><strong>${escapeHtml(entry.label)}</strong><small>${escapeHtml(entry.key)} · ${escapeHtml(entry.sourceType)}</small></button>`).join("") || `<p class="muted">No matches.</p>`;
    qa(".catalog-entry", $("catalogResults")).forEach(entry => entry.onclick = () => { navigator.clipboard?.writeText(entry.dataset.key); toast("Copied " + entry.dataset.key); });
  }

  function analyzeConflicts() {
    const entries = [];
    for (const [path, content] of Object.entries(state.boot?.draft.files || {})) {
      for (const match of content.matchAll(/([^"\s,\]]+)\|priority=(-?\d+)/g)) entries.push({ path, selector: match[1], priority: Number(match[2]) });
      for (const rule of ruleModels(content).filter(value => value.generic)) entries.push({ path, selector: rule.selector, priority: rule.priority });
    }
    const grouped = entries.reduce((output, entry) => ((output[entry.selector] ??= []).push(entry), output), {});
    const conflicts = Object.entries(grouped).filter(([, values]) => new Set(values.map(entry => entry.priority)).size > 1);
    $("conflicts").innerHTML = conflicts.length ? conflicts.map(([selector, values]) => `<strong>${escapeHtml(selector)}</strong><br><small>${values.map(value => `${value.priority} in ${escapeHtml(value.path)}`).join("<br>")}</small>`).join("<hr>") : "No unresolved explicit priority conflicts in this draft.";
  }

  async function simulateDraft() {
    const stage = selectedStage();
    openModal(`<h2 id="modalTitle">Simulate a rule decision</h2><p>Test a category and target against the candidate draft before applying it.</p><form id="simulateForm"><div class="modal-grid"><label>Category<select id="simulateCategory">${categoryOptions("items")}</select></label><label>Action<input id="simulateAction" value="use"></label><label class="wide-field">Target ID<input id="simulateTarget" value="minecraft:diamond"></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Simulate</button></div></form>`, () => {
      $("simulateForm").onsubmit = async event => {
        event.preventDefault();
        try {
          const category = `${$("simulateCategory").value}.${$("simulateAction").value}`;
          const target = $("simulateTarget").value;
          const result = await api({ action: "simulate", category, target });
          closeModal();
          openDrawer(validationHtml(result.validation) + `<h3>Candidate simulation</h3><p>${escapeHtml(result.explanation)}</p><p><strong>Context</strong> ${escapeHtml(category)}<br><strong>Target</strong> ${escapeHtml(target || stage?.id || "")}</p>`);
        } catch (error) { toast(error.message); }
      };
    });
  }

  function manageCollaborator() {
    openModal(`<h2 id="modalTitle">Manage a collaborator</h2><p>Collaborators can resume and edit this server side draft. Enter the Minecraft account UUID.</p><form id="collaboratorForm"><div class="modal-grid"><label class="wide-field">Player UUID<input id="collaboratorPlayer" placeholder="00000000-0000-0000-0000-000000000000" required></label><label class="wide-field">Access<select id="collaboratorAction"><option value="collaborator_add">Allow this player</option><option value="collaborator_remove">Remove this player</option></select></label></div><div class="modal-actions"><button type="button" data-close-modal class="ghost">Cancel</button><button type="submit" class="primary">Save access</button></div></form>`, () => {
      $("collaboratorForm").onsubmit = async event => {
        event.preventDefault();
        try {
          const action = $("collaboratorAction").value;
          const result = await api({ action, player: $("collaboratorPlayer").value.trim() });
          state.boot.draft.collaborators = result.collaborators;
          closeModal();
          toast(action === "collaborator_add" ? "Collaborator added" : "Collaborator removed");
        } catch (error) { toast(error.message); }
      };
    });
  }

  async function validate() {
    const result = await api({ action: "validate" });
    openDrawer(validationHtml(result));
    toast(result.valid ? "Everything is valid" : "Validation found errors");
  }

  function validationHtml(result) {
    return result.valid ? `<div class="validation-ok"><strong>Valid</strong><p>${result.stages} stages compiled successfully.</p></div>` : `<div class="validation-error"><strong>Apply blocked</strong>${result.errors.map(error => `<p>${escapeHtml(error)}</p>`).join("")}</div>`;
  }

  async function review() {
    const review = await api({ action: "review" });
    const diff = review.diff.map(entry => `<div class="diff"><strong class="change-${entry.change}">${entry.change}</strong><span>${escapeHtml(entry.path)}</span><small>${entry.beforeBytes} → ${entry.afterBytes} bytes</small></div>`).join("");
    const chatNotice = review.diff.length
      ? `<p class="muted">After a successful apply, online operators receive this change list in Minecraft chat. Added files are green, modified files are yellow, and removed files are red.</p>`
      : `<p class="muted">Nothing changed, so no operator chat message will be sent.</p>`;
    openDrawer(`${validationHtml(review.validation)}<h3>Stage file changes</h3>${diff || "<p>No changes.</p>"}${chatNotice}<div id="applyStatus" aria-live="polite"></div><div class="source-actions"><button id="confirmApply" class="primary" ${review.validation.valid ? "" : "disabled"}>Confirm and apply to server</button></div>`);
    q("#confirmApply")?.addEventListener("click", () => apply());
  }

  async function apply() {
    const button = $("confirmApply");
    const status = $("applyStatus");
    if (button) {
      button.disabled = true;
      button.textContent = "Applying to the server";
    }
    if (status) status.innerHTML = `<div class="validation-ok"><strong>Applying</strong><p>The server is validating, reloading, and synchronizing every connected player. Large modpacks can take a little while.</p></div>`;
    try {
      const result = await api({ action: "apply", confirmed: true });
      if (!result.success) throw new Error(result.explanation || "The server rejected the draft");
      state.boot.session.baseConfigurationRevision = result.configurationRevision;
      state.boot.draft.diff = [];
      state.boot.draft.canUndo = false;
      state.boot.draft.canRedo = false;
      renderAll();
      toast("Applied and synchronized to every client");
      const rollback = result.transactionId ? `<button id="rollback">Rollback this transaction</button>` : "";
      const appliedDiff = (result.diff || []).map(entry => `<div class="diff"><strong class="change-${entry.change}">${entry.change}</strong><span>${escapeHtml(entry.path)}</span><small>${entry.beforeBytes} → ${entry.afterBytes} bytes</small></div>`).join("");
      const operatorNotice = (result.diff || []).length
        ? "Online operators received the color coded change list in Minecraft chat."
        : "No file content changed, so no operator chat message was sent.";
      openDrawer(`<div class="validation-ok"><strong>Applied and synchronized</strong><p>Server revision ${result.configurationRevision}. ${operatorNotice}</p>${rollback}</div><h3>Applied file changes</h3>${appliedDiff || "<p>No file content changed. The live configuration was already current.</p>"}`);
      if (result.transactionId) {
        $("rollback").onclick = async () => {
          if (!confirm("Rollback this applied transaction")) return;
          try {
            const back = await api({ action: "rollback", transaction: result.transactionId, confirmed: true });
            toast(back.explanation);
          } catch (error) { toast(error.message); }
        };
      }
    } catch (error) {
      if (status) status.innerHTML = `<div class="validation-error"><strong>Apply failed</strong><p>${escapeHtml(error.message)}</p><p>Your draft is still safe. Correct the reported problem and try again.</p></div>`;
      if (button) {
        button.disabled = false;
        button.textContent = "Confirm and apply to server";
      }
      toast(`Apply failed. ${error.message}`);
    }
  }

  function openDrawer(html) {
    $("drawerBody").innerHTML = html;
    $("drawer").classList.add("open");
  }

  function extractArrayBlocks(text, table) {
    const lines = text.match(/.*(?:\r?\n|$)/g) || [];
    const offsets = [];
    let offset = 0;
    for (const line of lines) { offsets.push(offset); offset += line.length; }
    const blocks = [];
    let active = null;
    for (let index = 0; index < lines.length; index++) {
      const match = lines[index].match(/^\s*\[\[([^\]]+)\]\]\s*(?:#.*)?$/);
      const single = lines[index].match(/^\s*\[([^\]]+)\]\s*(?:#.*)?$/);
      const header = match?.[1] || single?.[1];
      if (active && header && header !== table && !header.startsWith(table + ".")) {
        active.end = offsets[index];
        active.text = text.slice(active.start, active.end);
        blocks.push(active);
        active = null;
      }
      if (match?.[1] === table) {
        if (active) {
          active.end = offsets[index];
          active.text = text.slice(active.start, active.end);
          blocks.push(active);
        }
        active = { start: offsets[index], end: text.length, text: "" };
      }
    }
    if (active) { active.end = text.length; active.text = text.slice(active.start); blocks.push(active); }
    return blocks;
  }

  function replaceArrayBlocks(text, table, blocks) {
    const found = extractArrayBlocks(text, table);
    if (!found.length) return blocks.length ? appendTomlBlock(text, blocks.join("\n")) : text;
    const insertion = found[0].start;
    let stripped = text;
    for (const block of [...found].reverse()) stripped = stripped.slice(0, block.start) + stripped.slice(block.end);
    const joined = blocks.map(block => block.trim()).join("\n\n") + "\n\n";
    return stripped.slice(0, insertion) + joined + stripped.slice(insertion).replace(/^\s+/, "");
  }

  function readBlockValue(text, key) {
    const lines = text.split(/\r?\n/);
    const pattern = new RegExp(`^\\s*${escapeRegex(key)}\\s*=\\s*(.*)$`);
    for (let index = 1; index < lines.length; index++) {
      if (/^\s*\[/.test(lines[index])) break;
      const match = lines[index].match(pattern);
      if (!match) continue;
      let value = match[1].trim();
      while (tomlBalance(value) > 0 && index + 1 < lines.length) value += "\n" + lines[++index].trim();
      return value;
    }
    return "";
  }

  function readNestedBlockValue(text, table, key) {
    const start = text.indexOf(`[[${table}]]`);
    if (start < 0) return "";
    return readBlockValue(text.slice(start), key);
  }

  function readTomlValue(text, path) {
    const bits = path.split(".");
    const key = bits.pop();
    const section = bits.join(".");
    const lines = text.split(/\r?\n/);
    let active = "";
    for (let index = 0; index < lines.length; index++) {
      const header = lines[index].match(/^\s*\[([^\]]+)\]/);
      if (header) { active = header[1]; continue; }
      if (active !== section) continue;
      const match = lines[index].match(new RegExp(`^\\s*${escapeRegex(key)}\\s*=\\s*(.*)$`));
      if (!match) continue;
      let value = match[1].trim();
      while (tomlBalance(value) > 0 && index + 1 < lines.length) value += "\n" + lines[++index].trim();
      return value;
    }
    return "";
  }

  function upsertToml(text, path, value) {
    const bits = path.split(".");
    const key = bits.pop();
    const section = bits.join(".");
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
      while (balance > 0 && last + 1 < end) { last++; balance = tomlBalance(lines.slice(index, last + 1).join("\n")); }
      lines.splice(index, last - index + 1, `${key} = ${encoded}`);
      return lines.join("\n");
    }
    lines.splice(end, 0, `${key} = ${encoded}`);
    return lines.join("\n");
  }

  function removeTomlValue(text, path) {
    const bits = path.split(".");
    const key = bits.pop();
    const section = bits.join(".");
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

  function removeTomlSection(text, section) {
    const lines = text.split(/\r?\n/);
    let start = -1;
    let end = lines.length;
    for (let index = 0; index < lines.length; index++) {
      const match = lines[index].match(/^\s*\[([^\]]+)\]\s*(?:#.*)?$/);
      if (!match) continue;
      if (start >= 0) { end = index; break; }
      if (match[1] === section) start = index;
    }
    if (start < 0) return text;
    lines.splice(start, end - start);
    return lines.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd() + "\n";
  }

  function encodeToml(value) {
    if (value && typeof value === "object" && Object.hasOwn(value, "$toml")) return value.$toml;
    if (typeof value === "boolean" || typeof value === "number") return String(value);
    if (Array.isArray(value)) return `[${value.map(encodeToml).join(", ")}]`;
    if (value && typeof value === "object") return `{ ${Object.entries(value).map(([key, item]) => `${tomlKey(key)} = ${encodeToml(item)}`).join(", ")} }`;
    return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n")}"`;
  }

  function conditionToml(type, target, count) {
    const fields = { type };
    if (target) fields.id = target;
    if (Number(count || 1) !== 1) fields.count = Number(count);
    return encodeToml(fields);
  }

  function parseSimpleArray(raw) {
    if (!raw || !raw.trim().startsWith("[") || !raw.trim().endsWith("]")) return null;
    const values = [];
    for (const match of raw.matchAll(/"((?:\\.|[^"\\])*)"|'([^']*)'|(-?\d+(?:\.\d+)?)|(true|false)/g)) {
      const item = match[1] ?? match[2] ?? match[3] ?? match[4];
      values.push(item.replace?.(/\\"/g, '"').replace?.(/\\\\/g, "\\") ?? item);
    }
    return values;
  }

  function inlineObjectValue(raw, key) {
    if (!raw) return "";
    const match = raw.match(new RegExp(`(?:^|[,\\s{])${escapeRegex(key)}\\s*=\\s*(\"(?:\\\\.|[^\"])*\"|'[^']*'|-?\\d+(?:\\.\\d+)?|true|false)`));
    return match ? stringValue(match[1]) : "";
  }

  function stringValue(raw) {
    const value = String(raw ?? "").trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) return value.slice(1, -1).replace(/\\n/g, "\n").replace(/\\"/g, '"').replace(/\\\\/g, "\\");
    return value;
  }

  function booleanValue(raw) {
    return String(raw).trim().toLowerCase() === "true";
  }

  function lineValues(value) {
    return String(value).split(/\r?\n|,/).map(item => item.trim()).filter(Boolean);
  }

  function tomlBalance(value) {
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

  function appendTomlBlock(text, block) {
    return text.replace(/\s*$/, "") + "\n\n" + block.trim() + "\n";
  }

  function dependenciesFor(text, legacy) {
    const raw = readTomlValue(text, legacy ? "stage.dependency" : "stage.dependencies") || readTomlValue(text, "stage.dependency");
    return parseSimpleArray(raw) || (stringValue(raw) ? [stringValue(raw)] : []);
  }

  function normalizeSelector(mode, key, search) {
    const clean = String(key).replace(/^(id|mod|tag|name):/, "");
    if (mode === "name") return `name:${String(search || clean).trim()}`;
    return `${mode}:${clean}`;
  }

  function selectorMode(selector) {
    const match = String(selector || "").match(/^(id|mod|tag|name):/);
    return match ? match[1] : "id";
  }

  function stripSelectorPrefix(value) {
    return String(value).replace(/^id:/, "");
  }

  function categoryOptions(selected) {
    return Object.entries(CATEGORIES).map(([id, value]) => `<option value="${id}" ${id === selected ? "selected" : ""}>${escapeHtml(value.label)}</option>`).join("");
  }

  function conditionOptions(selected, omitNone = false) {
    const builtIn = CONDITIONS.filter(([id]) => !omitNone || id !== "none").map(([id, label]) => ({ id, label }));
    const known = new Set(builtIn.map(value => value.id));
    const extensions = (state.boot?.extensions?.registrations || []).filter(value => String(value.kind).toUpperCase() === "CONDITION" && !known.has(String(value.id))).map(value => ({ id: String(value.id), label: `${value.title} extension` }));
    return [...builtIn, ...extensions].map(value => `<option value="${escapeHtml(value.id)}" ${value.id === selected ? "selected" : ""}>${escapeHtml(value.label)}</option>`).join("");
  }

  function viewerOptions(selected) {
    return [["inherit", "Follow normal policy"], ["show", "Always show"], ["hide", "Hide"], ["overlay", "Show with locked overlay"]].map(([value, label]) => `<option value="${value}" ${value === selected ? "selected" : ""}>${label}</option>`).join("");
  }

  function updateActionOptions(select, category, selected) {
    select.innerHTML = (CATEGORIES[category]?.actions || ["access"]).map(action => `<option value="${escapeHtml(action)}" ${action === selected ? "selected" : ""}>${escapeHtml(ACTION_LABELS[action] || title(action))}</option>`).join("");
  }

  function effectOptions(category) {
    const structure = category === "structures";
    const subject = structure ? "structure" : (CATEGORIES[category]?.label || "target").toLowerCase();
    const values = [
      ["lock", structure ? "Deny access to structure until this stage is owned" : `Deny ${subject} until this stage is owned`],
      ["deny", structure ? "Deny access to structure while this rule is active" : `Deny ${subject} while this rule is active`],
      ["allow", structure ? "Allow access to structure while this stage is owned" : `Allow ${subject} while this stage is owned`],
      ["unlock", structure ? "Allow access to structure while this rule is active" : `Allow ${subject} while this rule is active`]
    ];
    if (["mobs", "ores"].includes(category)) values.push(["replace", "Replace the selected target"]);
    if (["recipes", "advancements", "ores"].includes(category)) values.push(["present", "Only change how the target is shown"]);
    return values;
  }

  function updateEffectOptions(select, category, selected) {
    const values = effectOptions(category);
    const choice = values.some(([value]) => value === selected) ? selected : values[0][0];
    select.innerHTML = values.map(([value, label]) => `<option value="${value}" ${value === choice ? "selected" : ""}>${escapeHtml(label)}</option>`).join("");
  }

  function updateRuleExplanation(category, action, effect, priority, condition, lifetime, exceptionPriority) {
    const output = $("ruleExplanation");
    if (!output) return;
    const subject = CATEGORIES[category]?.label || title(category);
    const actionText = ACTION_LABELS[action] || title(action);
    const result = effectOptions(category).find(([value]) => value === effect)?.[1] || effectLabel(effect);
    const ownership = ["lock", "deny"].includes(effect)
      ? "A permanent deny protects the target before this stage is owned."
      : "An allow participates after this stage is owned and can override a lower priority deny.";
    const active = condition === "none" ? "There is no extra activation condition."
      : `${conditionLabel(condition)} must match. ${conditionHelp(condition)}`;
    const lifetimeText = lifetime === "permanent" ? "The decision follows normal stage ownership."
      : `The decision uses the ${title(lifetime)} lifetime and is temporary.`;
    const exception = exceptionPriority > priority
      ? `The exception priority ${exceptionPriority} overrides this rule at priority ${priority}.`
      : `Raise the exception above priority ${priority} if it must override this rule.`;
    const structureHelp = category === "structures"
      ? "Choose Inside an assigned structure, Enter an assigned structure, Leave an assigned structure, or Stay inside an assigned structure under activation when location timing should control the rule."
      : "";
    output.innerHTML = `<strong>${escapeHtml(subject)}. ${escapeHtml(actionText)}.</strong> ${escapeHtml(result)}. ${escapeHtml(ownership)} ${escapeHtml(active)} ${escapeHtml(lifetimeText)} ${escapeHtml(exception)} ${escapeHtml(structureHelp)}`;
  }

  function updateConditionHelp(selectId, targetLabelId, countLabelId, helpId) {
    const select = $(selectId);
    const targetLabel = $(targetLabelId);
    const countLabel = $(countLabelId);
    const help = $(helpId);
    if (!select || !targetLabel || !countLabel || !help) return;
    const definition = CONDITIONS.find(([id]) => id === select.value);
    const needsTarget = Boolean(definition?.[2]);
    targetLabel.classList.toggle("hidden", !needsTarget && !["kubejs", "script", "weather"].includes(select.value));
    countLabel.classList.toggle("hidden", ["none", "stage_owned", "dimension", "biome", "structure", "weather", "effect", "boss_session", "combat_session", "region_session"].includes(select.value));
    help.textContent = conditionHelp(select.value);
  }

  function conditionHelp(id) {
    return CONDITIONS.find(([value]) => value === id)?.[3]
      || "Choose the target and amount required. The editor validates the result before apply.";
  }

  function conditionLabel(id) {
    return CONDITIONS.find(([value]) => value === id)?.[1] || title(id || "always active");
  }

  function effectLabel(effect) {
    return ({ lock: "Lock", deny: "Deny", allow: "Allow", unlock: "Unlock", exclude: "Exclude", replace: "Replace", present: "Display" })[effect] || title(effect);
  }

  function viewerLabel(rule) {
    if (rule.jei === "inherit" && rule.emi === "inherit") return "Normal JEI and EMI policy";
    return `JEI ${rule.jei}, EMI ${rule.emi}`;
  }

  function childId(stageId, suffix) {
    const [namespace, path = namespace] = String(stageId).includes(":") ? String(stageId).split(":", 2) : ["pack", String(stageId)];
    return `${namespace}:${path}/${suffix}`;
  }

  function slug(value) {
    return String(value || "").trim().toLowerCase().replace(/[^a-z0-9_.-]+/g, "_").replace(/^_+|_+$/g, "");
  }

  function stageIdentity(nameValue, namespaceValue) {
    const raw = String(nameValue || "").trim();
    const separator = raw.indexOf(":");
    const complete = separator > 0;
    const namespace = slug(complete ? raw.slice(0, separator) : namespaceValue) || "pack";
    const rawPath = complete ? raw.slice(separator + 1) : raw;
    const path = slug(rawPath);
    return { namespace, path, id: `${namespace}:${path}`, name: complete ? title(path) : raw };
  }

  function title(value) {
    const text = String(value || "").replaceAll("_", " ").replaceAll(".", " ");
    return text ? text.charAt(0).toUpperCase() + text.slice(1) : "";
  }

  function tomlKey(value) {
    return /^[A-Za-z0-9_-]+$/.test(value) ? value : `"${String(value).replace(/"/g, '\\"')}"`;
  }

  function escapeRegex(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function formatDefault(value) {
    return value == null ? "" : Array.isArray(value) ? value.join("\n") : typeof value === "object" ? JSON.stringify(value, null, 2) : String(value);
  }

  function editorValue(raw, schema) {
    if (!raw) return formatDefault(schema.defaultValue);
    if (schema.type === "LIST") return (parseSimpleArray(raw) || [raw]).join("\n");
    return stringValue(raw);
  }

  function discardSourceChanges() {
    return !state.sourceDirty || confirm("Discard the unsaved source text");
  }

  function debounce(fn, wait) {
    let timer;
    return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), wait); };
  }

  qa(".tabs button").forEach(button => button.onclick = () => switchView(button.dataset.view));
  qa(".inspector-tabs button").forEach(button => button.onclick = () => { showInspector(button.dataset.inspector); if (button.dataset.inspector === "conflicts") analyzeConflicts(); });
  $("closeDrawer").onclick = () => $("drawer").classList.remove("open");
  $("fileSearch").oninput = renderStages;
  $("catalogSearch").oninput = debounce(searchCatalog, 220);
  $("prefix").onchange = searchCatalog;
  $("newStage").onclick = () => { $("newStagePanel").classList.toggle("hidden"); if (!$("newStagePanel").classList.contains("hidden")) $("newStageName").focus(); };
  $("cancelStage").onclick = () => $("newStagePanel").classList.add("hidden");
  $("newStagePanel").onsubmit = createStage;
  $("newStageName").oninput = updateNewStagePreview;
  $("newStageNamespace").oninput = updateNewStagePreview;
  $("duplicateStage").onclick = duplicateStage;
  $("renameStage").onclick = renameStage;
  $("moveStage").onclick = moveStage;
  $("archiveStage").onclick = () => archiveStage().catch(error => toast(error.message));
  $("exportStage").onclick = () => exportStage().catch(error => toast(error.message));
  $("importStage").onclick = importStage;
  $("deleteStage").onclick = () => deleteStage().catch(error => toast(error.message));
  $("settings").onclick = selectSettings;
  $("autoArrangeGraph").onclick = () => autoArrangeGraph().catch(error => toast(error.message));
  $("resetGraphLayout").onclick = () => resetGraphLayout().catch(error => toast(error.message));
  $("fitGraph").onclick = fitGraphViewport;
  $("connectGraphStages").onclick = beginGraphConnection;
  $("cancelGraphConnection").onclick = cancelGraphConnection;
  $("graphZoomOut").onclick = () => changeGraphZoom(-0.1);
  $("graphZoomIn").onclick = () => changeGraphZoom(0.1);
  $("graphCategory").onchange = () => { renderGraph(); requestAnimationFrame(fitGraphViewport); };
  $("graphSearch").oninput = debounce(() => { renderGraph(); requestAnimationFrame(fitGraphViewport); }, 180);
  bindGraphViewport();
  $("theme").onclick = () => document.documentElement.classList.toggle("light");
  $("validate").onclick = () => validate().catch(error => toast(error.message));
  $("review").onclick = () => review().catch(error => toast(error.message));
  $("simulateDraft").onclick = () => simulateDraft().catch(error => toast(error.message));
  $("collaborator").onclick = manageCollaborator;
  $("undo").onclick = async () => { await api({ action: "undo", revision: state.boot.draft.revision }); state.boot = await api({ action: "bootstrap" }); renderAll(); toast("Undone"); };
  $("redo").onclick = async () => { await api({ action: "redo", revision: state.boot.draft.revision }); state.boot = await api({ action: "bootstrap" }); renderAll(); toast("Redone"); };
  $("source").oninput = () => { state.sourceDirty = true; $("dirty").textContent = "Unsaved source changes"; };
  $("saveSource").onclick = async () => { await mutate(state.path, $("source").value); state.sourceDirty = false; $("dirty").textContent = "Saved to draft"; renderAll(); };
  window.addEventListener("beforeunload", event => { if (state.sourceDirty) { event.preventDefault(); event.returnValue = ""; } });
  boot();
})();
