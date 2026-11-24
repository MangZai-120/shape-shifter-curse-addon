package net.jackcooper.shapeShifterCurseAddon.client.sound;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisSounds;
import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisWallProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** Positional water loops for the travelling crest and the two mouth attacks. */
public final class OrcafisWallSound extends MovingSoundInstance {
    private final OrcafisEntity boss;
    private final SoundEvent event;

    private OrcafisWallSound(OrcafisEntity boss, SoundEvent event) {
        super(event, boss.getSoundCategory(), Random.create());
        this.boss = boss;
        this.event = event;
        repeat = true;
        tick();
    }

    private static SoundEvent current(OrcafisEntity boss) {
        float t = boss.motionTick(0);
        if (!boss.isAlive() || boss.isSilent()) return null;
        return switch (boss.motion()) {
            case WAVE -> t >= OrcafisSounds.WALL_RELEASE && t < OrcafisEntity.Motion.WAVE.ticks ? OrcafisSounds.WALL : null;
            case UNDERTOW -> t < OrcafisEntity.UNDERTOW_DRAW_TICKS && boss.drawingWater() ? OrcafisSounds.UNDERTOW_DRAW : null;
            case WATER_JET -> t >= OrcafisEntity.JET_START && t < OrcafisEntity.JET_END && boss.jetLength() > 0 ? OrcafisSounds.JET_STREAM : null;
            default -> null;
        };
    }

    @Override public void tick() {
        if (current(boss) != event) { setDone(); return; }
        float t = boss.motionTick(0);
        if (event != OrcafisSounds.WALL) {
            Vec3d mouth = boss.getPos().add(boss.jetOrigin());
            x = mouth.x; y = mouth.y; z = mouth.z;
            boolean intake = event == OrcafisSounds.UNDERTOW_DRAW;
            float strength = intake ? MathHelper.clamp(t/(OrcafisEntity.UNDERTOW_DRAW_TICKS-1),0,1) : MathHelper.clamp((t-13)/3,0,1);
            volume = boss.isMotionPaused() ? 0 : (intake ? .35f+.9f*strength : 1.25f*strength);
            pitch = intake ? .76f+.31f*strength : 1f;
            return;
        }
        float age = t - OrcafisSounds.WALL_RELEASE;
        Vec3d crest = boss.getPos().add(Vec3d.fromPolar(0,boss.bodyYaw).multiply(OrcafisSounds.wallDistance(age)));
        x = crest.x; y = crest.y + .8; z = crest.z;
        volume = boss.isMotionPaused() ? 0 : 1.32f * MathHelper.clamp((age+1)/6,.12f,1) * OrcafisWallProfile.remaining(age);
    }

    public static void register() {
        Map<OrcafisEntity,OrcafisWallSound> tracked = new IdentityHashMap<>();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tracked.clear());
        ClientEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            if (entity instanceof OrcafisEntity boss) tracked.put(boss,null);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,world) -> {
            var sound = tracked.remove(entity);
            if (sound != null) MinecraftClient.getInstance().getSoundManager().stop(sound);
        });
        ClientTickEvents.END_WORLD_TICK.register(world -> {
            var manager = MinecraftClient.getInstance().getSoundManager();
            var entries = tracked.entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                var boss = entry.getKey();
                var sound = entry.getValue();
                if (boss.getWorld() != world) {
                    if (sound != null) manager.stop(sound);
                    entries.remove();
                    continue;
                }
                SoundEvent desired = current(boss);
                if (desired != null) {
                    if (sound == null || sound.event != desired || (world.getTime()%20 == 0 && !manager.isPlaying(sound))) {
                        if (sound != null) manager.stop(sound);
                        sound = new OrcafisWallSound(boss,desired);
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
