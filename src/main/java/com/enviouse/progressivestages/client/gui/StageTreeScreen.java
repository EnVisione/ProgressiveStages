package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.client.ClientLockCache;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.ClientTriggerProgress;
import com.enviouse.progressivestages.client.GuideRequirementSummary;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageGuideTemplate;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementWidgetType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
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
import java.util.Objects;
import java.util.Set;

/**
 * The progression map uses vanilla advancement visuals.
 * Dragging pans the map, the mouse wheel zooms around the pointer, and clicking opens details.
 */
public final class StageTreeScreen extends Screen {

    private static final ResourceLocation WINDOW =
        ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    private static final ResourceLocation TITLE_BOX =
        ResourceLocation.withDefaultNamespace("advancements/title_box");
    private static final ResourceLocation DEFAULT_BACKGROUND =
        ResourceLocation.withDefaultNamespace("textures/block/stone.png");
    private static final ResourceLocation BUTTON =
        ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED =
        ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED =
        ResourceLocation.withDefaultNamespace("widget/button_disabled");

    private static final int BORDER_X = 9;
    private static final int HEADER_H = 18;
    private static final int BOTTOM_H = 9;
    private static final int NODE = 26;
    private static final int GOLD = 0xFFFFC74A;
    private static final int GREEN = 0xFF55AA55;
    private static final int RED = 0xFFFF5555;
    private static final String[] HELP_CONTROL_ICON = {
        "..###..",
        ".#...#.",
        ".....#.",
        "....#..",
        "...#...",
        "...#...",
        ".......",
        "...#...",
        "......."
    };
    private static final String[] CLOSE_CONTROL_ICON = {
        "#.....#",
        ".#...#.",
        "..#.#..",
        "...#...",
        "..#.#..",
        ".#...#.",
        "#.....#"
    };
    private static final int TEXT_MUTED = 0xFFB7B7B7;

    private int left, top, right, bottom;
    private int mapLeft, mapTop, mapRight, mapBottom;
    private double panX, panY;
    private double zoom = 1.0D;
    private int minNodeX, minNodeY, maxNodeX, maxNodeY;
    private boolean centered;
    private boolean draggingMap;
    private MapNode pressedNode;
    private double dragDistance;

    private final List<MapNode> nodes = new ArrayList<>();
    private final Map<StageId, MapNode> byId = new HashMap<>();
    private final Set<StageId> focusedPath = new HashSet<>();
    private StageId pathFocus;
    private StageId selected;
    private StageId hovered;
    private long mapHintUntil;

    private EditBox searchBox;
    private String filter = "";
    private boolean hideOwned;
    private String categoryFilter = "";
    private final List<String> categories = new ArrayList<>();
    private boolean categoryOpen;
    private int categoryScroll;
    private int categoryX, categoryY, categoryW = 112, categoryH = 13;
    private int categoryMenuX, categoryMenuY, categoryMenuW, categoryMenuH;
    private static final int CATEGORY_ROW_H = 14;
    private static final int CATEGORY_VISIBLE_ROWS = 8;
    private final Set<StageId> itemFilterMatches = new HashSet<>();
    private int ownedX, ownedY, ownedW = 45, ownedH = 12;
    private int homeX, homeY, homeW = 14, homeH = 12;
    private boolean guideOpenRequested;
    private boolean unlockHelpOpen;
    private int keyboardControlFocus = -1;
    private int helpX, helpY, helpW, helpH;
    private final List<HoverHint> hoverHints = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private int panelScroll, panelMax;
    private int panelScrollTrackX, panelScrollTrackY, panelScrollTrackHeight;
    private int panelScrollThumbY, panelScrollThumbHeight;
    private boolean draggingPanelScrollbar;
    private int panelScrollbarDragOffset;
    private int buyX, buyY, buyW, buyH;
    private boolean buyEnabled;
    private StageId buyStage;
    private final Map<String, List<ItemStack>> selectorIconCache = new HashMap<>();
    private long stageRevision = ClientStageCache.revision();

    private record MapNode(StageId id, int x, int y, boolean owned, boolean available) {}
    private record HoverHint(int x, int y, int width, int height, Component text) {}
    private record PreviewRow(ItemStack icon, List<FormattedCharSequence> lines, int height) {}

