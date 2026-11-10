package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.evolution.*;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Floating parchment: independent vertical scrolling, native MC text, server-authoritative purchases. */
public class EvolutionScreen extends Screen {
    private static final int INK = EvolutionInk.INK, ROW_GAP = 44;
    private static final float LABEL_SCALE = .8f;
    private static final int LABEL_LINE_HEIGHT = 8, LABEL_INSET = 6, SCROLLBAR_WIDTH = 7;
    private final Screen parent;
    private EvolutionRoute displayedRoute;
    private EvolutionTreePlan plan;
    private List<EvolutionNode> ordered = List.of();
    private final Map<Integer, Integer> rowTops = new HashMap<>();
    private String selectedId, waitingId;
    private long waitingSince;
    private boolean opened, draggingTree, draggingThumb, draggingDetailsThumb;
    private double dragY, dragScroll, treeScroll, detailScroll, thumbGrabOffset;
    private int minDepth, minLane, maxLane, treeContentHeight, detailContentHeight;
    private Box paper = Box.EMPTY, tree = Box.EMPTY, details = Box.EMPTY, detailBody = Box.EMPTY;
    private Box header = Box.EMPTY;
    private Box confirm = Box.EMPTY, back = Box.EMPTY;

    public EvolutionScreen(Screen parent) {
        super(Text.translatable("evolution.my_addon.screen.title"));
        this.parent = parent;
    }
    private record Box(int x, int y, int w, int h) {
        static final Box EMPTY = new Box(0, 0, 0, 0);
        int right() { return x + w; }
        int bottom() { return y + h; }
        boolean contains(double mx, double my) { return mx >= x && mx < right() && my >= y && my < bottom(); }
    }
    private EvolutionComponent component() {
        return client == null || client.player == null ? null : RegEvolutionComponent.EVOLUTION.get(client.player);
    }
    private EvolutionRoute route() {
        EvolutionComponent comp = component();
        if (comp != null && comp.getRoute() != null && !comp.getRoute().isEmpty())
            return EvolutionRegistry.INSTANCE.getRoute(comp.getRoute());
        if (client == null || client.player == null) return null;
        var form = RegPlayerFormComponent.PLAYER_FORM.get(client.player).nowForm;
        return form == null ? null : EvolutionRegistry.INSTANCE.getRouteByStartForm(form.getFormID());
    }
    @Override protected void init() {
        layout();
        if (!opened) {
            ClientPlayNetworking.send(SscAddonNetworking.PACKET_EVO_OPEN, PacketByteBufs.empty());
            opened = true;
        }
    }
    private void layout() {
        int ph = Math.min(Math.max(100, Math.min(440, (int)(height * .88))), Math.max(1, height - 12));
        int pw = Math.min(Math.max(180, Math.min(700, Math.min((int)(width * .91), (int)(ph * 1.5)))), Math.max(1, width - 12));
        paper = new Box((width - pw) / 2, (height - ph) / 2, pw, ph);
        int ix = paper.x + Math.max(8, pw * 25 / 1000), iy = paper.y + Math.max(8, ph * 3 / 100);
        int iw = pw - 2 * (ix - paper.x), ih = ph - 2 * (iy - paper.y), split = paper.x + pw * 69 / 100;
        int treeX = paper.x + pw * 9 / 100, treeRight = Math.min(paper.x + pw * 67 / 100 + 6, split - 2);
        tree = new Box(treeX, iy + 72, Math.max(1, treeRight - treeX), Math.max(1, ih - 98));
        details = new Box(split + 9, iy, Math.max(1, ix + iw - split - 9), ih);
        detailBody = new Box(details.x, details.y + 55, details.w, Math.max(1, details.h - 130));
        int buttonWidth = Math.max(1, Math.min(details.w - 8, Math.max(42, details.w * 43 / 100)));
        int buttonCenter = Math.max(buttonWidth / 2 + 3, Math.min(details.w - buttonWidth / 2 - 3, details.w * 44 / 100));
        confirm = new Box(details.x + buttonCenter - buttonWidth / 2, details.bottom() - 29, buttonWidth, 19);
        int hx = Math.max(ix + Math.max(12, pw * 3 / 100), tree.x), hy = iy + Math.max(10, ph * 25 / 1000);
        header = new Box(hx, hy, Math.max(1, tree.right() - hx), 51);
        back = new Box(tree.right() - 50, hy, 47, 16);
        refreshRoute();
        layoutRows();
        clampScroll();
    }
    private void refreshRoute() {
        EvolutionRoute current = route();
        if (current == displayedRoute) return;
        displayedRoute = current;
        ordered = current == null ? List.of() : current.nodes.stream()
                .sorted(Comparator.comparingInt((EvolutionNode n) -> n.col).thenComparingInt(n -> n.row)).toList();
        minDepth = ordered.stream().mapToInt(n -> n.col).min().orElse(0);
        minLane = ordered.stream().mapToInt(n -> n.row).min().orElse(0);
        maxLane = ordered.stream().mapToInt(n -> n.row).max().orElse(0);
        plan = new EvolutionTreePlan(ordered.stream().map(n -> new EvolutionTreePlan.Node(n.id, n.col, n.row, n.cost, n.autoUnlock, n.prereqs)).toList());
        if (current == null || current.getNode(selectedId) == null) {
            EvolutionComponent comp = component();
            selectedId = ordered.stream().filter(n -> !n.autoUnlock && comp != null && !comp.isUnlocked(n.id) && preMet(comp, n))
                    .map(n -> n.id).findFirst().orElse(ordered.isEmpty() ? null : ordered.get(0).id);
        }
        treeScroll = detailScroll = 0;
    }
    private void layoutRows() {
        rowTops.clear();
        int top = 8;
        for (int depth : ordered.stream().mapToInt(n -> n.col).distinct().sorted().toArray()) {
            rowTops.put(depth, top);
            int rowHeight = 0;
            for (EvolutionNode node : ordered) if (node.col == depth)
                rowHeight = Math.max(rowHeight, cardHeight(node));
            top += rowHeight + ROW_GAP;
        }
        treeContentHeight = ordered.isEmpty() ? 0 : top - ROW_GAP + 12;
    }
    private EvolutionNode selected() { return displayedRoute == null ? null : displayedRoute.getNode(selectedId); }
    private int cx(EvolutionNode n) { return tree.x + 6 + (tree.w - 18) * (n.row - minLane + 1) / (maxLane - minLane + 2); }
    private int cy(EvolutionNode n) { return tree.y + rowTops.getOrDefault(n.col, 8) + nodeSize(n) / 2 + 4 - (int)treeScroll; }
    private int cardWidthLimit(EvolutionNode node) {
        int nearest = tree.w - 20;
        for (EvolutionNode other : ordered) if (other.col == node.col && other != node)
            nearest = Math.min(nearest, Math.abs(cx(other) - cx(node)));
        int edgeRoom = 2 * Math.min(cx(node) - tree.x, tree.right() - 12 - cx(node)) - 8;
        int compactWidth = node.branch.isEmpty() ? 46 : 64;
        return Math.max(24, Math.min(compactWidth, Math.min(nearest - 12, edgeRoom)));
    }
    private int nodeSize(EvolutionNode n) {
        int desired = !n.branch.isEmpty() ? 48 : n.autoUnlock ? 26 : Math.min(40, 26 + (n.col - minDepth) * 2);
        return Math.max(12, Math.min(desired, cardWidthLimit(n) - 8));
    }
    private int labelWidth(EvolutionNode node) {
        return Math.max(1, (int)((cardWidthLimit(node) - 4 - LABEL_INSET * 2) / LABEL_SCALE));
    }
    private List<OrderedText> labelLines(EvolutionNode node) {
        return textRenderer.wrapLines(Text.literal(shortName(node)), labelWidth(node));
    }
    private int labelHeight(EvolutionNode node) { return labelLines(node).size() * LABEL_LINE_HEIGHT; }
    private int cardHeight(EvolutionNode node) { return nodeSize(node) + labelHeight(node) + 15; }
    private Box nodeCard(EvolutionNode n) {
        int size = nodeSize(n);
        int label = labelLines(n).stream().mapToInt(textRenderer::getWidth).max().orElse(0);
        int w = Math.max(size + 8, (int)Math.ceil(label * LABEL_SCALE) + 4 + LABEL_INSET * 2);
        return new Box(cx(n) - w / 2, cy(n) - size / 2 - 4, w, cardHeight(n));
    }
    private Box labelPlate(EvolutionNode node) {
        Box card = nodeCard(node);
        return new Box(card.x + 2, cy(node) + nodeSize(node) / 2 + 1, card.w - 4, labelHeight(node) + 8);
    }
    private Box labelContent(EvolutionNode node) {
        Box plate = labelPlate(node);
        return new Box(plate.x + LABEL_INSET, plate.y + 4, plate.w - LABEL_INSET * 2, plate.h - 8);
    }
    private void drawNodeLabel(DrawContext ctx, EvolutionNode node) {
        Box plate = labelPlate(node), content = labelContent(node);
        EvolutionInk.plaque(ctx, plate.x, plate.y, plate.w, plate.h);
        // This intersects with the tree viewport's existing scissor and restores it afterwards.
        ctx.enableScissor(content.x, content.y, content.right(), content.bottom());
        try {
            List<OrderedText> lines = labelLines(node);
            for (int i = 0; i < lines.size(); i++) {
                OrderedText line = lines.get(i);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(content.x + (content.w - textRenderer.getWidth(line) * LABEL_SCALE) / 2f,
                        content.y + i * LABEL_LINE_HEIGHT, 1);
                ctx.getMatrices().scale(LABEL_SCALE, LABEL_SCALE, 1);
                ctx.drawText(textRenderer, line, 0, 0, INK, false);
                ctx.getMatrices().pop();
            }
        } finally {
            ctx.disableScissor();
        }
    }
    private String shortName(EvolutionNode n) { return tr(n.nameKey).replace("技能：", ""); }
    private static String clean(Text text) { return Objects.requireNonNullElse(Formatting.strip(text.getString()), ""); }
    private boolean preMet(EvolutionComponent comp, EvolutionNode n) { return EvolutionSelection.prerequisitesMet(comp, n, Set.of()); }
    private boolean selectingRoute(EvolutionComponent comp, EvolutionNode n) {
        return comp != null && !comp.isOnSscaRoute() && !comp.isJobChanging() && displayedRoute != null && Objects.equals(displayedRoute.getBaseNodeId(), n.id);
    }
    private boolean canBuy(EvolutionComponent comp, EvolutionNode n) {
        return n != null && waitingId == null && comp != null && (selectingRoute(comp, n)
                || comp.isOnSscaRoute() && !comp.isJobChanging() && !n.autoUnlock && !comp.isUnlocked(n.id) && preMet(comp, n) && comp.getPoints() >= n.cost);
    }
    @Override public void tick() {
        EvolutionComponent comp = component();
        if (waitingId == null || comp == null) return;
        if (comp.isUnlocked(waitingId)) {
            waitingId = null;
            if (client.player != null) client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, .5f, 1f);
        } else if (Util.getMeasuringTimeMs() - waitingSince > 3000) waitingId = null;
    }
    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        layout();
        EvolutionComponent comp = component();
        ctx.getMatrices().push();
        // Keep the complete parchment above depth left by form-specific HUD and first-person renderers.
        ctx.getMatrices().translate(0, 0, 400);
        try {
            ctx.fill(0, 0, width, height, 0x32000000);
            EvolutionInk.paper(ctx, paper.x, paper.y, paper.w, paper.h);
            // Paper, transparent route ornaments, and controls occupy separate depth planes.
            ctx.getMatrices().translate(0, 0, 1);
            String route = displayedRoute == null ? "axolotl" : displayedRoute.routeId;
            EvolutionArtwork.decorations(ctx, route, paper.x, paper.y, paper.w, paper.h);
            Box treeThumb = scrollbarThumb(tree, treeScroll, treeContentHeight);
            if (route.equals("axolotl"))
                EvolutionArtwork.attachedCoral(ctx, paper.x, paper.y, paper.w, paper.h, treeThumb.w > 0 ? treeThumb.x + 1 : -1);
            // Foreground and opaque paper must occupy different depth planes.
            ctx.getMatrices().translate(0, 0, 10);
            drawHeader(ctx, comp);
            EvolutionInk.line(ctx, details.x - 7, details.y, details.x - 7, details.bottom());
            drawTree(ctx, comp);
            drawDetails(ctx, comp, mx, my);
            button(ctx, back, tr("gui.back"), true, back.contains(mx, my));
            int hintY = paper.bottom() - Math.max(8, paper.h * 3 / 100) - 12;
            text(ctx, fit(tr("evolution.my_addon.paper.scroll"), tree.w - 12), tree.x, hintY);
        } finally {
            ctx.draw();
            ctx.getMatrices().pop();
        }
    }
    private void drawHeader(DrawContext ctx, EvolutionComponent comp) {
        int x = header.x, y = header.y;
        EvolutionInk.frame(ctx, x, y, 44, 45);
        EvolutionInk.icon(ctx, displayedRoute == null ? "" : displayedRoute.routeId, x + 5, y + 5, 34);
        int tx = x + 51;
        String title = displayedRoute == null ? clean(getTitle()) : tr(displayedRoute.displayNameKey);
        int titleScale = tree.right() - tx >= 220 ? 2 : 1;
        int titleWidth = Math.max(20, (back.x - tx - 46) / titleScale);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(tx, y + 1, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1);
        text(ctx, fit(title, titleWidth), 0, 0);
        ctx.getMatrices().pop();
        int badgeX = tx + textRenderer.getWidth(fit(title, titleWidth)) * titleScale + 6;
        String badge = tr("evolution.my_addon.paper.experimental_badge");
        badge = fit(badge, Math.max(1, back.x - badgeX - 14));
        EvolutionInk.plaque(ctx, badgeX, y + 2, textRenderer.getWidth(badge) + 8, 12);
        text(ctx, badge, badgeX + 4, y + 4);
        int orbY = y + 15;
        EvolutionInk.orb(ctx, tx, orbY, 22);
        centerText(ctx, Integer.toString(comp == null ? 0 : comp.getPoints()), tx + 11, orbY + 7);
        int bx = tx + 26, bw = Math.max(8, Math.min(126, tree.right() - bx - 3));
        int previous = 0, next = -1;
        if (displayedRoute != null && comp != null) for (int milestone : displayedRoute.getLevelMilestones()) {
            if (!comp.hasGrantedLevel(milestone)) { next = milestone; break; }
            previous = milestone;
        }
        int exp = comp == null ? 0 : comp.getExp();
        int floor = EvolutionComponent.levelToTotalExp(previous) * 2;
        int goal = next < 0 ? floor : EvolutionComponent.levelToTotalExp(next) * 2;
        double progress = next < 0 ? 1 : Math.max(0, Math.min(1, (exp - floor) / (double)Math.max(1, goal - floor)));
        ctx.fill(bx + 3, orbY + 6, bx + 3 + (int)((bw - 6) * progress), orbY + 9, INK);
        EvolutionArtwork.nine(ctx, "experience_bar", bx, orbY + 3, bw, 9);
        drawWrapped(ctx, next < 0 ? tr("evolution.my_addon.paper.points_complete") : tr("evolution.my_addon.paper.next_point", Math.max(0, goal - exp)), bx, orbY + 14, bw, 2);
        EvolutionInk.line(ctx, tree.x, tree.y - 4, tree.right(), tree.y - 4);
    }
    private void drawTree(DrawContext ctx, EvolutionComponent comp) {
        ctx.enableScissor(tree.x, tree.y, tree.right(), tree.bottom());
        if (comp == null || displayedRoute == null) {
            drawWrapped(ctx, tr("evolution.my_addon.paper.no_route"), tree.x + 10, tree.y + 12, tree.w - 20, 5);
        } else {
            for (EvolutionNode node : ordered) for (String prerequisite : node.prereqs) {
                EvolutionNode pre = displayedRoute.getNode(prerequisite);
                if (pre == null) continue;
                int sx = cx(pre), sy = nodeCard(pre).bottom() + 4;
                int ex = cx(node), ey = nodeCard(node).y - 4, middle = (sy + ey) / 2;
                EvolutionInk.line(ctx, sx, sy, sx, middle);
                EvolutionInk.line(ctx, sx, middle, ex, middle);
                EvolutionInk.line(ctx, ex, middle, ex, ey);
            }
            for (EvolutionNode node : ordered) {
                int size = nodeSize(node), x = cx(node) - size / 2, y = cy(node) - size / 2;
                Box card = nodeCard(node);
                if (card.y > tree.bottom() || card.bottom() < tree.y) continue;
                EvolutionInk.frame(ctx, card.x, card.y, card.w, card.h);
                EvolutionInk.icon(ctx, node.id, x, y, size);
                if (comp.isUnlocked(node.id)) EvolutionInk.check(ctx, x, y, size);
                drawNodeLabel(ctx, node);
                if (node.id.equals(selectedId)) {
                    EvolutionInk.selection(ctx, card.x - 4, card.y - 4, card.w + 8, card.h + 8);
                }
            }
            Set<String> unlocked = new HashSet<>();
            for (EvolutionNode node : ordered) if (comp.isUnlocked(node.id)) unlocked.add(node.id);
            for (EvolutionTreePlan.Boundary boundary : plan.boundaries(unlocked)) drawBoundary(ctx, boundary);
        }
        ctx.disableScissor();
        scrollbar(ctx, tree, treeScroll, treeContentHeight);
    }
    private void drawBoundary(DrawContext ctx, EvolutionTreePlan.Boundary boundary) {
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, y = Integer.MIN_VALUE;
        for (String id : boundary.nodes()) {
            EvolutionNode node = displayedRoute.getNode(id);
            if (node == null) continue;
            left = Math.min(left, nodeCard(node).x - 6);
            right = Math.max(right, nodeCard(node).right() + 6);
            y = Math.max(y, nodeCard(node).bottom() + 11);
        }
        if (left == Integer.MAX_VALUE || y < tree.y + 5 || y + 5 >= tree.bottom()) return;
        String value = Integer.toString(boundary.cumulativeCost());
        boolean leftSide = boundary.nodes().size() == 1 && (left + right) / 2 < tree.x + tree.w / 2;
        left = Math.max(tree.x + 3 + (leftSide ? textRenderer.getWidth(value) + 3 : 0), left);
        right = Math.min(tree.right() - 9 - (leftSide ? 0 : textRenderer.getWidth(value) + 3), right);
        EvolutionInk.boundary(ctx, left, y, Math.max(1, right - left));
        text(ctx, value, leftSide ? left - textRenderer.getWidth(value) - 3 : right + 3, y - 4);
    }
    private void drawDetails(DrawContext ctx, EvolutionComponent comp, int mx, int my) {
        EvolutionNode node = selected();
        if (node == null || comp == null) return;
        int iconSize = Math.min(38, Math.max(24, details.w / 3));
        EvolutionInk.icon(ctx, node.id, details.x, details.y + 2, iconSize);
        if (comp.isUnlocked(node.id)) EvolutionInk.check(ctx, details.x, details.y + 2, iconSize);
        int titleScale = details.w >= 140 ? 2 : 1;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(details.x + iconSize + 5, details.y + 5, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1);
        drawWrapped(ctx, shortName(node), 0, 0, Math.max(10, (details.w - iconSize - 5) / titleScale), titleScale == 2 ? 2 : 4);
        ctx.getMatrices().pop();
        EvolutionInk.line(ctx, details.x, detailBody.y - 5, details.right(), detailBody.y - 5);
        List<OrderedText> lines = new ArrayList<>(textRenderer.wrapLines(Text.literal(tr(node.descKey)), Math.max(1, details.w - 7)));
        lines.add(Text.empty().asOrderedText());
        addLines(lines, tr("evolution.my_addon.paper.requirements"));
        if (node.prereqs.isEmpty()) addLines(lines, tr("evolution.my_addon.paper.no_prerequisites"));
        for (String id : node.prereqs) {
            EvolutionNode pre = displayedRoute.getNode(id);
            if (pre != null) addLines(lines, (comp.isUnlocked(id) ? "+ " : "- ") + shortName(pre));
        }
        if (node.autoUnlock && !node.branch.isEmpty()) addLines(lines, tr("evolution.my_addon.paper.auto_level", displayedRoute.autoBranchLevel));
        if (!preMet(comp, node)) addLines(lines, tr("evolution.my_addon.paper.all_prerequisites"));
        if (comp.isJobChanging()) addLines(lines, tr("evolution.my_addon.paper.job_changing"));
        detailContentHeight = lines.size() * 11;
        clampScroll();
        ctx.enableScissor(detailBody.x, detailBody.y, detailBody.right(), detailBody.bottom());
        int ly = detailBody.y - (int)detailScroll;
        for (OrderedText line : lines) {
            if (ly + 10 >= detailBody.y && ly < detailBody.bottom()) ctx.drawText(textRenderer, line, detailBody.x, ly, INK, false);
            ly += 11;
        }
        ctx.disableScissor();
        scrollbar(ctx, detailBody, detailScroll, detailContentHeight);
        EvolutionInk.line(ctx, details.x, confirm.y - 42, details.right(), confirm.y - 42);
        int costWidth = Math.max(1, Math.min(details.w, (confirm.x + confirm.w / 2 - details.x) * 2));
        List<OrderedText> costLabel = textRenderer.wrapLines(Text.literal(tr("evolution.my_addon.paper.point_cost")), costWidth);
        for (int i = 0; i < Math.min(2, costLabel.size()); i++) {
            OrderedText line = costLabel.get(i);
            ctx.drawText(textRenderer, line, confirm.x + (confirm.w - textRenderer.getWidth(line)) / 2, confirm.y - 35 + i * 10, INK, false);
        }
        centerText(ctx, comp.getPoints() + " / " + node.cost, confirm.x + confirm.w / 2, confirm.y - 13);
        String label = comp.isUnlocked(node.id) ? tr("evolution.my_addon.paper.unlocked")
                : waitingId != null ? tr("evolution.my_addon.paper.waiting")
                : selectingRoute(comp, node) ? tr("evolution.my_addon.paper.start_route")
                : node.autoUnlock ? tr("evolution.my_addon.paper.automatic")
                : !preMet(comp, node) ? tr("evolution.my_addon.paper.need_prerequisites")
                : comp.getPoints() < node.cost ? tr("evolution.my_addon.paper.need_points") : tr("evolution.my_addon.screen.confirm");
        button(ctx, confirm, label, canBuy(comp, node), confirm.contains(mx, my));
    }
    private void addLines(List<OrderedText> lines, String value) { lines.addAll(textRenderer.wrapLines(Text.literal(value), Math.max(1, details.w - 7))); }
    private void text(DrawContext ctx, String value, int x, int y) { ctx.drawText(textRenderer, value, x, y, INK, false); }
    private String tr(String key, Object... args) { return clean(Text.translatable(key, args)); }
    private String fit(String value, int maxWidth) { return textRenderer.trimToWidth(value, Math.max(1, maxWidth)); }
    private void centerText(DrawContext ctx, String value, int x, int y) { text(ctx, value, x - textRenderer.getWidth(value) / 2, y); }
    private void drawWrapped(DrawContext ctx, String value, int x, int y, int w, int limit) {
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(value), Math.max(1, w));
        for (int i = 0; i < Math.min(limit, lines.size()); i++) ctx.drawText(textRenderer, lines.get(i), x, y + i * 10, INK, false);
    }
    private void button(DrawContext ctx, Box box, String label, boolean enabled, boolean hover) {
        EvolutionInk.button(ctx, box.x, box.y, box.w, box.h);
        if (enabled && hover) EvolutionInk.frame(ctx, box.x + 2, box.y + 2, box.w - 4, box.h - 4);
        if (!enabled) for (int x = box.x + 4; x < box.right() - 3; x += 4) ctx.fill(x, box.bottom() - 3, x + 1, box.bottom() - 2, INK);
        if (box == back) EvolutionArtwork.draw(ctx, "back_arrow", box.x + 3, box.y + 3, 10);
        int inset = box == back ? 12 : 0;
        centerText(ctx, fit(label, box.w - 6 - inset), box.x + (box.w + inset) / 2, box.y + (box.h - 8) / 2);
    }
    private Box scrollbarThumb(Box box, double scroll, int contentHeight) {
        if (contentHeight <= box.h || box.h < 8) return Box.EMPTY;
        int trackHeight = box.h - 4, knob = Math.min(trackHeight, Math.max(12, trackHeight * box.h / contentHeight));
        int top = box.y + 2 + (int)((trackHeight - knob) * scroll / Math.max(1, contentHeight - box.h));
        int offset = Math.min((SCROLLBAR_WIDTH + 1) / 2, Math.max(0, paper.right() - box.right() - 5));
        return new Box(box.right() - SCROLLBAR_WIDTH - 1 + offset, top, SCROLLBAR_WIDTH, knob);
    }
    private boolean overScrollbar(Box box, Box thumb, double mx, double my) {
        return thumb.w > 0 && new Box(thumb.x - 1, box.y, thumb.w + 2, box.h).contains(mx, my);
    }
    private void scrollbar(DrawContext ctx, Box box, double scroll, int contentHeight) {
        Box thumb = scrollbarThumb(box, scroll, contentHeight);
        if (thumb.w == 0) return;
        ctx.fill(thumb.x + 2, box.y + 4, thumb.right() - 2, box.bottom() - 4, EvolutionInk.PAPER);
        EvolutionArtwork.vertical(ctx, "scroll_track", thumb.x + 1, box.y + 1, 5, box.h - 2);
        ctx.fill(thumb.x, thumb.y + 2, thumb.right(), thumb.bottom() - 2, EvolutionInk.PAPER);
        EvolutionArtwork.vertical(ctx, "scroll_thumb", thumb.x, thumb.y, thumb.w, thumb.h);
        EvolutionArtwork.stretch(ctx, "scroll_grip", thumb.x + 2, thumb.y + thumb.h / 2 - 2, 3, 5);
    }
    private void clampScroll() {
        treeScroll = Math.max(0, Math.min(Math.max(0, treeContentHeight - tree.h), treeScroll));
        detailScroll = Math.max(0, Math.min(Math.max(0, detailContentHeight - detailBody.h), detailScroll));
    }
    private void select(EvolutionNode node) {
        if (!Objects.equals(selectedId, node.id)) { selectedId = node.id; detailScroll = 0; clickSound(); }
    }
    private void buySelected() {
        EvolutionComponent comp = component();
        EvolutionNode node = selected();
        if (!canBuy(comp, node)) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        boolean chooseRoute = selectingRoute(comp, node);
        buffer.writeString(chooseRoute ? displayedRoute.routeId : node.id);
        ClientPlayNetworking.send(chooseRoute ? SscAddonNetworking.PACKET_EVO_SELECT_ROUTE : SscAddonNetworking.PACKET_EVO_UNLOCK, buffer);
        waitingId = node.id;
        waitingSince = Util.getMeasuringTimeMs();
        clickSound();
    }
    private void clickSound() { if (client != null && client.player != null) client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), .35f, 1f); }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        layout();
        if (button == 0) {
            if (back.contains(mx, my)) { close(); return true; }
            if (confirm.contains(mx, my)) { buySelected(); return true; }
            Box treeThumb = scrollbarThumb(tree, treeScroll, treeContentHeight);
            Box detailThumb = scrollbarThumb(detailBody, detailScroll, detailContentHeight);
            if (overScrollbar(tree, treeThumb, mx, my)) {
                startThumbDrag(treeThumb, my, false); return true;
            }
            if (overScrollbar(detailBody, detailThumb, mx, my)) {
                startThumbDrag(detailThumb, my, true); return true;
            }
            if (tree.contains(mx, my)) {
                for (EvolutionNode node : ordered) {
                    Box hit = nodeCard(node);
                    if (hit.contains(mx, my)) { select(node); return true; }
                }
                draggingTree = true; dragY = my; dragScroll = treeScroll;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    private void startThumbDrag(Box thumb, double my, boolean detail) {
        draggingThumb = !detail; draggingDetailsThumb = detail;
        boolean onThumb = my >= thumb.y && my < thumb.bottom();
        thumbGrabOffset = onThumb ? my - thumb.y : thumb.h / 2.0;
        if (!onThumb) moveThumb(my, detail);
    }
    private void moveThumb(double my, boolean detail) {
        Box box = detail ? detailBody : tree;
        int contentHeight = detail ? detailContentHeight : treeContentHeight;
        Box thumb = scrollbarThumb(box, detail ? detailScroll : treeScroll, contentHeight);
        double scroll = (my - box.y - 2 - thumbGrabOffset) / Math.max(1, box.h - 4 - thumb.h) * Math.max(0, contentHeight - box.h);
        if (detail) detailScroll = scroll; else treeScroll = scroll;
        clampScroll();
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && (draggingThumb || draggingDetailsThumb)) { moveThumb(my, draggingDetailsThumb); return true; }
        if (button == 0 && draggingTree) { treeScroll = dragScroll + dragY - my; clampScroll(); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && (draggingTree || draggingThumb || draggingDetailsThumb)) {
            draggingTree = draggingThumb = draggingDetailsThumb = false; return true;
        }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        if (tree.contains(mx, my) || overScrollbar(tree, scrollbarThumb(tree, treeScroll, treeContentHeight), mx, my)) {
            treeScroll -= amount * 24; clampScroll(); return true;
        }
        if (details.contains(mx, my) || overScrollbar(detailBody, scrollbarThumb(detailBody, detailScroll, detailContentHeight), mx, my)) {
            detailScroll -= amount * 22; clampScroll(); return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { buySelected(); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { treeScroll = 0; return true; }
        if (key == GLFW.GLFW_KEY_END) { treeScroll = treeContentHeight; clampScroll(); return true; }
        if (key == GLFW.GLFW_KEY_TAB && !ordered.isEmpty()) {
            int current = Math.max(0, ordered.indexOf(selected())), direction = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            EvolutionNode next = ordered.get(Math.floorMod(current + direction, ordered.size()));
            select(next); treeScroll = rowTops.getOrDefault(next.col, 8) + nodeSize(next) / 2 + 4 - tree.h / 2.0; clampScroll(); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
