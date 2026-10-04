# Editor rule regression verification

The editor and server share the schema 4 compiler. A saved rule must affect its selected action,
preserve unedited source, and release its restriction when the containing stage is owned.

## Automated coverage

`RulesPanel.test.tsx` covers complete unchanged rule groups, focused priority changes, compound
and nested conditions, viewer differences, extra selectors, scalar target updates, stale edits,
category changes with exceptions, ownership across lifetime changes, conditional crafting rejection,
generic conversions that would discard settings, and legacy enchantment list edits.
`EffectsPanel.test.tsx` checks the destination file for modern
and legacy stage attributes. Compiler tests verify every expanded parent has its matching exception
and that exception ownership remains consistent.

`EditorResponseDispatchTest` verifies response correlation and completion without scheduling work
on the render thread. Desktop acceptance must also save and apply a draft while the Minecraft
window is hidden, because a running network connection can outlive a paused window buffer swap.

`EditorRuleGameTests` loads real stage packages and checks pickup versus use and inventory,
placement versus interaction, bonemeal versus planting, block menu access, anvil versus retention,
trade visibility versus purchase, villager professions, pet breeding versus commands, beacon
effects, potion collection versus brewing, advancement visibility versus toast suppression, selected
item dropping, bucket placement, movement abilities, and dimension travel actions. Native water
spreading and native brewing tests compare world or inventory state before denial and after a
stage grant. A weather transition verifies that toast display returns without granting a stage or
reloading definitions. These server tests do not prove desktop rendering or authenticated multiplayer.

`LuckPermsPublicationGameTests` keeps two personal stage projections independent across grants,
revokes, provider changes and membership changes, while checking global invalidation and recovery.
`LuckPermsNativeGameTests` uses the actual provider, registered server player fixtures and native
permission queries on already loaded users. It checks independent positive output, another player's
node change, and synchronized context withdrawal and restoration through `ServerPlayer.setGameMode`.
It also checks that the bridge's own context notifications leave the update queue drained. Fixture
players, channels, transient nodes, definitions, ownership and clocks are restored after the test.
These registered fixtures are not authenticated clients.

Run the frontend checks and build before packaging the mod, then run Java unit tests, build, and
the server GameTests on the pinned Minecraft 1.21.1 and NeoForge 21.1.248 runtime. Selling Bin tests
need the real Selling Bin 1.6 artifact and the existing optional fixture switch. Provider tests need
the selected compatible LuckPerms runtime and `progressivestages.luckPermsGameTests=true` on the
GameTest server. Native provider coverage passed with LuckPerms 5.4.150. Use disposable output and complete cleanup after the
last consumer, preserving only required evidence and the requested candidate.

## Runtime boundaries

Fluid flow uses the nearest player within the configured mob spawn check radius. It normalizes
flowing water and lava to their source identifiers. Brewing uses the nearest player within
16 blocks and checks native predicted potion outputs before consuming the recipe ingredient.
Without a nearby player, these automated actions continue. Legacy potion lists still restrict
extraction only; generic `take` covers both the player slot and hopper path. Generic `brew` controls
the native brewing transaction. Third party brewing implementations need an applicable integration.

Native portal travel carries a portal action; direct dimension changes carry teleport. The general
enter action applies to both. Server packet checks restore advancement toast display on the next
progress update after a temporary condition ends.

## Desktop acceptance

Use the matching silent laptop client on the disposable dedicated server. Create and edit a stage
through Brave, apply the reviewed draft, reopen it, export it, and reimport it. Check the saved
source and the live behavior together. For selective Selling Bin restrictions, test bread and an
unrestricted sellable item through held insertion, open menu insertion, and auto sell, both before
and after obtaining the stage. Verify real LuckPerms synchronized loss, permanent retention, and
independent ownership. A real two player team test remains separate from detached player objects.
