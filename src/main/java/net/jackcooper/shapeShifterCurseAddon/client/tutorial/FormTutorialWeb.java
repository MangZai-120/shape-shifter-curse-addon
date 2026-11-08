package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonObject;
import io.github.apace100.apoli.component.PowerHolderComponent;
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
    private static final Identifier SPIRIT_REALM_LORD = new Identifier("my_addon", "familiar_fox_spirit_realm_lord");
    private static final String PAGE = "https://shape-shifter-curse-addon.readthedocs.io/zh-cn/latest/tutorials/spirit-realm-lord/";
    private static final String[] KEYS = {"primary", "secondary", "sneak", "attack", "use", "jump", "sprint"};

    private FormTutorialWeb() {}

    public static boolean supports(Identifier form) { return SPIRIT_REALM_LORD.equals(form); }

    static URI url(MinecraftClient client, Identifier form) {
        // Local isolated acceptance runs can serve the exact same Wiki build without publishing a test URL.
        String page = System.getProperty("ssca.audit.tutorialWebPage", PAGE);
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
                    var power = PowerHolderComponent.KEY.get(client.player).getPower(PowerTypeRegistry.get(id));
                    if (power instanceof SkillCooldownHolder holder && holder.cooldownSpec().cooldown() > 0)
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
