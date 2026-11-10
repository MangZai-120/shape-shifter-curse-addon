package net.jackcooper.shapeShifterCurseAddon.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/** Faded particles blend with the scene without becoming opaque occluders in its depth buffer. */
public final class FadedParticleBuffer {
    private final BufferBuilder vertices = new BufferBuilder(1024);

    public BufferBuilder vertices() {
        if (!vertices.isBuilding()) {
            vertices.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
        }
        return vertices;
    }

    public BufferBuilder.BuiltBuffer end() {
        return vertices.isBuilding() ? vertices.end() : null;
    }

    public void draw() {
        var built = end();
        if (built == null) return;
        if (built.isEmpty()) {
            built.release();
            return;
        }
        // Geometry construction only fills CPU buffers. Change the mask at GPU submission, not
        // around Particle.buildGeometry, and retain depth testing against blocks/entities.
        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.depthMask(false);
        try {
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.depthMask(writeDepth);
        }
    }

    public void discard() {
        var built = end();
        if (built != null) built.release();
    }
}
