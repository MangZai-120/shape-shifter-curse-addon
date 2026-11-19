package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisRockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class OrcafisRockRenderer extends EntityRenderer<OrcafisRockEntity> {
    public OrcafisRockRenderer(EntityRendererFactory.Context context) { super(context); }

    @Override public Identifier getTexture(OrcafisRockEntity rock) { return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE; }

    @Override public void render(OrcafisRockEntity rock,float yaw,float partial,MatrixStack m,VertexConsumerProvider buf,int light) {
        draw(m,buf,light,rock.age+partial);
        super.render(rock,yaw,partial,m,buf,light);
    }

    static void draw(MatrixStack m,VertexConsumerProvider buf,int light,float flight) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(18+flight*5));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-12+flight*7));
        block(Blocks.TUFF,m,buf,light,1.3f,1.10f,1.20f);
        m.translate(.43,-.08,.03);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(23));
        block(Blocks.ANDESITE,m,buf,light,.72f,.78f,.9f);
        m.pop();
    }

    private static void block(Block block,MatrixStack m,VertexConsumerProvider buf,int light,float x,float y,float z) {
        m.push();
        m.scale(x,y,z);
        m.translate(-.5,-.5,-.5);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(block.getDefaultState(),m,buf,light,OverlayTexture.DEFAULT_UV);
        m.pop();
    }
}
