package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class SnowFoxSounds {
    static final SoundEvent GATHER = event("gather");
    static final SoundEvent PLUNGE = event("plunge");
    static final SoundEvent IMPACT = event("ridge_impact");
    static final SoundEvent PACK = event("pack");
    public static final SoundEvent ROLLING = event("rolling");
    static final SoundEvent SHATTER = event("shatter");

    private SnowFoxSounds() {}
    static void init() {}

    private static SoundEvent event(String name) {
        var id = new Identifier("my_addon", "snow_fox." + name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    static void play(ServerPlayerEntity player, Vec3d pos, SoundEvent event, float volume, float pitch) {
        AddonSoundRange.play(player.getWorld(), null, pos.x, pos.y, pos.z, event, SoundCategory.PLAYERS, volume, pitch);
    }
}
