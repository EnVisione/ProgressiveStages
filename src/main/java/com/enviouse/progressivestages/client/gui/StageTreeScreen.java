package com.enviouse.progressivestages.client.gui;

import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.ClientTriggerProgress;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2.3 in-game progression codex — a VIEW-ONLY master/detail screen. The left pane is the stage
 * tree (indented by dependency depth, lock-status coloured); selecting a stage fills the right
 * pane with its icon, description, the prerequisites needed to advance, a percent-to-unlock bar
 * with live {@code [[triggers]]} conditions, and an icon grid of everything the stage unlocks.
 * Opened by the keybind or {@code /stage gui}. Clicking only changes the selection — it never
 * alters game state.
 */
public class StageTreeScreen extends Screen {

    private static final int PAD = 6;
    private static final int ROW_H = 18;
    private static final int GRID = 18;

    private int left, top, right, bottom, dividerX, contentTop;
    private int listScroll, detailScroll, listMax, detailMax, detailContentH;
    // v2.4 purchase button rect (skill-tree mode)
    private int buyX, buyY, buyW, buyH;
    private boolean buyEnabled;
    private StageId buyStage;

    private final List<Node> nodes = new ArrayList<>();
    private int selected = -1;

    private record Node(StageId id, int depth, boolean owned, boolean available) {}

    public StageTreeScreen() {
        super(Component.translatable("gui.progressivestages.tree.title"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new StageTreeScreen()));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 30, 480);
        int h = Math.min(this.height - 30, 280);
        this.left = (this.width - w) / 2;
        this.top = (this.height - h) / 2;
        this.right = left + w;
        this.bottom = top + h;
        this.contentTop = top + 18;
        this.dividerX = left + Math.max(150, (int) (w * 0.40));

        rebuildNodes();
        if (selected < 0 || selected >= nodes.size()) selected = defaultSelection();

