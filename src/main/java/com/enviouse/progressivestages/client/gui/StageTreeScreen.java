package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.ClientTriggerProgress;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementWidgetType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 3.0 progression map. Its interaction and visual language deliberately mirror vanilla's
 * advancement screen: framed icon nodes, elbow connectors, a tiled map, drag-to-pan, wheel
 * scrolling, and hover cards. Clicking a node pins the richer stage inspector (and purchase
 * control) without making map navigation itself mutate server state.
 */
public final class StageTreeScreen extends Screen {

    private static final ResourceLocation WINDOW =
        ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final ResourceLocation TITLE_BOX =
        ResourceLocation.withDefaultNamespace("advancements/title_box");
    private static final ResourceLocation DEFAULT_BACKGROUND =
        ResourceLocation.withDefaultNamespace("textures/block/stone.png");

    private static final int BORDER_X = 9;
    private static final int HEADER_H = 18;
    private static final int BOTTOM_H = 9;
    private static final int NODE = 26;
    private static final int LAYER_X = 84;
    private static final int LANE_Y = 46;

    private int left, top, right, bottom;
    private int mapLeft, mapTop, mapRight, mapBottom;
    private double panX, panY;
    private int minNodeX, minNodeY, maxNodeX, maxNodeY;
    private boolean centered;
    private boolean draggingMap;
    private MapNode pressedNode;
    private double dragDistance;

    private final List<MapNode> nodes = new ArrayList<>();
    private final Map<StageId, MapNode> byId = new HashMap<>();
    private StageId selected;
    private StageId hovered;

    private EditBox searchBox;
    private String filter = "";
    private boolean hideOwned;
    private String categoryFilter = "";
    private final List<String> categories = new ArrayList<>();
    private int categoryX, categoryY, categoryW = 92, categoryH = 13;
    private final Set<StageId> itemFilterMatches = new HashSet<>();
    private int ownedX, ownedY, ownedW = 45, ownedH = 12;
    private int homeX, homeY, homeW = 14, homeH = 12;

    private int panelX, panelY, panelW, panelH;
    private int panelScroll, panelMax;
    private int buyX, buyY, buyW, buyH;
    private boolean buyEnabled;
    private StageId buyStage;

    private record MapNode(StageId id, int x, int y, boolean owned, boolean available) {}

