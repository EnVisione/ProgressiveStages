package com.enviouse.progressivestages.server.loader;

/**
 * Single source of truth for the built-in 2.0 stage-file templates.
 * Used both by {@link com.enviouse.progressivestages.Progressivestages} during mod
 * construction (so the config directory is populated before first world load) and by
 * {@link StageFileLoader} during server start (so a deleted file can be regenerated).
 */
public final class DefaultStageTemplates {

    private DefaultStageTemplates() {}

    public static String stoneAge() {
        return """
            # ============================================================================
            # Stage definition for Stone Age (v2.0)
            # This is a STARTING STAGE — no dependency, granted to new players.
            # ============================================================================
            #
            # NEW IN 2.0: UNIFIED PREFIX SYSTEM
            # Every lock list accepts the same prefixes:
            #   "id:minecraft:stone"   (or just "minecraft:stone")   exact match
            #   "mod:mekanism"                                       every entry from a mod
            #   "tag:minecraft:crops"                                every entry in a tag
            #   "name:diamond"                                       case-insensitive substring
            #
            # Every category supports `locked` and `always_unlocked` (whitelist exceptions).
            # ============================================================================

            [stage]
            id = "stone_age"
            display_name = "Stone Age"
            description = "Basic survival tools and resources — the beginning of your journey"
            icon = "minecraft:stone_pickaxe"
            unlock_message = "&7&lStone Age Unlocked! &r&8Begin your journey into the unknown."

            # Starting stage: no dependency. To require another stage, add:
            #   dependency = "tutorial_complete"
            # or multiple:
            #   dependency = ["tutorial_complete", "spawn_visit"]

            [items]
            locked = []
            always_unlocked = []

            [blocks]
            locked = []
            always_unlocked = []

            [dimensions]
            locked = []

            # See diamond_age.toml for a complete 2.0 category reference (crops, loot,
            # enchants, pets, screens, mobs, regions, structures, etc.).
            """;
    }

    public static String ironAge() {
        return """
            # ============================================================================
            # Stage definition for Iron Age (v2.0)
            # Requires stone_age to be unlocked first.
            # ============================================================================

            [stage]
            id = "iron_age"
            display_name = "Iron Age"
            description = "Iron tools, armor, and basic machinery — industrialization begins"
            icon = "minecraft:iron_pickaxe"
            unlock_message = "&6&lIron Age Unlocked! &r&7You can now use iron equipment and basic machines."
            dependency = "stone_age"

            [items]
            locked = [
                "minecraft:iron_ingot",
                "minecraft:iron_block",
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:raw_iron",
                "minecraft:raw_iron_block",
                "minecraft:iron_pickaxe",
                "minecraft:iron_sword",
                "minecraft:iron_axe",
                "minecraft:iron_shovel",
                "minecraft:iron_hoe",
                "minecraft:iron_helmet",
                "minecraft:iron_chestplate",
                "minecraft:iron_leggings",
                "minecraft:iron_boots",
                "minecraft:shield",
                "minecraft:bucket",
                "minecraft:shears",
                "minecraft:flint_and_steel",
                "minecraft:compass",
                "minecraft:clock",
                "minecraft:minecart",
                "minecraft:rail",
                "minecraft:powered_rail",
                "minecraft:detector_rail",
                "minecraft:activator_rail"
            ]
            always_unlocked = []

            [blocks]
            locked = [
                "minecraft:iron_block",
                "minecraft:iron_door",
                "minecraft:iron_trapdoor",
                "minecraft:iron_bars",
                "minecraft:hopper",
                "minecraft:blast_furnace",
                "minecraft:smithing_table"
            ]
            always_unlocked = []

            [recipes]
            locked_ids = []
            locked_items = []
            """;
    }