        int contentH = nodes.size() * ROW_H;
        this.listMax = Math.max(0, contentH - (bottom - PAD - (contentTop + PAD)));
        this.listScroll = Math.min(listScroll, listMax);
    }

    private void rebuildNodes() {
        nodes.clear();
        Set<StageId> all = ClientStageCache.getAllStageDefinitionIds();

        // Build the DAG as parent → children (a stage's children are the stages that list it as a
        // dependency). Render as a depth-first TREE so each branch is grouped under its parent —
        // e.g. iron_age's children stay under iron_age and magic_age's under magic_age, instead of
        // being interleaved by raw depth. This is what makes branching / multi-path trees read right.
        Map<StageId, List<StageId>> children = new HashMap<>();
        List<StageId> roots = new ArrayList<>();
        for (StageId id : all) {
            boolean isRoot = true;
            for (StageId dep : ClientStageCache.getDependencies(id)) {
                if (all.contains(dep)) {
                    isRoot = false;
                    children.computeIfAbsent(dep, k -> new ArrayList<>()).add(id);
                }
            }
            if (isRoot) roots.add(id);
        }
        Comparator<StageId> byName = Comparator.comparing(ClientStageCache::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        roots.sort(byName);
        for (List<StageId> c : children.values()) c.sort(byName);

        Set<StageId> shown = new HashSet<>();
        for (StageId root : roots) dfs(root, 0, children, shown);
        // Anything not reachable from a root (cycle members / orphans) gets appended at depth 0.
        List<StageId> rest = new ArrayList<>(all);
        rest.sort(byName);
        for (StageId id : rest) if (!shown.contains(id)) dfs(id, 0, children, shown);
    }

    /** DFS a stage and its dependents; a multi-parent stage is listed under the first parent reached. */
    private void dfs(StageId id, int depth, Map<StageId, List<StageId>> children, Set<StageId> shown) {
        if (!shown.add(id)) return;
        boolean owned = ClientStageCache.hasStage(id);
        boolean available = !owned && depsSatisfied(id);
        nodes.add(new Node(id, depth, owned, available));
        for (StageId child : children.getOrDefault(id, List.of())) dfs(child, depth + 1, children, shown);
    }

    private int defaultSelection() {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).available()) return i;
        for (int i = 0; i < nodes.size(); i++) if (!nodes.get(i).owned()) return i;
        return nodes.isEmpty() ? -1 : 0;
    }

    private boolean depsSatisfied(StageId id) {
        for (StageId dep : ClientStageCache.getDependencies(id)) if (!ClientStageCache.hasStage(dep)) return false;
        return true;
    }

    /** Stages that declare {@code id} as a (direct) dependency — the forward branches from this stage. */
    private List<StageId> dependentsOf(StageId id) {
        List<StageId> out = new ArrayList<>();
        for (StageId other : ClientStageCache.getAllStageDefinitionIds()) {
            if (ClientStageCache.getDependencies(other).contains(id)) out.add(other);
        }
        out.sort(Comparator.comparing(ClientStageCache::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        // Frame
        g.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF000000);
        g.fill(left, top, right, bottom, 0xF00C0C0F);
        g.fill(left, contentTop - 1, right, contentTop, 0xFF2A2A30);
        g.fill(dividerX, contentTop, dividerX + 1, bottom, 0xFF2A2A30);

        int owned = (int) nodes.stream().filter(Node::owned).count();
        g.drawString(this.font, Component.translatable("gui.progressivestages.tree.title")
            .copy().withStyle(ChatFormatting.WHITE), left + PAD, top + 5, 0xFFFFFFFF);
        Component count = Component.literal(owned + " / " + nodes.size() + " unlocked").withStyle(ChatFormatting.GRAY);
        g.drawString(this.font, count, right - PAD - this.font.width(count), top + 5, 0xFFFFFFFF);

        if (nodes.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.progressivestages.tree.empty"),
                (left + right) / 2, (contentTop + bottom) / 2, 0xFF888888);
            return;
        }

        renderList(g, mouseX, mouseY);
        renderDetail(g, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left, x1 = dividerX;
        int areaTop = contentTop, areaBottom = bottom;
        g.enableScissor(x0, areaTop, x1, areaBottom);
        int y = areaTop + PAD - listScroll;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int rowTop = y;
            y += ROW_H;
            if (rowTop + ROW_H < areaTop || rowTop > areaBottom) continue;

            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= rowTop && mouseY < rowTop + ROW_H
                && mouseY >= areaTop && mouseY < areaBottom;
            if (i == selected) g.fill(x0, Math.max(rowTop, areaTop), x1, Math.min(rowTop + ROW_H, areaBottom), 0x553A6EA5);
            else if (hover) g.fill(x0, Math.max(rowTop, areaTop), x1, Math.min(rowTop + ROW_H, areaBottom), 0x22FFFFFF);

            int rowX = x0 + PAD + node.depth() * 10;
            g.renderItem(iconFor(node.id()), rowX, rowTop + 1);
            ChatFormatting color = node.owned() ? ChatFormatting.GREEN
                : node.available() ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            String marker = node.owned() ? "✔ " : node.available() ? "➤ " : "🔒 ";
            g.drawString(this.font, Component.literal(marker + ClientStageCache.getDisplayName(node.id())).withStyle(color),
                rowX + 20, rowTop + 5, 0xFFFFFFFF);
        }
        g.disableScissor();

        if (listMax > 0) {
            int trackH = areaBottom - areaTop;
            int thumbH = Math.max(16, trackH * trackH / (trackH + listMax));
            int thumbY = areaTop + (trackH - thumbH) * listScroll / listMax;
            g.fill(dividerX - 3, thumbY, dividerX - 1, thumbY + thumbH, 0xFF777777);
        }
    }

    private void renderDetail(GuiGraphics g, int mouseX, int mouseY) {
        if (selected < 0) return;
        Node node = nodes.get(selected);
        StageId id = node.id();
        int x0 = dividerX + PAD, x1 = right - PAD;
        ClientTriggerProgress.StageData data = ClientTriggerProgress.get(id);
        // Reserve a strip at the bottom for the purchase button (skill-tree mode) so content never
        // renders under it.
        boolean showBuy = data.purchasable() && !node.owned();
        int areaTop = contentTop, areaBottom = bottom - PAD - (showBuy ? 22 : 0);
        int paneW = x1 - x0;

        g.enableScissor(dividerX + 1, areaTop, right, areaBottom);
        int dy = areaTop + PAD - detailScroll;
        ItemStack hoverStack = null;

        // Header
        g.renderItem(iconFor(id), x0, dy);
        ChatFormatting nameColor = node.owned() ? ChatFormatting.GREEN
            : node.available() ? ChatFormatting.YELLOW : ChatFormatting.RED;
        g.drawString(this.font, Component.literal(ClientStageCache.getDisplayName(id))
            .withStyle(nameColor, ChatFormatting.BOLD), x0 + 20, dy + 1, 0xFFFFFFFF);
        g.drawString(this.font, Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY),
            x0 + 20, dy + 11, 0xFFFFFFFF);
        dy += 24;

        String statusKey = node.owned() ? "&aUnlocked" : node.available() ? "&eReady to unlock" : "&cLocked";
        g.drawString(this.font, TextUtil.parseColorCodes("Status: " + statusKey), x0, dy, 0xFFFFFFFF);
        dy += 13;

        // Description (wrapped)
        String desc = ClientStageCache.getDescription(id);
        if (desc != null && !desc.isEmpty()) {
            for (FormattedCharSequence line : this.font.split(TextUtil.parseColorCodes("&7" + desc), paneW)) {
                g.drawString(this.font, line, x0, dy, 0xFFFFFFFF);
                dy += 10;
            }
            dy += 3;
        }

        // Prerequisites — a multi-dependency gate requires ALL of these (the merge points of the DAG).
        List<StageId> deps = ClientStageCache.getDependencies(id);
        if (!deps.isEmpty()) {
            String header = deps.size() > 1 ? "Requires ALL of:" : "Requires:";
            g.drawString(this.font, Component.literal(header).withStyle(ChatFormatting.GRAY), x0, dy, 0xFFFFFFFF);
            dy += 11;
            for (StageId dep : deps) {
                boolean has = ClientStageCache.hasStage(dep);
                g.drawString(this.font, TextUtil.parseColorCodes((has ? "  &a✔ " : "  &c✗ ")
                    + ClientStageCache.getDisplayName(dep)), x0, dy, 0xFFFFFFFF);
                dy += 10;
            }
            dy += 3;
        }

        // Leads to — the forward branches (stages that list THIS one as a prerequisite). Makes the
        // "two big paths" / branching structure navigable from any node.
        List<StageId> leadsTo = dependentsOf(id);
        if (!leadsTo.isEmpty()) {
            g.drawString(this.font, Component.literal("Leads to:").withStyle(ChatFormatting.GRAY), x0, dy, 0xFFFFFFFF);
            dy += 11;
            for (StageId next : leadsTo) {
                ChatFormatting c = ClientStageCache.hasStage(next) ? ChatFormatting.GREEN : ChatFormatting.GRAY;
                g.drawString(this.font, Component.literal("  → " + ClientStageCache.getDisplayName(next)).withStyle(c),
                    x0, dy, 0xFFFFFFFF);
                dy += 10;
            }
            dy += 3;
        }

        // Progress to unlock
        if (data.hasTriggers()) {
            float pct = Math.max(0f, data.percent());
            g.drawString(this.font, TextUtil.parseColorCodes("Progress to unlock: &f" + Math.round(pct * 100) + "%"), x0, dy, 0xFFFFFFFF);
            dy += 11;
            drawBar(g, x0, dy, paneW, pct);
            dy += 9;
            for (ClientTriggerProgress.Rule rule : data.rules()) {
                String head = "  " + (rule.satisfied() ? "&a✔" : "&7…") + " &8" + rule.mode()
                    + (rule.description().isEmpty() ? "" : " &7" + rule.description());
                g.drawString(this.font, TextUtil.parseColorCodes(head), x0, dy, 0xFFFFFFFF);
                dy += 10;
                for (ClientTriggerProgress.Cond c : rule.conditions()) {
                    String mark = c.satisfied() ? "&a✔" : "&c✗";
                    int shown = Math.min(c.current(), c.threshold());
                    g.drawString(this.font, TextUtil.parseColorCodes("    " + mark + " &f" + c.label()
                        + " &7" + shown + "/" + c.threshold()), x0, dy, 0xFFFFFFFF);
                    dy += 10;
                }
            }
            dy += 3;
        } else if (!node.owned()) {
            g.drawString(this.font, TextUtil.parseColorCodes("&8Granted by command or progression."), x0, dy, 0xFFFFFFFF);
            dy += 13;
        }

        // Unlocks grid
        List<net.minecraft.resources.ResourceLocation> sample = data.unlockSample();
        if (data.unlockTotal() > 0) {
            g.drawString(this.font, Component.literal("Unlocks (" + data.unlockTotal() + " items):")
                .withStyle(ChatFormatting.GRAY), x0, dy, 0xFFFFFFFF);
            dy += 12;
            int cols = Math.max(1, paneW / GRID);
            for (int i = 0; i < sample.size(); i++) {
                int col = i % cols, rowI = i / cols;
                int ix = x0 + col * GRID;
                int iy = dy + rowI * GRID;
                ItemStack st = BuiltInRegistries.ITEM.getOptional(sample.get(i))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
                if (st.isEmpty()) continue;
                g.renderItem(st, ix, iy);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16
                    && mouseY >= areaTop && mouseY < areaBottom) {
                    hoverStack = st;
                }
            }
            int rows = (sample.size() + cols - 1) / cols;
            dy += rows * GRID + 2;
            if (data.unlockTotal() > sample.size()) {
                g.drawString(this.font, Component.literal("+" + (data.unlockTotal() - sample.size()) + " more…")
                    .withStyle(ChatFormatting.DARK_GRAY), x0, dy, 0xFFFFFFFF);
                dy += 11;
            }
        }

        g.disableScissor();

        // Track content height for scroll clamping (applied next frame).
        detailContentH = (dy + detailScroll) - (areaTop + PAD);
        detailMax = Math.max(0, detailContentH - (areaBottom - areaTop));
        if (detailScroll > detailMax) detailScroll = detailMax;

        // Detail scrollbar
        if (detailMax > 0) {
            int trackH = areaBottom - areaTop;
            int thumbH = Math.max(16, trackH * trackH / (trackH + detailMax));
            int thumbY = areaTop + (trackH - thumbH) * detailScroll / detailMax;
            g.fill(right - 3, thumbY, right - 1, thumbY + thumbH, 0xFF777777);
        }

        // Purchase button (fixed strip at the bottom of the detail pane; skill-tree mode)
        buyEnabled = false;
        buyStage = null;
        if (showBuy) {
            buyX = x0; buyY = bottom - PAD - 16; buyW = x1 - x0; buyH = 16;
            buyStage = id;
            boolean can = data.canPurchase();
            buyEnabled = can;
            boolean hover = can && mouseX >= buyX && mouseX <= buyX + buyW && mouseY >= buyY && mouseY <= buyY + buyH;
            g.fill(buyX, buyY, buyX + buyW, buyY + buyH, can ? (hover ? 0xFF3E8C46 : 0xFF2E6B33) : 0xFF3A2A2A);
            g.fill(buyX, buyY, buyX + buyW, buyY + 1, can ? 0xFF55E066 : 0xFF7A5A5A);
            String label = (can ? "Unlock — " : "Need: ") + data.costSummary();
            int tw = this.font.width(label);
            g.drawString(this.font, label, buyX + Math.max(2, (buyW - tw) / 2), buyY + 4,
                can ? 0xFFFFFFFF : 0xFFB0B0B0, false);
        }

        // Item tooltip (outside scissor so it can overflow the pane)
        if (hoverStack != null) {
            g.renderTooltip(this.font, hoverStack, mouseX, mouseY);
        }
    }

    private void drawBar(GuiGraphics g, int x, int y, int w, float frac) {
        int h = 6;
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF303036);
        int fill = (int) ((w - 2) * Math.max(0f, Math.min(1f, frac)));
        int color = frac >= 1f ? 0xFF55E066 : 0xFF4FA3E0;
        if (fill > 0) g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
    }

    private ItemStack iconFor(StageId id) {
        var icon = ClientStageCache.getIcon(id);
        if (icon.isPresent()) {
            var item = BuiltInRegistries.ITEM.getOptional(icon.get());
            if (item.isPresent()) return new ItemStack(item.get());
        }
        return new ItemStack(Items.BOOK);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // v2.4: purchase button
        if (button == 0 && buyEnabled && buyStage != null
                && mouseX >= buyX && mouseX <= buyX + buyW && mouseY >= buyY && mouseY <= buyY + buyH) {
            ClientTriggerProgress.requestPurchase(buyStage);
            return true;
        }
        if (button == 0 && mouseX >= left && mouseX < dividerX && mouseY >= contentTop && mouseY < bottom) {
            int idx = (int) ((mouseY - (contentTop + PAD) + listScroll) / ROW_H);
            if (idx >= 0 && idx < nodes.size()) {
                if (idx != selected) detailScroll = 0;
                selected = idx;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < dividerX) {
            if (listMax > 0) {
                listScroll = (int) Math.max(0, Math.min(listMax, listScroll - scrollY * ROW_H));
                return true;
            }
        } else if (detailMax > 0) {
            detailScroll = (int) Math.max(0, Math.min(detailMax, detailScroll - scrollY * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
