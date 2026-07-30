package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionNode;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRoute;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * 灵能宝珠「转职选择形态」界面（jackcooper 署名新类）。
 *
 * <p>一页一形态翻页展示所有进化形态（图标+名+描述）。玩家当前所处形态那页的「选择此形态」按钮灰显不可点、
 * 并提示「当前形态，不可选」；选定其它进化形态确认后发 {@link SscAddonNetworking#PACKET_JOB_CHANGE_CONFIRM}
 * 到服务端，由 EvolutionManager.startJobChange 校验（≥3 点 + 消耗宝珠）后带动画转职、倒退 3 个里程碑。</p>
 *
 * <p>渲染布局与开局用的 SscaFormSelectScreen 一致（一页一形态、数据驱动 buildForms），但语义是「转职」而非
 * 「开局进入路线」，故独立成类、发不同的确认包，且不改动 onixary 侧原界面。</p>
 */
public class JobChangeSelectScreen extends Screen {

    /** 一个可转职进入的进化形态条目。 */
    private static final class FormEntry {
        final Identifier formId;
        final Item icon;
        final String nameKey;
        final String descKey;

        FormEntry(Identifier formId, Item icon, String nameKey, String descKey) {
            this.formId = formId;
            this.icon = icon;
            this.nameKey = nameKey;
            this.descKey = descKey;
        }
    }

    /** 可转职进入的进化形态列表（数据驱动：所有「已开放且有起点形态」的进化路线）。 */
    private final List<FormEntry> forms = new ArrayList<>();

    /** 显示顺序（route id）；不在此表的按字母序排后。 */
    private static final List<String> DISPLAY_ORDER = List.of("familiar_fox", "axolotl");

    private void buildForms() {
        forms.clear();
        List<EvolutionRoute> ordered = new ArrayList<>();
        java.util.Map<String, EvolutionRoute> remain = new java.util.TreeMap<>();
        for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
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
            String ns = fid.getNamespace();
            String path = fid.getPath();
            Item icon = Items.NETHER_STAR;
            String baseId = route.getBaseNodeId();
            if (baseId != null) {
                EvolutionNode baseNode = route.getNode(baseId);
                if (baseNode != null && baseNode.icon != null) {
                    icon = baseNode.icon;
                }
            }
            String nameKey = "origin." + ns + ".form_" + path + ".name";
            String descKey = "evolution." + ns + ".start.form." + path + ".desc";
            forms.add(new FormEntry(fid, icon, nameKey, descKey));
        }
    }

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 210;
    private static final int ICON_SCALE = 3;

    private int page = 0;
    /** 「选择此形态」按钮引用，翻到当前所处形态那页时置灰。 */
    private ButtonWidget chooseButton;

    public JobChangeSelectScreen() {
        super(Text.translatable("evolution.my_addon.job_change.select.title"));
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
        int px = panelX();
        int py = panelY();
        boolean multiPage = forms.size() > 1;

        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> prevPage())
                .dimensions(px + 6, py + PANEL_H / 2 - 10, 20, 20).build();
        prev.active = multiPage;
        this.addDrawableChild(prev);

        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> nextPage())
                .dimensions(px + PANEL_W - 26, py + PANEL_H / 2 - 10, 20, 20).build();
        next.active = multiPage;
        this.addDrawableChild(next);

        this.chooseButton = ButtonWidget.builder(
                Text.translatable("evolution.my_addon.start.select.choose"),
                b -> chooseCurrentForm()
        ).dimensions(this.width / 2 - 80, py + PANEL_H - 54, 160, 20).build();
        this.addDrawableChild(this.chooseButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.back"),
                b -> this.close()
        ).dimensions(this.width / 2 - 60, py + PANEL_H - 30, 120, 20).build());
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

    /** 翻到玩家当前所处形态那页时，置灰「选择此形态」按钮（可见但不可点）。 */
    private void updateChooseButtonState() {
        if (chooseButton != null) {
            chooseButton.active = !isCurrentPageOwnForm();
        }
    }

    /** 当前页形态是否为玩家当前所处形态（不可转职到自身）。 */
    private boolean isCurrentPageOwnForm() {
        if (page < 0 || page >= forms.size()) {
            return false;
        }
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) {
            return false;
        }
        IForm nowForm = RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm;
        Identifier nowFormId = (nowForm == null) ? null : nowForm.getFormID();
        return forms.get(page).formId.equals(nowFormId);
    }

    private void chooseCurrentForm() {
        if (page < 0 || page >= forms.size()) {
            return;
        }
        if (isCurrentPageOwnForm()) {
            return; // 当前所处形态不可选（双保险，按钮本已置灰）
        }
        FormEntry form = forms.get(page);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(form.formId.toString());
        ClientPlayNetworking.send(SscAddonNetworking.PACKET_JOB_CHANGE_CONFIRM, buf);
        MinecraftClient.getInstance().setScreen(null); // 关界面看变身动画
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        updateChooseButtonState();
        this.renderBackground(ctx);

        int px = panelX();
        int py = panelY();
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, 0xE0140C08);
        drawBorder(ctx, px, py, PANEL_W, PANEL_H, 0xFFB8893A);

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, py + 10, 0xFFE8C66A);

        if (!forms.isEmpty()) {
            Text pageInfo = Text.translatable("evolution.my_addon.start.select.page", page + 1, forms.size());
            ctx.drawCenteredTextWithShadow(this.textRenderer, pageInfo, this.width / 2, py + 24, 0xFFAAAAAA);

            FormEntry form = forms.get(page);

            int iconCenterX = this.width / 2;
            int iconTopY = py + 44;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(iconCenterX - 8 * ICON_SCALE, iconTopY, 0);
            ctx.getMatrices().scale(ICON_SCALE, ICON_SCALE, 1);
            ctx.drawItem(new ItemStack(form.icon), 0, 0);
            ctx.getMatrices().pop();

            Text name = Text.translatable(form.nameKey).formatted(Formatting.GOLD, Formatting.BOLD);
            ctx.drawCenteredTextWithShadow(this.textRenderer, name, this.width / 2, iconTopY + 16 * ICON_SCALE + 8, 0xFFFFFFFF);

            MultilineText desc = MultilineText.create(this.textRenderer,
                    Text.translatable(form.descKey).formatted(Formatting.GRAY), PANEL_W - 40);
            desc.drawCenterWithShadow(ctx, this.width / 2, iconTopY + 16 * ICON_SCALE + 26);

            // 当前所处形态不可选，提示玩家
            if (isCurrentPageOwnForm()) {
                Text cur = Text.translatable("evolution.my_addon.job_change.current").formatted(Formatting.RED, Formatting.ITALIC);
                ctx.drawCenteredTextWithShadow(this.textRenderer, cur, this.width / 2, py + PANEL_H - 68, 0xFFFF6666);
            }
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
