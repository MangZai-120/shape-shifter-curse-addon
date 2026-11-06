package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialCatalog;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient;
import net.onixary.shapeShifterCurseFabric.custom_ui.CodexInstinctColumnHooks;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import org.lwjgl.glfw.GLFW;

/** Two readable, scrollable pages; descriptions share the current Codex rather than a second balance table. */
public final class FormTutorialScreen extends Screen {
    public static final int PAGE_COUNT = 2;
    private final Screen book;
    private final Identifier form;
    private final Runnable onShown;
    private ClientPlayNetworkHandler connection;
    private int page, bodyScroll, left, top, panelWidth, panelHeight, bodyTop, bodyHeight;
    private boolean shown;
    private Text body = Text.empty();
    private List<OrderedText> lines = List.of();
    private ButtonWidget previous, next;

    public FormTutorialScreen(Screen book, Identifier form, Runnable onShown) {
        super(text("title", Text.translatable("codex.form." + form.getNamespace() + "." + form.getPath() + ".name")));
        this.book = book;
        this.form = form;
        this.onShown = onShown;
    }

    public static MutableText text(String key, Object... args) {
        return Text.translatable("tutorial.ssc_addon.form." + key, args);
    }

    @Override protected void init() {
        if (connection == null) connection = client.getNetworkHandler();
        panelWidth = Math.min(500, width - 20);
        panelHeight = Math.min(330, height - 20);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        bodyTop = top + 54;
        bodyHeight = panelHeight - 108;
        int buttonWidth = (panelWidth - 40) / 3;
        int buttonTop = top + panelHeight - 30;
        previous = addDrawableChild(ButtonWidget.builder(text("previous"), button -> move(-1))
                .dimensions(left + 12, buttonTop, buttonWidth, 20).build());
        next = addDrawableChild(ButtonWidget.builder(text("next"), button -> move(1))
                .dimensions(left + 20 + buttonWidth, buttonTop, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(text("skip"), button -> close())
                .dimensions(left + 28 + buttonWidth * 2, buttonTop, buttonWidth, 20).build());
        rebuild();
    }

    private void rebuild() {
        previous.active = page > 0;
        next.setMessage(text(page == PAGE_COUNT - 1 ? "finish" : "next"));
        body = page == 0 ? skills() : effects();
        lines = textRenderer.wrapLines(body, panelWidth - 42);
        bodyScroll = Math.max(0, Math.min(bodyScroll, maxScroll()));
    }

    /** Also used by the real-client acceptance probe to inspect resolved key names and Codex text. */
    public Text content() { return body; }
    public Identifier formId() { return form; }

    private Text skills() {
        return Text.empty().append(text("primary", skillKey(true)).formatted(Formatting.DARK_AQUA))
                .append("\n").append(text("secondary", skillKey(false)).formatted(Formatting.DARK_AQUA))
                .append("\n").append(text("sneak", keyName(client.options.sneakKey)).formatted(Formatting.DARK_AQUA))
                .append("\n").append(text("attack", keyName(client.options.attackKey)).formatted(Formatting.DARK_AQUA))
                .append("\n").append(text("use", keyName(client.options.useKey)).formatted(Formatting.DARK_AQUA))
                .append("\n\n").append(text("keys_hint"))
                .append("\n\n").append(text(form.getPath() + ".skills"));
    }

    private Text skillKey(boolean primary) {
        if (!primary && FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form)) return text("no_secondary");
        var custom = SSCAddonConfig.client().getFormKeybind(form.getPath());
        if (!FormTutorialCatalog.usesSscKeys(form) && custom != null && custom.enabled) {
            String name = primary ? custom.primaryKey : custom.secondaryKey;
            if (name == null || name.isBlank() || "key.keyboard.unknown".equals(name)) return text("unbound");
            try { return InputUtil.fromTranslationKey(name).getLocalizedText(); }
            catch (IllegalArgumentException invalidKey) { return text("unbound"); }
        }
        KeyBinding direct = primary ? ShapeShifterCurseFabricClient.useActiveSkill1PowerKeybind
                : ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind;
        KeyBinding toolbar = ShapeShifterCurseFabricClient.activeSkillKeybind;
        if (direct != null && !direct.isUnbound()) {
            return toolbar != null && !toolbar.isUnbound()
                    ? text("bound_and_toolbar", direct.getBoundKeyLocalizedText(), toolbar.getBoundKeyLocalizedText())
                    : direct.getBoundKeyLocalizedText();
        }
        return toolbar != null && !toolbar.isUnbound() ? text("toolbar_key", toolbar.getBoundKeyLocalizedText()) : text("unbound");
    }

