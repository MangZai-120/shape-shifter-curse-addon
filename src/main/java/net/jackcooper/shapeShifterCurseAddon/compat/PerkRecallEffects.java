package net.jackcooper.shapeShifterCurseAddon.compat;

import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.joml.Vector3f;

/** One-shot feedback after the form's own recall action accepts the cast. */
final class PerkRecallEffects {
    private static final DustParticleEffect GOLDEN_SAND =
            new DustParticleEffect(new Vector3f(1.0f, 0.68f, 0.2f), 1.0f);

    private PerkRecallEffects() { }

    static void soulRecall(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.65f, 1.25f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.2f);
        burst(player, ParticleTypes.REVERSE_PORTAL, 24, 0.65, 0.35, 0.035);
        burst(player, ParticleTypes.SOUL, 10, 0.45, 0.25, 0.02);
        burst(player, ParticleTypes.ENCHANT, 16, 0.3, 0.2, 0.2);
    }

    static void brandRecall(ServerPlayerEntity player) {
        var world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.5f, 0.8f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.75f, 1.1f);
        burst(player, GOLDEN_SAND, 24, 0.65, 0.3, 0.025);
        burst(player, ParticleTypes.ENCHANT, 18, 0.4, 0.25, 0.2);
    }

    private static void burst(ServerPlayerEntity player, ParticleEffect particle, int count,
                              double spread, double height, double speed) {
        ParticleUtils.spawnDecorationParticles(player.getServerWorld(), player, particle,
                player.getX(), player.getY() + 0.5, player.getZ(),
                count, spread, height, spread, speed);
    }
}
