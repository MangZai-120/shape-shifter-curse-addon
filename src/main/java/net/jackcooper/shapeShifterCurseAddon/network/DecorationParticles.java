package net.jackcooper.shapeShifterCurseAddon.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Explicit opt-in for self decoration. Other viewers receive the original vanilla particle packet. */
public final class DecorationParticles {
    public static final Identifier ID = new Identifier("ssc_addon", "self_decoration_v1");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("SSCA_DecorationParticles");
    private static boolean loggedOnce;

    private DecorationParticles() {}

    public static void spawn(ServerWorld world, Entity owner, ParticleEffect effect,
                             double x, double y, double z, int count,
                             double dx, double dy, double dz, double speed) {
        ParticleS2CPacket original = new ParticleS2CPacket(effect, false, x, y, z,
                (float) dx, (float) dy, (float) dz, (float) speed, count);
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (owner == viewer && ServerPlayNetworking.canSend(viewer, ID)) {
                // 本人：与 SustainedVisuals 通道同款 ServerPlayNetworking.send 直发（该通道实测可达），
                // 不走 sendToPlayerIfNearby（其 32 格判定 + packet 参数在部分环境下可能被绕过）
                sendEnvelope(world, viewer, original);
            } else if (owner != viewer) {
                world.sendToPlayerIfNearby(viewer, false, x, y, z, original);
            }
        }
    }

    /** Same spherical range and force flag as ParticleUtils, including its 64-block variant. */
    public static void forced(ServerWorld world, Entity owner, ParticleEffect effect,
                              double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed, double range) {
        ParticleS2CPacket original = new ParticleS2CPacket(effect, true, x, y, z,
                (float) dx, (float) dy, (float) dz, (float) speed, count);
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer.squaredDistanceTo(x, y, z) > range * range) continue;
            if (owner == viewer && ServerPlayNetworking.canSend(viewer, ID)) {
                sendEnvelope(world, viewer, original);
            } else if (owner != viewer) {
                viewer.networkHandler.sendPacket(original);
            }
        }
    }

    /** 本人信封直发 + 一次性日志：若实机再失效，日志可直接定位断在哪一环。 */
    private static void sendEnvelope(ServerWorld world, ServerPlayerEntity viewer, ParticleS2CPacket original) {
        var buf = PacketByteBufs.create();
        buf.writeIdentifier(world.getRegistryKey().getValue());
        original.write(buf);
        ServerPlayNetworking.send(viewer, ID, buf);
        if (!loggedOnce) {
            loggedOnce = true;
            LOGGER.info("[SSCA_DecorationParticles] first self-decoration envelope sent to {} via channel {}",
                    viewer.getName().getString(), ID);
        }
    }
}