    public static String diamondAge() {
        return """
            # ============================================================================
            #
            #   ____                                    _           ____  _
            #  |  _ \\ _ __ ___   __ _ _ __ ___  ___ ___(_)_   _____/ ___|| |_ __ _  __ _  ___  ___
            #  | |_) | '__/ _ \\ / _` | '__/ _ \\/ __/ __| \\ \\ / / _ \\___ \\| __/ _` |/ _` |/ _ \\/ __|
            #  |  __/| | | (_) | (_| | | |  __/\\__ \\__ \\ |\\ V /  __/___) | || (_| | (_| |  __/\\__ \\
            #  |_|   |_|  \\___/ \\__, |_|  \\___||___/___/_| \\_/ \\___|____/ \\__\\__,_|\\__, |\\___||___/
            #                   |___/                                              |___/
            #
            #                       D I A M O N D   A G E   (v2.0 reference file)
            #
            # ============================================================================
            # This is the most exhaustively-documented stage file in the default template
            # set. Read it top-to-bottom the first time — every category is explained in
            # enough detail that you shouldn't need to cross-reference anything else.
            #
            # Everything you see below is EDITABLE. Delete sections you don't need, or
            # copy sections into new *.toml files to create more stages.
            # ============================================================================
            #
            # TABLE OF CONTENTS
            #   1.  [stage]              — identity + display + dependencies
            #   2.  THE PREFIX SYSTEM    — how locked lists work
            #   3.  [items]              — item use/pickup/inventory
            #   4.  [blocks]             — block placement/interaction
            #   5.  [fluids]             — buckets / placement / submersion / EMI visibility
            #   6.  [recipes]            — crafting gates (two flavors)
            #   7.  [crops]              — planting / growth / bonemeal / harvest
            #   8.  [dimensions]         — portal + teleport gating
            #   9.  [enchants]           — table / anvil / villager / inventory strip
            #  10.  [entities]           — attacking specific mobs
            #  11.  [[interactions]]     — "player does X to Y" rules (Create-style)
            #  12.  [loot]               — GLM that filters chests/fishing/drops
            #  13.  [mobs]               — spawn gating + dynamic replacement
            #  14.  [[ores.overrides]]   — (on hold for 2.0, parsed for forward compat)
            #  15.  [pets]               — taming / breeding / commanding / riding
            #  16.  [screens]            — block OR item GUI opens (incl. shulkers/lootr)
            #  16a. [trades]             — villager / wandering-trader trade gating (by result)
            #  17.  [structures]         — entry push-back + per-structure rule flags
            #  18.  [[regions]]          — 3D boxes with flags + debuffs
            #  19.  [curios]             — per-slot gating when Curios is installed
            #  20.  KubeJS integration  — scripting hooks
            #  21.  Lootr integration   — per-player chests, filter chain
            #  22.  [enforcement]        — per-stage exemptions to global enforcement
            #  23.  TROUBLESHOOTING      — "why isn't my lock working?"
            # ============================================================================


            # ============================================================================
            # 1. [stage] — IDENTITY + DEPENDENCIES
            # ============================================================================
            # This section defines the stage itself. Every other section below describes
            # what UNLOCKS when the stage is granted — everything is locked by default
            # UNTIL the player's team has this stage.
            # ----------------------------------------------------------------------------

            [stage]
            # The stage ID. Should match the filename without ".toml". Lower-case,
            # no spaces. Must be unique across all stage files in the pack.
            id = "diamond_age"

            # Human-readable name shown in the lock message, stage list, EMI tooltip, etc.
            # Supports &-codes for color/formatting when displayed.
            display_name = "Diamond Age"

            # Free-form one-liner. Used by FTB Quests integration and optional GUIs.
            description = "Diamond tools, advanced farming, and new dimensions await."

            # Item shown in the lock icon overlay and anywhere else a stage is rendered
            # visually. Must be a valid item ID.
            icon = "minecraft:diamond_pickaxe"

            # Broadcast to ALL team members the moment the stage is granted.
            # &-codes: &c=red &a=green &b=aqua &e=yellow &l=bold &o=italic &r=reset
            unlock_message = "&b&lDiamond Age Unlocked! &r&7You have ascended."

            # Prerequisite stage(s). Can be a single string or a list.
            #   dependency = "iron_age"
            #   dependency = ["iron_age", "nether_explorer"]
            # Omit entirely for starting stages. When linear_progression is enabled in
            # progressivestages.toml, missing dependencies are auto-granted recursively.
            dependency = "iron_age"


            # ============================================================================
            # 2. THE PREFIX SYSTEM — read once, use everywhere
            # ============================================================================
            # Every `locked = [...]` list in this file accepts the SAME four prefixes.
            # Learn them once and you understand all 14+ categories.
            #
            #   "id:minecraft:diamond"       exact match. "id:" is optional —
            #   "minecraft:diamond"          a plain namespace:path string means id:.
            #
            #   "mod:ae2"                    everything whose registry namespace is "ae2".
            #                                Applies PER-CATEGORY — `mod:ae2` in [items]
            #                                locks only AE2 items, not AE2 blocks.
            #
            #   "tag:minecraft:crops"        everything in the tag. Works with any tag
            #                                the category's registry has — item tags for
            #                                [items], block tags for [blocks], fluid tags
            #                                for [fluids], enchant tags for [enchants], etc.
            #
            #   "name:diamond"               case-insensitive substring match against the
            #                                FULL id (e.g. "minecraft:diamond_pickaxe"
            #                                contains "diamond", so it matches).
            #                                Powerful but broad — prefer the others.
            #
            #   "#minecraft:crops"           legacy shorthand for tag:. Still supported.
            #
            # Each category also has `always_unlocked = [...]` — an ID-only whitelist that
            # bypasses the lock. Use it for exceptions: "lock mod:ae2 except ae2:charger".
            #
            # Order of evaluation inside one category:
            #   1. Is the ID in always_unlocked? → UNLOCKED.
            #   2. Does any prefix in `locked` match the ID? → LOCKED at that stage.
            #   3. Otherwise → UNLOCKED.
            #
            # Cross-category interactions: if the same thing appears locked in TWO
            # categories, BOTH enforcement paths run. E.g. putting "mod:ae2" in [items]
            # AND [screens] gates both item use AND GUI opens.
            # ============================================================================


            # ============================================================================
            # 3. [items] — ITEM USE / PICKUP / INVENTORY HOLDING
            # ============================================================================
            # Locked items cannot be:
            #   • right-clicked to use
            #   • picked up from the ground
            #   • held in the hotbar (scrubbed each tick — see progressivestages.toml)
            #   • crafted (the crafting table refuses to hand over the output)
            #
            # Enforcement can be relaxed via the [enforcement] section further down —
            # e.g. "allow picking up diamonds but not crafting with them".
            # ----------------------------------------------------------------------------

            [items]
            locked = [
                "id:minecraft:diamond",
                "id:minecraft:diamond_pickaxe",
                "id:minecraft:diamond_sword",
                "id:minecraft:diamond_axe",
                "id:minecraft:diamond_shovel",
                "id:minecraft:diamond_hoe",
                "id:minecraft:diamond_helmet",
                "id:minecraft:diamond_chestplate",
                "id:minecraft:diamond_leggings",
                "id:minecraft:diamond_boots",

                # Tag example — locks every item tagged as a diamond gem across all mods.
                # "tag:c:gems/diamond",

                # Mod example — locks every item registered under the ae2 namespace.
                # "mod:ae2",

                # Name example — locks everything whose registry ID contains "netherite".
                # Matches minecraft:netherite_ingot, modpack:netherite_band, etc.
                # "name:netherite",
            ]
            always_unlocked = [
                # Exception: let diamond horse armor through even if "name:diamond" is in
                # the locked list above. Only exact IDs are accepted in always_unlocked —
                # mod:/tag:/name: would be too broad to be a meaningful exemption.
                # "id:minecraft:diamond_horse_armor",
            ]


            # ============================================================================
            # 4. [blocks] — BLOCK PLACEMENT / INTERACTION
            # ============================================================================
            # Locked blocks:
            #   • cannot be placed
            #   • cannot be right-clicked (opens GUIs too — but see [screens] for that)
            #   • can optionally be made unbreakable via enforcement settings
            # ----------------------------------------------------------------------------

            [blocks]
            locked = [
                "id:minecraft:diamond_block",
                "id:minecraft:enchanting_table",
                "id:minecraft:beacon",
                # "tag:minecraft:beacon_base_blocks",
                # "mod:ae2",
            ]
            always_unlocked = []


            # ============================================================================
            # 5. [fluids] — BUCKET / PLACEMENT / SUBMERSION / EMI VISIBILITY
            # ============================================================================
            # Locked fluids have THREE enforcement paths:
            #   (a) Bucket pickup of the source block is refused.
            #   (b) In-world placement (bucket empty, flowing source) is cancelled.
            #   (c) A player submerged in the fluid takes Slowness II + Blindness every
            #       tick until they exit. Use sparingly — it's very obvious.
            #   (d) EMI/JEI hide the fluid from recipe browsers.
            #
            # Pipe/machine transport is NOT gated here — see the Mekanism/AE2 compat notes
            # in the automation section for why and how to gate that via [blocks]/[screens].
            # ----------------------------------------------------------------------------

            [fluids]
            locked = [
                # "id:minecraft:lava",
                # "mod:mekanism",
                # "tag:c:acids",
            ]
            always_unlocked = [
                # "id:mekanism:hydrogen",   # let one fluid show in EMI despite mod:mekanism
            ]


            # ============================================================================
            # 6. [recipes] — TWO FLAVORS OF CRAFTING GATE
            # ============================================================================
            # Two lists, each solving a different problem:
            #
            # locked_ids   — block a specific recipe by its registry ID.
            #                Useful when one item has multiple recipes and you only want
            #                to gate one of them (e.g. the smithing path but not the
            #                crafting-table path).
            #
            # locked_items — block EVERY recipe whose output is the listed item.
            #                Item stays usable — players can still loot it — but crafting
            #                is blocked anywhere (crafting table, mechanical crafters,
            #                autocrafters). Use this when you want "can't craft, can find".
            # ----------------------------------------------------------------------------

            [recipes]
            locked_ids = [
                # "id:minecraft:diamond_chestplate_from_smithing",
                # "mod:createaddition",   # all crafting recipes from that mod
            ]
            locked_items = [
                # "id:minecraft:diamond_chestplate",  # no recipe works, item still findable
                # "id:minecraft:diamond_sword",
            ]


            # ============================================================================
            # 7. [crops] — PLANTING / GROWTH / BONEMEAL / HARVEST
            # ============================================================================
            # Four independent enforcement surfaces, all triggered by the crop block
            # appearing in locked below:
            #
            #   Planting   — cancelled at BlockEvent.EntityPlaceEvent time.
            #   Growth     — random-tick growth attempts return "DO_NOT_GROW" when the
            #                NEAREST PLAYER lacks the stage (nearest-player pattern, same
            #                radius as mob-spawn check).
            #   Bonemeal   — BonemealEvent cancelled.
            #   Harvest    — breaking a locked, fully-grown crop keeps only items whose
            #                registry path contains "seed" (wheat_seeds but NOT wheat).
            #
            # Note: crops are blocks, so `tag:` uses block tags.
            # ----------------------------------------------------------------------------

            [crops]
            locked = [
                # "tag:minecraft:crops",           # all vanilla crops at once
                # "mod:croptopia",
                # "id:minecraft:wheat",
            ]
            always_unlocked = [
                # "id:minecraft:carrots",
            ]


            # ============================================================================
            # 8. [dimensions] — PORTAL + TELEPORT GATING
            # ============================================================================
            # Only accepts exact IDs — a dimension is a unique value, not a class of things,
            # so "mod:" and "tag:" don't make sense here.
            #
            # Enforcement runs at three layers:
            #   (1) EntityTravelToDimensionEvent  (pre-travel cancel for vanilla + most mods)
            #   (2) PlayerChangedDimensionEvent   (post-travel bounce-back for stubborn mods)
            #   (3) Per-second tick check         (catches anything that slipped both events)
            #
            # Nature's Compass integration is automatic when that mod is installed — the
            # compass item itself is blocked from use if any dimension is locked for the
            # player.
            # ----------------------------------------------------------------------------

            [dimensions]
            locked = [
                # "id:minecraft:the_end",
                # "id:minecraft:the_nether",
                # "id:twilightforest:twilight_forest",
            ]


            # ============================================================================
            # 9. [enchants] — TABLE / ANVIL / VILLAGER / INVENTORY STRIP
            # ============================================================================
            # Every enforcement surface for enchantments at once:
            #
            #   Enchanting table — locked enchants are zeroed from the preview, and the
            #                      "apply" button refuses when the primary pick is locked.
            #                      Player keeps their XP + lapis.
            #   Anvil            — AnvilUpdateEvent cancelled if either input carries a
            #                      locked enchant (can't sneak it across via rename).
            #   Villager trades  — librarian offers for locked enchanted books disappear
            #                      from the merchant UI (ServerPlayerMerchantMixin
            #                      filters offers server-side before they're sent).
            #   Inventory strip  — periodic scan rewrites ItemEnchantments / stored
            #                      enchantments components, removing any locked entry.
            #                      Covers loot drops, fishing, dungeon chests, etc.
            # ----------------------------------------------------------------------------

            [enchants]
            locked = [
                # "id:minecraft:mending",
                # "id:minecraft:infinity",
                # "tag:c:curse",
                # "mod:apotheosis",
            ]


            # ============================================================================
            # 10. [entities] — ATTACKING / RIGHT-CLICK GATING (not spawning!)
            # ============================================================================
            # This category gates *attacking* specific entity types. The entity still
            # spawns, exists, can attack the player — but left-click does nothing. Useful
            # for "you can't kill the boss yet" setups.
            #
            # To gate SPAWNING, use [mobs].locked_spawns instead.
            # ----------------------------------------------------------------------------

            [entities]
            locked = [
                # "id:minecraft:warden",
                # "tag:minecraft:raiders",
                # "mod:alexsmobs",
            ]
            always_unlocked = [
                # "id:alexsmobs:capuchin_monkey",   # in-mod exception
            ]


            # ============================================================================
            # 11. [[interactions]] — FINE-GRAINED "PLAYER DOES X TO Y" RULES
            # ============================================================================
            # Each entry is one specific rule. Three shapes:
            #
            # block_right_click — gate right-clicking a specific block (empty-handed too).
            #   Useful for: enchanting table GUI, respawn anchors, beacons.
            #
            # item_on_block     — gate using a held item on a target block (Create-style).
            #   Useful for: Create mechanical crafting, flint-and-steel on obsidian, etc.
            #   Both held_item and target_block accept tags (#foo:bar) + IDs.
            #
            # item_on_entity    — gate using a held item on a specific entity.
            #   Useful for: name tags on mobs, saddling, feeding tameable animals.
            #
            # Add as many [[interactions]] entries as you want.
            # ----------------------------------------------------------------------------

            [[interactions]]
            type = "block_right_click"
            target_block = "minecraft:enchanting_table"
            description = "Use Enchanting Table"

            [[interactions]]
            type = "block_right_click"
            target_block = "minecraft:beacon"
            description = "Configure Beacon"

            # [[interactions]]
            # type = "item_on_block"
            # held_item = "create:andesite_alloy"
            # target_block = "#minecraft:logs"
            # description = "Create Andesite Casing"

            # [[interactions]]
            # type = "item_on_entity"
            # held_item = "minecraft:name_tag"
            # target_entity = "minecraft:zombie"
            # description = "Name a Zombie"


            # ============================================================================
            # 12. [loot] — CHESTS / FISHING / ARCHEOLOGY / MOB + BLOCK DROPS
            # ============================================================================
            # Backed by a Global Loot Modifier (GLM). The GLM runs on EVERY loot table
            # roll anywhere in the game — vanilla chests, modded chests, fishing,
            # archeology brushing, mob drops, block drops, Lootr per-player rolls.
            # Items listed here are silently removed from the loot stack before the
            # player sees them.
            #
            # Player resolution for the filter (in order):
            #   1. LAST_DAMAGE_PLAYER  (mob/block drops caused by a kill)
            #   2. THIS_ENTITY         (chest opener)
            #   3. Nearest player to ORIGIN within mob_spawn_check_radius
            #
            # If no player is in range at all, loot passes through unchanged — matches
            # the policy used by mob spawn gating.
            # ----------------------------------------------------------------------------

            [loot]
            locked = [
                # "id:minecraft:diamond",
                # "mod:artifacts",
                # "tag:c:gems/diamond",
            ]


            # ============================================================================
            # 13. [mobs] — SPAWN GATING + DYNAMIC REPLACEMENT
            # ============================================================================
            # locked_spawns — prevents the mob from spawning at all when the NEAREST
            #                 player lacks the stage. Fires at FinalizeSpawnEvent, so it
            #                 catches natural spawns, spawners, spawn eggs, and most
            #                 modded spawn paths.
            #
            # [[mobs.replacements]] — instead of cancelling a spawn, swap in a different
            #                        mob at the same coords. Picks the first matching
            #                        entry. Replacement is validated via
            #                        Mob.checkSpawnRules + checkSpawnObstruction before
            #                        spawning, so a water mob on land falls back to a
            #                        plain cancel rather than a broken despawn.
            #
            # Boss gating: add "id:minecraft:wither" or "id:minecraft:ender_dragon" to
            # locked_spawns — both fire FinalizeSpawnEvent so they're gated automatically.
            # ----------------------------------------------------------------------------

            [mobs]
            locked_spawns = [
                "id:minecraft:creeper",
                # "mod:borninchaos_v1",
                # "tag:minecraft:raiders",
            ]

            # [[mobs.replacements]]
            # target = "id:minecraft:enderman"
            # replace_with = "id:minecraft:zombie"

            # [[mobs.replacements]]
            # target = "mod:alexsmobs"
            # replace_with = "id:minecraft:zombie"


            # ============================================================================
            # 14. [[ores.overrides]] — VISUAL MASQUERADE (per-stage)
            # ============================================================================
            # Make a real block APPEAR as a different block to any player who is
            # missing this stage. The real block is unchanged on the server — it's a
            # purely visual + drop-replacement gate. Once the player unlocks this
            # stage, the masquerade vanishes immediately (a stage-grant flush kicks
            # out the fake state and the real ore comes back).
            #
            # FIELDS (all three are REQUIRED, all three must be exact namespaced IDs):
            #   target       the real block in the world to disguise
            #   display_as   what the client should render instead
            #   drop_as      what mining the disguised block yields
            #                (set this to the display_as item id if you want "stone
            #                that drops cobble" behavior, like vanilla mining)
            #
            # MULTIPLE OVERRIDES:
            #   Add as many [[ores.overrides]] blocks as you want — each is one
            #   target → display_as pair, scoped to THIS stage. Iron, gold, redstone,
            #   lapis can all hide under stone, or each can hide under a different
            #   filler block. Modded ores work the same way — just use the mod's
            #   block ID for target.
            #
            # PER-STAGE NATURE:
            #   These overrides apply ONLY while THIS stage is locked for the
            #   player. iron_age.toml can hide iron ore, diamond_age.toml can hide
            #   diamond ore — each player only sees the masquerade for stages they
            #   haven't earned yet. A player with iron_age but not diamond_age sees
            #   real iron ore and fake "stone" where diamond ore would be.
            #
            # PERFORMANCE — `ore_spoof_radius`:
            #   Goes in [enforcement] below. Cube radius (in blocks) around each
            #   player within which spoof packets are sent. Default 8. Larger =
            #   more accurate visuals when the player walks toward unloaded ore,
            #   smaller = less per-tick work. Server view-distance bounds it
            #   anyway. Recommended 8-16.
            #
            # PLAYER-PLACED BLOCKS:
            #   If a player places real iron ore (e.g. for decoration), it's never
            #   spoofed — only naturally-generated / non-player blocks are masked.
            #   The mod tracks player placements per-dimension in SavedData.
            #
            # DROPS:
            #   When a disguised block is broken, vanilla loot is discarded and the
            #   drop_as item is dropped in its place. Silk-touch and fortune are
            #   intentionally ignored on the gated block — once unlocked, the real
            #   ore returns and silk/fortune work normally.
            # ----------------------------------------------------------------------------

            [[ores.overrides]]
            target = "id:minecraft:diamond_ore"
            display_as = "id:minecraft:stone"
            drop_as = "id:minecraft:cobblestone"

            [[ores.overrides]]
            target = "id:minecraft:deepslate_diamond_ore"
            display_as = "id:minecraft:deepslate"
            drop_as = "id:minecraft:cobbled_deepslate"


            # ============================================================================
            # 15. [pets] — TAMING / BREEDING / COMMANDING / RIDING
            # ============================================================================
            # Four independent slots, chosen automatically based on what the player is
            # trying to do:
            #
            #   locked_taming     — wild tameable right-clicked with its taming item.
            #   locked_breeding   — an already-tame animal being fed its breeding food
            #                       (either your own or another player's).
            #   locked_commanding — YOUR tame pet right-clicked to sit / stand / follow.
            #   (Riding)          — mounting is gated via EntityMountEvent, using whichever
            #                       of the three slots above matches the pet's state.
            #                       A locked_taming entry alone is enough to block riding.
            #
            # Fall-through order: commanding → breeding → taming. If you only fill
            # locked_taming, all three scenarios use it as the gate.
            # ----------------------------------------------------------------------------

            [pets]
            locked_taming = [
                # "id:minecraft:wolf",
                # "tag:c:tamable",
            ]
            locked_breeding = [
                # "id:minecraft:cow",
            ]
            locked_commanding = [
                # "id:minecraft:wolf",   # tame player can't tell their own wolf to sit
            ]


            # ============================================================================
            # 16. [screens] — BLOCK OR ITEM GUI OPENS
            # ============================================================================
            # Unified list matched against BOTH block IDs (for block right-clicks) AND
            # item IDs (for item right-clicks that open a GUI, e.g. backpacks / portable
            # crafting tables / held shulker boxes). One list covers both surfaces.
            #
            # Works for every container type automatically — chest, trapped chest, barrel,
            # shulker box, ender chest, hopper, dispenser, dropper, and modded containers
            # that extend those base classes.
            #
            # LOOTR USERS: Lootr containers (lootr:chest, lootr:barrel, lootr:shulker)
            # all fire the same events vanilla containers do. Use Lootr's built-in tags:
            #   "tag:lootr:chests"    — every chest-shaped Lootr container
            #   "tag:lootr:barrels"   — barrels only
            #   "tag:lootr:shulkers"  — shulkers only
            #   "tag:lootr:containers" — everything Lootr treats as a container
            # ----------------------------------------------------------------------------

            [screens]
            locked = [
                # "id:minecraft:crafting_table",
                # "id:minecraft:anvil",
                # "id:minecraft:ender_chest",
                # "mod:create",                    # every Create block GUI
                # "tag:minecraft:shulker_boxes",   # blocks
                # "tag:c:shulker_boxes",           # item tag variant — locks held shulkers too
                # "tag:lootr:chests",              # all Lootr chests
            ]


            # ============================================================================
            # 16a. [trades] — VILLAGER / WANDERING-TRADER TRADE GATING
            # ============================================================================
            # Hides AND blocks any villager or wandering-trader offer whose RESULT item
            # matches this list — using the same prefix system as everything else
            # (id: / mod: / tag: / name:). Use this when you want "you can't BUY this yet"
            # without locking the item itself: unlike [items], the player can still hold,
            # use, and obtain the result through other means — they just can't trade for it.
            #
            #   • The offer disappears from the trade GUI (server-filtered, so a vanilla
            #     client never sees it), AND
            #   • the trade is blocked server-side even for a tampered/desynced client
            #     (the result slot is cleared authoritatively), so it can't be completed.
            #
            # NBT-AWARE: enchanted-book / enchanted-gear trades are ALSO gated by the
            # [enchants] category above — if the result carries a locked enchantment, the
            # trade is hidden/blocked too (no need to list enchanted_book here).
            #
            # Creative and spectator players bypass. Toggle globally with
            # enforcement.block_trades in progressivestages.toml.
            #
            # Example: "diamonds are post-Nether" — lock the diamond-tier tool/armor and
            # enchanted-book trades so blacksmith/toolsmith/weaponsmith offers for them
            # vanish until the stage is earned, while diamonds themselves stay usable.
            # ----------------------------------------------------------------------------

            [trades]
            locked = [
                # "id:minecraft:diamond_pickaxe",  # blacksmith/toolsmith diamond-pick trade
                # "id:minecraft:diamond_chestplate",
                # "tag:c:tools",                   # every tool result, across mods
                # "mod:create",                    # any Create item sold by a (modded) trader
            ]
            # always_unlocked — carve-outs exempt from this stage's [trades] locks.
            always_unlocked = [
                # "id:minecraft:diamond_hoe",
            ]


            # ============================================================================
            # 17. [structures] — ENTRY + RULES INSIDE LOCKED STRUCTURES
            # ============================================================================
            # locked_entry — the structure IDs that are gated. When a player steps inside
            #                the piece bounding box of any of these AND lacks the stage,
            #                they're teleported to the nearest outside edge. Applies
            #                to vanilla + modded structures, piece-precise (not just
            #                chunk-reference precise).
            #
            # [structures.rules] — applied to EVERY locked structure at once:
            #
            #   prevent_block_break   — block breaks inside are cancelled, and players get
            #                            mining fatigue V while inside for tactile feedback.
            #   prevent_block_place   — block placements inside are cancelled.
            #   prevent_explosions    — any block position inside a locked structure is
            #                            removed from the explosion's affected-blocks list
            #                            (so creepers / TNT can't punch holes in).
            #   disable_mob_spawning  — mobs cannot spawn inside the structure at all.
            #
            # Also: breaking a container (chest/barrel/shulker/lootr) inside a locked
            # structure is ALWAYS cancelled regardless of prevent_block_break — players
            # can't break the chest to spill the loot. This is the §2.12 "chest locking".
            # ----------------------------------------------------------------------------

            [structures]
            locked_entry = [
                # "id:minecraft:ocean_monument",
                # "id:minecraft:stronghold",
                # "id:minecraft:ancient_city",
                # "tag:minecraft:on_ocean_explorer_maps",
            ]

            [structures.rules]
            prevent_block_break = false
            prevent_block_place = false
            prevent_explosions = false
            disable_mob_spawning = false


            # ============================================================================
            # 18. [[regions]] — FIXED 3D BOXES WITH FLAGS + DEBUFFS
            # ============================================================================
            # A region is a hand-authored 3D box in a specific dimension with an on/off
            # flag per enforcement surface. Useful for spawn protection, admin zones,
            # story areas that open up as stages unlock.
            #
            # Flags:
            #   prevent_entry         — push players out AND apply Slowness III + Blindness
            #                            for 3 seconds so they feel the gate.
            #   prevent_block_break   — cancel break events inside.
            #   prevent_block_place   — cancel place events inside.
            #   prevent_explosions    — clear explosion-affected positions inside.
            #   disable_mob_spawning  — cancel mob spawns inside.
            #
            # pos1 + pos2 are inclusive corners, order doesn't matter. Y range is clamped
            # by the dimension's height at runtime.
            # ----------------------------------------------------------------------------

            # [[regions]]
            # dimension = "minecraft:overworld"
            # pos1 = [0, -64, 0]
            # pos2 = [1000, 320, 1000]
            # prevent_entry = true
            # prevent_block_break = false
            # prevent_block_place = false
            # prevent_explosions = true
            # disable_mob_spawning = false


            # ============================================================================
            # 19. [curios] — PER-SLOT GATING WHEN CURIOS IS INSTALLED
            # ============================================================================
            # locked_slots is a plain list of Curios slot identifiers ("ring", "necklace",
            # "belt", modded names, etc.). When Curios is loaded, a tick scan ejects items
            # from locked slots and also strips locked enchants from curio-held stacks.
            #
            # If Curios isn't installed, this section is parsed and ignored — safe to leave
            # in.
            # ----------------------------------------------------------------------------

            [curios]
            locked_slots = [
                # "ring",
                # "necklace",
                # "curious_armor:artifact",
            ]


            # ============================================================================
            # 20. KubeJS INTEGRATION — SCRIPTING HOOKS
            # ============================================================================
            # When KubeJS is installed, stages from this mod are first-class KubeJS stages.
            # Scripts can read and modify them with the standard KubeJS Stages API.
            #
            # READ:
            #   server_scripts/stages.js:
            #     ServerEvents.commandRegistry(event => {
            #         // no-op, just forcing the file to load
            #     })
            #     PlayerEvents.loggedIn(event => {
            #         if (event.player.stages.has("diamond_age")) {
            #             event.player.tell("Welcome back, Diamond-bearer.")
            #         }
            #     })
            #
            # GRANT:
            #     event.player.stages.add("diamond_age")   // respects dependencies
            #
            # REVOKE:
            #     event.player.stages.remove("diamond_age")
            #
            # REACT TO STAGE CHANGES:
            #     PlayerEvents.stageAdded("diamond_age", event => {
            #         event.player.tell("You earned the diamond stage!")
            #         event.player.give("minecraft:diamond_pickaxe")
            #     })
            #
            # GATE A KUBEJS RECIPE:
            #   server_scripts/recipes.js:
            #     ServerEvents.recipes(event => {
            #         // One recipe that only mentions the stage in its ID:
            #         event.shaped("minecraft:diamond_block", [
            #             "DDD", "DDD", "DDD"
            #         ], { D: "minecraft:diamond" }).id("diamond_age:compact_diamonds")
            #     })
            #   …then gate that recipe via [recipes] locked_ids above:
            #     locked_ids = ["id:diamond_age:compact_diamonds"]
            #
            # The compat bridge is in compat/kubejs/KubeJSStagesCompat.java. It fires the
            # standard KubeJS STAGE_ADDED / STAGE_REMOVED events on grant/revoke.
            # ============================================================================


            # ============================================================================
            # 21. LOOTR INTEGRATION — PER-PLAYER CHESTS, FILTER CHAIN
            # ============================================================================
            # Lootr replaces vanilla loot chests with per-player instances. When both mods
            # are installed, lock enforcement applies at TWO layers:
            #
            #   (a) Our Global Loot Modifier runs on every LootTable roll — including the
            #       rolls Lootr performs per-player. Locked items are removed before Lootr
            #       wraps them in the per-player inventory. This is automatic.
            #
            #   (b) A ServiceLoader-registered ILootrFilterProvider (see compat/lootr/)
            #       plugs directly into Lootr's own filter chain. Our filter runs at
            #       priority 1000 (late) so it operates on the already-populated stack,
            #       removing anything that would still be locked for the player Lootr
            #       identified from the LootContext.
            #
            # USING LOOTR TAGS IN THIS FILE:
            #   Lootr publishes these block tags which you can use anywhere in [blocks],
            #   [screens], or [structures] above:
            #     tag:lootr:chests          — every Lootr chest-shaped block
            #     tag:lootr:barrels
            #     tag:lootr:shulkers
            #     tag:lootr:containers      — everything Lootr considers a container
            #     tag:lootr:trapped_chests
            #
            # BEHAVIOR INSIDE LOCKED STRUCTURES:
            #   Right-clicking a Lootr chest inside a locked structure is refused by the
            #   structure chest-locking guard. Breaking it is also refused (can't skip the
            #   gate by mining). Both covered automatically — no config needed.
            # ============================================================================


            # ============================================================================
            # 22. [enforcement] — PER-STAGE EXEMPTIONS TO GLOBAL ENFORCEMENT
            # ============================================================================
            # Each list here exempts items from a specific enforcement action JUST for
            # the duration of this stage being locked. The item is still "locked" (shows
            # lock icon in EMI, counted by commands) — but the specific action in each
            # list is allowed.
            #
            # Accepted formats in every list:
            #   "minecraft:diamond"        exact item ID
            #   "#c:gems/diamond"          item tag (note the # prefix, different from
            #                              the prefix system above — this list is legacy)
            #   "mekanism"                 bare mod ID (no colon)
            #
            # Each list only applies when the matching global toggle in
            # progressivestages.toml is ON. If block_item_pickup is false globally,
            # allowed_pickup is irrelevant.
            # ----------------------------------------------------------------------------

            [enforcement]
            # Players can right-click / mine with these items even when locked.
            allowed_use = [
                # "minecraft:diamond_ore",
                # "minecraft:deepslate_diamond_ore",
            ]

            # Players can pick these up off the ground even when locked.
            allowed_pickup = [
                # "minecraft:diamond",         # stockpile while you work toward unlock
                # "#c:gems/diamond",
            ]

            # These items aren't removed from the hotbar by the inventory scrubber.
            allowed_hotbar = [
                # "minecraft:diamond",
            ]

            # Players can click/drag these with their mouse in GUIs (chests, etc.).
            allowed_mouse_pickup = [
                # "minecraft:diamond",
                # "minecraft:diamond_ore",
            ]

            # These items aren't auto-dropped from the main inventory by the scrubber.
            allowed_inventory = [
                # "minecraft:diamond",
                # "minecraft:diamond_ore",
                # "minecraft:deepslate_diamond_ore",
            ]

            # v2.0.1: per-stage radius (blocks) around each player within which
            # ore-masquerade packets are sent. Only consulted when this stage has
            # at least one [[ores.overrides]] entry. Default 8. Larger = smoother
            # visuals when running into fresh chunks, smaller = less per-tick work.
            ore_spoof_radius = 12

            # v2.0.1: Block crafting recipes whose INGREDIENTS contain any item
            # that is locked under THIS stage. Without this, a player who has
            # locked iron_ingot can still craft a hopper (uses iron) as long as
            # the hopper itself isn't locked. With this set to true, the crafting
            # output is suppressed whenever any ingredient is gated by this stage.
            # Covers crafting table, anvil, smithing table, stonecutter, loom,
            # cartography table (all output slots).
            # Default: false. Set per-stage as appropriate — e.g. iron_age.toml
            # may want this true so iron is truly a "you can't use it for ANYTHING"
            # gate, while diamond_age.toml might leave it false.
            block_crafting_with_locked_ingredients = true

            # v2.0.1: Block automated crafting (vanilla Crafter, mod auto-crafters
            # via the public API hook) from producing items whose recipe contains
            # any locked ingredient — checked against the NEAREST player within
            # crafter_check_radius. Prevents bypassing the manual-craft gate via
            # autocrafters loaded with locked materials by another player.
            # Default: false.
            block_automated_crafting = true

            # Radius (blocks) for the nearest-player check used by automated-craft
            # gating. Only consulted when block_automated_crafting = true.
            # Default 32. Larger = stricter (catches autocrafters further from the
            # gated player), smaller = looser.
            crafter_check_radius = 32


            # ============================================================================
            # 23. TROUBLESHOOTING — "WHY ISN'T MY LOCK WORKING?"
            # ============================================================================
            #
            # "Items in my locked list aren't locked."
            #   1. Check the item ID exactly — F3+H ingame shows the full registry ID.
            #   2. Check progressivestages.toml — the corresponding global toggle
            #      (block_item_use, block_item_pickup, etc.) must be ON.
            #   3. Check [enforcement] in this file — maybe you exempted the item.
            #   4. Enable debug_logging in progressivestages.toml and reload stages via
            #      /progressivestages reload — the server log will show which locks were
            #      registered for each stage.
            #
            # "My tag: prefix does nothing."
            #   Tag membership is resolved at runtime from datapacks. If the tag doesn't
            #   exist or has zero members, the prefix matches nothing. Check via /tag.
            #
            # "Mobs are still spawning."
            #   The nearest-player check uses mob_spawn_check_radius (default 128 blocks).
            #   If no player is within that range, the spawn is allowed because no one is
            #   there to see it. Increase the radius in progressivestages.toml if you want
            #   more aggressive gating — max is 512.
            #
            # "My enchant is still appearing in the enchanting table."
            #   Secondary enchants from getEnchantmentList can slip past the clue-based
            #   preview filter. The inventory scan still strips them within a tick of
            #   application, so players can't keep them — but the preview may briefly show
            #   them. Ship-level fix would need a mixin at a lower level than 2.0 attempts.
            #
            # "Players are grief-breaking a locked structure's walls."
            #   Turn on [structures.rules] prevent_block_break. Players also get mining
            #   fatigue V while inside, so even without the flag they can't practically
            #   progress.
            #
            # "Create harvester is still harvesting my locked crop."
            #   Create ultimately fires vanilla BlockDropsEvent, which our GLM filter
            #   catches. If you're still seeing drops, the crop isn't in [crops] or
            #   [loot] — add "mod:croptopia" / "tag:minecraft:crops" / etc.
            #
            # "Mekanism pipes are transferring locked fluids between machines."
            #   Pipe-to-pipe transport doesn't fire vanilla events, so we can't intercept
            #   the transfer itself. Workaround: lock the SOURCE machine block in [blocks]
            #   or [screens]. Players can't open/configure it → pipes have nothing to pull.
            #
            # "Curios items aren't being ejected."
            #   Check server log for "Curios compat active — scanning curio slots". If
            #   absent, either Curios isn't installed or its API couldn't be resolved on
            #   this version. Fall back: add the items to [items] locked instead — Curios
            #   items are regular Items and ItemEnforcer still scrubs them from the main
            #   inventory.
            # ============================================================================
            """;
    }

