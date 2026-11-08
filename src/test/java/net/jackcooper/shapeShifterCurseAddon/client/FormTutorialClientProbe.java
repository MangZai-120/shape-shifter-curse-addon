package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.tutorial.FormTutorialClient;
import net.jackcooper.shapeShifterCurseAddon.client.tutorial.FormTutorialScreen;
import net.jackcooper.shapeShifterCurseAddon.client.tutorial.FormTutorialLessons;
import net.jackcooper.shapeShifterCurseAddon.client.tutorial.FormTutorialWeb;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialManager;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialState;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialCatalog;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P1;
import net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.ssca.neomagic.NeoState;
import org.lwjgl.glfw.GLFW;

/** Native tutorial book, packets and controls in a disposable integrated-server world. */
public final class FormTutorialClientProbe {
    private static final Identifier ORIGINAL_FORM = new Identifier("shape-shifter-curse", "familiar_fox_1");
    private static final Identifier CLOSED_BOOK_FORM = FormIdentifiers.WILD_CAT_SP;
    private static final List<String> SHOTS = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static volatile Throwable failure;
    private static volatile boolean finished, firstSeen, secondSeen, closedBookSeen, closedBookOffered, serverReady, languageReady, wrongOfferSent;
    private static boolean starting;
    private static boolean lateRequestRejected;
    private static int stage, ticks, serverTicks, checks;
    private static BookOfShapeShifterScreenV2_P1 book;
    private static Screen otherScreen;
    private static Object heldRequest;
    private static UUID currentRequestId;
    private static final boolean NATIVE = Boolean.getBoolean("ssca.audit.nativeEnvironment");
    private static final List<Identifier> ALL_FORMS = FormTutorialCatalog.forms().stream()
            .filter(form -> !FormTutorialWeb.supports(form))
            .sorted(java.util.Comparator.comparing(Identifier::toString)).toList();
    private static int sweepForm, sweepLanguage, sweptPages;
    private static boolean sweepRequested;

    private FormTutorialClientProbe() { }

    private static void check(boolean result, String reason) {
        checks++;
        if (!result) throw new AssertionError(reason);
    }

    private static void next() {
        stage++;
        ticks = 0;
        System.out.println("FORM_TUTORIAL_CLIENT_STAGE " + stage);
    }

    private static int page(Screen screen) throws ReflectiveOperationException {
        return intField(screen, "page");
    }

    private static int intField(Screen screen, String name) throws ReflectiveOperationException {
        Field field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }

    private static Field clientField(String name) throws ReflectiveOperationException {
        Field field = FormTutorialClient.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static List<ButtonWidget> buttons(Screen screen) {
        return screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();
    }

    private static ButtonWidget button(Screen screen, String key) {
        String label = Text.translatable("tutorial.ssc_addon.form." + key).getString();
        return buttons(screen).stream().filter(b -> b.visible && b.getMessage().getString().equals(label)).findFirst().orElseThrow(
                () -> new AssertionError("Missing " + key + " button on " + screen.getClass().getSimpleName()));
    }

    private static void click(Screen screen, String key) {
        ButtonWidget b = button(screen, key);
        check(b.active, "Control is active: " + key);
        screen.mouseClicked(b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, 0);
        screen.mouseReleased(b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, 0);
    }

    private static void fits(Screen screen) {
        check(buttons(screen).stream().filter(b -> b.visible).allMatch(b -> b.getX() >= 0 && b.getY() >= 0
                        && b.getX() + b.getWidth() <= screen.width && b.getY() + b.getHeight() <= screen.height),
                "Every tutorial control fits the actual GUI size " + screen.width + "x" + screen.height);
    }

    private static void lastLesson(Screen screen) throws ReflectiveOperationException {
        var tutorial = (FormTutorialScreen) screen;
        while (page(screen) < tutorial.lessonCount() - 1) screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
    }

    private static void shot(MinecraftClient client, String name) throws Exception {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(Path.of(name)); }
        SHOTS.add(name);
    }

    private static void openBook(MinecraftClient client) {
        book = new BookOfShapeShifterScreenV2_P1();
        book.currentPlayer = client.player;
        client.setScreen(book);
    }

    private static boolean form(MinecraftClient client, Identifier id) {
        var current = FormUtils.getPlayerForm(client.player);
        return current != null && id.equals(current.getFormID());
    }

