package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRoute;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * SSCA 进化路线 - 开局形态选择界面（类似书籍的可翻页界面）。
 *
 * <p>玩家在 {@link net.onixary.shapeShifterCurseFabric.custom_ui.StartBookScreenV2 翻开幻形者之书界面} 选择
 * 「SSCA 进化路线」并确认后进入本界面。每一页展示一个可选的 SSCA 进化形态（图标 + 名称 + 描述），
 * 点击「选择此形态」即发包让服务端走 SSCA 进化路线并播放进化动画。</p>
 *
 * <p>使用进化树共用的羊皮纸与手绘头像，翻页和开局选择仍由已同步的路线数据驱动。</p>
 */
public class SscaFormSelectScreen extends Screen {

    /** 一个可在开局选择的 SSCA 进化形态条目。 */
    private static final class StartForm {
        final Identifier formId;
        final String routeId;
        final String portrait;
        final String nameKey;
        final String descKey;

        StartForm(Identifier formId, String routeId, String portrait, String nameKey, String descKey) {
            this.formId = formId;
            this.routeId = routeId;
            this.portrait = portrait;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }
    }

    /**
     * 开局可选的 SSCA 进化形态列表（数据驱动：来自所有「已开放且有起点形态」的进化路线）。
     * <p>加新形态只需新增 route JSON，界面自动出现并支持翻页。</p>
     */
    private final List<StartForm> forms = new ArrayList<>();

    /**
     * 开局可选形态的显示顺序（route id 升序）。不在此表中的 route 排在最后、按 route id 字母序。
     * <p>调整翻页顺序只需调整此数组。</p>
     */
    private static final List<String> DISPLAY_ORDER = List.of(
            "familiar_fox",  // 进化使魔（第一页）
            "axolotl"        // 进化美西螈（第二页）
    );

    /** 从已加载的进化路线动态构建可选形态列表（按 {@link #DISPLAY_ORDER} 排序）。 */
    private void buildForms() {
        forms.clear();
        // 先按 DISPLAY_ORDER 收集，保证指定顺序；未列出的 route 追加在末尾（字母序兜底）
        List<EvolutionRoute> ordered = new ArrayList<>();
        // 用 TreeMap 保证未指定 route 的稳定字母序
        java.util.Map<String, EvolutionRoute> remain = new java.util.TreeMap<>();
        for (EvolutionRoute route : EvolutionRegistry.CLIENT.all().values()) {
            if (!route.enabled || route.startForm == null) {
                continue;
            }
            remain.put(route.routeId, route);
        }
        for (String rid : DISPLAY_ORDER) {
            EvolutionRoute r = remain.remove(rid);
            if (r != null) {
                ordered.add(r);
            }
        }
        ordered.addAll(remain.values());

        for (EvolutionRoute route : ordered) {
            Identifier fid = route.startForm;
            Identifier resourceId = net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration.legacy(fid);
            String ns = resourceId.getNamespace();
            String path = resourceId.getPath();
            // 直接使用进化树左上角同一张 64x64 手绘头像。
            String portrait = EvolutionArtwork.hasSprite(route.routeId) ? route.routeId : route.getBaseNodeId();
            String nameKey = "origin." + ns + ".form_" + path + ".name";
            String descKey = "evolution." + ns + ".start.form." + path + ".desc";
            forms.add(new StartForm(fid, route.routeId, portrait == null ? "mana_system" : portrait, nameKey, descKey));
        }
    }

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 210;
    private static final int ICON_SCALE = 3;

    private final Screen parent;
    private int page = 0;

    public SscaFormSelectScreen(Screen parent) {
        super(Text.translatable("evolution.my_addon.start.select.title"));
        this.parent = parent;
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        buildForms();
        page = Math.max(0, Math.min(page, forms.size() - 1));
        int px = panelX();
        int py = panelY();
        boolean multiPage = forms.size() > 1;

        // 翻页按钮（面板两侧中部）；仅有一页时禁用，体现可翻页框架
        ButtonWidget prev = new PaperButton(px + 40, py + PANEL_H / 2 - 10, 22, 22,
                Text.literal("<"), b -> prevPage(), -1);
        prev.active = multiPage;
        this.addDrawableChild(prev);

        ButtonWidget next = new PaperButton(px + PANEL_W - 62, py + PANEL_H / 2 - 10, 22, 22,
                Text.literal(">"), b -> nextPage(), 1);
        next.active = multiPage;
        this.addDrawableChild(next);

        // 选择此形态
        ButtonWidget choose = new PaperButton(this.width / 2 - 80, py + PANEL_H - 54, 160, 20,
                Text.translatable("evolution.my_addon.start.select.choose"), b -> chooseCurrentForm(), 0);
        choose.active = !forms.isEmpty();
        this.addDrawableChild(choose);

        // 返回
        this.addDrawableChild(new PaperButton(this.width / 2 - 60, py + PANEL_H - 30, 120, 20,
                Text.translatable("gui.back"), b -> close(), 0));
    }

    private void prevPage() {
        if (forms.isEmpty()) {
            return;
        }
        page = (page - 1 + forms.size()) % forms.size();
    }

