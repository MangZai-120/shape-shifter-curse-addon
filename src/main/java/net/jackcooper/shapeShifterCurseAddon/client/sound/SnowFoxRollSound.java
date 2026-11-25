package net.jackcooper.shapeShifterCurseAddon.client.sound;

import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxMotion;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxSounds;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxState;
import net.jackcooper.shapeShifterCurseAddon.sound.ExtendedRangeSound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public final class SnowFoxRollSound extends MovingSoundInstance implements ExtendedRangeSound {
    private final PlayerEntity owner;
    private Vec3d previous;
    private float speed;

    public SnowFoxRollSound(PlayerEntity owner) {
        super(SnowFoxSounds.ROLLING, SoundCategory.PLAYERS, Random.create());
        this.owner = owner;
        previous = owner.getPos();
        x = previous.x; y = previous.y; z = previous.z;
        volume = .001f;
        repeat = true;
    }

    @Override public boolean ssca$hasExtendedRange() { return true; }

    @Override public void tick() {
        var shell = SnowFoxMotion.STATE.get(owner);
        if (owner.isRemoved() || MinecraftClient.getInstance().world != owner.getWorld()
                || shell.phase() != SnowFoxState.Phase.SHELL) {
            setDone();
            return;
        }
        Vec3d pos = owner.getPos();
        float travel = (float)Math.hypot(pos.x - previous.x, pos.z - previous.z);
        previous = pos;
        speed = MathHelper.lerp(.32f, speed, MathHelper.clamp(travel / .4f, 0, 1));
        float size = MathHelper.clamp((shell.mass() - 10) / 40, 0, 1);
        volume = speed * (.33f + .49f * size);
        pitch = .78f + .31f * speed - .25f * size;
        x = pos.x; y = pos.y + .22; z = pos.z;
    }
}
