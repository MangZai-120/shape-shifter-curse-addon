package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
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
public class SscaFormSelectScreen extends EvolutionFormSelectionScreen {

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
            String portrait = portraitFor(route.routeId, route.getBaseNodeId());
            String nameKey = "origin." + ns + ".form_" + path + ".name";
            String descKey = "evolution." + ns + ".start.form." + path + ".desc";
            forms.add(new StartForm(fid, route.routeId, portrait, nameKey, descKey));
        }
    }

    private final Screen parent;
    private int page = 0;

    public SscaFormSelectScreen(Screen parent) {
        super(Text.translatable("evolution.ssc_addon.start.select.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildForms();
        page = Math.max(0, Math.min(page, forms.size() - 1));
        int px = panelX();
        int py = panelY();
        boolean multiPage = forms.size() > 1;

        // 翻页按钮（面板两侧中部）；仅有一页时禁用，体现可翻页框架
        ButtonWidget prev = paperButton(px + 40, py + PANEL_H / 2 - 10, 22, 22,
                Text.literal("<"), b -> prevPage(), -1);
        prev.active = multiPage;
        this.addDrawableChild(prev);

        ButtonWidget next = paperButton(px + PANEL_W - 62, py + PANEL_H / 2 - 10, 22, 22,
                Text.literal(">"), b -> nextPage(), 1);
        next.active = multiPage;
        this.addDrawableChild(next);

        // 选择此形态
        ButtonWidget choose = paperButton(this.width / 2 - 80, py + PANEL_H - 54, 160, 20,
                Text.translatable("evolution.ssc_addon.start.select.choose"), b -> chooseCurrentForm(), 0);
        choose.active = !forms.isEmpty();
        this.addDrawableChild(choose);

        // 返回
        this.addDrawableChild(paperButton(this.width / 2 - 60, py + PANEL_H - 30, 120, 20,
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
        StartForm form = forms.isEmpty() ? null : forms.get(page);
        renderFormPage(ctx, mouseX, mouseY, delta, page, forms.size(), form == null ? null :
                new FormPage(form.routeId, form.portrait, Text.translatable(form.nameKey), Text.translatable(form.descKey)), null);
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
