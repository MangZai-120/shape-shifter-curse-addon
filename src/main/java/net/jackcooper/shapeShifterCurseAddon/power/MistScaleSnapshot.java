package net.jackcooper.shapeShifterCurseAddon.power;

import net.minecraft.entity.LivingEntity;
import net.jackcooper.shapeShifterCurseAddon.util.StableSscScaleWrites;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

/** Owns only the temporary mist overrides, never the normal form's scale. */
final class MistScaleSnapshot {
    private ScaleData[] channels;
    private float[] previous, applied;
    private Boolean[] persistence;

    void apply(LivingEntity entity, float width, float height, float eye) {
        if (channels == null) {
            channels = new ScaleData[]{ScaleTypes.HITBOX_WIDTH.getScaleData(entity),
                    ScaleTypes.HITBOX_HEIGHT.getScaleData(entity), ScaleTypes.EYE_HEIGHT.getScaleData(entity)};
            previous = new float[3]; persistence = new Boolean[3];
            for (int i=0;i<3;i++) {
                previous[i]=channels[i].getBaseScale();
                persistence[i]=channels[i].getPersistence();
                channels[i].setPersistence(false);
            }
        }
        applied = new float[]{width,height,eye};
        for (int i=0;i<3;i++) {
            if (!StableSscScaleWrites.skipScale(channels[i],applied[i])) channels[i].setScale(applied[i]);
        }
    }

    void restore() {
        if (channels == null) return;
        for (int i=0;i<3;i++) {
            // Form changes apply the new scales before removing old powers. Preserve those writes.
            if (channels[i].getBaseScale()==applied[i] && channels[i].getTargetScale()==applied[i]) {
                channels[i].setScale(previous[i]);
                channels[i].setPersistence(persistence[i]);
            }
        }
        channels=null;
    }
}
