package net.jackcooper.shapeShifterCurseAddon.sound;

import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

/** A direct sound entry carries its doubled broadcast radius through the vanilla sound packet. */
public final class AddonSoundRange {
    private static final String PREFIX = "sound_range_x2/";
    private AddonSoundRange() {}

    /** Keep vanilla's recipient selection, random seed and entity overrides. Mark only this playback. */
    public static void play(World world, PlayerEntity except, double x, double y, double z,
                            SoundEvent event, SoundCategory category, float volume, float pitch) {
        SoundRangeRules.withActionScope(() -> world.playSound(except, x, y, z, event, category, volume, pitch));
    }

    public static void play(World world, PlayerEntity except, BlockPos pos,
                            SoundEvent event, SoundCategory category, float volume, float pitch) {
        SoundRangeRules.withActionScope(() -> world.playSound(except, pos, event, category, volume, pitch));
    }

    public static void play(PlayerEntity player, SoundEvent event, SoundCategory category, float volume, float pitch) {
        SoundRangeRules.withActionScope(() -> player.playSound(event, category, volume, pitch));
    }

    public static void play(Entity source, SoundEvent event, float volume, float pitch) {
        SoundRangeRules.withActionScope(() -> source.playSound(event, volume, pitch));
    }

    public static PlaySoundS2CPacket positionedPacket(RegistryEntry<SoundEvent> event, SoundCategory category,
                                                      double x, double y, double z, float volume, float pitch, long seed) {
        return new PlaySoundS2CPacket(extend(event, volume), category, x, y, z, volume, pitch, seed);
    }

    public static PlaySoundFromEntityS2CPacket trackingPacket(RegistryEntry<SoundEvent> event, SoundCategory category,
                                                             Entity source, float volume, float pitch, long seed) {
        return new PlaySoundFromEntityS2CPacket(extend(event, volume), category, source, volume, pitch, seed);
    }

    public static boolean isAddonActionTarget(Object target) {
        if (target instanceof LivingEntity living) {
            var form = FormUtils.getCurrentForm(living);
            return form != null && form.getFormID() != null && form.getFormID().getNamespace().equals("ssc_addon");
        }
        return target instanceof Pair<?, ?> pair
                && (isAddonActionTarget(pair.getLeft()) || isAddonActionTarget(pair.getRight()));
    }

    public static boolean isExtended(SoundEvent event, float callVolume) {
        return !originalId(event.getId()).equals(event.getId());
    }

    /** Explicit packet marker avoids mistaking another mod's fixed-range sound for an SSCA sound. */
    public static Identifier originalId(Identifier id) {
        if (!id.getNamespace().equals("ssc_addon") || !id.getPath().startsWith(PREFIX)) return id;
        String encoded = id.getPath().substring(PREFIX.length());
        int slash = encoded.indexOf('/');
        if (slash <= 0 || slash == encoded.length() - 1) return id;
        Identifier decoded = Identifier.tryParse(encoded.substring(0, slash) + ":" + encoded.substring(slash + 1));
        return decoded != null ? decoded : id;
    }

    public static SoundEvent extend(SoundEvent event, float callVolume) {
        if (isExtended(event, callVolume)) return event;
        Identifier id = event.getId();
        Identifier marker = new Identifier("ssc_addon", PREFIX + id.getNamespace() + "/" + id.getPath());
        return SoundEvent.of(marker, (float) SoundRangeRules.distance(event.getDistanceToTravel(callVolume)));
    }

    public static RegistryEntry<SoundEvent> extendPlayback(RegistryEntry<SoundEvent> event, float callVolume) {
        return SoundRangeRules.inActionScope() ? extend(event, callVolume) : event;
    }

    private static RegistryEntry<SoundEvent> extend(RegistryEntry<SoundEvent> entry, float volume) {
        SoundEvent original = entry.value();
        SoundEvent extended = extend(original, volume);
        return extended == original ? entry : RegistryEntry.of(extended);
    }
}