    public StageTreeScreen() {
        super(Component.translatable("gui.progressivestages.tree.title"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof StageTreeScreen current) {
                current.selected = null;
                current.panelScroll = 0;
                current.categoryOpen = false;
                current.unlockHelpOpen = false;
                current.keyboardControlFocus = -1;
                if (current.searchBox != null) current.searchBox.visible = true;
                current.rebuild(false);
            }
            else mc.setScreen(new StageTreeScreen());
        });
    }

    public static void openGuide() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!ClientStageCache.isStageGuideEnabled()) return;
            if (mc.screen instanceof StageTreeScreen current) {
                current.selectGuideStage();
                current.panelScroll = 0;
                current.categoryOpen = false;
            } else {
                StageTreeScreen screen = new StageTreeScreen();
                screen.guideOpenRequested = true;
                mc.setScreen(screen);
            }
        });
    }

    public static void refreshIfOpen() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.screen instanceof StageTreeScreen current) current.rebuild(false);
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (!ClientStageCache.isStageGuideVisible(selected)) unlockHelpOpen = false;
        if (selected != null && ClientStageCache.hasStage(selected)) unlockHelpOpen = false;
        long revision = ClientStageCache.revision();
        if (stageRevision == revision) return;
        stageRevision = revision;
        selectorIconCache.clear();
        recomputeItemFilter();
        rebuild(false);
        ClientTriggerProgress.refreshFromServer();
        if (guideOpenRequested) selectGuideStage();
    }

    @Override
    protected void init() {
        int w = Math.max(230, Math.min(width - 24, 460));
        int h = Math.max(140, Math.min(height - 38, 250));
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
            String nextFilter = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!filter.equals(nextFilter)) closeUnlockHelp();
            filter = nextFilter;
            recomputeItemFilter();
            rebuild(false);
        });
        searchBox.visible = true;
        addRenderableWidget(searchBox);

        recomputeItemFilter();
        rebuild(true);
        if (guideOpenRequested) selectGuideStage();
        mapHintUntil = Util.getMillis() + 5000L;
    }

    private void selectGuideStage() {
        guideOpenRequested = false;
        selected = guideStages().stream().findFirst().orElse(null);
        unlockHelpOpen = selected != null && canShowUnlockHelp(selected);
        keyboardControlFocus = unlockHelpOpen ? 0 : -1;
        panelScroll = 0;
        if (selected != null) rebuild(false);
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
        int maxDepth = layers.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int widestLayer = layers.values().stream().mapToInt(List::size).max().orElse(1);
        for (Map.Entry<Integer, List<StageId>> layer : layers.entrySet()) {
            layer.getValue().sort(order);
            for (int lane = 0; lane < layer.getValue().size(); lane++) {
                StageId id = layer.getValue().get(lane);
                StageTreeLayout.Position automatic = StageTreeLayout.automaticPosition(
                    layer.getKey(), maxDepth, lane, layer.getValue().size(), widestLayer);
                int x = ClientStageCache.getUiX(id).orElse(automatic.x());
                int y = ClientStageCache.getUiY(id).orElse(automatic.y());
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

        if (selected != null && !byId.containsKey(selected)) {
            selected = null;
            unlockHelpOpen = false;
            keyboardControlFocus = -1;
        }
        pathFocus = null;
        focusedPath.clear();
        computeBounds();
        if (recenter || !centered) centerGraph();
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
            .filter(this::categoryVisible)
            .map(ClientStageCache::getCategory).map(String::trim).filter(s -> !s.isEmpty())
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(categories::add);
        if (!categoryFilter.isEmpty()
                && categories.stream().noneMatch(categoryFilter::equalsIgnoreCase)) categoryFilter = "";
    }

    private boolean categoryVisible(StageId id) {
        return visibleToViewer(id);
    }

    private boolean visibleToViewer(StageId id) {
        if (id == null || ClientStageCache.isHidden(id)) return false;
        boolean owned = ClientStageCache.hasStage(id);
        return revealed(id, owned, !owned && dependenciesSatisfied(id));
    }

    private void closeUnlockHelp() {
        unlockHelpOpen = false;
        keyboardControlFocus = -1;
        panelScroll = 0;
    }

    private void selectCategory(int index) {
        categoryFilter = index <= 0 ? "" : categories.get(index - 1);
        categoryOpen = false;
        selected = null;
        closeUnlockHelp();
        playButtonSound();
        rebuild(true);
        mapHintUntil = Util.getMillis() + 2500L;
    }

    private int categoryOptionCount() {
        return categories.size() + 1;
    }

    private int categoryMenuIndexAt(double mouseX, double mouseY) {
        if (!categoryOpen || !inside(mouseX, mouseY, categoryMenuX, categoryMenuY,
                categoryMenuW, categoryMenuH)) return -1;
        int row = ((int) mouseY - categoryMenuY - 1) / CATEGORY_ROW_H;
        int index = categoryScroll + row;
        return index >= 0 && index < categoryOptionCount() ? index : -1;
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

    private void centerGraph() {
        if (nodes.isEmpty()) {
            panX = panY = 0;
            zoom = 1.0D;
        } else {
            double minimumCenterX = minNodeX + NODE / 2.0D;
            double maximumCenterX = maxNodeX - NODE / 2.0D;
            double minimumCenterY = minNodeY + NODE / 2.0D;
            double maximumCenterY = maxNodeY - NODE / 2.0D;
            panX = -(minimumCenterX + maximumCenterX) / 2.0D;
            panY = -(minimumCenterY + maximumCenterY) / 2.0D;
            zoom = StageTreeViewport.fitZoom(
                mapRight - mapLeft,
                mapBottom - mapTop,
                minimumCenterX,
                maximumCenterX,
                minimumCenterY,
                maximumCenterY);
        }
        centered = true;
        clampPan();
    }

    private void clampPan() {
        int viewportW = Math.max(1, mapRight - mapLeft);
        int viewportH = Math.max(1, mapBottom - mapTop);
        panX = StageTreeViewport.clampPan(
            panX, viewportW, zoom, minNodeX + NODE / 2.0D, maxNodeX - NODE / 2.0D);
        panY = StageTreeViewport.clampPan(
            panY, viewportH, zoom, minNodeY + NODE / 2.0D, maxNodeY - NODE / 2.0D);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoverHints.clear();
        renderBackground(g, mouseX, mouseY, partialTick);
        renderMapBackground(g);

        MapNode hoveredNode = inside(mouseX, mouseY, mapLeft, mapTop, mapRight - mapLeft, mapBottom - mapTop)
            ? nodeAt(mouseX, mouseY)
            : null;
        hovered = hoveredNode != null ? hoveredNode.id() : null;
        refreshFocusedPath(hovered != null ? hovered : selected);
        g.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
        renderConnections(g);
        renderNodes(g);
        g.disableScissor();
        renderMapHud(g);

        if (selected != null) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            renderInspector(g, mouseX, mouseY);
            g.pose().popPose();
        }
        renderWindowFrame(g, mouseX, mouseY);
        // Screen.render would run the blur pass again. Render widgets directly above the completed map.
        for (var renderable : renderables) {
            if (renderable == searchBox && (searchBox == null || !searchBox.visible)) continue;
            renderable.render(g, mouseX, mouseY, partialTick);
        }

        if (selected != null && renderInspectorTooltip(g, mouseX, mouseY)) {
            return;
        }
        if (hovered != null && (selected == null || !insideInspector(mouseX, mouseY))) {
            renderNodeTooltip(g, hovered, mouseX, mouseY);
        } else if (inside(mouseX, mouseY, homeX, homeY, homeW, homeH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.home.tooltip"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, ownedX, ownedY, ownedW, ownedH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.owned.tooltip"), mouseX, mouseY);
        } else if (!categories.isEmpty() && !categoryOpen
                && inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.category.tooltip"), mouseX, mouseY);
        } else if (searchBox != null && searchBox.visible
                && inside(mouseX, mouseY, searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight())) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.search.tooltip"), mouseX, mouseY);
        }
    }

    private List<StageId> guideStages() {
        List<StageId> result = new ArrayList<>();
        for (StageId id : ClientStageCache.getAllStageDefinitionIds()) {
            if (ClientStageCache.isHidden(id) || ClientStageCache.hasStage(id)) continue;
            var definition = ClientStageCache.getStageDefinition(id).orElse(null);
            if (definition == null || !ClientStageCache.isStageGuideVisible(id)
                    || "exclude".equalsIgnoreCase(definition.recommendation())) continue;
            if ("unlocked".equalsIgnoreCase(definition.uiReveal())) continue;
            if ("dependencies".equalsIgnoreCase(definition.uiReveal()) && !dependenciesSatisfied(id)) continue;
            if (!dependenciesSatisfied(id)) continue;
            boolean hasGuideText = !definition.howToUnlock().isBlank()
                || !definition.nextSteps().isBlank() || !definition.whereToFind().isBlank();
            if ("auto".equalsIgnoreCase(definition.recommendation())
                    && !definition.hasTriggers() && !hasGuideText) continue;
            result.add(id);
        }
        result.sort(Comparator.comparingInt(ClientStageCache::getUiSortOrder)
            .thenComparing(ClientStageCache::getCategory, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ClientStageCache::getDisplayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(StageId::toString, String.CASE_INSENSITIVE_ORDER));
        return result;
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
        g.fill(mapLeft, mapTop, mapRight, mapTop + 5, 0x33000000);
        g.fill(mapLeft, mapBottom - 5, mapRight, mapBottom, 0x33000000);
        g.fill(mapLeft, mapTop, mapLeft + 4, mapBottom, 0x22000000);
        g.fill(mapRight - 4, mapTop, mapRight, mapBottom, 0x22000000);
        if (nodes.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.progressivestages.tree.empty"),
                (mapLeft + mapRight) / 2, (mapTop + mapBottom) / 2 - 4, 0xFFFFFFFF);
        }
    }

    private void renderMapHud(GuiGraphics g) {
        long remaining = mapHintUntil - Util.getMillis();
        if (remaining <= 0L) return;
        int alpha = remaining >= 500L ? 230 : Math.max(0, (int) (remaining * 230L / 500L));
        Component label = Component.translatable(
            "gui.progressivestages.tree.zoom", Math.round(zoom * 100.0D));
        int labelWidth = font.width(label);
        int x = mapLeft + 6;
        int y = mapBottom - font.lineHeight - 6;
        fillPixelRounded(g, x - 3, y - 2, labelWidth + 6, font.lineHeight + 4,
            withAlpha(0xFF000000, alpha * 3 / 5));
        g.drawString(font, label, x, y, withAlpha(0xFFFFFFFF, alpha), false);

        Component navigation = Component.translatable("gui.progressivestages.tree.navigation_hint");
        int navigationWidth = font.width(navigation);
        int navigationX = mapRight - navigationWidth - 6;
        if (navigationX > x + labelWidth + 12) {
            fillPixelRounded(g, navigationX - 3, y - 2, navigationWidth + 6, font.lineHeight + 4,
                withAlpha(0xFF000000, alpha * 3 / 5));
            g.drawString(font, navigation, navigationX, y, withAlpha(0xFFFFFFFF, alpha), false);
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
        boolean hasFocus = pathFocus != null;
        renderConnectionPass(g, false, hasFocus);
        if (hasFocus) renderConnectionPass(g, true, true);
    }

    private void renderConnectionPass(GuiGraphics g, boolean emphasized, boolean hasFocus) {
        for (MapNode child : nodes) {
            for (StageId dependency : ClientStageCache.getDependencies(child.id())) {
                MapNode parent = byId.get(dependency);
                if (parent == null) continue;
                boolean focused = hasFocus
                    && focusedPath.contains(parent.id())
                    && focusedPath.contains(child.id());
                if (hasFocus && focused != emphasized) continue;

                int x1 = screenX(parent) + NODE;
                int y1 = screenY(parent) + NODE / 2;
                int x2 = screenX(child);
                int y2 = screenY(child) + NODE / 2;
                int stateColor = child.owned() ? GREEN : child.available() ? GOLD : 0xFF9A9A9A;
                if (hasFocus && !focused) {
                    drawConnector(g, x1, y1, x2, y2, withAlpha(stateColor, 52), 1);
                } else {
                    drawConnector(g, x1, y1, x2, y2, hasFocus ? 0xE6000000 : 0xB8000000, 3);
                    int alpha = child.owned() || child.available() ? 255 : hasFocus ? 210 : 135;
                    drawConnector(g, x1, y1, x2, y2, withAlpha(stateColor, alpha), 1);
                }
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

    private void renderNodes(GuiGraphics g) {
        int pulseAlpha = 205 + (int) Math.round(Math.sin(Util.getMillis() / 260.0D) * 35.0D);
        for (MapNode node : nodes) {
            int x = screenX(node), y = screenY(node);
            if (x + NODE < mapLeft || x > mapRight || y + NODE < mapTop || y > mapBottom) continue;
            AdvancementWidgetType state = node.owned() ? AdvancementWidgetType.OBTAINED : AdvancementWidgetType.UNOBTAINED;
            g.blitSprite(state.frameSprite(frameType(node.id())), x, y, NODE, NODE);
            g.renderFakeItem(iconFor(node.id()), x + 5, y + 5);

            boolean dimmed = pathFocus != null && !focusedPath.contains(node.id());
            if (dimmed) {
                fillPixelRounded(g, x + 1, y + 1, NODE - 2, NODE - 2, 0x72000000);
            }

            int accent = stageColorOr(node.id(), GOLD);
            if (node.id().equals(selected)) {
                renderPixelRoundedOutline(g, x - 3, y - 3, NODE + 6, NODE + 6, 0xD9000000);
                renderPixelRoundedOutline(g, x - 2, y - 2, NODE + 4, NODE + 4, accent);
            } else if (node.id().equals(hovered)) {
                renderPixelRoundedOutline(g, x - 2, y - 2, NODE + 4, NODE + 4, 0xEEFFFFFF);
            } else if (node.available() && !dimmed) {
                renderCornerBrackets(g, x - 1, y - 1, NODE + 2, NODE + 2,
                    withAlpha(accent, pulseAlpha));
            }
        }
    }

    private void refreshFocusedPath(StageId focus) {
        if (Objects.equals(pathFocus, focus)) return;
        pathFocus = focus;
        focusedPath.clear();
        if (focus != null) {
            focusedPath.addAll(StageTreeFocus.branch(
                focus, byId.keySet(), ClientStageCache::getDependencies));
        }
    }

    private AdvancementType frameType(StageId id) {
        return switch (ClientStageCache.getUiFrame(id).toLowerCase(Locale.ROOT)) {
            case "challenge" -> AdvancementType.CHALLENGE;
            case "goal" -> AdvancementType.GOAL;
            default -> AdvancementType.TASK;
        };
    }

    private int screenX(MapNode node) {
        return StageTreeViewport.nodeTopLeft(
            (mapLeft + mapRight) / 2, node.x() + NODE / 2.0D, panX, zoom, NODE);
    }

    private int screenY(MapNode node) {
        return StageTreeViewport.nodeTopLeft(
            (mapTop + mapBottom) / 2, node.y() + NODE / 2.0D, panY, zoom, NODE);
    }

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
        renderControl(g, ownedX, ownedY, ownedW, ownedH, ownedHover, true, hideOwned);
        Component ownedLabel = Component.translatable(hideOwned
            ? "gui.progressivestages.tree.owned.hidden"
            : "gui.progressivestages.tree.owned.visible");
        g.drawCenteredString(font, ownedLabel, ownedX + ownedW / 2, ownedY + 2, 0xFFFFFFFF);

        boolean homeHover = inside(mouseX, mouseY, homeX, homeY, homeW, homeH);
        renderControl(g, homeX, homeY, homeW, homeH, homeHover, true, false);
        renderTargetIcon(g, homeX + homeW / 2, homeY + homeH / 2, 0xFFFFFFFF);

        if (!categories.isEmpty()) {
            boolean categoryHover = inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH);
            if (categoryHover) hovered = null;
            renderControl(g, categoryX, categoryY, categoryW, categoryH,
                categoryHover, true, !categoryFilter.isEmpty());
            String raw = categoryFilter.isEmpty()
                ? Component.translatable("gui.progressivestages.tree.category.all").getString()
                : categoryFilter;
            String label = font.plainSubstrByWidth(raw, categoryW - 18);
            g.drawString(font, label, categoryX + 5, categoryY + 3, 0xFFFFFFFF, false);
            renderChevron(g, categoryX + categoryW - 9, categoryY + categoryH / 2,
                categoryOpen, 0xFFFFFFFF);
            renderCategoryMenu(g, mouseX, mouseY);
        }
    }

    private void renderCategoryMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (!categoryOpen) return;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 1200.0F);
        int rows = Math.min(CATEGORY_VISIBLE_ROWS, categoryOptionCount());
        categoryMenuX = categoryX;
        categoryMenuY = categoryY + categoryH + 2;
        categoryMenuW = Math.max(categoryW, 132);
        categoryMenuH = rows * CATEGORY_ROW_H + 2;
        int maximumScroll = Math.max(0, categoryOptionCount() - rows);
        categoryScroll = Math.max(0, Math.min(categoryScroll, maximumScroll));
        if (inside(mouseX, mouseY, categoryMenuX, categoryMenuY, categoryMenuW, categoryMenuH)) {
            hovered = null;
        }
        fillPixelRounded(g, categoryMenuX, categoryMenuY, categoryMenuW, categoryMenuH, 0xF0141414);
        renderPixelRoundedOutline(g, categoryMenuX, categoryMenuY,
            categoryMenuW, categoryMenuH, 0xFFE0B54D);
        for (int row = 0; row < rows; row++) {
            int index = categoryScroll + row;
            if (index >= categoryOptionCount()) break;
            int y = categoryMenuY + 1 + row * CATEGORY_ROW_H;
            boolean hover = inside(mouseX, mouseY, categoryMenuX + 1, y,
                categoryMenuW - 2, CATEGORY_ROW_H);
            boolean active = index == 0 ? categoryFilter.isEmpty()
                : categories.get(index - 1).equalsIgnoreCase(categoryFilter);
            if (hover || active) {
                fillPixelRounded(g, categoryMenuX + 2, y + 1, categoryMenuW - 4,
                    CATEGORY_ROW_H - 2, active ? 0xCC785F27 : 0xCC4D4D4D);
            }
            String value = index == 0
                ? Component.translatable("gui.progressivestages.tree.category.all").getString()
                : categories.get(index - 1);
            g.drawString(font, font.plainSubstrByWidth(value, categoryMenuW - 12),
                categoryMenuX + 8, y + 3, active ? GOLD : 0xFFFFFFFF, false);
            if (active) {
                g.fill(categoryMenuX + 4, y + 5, categoryMenuX + 6, y + 9, GOLD);
            }
        }
        if (maximumScroll > 0) {
            int track = categoryMenuH - 6;
            int thumb = Math.max(8, track * rows / categoryOptionCount());
            int thumbY = categoryMenuY + 3 + (track - thumb) * categoryScroll / maximumScroll;
            fillPixelRounded(g, categoryMenuX + categoryMenuW - 4, thumbY, 2, thumb, GOLD);
        }
        g.pose().popPose();
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
        if (node == null) {
            boolean owned = ClientStageCache.hasStage(selected);
            node = new MapNode(selected, 0, 0, owned, !owned && dependenciesSatisfied(selected));
        }
        panelW = Math.min(196, Math.max(156, mapRight - mapLeft - 20));
        panelH = Math.max(90, mapBottom - mapTop - 12);
        panelX = mapRight - panelW - 6;
        panelY = mapTop + 6;
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xEE000000);
        g.blitSprite(TITLE_BOX, panelX, panelY, panelW, panelH);

        int x = panelX + 8;
        int contentTop = panelY + 31;
        ClientTriggerProgress.StageData data = ClientTriggerProgress.get(selected);
        boolean showBuy = data.purchasable() && !unlockHelpOpen;
        int contentBottom = panelY + panelH - 7 - (showBuy ? 20 : 0);
        panelScrollTrackX = panelScrollTrackY = panelScrollTrackHeight = 0;
        panelScrollThumbY = panelScrollThumbHeight = 0;
        int innerW = panelW - 16;
        int nameColor = stageColorOr(selected,
            node.owned() ? GREEN : node.available() ? GOLD : RED);
        g.fill(panelX + 3, panelY + 9, panelX + 4, panelY + panelH - 9,
            withAlpha(nameColor, 210));

        g.renderFakeItem(iconFor(selected), x, panelY + 6);
        String displayName = font.plainSubstrByWidth(
            ClientStageCache.getDisplayName(selected), Math.max(20, panelW - 61));
        g.drawString(font, Component.literal(displayName).withStyle(ChatFormatting.BOLD),
            x + 21, panelY + 7, nameColor, false);
        Component status = Component.translatable(node.owned()
            ? "gui.progressivestages.tree.status.unlocked"
            : node.available() ? "gui.progressivestages.tree.status.ready"
            : "gui.progressivestages.tree.status.locked");
        g.drawString(font, status, x + 21, panelY + 17, nameColor, false);
        boolean closeHover = inside(mouseX, mouseY, panelX + panelW - 16, panelY + 4, 13, 13);
        renderControl(g, panelX + panelW - 16, panelY + 4, 13, 13,
            closeHover, true, false);
        renderControlIcon(g, panelX + panelW - 16, panelY + 4, 13, 13,
            CLOSE_CONTROL_ICON, closeHover ? RED : 0xFFFFFFFF);
        addHoverHint(panelX + 4, panelY + 4, Math.max(20, panelW - 48), 22,
            Component.translatable("gui.progressivestages.tree.status.tooltip", status));
        helpW = helpH = 13;
        helpX = panelX + panelW - 31;
        helpY = panelY + 4;
        boolean helpAvailable = canShowUnlockHelp(selected);
        if (helpAvailable) {
            boolean helpHover = inside(mouseX, mouseY, helpX, helpY, helpW, helpH);
            renderControl(g, helpX, helpY, helpW, helpH,
                helpHover || keyboardControlFocus == 0, true, unlockHelpOpen);
            renderControlIcon(g, helpX, helpY, helpW, helpH, HELP_CONTROL_ICON, GOLD);
        }
        g.fill(x, panelY + 28, panelX + panelW - 7, panelY + 29,
            withAlpha(nameColor, 120));
        addHoverHint(panelX + panelW - 16, panelY + 4, 13, 13,
            Component.translatable("gui.progressivestages.tree.close.tooltip"));
        if (helpAvailable) {
            addHoverHint(helpX, helpY, helpW, helpH,
                Component.translatable("gui.progressivestages.tree.guide.unlock.tooltip",
                    ClientStageCache.getDisplayName(selected)));
        }

        g.enableScissor(panelX + 3, contentTop, panelX + panelW - 3, contentBottom);
        int y = contentTop - panelScroll;
        String description = ClientStageCache.getDescription(selected);
        if (!description.isBlank()) {
            for (FormattedCharSequence line : font.split(TextUtil.parseColorCodes("&7" + description), innerW)) {
                g.drawString(font, line, x, y, 0xFFFFFFFF, false);
                y += 10;
            }
            y += 7;
        }

        boolean suppressPrerequisiteDetails = false;
        boolean suppressSlotDetails = false;
        if (unlockHelpOpen && canShowUnlockHelp(selected)) {
            var guide = ClientStageCache.getStageDefinition(selected).orElse(null);
            if (guide != null) {
                List<GuideRequirementSummary.Line> requirementSummary = guideRequirementLines(selected, data);
                boolean summaryEmbedded = StageGuideTemplate.usesToken(
                    guide.howToUnlock(), StageGuideTemplate.STAGE_REQUIREMENTS)
                    || StageGuideTemplate.usesToken(guide.nextSteps(), StageGuideTemplate.STAGE_REQUIREMENTS)
                    || StageGuideTemplate.usesToken(guide.whereToFind(), StageGuideTemplate.STAGE_REQUIREMENTS);
                GuideRequirementSummary.DisplayPlan displayPlan =
                    GuideRequirementSummary.displayPlan(requirementSummary, summaryEmbedded);
                suppressPrerequisiteDetails = displayPlan.suppressPrerequisiteDetails();
                suppressSlotDetails = displayPlan.suppressSlotDetails();
                y = drawSectionHeading(g, Component.translatable(
                    "gui.progressivestages.tree.guide.unlock.title", ClientStageCache.getDisplayName(selected)),
                    x, y, innerW, GOLD);
                if (displayPlan.showGeneratedSummary()) {
                    for (GuideRequirementSummary.Line requirement : requirementSummary) {
                        y = drawGuideLine(g, guideRequirementComponent(requirement), x, y, innerW, GOLD);
                    }
                }
                if (!guide.howToUnlock().isBlank()) {
                    y = drawGuideLine(g, Component.translatable("gui.progressivestages.tree.guide.unlock.text",
                        expandGuideText(guide.howToUnlock(), selected, data)), x, y, innerW, GOLD);
                }
                if (!guide.nextSteps().isBlank()) {
                    y = drawGuideLine(g, Component.translatable("gui.progressivestages.tree.guide.next",
                        expandGuideText(guide.nextSteps(), selected, data)), x, y, innerW, GOLD);
                }
                if (!guide.whereToFind().isBlank()) {
                    y = drawGuideLine(g, Component.translatable("gui.progressivestages.tree.guide.location",
                        expandGuideText(guide.whereToFind(), selected, data)), x, y, innerW, GOLD);
                }
            }
        }

        List<StageId> dependencies = ClientStageCache.getDependencies(selected).stream()
            .filter(dependency -> !ClientStageCache.isHidden(dependency)).toList();
        if (!dependencies.isEmpty() && !suppressPrerequisiteDetails) {
            String mode = ClientStageCache.getDependencyMode(selected);
            Component prerequisiteTitle = switch (mode) {
                case "any" -> Component.translatable("gui.progressivestages.tree.prerequisites.any");
                case "at_least" -> Component.translatable("gui.progressivestages.tree.prerequisites.count",
                    ClientStageCache.getDependencyCount(selected));
                default -> Component.translatable("gui.progressivestages.tree.prerequisites.all");
            };
            y = drawSectionHeading(g, prerequisiteTitle, x, y, innerW, GOLD);
            int dependencyHintY = y - 14;
            for (StageId dependency : dependencies) {
                boolean has = ClientStageCache.hasStage(dependency);
                g.drawString(font, (has ? "✔ " : "✗ ") + ClientStageCache.getDisplayName(dependency), x + 3, y,
                    has ? GREEN : RED, false);
                y += 10;
            }
            y += 3;
            addHoverHint(x, dependencyHintY, innerW, Math.max(10, y - dependencyHintY),
                Component.translatable("gui.progressivestages.tree.prerequisites.tooltip"));
        }

        String slotGroup = ClientStageCache.getSlotGroup(selected);
        if (!slotGroup.isBlank() && !suppressSlotDetails) {
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.slots.heading"),
                x, y, innerW, GOLD);
            int slotsHintY = y - 14;
            g.drawString(font, Component.literal(humanizeIdentifier(slotGroup))
                .withStyle(ChatFormatting.BOLD), x + 3, y, 0xFFFFD45A, false);
            y += 11;
            int limit = ClientStageCache.getSlotLimit(selected);
            int active = ClientStageCache.getOwnedSlotCount(slotGroup);
            Component slots = limit <= 0
                ? Component.translatable("gui.progressivestages.tree.slots.unlimited")
                : Component.translatable("gui.progressivestages.tree.slots.limited", active, limit);
            for (FormattedCharSequence wrapped : font.split(slots, innerW)) {
                g.drawString(font, wrapped, x + 3, y, 0xFFCCCCCC, false);
                y += 10;
            }
            if (limit > 0) {
                Component policy = Component.translatable("gui.progressivestages.tree.slots.policy",
                    humanizeIdentifier(ClientStageCache.getSlotPolicy(selected)));
                g.drawString(font, policy, x + 3, y, 0xFFAAAAAA, false);
                y += 15;
            } else {
                y += 6;
            }
            addHoverHint(x, slotsHintY, innerW, Math.max(10, y - slotsHintY),
                Component.translatable("gui.progressivestages.tree.slots.tooltip"));
        }

        if (data.hasTriggers()) {
            float pct = Math.max(0, data.percent());
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.triggers"), x, y, innerW, GOLD);
            int triggersHintY = y - 14;
            g.drawString(font, Component.translatable("gui.progressivestages.tree.progress.percent",
                Math.round(pct * 100)), x, y, 0xFF7FD8FF, false);
            y += 11;
            drawProgressBar(g, x, y, innerW, pct, nameColor);
            y += 10;
            int route = 1;
            for (ClientTriggerProgress.Rule rule : data.rules()) {
                Component routeLabel = Component.translatable(
                    "any_of".equals(rule.mode())
                        ? "gui.progressivestages.tree.trigger.any"
                        : "gui.progressivestages.tree.trigger.all",
                    route++);
                g.drawString(font, (rule.satisfied() ? "✔ " : "• ") + routeLabel.getString(), x + 2, y,
                    rule.satisfied() ? GREEN : TEXT_MUTED, false);
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
                        g.drawString(font, wrapped, x + 5, y,
                            condition.satisfied() ? GREEN : 0xFFCCCCCC, false);
                        y += 10;
                    }
                }
            }
            y += 3;
            addHoverHint(x, triggersHintY, innerW, Math.max(10, y - triggersHintY),
                Component.translatable("gui.progressivestages.tree.triggers.tooltip"));
        }

        if (!data.challenges().isEmpty()) {
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.challenges"),
                x, y, innerW, 0xFFFFAA55);
            int challengesHintY = y - 14;
            for (ClientTriggerProgress.Challenge challenge : data.challenges()) {
                g.drawString(font, humanizeResource(challenge.id()) + ". "
                        + humanizeIdentifier(challenge.status()), x + 3, y,
                    challenge.status().equals("succeeded") ? GREEN : 0xFFDDCC88, false);
                y += 10;
                for (String budget : challenge.budgets()) {
                    g.drawString(font, "  " + budget, x + 3, y, 0xFFCCCCCC, false);
                    y += 10;
                }
            }
            y += 3;
            addHoverHint(x, challengesHintY, innerW, Math.max(10, y - challengesHintY),
                Component.translatable("gui.progressivestages.tree.challenges.tooltip"));
        }

        if (!data.modifiers().isEmpty()) {
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.modifiers"),
                x, y, innerW, 0xFFAA88FF);
            int modifiersHintY = y - 14;
            for (ClientTriggerProgress.ModifierPreview modifier : data.modifiers()) {
                y = renderModifierPreview(g, modifier, x, y, innerW);
            }
            y += 4;
            addHoverHint(x, modifiersHintY, innerW, Math.max(10, y - modifiersHintY),
                Component.translatable("gui.progressivestages.tree.modifiers.tooltip"));
        }

        if (!data.why().isEmpty()) {
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.why"),
                x, y, innerW, 0xFF55DDDD);
            int whyHintY = y - 14;
            for (ClientTriggerProgress.Why why : data.why().stream()
                    .skip(Math.max(0, data.why().size() - 5)).toList()) {
                String line = java.util.stream.Stream.of(
                        humanizeIdentifier(why.effect()),
                        humanizeIdentifier(why.category()),
                        displayTarget(why.target()))
                    .filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(". "));
                for (FormattedCharSequence wrapped : font.split(Component.literal(line), innerW - 5)) {
                    g.drawString(font, wrapped, x + 3, y, why.blocked() ? 0xFFFF7777 : 0xFF77DD77, false);
                    y += 10;
                }
            }
            y += 3;
            addHoverHint(x, whyHintY, innerW, Math.max(10, y - whyHintY),
                Component.translatable("gui.progressivestages.tree.why.tooltip"));
        }

        if (!data.history().isEmpty()) {
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.history"),
                x, y, innerW, TEXT_MUTED);
            int historyHintY = y - 14;
            for (ClientTriggerProgress.History history : data.history().stream()
                    .skip(Math.max(0, data.history().size() - 5)).toList()) {
                String line = humanizeIdentifier(history.direction()) + ". "
                    + (history.committed() ? "Committed" : "Rejected");
                g.drawString(font, line, x + 3, y, history.committed() ? 0xFF77DD77 : 0xFFFF7777, false);
                y += 10;
            }
            y += 3;
            addHoverHint(x, historyHintY, innerW, Math.max(10, y - historyHintY),
                Component.translatable("gui.progressivestages.tree.history.tooltip"));
        }

        if (data.unlockTotal() > 0) {
            y = drawSectionHeading(g,
                Component.translatable("gui.progressivestages.tree.unlocks", data.unlockTotal()),
                x, y, innerW, GOLD);
            int unlocksHintY = y - 14;
            int columns = Math.max(1, innerW / 18);
            for (int i = 0; i < data.unlockSample().size(); i++) {
                ResourceLocation itemId = data.unlockSample().get(i);
                ItemStack stack = BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY);
                g.renderItem(stack, x + (i % columns) * 18, y + (i / columns) * 18);
            }
            y += ((data.unlockSample().size() + columns - 1) / columns) * 18 + 3;
            addHoverHint(x, unlocksHintY, innerW, Math.max(10, y - unlocksHintY),
                Component.translatable("gui.progressivestages.tree.unlocks.tooltip"));
        }
        g.disableScissor();

        int contentHeight = y + panelScroll - contentTop;
        panelMax = Math.max(0, contentHeight - (contentBottom - contentTop));
        panelScroll = Math.max(0, Math.min(panelScroll, panelMax));
        if (panelMax == 0) draggingPanelScrollbar = false;
        if (panelMax > 0) {
            int track = contentBottom - contentTop;
            int thumb = Math.max(12, track * track / (track + panelMax));
            int thumbY = contentTop + (track - thumb) * panelScroll / panelMax;
            panelScrollTrackX = panelX + panelW - 6;
            panelScrollTrackY = contentTop;
            panelScrollTrackHeight = track;
            panelScrollThumbY = thumbY;
            panelScrollThumbHeight = thumb;
            fillPixelRounded(g, panelScrollTrackX, contentTop, 4, track, 0x66101010);
            fillPixelRounded(g, panelScrollTrackX, thumbY, 4, thumb, nameColor);
            addHoverHint(panelScrollTrackX - 3, contentTop, 10, track,
                Component.translatable("gui.progressivestages.tree.scroll.tooltip"));
        }

        buyEnabled = false;
        buyStage = null;
        if (showBuy) {
            buyX = x;
            buyY = panelY + panelH - 21;
            buyW = innerW;
            buyH = 16;
            buyStage = selected;
            buyEnabled = data.canPurchase();
            boolean hover = buyEnabled && inside(mouseX, mouseY, buyX, buyY, buyW, buyH);
            renderControl(g, buyX, buyY, buyW, buyH, hover, buyEnabled, buyEnabled);
            String purchaseLabel = !buyEnabled ? "gui.progressivestages.tree.purchase.need"
                : node.owned() ? "gui.progressivestages.tree.purchase.ownership"
                : "gui.progressivestages.tree.purchase";
            Component label = Component.translatable(purchaseLabel, data.costSummary());
            String visibleLabel = font.plainSubstrByWidth(label.getString(), buyW - 8);
            g.drawCenteredString(font, visibleLabel, buyX + buyW / 2, buyY + 4,
                buyEnabled ? 0xFFFFFFFF : 0xFFB0B0B0);
            addHoverHint(buyX, buyY, buyW, buyH,
                Component.translatable("gui.progressivestages.tree.purchase.tooltip", data.costSummary()));
        }
    }

    private boolean canShowUnlockHelp(StageId id) {
        return id != null && ClientStageCache.isStageGuideVisible(id)
            && !ClientStageCache.hasStage(id)
            && visibleToViewer(id)
            && ClientStageCache.getStageDefinition(id).isPresent();
    }

    static int centeredControlIconX(int controlX, int controlWidth, int iconWidth) {
        return controlX + (controlWidth - iconWidth) / 2;
    }

    static int centeredControlIconY(int controlY, int controlHeight, int iconHeight) {
        return controlY + (controlHeight - iconHeight) / 2;
    }

    private static void renderControlIcon(
            GuiGraphics graphics,
            int controlX,
            int controlY,
            int controlWidth,
            int controlHeight,
            String[] pixels,
            int color
    ) {
        int iconWidth = pixels[0].length();
        int iconHeight = pixels.length;
        int iconX = centeredControlIconX(controlX, controlWidth, iconWidth);
        int iconY = centeredControlIconY(controlY, controlHeight, iconHeight);
        for (int row = 0; row < iconHeight; row++) {
            int column = 0;
            while (column < iconWidth) {
                if (pixels[row].charAt(column) != '#') {
                    column++;
                    continue;
                }
                int runEnd = column + 1;
                while (runEnd < iconWidth && pixels[row].charAt(runEnd) == '#') {
                    runEnd++;
                }
                graphics.fill(iconX + column, iconY + row, iconX + runEnd, iconY + row + 1, color);
                column = runEnd;
            }
        }
    }

    private String expandGuideText(String template, StageId id, ClientTriggerProgress.StageData data) {
        Map<String, String> values = new HashMap<>();
        values.put(StageGuideTemplate.STAGE_NAME, ClientStageCache.getDisplayName(id));
        values.put(StageGuideTemplate.STAGE_REQUIREMENTS, guideRequirementText(id, data));
        values.put(StageGuideTemplate.REMAINING_REQUIREMENTS, remainingRequirements(id, data));
        values.put(StageGuideTemplate.STAGE_PROGRESS, stageProgress(data));
        return StageGuideTemplate.expand(template, values).text();
    }

    private List<GuideRequirementSummary.Line> guideRequirementLines(
            StageId id, ClientTriggerProgress.StageData data) {
        List<String> dependencies = ClientStageCache.getDependencies(id).stream()
            .filter(dependency -> !ClientStageCache.isHidden(dependency) && visibleToViewer(dependency))
            .map(ClientStageCache::getDisplayName).toList();
        boolean hiddenRequirementAlreadyMet = ClientStageCache.getDependencies(id).stream()
            .anyMatch(dependency -> (ClientStageCache.isHidden(dependency) || !visibleToViewer(dependency))
                && ClientStageCache.hasStage(dependency));
        List<GuideRequirementSummary.TriggerRoute> routes = data.rules().stream()
            .map(rule -> new GuideRequirementSummary.TriggerRoute(rule.mode(), rule.description(),
                rule.conditions().stream().map(condition -> new GuideRequirementSummary.Condition(
                    condition.label(), condition.threshold())).toList()))
            .toList();
        String group = ClientStageCache.getSlotGroup(id);
        return GuideRequirementSummary.project(new GuideRequirementSummary.Input(
            data.acquisition() != null && data.acquisition().available(),
            hasConcealedUnmetDependency(id),
            hiddenRequirementAlreadyMet,
            dependencies,
            ClientStageCache.getDependencyMode(id),
            ClientStageCache.getDependencyCount(id),
            routes,
            data.purchasable(),
            data.costSummary(),
            data.purchasable() && !data.purchaseBypassesTriggers(),
            humanizeIdentifier(group),
            group.isBlank() ? 0 : ClientStageCache.getOwnedSlotCount(group),
            ClientStageCache.getSlotLimit(id),
            humanizeIdentifier(ClientStageCache.getSlotPolicy(id)),
            ClientStageCache.stageHasTriggers(id)));
    }

    private String guideRequirementText(StageId id, ClientTriggerProgress.StageData data) {
        return guideRequirementLines(id, data).stream()
            .map(this::guideRequirementComponent)
            .map(Component::getString)
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private Component guideRequirementComponent(GuideRequirementSummary.Line line) {
        return Component.translatable(line.translationKey(), line.arguments().toArray());
    }

    private String stageRequirements(StageId id) {
        List<StageId> allDependencies = ClientStageCache.getDependencies(id);
        if (hasConcealedUnmetDependency(id)) {
            return Component.translatable("gui.progressivestages.tree.guide.requirements.unavailable").getString();
        }
        List<String> dependencies = allDependencies.stream()
            .filter(dependency -> !ClientStageCache.isHidden(dependency) && visibleToViewer(dependency))
            .map(ClientStageCache::getDisplayName).toList();
        if (dependencies.isEmpty()) {
            return Component.translatable(allDependencies.isEmpty()
                ? "gui.progressivestages.tree.guide.requirements.none"
                : "gui.progressivestages.tree.guide.requirements.hidden_met").getString();
        }
        String joined = String.join(", ", dependencies);
        return switch (ClientStageCache.getDependencyMode(id)) {
            case "any" -> "Any of " + joined;
            case "at_least" -> "At least " + ClientStageCache.getDependencyCount(id) + " of " + joined;
            default -> "All of " + joined;
        };
    }

    private boolean hasConcealedUnmetDependency(StageId id) {
        return ClientStageCache.getDependencies(id).stream()
            .anyMatch(dependency -> !ClientStageCache.hasStage(dependency) && !visibleToViewer(dependency));
    }

    private String remainingRequirements(StageId id, ClientTriggerProgress.StageData data) {
        if (hasConcealedUnmetDependency(id)) {
            return Component.translatable("gui.progressivestages.tree.guide.remaining.unavailable").getString();
        }
        NetworkHandler.AcquisitionInfo acquisition = data.acquisition();
        if (acquisition == null) {
            return Component.translatable("gui.progressivestages.tree.guide.remaining.unavailable").getString();
        }
        List<String> remaining = new ArrayList<>();
        if (!acquisition.available()) {
            remaining.add(Component.translatable("gui.progressivestages.tree.guide.remaining.unavailable").getString());
        } else if (!acquisition.remaining().isBlank()) {
            remaining.add(acquisition.remaining());
        }
        long cooldownMillis = data.remainingCooldownMillis();
        if (cooldownMillis > 0L) {
            long seconds = Math.max(1L, (cooldownMillis + 999L) / 1000L);
            remaining.add(Component.translatable("gui.progressivestages.tree.guide.remaining.cooldown", seconds).getString());
        }
        return remaining.isEmpty()
            ? Component.translatable("gui.progressivestages.tree.guide.remaining.met").getString()
            : String.join("; ", remaining);
    }

    private String stageProgress(ClientTriggerProgress.StageData data) {
        float percent = data.percent();
        if (data.purchasable() && data.canPurchase()) {
            return Component.translatable("gui.progressivestages.tree.guide.progress.ready_to_buy").getString();
        }
        if (percent < 0f) {
            return Component.translatable(data.purchasable()
                ? "gui.progressivestages.tree.guide.progress.purchase_not_ready"
                : "gui.progressivestages.tree.guide.remaining.unavailable").getString();
        }
        int progress = Math.round(percent * 100);
        return Component.translatable(data.purchasable()
            ? "gui.progressivestages.tree.guide.progress.trigger_purchase"
            : "gui.progressivestages.tree.guide.progress.trigger", progress).getString();
    }

    private void addHoverHint(int x, int y, int width, int height, Component text) {
        if (width > 0 && height > 0) hoverHints.add(new HoverHint(x, y, width, height, text));
    }

    private boolean renderInspectorTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (selected == null) return false;
        if (canShowUnlockHelp(selected) && inside(mouseX, mouseY, helpX, helpY, helpW, helpH)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.guide.unlock.tooltip",
                ClientStageCache.getDisplayName(selected)), mouseX, mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, panelX + panelW - 16, panelY + 4, 13, 13)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.close.tooltip"), mouseX, mouseY);
            return true;
        }
        for (int index = hoverHints.size() - 1; index >= 0; index--) {
            HoverHint hint = hoverHints.get(index);
            if (inside(mouseX, mouseY, hint.x(), hint.y(), hint.width(), hint.height())) {
                g.renderTooltip(font, hint.text(), mouseX, mouseY);
                return true;
            }
        }
        if (keyboardControlFocus == 0 && canShowUnlockHelp(selected)) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.guide.unlock.tooltip",
                ClientStageCache.getDisplayName(selected)), helpX + helpW / 2, helpY + helpH);
            return true;
        }
        if (keyboardControlFocus == 1) {
            g.renderTooltip(font, Component.translatable("gui.progressivestages.tree.close.tooltip"),
                panelX + panelW - 10, panelY + 17);
            return true;
        }
        return false;
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int width, float fraction, int accent) {
        fillPixelRounded(g, x, y, width, 7, 0xFF151515);
        renderPixelRoundedOutline(g, x, y, width, 7, 0xFF686868);
        int fill = Math.round((width - 4) * Math.max(0, Math.min(1, fraction)));
        if (fill > 0) {
            int color = fraction >= 1 ? GREEN : accent;
            g.fill(x + 2, y + 2, x + 2 + fill, y + 5, color);
        }
    }

    private int renderModifierPreview(
            GuiGraphics g,
            ClientTriggerProgress.ModifierPreview preview,
            int x,
            int y,
            int width
    ) {
        int cardX = x + 1;
        int cardW = Math.max(40, width - 2);
        List<PreviewRow> rows = new ArrayList<>();
        for (ClientTriggerProgress.ModifierField field : preview.fields()) {
            ItemStack icon = selectorIcon(field.registry(), field.selector());
            String value = field.selector().isBlank()
                ? humanizeFreeText(field.value())
                : selectorDisplayName(field.registry(), field.selector(), icon);
            Component line = Component.literal(field.label() + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
            int textWidth = Math.max(20, cardW - 10 - (icon.isEmpty() ? 0 : 19));
            List<FormattedCharSequence> wrapped = font.split(line, textWidth);
            int height = Math.max(icon.isEmpty() ? 10 : 18, wrapped.size() * 10) + 2;
            rows.add(new PreviewRow(icon, wrapped, height));
        }

        int cardH = 27 + rows.stream().mapToInt(PreviewRow::height).sum();
        fillPixelRounded(g, cardX, y, cardW, cardH, 0xB0161616);
        renderPixelRoundedOutline(g, cardX, y, cardW, cardH, 0x995D4B78);

        Component title = modifierTitle(preview.kind());
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD),
            cardX + 5, y + 5, 0xFFCCAAFF, false);
        String subtitle = humanizeResource(preview.id());
        g.drawString(font, font.plainSubstrByWidth(subtitle, cardW - 10),
            cardX + 5, y + 15, 0xFF888888, false);

        int rowY = y + 27;
        for (PreviewRow row : rows) {
            int textX = cardX + 5;
            if (!row.icon().isEmpty()) {
                g.renderItem(row.icon(), textX, rowY);
                textX += 19;
            }
            int textY = rowY + (row.icon().isEmpty() ? 0 : 1);
            for (FormattedCharSequence line : row.lines()) {
                g.drawString(font, line, textX, textY, 0xFFFFFFFF, false);
                textY += 10;
            }
            rowY += row.height();
        }
        return y + cardH + 5;
    }

    private Component modifierTitle(String kind) {
        return Component.translatable(switch (kind) {
            case "block_drop_bonus" -> "gui.progressivestages.tree.modifier.block_drop";
            case "equipment_locked" -> "gui.progressivestages.tree.modifier.locked";
            case "equipment_active" -> "gui.progressivestages.tree.modifier.active";
            case "affinity" -> "gui.progressivestages.tree.modifier.affinity";
            default -> "gui.progressivestages.tree.modifier.equipment";
        });
    }

    private ItemStack selectorIcon(String registry, String selector) {
        if (registry.isBlank() || selector.isBlank()) return ItemStack.EMPTY;
        String key = registry + "|" + normalizeSelector(selector);
        List<ItemStack> icons = selectorIconCache.computeIfAbsent(key,
            ignored -> resolveSelectorIcons(registry, selector));
        if (icons.isEmpty()) return ItemStack.EMPTY;
        int cycle = (int) (Util.getMillis() / 1100L);
        return icons.get(Math.floorMod(cycle + key.hashCode(), icons.size()));
    }

    private List<ItemStack> resolveSelectorIcons(String registry, String selector) {
        String normalized = normalizeSelector(selector);
        List<ItemStack> icons = new ArrayList<>();
        if (normalized.startsWith("tag:") || normalized.startsWith("#")) {
            String rawTag = normalized.startsWith("tag:") ? normalized.substring(4) : normalized.substring(1);
            ResourceLocation tagId = ResourceLocation.tryParse(rawTag);
            if (tagId == null) return List.of();
            if ("block".equals(registry)) {
                BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tagId))
                    .ifPresent(values -> values.forEach(holder -> addPreviewIcon(icons, holder.value().asItem())));
            } else if ("item".equals(registry)) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId))
                    .ifPresent(values -> values.forEach(holder -> addPreviewIcon(icons, holder.value())));
            }
            return List.copyOf(icons);
        }
        if (normalized.startsWith("mod:")) {
            String namespace = normalized.substring(4);
            if ("block".equals(registry)) {
                BuiltInRegistries.BLOCK.forEach(block -> {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                    if (id != null && id.getNamespace().equals(namespace)) addPreviewIcon(icons, block.asItem());
                });
            } else if ("item".equals(registry)) {
                BuiltInRegistries.ITEM.forEach(item -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    if (id != null && id.getNamespace().equals(namespace)) addPreviewIcon(icons, item);
                });
            }
            return List.copyOf(icons);
        }
        if (normalized.equals("all") || normalized.equals("*")) return List.of();
        String rawId = normalized.startsWith("id:") ? normalized.substring(3) : normalized;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) return List.of();
        if ("block".equals(registry)) {
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> addPreviewIcon(icons, block.asItem()));
        } else if ("item".equals(registry)) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> addPreviewIcon(icons, item));
        }
        return List.copyOf(icons);
    }

    private static void addPreviewIcon(List<ItemStack> icons, net.minecraft.world.item.Item item) {
        if (item != Items.AIR) icons.add(new ItemStack(item));
    }

    private String selectorDisplayName(String registry, String selector, ItemStack icon) {
        String normalized = normalizeSelector(selector);
        if (normalized.startsWith("tag:") || normalized.startsWith("#")) {
            String rawTag = normalized.startsWith("tag:") ? normalized.substring(4) : normalized.substring(1);
            ResourceLocation tag = ResourceLocation.tryParse(rawTag);
            return tag == null ? humanizeIdentifier(rawTag) : humanizeTag(tag);
        }
        if (normalized.startsWith("mod:")) {
            return "All " + humanizeIdentifier(normalized.substring(4)) + " content";
        }
        if (normalized.startsWith("name:")) {
            return "Names containing " + humanizeIdentifier(normalized.substring(5));
        }
        if (normalized.equals("all") || normalized.equals("*")) return "Everything";
        if (!icon.isEmpty()) return icon.getHoverName().getString();
        String rawId = normalized.startsWith("id:") ? normalized.substring(3) : normalized;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id == null ? humanizeIdentifier(rawId) : humanizeResource(id);
    }

    private static String normalizeSelector(String selector) {
        String normalized = selector == null ? "" : selector.trim();
        int priority = normalized.indexOf("|priority=");
        return priority < 0 ? normalized : normalized.substring(0, priority).trim();
    }

    private static String humanizeTag(ResourceLocation tag) {
        String[] parts = tag.getPath().split("/");
        StringBuilder output = new StringBuilder();
        for (int index = parts.length - 1; index >= 0; index--) {
            if (output.length() > 0) output.append(' ');
            output.append(humanizeIdentifier(parts[index]));
        }
        return output.toString();
    }

    private static String humanizeResource(ResourceLocation id) {
        if (id == null) return "";
        String path = id.getPath();
        return humanizeIdentifier(path.substring(path.lastIndexOf('/') + 1));
    }

    private String displayTarget(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(item -> item.getDescription().getString())
            .filter(value -> !value.isBlank())
            .orElseGet(() -> BuiltInRegistries.BLOCK.getOptional(id)
                .map(block -> block.getName().getString())
                .filter(value -> !value.isBlank())
                .orElseGet(() -> humanizeResource(id)));
    }

    private static String humanizeFreeText(String value) {
        if (value == null || value.isBlank()) return "";
        if (!value.contains("_") && !value.contains(":")) return value;
        if (value.contains(", ")) {
            return java.util.Arrays.stream(value.split(", "))
                .map(StageTreeScreen::humanizeIdentifier)
                .collect(java.util.stream.Collectors.joining(", "));
        }
        return humanizeIdentifier(value);
    }

    private static String humanizeIdentifier(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.trim();
        int namespace = clean.lastIndexOf(':');
        if (namespace >= 0) clean = clean.substring(namespace + 1);
        int path = clean.lastIndexOf('/');
        if (path >= 0) clean = clean.substring(path + 1);
        clean = clean.replace('_', ' ').replace('-', ' ').replace('.', ' ');
        StringBuilder output = new StringBuilder(clean.length());
        boolean capitalize = true;
        for (int index = 0; index < clean.length(); index++) {
            char character = clean.charAt(index);
            if (Character.isWhitespace(character)) {
                if (output.length() > 0 && output.charAt(output.length() - 1) != ' ') output.append(' ');
                capitalize = true;
            } else {
                output.append(capitalize ? Character.toUpperCase(character) : character);
                capitalize = false;
            }
        }
        return output.toString().trim();
    }

    private int drawSectionHeading(
            GuiGraphics g,
            Component title,
            int x,
            int y,
            int width,
            int color
    ) {
        String label = font.plainSubstrByWidth(title.getString(), width);
        g.drawString(font, label, x, y, color, false);
        int lineX = x + font.width(label) + 4;
        if (lineX < x + width) {
            g.fill(lineX, y + 5, Math.min(x + width, lineX + 42), y + 6, withAlpha(color, 95));
        }
        return y + 14;
    }

    private int drawGuideLine(GuiGraphics g, Component line, int x, int y, int width, int color) {
        for (FormattedCharSequence wrapped : font.split(line, width)) {
            g.drawString(font, wrapped, x, y, color, false);
            y += 10;
        }
        return y + 3;
    }

    private void renderControl(
            GuiGraphics g,
            int x,
            int y,
            int width,
            int height,
            boolean hovered,
            boolean enabled,
            boolean active
    ) {
        ResourceLocation sprite = enabled
            ? hovered ? BUTTON_HIGHLIGHTED : BUTTON
            : BUTTON_DISABLED;
        g.blitSprite(sprite, x, y, width, height);
        if (active) {
            renderPixelRoundedOutline(g, x, y, width, height, withAlpha(GOLD, 220));
        }
    }

    private static void renderTargetIcon(GuiGraphics g, int centerX, int centerY, int color) {
        g.fill(centerX - 3, centerY, centerX - 1, centerY + 1, color);
        g.fill(centerX + 2, centerY, centerX + 4, centerY + 1, color);
        g.fill(centerX, centerY - 3, centerX + 1, centerY - 1, color);
        g.fill(centerX, centerY + 2, centerX + 1, centerY + 4, color);
        g.fill(centerX, centerY, centerX + 1, centerY + 1, color);
    }

    private static void renderChevron(
            GuiGraphics g,
            int centerX,
            int centerY,
            boolean pointsUp,
            int color
    ) {
        int direction = pointsUp ? -1 : 1;
        g.fill(centerX - 3, centerY - direction, centerX + 4, centerY - direction + 1, color);
        g.fill(centerX - 2, centerY, centerX + 3, centerY + 1, color);
        g.fill(centerX - 1, centerY + direction, centerX + 2, centerY + direction + 1, color);
    }

    private static void fillPixelRounded(
            GuiGraphics g,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (width <= 0 || height <= 0) return;
        if (width < 3 || height < 3) {
            g.fill(x, y, x + width, y + height, color);
            return;
        }
        g.fill(x + 1, y, x + width - 1, y + height, color);
        g.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private static void renderPixelRoundedOutline(
            GuiGraphics g,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        if (width < 5 || height < 5) {
            g.renderOutline(x, y, width, height, color);
            return;
        }
        g.fill(x + 2, y, x + width - 2, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x, y + 2, x + 1, y + height - 2, color);
        g.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        g.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        g.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
        g.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        g.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
    }

    private static void renderCornerBrackets(
            GuiGraphics g,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        int length = Math.min(6, Math.min(width, height) / 3);
        g.fill(x + 2, y, x + 2 + length, y + 1, color);
        g.fill(x, y + 2, x + 1, y + 2 + length, color);
        g.fill(x + width - 2 - length, y, x + width - 2, y + 1, color);
        g.fill(x + width - 1, y + 2, x + width, y + 2 + length, color);
        g.fill(x + 2, y + height - 1, x + 2 + length, y + height, color);
        g.fill(x, y + height - 2 - length, x + 1, y + height - 2, color);
        g.fill(x + width - 2 - length, y + height - 1, x + width - 2, y + height, color);
        g.fill(x + width - 1, y + height - 2 - length, x + width, y + height - 2, color);
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.clamp(alpha, 0, 255) << 24;
    }

    private void playButtonSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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
            closeUnlockHelp();
            playButtonSound();
            rebuild(false);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, homeX, homeY, homeW, homeH)) {
            playButtonSound();
            centerGraph();
            mapHintUntil = Util.getMillis() + 2500L;
            return true;
        }
        if (button == 0 && isOverPanelScrollbar(mouseX, mouseY)) {
            panelScrollbarDragOffset = inside(mouseX, mouseY, panelScrollTrackX - 3,
                    panelScrollThumbY, 10, panelScrollThumbHeight)
                ? (int) mouseY - panelScrollThumbY
                : panelScrollThumbHeight / 2;
            draggingPanelScrollbar = true;
            setPanelScrollFromScrollbar(mouseY);
            return true;
        }
        if (button == 0 && categoryOpen) {
            int category = categoryMenuIndexAt(mouseX, mouseY);
            if (category >= 0) {
                selectCategory(category);
                return true;
            }
            if (!inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH)) {
                categoryOpen = false;
                return true;
            }
        }
        if (button == 0 && !categories.isEmpty()
                && inside(mouseX, mouseY, categoryX, categoryY, categoryW, categoryH)) {
            categoryOpen = !categoryOpen;
            playButtonSound();
            if (categoryOpen) {
                int selectedIndex = categoryFilter.isEmpty() ? 0 : categories.indexOf(categoryFilter) + 1;
                categoryScroll = Math.max(0, selectedIndex - CATEGORY_VISIBLE_ROWS / 2);
            }
            return true;
        }
        if (selected != null) {
            if (button == 0 && canShowUnlockHelp(selected)
                    && inside(mouseX, mouseY, helpX, helpY, helpW, helpH)) {
                unlockHelpOpen = !unlockHelpOpen;
                keyboardControlFocus = unlockHelpOpen ? 0 : -1;
                panelScroll = 0;
                playButtonSound();
                return true;
            }
            if (button == 0 && inside(mouseX, mouseY, panelX + panelW - 16, panelY + 4, 13, 13)) {
                playButtonSound();
                selected = null;
                unlockHelpOpen = false;
                keyboardControlFocus = -1;
                return true;
            }
            if (button == 0 && buyEnabled && buyStage != null && inside(mouseX, mouseY, buyX, buyY, buyW, buyH)) {
                playButtonSound();
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
        if (button == 0 && draggingPanelScrollbar) {
            setPanelScrollFromScrollbar(mouseY);
            return true;
        }
        if (button == 0 && draggingMap) {
            dragDistance += Math.hypot(dragX, dragY);
            if (dragDistance >= 2.0) {
                mapHintUntil = 0L;
                StageTreeViewport.Camera camera = StageTreeViewport.drag(
                    new StageTreeViewport.Camera(panX, panY, zoom), dragX, dragY);
                panX = camera.panX();
                panY = camera.panY();
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingPanelScrollbar) {
            draggingPanelScrollbar = false;
            return true;
        }
        if (button == 0 && draggingMap) {
            if (dragDistance < 2.0 && pressedNode != null) {
                playButtonSound();
                selected = pressedNode.id();
                panelScroll = 0;
                unlockHelpOpen = false;
                keyboardControlFocus = -1;
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
        if (categoryOpen && inside(mouseX, mouseY, categoryMenuX, categoryMenuY,
                categoryMenuW, categoryMenuH)) {
            int rows = Math.min(CATEGORY_VISIBLE_ROWS, categoryOptionCount());
            int maximumScroll = Math.max(0, categoryOptionCount() - rows);
            categoryScroll = Math.max(0, Math.min(maximumScroll,
                categoryScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (insideInspector(mouseX, mouseY)) {
            if (panelMax > 0) {
                setPanelScroll((int) (panelScroll - scrollY * 12));
            }
            return true;
        }
        if (categoryOpen) {
            return true;
        }
        if (inside(mouseX, mouseY, mapLeft, mapTop, mapRight - mapLeft, mapBottom - mapTop)) {
            StageTreeViewport.Camera camera = StageTreeViewport.zoomAt(
                new StageTreeViewport.Camera(panX, panY, zoom),
                scrollY,
                mouseX - (mapLeft + mapRight) / 2.0D,
                mouseY - (mapTop + mapBottom) / 2.0D);
            panX = camera.panX();
            panY = camera.panY();
            zoom = camera.zoom();
            mapHintUntil = Util.getMillis() + 1800L;
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
        if (selected != null && unlockHelpOpen && panelMax > 0) {
            int page = Math.max(10, panelScrollTrackHeight - 10);
            if (keyCode == GLFW.GLFW_KEY_UP) {
                setPanelScroll(panelScroll - 10);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                setPanelScroll(panelScroll + 10);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                setPanelScroll(panelScroll - page);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                setPanelScroll(panelScroll + page);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                setPanelScroll(0);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                setPanelScroll(panelMax);
                return true;
            }
        }
        double dx = 0, dy = 0;
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) dx = 16;
        else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) dx = -16;
        else if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) dy = 16;
        else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) dy = -16;
        else if (keyCode == GLFW.GLFW_KEY_C) {
            if (!categories.isEmpty()) {
                categoryOpen = !categoryOpen;
                playButtonSound();
            }
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_SPACE) {
            playButtonSound();
            centerGraph();
            mapHintUntil = Util.getMillis() + 2500L;
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ESCAPE && categoryOpen) {
            categoryOpen = false;
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ESCAPE && unlockHelpOpen) {
            unlockHelpOpen = false;
            keyboardControlFocus = -1;
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ESCAPE && selected != null) {
            selected = null;
            unlockHelpOpen = false;
            keyboardControlFocus = -1;
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_TAB && selected != null && canShowUnlockHelp(selected)) {
            keyboardControlFocus = keyboardControlFocus == 0 ? 1 : 0;
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ENTER && selected != null
                && canShowUnlockHelp(selected) && keyboardControlFocus == 0) {
            unlockHelpOpen = !unlockHelpOpen;
            panelScroll = 0;
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ENTER && selected != null && keyboardControlFocus == 1) {
            selected = null;
            unlockHelpOpen = false;
            keyboardControlFocus = -1;
            return true;
        }
        if (dx != 0 || dy != 0) {
            panX += dx / zoom;
            panY += dy / zoom;
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

    private boolean isOverPanelScrollbar(double x, double y) {
        return selected != null && panelMax > 0 && inside(x, y, panelScrollTrackX - 3,
            panelScrollTrackY, 10, panelScrollTrackHeight);
    }

    private void setPanelScrollFromScrollbar(double mouseY) {
        setPanelScroll(panelScrollForThumbPosition(mouseY, panelScrollTrackY,
            panelScrollTrackHeight, panelScrollThumbHeight, panelScrollbarDragOffset, panelMax));
    }

    private void setPanelScroll(int requested) {
        panelScroll = clampPanelScroll(requested, panelMax);
    }

    static int clampPanelScroll(int requested, int maximum) {
        return Math.max(0, Math.min(Math.max(0, maximum), requested));
    }

    static int panelScrollForThumbPosition(double pointerY, int trackY, int trackHeight,
                                           int thumbHeight, int grabOffset, int maxScroll) {
        int travel = Math.max(0, trackHeight - thumbHeight);
        if (travel == 0 || maxScroll <= 0) return 0;
        double fraction = (pointerY - grabOffset - trackY) / travel;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * maxScroll);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
