package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.jackcooper.shapeShifterCurseAddon.entity.RegAddonEntities;
import net.jackcooper.shapeShifterCurseAddon.entity.SnowFoxShellEntity;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionTalents;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class SnowFoxState implements AutoSyncedComponent, ServerTickingComponent {
    public enum Phase { IDLE, CHARGE, DIVE, SHELL, RECOVERY }
    private static final Identifier POWERS = new Identifier("my_addon", "snow_fox_talents");
    private final PlayerEntity owner;
    private Phase phase = Phase.IDLE;
    private long since;
    private long castId = -1;
    private ServerWorld castWorld;
    private Vec3d position;
    private SnowFoxRoll roll;
    private SnowFoxShellEntity hitShell;
    private SnowFoxWave wave;
    private float displayMass;
    private boolean checkPowers = true;
    private boolean wasFox;

    public SnowFoxState(PlayerEntity owner) { this.owner = owner; }
    public Phase phase() { return phase; }
    public float mass() { return displayMass; }
    public float age(float delta) { return (owner.getWorld().getTime() - since) + delta; }

    public void start(ServerPlayerEntity player, boolean rolling) {
        if (rolling && phase == Phase.SHELL) {
            if (age(0) >= 8) {
                shedSnow(player, false);
                endShell(player);
            }
            return;
        }
        if (phase != Phase.IDLE || !player.isAlive() || player.isSpectator() || player.hasVehicle()
                || (!rolling && player.isOnGround())) return;
        String node = rolling ? "rolling_charge" : "super_dive";
        if (!EvolutionTalents.isActive(player, node)) return;
        if (rolling && SnowFoxRoll.blockAt(player, player.getPos(), .75) != null) return;
        String skill = rolling ? SnowFoxMotion.ROLL.toString() : SnowFoxMotion.DIVE.toString();
        if (!SkillCooldowns.begin(player, skill)) return;
        castId = SkillCooldowns.currentCastId(player, skill);
        castWorld = player.getServerWorld();
        position = player.getPos();
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        if (rolling) {
            roll = new SnowFoxRoll(player);
            displayMass = 10;
            hitShell = RegAddonEntities.SNOW_FOX_SHELL.create(castWorld);
            if (hitShell != null) {
                hitShell.align(player, position, .75);
                castWorld.spawnEntity(hitShell);
            }
        }
        change(player, rolling ? Phase.SHELL : Phase.CHARGE);
        if (rolling) SnowFoxSounds.play(player, position, SoundEvents.BLOCK_SNOW_BREAK, .85f, 1f);
    }

    private void change(ServerPlayerEntity player, Phase next) {
        phase = next;
        since = player.getWorld().getTime();
        SnowFoxMotion.STATE.sync(player);
    }

    public void steer(double x, double z) {
        if (phase == Phase.SHELL && roll != null) roll.steer(x, z);
    }

    public boolean absorb(ServerPlayerEntity player, float damage) {
        if (phase != Phase.SHELL || roll == null || castWorld != player.getWorld() || !player.isAlive()) return false;
        roll.mass -= Math.max(0, damage);
        if (roll.mass < 0) {
            shedSnow(player, true);
            endShell(player);
        }
        else {
            displayMass = (float)roll.mass;
            SnowFoxMotion.STATE.sync(player);
        }
        return true;
    }

    private void shedSnow(ServerPlayerEntity player, boolean broken) {
        double r = SnowFoxRoll.radius(displayMass);
        Vec3d center = position.add(0, r, 0);
        castWorld.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z,
                broken ? 28 : 14, r * .54, r * .38, r * .54, broken ? .1 : .045);
        SnowFoxSounds.play(player, center, SoundEvents.BLOCK_SNOW_BREAK,
                broken ? .82f : .65f, broken ? 1.18f : .85f);
    }

    private void endShell(ServerPlayerEntity player) {
        SkillCooldowns.completed(player, SnowFoxMotion.ROLL.toString(), castId);
        castId = -1;
        roll = null;
        if (hitShell != null) { hitShell.discard(); hitShell = null; }
        change(player, Phase.RECOVERY);
    }

    public void interrupt(ServerPlayerEntity player) {
        if (phase == Phase.SHELL) SkillCooldowns.interruptedFull(player, SnowFoxMotion.ROLL.toString(), castId);
        if (phase == Phase.CHARGE || phase == Phase.DIVE)
            SkillCooldowns.cancelled(player, SnowFoxMotion.DIVE.toString(), castId);
        castId = -1;
        roll = null;
        if (hitShell != null) { hitShell.discard(); hitShell = null; }
        wave = null;
        if (phase != Phase.IDLE) change(player, Phase.IDLE);
    }

    @Override public void serverTick() {
        if (!(owner instanceof ServerPlayerEntity p)) return;
        boolean fox = FormUtils.isForm(p, FormIdentifiers.UPGRADE_SNOW_FOX);
        if (checkPowers || fox != wasFox || (fox && p.age % 5 == 0)) reconcilePowers(p, fox);
        wasFox = fox;
        checkPowers = false;
        if (phase == Phase.IDLE && wave == null) return;
        if (!fox || !p.isAlive() || p.isSpectator() || p.hasVehicle() || castWorld != p.getWorld()
                || AbilityUseGuard.blocked(p)) { interrupt(p); return; }
        if (wave != null && !wave.tick(p)) wave = null;
        switch (phase) {
            case RECOVERY -> { if (age(0) >= 6) change(p, Phase.IDLE); }
            case SHELL -> {
                if (age(0) >= 8) {
                    var step = roll.advance(p, position);
                    position = step.feet();
                    if (step.contact() != null) {
                        SnowFoxRoll.burst(p, position, roll.mass, step.contact(), step.enemy());
                        move(p, position);
                        endShell(p);
                        return;
                    }
                    if (Math.abs(displayMass - roll.mass) > .01) {
                        displayMass = (float)roll.mass;
                        SnowFoxMotion.STATE.sync(p);
                    }
                }
                if (hitShell != null) hitShell.align(p,position,SnowFoxRoll.radius(roll.mass));
                move(p, position);
            }
            case CHARGE -> {
                move(p, position);
                if (age(0) >= 10) {
                    SnowFoxSounds.play(p, position, SnowFoxSounds.PLUNGE, .92f, 1f);
                    change(p, Phase.DIVE);
                    descend(p);
                }
            }
            case DIVE -> descend(p);
            default -> { }
        }
    }

    private void descend(ServerPlayerEntity player) {
        Vec3d end = position.add(0, -2.0, 0);
        var hit = castWorld.raycast(new RaycastContext(position.add(0,.001,0), end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            if (end.y < castWorld.getBottomY()) { interrupt(player); return; }
            castWorld.spawnParticles(ParticleTypes.END_ROD, position.x, position.y, position.z, 4, .045,.42,.045,0);
            position = end;
            move(player, end);
            return;
        }
        position = hit.getPos();
        move(player, position);
        SkillCooldowns.completed(player, SnowFoxMotion.DIVE.toString(), castId);
        castId = -1;
        wave = new SnowFoxWave(player, position);
        change(player, Phase.IDLE);
    }

    private static void move(ServerPlayerEntity p, Vec3d pos) {
        p.fallDistance = 0;
        p.setVelocity(Vec3d.ZERO);
        p.networkHandler.requestTeleport(pos.x, pos.y, pos.z, p.getYaw(), p.getPitch(), EnumSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT));
    }

    private void reconcilePowers(ServerPlayerEntity player, boolean fox) {
        var holder = PowerHolderComponent.KEY.get(player);
        var route = fox ? EvolutionRegistry.INSTANCE.getRoute("snow_fox") : null;
        Set<Identifier> wanted = new HashSet<>();
        if (route != null) for (var node : route.nodes) {
            if (!node.grantsPowers.isEmpty() && EvolutionTalents.isActive(player, node.id))
                for (String id : node.grantsPowers) wanted.add(new Identifier(id));
        }
        boolean changed = false;
        for (var existing : Set.copyOf(holder.getPowersFromSource(POWERS))) {
            if (!wanted.remove(existing.getIdentifier())) {
                holder.removePower(existing, POWERS);
                changed = true;
            }
        }
        for (var id : wanted) if (PowerTypeRegistry.contains(id))
            changed |= holder.addPower(PowerTypeRegistry.get(id), POWERS);
        if (changed) PowerHolderComponent.KEY.sync(player);
    }

    @Override public void writeToNbt(NbtCompound nbt) { }
    @Override public void readFromNbt(NbtCompound nbt) { checkPowers = true; }
    @Override public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeEnumConstant(phase); buf.writeLong(since); buf.writeFloat(displayMass);
    }
    @Override public void applySyncPacket(PacketByteBuf buf) {
        phase = buf.readEnumConstant(Phase.class); since = buf.readLong(); displayMass = buf.readFloat();
    }
}
