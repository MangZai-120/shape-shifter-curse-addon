package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 荧光幼灵「潮汐束缚（拴人）」守卫者激光渲染器（纯客户端）。
 *
 * <p>服务端每 10 tick 通过 {@code PACKET_TIDAL_TETHER} 把「水球 entityId + 被拴目标 entityId 列表」
 * 同步过来，存进 {@link #ACTIVE}；这里在 {@code WorldRenderEvents.AFTER_ENTITIES} 逐帧从水球向每个
 * 被拴目标画一条守卫者风格光束。条目在超过 expireTime 未刷新（或水球消失）后自动清除。
 */
@Environment(EnvType.CLIENT)
public final class TidalTetherBeamRenderer {

    /** 原版守卫者激光贴图。 */
    private static final Identifier BEAM_TEXTURE = new Identifier("textures/entity/guardian_beam.png");

    /** key = 水球 entityId，value = 被拴目标列表 + 过期时刻。 */
    private static final Map<Integer, Entry> ACTIVE = new ConcurrentHashMap<>();

    private TidalTetherBeamRenderer() {
    }

    private static final class Entry {
        final int[] targetIds;
        final long expireTime;

        Entry(int[] targetIds, long expireTime) {
            this.targetIds = targetIds;
            this.expireTime = expireTime;
        }
    }

    /** 收到服务端同步：更新某水球的被拴目标列表与过期时刻。 */
    public static void update(int orbId, int[] targetIds, long expireTime) {
        ACTIVE.put(orbId, new Entry(targetIds, expireTime));
    }

    /** 断线 / 切世界时清空，避免残留。 */
    public static void clear() {
        ACTIVE.clear();
    }

    /** {@code WorldRenderEvents.AFTER_ENTITIES} 回调：逐帧画所有活跃的拴人光束。 */
    public static void render(WorldRenderContext ctx) {
        if (ACTIVE.isEmpty()) return;
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null) return;
        World world = MinecraftClient.getInstance().world;
        if (world == null) return;

        long now = world.getTime();
        float tickDelta = ctx.tickDelta();
        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(BEAM_TEXTURE));
        float age = (float) now + tickDelta;

        Iterator<Map.Entry<Integer, Entry>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> me = it.next();
            Entry en = me.getValue();
            if (now > en.expireTime) {
                it.remove();   // 过期未刷新 → 清除
                continue;
            }
            Entity orb = world.getEntityById(me.getKey());
            if (orb == null) continue;   // 水球已消失 → 本帧不画（下帧过期清除）
            Vec3d op = orb.getLerpedPos(tickDelta).add(0.0, orb.getHeight() * 0.5, 0.0);
            for (int tid : en.targetIds) {
                Entity tgt = world.getEntityById(tid);
                if (tgt == null || !tgt.isAlive()) continue;
                Vec3d tp = tgt.getLerpedPos(tickDelta).add(0.0, tgt.getHeight() * 0.5, 0.0);
                ms.push();
                ms.translate(op.x - camPos.x, op.y - camPos.y, op.z - camPos.z);
                drawBeam(ms, vc, tp.x - op.x, tp.y - op.y, tp.z - op.z, age);
                ms.pop();
            }
        }
    }

    /** 从本地原点（水球）沿 (relX,relY,relZ) 到目标画一条守卫者风格光束。 */
    private static void drawBeam(MatrixStack ms, VertexConsumer vc, double relX, double relY, double relZ, float age) {
        double full = Math.sqrt(relX * relX + relY * relY + relZ * relZ);
        if (full < 1.0e-3) return;
        double inv = 1.0 / full;
        double dnx = relX * inv, dny = relY * inv, dnz = relZ * inv;
        // 原版守卫者朝向：绕 Y 转 (PI/2 - yaw)，再绕 X 转 pitch，使本地 +Y 指向目标
        float pitch = (float) Math.acos(MathHelper.clamp(dny, -1.0, 1.0));
        float yaw = (float) Math.atan2(dnz, dnx);

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotation(1.5707964f - yaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotation(pitch));

        MatrixStack.Entry e = ms.peek();
        Matrix4f pose = e.getPositionMatrix();
        Matrix3f nrm = e.getNormalMatrix();

        float len = (float) full;
        float halfW = 0.14f;
        float scroll = -(age * 0.06f);       // 贴图沿光束滚动，形成流动感
        float v0 = scroll;
        float v1 = len * 0.7f + scroll;
        // 守卫者风格：紫 ↔ 青 脉动
        float pulse = 0.5f + 0.5f * MathHelper.sin(age * 0.35f);
        float cr = 0.45f + 0.40f * pulse;
        float cg = 0.75f;
        float cb = 1.00f;
        float ca = 0.85f;

        // 两片交叉四边形（getEntityCutoutNoCull 天然双面，单片即两面可见）
        for (int q = 0; q < 2; q++) {
            float ox = (q == 0) ? halfW : 0f;
            float oz = (q == 0) ? 0f : halfW;
            vtx(vc, pose, nrm, -ox, 0f, -oz, 0f, v1, cr, cg, cb, ca);
            vtx(vc, pose, nrm, -ox, len, -oz, 0f, v0, cr, cg, cb, ca);
            vtx(vc, pose, nrm, ox, len, oz, 1f, v0, cr, cg, cb, ca);
            vtx(vc, pose, nrm, ox, 0f, oz, 1f, v1, cr, cg, cb, ca);
        }
        ms.pop();
    }

    private static void vtx(VertexConsumer vc, Matrix4f pose, Matrix3f nrm,
                            float x, float y, float z, float u, float v,
                            float r, float g, float b, float a) {
        vc.vertex(pose, x, y, z).color(r, g, b, a).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(nrm, 0f, 1f, 0f).next();
    }
}
