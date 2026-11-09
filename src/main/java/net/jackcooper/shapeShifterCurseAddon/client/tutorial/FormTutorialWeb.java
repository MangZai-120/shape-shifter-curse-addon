package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonObject;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.LoggerFactory;

/** The tutorial opens in the system browser; its page mirrors the native book UI. */
public final class FormTutorialWeb {
    private static final String ROOT = "https://shape-shifter-curse-addon.readthedocs.io/zh-cn/latest/tutorials/";
    private static final Map<Identifier, String> PAGES = Map.ofEntries(
            Map.entry(new Identifier("my_addon", "familiar_fox_spirit_realm_lord"), "spirit-realm-lord"),
            Map.entry(new Identifier("my_addon", "axolotl_abyssal_overlord"), "abyssal-overlord"),
            Map.entry(new Identifier("my_addon", "axolotl_fluorescent"), "fluorescent"),
            Map.entry(new Identifier("my_addon", "axolotl_aling"), "aling"),
            Map.entry(new Identifier("my_addon", "familiar_fox_red"), "red"),
            Map.entry(new Identifier("my_addon", "familiar_fox_mancianima"), "mancianima"),
            Map.entry(new Identifier("my_addon", "snow_fox_frost_spirit"), "frost-spirit"),
            Map.entry(new Identifier("my_addon", "snow_fox_frostspine"), "frostspine"),
            Map.entry(new Identifier("my_addon", "allay_crystal_sound"), "crystal-sound"),
            Map.entry(new Identifier("my_addon", "allay_spirit_of_the_fallen"), "fallen-spirit"),
            Map.entry(new Identifier("my_addon", "wild_cat_moonlight_phantom"), "moonlight-phantom"),
            Map.entry(new Identifier("my_addon", "wild_cat_nightmare"), "nightmare"),
            Map.entry(new Identifier("my_addon", "ocelot_wind_spirit"), "wind-spirit"),
            Map.entry(new Identifier("my_addon", "ocelot_nova"), "nova"),
            Map.entry(new Identifier("my_addon", "anubis_wolf_netherjudge"), "netherjudge"),
            Map.entry(new Identifier("my_addon", "anubis_wolf_golden_sandstorm"), "golden-sandstorm"),
            Map.entry(new Identifier("my_addon", "bat_desmodus"), "desmodus"),
            Map.entry(new Identifier("my_addon", "bat_parasitic_fruit"), "parasitic-fruit"));
    private static final String[] KEYS = {"primary", "secondary", "sneak", "attack", "use", "jump", "sprint"};

    private FormTutorialWeb() {}

    public static boolean supports(Identifier form) { return form != null && PAGES.containsKey(form); }

    static URI url(MinecraftClient client, Identifier form) {
        // Local isolated acceptance runs can serve the exact same Wiki build without publishing a test URL.
        if (!supports(form)) throw new IllegalArgumentException("No web tutorial for " + form);
        String page = System.getProperty("ssca.audit.tutorialWebPage");
        if (page == null) {
            page = ROOT + PAGES.get(form) + "/";
            var localRoot = client.runDirectory.toPath().resolve("ssca-tutorials");
            if (Files.isRegularFile(localRoot.resolve(PAGES.get(form)).resolve("index.html"))) {
                try {
                    page = FormTutorialLocalServer.page(localRoot, PAGES.get(form)).toString();
                } catch (java.io.IOException | LinkageError | RuntimeException failure) {
                    LoggerFactory.getLogger("SSCA-Tutorial").warn("Cannot serve the synchronized tutorial; using the online page", failure);
                }
            }
        }
        StringBuilder url = new StringBuilder(page);
        url.append(page.contains("?") ? '&' : '?').append("lang=")
                .append(client.options.language.startsWith("zh_") ? "zh_cn" : "en_us");
        for (String key : KEYS) url.append('&').append(key).append('=')
                .append(URLEncoder.encode(FormTutorialKeys.key(client, form, key).getString(), StandardCharsets.UTF_8));
        JsonObject states = new JsonObject();
        if (client.player != null) for (var lesson : FormTutorialLessons.forForm(form)) {
            JsonObject state = new JsonObject();
            if (!lesson.node().isEmpty()) {
                var evolution = RegEvolutionComponent.EVOLUTION.get(client.player);
                if (evolution != null && !evolution.isUnlocked(lesson.node())) state.addProperty("locked", true);
            } else if (!lesson.power().isEmpty()) {
                Identifier id = Identifier.tryParse(lesson.power());
                if (id != null && PowerTypeRegistry.contains(id)) {
                    PowerType<?> type = PowerTypeRegistry.get(id);
                    var power = PowerHolderComponent.KEY.get(client.player).getPower(type);
                    // Aling's laser accumulates cooldown per shot; its page explains that variable cost.
                    if (!lesson.id().equals("aling_laser") && power instanceof SkillCooldownHolder holder
                            && holder.cooldownSpec().cooldown() > 0)
                        state.addProperty("cooldown", holder.cooldownSpec().cooldown() / 20.0);
                }
            }
            if (!state.entrySet().isEmpty()) states.add(lesson.id(), state);
        }
        url.append("&autoplay=1&v=20261108-native-web-r2&state=")
                .append(URLEncoder.encode(states.toString(), StandardCharsets.UTF_8));
        return URI.create(url.toString());
    }

    public static void open(MinecraftClient client, Identifier form, Runnable onShown) {
        openExternal(client, url(client, form), onShown);
    }

    static void openExternal(MinecraftClient client, URI url, Runnable onDispatched) {
        CompletableFuture.runAsync(() -> Util.getOperatingSystem().open(url), Util.getIoWorkerExecutor())
                .whenComplete((ignored, failure) -> client.execute(() -> {
                    if (failure != null) LoggerFactory.getLogger("SSCA-Tutorial").warn("Cannot hand the tutorial to the system browser", failure);
                    if (client.player != null) client.player.sendMessage(FormTutorialScreen.text(
                                    failure == null ? "web.external_opened" : "web.external_failed")
                            .styled(style -> style.withUnderline(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url.toString()))), false);
                    // The OS browser owns subsequent loading. A handoff, rather than a page-view claim, consumes this offer.
                    if (failure == null) onDispatched.run();
                }));
    }
}
