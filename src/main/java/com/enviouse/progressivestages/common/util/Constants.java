package com.enviouse.progressivestages.common.util;

import net.minecraft.resources.ResourceLocation;

/**
 * Constants used throughout the mod
 */
public final class Constants {
    public static final String MOD_ID = "progressivestages";
    public static final String MOD_NAME = "ProgressiveStages";
    public static final String MOD_VERSION = "1.1.0";

    // Network packet IDs
    public static final ResourceLocation STAGE_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_sync");
    public static final ResourceLocation STAGE_UPDATE_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_update");
    public static final ResourceLocation LOCK_REGISTRY_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "lock_registry_sync");
    public static final ResourceLocation LOCK_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "lock_sync");
    public static final ResourceLocation STAGE_DEFINITIONS_SYNC_PACKET = ResourceLocation.fromNamespaceAndPath(MOD_ID, "stage_definitions_sync");

    // Stage file directory name (inside config folder)
    public static final String STAGE_FILES_DIRECTORY = "ProgressiveStages";

    // Attachment type names
    public static final ResourceLocation TEAM_STAGE_ATTACHMENT = ResourceLocation.fromNamespaceAndPath(MOD_ID, "team_stages");

    private Constants() {
        // Prevent instantiation
    }
}