    public StageTreeScreen() {
        super(Component.translatable("gui.progressivestages.tree.title"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof StageTreeScreen current) current.rebuild(false);
            else mc.setScreen(new StageTreeScreen());
        });
    }

    @Override
    protected void init() {
        int w = Math.max(230, Math.min(width - 24, 520));
        int h = Math.max(140, Math.min(height - 38, 300));
        left = (width - w) / 2;
        top = (height - h) / 2;
        right = left + w;
        bottom = top + h;
        mapLeft = left + BORDER_X;
        mapTop = top + HEADER_H;
        mapRight = right - BORDER_X;
        mapBottom = bottom - BOTTOM_H;
        categoryX = mapLeft + 5;
        categoryY = mapTop + 5;

        homeX = right - BORDER_X - homeW;
        homeY = top + 3;
        ownedX = homeX - 4 - ownedW;
        ownedY = top + 3;
        int searchW = Math.min(150, Math.max(68, w / 3));
        int searchX = ownedX - 4 - searchW;
        searchBox = new EditBox(font, searchX, top + 3, searchW, 12,
            Component.translatable("gui.progressivestages.tree.search.label"));
        searchBox.setBordered(true);
        searchBox.setMaxLength(64);
        searchBox.setValue(filter);
        searchBox.setHint(Component.translatable("gui.progressivestages.tree.search.hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(value -> {
            filter = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            recomputeItemFilter();
            rebuild(false);
        });
        addRenderableWidget(searchBox);

        recomputeItemFilter();
        rebuild(true);
    }

    private void recomputeItemFilter() {
        itemFilterMatches.clear();
        if (filter.isEmpty()) return;
        for (ResourceLocation item : ClientLockCache.getAllItemLocks().keySet()) {
            if (!item.toString().contains(filter)) continue;
            itemFilterMatches.addAll(ClientLockCache.getRequiredStagesForItem(item));
        }
    }

    private void rebuild(boolean recenter) {
        nodes.clear();
        byId.clear();
        refreshCategories();

        Set<StageId> layoutIds = new HashSet<>();
        for (StageId id : ClientStageCache.getAllStageDefinitionIds()) {
            if (!ClientStageCache.isHidden(id)) layoutIds.add(id);
        }

        Map<StageId, Integer> depthMemo = new HashMap<>();
        Map<Integer, List<StageId>> layers = new LinkedHashMap<>();
        for (StageId id : layoutIds) {
            int depth = depthOf(id, layoutIds, depthMemo, new HashSet<>());
            layers.computeIfAbsent(depth, ignored -> new ArrayList<>()).add(id);
        }

        Comparator<StageId> order = Comparator
            .comparingInt(ClientStageCache::getUiSortOrder)
            .thenComparing(ClientStageCache::getCategory, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ClientStageCache::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        Map<StageId, int[]> positions = new HashMap<>();
        for (Map.Entry<Integer, List<StageId>> layer : layers.entrySet()) {
            layer.getValue().sort(order);
            for (int lane = 0; lane < layer.getValue().size(); lane++) {
                StageId id = layer.getValue().get(lane);
                int x = ClientStageCache.getUiX(id).orElse(layer.getKey() * LAYER_X);
                int y = ClientStageCache.getUiY(id).orElse(lane * LANE_Y);
                positions.put(id, new int[]{x, y});
            }
        }

        for (StageId id : layoutIds) {
            boolean owned = ClientStageCache.hasStage(id);
            boolean available = !owned && dependenciesSatisfied(id);
            if (!revealed(id, owned, available) || (hideOwned && owned) || !matchesFilter(id)) continue;
            int[] pos = positions.get(id);
            MapNode node = new MapNode(id, pos[0], pos[1], owned, available);
            nodes.add(node);
            byId.put(id, node);
        }
        nodes.sort(Comparator.comparingInt(MapNode::x).thenComparingInt(MapNode::y));

        if (selected != null && !byId.containsKey(selected)) selected = null;
        computeBounds();
        if (recenter || !centered) centerOnBestNode();
        else clampPan();
    }

    private int depthOf(StageId id, Set<StageId> all, Map<StageId, Integer> memo, Set<StageId> visiting) {
        Integer cached = memo.get(id);
        if (cached != null) return cached;
        if (!visiting.add(id)) return 0;
        int depth = 0;
        for (StageId dep : ClientStageCache.getDependencies(id)) {
            if (all.contains(dep)) depth = Math.max(depth, depthOf(dep, all, memo, visiting) + 1);
        }
        visiting.remove(id);
        memo.put(id, depth);
        return depth;
    }

    private boolean revealed(StageId id, boolean owned, boolean available) {
        if (owned) return true;
        return switch (ClientStageCache.getUiReveal(id).toLowerCase(Locale.ROOT)) {
            case "unlocked" -> false;
            case "dependencies" -> available;
            default -> true;
        };
    }

    private boolean matchesFilter(StageId id) {
        if (!categoryFilter.isEmpty()
                && !ClientStageCache.getCategory(id).equalsIgnoreCase(categoryFilter)) return false;
        if (filter.isEmpty()) return true;
        return ClientStageCache.getDisplayName(id).toLowerCase(Locale.ROOT).contains(filter)
            || id.toString().toLowerCase(Locale.ROOT).contains(filter)
            || ClientStageCache.getDescription(id).toLowerCase(Locale.ROOT).contains(filter)
            || ClientStageCache.getCategory(id).toLowerCase(Locale.ROOT).contains(filter)
            || itemFilterMatches.contains(id);
    }

    private void refreshCategories() {
        categories.clear();
        ClientStageCache.getAllStageDefinitionIds().stream()
            .map(ClientStageCache::getCategory).map(String::trim).filter(s -> !s.isEmpty())
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(categories::add);
        if (!categoryFilter.isEmpty()
                && categories.stream().noneMatch(categoryFilter::equalsIgnoreCase)) categoryFilter = "";
    }

    private void cycleCategory(int direction) {
        int current = categoryFilter.isEmpty() ? -1 : categories.indexOf(categoryFilter);
        int slots = categories.size() + 1;
        int next = Math.floorMod(current + 1 + direction, slots) - 1;
        categoryFilter = next < 0 ? "" : categories.get(next);
        selected = null;
        rebuild(true);
    }

    private boolean dependenciesSatisfied(StageId id) {
        int owned = 0;
        for (StageId dependency : ClientStageCache.getDependencies(id)) {
            if (ClientStageCache.hasStage(dependency)) owned++;
        }
        return owned >= ClientStageCache.getDependencyCount(id);
    }

    private void computeBounds() {
        if (nodes.isEmpty()) {
            minNodeX = minNodeY = 0;
            maxNodeX = maxNodeY = NODE;
            return;
        }
        minNodeX = nodes.stream().mapToInt(MapNode::x).min().orElse(0);
        minNodeY = nodes.stream().mapToInt(MapNode::y).min().orElse(0);
        maxNodeX = nodes.stream().mapToInt(n -> n.x() + NODE).max().orElse(NODE);
        maxNodeY = nodes.stream().mapToInt(n -> n.y() + NODE).max().orElse(NODE);
    }

    private void centerOnBestNode() {
        MapNode target = null;
        for (MapNode n : nodes) if (n.available()) { target = n; break; }
        if (target == null) for (MapNode n : nodes) if (!n.owned()) { target = n; break; }
        if (target == null && !nodes.isEmpty()) target = nodes.get(0);
        if (target == null) {
            panX = panY = 0;
        } else {
            panX = (mapRight - mapLeft - NODE) / 2.0 - target.x();
            panY = (mapBottom - mapTop - NODE) / 2.0 - target.y();
        }
        centered = true;
        clampPan();
    }

    private void clampPan() {
        int viewportW = Math.max(1, mapRight - mapLeft);
        int viewportH = Math.max(1, mapBottom - mapTop);
        int contentW = maxNodeX - minNodeX;
        int contentH = maxNodeY - minNodeY;
        if (contentW + 40 <= viewportW) panX = (viewportW - contentW) / 2.0 - minNodeX;
        else panX = Math.max(viewportW - maxNodeX - 24, Math.min(24 - minNodeX, panX));
        if (contentH + 40 <= viewportH) panY = (viewportH - contentH) / 2.0 - minNodeY;
        else panY = Math.max(viewportH - maxNodeY - 24, Math.min(24 - minNodeY, panY));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderMapBackground(g);

        hovered = null;
        g.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
        renderConnections(g);
        renderNodes(g, mouseX, mouseY);
        g.disableScissor();

        if (selected != null) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            renderInspector(g, mouseX, mouseY);
            g.pose().popPose();
        }
        renderWindowFrame(g, mouseX, mouseY);
        // Screen.render would run the blur pass again. Render widgets directly above the completed map.
        for (var renderable : renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }

        if (hovered != null && (selected == null || !insideInspector(mouseX, mouseY))) {
            renderNodeTooltip(g, hovered, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, homeX, homeY, homeW, homeH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.home.tooltip"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, ownedX, ownedY, ownedW, ownedH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.owned.tooltip"), mouseX, mouseY);
        } else if (!categories.isEmpty()
                && inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.category.tooltip"), mouseX, mouseY);
        }
    }

    private void renderMapBackground(GuiGraphics g) {
        ResourceLocation texture = backgroundTexture();
        for (int x = mapLeft; x < mapRight; x += 16) {
            for (int y = mapTop; y < mapBottom; y += 16) {
                int w = Math.min(16, mapRight - x);
                int h = Math.min(16, mapBottom - y);
                g.blit(texture, x, y, 0, 0, w, h, 16, 16);
            }
        }
        g.fill(mapLeft, mapTop, mapRight, mapBottom, 0x66000000);
        if (nodes.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.progressivestages.tree.empty"),
                (mapLeft + mapRight) / 2, (mapTop + mapBottom) / 2 - 4, 0xFFFFFFFF);
        }
    }

    private ResourceLocation backgroundTexture() {
        String configured = selected != null ? ClientStageCache.getUiBackground(selected) : "";
        if (configured.isBlank()) {
            for (MapNode node : nodes) {
                configured = ClientStageCache.getUiBackground(node.id());
                if (!configured.isBlank()) break;
            }
        }
        if (configured.isBlank()) return DEFAULT_BACKGROUND;
        ResourceLocation raw = ResourceLocation.tryParse(configured);
        if (raw == null) return DEFAULT_BACKGROUND;
        String path = raw.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return ResourceLocation.fromNamespaceAndPath(raw.getNamespace(), path);
    }

    private void renderConnections(GuiGraphics g) {
        for (MapNode child : nodes) {
            for (StageId dependency : ClientStageCache.getDependencies(child.id())) {
                MapNode parent = byId.get(dependency);
                if (parent == null) continue;
                int color = child.owned() ? 0xFF55AA55 : child.available() ? 0xFFFFD45A : 0xFFB0B0B0;
                drawConnector(g, screenX(parent) + NODE, screenY(parent) + NODE / 2,
                    screenX(child), screenY(child) + NODE / 2, 0xFF000000, 3);
                drawConnector(g, screenX(parent) + NODE, screenY(parent) + NODE / 2,
                    screenX(child), screenY(child) + NODE / 2, color, 1);
            }
        }
    }

    private void drawConnector(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness) {
        int mid = (x1 + x2) / 2;
        int half = thickness / 2;
        g.fill(Math.min(x1, mid), y1 - half, Math.max(x1, mid) + 1, y1 - half + thickness, color);
        g.fill(mid - half, Math.min(y1, y2), mid - half + thickness, Math.max(y1, y2) + 1, color);
        g.fill(Math.min(mid, x2), y2 - half, Math.max(mid, x2) + 1, y2 - half + thickness, color);
    }

    private void renderNodes(GuiGraphics g, int mouseX, int mouseY) {
        for (MapNode node : nodes) {
            int x = screenX(node), y = screenY(node);
            if (x + NODE < mapLeft || x > mapRight || y + NODE < mapTop || y > mapBottom) continue;
            AdvancementWidgetType state = node.owned() ? AdvancementWidgetType.OBTAINED : AdvancementWidgetType.UNOBTAINED;
            g.blitSprite(state.frameSprite(frameType(node.id())), x, y, NODE, NODE);
            g.renderFakeItem(iconFor(node.id()), x + 5, y + 5);
            if (node.id().equals(selected)) g.renderOutline(x - 2, y - 2, NODE + 4, NODE + 4, 0xFFFFFFFF);
            else if (node.available()) g.renderOutline(x - 1, y - 1, NODE + 2, NODE + 2, 0xFFFFD45A);
            if (mouseX >= x && mouseX <= x + NODE && mouseY >= y && mouseY <= y + NODE
                    && mouseX >= mapLeft && mouseX < mapRight && mouseY >= mapTop && mouseY < mapBottom) {
                hovered = node.id();
            }
        }
    }

    private AdvancementType frameType(StageId id) {
        return switch (ClientStageCache.getUiFrame(id).toLowerCase(Locale.ROOT)) {
            case "challenge" -> AdvancementType.CHALLENGE;
            case "goal" -> AdvancementType.GOAL;
            default -> AdvancementType.TASK;
        };
    }

    private int screenX(MapNode node) { return mapLeft + (int) Math.round(panX) + node.x(); }
    private int screenY(MapNode node) { return mapTop + (int) Math.round(panY) + node.y(); }

    private void renderWindowFrame(GuiGraphics g, int mouseX, int mouseY) {
        // Resize vanilla's 252x140 advancement frame by repeating its center/edge regions while
        // retaining the exact original corners, header, bevel, and palette.
        g.blit(WINDOW, left, top, 0, 0, 0, 9, 18, 256, 256);
        tileHorizontal(g, left + 9, top, right - left - 18, 18, 9, 0, 234);
        g.blit(WINDOW, right - 9, top, 0, 243, 0, 9, 18, 256, 256);
        tileVertical(g, left, top + 18, bottom - top - 27, 9, 0, 18, 113);
        tileVertical(g, right - 9, top + 18, bottom - top - 27, 9, 243, 18, 113);
        g.blit(WINDOW, left, bottom - 9, 0, 0, 131, 9, 9, 256, 256);
        tileHorizontal(g, left + 9, bottom - 9, right - left - 18, 9, 9, 131, 234);
        g.blit(WINDOW, right - 9, bottom - 9, 0, 243, 131, 9, 9, 256, 256);

        int owned = 0;
        int total = 0;
        for (StageId id : ClientStageCache.getAllStageDefinitionIds()) {
            if (ClientStageCache.isHidden(id)) continue;
            total++;
            if (ClientStageCache.hasStage(id)) owned++;
        }
        String title = Component.translatable("gui.progressivestages.tree.title").getString();
        g.drawString(font, title, left + 8, top + 6, 0xFF404040, false);
        if (searchBox.getX() - (left + 8) > font.width(title) + 40) {
            String count = owned + "/" + total;
            g.drawString(font, count, searchBox.getX() - font.width(count) - 5, top + 6, 0xFF606060, false);
        }

        boolean ownedHover = inside(mouseX, mouseY, ownedX, ownedY, ownedW, ownedH);
        g.fill(ownedX, ownedY, ownedX + ownedW, ownedY + ownedH,
            hideOwned ? 0xFF6A8F55 : ownedHover ? 0xFFA0A0A0 : 0xFF777777);
        g.renderOutline(ownedX, ownedY, ownedW, ownedH, 0xFF202020);
        Component ownedLabel = Component.translatable(hideOwned
            ? "gui.progressivestages.tree.owned.hidden"
            : "gui.progressivestages.tree.owned.visible");
        g.drawCenteredString(font, ownedLabel, ownedX + ownedW / 2, ownedY + 2, 0xFFFFFFFF);

        boolean homeHover = inside(mouseX, mouseY, homeX, homeY, homeW, homeH);
        g.fill(homeX, homeY, homeX + homeW, homeY + homeH, homeHover ? 0xFFA0A0A0 : 0xFF777777);
        g.renderOutline(homeX, homeY, homeW, homeH, 0xFF202020);
        g.drawCenteredString(font, "•", homeX + homeW / 2, homeY + 2, 0xFFFFFFFF);

        // Category selector is placed inside the map like vanilla advancement tabs. Left click
        // advances, right click goes back, and C provides a keyboard equivalent.
        if (!categories.isEmpty()) {
            boolean categoryHover = inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH);
            if (categoryHover) hovered = null;
            g.fill(categoryX, categoryY, categoryX + categoryW, categoryY + categoryH,
                categoryHover ? 0xEEA0A0A0 : 0xEE666666);
            g.renderOutline(categoryX, categoryY, categoryW, categoryH, 0xFF202020);
            String raw = categoryFilter.isEmpty()
                ? Component.translatable("gui.progressivestages.tree.category.all").getString()
                : categoryFilter;
            String label = font.plainSubstrByWidth(raw, categoryW - 10);
            g.drawCenteredString(font, label, categoryX + categoryW / 2, categoryY + 3, 0xFFFFFFFF);
        }
    }

    private void tileHorizontal(GuiGraphics g, int x, int y, int width, int height, int u, int v, int sourceWidth) {
        for (int done = 0; done < width;) {
            int take = Math.min(sourceWidth, width - done);
            g.blit(WINDOW, x + done, y, 0, u, v, take, height, 256, 256);
            done += take;
        }
    }

    private void tileVertical(GuiGraphics g, int x, int y, int height, int width, int u, int v, int sourceHeight) {
        for (int done = 0; done < height;) {
            int take = Math.min(sourceHeight, height - done);
            g.blit(WINDOW, x, y + done, 0, u, v, width, take, 256, 256);
            done += take;
        }
    }

    private void renderNodeTooltip(GuiGraphics g, StageId id, int mouseX, int mouseY) {
        MapNode node = byId.get(id);
        if (node == null) return;
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(ClientStageCache.getDisplayName(id)).withStyle(
            node.owned() ? ChatFormatting.GREEN : node.available() ? ChatFormatting.YELLOW : ChatFormatting.RED,
            ChatFormatting.BOLD).getVisualOrderText());
        lines.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        Component status = Component.translatable(node.owned()
            ? "gui.progressivestages.tree.status.unlocked"
            : node.available() ? "gui.progressivestages.tree.status.ready"
            : "gui.progressivestages.tree.status.locked");
        lines.add(status.copy().withStyle(node.owned() ? ChatFormatting.GREEN
            : node.available() ? ChatFormatting.YELLOW : ChatFormatting.RED).getVisualOrderText());
        String category = ClientStageCache.getCategory(id);
        if (!category.isBlank()) lines.add(Component.literal(category).withStyle(ChatFormatting.DARK_AQUA).getVisualOrderText());
        ClientTriggerProgress.StageData data = ClientTriggerProgress.get(id);
        if (data.hasTriggers()) {
            lines.add(Component.translatable("gui.progressivestages.tree.progress.percent",
                    Math.round(Math.max(0, data.percent()) * 100))
                .withStyle(ChatFormatting.AQUA).getVisualOrderText());
        }
        String description = ClientStageCache.getDescription(id);
        if (!description.isBlank()) lines.addAll(font.split(
            TextUtil.parseColorCodes("&7" + description), 210));
        lines.add(Component.translatable("gui.progressivestages.tree.details")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
            .getVisualOrderText());
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    private void renderInspector(GuiGraphics g, int mouseX, int mouseY) {
        MapNode node = byId.get(selected);
        if (node == null) return;
        panelW = Math.min(208, Math.max(160, mapRight - mapLeft - 20));
        panelH = Math.max(90, mapBottom - mapTop - 12);
        panelX = mapRight - panelW - 6;
        panelY = mapTop + 6;
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xEE000000);
        g.blitSprite(TITLE_BOX, panelX, panelY, panelW, panelH);

        int x = panelX + 7;
        int contentTop = panelY + 28;
        ClientTriggerProgress.StageData data = ClientTriggerProgress.get(selected);
        boolean showBuy = data.purchasable() && !node.owned();
        int contentBottom = panelY + panelH - 7 - (showBuy ? 20 : 0);
        int innerW = panelW - 14;

        g.renderFakeItem(iconFor(selected), x, panelY + 6);
        int nameColor = stageColorOr(selected, node.owned() ? 0xFF55AA55 : node.available() ? 0xFFFFD45A : 0xFFFF5555);
        g.drawString(font, Component.literal(ClientStageCache.getDisplayName(selected)).withStyle(ChatFormatting.BOLD),
            x + 21, panelY + 7, nameColor, false);
        Component status = Component.translatable(node.owned()
            ? "gui.progressivestages.tree.status.unlocked"
            : node.available() ? "gui.progressivestages.tree.status.ready"
            : "gui.progressivestages.tree.status.locked");
        g.drawString(font, status,
            x + 21, panelY + 17, 0xFF606060, false);
        g.drawString(font, "×", panelX + panelW - 12, panelY + 7,
            inside(mouseX, mouseY, panelX + panelW - 15, panelY + 4, 12, 12) ? 0xFFFF5555 : 0xFF606060, false);

        g.enableScissor(panelX + 3, contentTop, panelX + panelW - 3, contentBottom);
        int y = contentTop - panelScroll;
        String description = ClientStageCache.getDescription(selected);
        if (!description.isBlank()) {
            for (FormattedCharSequence line : font.split(TextUtil.parseColorCodes("&7" + description), innerW)) {
                g.drawString(font, line, x, y, 0xFFFFFFFF, false);
                y += 10;
            }
            y += 4;
        }

        List<StageId> dependencies = ClientStageCache.getDependencies(selected);
        if (!dependencies.isEmpty()) {
            String mode = ClientStageCache.getDependencyMode(selected);
            Component prerequisiteTitle = switch (mode) {
                case "any" -> Component.translatable("gui.progressivestages.tree.prerequisites.any");
                case "at_least" -> Component.translatable("gui.progressivestages.tree.prerequisites.count",
                    ClientStageCache.getDependencyCount(selected));
                default -> Component.translatable("gui.progressivestages.tree.prerequisites.all");
            };
            g.drawString(font, prerequisiteTitle, x, y, 0xFFAAAAAA, false);
            y += 11;
            for (StageId dependency : dependencies) {
                boolean has = ClientStageCache.hasStage(dependency);
                g.drawString(font, (has ? "✔ " : "✗ ") + ClientStageCache.getDisplayName(dependency), x + 3, y,
                    has ? 0xFF55AA55 : 0xFFFF5555, false);
                y += 10;
            }
            y += 3;
        }

        if (data.hasTriggers()) {
            float pct = Math.max(0, data.percent());
            g.drawString(font, Component.translatable("gui.progressivestages.tree.triggers"),
                x, y, 0xFFFFD45A, false);
            y += 11;
            g.drawString(font, Component.translatable("gui.progressivestages.tree.progress.percent",
                Math.round(pct * 100)), x, y, 0xFF55FFFF, false);
            y += 11;
            drawProgressBar(g, x, y, innerW, pct);
            y += 9;
            int route = 1;
            for (ClientTriggerProgress.Rule rule : data.rules()) {
                Component routeLabel = Component.translatable(
                    "any_of".equals(rule.mode())
                        ? "gui.progressivestages.tree.trigger.any"
                        : "gui.progressivestages.tree.trigger.all",
                    route++);
                g.drawString(font, (rule.satisfied() ? "✔ " : "• ") + routeLabel.getString(), x + 2, y,
                    rule.satisfied() ? 0xFF55AA55 : 0xFFAAAAAA, false);
                y += 10;
                if (!rule.description().isBlank()) {
                    for (FormattedCharSequence wrapped : font.split(
                            Component.literal(rule.description()), innerW - 7)) {
                        g.drawString(font, wrapped, x + 5, y, 0xFFCCCCCC, false);
                        y += 10;
                    }
                }
                for (ClientTriggerProgress.Cond condition : rule.conditions()) {
                    String line = (condition.satisfied() ? "✔ " : "✗ ") + condition.label()
                        + " " + Math.min(condition.current(), condition.threshold()) + "/" + condition.threshold();
                    for (FormattedCharSequence wrapped : font.split(Component.literal(line), innerW - 5)) {
                        g.drawString(font, wrapped, x + 5, y, condition.satisfied() ? 0xFF55AA55 : 0xFFCCCCCC, false);
                        y += 10;
                    }
                }
            }
            y += 3;
        }

        if (!data.challenges().isEmpty()) {
            g.drawString(font, Component.translatable("gui.progressivestages.tree.challenges"), x, y,
                0xFFFFAA55, false);
            y += 11;
            for (ClientTriggerProgress.Challenge challenge : data.challenges()) {
                g.drawString(font, challenge.id().getPath() + ". " + challenge.status(), x + 3, y,
                    challenge.status().equals("succeeded") ? 0xFF55AA55 : 0xFFDDCC88, false);
                y += 10;
                for (String budget : challenge.budgets()) {
                    g.drawString(font, "  " + budget, x + 3, y, 0xFFCCCCCC, false);
                    y += 10;
                }
            }
            y += 3;
        }

        if (!data.modifiers().isEmpty()) {
            g.drawString(font, Component.translatable("gui.progressivestages.tree.modifiers"), x, y,
                0xFFAA88FF, false);
            y += 11;
            for (String modifier : data.modifiers()) {
                for (FormattedCharSequence wrapped : font.split(Component.literal(modifier), innerW - 5)) {
                    g.drawString(font, wrapped, x + 3, y, 0xFFCCCCCC, false);
                    y += 10;
                }
            }
            y += 3;
        }

        if (!data.why().isEmpty()) {
            g.drawString(font, Component.translatable("gui.progressivestages.tree.why"), x, y,
                0xFF55DDDD, false);
            y += 11;
            for (ClientTriggerProgress.Why why : data.why().stream()
                    .skip(Math.max(0, data.why().size() - 5)).toList()) {
                String line = why.effect() + ". " + why.category() + ". " + why.target();
                for (FormattedCharSequence wrapped : font.split(Component.literal(line), innerW - 5)) {
                    g.drawString(font, wrapped, x + 3, y, why.blocked() ? 0xFFFF7777 : 0xFF77DD77, false);
                    y += 10;
                }
            }
            y += 3;
        }

        if (!data.history().isEmpty()) {
            g.drawString(font, Component.translatable("gui.progressivestages.tree.history"), x, y,
                0xFFAAAAAA, false);
            y += 11;
            for (ClientTriggerProgress.History history : data.history().stream()
                    .skip(Math.max(0, data.history().size() - 5)).toList()) {
                String line = history.direction() + ". " + (history.committed() ? "committed" : "rejected");
                g.drawString(font, line, x + 3, y, history.committed() ? 0xFF77DD77 : 0xFFFF7777, false);
                y += 10;
            }
            y += 3;
        }

        if (data.unlockTotal() > 0) {
            g.drawString(font, Component.translatable("gui.progressivestages.tree.unlocks",
                data.unlockTotal()), x, y, 0xFFAAAAAA, false);
            y += 11;
            int columns = Math.max(1, innerW / 18);
            for (int i = 0; i < data.unlockSample().size(); i++) {
                ResourceLocation itemId = data.unlockSample().get(i);
                ItemStack stack = BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY);
                g.renderItem(stack, x + (i % columns) * 18, y + (i / columns) * 18);
            }
            y += ((data.unlockSample().size() + columns - 1) / columns) * 18 + 3;
        }
        g.disableScissor();

        int contentHeight = y + panelScroll - contentTop;
        panelMax = Math.max(0, contentHeight - (contentBottom - contentTop));
        panelScroll = Math.max(0, Math.min(panelScroll, panelMax));
        if (panelMax > 0) {
            int track = contentBottom - contentTop;
            int thumb = Math.max(12, track * track / (track + panelMax));
            int thumbY = contentTop + (track - thumb) * panelScroll / panelMax;
            g.fill(panelX + panelW - 3, thumbY, panelX + panelW - 1, thumbY + thumb, 0xFFAAAAAA);
        }

        buyEnabled = false;
        buyStage = null;
        if (showBuy) {
            buyX = x;
            buyY = panelY + panelH - 20;
            buyW = innerW;
            buyH = 14;
            buyStage = selected;
            buyEnabled = data.canPurchase();
            boolean hover = buyEnabled && inside(mouseX, mouseY, buyX, buyY, buyW, buyH);
            g.fill(buyX, buyY, buyX + buyW, buyY + buyH,
                buyEnabled ? hover ? 0xFF6A9F55 : 0xFF4F7F3F : 0xFF5A4545);
            g.renderOutline(buyX, buyY, buyW, buyH, 0xFF202020);
            Component label = Component.translatable(buyEnabled
                ? "gui.progressivestages.tree.purchase"
                : "gui.progressivestages.tree.purchase.need", data.costSummary());
            g.drawCenteredString(font, label, buyX + buyW / 2, buyY + 3, 0xFFFFFFFF);
        }
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int width, float fraction) {
        g.fill(x, y, x + width, y + 6, 0xFF000000);
        int fill = Math.round((width - 2) * Math.max(0, Math.min(1, fraction)));
        g.fill(x + 1, y + 1, x + 1 + fill, y + 5, fraction >= 1 ? 0xFF55AA55 : 0xFF55AADD);
    }

    private int stageColorOr(StageId id, int fallback) {
        String color = ClientStageCache.getColor(id);
        if (color.startsWith("#") && color.length() == 7) {
            try { return 0xFF000000 | Integer.parseInt(color.substring(1), 16); }
            catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private ItemStack iconFor(StageId id) {
        return ClientStageCache.getIcon(id)
            .flatMap(BuiltInRegistries.ITEM::getOptional)
            .map(ItemStack::new)
            .orElseGet(() -> new ItemStack(Items.BOOK));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && searchBox != null
                && !inside(mouseX, mouseY, searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight())) {
            searchBox.setFocused(false);
        }
        if (button == 0 && inside(mouseX, mouseY, ownedX, ownedY, ownedW, ownedH)) {
            hideOwned = !hideOwned;
            rebuild(false);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, homeX, homeY, homeW, homeH)) {
            centerOnBestNode();
            return true;
        }
        if ((button == 0 || button == 1) && !categories.isEmpty()
                && inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH)) {
            cycleCategory(button == 0 ? 1 : -1);
            return true;
        }
        if (selected != null) {
            if (button == 0 && inside(mouseX, mouseY, panelX + panelW - 15, panelY + 4, 12, 12)) {
                selected = null;
                return true;
            }
            if (button == 0 && buyEnabled && buyStage != null && inside(mouseX, mouseY, buyX, buyY, buyW, buyH)) {
                ClientTriggerProgress.requestPurchase(buyStage);
                return true;
            }
            if (insideInspector(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, mapLeft, mapTop, mapRight - mapLeft, mapBottom - mapTop)) {
            if (button == 0) {
                pressedNode = nodeAt(mouseX, mouseY);
                draggingMap = true;
                dragDistance = 0.0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingMap) {
            dragDistance += Math.hypot(dragX, dragY);
            if (dragDistance >= 2.0) {
                panX += dragX;
                panY += dragY;
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingMap) {
            if (dragDistance < 2.0 && pressedNode != null) {
                selected = pressedNode.id();
                panelScroll = 0;
            }
            draggingMap = false;
            pressedNode = null;
            dragDistance = 0.0;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selected != null && insideInspector(mouseX, mouseY) && panelMax > 0) {
            panelScroll = (int) Math.max(0, Math.min(panelMax, panelScroll - scrollY * 12));
            return true;
        }
        if (inside(mouseX, mouseY, mapLeft, mapTop, mapRight - mapLeft, mapBottom - mapTop)) {
            if (hasShiftDown() || scrollX != 0) panX += (scrollX != 0 ? scrollX : scrollY) * 16;
            else panY += scrollY * 16;
            clampPan();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let the focused search field consume letters, arrows, home/end, and editing shortcuts.
        // Without this guard, WASD map navigation makes several search terms impossible to type.
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        double dx = 0, dy = 0;
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) dx = 16;
        else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) dx = -16;
        else if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) dy = 16;
        else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) dy = -16;
        else if (keyCode == GLFW.GLFW_KEY_C) {
            if (!categories.isEmpty()) cycleCategory(hasShiftDown() ? -1 : 1);
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            return true;
        }
        if (dx != 0 || dy != 0) {
            panX += dx;
            panY += dy;
            clampPan();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private MapNode nodeAt(double mouseX, double mouseY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            MapNode node = nodes.get(i);
            if (inside(mouseX, mouseY, screenX(node), screenY(node), NODE, NODE)) return node;
        }
        return null;
    }

    private boolean insideInspector(double x, double y) {
        return selected != null && inside(x, y, panelX, panelY, panelW, panelH);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
