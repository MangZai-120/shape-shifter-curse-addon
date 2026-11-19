package net.jackcooper.shapeShifterCurseAddon.client.sound;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

public final class OrcafisWallSound extends MovingSoundInstance {
    private final OrcafisEntity boss;

    private OrcafisWallSound(OrcafisEntity boss) {
        super(OrcafisSounds.WALL, boss.getSoundCategory(), Random.create());
        this.boss = boss;
        repeat = true;
        tick();
    }

    private static boolean active(OrcafisEntity boss) {
        float t = boss.motionTick(0);
        return boss.isAlive() && !boss.isSilent() && boss.motion() == OrcafisEntity.Motion.WAVE
                && t >= OrcafisSounds.WALL_RELEASE && t < OrcafisEntity.Motion.WAVE.ticks;
    }

    @Override public void tick() {
        if (!active(boss)) { setDone(); return; }
        float t = boss.motionTick(0);
        float age = t - OrcafisSounds.WALL_RELEASE;
        Vec3d crest = boss.getPos().add(Vec3d.fromPolar(0,boss.bodyYaw).multiply(OrcafisSounds.wallDistance(age)));
        x = crest.x; y = crest.y + .8; z = crest.z;
        float retreat = MathHelper.clamp((t-OrcafisSounds.WALL_RECEDES)/(OrcafisEntity.Motion.WAVE.ticks-OrcafisSounds.WALL_RECEDES),0,1);
        volume = boss.isMotionPaused() ? 0 : 1.32f * MathHelper.clamp((age+1)/6,.12f,1) * (1-retreat*retreat*(3-2*retreat));
    }

    public static void register() {
        Map<OrcafisEntity,OrcafisWallSound> tracked = new IdentityHashMap<>();
        ClientEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            if (entity instanceof OrcafisEntity boss) tracked.put(boss,null);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,world) -> {
            var sound = tracked.remove(entity);
            if (sound != null) MinecraftClient.getInstance().getSoundManager().stop(sound);
        });
        ClientTickEvents.END_WORLD_TICK.register(world -> {
            var manager = MinecraftClient.getInstance().getSoundManager();
            for (var entry : tracked.entrySet()) {
                var boss = entry.getKey();
                var sound = entry.getValue();
                if (boss.getWorld() != world) continue;
                if (active(boss)) {
                    if (sound == null || (world.getTime()%20 == 0 && !manager.isPlaying(sound))) {
                        sound = new OrcafisWallSound(boss);
                        entry.setValue(sound);
                        manager.play(sound);
                    }
                } else if (sound != null) {
                    manager.stop(sound);
                    entry.setValue(null);
                }
            }
        });
    }
}
