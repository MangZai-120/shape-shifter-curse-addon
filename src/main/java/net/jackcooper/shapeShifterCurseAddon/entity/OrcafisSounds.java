package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class OrcafisSounds {
    private static final SoundEvent STEP = event("step");
    private static final SoundEvent GATHER = event("gather");
    private static final SoundEvent BREAK_GROUND = event("break_ground");
    private static final SoundEvent LAND = event("land");
    private static final SoundEvent SWEEP = event("sweep");
    private static final SoundEvent PLOW = event("plow");
    private static final SoundEvent RUSH = event("rush");
    private static final SoundEvent BREACH = event("breach");
    private static final SoundEvent SPLASH = event("splash");
    private static final SoundEvent[] TIDE_BEATS = {event("slap"),event("slap_snap"),event("slap_double"),
            event("slap_draw"),event("slap_final")};
    private static final float[] BEAT_VOLUME = {1.40f,1.06f,1.45f,.90f,1.72f};
    private static final SoundEvent TIDE_PULL = event("tide_pull");
    private static final SoundEvent CREST_PASS = event("crest_pass");
    private static final SoundEvent BACKWASH = event("backwash");
    private static final SoundEvent SETTLE = event("settle");
    public static final SoundEvent WALL = event("wall");
    public static final SoundEvent UNDERTOW_DRAW = event("undertow_draw");
    static final SoundEvent UNDERTOW_BITE = event("undertow_bite");
    static final SoundEvent JET_CHARGE = event("jet_charge");
    public static final SoundEvent JET_STREAM = event("jet_stream");
    static final SoundEvent JET_RELEASE = event("jet_release");
    public static final int WALL_RELEASE = 40;
    public static final int WALL_RECEDES = 90;

    private OrcafisSounds() {}
    static void init() {}

    static void mouthSound(OrcafisEntity boss, Vec3d mouth, SoundEvent sound, float volume) {
        if (!boss.isSilent())
            boss.getWorld().playSound(null,mouth.x,mouth.y,mouth.z,sound,boss.getSoundCategory(),volume,1f);
    }

    private static SoundEvent event(String key) {
        Identifier id = new Identifier("my_addon","orcafis." + key);
        return Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));
    }

    public static float wallDistance(float age) {
        return 2.3f + age * .27f;
    }

    static void passingCrest(ServerPlayerEntity player, OrcafisEntity boss, Vec3d position, int beat) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(CREST_PASS),boss.getSoundCategory(),
                position.x,position.y,position.z,beat == 4 ? .82f : .58f,beat == 4 ? .86f : 1.04f,boss.getRandom().nextLong()));
    }

    static void tick(OrcafisEntity boss, OrcafisEntity.Motion motion, int t, boolean stepped) {
        switch (motion) {
            case WALK -> {
                if (stepped && (t == 10 || t == 40)) {
                    boss.playSound(STEP, 1.1f, t == 10 ? .95f : 1.02f);
                    var floor = boss.getWorld().getBlockState(BlockPos.ofFloored(boss.getX(),boss.getY()-.12,boss.getZ()));
                    if (!floor.isAir()) boss.playSound(floor.getSoundGroup().getStepSound(), .54f, .84f);
                }
            }
            case BURROW -> { if (t % 16 == 4) boss.playSound(RUSH,.52f,.72f); }
            case BREACH -> {
                if (t == 12) boss.playSound(PLOW,.74f,.83f);
                if (t == OrcafisLandAttacks.LIFT) boss.playSound(BREAK_GROUND,1.4f,.92f);
                if (t == OrcafisLandAttacks.IMPACT) {
                    boss.playSound(LAND,1.8f,.92f);
                    boss.playSound(SoundEvents.ENTITY_PLAYER_HURT,1f,1f);
                    boss.playSound(SoundEvents.ENTITY_PLAYER_BIG_FALL,1.35f,1f);
                }
                if (t == OrcafisLandAttacks.IMPACT + 10) boss.playSound(SETTLE,.62f,.86f);
            }
            case SWEEP -> {
                if (t == 9) boss.playSound(STEP,.75f,.88f);
                if (t == 19) boss.playSound(SWEEP,1.3f,.96f);
            }
            case PLOW -> {
                if (t == 8) boss.playSound(BREAK_GROUND,.78f,.80f);
                if (t == 32) boss.playSound(PLOW,1.4f,1f);
                if (t == 52) boss.playSound(SETTLE,.73f,1.04f);
            }
            case DASH -> {
                if (t == 5) boss.playSound(GATHER,.63f,1.16f);
                if (t == 13) boss.playSound(RUSH,1.4f,1.07f);
            }
            case WATER_LUNGE -> { if (t == 8) boss.playSound(RUSH,1.05f,1.12f); }
            case LAND_BITE, WATER_BITE -> {
                if (t == 4) boss.playSound(RUSH,.55f,1.3f);
                if (t == OrcafisHuntingAttack.BITE_IMPACT) {
                    boss.playSound(SoundEvents.ENTITY_FOX_BITE,1.1f,.68f);
                    if (motion == OrcafisEntity.Motion.WATER_BITE) boss.playSound(SPLASH,.7f,1.25f);
                }
            }
            case RETURN -> { if (t == 12) boss.playSound(RUSH,.58f,.83f); }
            case POUNCE -> {
                if (t == 24) boss.playSound(BREACH,1.25f,1.04f);
                if (t == 66) {
                    boss.playSound(SPLASH,1.65f,.93f);
                    boss.playSound(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED,1.45f,1f);
                }
            }
            case TIDE -> {
                if (t == 4) boss.playSound(GATHER,.74f,1.08f);
                int age = t - OrcafisTidePulse.START;
                if (age >= -7 && age < 4*OrcafisTidePulse.PERIOD && (age+7)%OrcafisTidePulse.PERIOD == 0) {
                    boss.playSound(TIDE_PULL,age > 65 ? 1.08f : .70f,age > 65 ? .9f : 1.06f);
                    boss.playSound(SoundEvents.AMBIENT_UNDERWATER_EXIT,1.15f,1f);
                }
                if (OrcafisTidePulse.active(t) && age%OrcafisTidePulse.PERIOD == 0) {
                    int beat = age/OrcafisTidePulse.PERIOD;
                    boss.playSound(TIDE_BEATS[beat],BEAT_VOLUME[beat],1f);
                    boss.playSound(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED,1.18f,1f);
                }
            }
            case WAVE -> {
                if (t == 15) boss.playSound(GATHER,1.1f,.91f);
                if (t == WALL_RELEASE) boss.playSound(SPLASH,1.36f,1.04f);
                if (t == WALL_RECEDES && !boss.isSilent()) {
                    Vec3d lastCrest = boss.getPos().add(Vec3d.fromPolar(0,boss.bodyYaw)
                            .multiply(wallDistance(OrcafisEntity.Motion.WAVE.ticks - 1 - WALL_RELEASE)));
                    boss.getWorld().playSound(null,lastCrest.x,lastCrest.y+.8,lastCrest.z,BACKWASH,boss.getSoundCategory(),1.4f,1f);
                }
            }
            default -> {}
        }
    }
}
