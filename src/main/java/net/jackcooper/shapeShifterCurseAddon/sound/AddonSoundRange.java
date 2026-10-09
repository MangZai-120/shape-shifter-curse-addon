package net.jackcooper.shapeShifterCurseAddon.sound;

import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.tuple.Pair;

/** A direct sound entry carries its doubled broadcast radius through the vanilla sound packet. */
public final class AddonSoundRange {
    private static final String PREFIX = "sound_range_x2/";
    private AddonSoundRange() {}

    public static boolean isAddonActionTarget(Object target) {
        if (target instanceof LivingEntity living) {
            var form = FormUtils.getCurrentForm(living);
            return form != null && form.getFormID() != null && form.getFormID().getNamespace().equals("my_addon");
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
        if (!SoundRangeRules.isAddonPlayback() || isExtended(event.value(), callVolume)) return event;
        return RegistryEntry.of(extend(event.value(), callVolume));
    }
}
