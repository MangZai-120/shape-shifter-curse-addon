package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jackcooper.shapeShifterCurseAddon.client.colorpicker.FormColorPreviewState;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Tint exposed vanilla skin below the two SSCA allay overlays, including first-person cuffs. */
public final class AllaySkinColor {
    private static final Map<Identifier, Source> SOURCES = new LinkedHashMap<>(8, 0.75f, true);
    private static final Map<Key, Identifier> TINTED = new LinkedHashMap<>(64, 0.75f, true);
    private record Source(NativeImage image, int average) { }
    private record Key(Identifier texture, int color, boolean reverse) { }

    private AllaySkinColor() { }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("ssc_addon", "allay_skin_color"); }
            @Override public void reload(ResourceManager manager) { clear(); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static Identifier texture(AbstractClientPlayerEntity player, Identifier original) {
        if (!RenderSystem.isOnRenderThread()) return original;
        var form = FormTextureUtils.getPlayerForm_Render(player);
        if (form == null || !form.getFormID().equals(FormIdentifiers.ALLAY_SP)
                && !form.getFormID().equals(FormIdentifiers.FALLEN_ALLAY_SP)) return original;
        FormTextureUtils.ColorSetting colors;
        if (player == MinecraftClient.getInstance().player && FormTextureUtils.useTempFormTexture
                && FormTextureUtils.tempFormTextureProcessor instanceof FormColorPreviewState preview) {
            if (!preview.ssca$colorEnabled() || preview.ssca$keepOriginalSkin()) return original;
            colors = preview.ssca$previewColors();
        } else {
            var skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
            if (!skin.isEnableFormColor() || skin.shouldKeepOriginalSkin()) return original;
            colors = skin.getFormColor();
        }
        if (colors == null || colors.accentColor2() >>> 24 == 0) return original;
        Key key = new Key(original, colors.accentColor2(), colors.accent2GreyReverse());
        Identifier cached = TINTED.get(key);
        if (cached != null) return cached;
        Source source = source(original);
        if (source == null) return original;
        NativeImage recolored = new NativeImage(source.image.getWidth(), source.image.getHeight(), false);
        try {
            for (int y = 0; y < recolored.getHeight(); y++) for (int x = 0; x < recolored.getWidth(); x++) {
                int pixel = source.image.getColor(x, y);
                int tinted = FormTextureUtils.ProcessMaskChannel(pixel, 255, key.color, source.average, key.reverse);
                recolored.setColor(x, y, (pixel & 0xff000000) | (tinted & 0xffffff));
            }
            Identifier result = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture(
                    "ssca_allay_skin", new NativeImageBackedTexture(recolored));
            recolored = null;
            TINTED.put(key, result);
            while (TINTED.size() > 64) {
                var iterator = TINTED.entrySet().iterator();
                var oldest = iterator.next();
                MinecraftClient.getInstance().getTextureManager().destroyTexture(oldest.getValue());
                iterator.remove();
            }
            return result;
        } finally {
            if (recolored != null) recolored.close();
        }
    }

    private static Source source(Identifier id) {
        Source cached = SOURCES.get(id);
        if (cached != null) return cached;
        MinecraftClient client = MinecraftClient.getInstance();
        NativeImage image = null;
        var resource = client.getResourceManager().getResource(id);
        if (resource.isPresent()) {
            try (var stream = resource.get().getInputStream()) { image = NativeImage.read(stream); }
            catch (IOException ignored) { return null; }
        } else {
            // Downloaded player skins are GPU textures, rather than resource-pack files.
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                RenderSystem.bindTexture(client.getTextureManager().getTexture(id).getGlId());
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (width < 64 || height < 32 || width > 4096 || height > 4096) return null;
                image = new NativeImage(width, height, false);
                image.loadFromTextureImage(0, false);
            } finally {
                RenderSystem.bindTexture(previous);
                RenderSystem.activeTexture(active);
            }
        }
        long sum = 0; int count = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getColor(x, y);
            if (pixel >>> 24 != 0) { sum += FormTextureUtils.getGreyScale(pixel); count++; }
        }
        Source result = new Source(image, count == 0 ? 255 : (int)(sum / count));
        SOURCES.put(id, result);
        while (SOURCES.size() > 8) {
            var iterator = SOURCES.entrySet().iterator();
            var oldest = iterator.next(); oldest.getValue().image.close(); iterator.remove();
        }
        return result;
    }

    private static void clear() {
        for (Source source : SOURCES.values()) source.image.close();
        SOURCES.clear();
        for (Identifier id : TINTED.values()) MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        TINTED.clear();
    }
}
