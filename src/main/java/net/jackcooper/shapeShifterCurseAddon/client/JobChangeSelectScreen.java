package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.jackcooper.shapeShifterCurseAddon.client.evolution.EvolutionFormSelectionScreen;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRoute;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;

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
public class JobChangeSelectScreen extends EvolutionFormSelectionScreen {

    /** 一个可转职进入的进化形态条目。 */
    private static final class FormEntry {
        final Identifier formId;
        final String routeId;
        final String portrait;
        final String nameKey;
        final String descKey;

        FormEntry(Identifier formId, String routeId, String portrait, String nameKey, String descKey) {
            this.formId = formId;
            this.routeId = routeId;
            this.portrait = portrait;
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
            String portrait = portraitFor(route.routeId, route.getBaseNodeId());
            String nameKey = "origin." + ns + ".form_" + path + ".name";
            String descKey = "evolution." + ns + ".start.form." + path + ".desc";
            forms.add(new FormEntry(fid, route.routeId, portrait, nameKey, descKey));
        }
    }

    private int page = 0;
    /** 「选择此形态」按钮引用，翻到当前所处形态那页时置灰。 */
    private ButtonWidget chooseButton;

    public JobChangeSelectScreen() {
        super(Text.translatable("evolution.ssc_addon.job_change.select.title"));
    }

    @Override
    protected void init() {
        buildForms();
        page = Math.max(0, Math.min(page, forms.size() - 1));
        int px = panelX();
        int py = panelY();
        boolean multiPage = forms.size() > 1;

        ButtonWidget prev = paperButton(px + 40, py + PANEL_H / 2 - 10, 22, 22,
                Text.literal("<"), b -> prevPage(), -1);
        prev.active = multiPage;
        this.addDrawableChild(prev);

        ButtonWidget next = paperButton(px + PANEL_W - 62, py + PANEL_H / 2 - 10, 22, 22,
                Text.literal(">"), b -> nextPage(), 1);
        next.active = multiPage;
        this.addDrawableChild(next);

        this.chooseButton = paperButton(this.width / 2 - 80, py + PANEL_H - 54, 160, 20,
                Text.translatable("evolution.ssc_addon.start.select.choose"), b -> chooseCurrentForm(), 0);
        this.addDrawableChild(this.chooseButton);
        updateChooseButtonState();

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

    /** 翻到玩家当前所处形态那页时，置灰「选择此形态」按钮（可见但不可点）。 */
    private void updateChooseButtonState() {
        if (chooseButton != null) {
            chooseButton.active = !forms.isEmpty() && !isCurrentPageOwnForm();
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
        FormEntry form = forms.isEmpty() ? null : forms.get(page);
        Text status = isCurrentPageOwnForm() ? Text.translatable("evolution.ssc_addon.job_change.current") : null;
        renderFormPage(ctx, mouseX, mouseY, delta, page, forms.size(), form == null ? null :
                new FormPage(form.routeId, form.portrait, Text.translatable(form.nameKey), Text.translatable(form.descKey)), status);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