    private static Text keyName(KeyBinding key) {
        return key == null || key.isUnbound() ? text("unbound") : key.getBoundKeyLocalizedText();
    }

    private Text effects() {
        MutableText result = Text.empty();
        if (FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form) || FormIdentifiers.UPGRADE_AXOLOTL.equals(form)) {
            result.append(CodexData.getContentText(CodexData.ContentType.APPEARANCE, client.player)).append("\n\n");
        }
        section(result, "pros", CodexData.getContentText(CodexData.ContentType.PROS, client.player));
        section(result, "cons", CodexData.getContentText(CodexData.ContentType.CONS, client.player));
        var provider = CodexInstinctColumnHooks.provider();
        Text instincts = provider == null ? null : provider.instinctsContent(client.player);
        section(result, "instincts", instincts == null ? CodexData.getContentText(CodexData.ContentType.INSTINCTS, client.player) : instincts);
        return result;
    }

    private static void section(MutableText target, String title, Text content) {
        if (!target.getString().isEmpty()) target.append("\n\n");
        target.append(text(title).formatted(Formatting.DARK_AQUA, Formatting.BOLD)).append("\n").append(content);
    }

    private void move(int offset) {
        if (page + offset >= PAGE_COUNT) { close(); return; }
        page = Math.max(0, page + offset);
        bodyScroll = 0;
        rebuild();
    }

    private int visibleLines() { return Math.max(1, bodyHeight / 11); }
    private int maxScroll() { return Math.max(0, lines.size() - visibleLines()); }
    private void scroll(int amount) { bodyScroll = Math.max(0, Math.min(maxScroll(), bodyScroll + amount)); }

    @Override public void tick() {
        if (client.player == null || !client.player.isAlive() || !form.equals(FormTutorialClient.currentForm(client))) close();
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xffeaddbd);
        context.drawBorder(left, top, panelWidth, panelHeight, 0xff7e6e50);
        context.drawText(textRenderer, textRenderer.trimToWidth(title.getString(), panelWidth - 28), left + 14, top + 13, 0xff302a22, false);
        context.drawText(textRenderer, text(page == 0 ? "skills" : "effects"), left + 14, top + 34, 0xff345564, false);
        Text counter = text("page", page + 1, PAGE_COUNT);
        context.drawText(textRenderer, counter, left + panelWidth - 14 - textRenderer.getWidth(counter), top + 34, 0xff635840, false);
        context.fill(left + 12, bodyTop - 5, left + panelWidth - 12, bodyTop + bodyHeight + 3, 0xfff4ead3);
        for (int i = 0; i < visibleLines() && i + bodyScroll < lines.size(); i++) {
            context.drawText(textRenderer, lines.get(i + bodyScroll), left + 17, bodyTop + i * 11, 0xff302a22, false);
        }
        if (maxScroll() > 0) {
            int x = left + panelWidth - 20;
            int thumb = Math.max(8, bodyHeight * visibleLines() / lines.size());
            int y = bodyTop + (bodyHeight - thumb) * bodyScroll / maxScroll();
            context.fill(x, bodyTop, x + 3, bodyTop + bodyHeight, 0xffd7c9a9);
            context.fill(x, y, x + 3, y + thumb, 0xff7e6e50);
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(text("scroll").getString(), panelWidth - 28),
                left + 14, top + panelHeight - 46, 0xff635840, false);
        super.render(context, mouseX, mouseY, delta);
        if (!shown) { shown = true; onShown.run(); }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll(-Math.round((float) amount * 3));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> scroll(1);
            case GLFW.GLFW_KEY_UP -> scroll(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll(visibleLines());
            case GLFW.GLFW_KEY_PAGE_UP -> scroll(-visibleLines());
            case GLFW.GLFW_KEY_HOME -> bodyScroll = 0;
            case GLFW.GLFW_KEY_END -> bodyScroll = maxScroll();
            case GLFW.GLFW_KEY_RIGHT -> move(1);
            case GLFW.GLFW_KEY_LEFT -> move(-1);
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        client.setScreen(client.world != null && client.player != null && client.player.isAlive()
                && client.getNetworkHandler() == connection ? book : null);
    }
}
