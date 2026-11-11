package net.jackcooper.shapeShifterCurseAddon.client.sound;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;

/** One local ding at a charge threshold; a held key never loops this sound. */
public final class ParasiticFruitChargeSoundInstance extends AbstractSoundInstance {
    public ParasiticFruitChargeSoundInstance(boolean fullCharge) {
        super(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, Random.create());
        relative = true;
        attenuationType = SoundInstance.AttenuationType.NONE;
        volume = 0.45F;
        pitch = fullCharge ? 1.2F : 0.8F;
        x = 0; y = 0; z = 0;
    }

}
