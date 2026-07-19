package com.enviouse.progressivestages.common.util;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Constants used throughout the mod
 */
public final class Constants {
    public static final String MOD_ID = "progressivestages";
    public static final String MOD_NAME = "ProgressiveStages";

    /**
     * Runtime version read from the mod container (injected from gradle.properties mod_version
     * into neoforge.mods.toml at build time). Never needs manual updating.
     */
    public static String MOD_VERSION = ModList.get()
        .getModContainerById(MOD_ID)
        .map(mc -> mc.getModInfo().getVersion().toString())
        .orElse("unknown");

    // Network packet IDs
    public static final ResourceLocation STAGE_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_sync");
    public static final ResourceLocation STAGE_UPDATE_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_update");
    public static final ResourceLocation LOCK_REGISTRY_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "lock_registry_sync");
    public static final ResourceLocation LOCK_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "lock_sync");
    public static final ResourceLocation ABILITY_STATE_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "ability_state");
    public static final ResourceLocation ENTITY_VISIBILITY_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "entity_visibility");
    public static final ResourceLocation STAGE_DEFINITIONS_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_definitions_sync");
    public static final ResourceLocation CREATIVE_BYPASS_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "creative_bypass");
    public static final ResourceLocation REVEAL_POLICY_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "reveal_policy");
    public static final ResourceLocation ORE_SPOOF_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "ore_spoof");
    // v2.3: stage-tree GUI — C2S request for data + open, S2C data + open
    public static final ResourceLocation REQUEST_STAGE_GUI_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "request_stage_gui");
    public static final ResourceLocation STAGE_GUI_DATA_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_gui_data");
    // v2.4: skill-tree purchase request (C2S)
    public static final ResourceLocation REQUEST_PURCHASE_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "request_purchase");
    // v2.4: unlock juice — toast popup (S2C) + active-goal HUD bar progress (S2C)
    public static final ResourceLocation UNLOCK_TOAST_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "unlock_toast");
    public static final ResourceLocation ACTIVE_GOAL_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "active_goal");
    public static final ResourceLocation CHALLENGE_HUD_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "challenge_hud");
    public static final ResourceLocation CLIENT_SNAPSHOT_MANIFEST_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "client_snapshot_manifest");
    public static final ResourceLocation CLIENT_SNAPSHOT_CHUNK_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "client_snapshot_chunk");
    public static final ResourceLocation CLIENT_SNAPSHOT_ACK_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "client_snapshot_ack");
    public static final ResourceLocation CLIENT_SNAPSHOT_REQUEST_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "client_snapshot_request");
    public static final ResourceLocation EDITOR_OPEN_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "editor_open");
    public static final ResourceLocation EDITOR_REQUEST_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "editor_request");
    public static final ResourceLocation EDITOR_RESPONSE_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "editor_response");

    // v3.0 unified config layout:
    // config/progressivestages/progressivestages.toml
    // config/progressivestages/stages/*.toml
    public static final String CONFIG_DIRECTORY = "progressivestages";
    public static final String STAGES_DIRECTORY = "stages";
    public static final String MAIN_CONFIG_FILE = CONFIG_DIRECTORY + "/progressivestages.toml";

    // Attachment type names
    public static final ResourceLocation TEAM_STAGE_ATTACHMENT = ResourceLocation.fromNamespaceAndPath(MOD_ID, "team_stages");

    private Constants() {
        // Prevent instantiation
    }
}
