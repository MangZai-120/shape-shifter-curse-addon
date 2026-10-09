package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Server owns both targeting and danger flags. Only successful channel completion deals damage. */
public final class DeathFinaleManager {
    public static final Identifier SNAPSHOT = new Identifier("ssc_addon", "death_finale_state");
    private static final Map<UUID, Sequence> ACTIVE = new LinkedHashMap<>();
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static final Map<UUID, Set<UUID>> DANGER = new HashMap<>();

    private static final class Sequence {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final ServerWorld world;
        final DeathFinaleRules.ChargePosition position;
        final int duration;
        final double radius = DeathFinaleRules.RADIUS;
        int elapsed;
        long releasedAt = -1;
        Sequence(ServerPlayerEntity player, int duration) {
            owner = player.getUuid(); world = player.getServerWorld();
            position = new DeathFinaleRules.ChargePosition(player.getPos());
            this.duration = duration;
        }
        Vec3d center() { return position.center(); }
    }

    private DeathFinaleManager() {}
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DeathFinaleManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ACTIVE.remove(handler.player.getUuid());
            VIEWERS.remove(handler.player.getUuid()); DANGER.remove(handler.player.getUuid());
            sync(server, true);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear(); VIEWERS.clear(); DANGER.clear();
        });
    }

    public static void begin(ServerPlayerEntity caster, int duration) {
        ACTIVE.put(caster.getUuid(), new Sequence(caster, duration));
        caster.getWorld().playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.PLAYERS, 1.1f, 0.55f);
        sync(caster.getServer(), true);
    }

    public static void advance(ServerPlayerEntity caster, int ticks) {
        Sequence s = ACTIVE.get(caster.getUuid());
        if (s == null || s.releasedAt >= 0 || caster.getWorld() != s.world
                || !s.position.follow(caster.getPos())) return;
        s.elapsed = ticks;
        // Movement and jumping use the shared channel lock; gravity/knockback remain vanilla.
        int remaining = s.duration - ticks;
        if (remaining > 0 && ticks % (remaining <= 60 ? 10 : 20) == 0) {
            s.world.playSound(null, s.center().x, s.center().y, s.center().z, SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.PLAYERS, 1.3f, 0.65f + 0.45f * ticks / s.duration);
            if (ticks % 20 == 0) s.world.playSound(null, s.center().x, s.center().y, s.center().z,
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.65f, 0.55f);
        }
    }

    public static boolean canContinue(ServerPlayerEntity caster) {
        Sequence s = ACTIVE.get(caster.getUuid());
        return s != null && s.releasedAt < 0 && caster.getWorld() == s.world
                && s.position.canContinue(caster.getPos());
    }

    public static void cancel(ServerPlayerEntity caster) {
        if (ACTIVE.remove(caster.getUuid()) != null) sync(caster.getServer(), true);
    }

    private static boolean eligible(Sequence s, ServerPlayerEntity owner, LivingEntity target) {
        if (owner == null || target.getWorld() != s.world || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || target instanceof ArmorStandEntity
                || !DeathFinaleRules.contains(target.squaredDistanceTo(s.center()), s.radius)
                || WhitelistUtils.isProtected(owner, target)
                || DomainManager.blocksPath(s.world, s.center(), target.getPos(), 0)) return false;
        if (target instanceof ServerPlayerEntity player && !owner.shouldDamagePlayer(player)) return false;
        return !target.isInvulnerableTo(SpellDamageSource.of(s.world.getDamageSources(), owner));
    }

    public static void release(ServerPlayerEntity caster, float power, UUID refundId, int exp) {
        Sequence s = ACTIVE.get(caster.getUuid());
        if (s == null || s.releasedAt >= 0 || s.elapsed < s.duration || caster.getWorld() != s.world
                || !s.position.follow(caster.getPos())) return;
        s.releasedAt = s.world.getTime();
        // No falloff and no block explosion. Re-evaluate targets at completion, not at the start.
        for (LivingEntity target : s.world.getEntitiesByClass(LivingEntity.class,
                Box.of(s.center(), s.radius * 2, s.radius * 2, s.radius * 2), e -> eligible(s, caster, e))) {
            if (SpellHitHelper.projectileHit(caster, target, power, FormationElement.VOID, refundId, exp)
                    == SpellHitHelper.HitResult.HIT) exp = 0;
        }
        s.world.playSound(null, s.center().x, s.center().y, s.center().z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                SoundCategory.PLAYERS, 2f, 0.65f);
        s.world.playSound(null, s.center().x, s.center().y, s.center().z, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
                SoundCategory.PLAYERS, 1.5f, 0.6f);
        sync(caster.getServer(), true);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty() && VIEWERS.isEmpty()) return;
        boolean removed = ACTIVE.values().removeIf(s -> {
            if (s.releasedAt >= 0) return s.world.getTime() - s.releasedAt >= DeathFinaleRules.AFTERGLOW_TICKS;
            var owner = server.getPlayerManager().getPlayer(s.owner);
            return owner == null || !owner.isAlive() || owner.getWorld() != s.world || !SpellChannelManager.isCasting(owner);
        });
        sync(server, removed || server.getTicks() % 5 == 0);
    }

    private static void sync(MinecraftServer server, boolean force) {
        for (var viewer : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(viewer, SNAPSHOT)) continue;
            var visible = ACTIVE.values().stream().filter(s -> s.world == viewer.getWorld()
                    && viewer.squaredDistanceTo(s.center()) <= 64 * 64).toList();
            Set<UUID> danger = new HashSet<>();
            for (var s : visible) if (s.releasedAt < 0 && eligible(s, server.getPlayerManager().getPlayer(s.owner), viewer))
                danger.add(s.id);
            UUID id = viewer.getUuid();
            boolean wasVisible = VIEWERS.contains(id);
            if (visible.isEmpty() && !wasVisible) continue;
            if (!force && !visible.isEmpty() && wasVisible && danger.equals(DANGER.get(id))) continue;
            var out = PacketByteBufs.create();
            out.writeIdentifier(viewer.getWorld().getRegistryKey().getValue());
            out.writeVarInt(visible.size());
            for (var s : visible) {
                out.writeUuid(s.id); out.writeUuid(s.owner);
                out.writeDouble(s.center().x); out.writeDouble(s.center().y); out.writeDouble(s.center().z);
                out.writeDouble(s.radius); out.writeVarInt(s.duration);
                out.writeVarInt(s.releasedAt < 0 ? s.elapsed : s.duration + (int) (s.world.getTime() - s.releasedAt));
                out.writeBoolean(s.releasedAt >= 0); out.writeBoolean(danger.contains(s.id));
            }
            ServerPlayNetworking.send(viewer, SNAPSHOT, out);
            if (visible.isEmpty()) { VIEWERS.remove(id); DANGER.remove(id); }
            else { VIEWERS.add(id); DANGER.put(id, danger); }
        }
    }
}