    public static String triggers() {
        return """
            # ============================================================================
            # ProgressiveStages 2.0 — Trigger Configuration
            # ============================================================================
            # Automatic stage grants based on player actions. All triggers are event-driven
            # (no per-tick polling). Five sections live in this file:
            #
            #   [advancements]   single-shot: earning advancement A grants stage X.
            #   [items]          single-shot: picking up (or having) item I grants stage X.
            #   [dimensions]     one-time:   first entry into dimension D grants stage X.
            #   [bosses]         one-time:   killing entity B grants stage X.
            #   [[multi]]        N-of-N:     stage X is granted ONLY when MULTIPLE
            #                                sub-triggers have ALL fired (or ANY fired)
            #                                for the same player.
            #
            # Format for the first four sections:
            #   "resource_id" = "stage_id"
            #
            # The four one-to-one sections are unchanged from 1.x. Multi-trigger is new
            # in 2.0 — see the [[multi]] section at the bottom of this file for a deep
            # explanation with worked examples.
            #
            # Trigger persistence is stored per-world. To reset a trigger that has already
            # fired (dimension entry, boss kill, or any multi-requirement):
            #   /progressivestages trigger reset <player> dimension <id>
            #   /progressivestages trigger reset <player> boss <id>
            #   /progressivestages trigger reset <player> multi <requirement-id>
            #
            # To see all loaded multi-requirements plus per-player progress:
            #   /progressivestages multi list [player]
            # ============================================================================


            # ============================================================================
            # ADVANCEMENT TRIGGERS — one advancement -> one stage
            # ============================================================================
            # When the player earns the advancement, the stage is granted IMMEDIATELY
            # (no restart, no relog). Fires once per (player, stage) — re-earning the
            # advancement doesn't re-grant. Stages still granted via /stage grant work
            # normally regardless of advancement state.
            # ----------------------------------------------------------------------------
            [advancements]
            # "minecraft:story/mine_stone" = "stone_age"
            # "minecraft:story/smelt_iron" = "iron_age"
            # "minecraft:story/mine_diamond" = "diamond_age"
            # "minecraft:nether/return_to_sender" = "nether_warrior"


            # ============================================================================
            # ITEM PICKUP TRIGGERS — one item -> one stage
            # ============================================================================
            # Two surfaces:
            #   (a) Pickup event — when a player picks up the item off the ground.
            #   (b) Login scan   — when a player logs in WITH the item already in their
            #                      inventory (grants the stage retroactively).
            # ----------------------------------------------------------------------------
            [items]
            # "minecraft:iron_ingot" = "iron_age"
            # "minecraft:diamond" = "diamond_age"
            # "minecraft:netherite_ingot" = "netherite_age"


            # ============================================================================
            # DIMENSION ENTRY TRIGGERS — first entry only
            # ============================================================================
            # Fires the FIRST time the player enters the dimension. Persisted across
            # restarts. Reset with: /progressivestages trigger reset <player> dimension <id>
            # ----------------------------------------------------------------------------
            [dimensions]
            # "minecraft:the_nether" = "nether_explorer"
            # "minecraft:the_end"    = "end_explorer"


            # ============================================================================
            # BOSS KILL TRIGGERS — first kill only
            # ============================================================================
            # Fires the FIRST time the player kills the entity. Direct hits, projectiles,
            # and recently-damaged-by-player attribution all count.  Persisted across
            # restarts. Reset with: /progressivestages trigger reset <player> boss <id>
            # ----------------------------------------------------------------------------
            [bosses]
            # "minecraft:ender_dragon" = "dragon_slayer"
            # "minecraft:wither"       = "wither_slayer"
            # "minecraft:warden"       = "warden_slayer"


            # ============================================================================
            # MULTI-TRIGGER REQUIREMENTS  [[multi]]   — NEW IN 2.0
            # ============================================================================
            # A multi-trigger grants a stage ONLY when several sub-triggers have ALL fired
            # (or, optionally, when ANY ONE of them fires). Use this when "do one thing"
            # isn't strict enough — for example, "must own a full stone tool set" or "must
            # have visited both the Nether AND the End".
            #
            # SHAPE
            # -----
            # Each [[multi]] entry is one requirement. Fields:
            #
            #   stage         = "<stage-id>"        REQUIRED. Stage to grant on completion.
            #   description   = "<free text>"      Optional. Shown by /progressivestages multi list.
            #   id            = "<stable-id>"      Optional. Persistence handle (see PROGRESS).
            #                                      If omitted, an id is auto-derived from a hash
            #                                      of the contents (stable across reorderings).
            #   all_of        = [ "<sub>", ... ]   Mutually exclusive with any_of. ALL must fire.
            #   any_of        = [ "<sub>", ... ]   Mutually exclusive with all_of. ONE must fire.
            #
            # SUB-TRIGGER PREFIXES
            # --------------------
            # Each sub-trigger string uses a "<surface>:<id>" prefix to pick the surface
            # it listens on:
            #
            #   item:<namespace:path>          item picked up OR already in inventory at login
            #   advancement:<namespace:path>   advancement earned (or already earned at login)
            #   dimension:<namespace:path>     dimension entered (or current dim at login)
            #   boss:<namespace:path>          entity killed (NOT retroactive — see PROGRESS)
            #
            # ALL OR ANY?
            # -----------
            # all_of  -> stage is granted ONLY after every sub-trigger has fired at least
            #            once for that player. Progress is partial — the player can satisfy
            #            sub-triggers in any order, over any timespan, across sessions.
            # any_of  -> stage is granted the moment ANY one sub-trigger fires. (Logically
            #            this could be split into N separate single-trigger lines, but
            #            grouping is cleaner when several routes lead to the same stage.)
            #
            # PROGRESS, PERSISTENCE, AND RESETS
            # ---------------------------------
            # Per-player progress is persisted in world/data/progressivestages_triggers.dat
            # (the same file as boss/dimension persistence). For each player, each sub-key
            # of each requirement is either satisfied or not.
            #
            # The `id` field controls the persistence key. If two requirements share an id
            # they share their progress, which is almost never what you want — change the
            # id if you copy a requirement.
            #
            # To reset a player's progress on one requirement:
            #   /progressivestages trigger reset <player> multi <requirement-id>
            #
            # To inspect every requirement plus a player's progress through them:
            #   /progressivestages multi list <player>
            #
            # Retroactive credit:
            #   item:        scanned at login from inventory
            #   advancement: scanned at login from advancement progress
            #   dimension:   the current dimension on login counts as visited
            #   boss:        NOT scanned (a past kill cannot be retrieved); the player has
            #                to kill the boss again after the requirement is added. Use the
            #                reset command + /stage grant to award by hand if needed.
            #
            # WORKED EXAMPLES
            # ---------------

            # Example 1: Stone tool master — must own a stone sword, pickaxe, AND axe.
            # The user's canonical example. Stage granted only when all three items have
            # been picked up (or are present in inventory at login).
            #
            # [[multi]]
            # stage = "stone_tools_master"
            # description = "Own a full set of stone tools"
            # all_of = [
            #     "item:minecraft:stone_sword",
            #     "item:minecraft:stone_pickaxe",
            #     "item:minecraft:stone_axe",
            # ]

            # Example 2: Cross-surface — kill the dragon AND earn the achievement.
            # Mixes two different sub-types in one requirement.
            #
            # [[multi]]
            # stage = "true_dragon_slayer"
            # description = "Dragon kill + Free The End"
            # all_of = [
            #     "boss:minecraft:ender_dragon",
            #     "advancement:minecraft:end/kill_dragon",
            # ]

            # Example 3: Any-of — visiting either non-overworld dimension is enough.
            # Equivalent to two separate [dimensions] lines, but documents intent better.
            #
            # [[multi]]
            # stage = "off_world_explorer"
            # description = "Visit the Nether or the End"
            # any_of = [
            #     "dimension:minecraft:the_nether",
            #     "dimension:minecraft:the_end",
            # ]

            # Example 4: Many-of-many — stack several surfaces under one stage.
            #
            # [[multi]]
            # id = "endgame_gate"
            # stage = "endgame"
            # description = "Beat the major bosses + reach the End"
            # all_of = [
            #     "boss:minecraft:ender_dragon",
            #     "boss:minecraft:wither",
            #     "dimension:minecraft:the_end",
            #     "advancement:minecraft:adventure/totem_of_undying",
            # ]
            """;
    }
}
