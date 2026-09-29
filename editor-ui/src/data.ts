export interface CategoryDefinition {
  label: string;
  catalog: string;
  actions: string[];
  description: string;
}

export const CATEGORIES: Record<string, CategoryDefinition> = {
  items: { label: "Items", catalog: "items", actions: ["use", "pickup", "inventory", "hotbar", "drop", "access"], description: "Held items, inventory content, and item actions." },
  blocks: { label: "Blocks", catalog: "blocks", actions: ["place", "break", "interact", "access"], description: "Placement, breaking, and block interaction." },
  fluids: { label: "Fluids", catalog: "fluids", actions: ["pickup", "place", "flow", "submerge", "access"], description: "Buckets, flow, placement, and submersion." },
  recipes: { label: "Recipes", catalog: "recipes", actions: ["craft"], description: "Lock recipe output items or exact recipe IDs." },
  crops: { label: "Crops", catalog: "blocks", actions: ["plant", "grow", "bonemeal", "harvest", "access"], description: "Planting, growth, bonemeal, and harvest." },
  dimensions: { label: "Dimensions", catalog: "dimensions", actions: ["enter", "portal", "teleport", "access"], description: "Dimension travel and portal access." },
  enchants: { label: "Enchantments", catalog: "enchantments", actions: ["table", "anvil", "trade", "hold", "access"], description: "Enchanting, anvils, trades, and owned enchantments." },
  entities: { label: "Mobs and entities", catalog: "entities", actions: ["presence", "attack", "interact", "mount", "access"], description: "Per player presence, combat, interaction, and riding." },
  interactions: { label: "Inventory insertion", catalog: "items", actions: ["item_into_inventory"], description: "Check an inserted item together with its destination." },
  loot: { label: "Loot", catalog: "loot_tables", actions: ["generate"], description: "Lock loot table IDs with the static loot rules." },
  mobs: { label: "Mob spawning", catalog: "entities", actions: ["spawn", "access"], description: "Cancel matching mob spawns." },
  pets: { label: "Pets", catalog: "entities", actions: ["tame", "breed", "command", "access"], description: "Taming, breeding, and commands for pets." },
  screens: { label: "Menus and screens", catalog: "menus", actions: ["open"], description: "Block and held item interfaces." },
  trades: { label: "Villager trades", catalog: "items", actions: ["display", "purchase", "access"], description: "Trade visibility and purchase access." },
  professions: { label: "Villager professions", catalog: "professions", actions: ["trade", "access"], description: "Trading by villager profession." },
  advancements: { label: "Advancements", catalog: "advancements", actions: ["display", "toast", "access"], description: "Advancement visibility and notifications." },
  structures: { label: "Structures", catalog: "structures", actions: ["enter", "break", "place", "open_container", "item_use", "interact_block", "interact_entity", "access"], description: "Entry and player actions inside exact structure sessions." },
  regions: { label: "Regions", catalog: "dimensions", actions: [], description: "Use a region entry with a dimension, two XYZ corners, and prevention flags." },
  curios: { label: "Curios slots", catalog: "curios_slots", actions: ["equip"], description: "Lock exact Curios equipment slots." },
  ores: { label: "Ore overrides", catalog: "blocks", actions: [], description: "Use Block overrides to choose target blocks, shown blocks, and drops." },
  beacon: { label: "Beacon effects", catalog: "effects", actions: ["apply", "access"], description: "Effects selected through beacons." },
  brewing: { label: "Brewing", catalog: "potions", actions: ["brew", "take", "access"], description: "Potion brewing and output collection." },
  abilities: { label: "Player abilities", catalog: "abilities", actions: ["use", "access"], description: "Jumping, sprinting, swimming, climbing, and elytra." }
};

/** Categories that need dedicated fields and cannot be represented by a generic rule. */
export const SPECIALIZED_RULE_GUIDANCE: Record<string, { title: string; detail: string }> = {
  ores: {
    title: "Use Block overrides",
    detail: "Ore display and drop changes need target blocks, a shown block, and a dropped item. Add them in Block overrides above."
  },
  regions: {
    title: "Use a region entry in Source",
    detail: "A region needs a dimension, two XYZ corners, and prevention flags. Add a [[regions]] table in the stage Source file; a generic rule cannot describe an area."
  }
};

/** Core rule targets whose runtime checks accept only exact resource selectors. */
export const EXACT_SELECTOR_CATEGORIES = new Set(["dimensions", "structures", "abilities"]);

export interface LegacyRuleBinding {
  actions: string[];
  locked: string | Record<string, string>;
  alwaysUnlocked?: string;
  selector: "resource" | "exact" | "ability" | "slot";
}

