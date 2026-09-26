package net.jackcooper.shapeShifterCurseAddon.client.particle;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;

import java.util.UUID;

public final class FirstPersonParticles {
    // All scopes execute on the client thread, including decoded network packets.
    // 2026-09-26 线程封闭修复：AsyncParticles 可能把粒子 tick 挪到工作线程，ssca$inheritDecoration
    // 的 emitScoped 会在该线程执行；static 字段的保存/恢复在两线程交织时会交叉覆盖（A 存 previous=null
    // → B 存 previous=ownerA → A 恢复 null → B 恢复 ownerA，owner 永久泄漏→后续无关粒子被误打标淡出）。
    // ThreadLocal 让每个线程持有独立作用域，与服务端 DecorationParticleScope 同款模式。
    private static final ThreadLocal<UUID> source = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> projectileScope = new ThreadLocal<>();
    private static boolean receivedOnce;

    private FirstPersonParticles() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DecorationParticles.ID, (client, handler, buf, sender) -> {
            var dimension = buf.readIdentifier();
            var packet = new ParticleS2CPacket(buf);
            // 2026-09-26 协议 v1.1：信封尾部追加 1 字节弹道标记（0=普通装饰，1=弹道粒子豁免锥压制）。
            // 旧版发送端无此字节→ readBoolean 抛越界→按普通装饰处理，前后向兼容。
            boolean projectile;
            try {
                projectile = buf.readBoolean();
            } catch (Exception legacy) {
                projectile = false;
            }
            final boolean isProjectile = projectile;
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null || client.player == null
                        || !client.world.getRegistryKey().getValue().equals(dimension)) {
                    return;
                }
                if (!receivedOnce) {
                    receivedOnce = true;
                    org.slf4j.LoggerFactory.getLogger("SSCA_DecorationParticles")
                            .info("[SSCA_DecorationParticles] first self-decoration envelope received and decoded");
                }
                // Only the effect's owner receives this envelope. Use vanilla decoding/spread/settings.
                emitScoped(client.player.getUuid(), isProjectile, () -> handler.onParticle(packet));
            });
        });
    }

    public static void emit(UUID owner, Runnable emission) { emitScoped(owner, false, emission); }

    /** 弹道作用域重载：内部产生的粒子豁免准星锥压制（火球拖尾/爆炸/火环）。 */
    public static void emitProjectile(UUID owner, Runnable emission) { emitScoped(owner, true, emission); }

    private static void emitScoped(UUID owner, boolean projectile, Runnable emission) {
        UUID previous = source.get();
        Boolean previousProjectile = projectileScope.get();
        source.set(owner);
        projectileScope.set(projectile);
        try {
            emission.run();
        } finally {
            if (previous == null) source.remove(); else source.set(previous);
            if (previousProjectile == null) projectileScope.remove(); else projectileScope.set(previousProjectile);
        }
    }

    public static void tag(Particle particle) {
        if (net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.isProtected()) return;
        UUID owner = source.get();
        boolean projectile = Boolean.TRUE.equals(projectileScope.get());
        var scopedOwner = net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.owner();
        if (owner == null && scopedOwner != null) {
            owner = scopedOwner.getUuid();
            projectile = net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.isProjectileScope();
        }
        if (owner == null || particle == null) return;
        var client = MinecraftClient.getInstance();
        var player = client == null ? null : client.player;
        if (player != null && owner.equals(player.getUuid())
                && particle instanceof ParticleOwnership decoration) {
            decoration.ssca$setDecorationOwner(owner);
            if (projectile) decoration.ssca$markProjectileDecoration();
        }
    }
}