    private static void switchForm(MinecraftClient client, Identifier id) {
        client.setScreen(null);
        serverReady = false;
        client.getServer().execute(() -> {
            try {
                var player = client.getServer().getPlayerManager().getPlayerList().get(0);
                // Sheep is a temporary effect-backed form: the real sweep restores an orphan form immediately.
                player.removeStatusEffect(SscAddon.SHEEP_FORM);
                if (FormIdentifiers.SHEEP_FORM.equals(id)) {
                    player.addStatusEffect(new StatusEffectInstance(SscAddon.SHEEP_FORM, 200));
                } else {
                    FormUtils._setForm(player, RegPlayerForms.getPlayerForm(id));
                }
                serverReady = true;
            } catch (Throwable error) { fail(error); }
        });
    }

    public static void install() {
        // This run exercises form onboarding only, independently of new-world spell onboarding.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var state = NeoState.get(server);
            state.tutorialPolicyInitialized = true;
            state.tutorialAutoEnabled = false;
            state.markDirty();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
            try {
                var player = server.getPlayerManager().getPlayerList().get(0);
                if (++serverTicks == 40) {
                    server.getOverworld().setTimeOfDay(6000);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    player.changeGameMode(GameMode.SURVIVAL);
                    FormUtils._setForm(player, RegPlayerForms.getPlayerForm(FormIdentifiers.FAMILIAR_FOX_RED));
                    serverReady = true;
                }
                var state = FormTutorialState.get(server);
                firstSeen = state.hasSeen(player.getUuid(), FormIdentifiers.FAMILIAR_FOX_RED);
                secondSeen = state.hasSeen(player.getUuid(), FormIdentifiers.ANUBIS_WOLF_SP);
                closedBookSeen = state.hasSeen(player.getUuid(), CLOSED_BOOK_FORM);
                if (FormUtils.getPlayerForm(player).getFormID().equals(CLOSED_BOOK_FORM)) {
                    Field pending = FormTutorialManager.class.getDeclaredField("PENDING");
                    pending.setAccessible(true);
                    closedBookOffered |= ((Map<?, ?>) pending.get(null)).containsKey(player.getUuid());
                }
            } catch (Throwable error) { fail(error); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            try {
                if (System.nanoTime() - START > 300_000_000_000L)
                    throw new AssertionError("Form tutorial client timed out at stage " + stage + ", screen=" + client.currentScreen);
                if (client.getOverlay() != null) return;
                if (!starting && client.currentScreen instanceof TitleScreen) {
                    starting = true;
                    client.options.pauseOnLostFocus = false;
                    if (!NATIVE) {
                        client.options.getMaxFps().setValue(60);
                        client.options.getViewDistance().setValue(4);
                    }
                    var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                            DataConfiguration.SAFE_MODE.enabledFeatures());
                    client.createIntegratedServerLoader().createAndStart("Form-Tutorial-" + System.currentTimeMillis(),
                            new LevelInfo("Form tutorial acceptance", GameMode.SURVIVAL, false, Difficulty.PEACEFUL,
                                    true, new GameRules(), data), new GeneratorOptions(20261106L, false, false),
                            registries -> registries.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (client.player == null || client.world == null) return;
                ticks++;
                if (ticks % 200 == 0) {
                    System.out.println("FORM_TUTORIAL_CLIENT_WAIT stage=" + stage + " ticks=" + ticks
                            + " form=" + FormUtils.getPlayerForm(client.player).getFormID() + " screen=" + client.currentScreen
                            + " serverReady=" + serverReady + " closedBookOffered=" + closedBookOffered);
                }
                switch (stage) {
                    case 0 -> {
                        if (!serverReady || !form(client, FormIdentifiers.FAMILIAR_FOX_RED)) return;
                        check(!firstSeen, "The form is unseen before the first book opening");
                        var custom = SSCAddonConfig.client().getOrCreateFormKeybind(FormIdentifiers.FAMILIAR_FOX_RED.getPath());
                        custom.enabled = true;
                        custom.primaryKey = "key.keyboard.f";
                        custom.secondaryKey = "key.keyboard.j";
                        openBook(client);
                        next();
                    }
                    case 1 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen)) return;
                        check(page(client.currentScreen) == 0, "First book opening starts the skill tutorial");
                        var tutorial = (FormTutorialScreen) client.currentScreen;
                        check(tutorial.lessonCount() >= 8, "The form has individual skill and passive lessons");
                        check(tutorial.lessonId().equals("red_ring"), "The first page teaches one specific primary skill");
                        check(tutorial.formId().equals(FormIdentifiers.FAMILIAR_FOX_RED), "The offer keeps the requested form identity");
                        check(tutorial.content().getString().contains(InputUtil.fromTranslationKey("key.keyboard.f").getLocalizedText().getString()),
                                "The skill page displays the actual custom primary key F");
                        check(!tutorial.content().getString().contains(Text.translatable("tutorial.ssc_addon.form.lesson.red_fireball.title").getString()),
                                "A primary page does not bundle the secondary skill into its text");
                        fits(client.currentScreen);
                        next();
                    }
                    case 2 -> {
                        if (!firstSeen || ticks < 10) return;
                        check(true, "Actual shown acknowledgement persists on the integrated server");
                        shot(client, "form-01-skills-zh.png");
                        click(client.currentScreen, "next");
                        next();
                    }
                    case 3 -> {
                        if (ticks < 10) return;
                        check(page(client.currentScreen) == 1, "Next opens the separate secondary skill");
                        check(((FormTutorialScreen) client.currentScreen).lessonId().equals("red_fireball"), "The second page is the red fireball");
                        check(((FormTutorialScreen) client.currentScreen).content().getString().contains(
                                InputUtil.fromTranslationKey("key.keyboard.j").getLocalizedText().getString()), "Secondary page resolves custom J");
                        shot(client, "form-02-effects-zh.png");
                        click(client.currentScreen, "contents");
                        for (int i = 0; i < 12; i++) client.currentScreen.mouseScrolled(client.currentScreen.width / 2.0,
                                client.currentScreen.height / 2.0, -1);
                        next();
                    }
                    case 4 -> {
                        if (ticks < 10) return;
                        check(intField(client.currentScreen, "indexScroll") > 0, "The mouse wheel scrolls the multi-lesson contents list");
                        shot(client, "form-03-effects-scrolled-zh.png");
                        click(client.currentScreen, "read_page");
                        lastLesson(client.currentScreen);
                        click(client.currentScreen, "finish");
                        check(client.currentScreen == book, "Finishing restores the original book instance");
                        next();
                    }
                    case 5 -> {
                        if (ticks < 35) return;
                        check(client.currentScreen == book, "Returning to the book does not auto-open the tutorial again");
                        SSCAddonConfig.client().getOrCreateFormKeybind(FormIdentifiers.FAMILIAR_FOX_RED.getPath()).enabled = false;
                        ShapeShifterCurseFabricClient.useActiveSkill1PowerKeybind.setBoundKey(InputUtil.fromTranslationKey("key.keyboard.h"));
                        ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind.setBoundKey(InputUtil.UNKNOWN_KEY);
                        ShapeShifterCurseFabricClient.activeSkillKeybind.setBoundKey(InputUtil.fromTranslationKey("key.keyboard.b"));
                        KeyBinding.updateKeysByCode();
                        client.setScreen(null);
                        openBook(client);
                        next();
                    }
                    case 6 -> {
                        if (ticks < 35) return;
                        check(client.currentScreen == book, "A fresh book instance does not repeat an acknowledged form tutorial");
                        shot(client, "form-04-book-replay-entry-zh.png");
                        click(book, "replay");
                        next();
                    }
                    case 7 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen) || ticks < 8) return;
                        check(page(client.currentScreen) == 0, "The book replay entry restarts from skills");
                        String body = ((FormTutorialScreen) client.currentScreen).content().getString();
                        Text toolbar = FormTutorialScreen.text("toolbar_key", InputUtil.fromTranslationKey("key.keyboard.b").getLocalizedText());
                        Text direct = FormTutorialScreen.text("bound_and_toolbar", InputUtil.fromTranslationKey("key.keyboard.h").getLocalizedText(),
                                InputUtil.fromTranslationKey("key.keyboard.b").getLocalizedText());
                        check(body.contains(direct.getString()),
                                "SSC native direct-key and toolbar-key guidance is resolved from the real H/B bindings");
                        click(client.currentScreen, "next");
                        body = ((FormTutorialScreen) client.currentScreen).content().getString();
                        check(body.contains(toolbar.getString()),
                                "An unbound SSC direct secondary key correctly shows its real toolbar fallback B");
                        click(client.currentScreen, "skip");
                        check(client.currentScreen == book, "Skipping restores the original book");
                        next();
                    }
                    case 8 -> {
                        if (ticks < 35) return;
                        check(client.currentScreen == book, "Skipped replay stays closed");
                        click(book, "replay");
                        next();
                    }
                    case 9 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen) || ticks < 8) return;
                        check(client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape is handled");
                        check(client.currentScreen == book, "Escape restores the book");
                        next();
                    }
                    case 10 -> {
                        if (ticks < 35) return;
                        client.options.getGuiScale().setValue(NATIVE ? 6 : 4);
                        client.onResolutionChanged();
                        click(book, "replay");
                        next();
                    }
                    case 11 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen) || ticks < 8) return;
                        check(client.currentScreen.width < 550, "The large GUI-scale acceptance exercises a narrow screen");
                        check(intField(client.currentScreen,"bodyLeft")>intField(client.currentScreen,"diagramLeft")+intField(client.currentScreen,"diagramWidth")
                                && Math.abs(intField(client.currentScreen,"bodyWidth")-intField(client.currentScreen,"diagramWidth"))<=1,
                                "Narrow screens retain equal-width video and text columns");
                        fits(client.currentScreen);
                        shot(client, "form-05-small-gui-skills-zh.png");
                        click(client.currentScreen, "next");
                        next();
                    }
                    case 12 -> {
                        if (ticks < 8) return;
                        fits(client.currentScreen);
                        check(client.currentScreen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0), "The long effects page accepts keyboard scrolling");
                        client.getLanguageManager().setLanguage("en_us");
                        client.options.language = "en_us";
                        client.reloadResources().whenComplete((unused, error) -> {
                            if (error != null) fail(error);
                            else client.execute(() -> {
                                if (client.currentScreen != null) client.currentScreen.resize(client,
                                        client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                                languageReady = true;
                            });
                        });
                        next();
                    }
                    case 13 -> {
                        if (!languageReady || ticks < 10) return;
                        check(!Text.translatable("tutorial.ssc_addon.form.how").getString().contains("tutorial.ssc_addon"),
                                "English section title resolves after a real resource reload");
                        fits(client.currentScreen);
                        shot(client, "form-06-small-gui-effects-en.png");
                        lastLesson(client.currentScreen);
                        click(client.currentScreen, "finish");
                        check(client.currentScreen == book, "Finishing after language reload restores the book");
                        client.options.getGuiScale().setValue(NATIVE ? 5 : 2);
                        client.onResolutionChanged();
                        switchForm(client, FormIdentifiers.ANUBIS_WOLF_SP);
                        next();
                    }
                    case 14 -> {
                        if (!serverReady || !form(client, FormIdentifiers.ANUBIS_WOLF_SP)) return;
                        check(!secondSeen, "A second form has an independent unseen record");
                        openBook(client);
                        next();
                    }
                    case 15 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen) || !secondSeen || ticks < 8) return;
                        check(page(client.currentScreen) == 0, "The second form receives its own automatic skill tutorial");
                        check(firstSeen, "Acknowledging a second form retains the first viewing record");
                        shot(client, "form-07-second-form-skills-en.png");
                        click(client.currentScreen, "skip");
                        check(client.currentScreen == book, "The second form's skip restores its book");
                        switchForm(client, ORIGINAL_FORM);
                        next();
                    }
                    case 16 -> {
                        if (!serverReady || !form(client, ORIGINAL_FORM)) return;
                        openBook(client);
                        next();
                    }
                    case 17 -> {
                        if (ticks < 35) return;
                        check(client.currentScreen == book, "Original SSC forms do not receive addon tutorials");
                        check(buttons(book).stream().noneMatch(b -> b.getMessage().getString().equals(
                                Text.translatable("tutorial.ssc_addon.form.replay").getString())),
                                "Original SSC forms have no addon tutorial replay entry");
                        switchForm(client, FormIdentifiers.SHEEP_FORM);
                        next();
                    }
                    case 18 -> {
                        if (!serverReady || !form(client, FormIdentifiers.SHEEP_FORM)) return;
                        openBook(client);
                        next();
                    }
                    case 19 -> {
                        if (ticks < 35) return;
                        check(client.currentScreen == book, "The temporary sheep form does not receive acquisition tutorials");
                        check(buttons(book).stream().noneMatch(b -> b.getMessage().getString().equals(
                                Text.translatable("tutorial.ssc_addon.form.replay").getString())),
                                "The temporary sheep form has no tutorial replay entry");
                        switchForm(client, CLOSED_BOOK_FORM);
                        next();
                    }
                    case 20 -> {
                        if (!serverReady || !form(client, CLOSED_BOOK_FORM)) return;
                        check(!closedBookSeen, "The close-before-offer case starts with an unseen third form");
                        openBook(client);
                        next();
                    }
                    case 21 -> {
                        check(client.currentScreen == book, "The player can leave the book before the asynchronous offer is displayed");
                        otherScreen = new InventoryScreen(client.player);
                        client.setScreen(otherScreen);
                        next();
                    }
                    case 22 -> {
                        if (ticks < 35 || !closedBookOffered) return;
                        check(client.currentScreen == otherScreen, "A delayed real offer does not replace the player's inventory screen");
                        check(!closedBookSeen, "Closing the book before actual display does not consume the viewing record");
                        openBook(client);
                        next();
                    }
                    case 23 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen) || !closedBookSeen || ticks < 8) return;
                        check(((FormTutorialScreen) client.currentScreen).formId().equals(CLOSED_BOOK_FORM),
                                "Reopening the book after an unseen dropped offer still provides the correct tutorial");
                        check(firstSeen && secondSeen, "Viewing records remain independent across all three forms");
                        click(client.currentScreen, "skip");
                        check(client.currentScreen == book, "Recovered tutorial returns to its current book");
                        // Keep a real production request waiting while a deliberately stale network response arrives.
                        click(book, "replay");
                        heldRequest = clientField("queued").get(null);
                        check(heldRequest != null, "A real replay click schedules a production request");
                        var id = heldRequest.getClass().getDeclaredMethod("id");
                        id.setAccessible(true);
                        currentRequestId = (UUID) id.invoke(heldRequest);
                        clientField("queued").set(null, null);
                        clientField("requested").set(null, heldRequest);
                        clientField("pending").set(null, null);
                        client.getServer().execute(() -> {
                            try {
                                var player = client.getServer().getPlayerManager().getPlayerList().get(0);
                                var packet = PacketByteBufs.create();
                                packet.writeUuid(new UUID(currentRequestId.getMostSignificantBits() ^ 1L,
                                        currentRequestId.getLeastSignificantBits()));
                                packet.writeUuid(UUID.randomUUID());
                                packet.writeIdentifier(CLOSED_BOOK_FORM);
                                packet.writeBoolean(true);
                                ServerPlayNetworking.send(player, FormTutorialManager.OFFER, packet);
                                wrongOfferSent = true;
                            } catch (Throwable error) { fail(error); }
                        });
                        next();
                    }
                    case 24 -> {
                        if (!wrongOfferSent || ticks < 35) return;
                        check(client.currentScreen == book, "An older response for the same form does not open on a newer book request");
                        check(clientField("requested").get(null) == heldRequest,
                                "Rejecting a wrong request ID preserves the current request");
                        check(clientField("pending").get(null) == null, "An older response never becomes a pending tutorial offer");
                        lateRequestRejected = true;
                        var packet = PacketByteBufs.create();
                        packet.writeUuid(currentRequestId);
                        packet.writeIdentifier(CLOSED_BOOK_FORM);
                        packet.writeBoolean(true);
                        ClientPlayNetworking.send(FormTutorialManager.REQUEST, packet);
                        next();
                    }
                    case 25 -> {
                        if (!(client.currentScreen instanceof FormTutorialScreen) || ticks < 8) return;
                        check(((FormTutorialScreen) client.currentScreen).formId().equals(CLOSED_BOOK_FORM),
                                "The matching response still opens normally after a stale response was rejected");
                        check(clientField("requested").get(null) == null, "Showing the matching response consumes the current request");
                        shot(client, "form-08-late-request-rejected-en.png");
                        click(client.currentScreen, "skip");
                        check(client.currentScreen == book, "The correctly matched replay returns to its book");
                        next();
                    }
                    case 26 -> {
                        if (!sweepRequested) {
                            sweepRequested = true;
                            client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayerList().get(0).changeGameMode(GameMode.CREATIVE));
                            switchForm(client, ALL_FORMS.get(sweepForm));
                            return;
                        }
                        if (!serverReady || !form(client, ALL_FORMS.get(sweepForm))) return;
                        client.setScreen(new FormTutorialScreen(book, ALL_FORMS.get(sweepForm), () -> {}));
                        next();
                    }
                    case 27 -> {
                        if (ticks < 2) return;
                        var tutorial = (FormTutorialScreen) client.currentScreen;
                        check(Math.abs(intField(tutorial,"diagramWidth")-intField(tutorial,"bodyWidth"))<=1
                                        && intField(tutorial,"diagramTop")==intField(tutorial,"bodyTop"),
                                "Every lesson uses equal-width columns with aligned tops");
                        fits(tutorial);
                        check(tutorial.lessonCount() == FormTutorialLessons.forForm(tutorial.formId()).size(), "All configured lessons are reachable");
                        check(!tutorial.content().getString().contains("tutorial.ssc_addon"), "All lesson text resolves in " + client.options.language);
                        check(!tutorial.content().getString().matches("(?s).*\\{(primary|secondary|attack|use|sneak|jump|sprint)\\}.*"), "All operation keys are resolved");
                        check(tutorial.content().getString().contains(FormTutorialScreen.text("how").getString())
                                && tutorial.content().getString().contains(FormTutorialScreen.text("result").getString())
                                && tutorial.content().getString().contains(FormTutorialScreen.text("remember").getString()), "Each lesson has three concise teaching sections");
                        int bodyTop = intField(tutorial, "bodyTop"), bodyHeight = intField(tutorial, "bodyHeight");
                        check(bodyTop >= 0 && bodyHeight >= 10 && bodyTop + bodyHeight <= intField(tutorial, "top") + intField(tutorial, "panelHeight") - 40,
                                "Lesson body and navigation do not overlap");
                        sweptPages++;
                        if (page(tutorial) == 0 && sweepForm % 4 == 0)
                            shot(client, "illustrated-" + sweepLanguage + "-" + tutorial.formId().getPath() + ".png");
                        if (page(tutorial) < tutorial.lessonCount() - 1) {
                            tutorial.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
                            ticks = 0;
                            return;
                        }
                        tutorial.close();
                        sweepForm++; sweepRequested = false;
                        if (sweepForm < ALL_FORMS.size()) { stage = 26; ticks = 0; return; }
                        if (sweepLanguage == 0) {
                            sweepLanguage++; sweepForm = 0; languageReady = false;
                            client.getLanguageManager().setLanguage("zh_cn"); client.options.language = "zh_cn";
                            client.options.getGuiScale().setValue(NATIVE ? 4 : 2);
                            client.reloadResources().whenComplete((unused,error) -> {
                                if (error != null) fail(error);
                                else client.execute(() -> { client.onResolutionChanged(); languageReady = true; });
                            });
                            stage = 28; ticks = 0; return;
                        }
                        var report = new java.util.LinkedHashMap<String,Object>();
                        report.put("passed",true); report.put("checks",checks); report.put("real_client",true); report.put("real_packets",true);
                        report.put("screenshots",SHOTS); report.put("languages",List.of("zh_cn","en_us"));
                        report.put("dynamic_bindings",true); report.put("closed_book_offer_discarded",true); report.put("lateRequestRejected",lateRequestRejected);
                        report.put("forms_swept",ALL_FORMS.size()); report.put("illustrated_pages_swept",sweptPages);
                        report.put("native_environment",NATIVE);
                        Files.writeString(Path.of("form-tutorial-client-result.json"),new Gson().toJson(report));
                        finished = true; System.out.println("FORM_TUTORIAL_CLIENT_PASS checks=" + checks + " pages=" + sweptPages);
                        client.scheduleStop();
                    }
                    case 28 -> {
                        if (languageReady) { stage = 26; ticks = 0; }
                    }
                    default -> throw new AssertionError("Unknown acceptance stage " + stage);
                }
            } catch (Throwable error) { fail(error); }
        });
    }

    private static void fail(Throwable error) {
        if (failure != null) return;
        failure = error;
        finished = true;
        error.printStackTrace();
        try {
            Files.writeString(Path.of("form-tutorial-client-failure.json"), new Gson().toJson(Map.of(
                    "stage", stage, "ticks", ticks, "error", error.toString())));
        } catch (Exception ignored) { }
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Form tutorial client acceptance failed", failure);
        if (!finished) throw new AssertionError("Form tutorial client stopped before acceptance finished");
    }
}
