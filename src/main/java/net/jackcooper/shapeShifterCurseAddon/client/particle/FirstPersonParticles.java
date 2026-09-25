package net.jackcooper.shapeShifterCurseAddon.client.particle;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;

import java.util.UUID;

public final class FirstPersonParticles {
    // All scopes execute on the client thread, including decoded network packets.
    private static UUID source;
    private static boolean receivedOnce;

    private FirstPersonParticles() {}

    public static void init() {
        AsyncParticleCompatibility.init();
        ClientPlayNetworking.registerGlobalReceiver(DecorationParticles.ID, (client, handler, buf, sender) -> {
            var dimension = buf.readIdentifier();
            var packet = new ParticleS2CPacket(buf);
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
                emit(client.player.getUuid(), () -> handler.onParticle(packet));
            });
        });
    }

    public static void emit(UUID owner, Runnable emission) {
        UUID previous = source;
        source = owner;
        try {
            emission.run();
        } finally {
            source = previous;
        }
    }

    public static void tag(Particle particle) {
        if (source == null || particle == null) return;
        var player = MinecraftClient.getInstance().player;
        if (player != null && source.equals(player.getUuid())
                && particle instanceof OwnedDecoration decoration) {
            decoration.ssca$setDecorationOwner(source);
        }
    }
}
