export interface CategoryDefinition {
  label: string;
  catalog: string;
  actions: string[];
  description: string;
}

export const CATEGORIES: Record<string, CategoryDefinition> = {
  items: { label: "Items", catalog: "items", actions: ["use", "pickup", "inventory", "hotbar", "mouse_pickup", "drop"], description: "Held items, inventory content, and item actions." },
  blocks: { label: "Blocks", catalog: "blocks", actions: ["place", "break", "interact"], description: "Placement, breaking, and block interaction." },
  fluids: { label: "Fluids", catalog: "fluids", actions: ["pickup", "place", "flow", "submerge"], description: "Buckets, flow, placement, and submersion." },
  recipes: { label: "Recipes", catalog: "recipes", actions: ["craft", "automate", "display"], description: "Manual crafting, automation, and recipe viewers." },
  crops: { label: "Crops", catalog: "blocks", actions: ["plant", "grow", "bonemeal", "harvest"], description: "Planting, growth, bonemeal, and harvest." },
  dimensions: { label: "Dimensions", catalog: "dimensions", actions: ["enter", "portal", "teleport"], description: "Dimension travel and portal access." },
  enchants: { label: "Enchantments", catalog: "enchantments", actions: ["table", "anvil", "trade", "hold"], description: "Enchanting, anvils, trades, and owned enchantments." },
  entities: { label: "Mobs and entities", catalog: "entities", actions: ["attack", "interact", "mount"], description: "Combat, interaction, and riding." },
  interactions: { label: "Interactions", catalog: "items", actions: ["block_right_click", "item_on_block", "item_on_entity"], description: "Detailed item and target combinations." },
  loot: { label: "Loot", catalog: "loot_tables", actions: ["generate", "open", "drop"], description: "Chests, drops, fishing, and loot tables." },
  mobs: { label: "Mob spawning", catalog: "entities", actions: ["spawn", "replace"], description: "Spawn cancellation and replacement." },
  pets: { label: "Pets", catalog: "entities", actions: ["tame", "breed", "command", "ride"], description: "Taming, breeding, commands, and riding." },
  screens: { label: "Menus and screens", catalog: "menus", actions: ["open"], description: "Block and held item interfaces." },
  trades: { label: "Villager trades", catalog: "items", actions: ["display", "purchase"], description: "Trade visibility and purchase access." },
  professions: { label: "Villager professions", catalog: "professions", actions: ["trade"], description: "Trading by villager profession." },
  advancements: { label: "Advancements", catalog: "advancements", actions: ["display", "toast"], description: "Advancement visibility and notifications." },
  structures: { label: "Structures", catalog: "structures", actions: ["enter", "leave", "stay", "break", "place", "open_container", "item_use", "interact_block", "interact_entity"], description: "Entry and actions inside exact structure sessions." },
  regions: { label: "Regions", catalog: "dimensions", actions: ["enter", "leave", "stay", "break", "place", "explode", "spawn"], description: "Authored areas and activity inside them." },
  curios: { label: "Curios slots", catalog: "curios_slots", actions: ["equip", "retain"], description: "Equipping and retaining Curios items." },
  ores: { label: "Ore visuals and drops", catalog: "blocks", actions: ["display", "drop"], description: "Ore masking and generated drops." },
  beacon: { label: "Beacon effects", catalog: "effects", actions: ["apply"], description: "Effects selected through beacons." },
  brewing: { label: "Brewing", catalog: "potions", actions: ["brew", "take"], description: "Potion brewing and output collection." },
  abilities: { label: "Player abilities", catalog: "abilities", actions: ["use"], description: "Jumping, sprinting, swimming, climbing, and elytra." }
};

export const ACTION_LABELS: Record<string, string> = {
  use: "Use the target", pickup: "Pick up the target", inventory: "Keep in inventory",
  hotbar: "Keep in hotbar", mouse_pickup: "Move with the cursor", drop: "Drop the target",
  place: "Place the target", break: "Break the target", interact: "Interact with the target",
  flow: "Allow fluid flow", submerge: "Enter the fluid", craft: "Craft the recipe",
  automate: "Craft through automation", display: "Show in a viewer", plant: "Plant the crop",
  grow: "Let the crop grow", bonemeal: "Use bonemeal", harvest: "Harvest the crop",
  enter: "Enter the target", leave: "Leave the target", stay: "Remain inside",
  portal: "Use a portal", teleport: "Teleport there", table: "Use at an enchanting table",
  anvil: "Use in an anvil", trade: "Trade for the target", hold: "Keep the enchantment",
  attack: "Attack the entity", mount: "Mount the entity", block_right_click: "Right click a block",
  item_on_block: "Use an item on a block", item_on_entity: "Use an item on an entity",
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
  { value: "replace", label: "Replace the selected target" },
  { value: "present", label: "Only change presentation" }
];

export const NAVIGATION = [
  { id: "overview", label: "Overview", detail: "Draft health and recent work", icon: "home" },
  { id: "stages", label: "Stages", detail: "Build progression and rules", icon: "stages" },
  { id: "layout", label: "Player layout", detail: "Arrange and connect stages", icon: "layout" },
  { id: "settings", label: "Main settings", detail: "Server configuration", icon: "settings" },
  { id: "registry", label: "Registry", detail: "Search installed content", icon: "search" },
  { id: "extensions", label: "Extensions", detail: "Java and KubeJS features", icon: "extensions" }
] as const;