/** Fields and actions actually read by the legacy stage compiler. */
export const LEGACY_RULE_BINDINGS: Record<string, LegacyRuleBinding> = {
  items: { actions: ["use"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  blocks: { actions: ["interact"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  fluids: { actions: ["interact"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  entities: { actions: ["presence"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  enchants: { actions: ["apply"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  crops: { actions: ["grow"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  screens: { actions: ["open"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  loot: { actions: ["generate"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  trades: { actions: ["trade"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  professions: { actions: ["trade"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  advancements: { actions: ["display"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  beacon: { actions: ["apply"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  brewing: { actions: ["take"], locked: "locked", alwaysUnlocked: "always_unlocked", selector: "resource" },
  dimensions: { actions: ["enter"], locked: "locked", selector: "exact" },
  mobs: { actions: ["spawn"], locked: "locked_spawns", selector: "resource" },
  pets: { actions: ["tame", "breed", "command"], locked: { tame: "locked_taming", breed: "locked_breeding", command: "locked_commanding" }, selector: "resource" },
  curios: { actions: ["equip"], locked: "locked_slots", selector: "slot" },
  abilities: { actions: ["use"], locked: "locked", selector: "ability" },
  recipes: { actions: ["craft"], locked: "locked", selector: "resource" }
};

export const ACTION_LABELS: Record<string, string> = {
  access: "Any action in this category",
  use: "Use the target", pickup: "Pick up the target", inventory: "Keep in inventory",
  hotbar: "Keep in hotbar", mouse_pickup: "Move with the cursor", drop: "Drop the target",
  place: "Place the target", break: "Break the target", interact: "Interact with the target",
  flow: "Allow fluid flow", submerge: "Enter the fluid", craft: "Craft the recipe",
  automate: "Craft through automation", display: "Show in a viewer", plant: "Plant the crop",
  grow: "Let the crop grow", bonemeal: "Use bonemeal", harvest: "Harvest the crop",
  enter: "Enter the target", leave: "Leave the target", stay: "Remain inside",
  portal: "Use a portal", teleport: "Teleport there", table: "Use at an enchanting table",
  anvil: "Use in an anvil", trade: "Trade for the target", hold: "Keep the enchantment",
  presence: "Have the entity nearby", attack: "Attack the entity", mount: "Mount the entity", block_right_click: "Right click a block",
  item_on_block: "Use an item on a block", item_on_entity: "Use an item on an entity", item_into_inventory: "Insert an item into an inventory",
  generate: "Generate the loot", open: "Open the target", spawn: "Spawn the entity",
  replace: "Replace the entity", tame: "Tame the pet", breed: "Breed the pet",
  command: "Command the pet", ride: "Ride the pet", purchase: "Purchase the trade",
  toast: "Show the toast", explode: "Damage with explosions", equip: "Equip the target",
  retain: "Keep the target equipped", apply: "Apply the effect", brew: "Brew the potion",
  take: "Take the brewed potion", open_container: "Open containers inside",
  item_use: "Use items inside", interact_block: "Interact with blocks inside",
  interact_entity: "Interact with entities inside"
};

export interface ConditionDefinition {
  id: string;
  label: string;
  catalog: string;
  help: string;
}

export const CONDITIONS: ConditionDefinition[] = [
  { id: "none", label: "Always active", catalog: "", help: "No extra location or event requirement." },
  { id: "stage_owned", label: "Owns another stage", catalog: "stages", help: "Active while the player owns the selected stage." },
  { id: "dimension", label: "Inside a dimension", catalog: "dimensions", help: "Follows the player into and out of the selected dimension." },
  { id: "biome", label: "Inside a biome", catalog: "biomes", help: "Active only inside the selected biome." },
  { id: "structure", label: "Inside an assigned structure", catalog: "structures", help: "Uses exact structure session context from the server." },
  { id: "weather", label: "During weather", catalog: "", help: "Use rain, thunder, or clear as the target." },
  { id: "kill", label: "Kill a mob", catalog: "entities", help: "Counts matching entity kills." },
  { id: "mine", label: "Mine a block", catalog: "blocks", help: "Counts matching blocks mined." },
  { id: "craft", label: "Craft an item", catalog: "items", help: "Counts crafted items." },
  { id: "pickup", label: "Pick up an item", catalog: "items", help: "Counts item pickups." },
  { id: "use", label: "Use an item", catalog: "items", help: "Counts item use events." },
  { id: "advancement", label: "Earn an advancement", catalog: "advancements", help: "Matches an earned advancement." },
  { id: "item_possession", label: "Own an item", catalog: "items", help: "Checks current inventory ownership." },
  { id: "effect", label: "Have an effect", catalog: "effects", help: "Checks a current status effect." },
  { id: "enter_structure", label: "Enter an assigned structure", catalog: "structures", help: "Matches exact structure entry." },
  { id: "leave_structure", label: "Leave an assigned structure", catalog: "structures", help: "Matches exact structure exit." },
  { id: "structure_time", label: "Stay inside an assigned structure", catalog: "structures", help: "Amount is seconds spent inside." },
  { id: "tame", label: "Tame a mob", catalog: "entities", help: "Counts matching tame events." },
  { id: "kill_with_item", label: "Kill with an item", catalog: "items", help: "Matches kills made with the selected item." },
  { id: "fish", label: "Catch an item", catalog: "items", help: "Counts fishing results." },
  { id: "sleep", label: "Sleep", catalog: "", help: "Counts completed sleep events." },
  { id: "ride", label: "Ride an entity", catalog: "entities", help: "Measures movement while riding." },
  { id: "level", label: "Reach an XP level", catalog: "", help: "Amount is the required experience level." },
  { id: "play_time", label: "Reach play time", catalog: "", help: "Amount is required play time." },
  { id: "death", label: "Player dies", catalog: "", help: "Matches the stage owner's death." },
  { id: "other_player_death", label: "Another player dies", catalog: "", help: "Matches another online player's death." },
  { id: "respawn", label: "Player respawns", catalog: "", help: "Matches after returning from death." },
  { id: "player_kill", label: "Defeat another player", catalog: "", help: "Matches a player combat victory." },
  { id: "damage_taken", label: "Take damage", catalog: "", help: "Amount is cumulative damage taken." },
  { id: "damage_dealt", label: "Deal damage", catalog: "", help: "Amount is cumulative damage dealt." },
  { id: "hits_taken", label: "Be hit", catalog: "", help: "Amount is the number of damaging hits." },
  { id: "hits_dealt", label: "Hit an entity", catalog: "", help: "Amount is the number of damaging hits dealt." },
  { id: "health_gained", label: "Recover health", catalog: "", help: "Amount is health recovered." },
  { id: "health_lost", label: "Lose health", catalog: "", help: "Amount is health lost." },
  { id: "no_damage_for", label: "Avoid damage for time", catalog: "", help: "Amount is milliseconds without damage." },
  { id: "current_health", label: "Reach a health value", catalog: "", help: "Amount is minimum health." },
  { id: "food", label: "Reach a food value", catalog: "", help: "Amount is minimum food." },
  { id: "altitude", label: "Reach an altitude", catalog: "", help: "Amount is the minimum Y coordinate." },
  { id: "stage_count", label: "Own a number of stages", catalog: "", help: "Amount is the number of owned stages." },
  { id: "online_team_size", label: "Have team members online", catalog: "", help: "Amount is the online team size." },
  { id: "stage_held_for", label: "Own a stage for time", catalog: "stages", help: "Amount is milliseconds the stage has been owned." },
  { id: "boss_session", label: "During a boss fight", catalog: "entities", help: "Active during the matching boss session." },
  { id: "combat_session", label: "During combat", catalog: "entities", help: "Active during a matching combat session." },
  { id: "region_session", label: "During a region session", catalog: "", help: "Active during a supplied region session." },
  { id: "kubejs", label: "KubeJS event", catalog: "events", help: "Matches a registered KubeJS event identifier." },
  { id: "script", label: "Script callback", catalog: "events", help: "Matches a registered script callback." }
];

export const EFFECTS = [
  { value: "lock", label: "Deny until this stage is owned" },
  { value: "deny", label: "Deny while this rule is active" },
  { value: "allow", label: "Allow while this stage is owned" },
  { value: "unlock", label: "Allow while this rule is active" },
  { value: "exclude", label: "Always allow this exact match" },
  { value: "replace", label: "Replace the selected target" },
  { value: "present", label: "Only change presentation" }
];

export function ruleEffects(category: string, action: string) {
  if (category === "interactions" && action === "item_into_inventory") {
    return EFFECTS.filter(effect => ["lock", "allow", "exclude"].includes(effect.value));
  }
  if (category === "recipes" && action === "craft") return EFFECTS.filter(effect => effect.value === "lock");
  return EFFECTS
    .filter(effect => !["exclude", "replace", "present"].includes(effect.value));
}

export const NAVIGATION = [
  { id: "stages", label: "Stages", icon: "stages" },
  { id: "layout", label: "Player UI", icon: "layout" },
  { id: "settings", label: "Settings", icon: "settings" },
  { id: "registry", label: "Registry", icon: "search" },
  { id: "extensions", label: "Extensions", icon: "extensions" }
] as const;
