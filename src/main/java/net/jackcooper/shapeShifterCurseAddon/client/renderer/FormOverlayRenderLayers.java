package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import net.onixary.shapeShifterCurseFabric.util.ClientUtils;

import java.util.function.Function;

/** Keeps the emissive pass on the same surface as SSC's vanilla-player skin overlay. */
public abstract class FormOverlayRenderLayers extends RenderLayer {
    private static final boolean IRIS_INSTALLED = FabricLoader.getInstance().isModLoaded("iris");
    private static final boolean IMMEDIATELY_FAST_INSTALLED = FabricLoader.getInstance().isModLoaded("immediatelyfast");

    private static final Function<Identifier, RenderLayer> EMISSIVE_Z_OFFSET = Util.memoize(texture ->
            of("ssc_addon_overlay_emissive", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS, 256, true, true,
                    MultiPhaseParameters.builder()
                            .program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                            .texture(new Texture(texture, false, false))
                            .transparency(TRANSLUCENT_TRANSPARENCY)
                            .cull(DISABLE_CULLING)
                            .writeMaskState(COLOR_MASK)
                            .overlay(ENABLE_OVERLAY_COLOR)
                            .layering(VIEW_OFFSET_Z_LAYERING)
                            .build(true)));

    private FormOverlayRenderLayers(String name, VertexFormat format, VertexFormat.DrawMode mode,
                                    int size, boolean crumbling, boolean translucent, Runnable start, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, start, end);
    }

    public static RenderLayer emissive(Identifier texture) {
        // SSC moves its cutout overlay towards the camera for these renderers. Without the
        // identical view offset, its depth buffer rejects the inner skin's emissive pixels.
        if (usesViewOffset()) {
            return EMISSIVE_Z_OFFSET.apply(texture);
        }
        return RenderLayer.getEntityTranslucentEmissive(texture);
    }

    public static boolean usesViewOffset() {
        return (FormRenderUtils.isRenderingInWorld && IRIS_INSTALLED) || IMMEDIATELY_FAST_INSTALLED;
    }

    public static boolean scaleInventoryOverlay() {
        // Match the additional geometry transform in FormRenderFeature.rM_PartB exactly.
        return IMMEDIATELY_FAST_INSTALLED && ClientUtils.isOpenInventoryScreen;
    }
}
