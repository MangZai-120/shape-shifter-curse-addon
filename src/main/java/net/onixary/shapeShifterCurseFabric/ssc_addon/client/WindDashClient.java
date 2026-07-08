package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import org.joml.Vector3f;

/**
 * 风灵「风之冲刺」客户端：主技能键检测 + 悬浮期绿色落点预览。
 *
 * <p>纯客户端：检测 sp_primary 按键边沿 → 发 {@code PACKET_WIND_DASH} 给服务端。
 * 当本地玩家处于悬浮阶段（{@code DashClientState.phase == HOVER}）时，持续在准星水平方向
 * 最远 16 格、半径 3 格的落点渲染绿色粉尘圆环（仿契灵紫色落点）。
 */
@Environment(EnvType.CLIENT)
public final class WindDashClient {

    private static final Vector3f GREEN = new Vector3f(0.3f, 0.95f, 0.4f);
    private static final double MAX_RANGE = 16.0;
    private static final double RADIUS = 3.0;
    private static final int PREVIEW_INTERVAL = 2; // 每 2 tick 刷一次预览粒子

    private static boolean lastPressed = false;
    private static int previewTick = 0;

    private WindDashClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WindDashClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            lastPressed = false;
            return;
        }

        boolean isWindSpirit = isWindSpirit(player);
        // 按键边沿检测（仅风灵、无 GUI）
        boolean pressed = isWindSpirit && client.currentScreen == null
                && SscAddonKeybindings.getPrimaryKey().isPressed();
        if (pressed && !lastPressed) {
            ClientPlayNetworking.send(SscAddonNetworking.PACKET_WIND_DASH, PacketByteBufs.empty());
        }
        lastPressed = pressed;

        // 悬浮阶段渲染绿色落点预览
        if (isWindSpirit && DashClientState.phase == DashClientState.PHASE_HOVER) {
            if ((previewTick++ % PREVIEW_INTERVAL) == 0) {
                Vec3d landing = computeHorizontalLanding(world, player);
                if (landing != null) {
                    renderGreenLanding(world, landing);
                }
            }
        }

        if (!isWindSpirit) {
            DashClientState.reset();
        }
    }

    /**
     * 计算冲刺落点：用完整 3D 准星射线（含俯仰角），命中方块取该处地面（水平距离≤MAX_RANGE）；
     * 打空（看天空）才退到水平最远 MAX_RANGE。瞄近处地面=近落点，瞄远处=远落点。
     */
    private static Vec3d computeHorizontalLanding(ClientWorld world, ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector().normalize();
        Vec3d flatLook = new Vec3d(look.x, 0, look.z);
        double flatLen = flatLook.length();
        double max3D = flatLen > 1.0e-4 ? MAX_RANGE / flatLen : MAX_RANGE;
        Vec3d end3D = eye.add(look.multiply(max3D));
        BlockHitResult bhr = world.raycast(new RaycastContext(eye, end3D,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (bhr.getType() != HitResult.Type.MISS) {
            Vec3d hit = bhr.getPos();
            double groundY = findGroundYClient(world, hit.x, hit.z, Math.min(hit.y, player.getY()));
            return new Vec3d(hit.x, groundY, hit.z);
        }
        Vec3d flatDir;
        if (flatLen > 1.0e-4) {
            flatDir = flatLook.normalize();
        } else {
            float yaw = player.getYaw();
            flatDir = new Vec3d(-MathHelper.sin(yaw * 0.017453292f), 0, MathHelper.cos(yaw * 0.017453292f));
        }
        double hx = player.getX() + flatDir.x * MAX_RANGE;
        double hz = player.getZ() + flatDir.z * MAX_RANGE;
        double gy = findGroundYClient(world, hx, hz, player.getY());
        return new Vec3d(hx, gy, hz);
    }

    private static double findGroundYClient(ClientWorld world, double x, double z, double fromY) {
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        int startY = (int) Math.floor(fromY) + 1;
        for (int y = startY; y >= startY - 20 && y >= world.getBottomY(); y--) {
            mpos.set(x, y, z);
            var st = world.getBlockState(mpos);
            if (!st.isAir() && !st.getCollisionShape(world, mpos).isEmpty()) {
                return y + 1;
            }
        }
        return fromY;
    }

    /** 绿色落点指示器：中心 + 圆环 + 向上细柱（仿契灵紫色落点）。 */
    private static void renderGreenLanding(ClientWorld world, Vec3d landing) {
        DustParticleEffect dust = new DustParticleEffect(GREEN, 1.4f);
        double cx = landing.x;
        double cy = landing.y + 0.05;
        double cz = landing.z;
        // 中心点
        world.addParticle(dust, cx, cy, cz, 0, 0, 0);
        // 半径 3 格圆环（24 点，更密以体现 3 格范围）
        int ringPoints = 24;
        for (int i = 0; i < ringPoints; i++) {
            double ang = (Math.PI * 2.0 / ringPoints) * i;
            world.addParticle(dust, cx + Math.cos(ang) * RADIUS, cy, cz + Math.sin(ang) * RADIUS, 0, 0, 0);
        }
        // 向上细柱方便远处可见
        for (int i = 1; i <= 4; i++) {
            world.addParticle(dust, cx, cy + i * 0.4, cz, 0, 0, 0);
        }
    }

    private static boolean isWindSpirit(ClientPlayerEntity player) {
        try {
            IForm form = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
            if (form == null) return false;
            return FormIdentifiers.OCELOT_SP.equals(form.getFormID());
        } catch (Exception e) {
            return false;
        }
    }
}