    private void nextPage() {
        if (forms.isEmpty()) {
            return;
        }
        page = (page + 1) % forms.size();
    }

    private void chooseCurrentForm() {
        if (page < 0 || page >= forms.size()) {
            return;
        }
        StartForm form = forms.get(page);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(form.formId.toString());
        ClientPlayNetworking.send(SscAddonNetworking.PACKET_SSCA_START_ROUTE, buf);
        // 关闭所有界面，让玩家看到进化动画
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int px = panelX();
        int py = panelY();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        try {
            ctx.fill(0, 0, width, height, 0x32000000);
            EvolutionInk.paper(ctx, px, py, PANEL_W, PANEL_H);
            ctx.getMatrices().translate(0, 0, 1);
            if (!forms.isEmpty()) EvolutionArtwork.selectionDecorations(ctx, forms.get(page).routeId, px, py, PANEL_W, PANEL_H);
            ctx.getMatrices().translate(0, 0, 10);
            centered(ctx, this.title, width / 2, py + 12, PANEL_W - 64);
            EvolutionInk.divider(ctx, px + 48, py + 36, PANEL_W - 96);
            if (!forms.isEmpty()) {
                // 页码
                Text pageInfo = Text.translatable("evolution.my_addon.start.select.page", page + 1, forms.size());
                centered(ctx, pageInfo, width / 2, py + 25, 64);

                StartForm form = forms.get(page);

                // 形态图标（放大）
                int iconCenterX = this.width / 2;
                int iconTopY = py + 44;
                int side = 16 * ICON_SCALE;
                EvolutionInk.frame(ctx, iconCenterX - side / 2 - 4, iconTopY - 3, side + 8, side + 6);
                EvolutionInk.icon(ctx, form.portrait, iconCenterX - side / 2, iconTopY, side);

                // 形态名称
                Text name = Text.translatable(form.nameKey);
                int nameWidth = Math.min(PANEL_W - 104, textRenderer.getWidth(plain(name)) + 20);
                EvolutionInk.plaque(ctx, width / 2 - nameWidth / 2, iconTopY + side + 5, nameWidth, 17);
                centered(ctx, name, width / 2, iconTopY + side + 10, nameWidth - 16);

                // 形态描述（多行居中）
                var lines = textRenderer.wrapLines(Text.literal(plain(Text.translatable(form.descKey))), PANEL_W - 100);
                int descTop = iconTopY + side + 27, availableHeight = py + PANEL_H - 60 - descTop;
                float scale = Math.min(1, availableHeight / (float)Math.max(1, lines.size() * 10));
                ctx.enableScissor(px + 48, descTop, px + PANEL_W - 48, py + PANEL_H - 60);
                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i);
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(width / 2f - textRenderer.getWidth(line) * scale / 2, descTop + i * 10 * scale, 0);
                    ctx.getMatrices().scale(scale, scale, 1);
                    ctx.drawText(textRenderer, line, 0, 0, EvolutionInk.INK, false);
                    ctx.getMatrices().pop();
                }
                ctx.disableScissor();
            }
            super.render(ctx, mouseX, mouseY, delta);
        } finally { ctx.draw(); ctx.getMatrices().pop(); }
    }
    private String plain(Text value) { return java.util.Objects.requireNonNullElse(Formatting.strip(value.getString()), ""); }
    private void centered(DrawContext ctx, Text value, int cx, int y, int maxWidth) {
        String text = plain(value);
        float scale = Math.min(1, maxWidth / (float)Math.max(1, textRenderer.getWidth(text)));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - textRenderer.getWidth(text) * scale / 2, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(textRenderer, text, 0, 0, EvolutionInk.INK, false);
        ctx.getMatrices().pop();
    }
    private final class PaperButton extends ButtonWidget {
        private final int arrow;
        PaperButton(int x, int y, int w, int h, Text label, PressAction action, int arrow) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION_SUPPLIER);
            this.arrow = arrow;
        }
        @Override public void renderButton(DrawContext ctx, int mx, int my, float delta) {
            EvolutionInk.button(ctx, getX(), getY(), getWidth(), getHeight());
            if (active && (isHovered() || isFocused())) EvolutionInk.frame(ctx, getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4);
            if (arrow == 0) centered(ctx, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, getWidth() - 14);
            else {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(getX() + (arrow > 0 ? getWidth() : 0), getY() + (arrow > 0 ? getHeight() : 0), 0);
                // A half turn preserves the quad winding, so GUI face culling cannot hide the arrow.
                if (arrow > 0) ctx.getMatrices().scale(-1, -1, 1);
                EvolutionArtwork.draw(ctx, "back_arrow", (getWidth() - 14) / 2, (getHeight() - 14) / 2, 14);
                ctx.getMatrices().pop();
            }
            if (!active) for (int x = getX() + 5; x < getX() + getWidth() - 4; x += 4)
                ctx.fill(x, getY() + getHeight() - 4, x + 1, getY() + getHeight() - 3, EvolutionInk.INK);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
