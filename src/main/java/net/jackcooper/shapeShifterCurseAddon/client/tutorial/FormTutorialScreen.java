package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** One illustrated mechanic per page, with contents navigation and the player's real key names. */
public final class FormTutorialScreen extends Screen {
    private static final Pattern KEY_TOKEN = Pattern.compile("\\{(primary|secondary|sneak|attack|use|jump|sprint)\\}");
    private final Screen book;
    private final Identifier form;
    private final Runnable onShown;
    private ClientPlayNetworkHandler connection;
    private List<FormTutorialLessons.Lesson> lessons = List.of();
    private final List<ButtonWidget> entries = new ArrayList<>();
    private int page, bodyScroll, indexScroll, ticks;
    private int left, top, panelWidth, panelHeight, contentTop, contentHeight;
    private int bodyLeft, bodyTop, bodyWidth, bodyHeight, diagramLeft, diagramTop, diagramWidth, diagramHeight;
    private boolean shown, indexOpen;
    private String language = "";
    private Text body = Text.empty();
    private List<OrderedText> lines = List.of();
    private ButtonWidget previous, next, index;

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
        panelWidth = Math.min(660, width - 16);
        panelHeight = Math.min(370, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        contentTop = top + 47;
        contentHeight = Math.max(24, panelHeight - 91);
        diagramLeft = left + 12; diagramTop = contentTop;
        diagramWidth = (panelWidth - 36) / 2; diagramHeight = contentHeight;
        bodyLeft = diagramLeft + diagramWidth + 12; bodyTop = contentTop;
        bodyWidth = panelWidth - diagramWidth - 36; bodyHeight = contentHeight;
        int gap = 5, buttonWidth = (panelWidth - 24 - gap * 3) / 4;
        int buttonTop = top + panelHeight - 28;
        previous = addDrawableChild(ButtonWidget.builder(text("previous"), button -> move(-1))
                .dimensions(left + 12, buttonTop, buttonWidth, 20).build());
        index = addDrawableChild(ButtonWidget.builder(text("contents"), button -> toggleIndex())
                .dimensions(left + 12 + buttonWidth + gap, buttonTop, buttonWidth, 20).build());
        next = addDrawableChild(ButtonWidget.builder(text("next"), button -> move(1))
                .dimensions(left + 12 + (buttonWidth + gap) * 2, buttonTop, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(text("skip"), button -> close())
                .dimensions(left + 12 + (buttonWidth + gap) * 3, buttonTop, buttonWidth, 20).build());
        entries.clear();
        int rows = Math.max(1, contentHeight / 25);
        for (int row = 0; row < rows; row++) {
            final int offset = row;
            entries.add(addDrawableChild(ButtonWidget.builder(Text.empty(), button -> select(indexScroll + offset))
                    .dimensions(left + 14, contentTop + row * 25, panelWidth - 36, 22).build()));
        }
        rebuild();
    }

    private void rebuild() {
        lessons = FormTutorialLessons.forForm(form);
        language = client.options.language;
        page = Math.max(0, Math.min(page, Math.max(0, lessons.size() - 1)));
        previous.active = page > 0;
        next.active = !lessons.isEmpty();
        next.setMessage(text(page == lessons.size() - 1 ? "finish" : "next"));
        index.setMessage(text(indexOpen ? "read_page" : "contents"));
        body = Text.empty();
        if (!lessons.isEmpty()) {
            var lesson = lessons.get(page);
            MutableText result = Text.empty();
            appendSection(result, "how", resolve(lesson, "trigger"), bodyHeight < 150);
            appendSection(result, "result", resolve(lesson, "effect"), bodyHeight < 150);
            appendSection(result, "remember", resolve(lesson, "note"), bodyHeight < 150);
            body = result;
        }
        lines = textRenderer.wrapLines(body, Math.max(20, bodyWidth - 8));
        bodyScroll = Math.max(0, Math.min(bodyScroll, maxScroll()));
        updateIndex();
    }

    private static void appendSection(MutableText result, String title, Text description, boolean compact) {
        if (!result.getString().isEmpty()) result.append(compact ? "\n" : "\n\n");
        result.append(text(title).styled(style -> style.withColor(0x404040).withBold(true))).append("\n").append(description);
    }

    private Text resolve(FormTutorialLessons.Lesson lesson, String section) {
        String value = Text.translatable(lesson.key(section)).getString();
        var matcher = KEY_TOKEN.matcher(value);
        MutableText result = Text.empty();
        int cursor = 0;
        while (matcher.find()) {
            result.append(value.substring(cursor, matcher.start()));
            result.append(FormTutorialKeys.key(client, form, matcher.group(1)));
            cursor = matcher.end();
        }
        return result.append(value.substring(cursor));
    }

    public Text content() { return body; }
    public Identifier formId() { return form; }
    public int lessonCount() { return lessons.size(); }
    public String lessonId() { return lessons.isEmpty() ? "" : lessons.get(page).id(); }

    private void move(int offset) {
        if (page + offset >= lessons.size()) { close(); return; }
        select(Math.max(0, page + offset));
    }

    private void select(int selected) {
        if (selected < 0 || selected >= lessons.size()) return;
        page = selected; bodyScroll = 0; indexOpen = false;
        rebuild();
    }

    private void toggleIndex() {
        indexOpen = !indexOpen;
        updateIndex();
        index.setMessage(text(indexOpen ? "read_page" : "contents"));
    }

    private void updateIndex() {
        indexScroll = Math.max(0, Math.min(indexScroll, Math.max(0, lessons.size() - entries.size())));
        for (int row = 0; row < entries.size(); row++) {
            ButtonWidget button = entries.get(row);
            int number = indexScroll + row;
            button.visible = indexOpen && number < lessons.size();
            if (number < lessons.size()) {
                var lesson = lessons.get(number);
                button.setMessage(Text.literal((number + 1) + ". ")
                        .append(Text.translatable(lesson.key("title"))).append("  ")
                        .append(text("kind." + lesson.kind().name().toLowerCase(Locale.ROOT))));
                button.active = number != page;
            }
        }
    }

    private int visibleLines() { return Math.max(1, bodyHeight / 10); }
    private int maxScroll() { return Math.max(0, lines.size() - visibleLines()); }
    private void scroll(int amount) {
        if (indexOpen) { indexScroll += amount; updateIndex(); }
        else bodyScroll = Math.max(0, Math.min(maxScroll(), bodyScroll + amount));
    }

    @Override public void tick() {
        ticks++;
        if (client.player == null || !client.player.isAlive() || !form.equals(FormTutorialClient.currentForm(client))) { close(); return; }
        if (lessons != FormTutorialLessons.forForm(form) || !language.equals(client.options.language)) rebuild();
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        FormTutorialTextures.window(context, left, top, panelWidth, panelHeight);
        context.drawText(textRenderer, textRenderer.trimToWidth(title.getString(), panelWidth - 90), left + 13, top + 9, 0xff404040, false);
        Text counter = text("page", page + 1, lessons.size());
        context.drawText(textRenderer, counter, left + panelWidth - 13 - textRenderer.getWidth(counter), top + 9, 0xff404040, false);
        if (!lessons.isEmpty()) {
            var lesson = lessons.get(page);
            Text kind = text("kind." + lesson.kind().name().toLowerCase(Locale.ROOT));
            int kindWidth = textRenderer.getWidth(kind) + 10;
            FormTutorialTextures.inset(context, left + 12, top + 25, kindWidth, 16);
            context.drawText(textRenderer, kind, left + 17, top + 28, 0xffe0e0e0, false);
            context.drawText(textRenderer, textRenderer.trimToWidth(
                    Text.translatable(lesson.key("title")).getString(), panelWidth - kindWidth - 35),
                    left + 21 + kindWidth, top + 28, 0xff404040, false);
            if (indexOpen) renderIndex(context);
            else {
                FormTutorialDiagram.render(context, textRenderer, client, lesson,
                        diagramLeft, diagramTop, diagramWidth, diagramHeight, ticks + delta);
                context.enableScissor(bodyLeft - 2, bodyTop, bodyLeft + bodyWidth, bodyTop + bodyHeight);
                for (int i = 0; i < visibleLines() && i + bodyScroll < lines.size(); i++)
                    context.drawText(textRenderer, lines.get(i + bodyScroll), bodyLeft, bodyTop + i * 10, 0xff303030, false);
                context.disableScissor();
                scrollbar(context, bodyLeft + bodyWidth - 2, bodyTop, bodyHeight, lines.size(), visibleLines(), bodyScroll);
                renderStatus(context, lesson);
            }
        }
        super.render(context, mouseX, mouseY, delta);
        if (!shown && !lessons.isEmpty()) { shown = true; onShown.run(); }
    }

    private void renderIndex(DrawContext context) {
        FormTutorialTextures.inset(context, left + 12, contentTop - 2, panelWidth - 24, contentHeight + 2);
        scrollbar(context, left + panelWidth - 18, contentTop, contentHeight, lessons.size(), entries.size(), indexScroll);
    }

    private void renderStatus(DrawContext context, FormTutorialLessons.Lesson lesson) {
        String hintKey = maxScroll() > 0 ? "reading_scroll" : "reading_hint";
        Text status = text(hintKey);
        if (!lesson.node().isEmpty() && client.player != null) {
            var evolution = RegEvolutionComponent.EVOLUTION.get(client.player);
            if (evolution != null && !evolution.isUnlocked(lesson.node())) status = text("locked");
        } else if (!lesson.power().isEmpty() && client.player != null) {
            Identifier id = Identifier.tryParse(lesson.power());
            if (id != null && PowerTypeRegistry.contains(id)) {
                PowerType<?> type = PowerTypeRegistry.get(id);
                var power = PowerHolderComponent.KEY.get(client.player).getPower(type);
                if (power instanceof SkillCooldownHolder holder && holder.cooldownSpec().cooldown() > 0) {
                    String seconds = String.format(Locale.ROOT, "%.1f", holder.cooldownSpec().cooldown() / 20.0);
                    status = text("cooldown", seconds, text(hintKey));
                }
            }
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(status.getString(), panelWidth - 26),
                left + 13, top + panelHeight - 41, 0xff404040, false);
    }

    private static void scrollbar(DrawContext context, int x, int y, int height, int total, int visible, int offset) {
        if (total <= visible) return;
        int thumb = Math.max(6, height * visible / total);
        int start = y + (height - thumb) * offset / (total - visible);
        FormTutorialTextures.scroll(context, x - 3, y, height, thumb, start - y);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll(-Math.round((float) amount * (indexOpen ? 1 : 3)));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> { if (indexOpen) toggleIndex(); else close(); }
            case GLFW.GLFW_KEY_DOWN -> scroll(1);
            case GLFW.GLFW_KEY_UP -> scroll(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll(indexOpen ? entries.size() : visibleLines());
            case GLFW.GLFW_KEY_PAGE_UP -> scroll(indexOpen ? -entries.size() : -visibleLines());
            case GLFW.GLFW_KEY_HOME -> { if (indexOpen) { indexScroll = 0; updateIndex(); } else bodyScroll = 0; }
            case GLFW.GLFW_KEY_END -> { if (indexOpen) { indexScroll = lessons.size(); updateIndex(); } else bodyScroll = maxScroll(); }
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
