package com.enviouse.progressivestages.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClientChallengeHud {

    private static volatile List<Entry> entries = List.of();

    private ClientChallengeHud() {}

    public static void setEntries(List<Entry> next) {
        entries = next == null ? List.of() : List.copyOf(next);
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static void clear() {
        entries = List.of();
    }

    public static void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Entry> snapshot = entries;
        if (snapshot.isEmpty() || minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        Map<String, Integer> offsets = new HashMap<>();
        for (Entry entry : snapshot) {
            renderEntry(graphics, minecraft, entry, offsets);
        }
    }

    private static void renderEntry(GuiGraphics graphics, Minecraft minecraft, Entry entry,
                                    Map<String, Integer> offsets) {
        double scale = Math.max(0.25, Math.min(10, entry.scale()));
        int width = panelWidth(minecraft, entry);
        int height = panelHeight(entry);
        String placement = normalizedPlacement(entry.placement());
        int offset = offsets.getOrDefault(placement, 0);
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int scaledWidth = (int) Math.ceil(width * scale);
        int scaledHeight = (int) Math.ceil(height * scale);
        int screenX = switch (placement) {
            case "top_left", "bottom_left" -> 8;
            case "top_right", "bottom_right" -> screenWidth - scaledWidth - 8;
            default -> (screenWidth - scaledWidth) / 2;
        };
        int screenY = switch (placement) {
            case "bottom", "bottom_left", "bottom_right" -> screenHeight - scaledHeight - 8 - offset;
            case "center" -> (screenHeight - scaledHeight) / 2 + offset;
            default -> 8 + offset;
        };
        offsets.put(placement, offset + scaledHeight + 5);

        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, 0);
        graphics.pose().scale((float) scale, (float) scale, 1);
        int accent = animatedColor(entry.color(), entry.animation());
        graphics.fill(0, 0, width, height, 0xD0101010);
        graphics.fill(0, 0, width, 1, accent);
        graphics.fill(0, height - 1, width, height, accent);
        graphics.fill(0, 0, 1, height, accent);
        graphics.fill(width - 1, 0, width, height, accent);

        int textX = 7;
        ResourceLocation iconId = ResourceLocation.tryParse(entry.icon());
        if (iconId != null) {
            BuiltInRegistries.ITEM.getOptional(iconId).ifPresent(item ->
                graphics.renderItem(new ItemStack(item), 5, 5));
            if (BuiltInRegistries.ITEM.containsKey(iconId)) textX = 26;
        }
        int statusColor = statusColor(entry.status(), accent);
        graphics.drawString(minecraft.font, entry.title(), textX, 6, accent, true);
        if (entry.compact()) {
            graphics.drawString(minecraft.font, compactStatus(entry), textX, height - 11, statusColor, false);
        } else {
            int y = 18;
            graphics.drawString(minecraft.font, detailStatus(entry), 7, y, statusColor, false);
            y += 11;
            if (!entry.session().isBlank()) {
                graphics.drawString(minecraft.font, "Session. " + entry.session(), 7, y, 0xFFBBBBBB, false);
                y += 10;
            }
            if (!entry.successCriteria().isBlank()) {
                graphics.drawString(minecraft.font, "Success. " + entry.successCriteria(), 7, y, 0xFFBBBBBB, false);
                y += 10;
            }
            for (String budget : entry.budgets()) {
                graphics.drawString(minecraft.font, budget, 7, y, 0xFFE0E0E0, false);
                y += 10;
            }
            if (!entry.explanation().isBlank() && !entry.status().equals("active")) {
                graphics.drawString(minecraft.font, entry.explanation(), 7, y, statusColor, false);
            }
        }
        graphics.pose().popPose();
    }

    private static int panelWidth(Minecraft minecraft, Entry entry) {
        int width = Math.max(150, minecraft.font.width(entry.title()) + (entry.icon().isBlank() ? 14 : 34));
        width = Math.max(width, minecraft.font.width(detailStatus(entry)) + 14);
        width = Math.max(width, minecraft.font.width("Session. " + entry.session()) + 14);
        width = Math.max(width, minecraft.font.width("Success. " + entry.successCriteria()) + 14);
        for (String budget : entry.budgets()) width = Math.max(width, minecraft.font.width(budget) + 14);
        if (!entry.explanation().isBlank()) width = Math.max(width, minecraft.font.width(entry.explanation()) + 14);
        return Math.min(320, width);
    }

    private static int panelHeight(Entry entry) {
        if (entry.compact()) return 29;
        int lines = Math.max(1, entry.budgets().size());
        if (!entry.session().isBlank()) lines++;
        if (!entry.successCriteria().isBlank()) lines++;
        if (!entry.explanation().isBlank() && !entry.status().equals("active")) lines++;
        return 31 + lines * 10;
    }

    private static String compactStatus(Entry entry) {
        String timer = timer(entry);
        return entry.status() + ". " + (timer.isBlank() ? step(entry) : timer);
    }

    private static String detailStatus(Entry entry) {
        String timer = timer(entry);
        String suffix = timer.isBlank() ? "" : ". " + timer;
        return capitalize(entry.status()) + ". " + step(entry) + ". Attempt " + entry.attempts() + suffix;
    }

    private static String step(Entry entry) {
        if (entry.totalSteps() <= 0) return "No ordered steps";
        return "Step " + Math.min(entry.currentStep() + 1, entry.totalSteps()) + " of " + entry.totalSteps();
    }

    private static String timer(Entry entry) {
        if (entry.timeoutMillis() <= 0 || !entry.status().equals("active")) return "";
        long remaining = Math.max(0, entry.startedAt() + entry.timeoutMillis() - System.currentTimeMillis());
        return String.format(Locale.ROOT, "%.1fs", remaining / 1000D);
    }

    private static String normalizedPlacement(String placement) {
        if (placement == null) return "top";
        String value = placement.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (value) {
            case "top", "top_left", "top_right", "bottom", "bottom_left", "bottom_right", "center" -> value;
            default -> "top";
        };
    }

    private static int animatedColor(String value, String animation) {
        int color = color(value);
        if (!"pulse".equalsIgnoreCase(animation) && !"flash".equalsIgnoreCase(animation)) return color;
        double speed = "flash".equalsIgnoreCase(animation) ? 8D : 3D;
        double strength = 0.68D + 0.32D * (Math.sin(System.currentTimeMillis() / 1000D * speed) + 1D) / 2D;
        int red = (int) (((color >> 16) & 255) * strength);
        int green = (int) (((color >> 8) & 255) * strength);
        int blue = (int) ((color & 255) * strength);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int color(String value) {
        if (value == null) return 0xFFFFFFFF;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "black" -> 0xFF202020;
            case "red" -> 0xFFFF5555;
            case "green" -> 0xFF55FF55;
            case "blue" -> 0xFF5555FF;
            case "yellow", "gold" -> 0xFFFFD45A;
            case "aqua", "cyan" -> 0xFF55FFFF;
            case "purple", "magenta" -> 0xFFFF55FF;
            case "gray", "grey" -> 0xFFAAAAAA;
            default -> parseHex(value);
        };
    }

    private static int parseHex(String value) {
        String raw = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (raw.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(raw, 16);
            if (raw.length() == 8) return (int) Long.parseLong(raw, 16);
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }

    private static int statusColor(String status, int fallback) {
        return switch (status) {
            case "failed", "expired" -> 0xFFFF5555;
            case "succeeded" -> 0xFF55FF55;
            default -> fallback;
        };
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public record Entry(ResourceLocation id, String title, String status, int currentStep, int totalSteps,
                        int attempts, long startedAt, long timeoutMillis, List<String> budgets,
                        String session, String successCriteria, String explanation,
                        String placement, double scale, String color,
                        String icon, String animation, boolean compact) {
        public Entry {
            budgets = budgets == null ? List.of() : List.copyOf(budgets);
            title = title == null ? id.toString() : title;
            status = status == null ? "inactive" : status;
            session = session == null ? "" : session;
            successCriteria = successCriteria == null ? "" : successCriteria;
            explanation = explanation == null ? "" : explanation;
            placement = placement == null ? "top" : placement;
            color = color == null ? "white" : color;
            icon = icon == null ? "" : icon;
            animation = animation == null ? "none" : animation;
        }
    }
}
