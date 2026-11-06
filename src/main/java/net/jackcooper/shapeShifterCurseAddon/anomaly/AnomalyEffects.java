package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.effect.AnomalyMarkerEffect;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Registered elemental effects, local reactions and bounded summon-hit budgets. */
public final class AnomalyEffects {
    public static final StatusEffect LUNAR_ECLIPSE = new AnomalyMarkerEffect(0xDBD7A7);
    public static final StatusEffect CURSE_WEAKNESS = new AnomalyMarkerEffect(0x8B56AF);
    public static final StatusEffect SUMMON_MARK = new AnomalyMarkerEffect(0x79CBA7);
    public static final StatusEffect VOID_EROSION = new AnomalyMarkerEffect(0x6961A5);
    public static final StatusEffect SPACE_BIND = new AnomalyMarkerEffect(0xAFDCEC);
    public static final StatusEffect FROST_SLOW = new AnomalyMarkerEffect(0x72BDCF);
    public static final StatusEffect NO_SPRINT = new AnomalyMarkerEffect(0xAFDCEC);
    public static final StatusEffect FLAME_SPIRIT = new AnomalyMarkerEffect(0xE5A25B);
    public static final StatusEffect FROST_HUNT = new AnomalyMarkerEffect(0x8FCEE5);
    public static final StatusEffect SPIRIT_MOON = new AnomalyMarkerEffect(0xB4E9D5);
    public static final StatusEffect SUMMON_WEAKNESS = new AnomalyMarkerEffect(0xA48FBC);
    public static final StatusEffect FIRE_ZONE = new AnomalyMarkerEffect(0xE57F46);
    public static final RegistryKey<DamageType> FIRE_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier("ssc_addon", "anomaly_fire"));
    public static final RegistryKey<DamageType> MAGIC_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier("ssc_addon", "anomaly_magic"));

    @FunctionalInterface public interface BaseExecutor { void apply(LivingEntity target, SpellAnomalyContext context); }
    @FunctionalInterface public interface ReactionExecutor { void apply(LivingEntity target, SpellAnomalyContext context); }
    public record EffectView(String id, int ticks, double strength) {}
    public record BudgetSnapshot(int fireHits, int frostHits, double healingRemaining) {}
    public record Diagnostics(int activeTargets, int zones, int queuedProcs, long localQueries,
                              long reactionDamageCalls, long effectUpdates) {}

    private static final UUID SLOW_MODIFIER = UUID.fromString("362902c5-ebc9-4efe-8096-31bb6f2e3573");
    private static final Map<String, StatusEffect> EFFECTS = new LinkedHashMap<>();
    private static final Map<String, BaseExecutor> BASES = new LinkedHashMap<>();
    private static final Map<String, ReactionExecutor> REACTIONS = new LinkedHashMap<>();
    private static final Map<LivingEntity, ServerWorld> ACTIVE = new IdentityHashMap<>();
    private static final Map<ZoneKey, Zone> ZONES = new LinkedHashMap<>();
    private static final ArrayDeque<Proc> PROCS = new ArrayDeque<>();
    // A primitive holder retains no entities and avoids a new ThreadLocal entry for every reaction hit.
    private static final ThreadLocal<int[]> REACTION_DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static boolean initialized;
    private static long localQueries, reactionDamageCalls, effectUpdates;

    static {
        EFFECTS.put("lunar_eclipse", LUNAR_ECLIPSE);
        EFFECTS.put("curse_weakness", CURSE_WEAKNESS);
        EFFECTS.put("summon_mark", SUMMON_MARK);
        EFFECTS.put("void_erosion", VOID_EROSION);
        EFFECTS.put("space_bind", SPACE_BIND);
        EFFECTS.put("frost_slow", FROST_SLOW);
        EFFECTS.put("no_sprint", NO_SPRINT);
        EFFECTS.put("flame_spirit", FLAME_SPIRIT);
        EFFECTS.put("frost_hunt", FROST_HUNT);
        EFFECTS.put("spirit_moon", SPIRIT_MOON);
        EFFECTS.put("summon_weakness", SUMMON_WEAKNESS);
        EFFECTS.put("fire_zone", FIRE_ZONE);
        registerBase("fire", (target, context) -> reactionDamage(target, context, 8, true));
        registerBase("ice", (target, context) -> FrozenControl.freeze(target, FrozenControl.DEFAULT_DURATION_TICKS));
        registerBase("lunar", (target, context) -> apply(target, LUNAR_ECLIPSE, 120, 0));
        registerBase("curse", (target, context) -> apply(target, CURSE_WEAKNESS, 120, 0));
        registerBase("summon", (target, context) -> apply(target, SUMMON_MARK, 160, 0));
        registerBase("void", (target, context) -> apply(target, VOID_EROSION, 120, 0));
        registerBase("space", (target, context) -> apply(target, SPACE_BIND, 100, 0));

        registerReaction("fire", "ice", (target, context) -> area(target, context, 5, other -> {
            reactionDamage(other, context, 8, false);
            if (other.isAlive()) apply(other, FROST_SLOW, 100, 0);
        }));
        registerReaction("fire", "lunar", (target, context) -> apply(target, LUNAR_ECLIPSE, 120, 1));
        registerReaction("fire", "curse", (target, context) -> area(target, context, 3,
                other -> apply(other, CURSE_WEAKNESS, 100, 0)));
        registerReaction("fire", "summon", (target, context) -> budget(target, FLAME_SPIRIT, context));
        registerReaction("fire", "void", (target, context) -> reactionDamage(target, context, 4, false));
        registerReaction("fire", "space", AnomalyEffects::fireZone);
        registerReaction("ice", "lunar", (target, context) -> extend(target, LUNAR_ECLIPSE, 180, 0));
        registerReaction("ice", "curse", (target, context) -> apply(target, CURSE_WEAKNESS, 120, 2));
        registerReaction("ice", "summon", (target, context) -> budget(target, FROST_HUNT, context));
        registerReaction("ice", "void", (target, context) -> reactionDamage(target, context, 6, false));
        registerReaction("ice", "space", (target, context) -> apply(target, NO_SPRINT, 100, 0));
        registerReaction("lunar", "curse", (target, context) -> {
            extend(target, LUNAR_ECLIPSE, 180, 0); extend(target, CURSE_WEAKNESS, 180, 0);
        });
        registerReaction("lunar", "summon", (target, context) -> budget(target, SPIRIT_MOON, context));
        registerReaction("lunar", "void", (target, context) -> apply(target, VOID_EROSION, 120, 1));
        registerReaction("lunar", "space", (target, context) -> area(target, context, 3,
                other -> apply(other, LUNAR_ECLIPSE, 100, 0)));
        registerReaction("curse", "summon", (target, context) -> apply(target, SUMMON_WEAKNESS, 120, 0));
        registerReaction("curse", "void", (target, context) -> {
            apply(target, CURSE_WEAKNESS, 120, 1); apply(target, VOID_EROSION, 120, 1);
        });
        registerReaction("curse", "space", (target, context) -> area(target, context, 3, other -> {
            apply(other, CURSE_WEAKNESS, 100, 0); apply(other, NO_SPRINT, 100, 0);
        }));
        registerReaction("summon", "void", (target, context) -> apply(target, SUMMON_MARK, 160, 1));
        registerReaction("summon", "space", (target, context) -> area(target, context, 3,
                other -> apply(other, SUMMON_MARK, 160, 0)));
        registerReaction("void", "space", (target, context) -> area(target, context, 3,
                other -> apply(other, VOID_EROSION, 100, 0)));
    }

    private AnomalyEffects() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        EFFECTS.forEach((id, effect) -> Registry.register(Registries.STATUS_EFFECT,
                new Identifier("ssc_addon", "anomaly_" + id), effect));
        ServerTickEvents.END_SERVER_TICK.register(server -> { drain(); tickZones(); });
        ServerLivingEntityEvents.AFTER_DEATH.register((target, source) -> clear(target));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) clear(living);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living) clear(living);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> clear(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> { clear(old); clear(player); });
        PlayerSessionCleanup.register((handler, server) -> clear(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
        ServerWorldEvents.UNLOAD.register((server, world) -> AnomalyReactionFeedback.clear(world));
    }

    public static void registerBase(String element, BaseExecutor executor) {
        Objects.requireNonNull(element); Objects.requireNonNull(executor);
        if (element.isBlank() || BASES.putIfAbsent(element, executor) != null)
            throw new IllegalArgumentException("Duplicate or empty anomaly type: " + element);
    }

    public static void registerReaction(String first, String second, ReactionExecutor executor) {
        Objects.requireNonNull(executor);
        if (first == null || second == null || first.isBlank() || second.isBlank() || first.equals(second)
                || REACTIONS.putIfAbsent(pair(first, second), executor) != null)
            throw new IllegalArgumentException("Invalid or duplicate anomaly reaction");
    }

    public static boolean hasBase(String element) { return BASES.containsKey(element); }
    public static Map<String, BaseExecutor> registeredBases() { return Collections.unmodifiableMap(BASES); }
    public static Map<String, ReactionExecutor> registeredReactions() { return Collections.unmodifiableMap(REACTIONS); }

    public static void activate(String element, LivingEntity target, SpellAnomalyContext context) {
        if (!live(target)) return;
        var executor = BASES.get(element);
        if (executor != null) executor.apply(target, context);
    }

    public static void react(String first, String second, LivingEntity target, SpellAnomalyContext context) {
        if (!live(target) || first == null || second == null || first.equals(second)) return;
        var executor = REACTIONS.get(pair(first, second));
        if (executor != null) {
            AnomalyReactionFeedback.show(first, second, target, context);
            executor.apply(target, context);
        }
    }

    static String pair(String first, String second) {
        return first.compareTo(second) < 0 ? first + "+" + second : second + "+" + first;
    }

    public static final class Cache {
        double healingSuppression, dealtReduction, summonVulnerability, spellVulnerability, summonReduction, slow;
        boolean teleportBlocked, sprintBlocked;
        Budget fire, frost, healing;
    }

    private static final class Budget {
        final SpellAnomalyContext creator;
        double remaining;
        Budget(SpellAnomalyContext creator, int remaining) { this.creator = creator; this.remaining = remaining; }
    }

    private static Cache cache(LivingEntity target) { return ((AnomalyEffectAccess) target).ssca$anomalyEffects(); }

    public static boolean hasState(LivingEntity target) {
        return target != null && (cache(target) != null || target.hasStatusEffect(SscAddon.FROZEN));
    }

    public static boolean isManaged(StatusEffect effect) {
        return effect instanceof AnomalyMarkerEffect || effect == SscAddon.FROZEN_RECOVERY;
    }

    /** Called only on relevant status changes, including hidden-effect promotion and purification. */
    public static void refresh(LivingEntity target) {
        Cache prior = cache(target);
        double healing = strength(target, LUNAR_ECLIPSE), dealt = strength(target, CURSE_WEAKNESS);
        double summon = strength(target, SUMMON_MARK), spell = strength(target, VOID_EROSION);
        boolean space = target.hasStatusEffect(SPACE_BIND), sprint = target.hasStatusEffect(NO_SPRINT);
        double slow = Math.max(target.hasStatusEffect(SscAddon.FROZEN_RECOVERY) ? .25 : 0,
                Math.max(space ? .30 : 0, target.hasStatusEffect(FROST_SLOW) ? .50 : 0));
        boolean fireBudget = target.hasStatusEffect(FLAME_SPIRIT), frostBudget = target.hasStatusEffect(FROST_HUNT);
        boolean healBudget = target.hasStatusEffect(SPIRIT_MOON), summonWeak = target.hasStatusEffect(SUMMON_WEAKNESS);
        boolean zone = target.hasStatusEffect(FIRE_ZONE);
        boolean any = healing > 0 || dealt > 0 || summon > 0 || spell > 0 || space || sprint
                || slow > 0 || fireBudget || frostBudget || healBudget || summonWeak || zone;
        Cache next = any ? prior == null ? new Cache() : prior : null;
        if (next != null) {
            next.healingSuppression = healing; next.dealtReduction = dealt;
            next.summonVulnerability = summon; next.spellVulnerability = spell;
            next.summonReduction = summonWeak ? .50 : 0;
            next.teleportBlocked = space; next.sprintBlocked = sprint; next.slow = slow;
            if (!fireBudget) next.fire = null;
            if (!frostBudget) next.frost = null;
            if (!healBudget) next.healing = null;
        }
        ((AnomalyEffectAccess) target).ssca$anomalyEffects(next);
        if (target.getWorld() instanceof ServerWorld world) {
            if (any) ACTIVE.put(target, world); else ACTIVE.remove(target);
            if (!zone && !ZONES.isEmpty()) {
                for (Zone current : ZONES.values())
                    if (current.anchor == target && now(current.world) < current.until) current.cancelled = true;
            }
            // Prior can be reused, so compare the actual modifier rather than its already-updated cache value.
            var speed = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                var modifier = speed.getModifier(SLOW_MODIFIER);
                if (slow <= 0) { if (modifier != null) speed.removeModifier(SLOW_MODIFIER); }
                else if (modifier == null || Math.abs(modifier.getValue() + slow) > 1e-9) {
                    speed.removeModifier(SLOW_MODIFIER);
                    speed.addTemporaryModifier(new EntityAttributeModifier(SLOW_MODIFIER, "SSCA anomaly slow",
                            -slow, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
            effectUpdates++;
            AnomalyManager.effectsChanged(target);
        }
        if (sprint && target.isSprinting()) target.setSprinting(false);
    }

    private static double strength(LivingEntity target, StatusEffect type) {
        StatusEffectInstance effect = target.getStatusEffect(type);
        if (effect == null) return 0;
        int amp = effect.getAmplifier();
        if (type == LUNAR_ECLIPSE) return amp >= 1 ? .75 : .50;
        if (type == CURSE_WEAKNESS) return amp >= 2 ? .35 : amp == 1 ? .30 : .20;
        if (type == SUMMON_MARK) return amp >= 1 ? .40 : .25;
        if (type == VOID_EROSION) return amp >= 1 ? .25 : .15;
        return type == SPACE_BIND ? .30 : type == FROST_SLOW ? .50 : 0;
    }

    public static float modifyHealing(LivingEntity target, float amount) {
        Cache state = cache(target);
        return state == null || amount <= 0 ? amount : (float)(amount * (1 - state.healingSuppression));
    }

    public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || target.getWorld().isClient) return amount;
        Cache received = cache(target);
        Cache dealt = source.getAttacker() instanceof LivingEntity attacker ? cache(attacker) : null;
        if (received == null && dealt == null) return amount;
        boolean summon = isSummonDamage(source);
        if (dealt != null) amount *= 1 - Math.max(dealt.dealtReduction,
                isSummoned(target) ? dealt.summonReduction : 0);
        if (received != null) {
            double vulnerability = Math.max(summon ? received.summonVulnerability : 0,
                    isSpellDamage(source) ? received.spellVulnerability : 0);
            amount *= 1 + vulnerability;
        }
        return amount;
    }

    public static boolean teleportBlocked(LivingEntity target) {
        Cache state = cache(target); return state != null && state.teleportBlocked;
    }

    public static boolean sprintBlocked(LivingEntity target) {
        Cache state = cache(target); return state != null && state.sprintBlocked;
    }

    public static boolean isReaction(DamageSource source) {
        return source.isOf(FIRE_DAMAGE) || source.isOf(MAGIC_DAMAGE);
    }

    public static boolean isSpellDamage(DamageSource source) {
        return source.isOf(SpellDamageSource.SPELL_DAMAGE) || isReaction(source)
                || source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC);
    }

    private static boolean isSummoned(Entity entity) {
        return entity instanceof SpellAnomalyHits.SummonedData summon && summon.ssca$summonAnomaly() != null;
    }

    private static SpellAnomalyContext summonContext(DamageSource source) {
        Entity direct = source.getSource();
        if (direct instanceof SpellAnomalyHits.SummonedData summon) return summon.ssca$summonAnomaly();
        if (direct instanceof SpellAnomalyHits.ProjectileData projectile) {
            var context = projectile.ssca$anomaly();
            if (context != null && context.element().equals("summon")) return context;
        }
        return source.getAttacker() instanceof SpellAnomalyHits.SummonedData summon ? summon.ssca$summonAnomaly() : null;
    }

    private static boolean isSummonDamage(DamageSource source) { return !isReaction(source) && summonContext(source) != null; }

    private record Proc(LivingEntity target, Cache state, Budget fire, Budget frost, Budget healing,
                        SpellAnomalyContext summoner, ServerWorld world) {}

    /** Reuses the existing damage wrapper; ordinary hits allocate no event and do no map lookup. */
    public static void afterDamage(LivingEntity target, DamageSource source, boolean accepted) {
        if (!accepted || target.getWorld().isClient) return;
        Cache state = cache(target);
        if (state == null || state.fire == null && state.frost == null && state.healing == null) return;
        if (isReaction(source) || REACTION_DEPTH.get()[0] > 0) return;
        SpellAnomalyContext summoner = summonContext(source);
        if (summoner == null || !(target.getWorld() instanceof ServerWorld world)) return;
        PROCS.addLast(new Proc(target, state, state.fire, state.frost, state.healing, summoner, world));
    }

    /** Original hit/refund/statistic settlement finishes before extra damage is applied. */
    public static void drain() {
        while (!PROCS.isEmpty()) {
            Proc proc = PROCS.removeFirst();
            LivingEntity target = proc.target(); Cache state = cache(target);
            if (!live(target) || target.getWorld() != proc.world() || state != proc.state()) continue;
            if (proc.fire() != null && state.fire == proc.fire() && proc.fire().remaining > 0
                    && target.hasStatusEffect(FLAME_SPIRIT)) {
                proc.fire().remaining--;
                reactionDamage(target, proc.summoner(), 2, true);
                if (proc.fire().remaining <= 0) target.removeStatusEffect(FLAME_SPIRIT);
            }
            if (live(target) && proc.frost() != null && state.frost == proc.frost() && proc.frost().remaining > 0
                    && target.hasStatusEffect(FROST_HUNT)) {
                proc.frost().remaining--;
                reactionDamage(target, proc.summoner(), 6, false);
                if (proc.frost().remaining <= 0) target.removeStatusEffect(FROST_HUNT);
            }
            if (proc.healing() != null && state.healing == proc.healing() && proc.healing().remaining > 0
                    && target.hasStatusEffect(SPIRIT_MOON)) {
                ServerPlayerEntity owner = proc.world().getServer().getPlayerManager().getPlayer(proc.summoner().casterUUID());
                if (owner != null && owner.isAlive() && owner.getWorld() == proc.world()) {
                    float before = owner.getHealth(); owner.heal((float)Math.min(1, proc.healing().remaining));
                    proc.healing().remaining = Math.max(0, proc.healing().remaining - Math.max(0, owner.getHealth() - before));
                    if (proc.healing().remaining <= 0) target.removeStatusEffect(SPIRIT_MOON);
                }
            }
        }
    }

    private static void budget(LivingEntity target, StatusEffect marker, SpellAnomalyContext context) {
        apply(target, marker, 120, 0);
        if (!target.hasStatusEffect(marker)) return;
        Cache state = cache(target);
        if (marker == FLAME_SPIRIT) state.fire = new Budget(context, 3);
        else if (marker == FROST_HUNT) state.frost = new Budget(context, 1);
        else if (marker == SPIRIT_MOON) state.healing = new Budget(context, 4);
    }

    private static void apply(LivingEntity target, StatusEffect type, int ticks, int amplifier) {
        if (live(target)) target.addStatusEffect(new StatusEffectInstance(type, ticks, amplifier, false, false, true));
    }

    private static void extend(LivingEntity target, StatusEffect type, int ticks, int minimumAmplifier) {
        StatusEffectInstance old = target.getStatusEffect(type);
        apply(target, type, Math.max(ticks, old == null ? 0 : old.getDuration()),
                Math.max(minimumAmplifier, old == null ? 0 : old.getAmplifier()));
    }

    public static boolean reactionDamage(LivingEntity target, SpellAnomalyContext context, float amount, boolean fire) {
        if (!live(target) || amount <= 0) return false;
        ServerWorld world = (ServerWorld)target.getWorld();
        ServerPlayerEntity owner = context == null ? null : world.getServer().getPlayerManager().getPlayer(context.casterUUID());
        Entity attacker = owner != null && owner.getWorld() == world ? owner : null;
        DamageSource source = world.getDamageSources().create(fire ? FIRE_DAMAGE : MAGIC_DAMAGE, attacker, attacker);
        boolean[] accepted = {false};
        int[] depth = REACTION_DEPTH.get(); depth[0]++;
        try {
            reactionDamageCalls++;
            DomainManager.runAttachedEffect(attacker, target, () -> accepted[0] = target.damage(source, amount));
        } finally {
            depth[0]--;
        }
        return accepted[0];
    }

    private static boolean live(LivingEntity target) {
        return target != null && target.getWorld() instanceof ServerWorld && target.isAlive()
                && !target.isRemoved() && !target.isSpectator();
    }

    private static void area(LivingEntity origin, SpellAnomalyContext context, double radius, Consumer<LivingEntity> action) {
        ServerWorld world = (ServerWorld)origin.getWorld();
        Vec3d center = origin.getPos();
        localArea(world, center, context, radius, action);
    }

    private static void localArea(ServerWorld world, Vec3d center, SpellAnomalyContext context,
                                  double radius, Consumer<LivingEntity> action) {
        localQueries++;
        AnomalyManager.recordLocalQuery();
        for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class,
                new Box(center, center).expand(radius), target -> eligible(world, center, radius, context, target)))
            action.accept(other);
    }

    private static boolean eligible(ServerWorld world, Vec3d center, double radius,
                                    SpellAnomalyContext context, LivingEntity target) {
        if (!live(target) || target instanceof ArmorStandEntity || target.getPos().squaredDistanceTo(center) > radius * radius
                || DomainManager.blocksPath(world, center, target.getPos(), 0)) return false;
        if (context == null) return !(target instanceof PlayerEntity);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(context.casterUUID());
        if (owner == null || owner.getWorld() != world)
            return !(target instanceof PlayerEntity)
                    && (owner == null || !owner.isTeammate(target))
                    && !WhitelistUtils.isProtected(context.casterUUID(), world, target);
        return target != owner && !owner.isTeammate(target) && !WhitelistUtils.isProtected(owner, target)
                && (!(target instanceof PlayerEntity player) || owner.shouldDamagePlayer(player));
    }

    private record ZoneKey(UUID target, UUID owner) {}
    private record ZoneCell(ServerWorld world, int x, int y, int z) {
        static ZoneCell at(ServerWorld world, Vec3d center) {
            return new ZoneCell(world, (int)Math.floor(center.x / 6),
                    (int)Math.floor(center.y / 6), (int)Math.floor(center.z / 6));
        }
        Box bounds() { return new Box(x * 6.0 - 3, y * 6.0 - 3, z * 6.0 - 3,
                (x + 1) * 6.0 + 3, (y + 1) * 6.0 + 3, (z + 1) * 6.0 + 3); }
    }
    private static final class Zone {
        final LivingEntity anchor; final ServerWorld world; final Vec3d center; final SpellAnomalyContext context;
        final ZoneCell cell;
        final long until; long next; boolean cancelled;
        Zone(LivingEntity target, SpellAnomalyContext context) {
            anchor = target; world = (ServerWorld)target.getWorld(); center = target.getPos(); this.context = context;
            cell = ZoneCell.at(world, center);
            next = now(world) + 20; until = now(world) + 100;
        }
    }

    private static void fireZone(LivingEntity target, SpellAnomalyContext context) {
        if (context == null) return;
        apply(target, FIRE_ZONE, 100, 0);
        if (!target.hasStatusEffect(FIRE_ZONE)) return;
        ZONES.put(new ZoneKey(target.getUuid(), context.casterUUID()), new Zone(target, context));
    }

    private static void tickZones() {
        if (ZONES.isEmpty()) return;
        Map<ZoneCell, List<LivingEntity>> candidates = null;
        var iterator = ZONES.values().iterator();
        while (iterator.hasNext()) {
            Zone zone = iterator.next(); long now = now(zone.world);
            if (zone.cancelled || !live(zone.anchor) || zone.anchor.getWorld() != zone.world
                    || now > zone.until || !zone.anchor.hasStatusEffect(FIRE_ZONE) && now < zone.until) {
                iterator.remove(); continue;
            }
            if (now >= zone.next && now <= zone.until) {
                zone.next += 20;
                AnomalyReactionFeedback.fireZonePulse(zone.anchor, zone.center);
                if (candidates == null) candidates = new HashMap<>();
                List<LivingEntity> nearby = candidates.get(zone.cell);
                if (nearby == null) {
                    localQueries++; AnomalyManager.recordLocalQuery();
                    nearby = zone.world.getEntitiesByClass(LivingEntity.class, zone.cell.bounds(), LivingEntity::isAlive);
                    candidates.put(zone.cell, nearby);
                }
                for (LivingEntity other : nearby) {
                    if (!eligible(zone.world, zone.center, 3, zone.context, other)) continue;
                    var access = (AnomalyEffectAccess)other;
                    long last = access.ssca$lastAnomalyZoneHitTick();
                    if (last != Long.MIN_VALUE && now >= last && now - last < 20) continue;
                    if (reactionDamage(other, zone.context, 1, true)) access.ssca$lastAnomalyZoneHitTick(now);
                }
            }
            if (zone.cancelled || now >= zone.until) iterator.remove();
        }
        // Never retain queried entities across ticks or worlds.
        if (candidates != null) candidates.clear();
    }

    private static long now(ServerWorld world) { return AnomalyManager.now(world.getServer()); }

    private static void cancelZones(LivingEntity target) {
        for (Zone zone : ZONES.values()) if (zone.anchor == target) zone.cancelled = true;
    }

    public static void clear(LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld)) return;
        ((AnomalyEffectAccess)target).ssca$lastAnomalyZoneHitTick(Long.MIN_VALUE);
        if (!hasState(target)) {
            boolean loadedMarker = false;
            for (StatusEffectInstance effect : target.getStatusEffects())
                if (isManaged(effect.getEffectType())) { loadedMarker = true; break; }
            if (!loadedMarker) return;
        }
        for (StatusEffect effect : EFFECTS.values()) target.removeStatusEffect(effect);
        target.removeStatusEffect(SscAddon.FROZEN_RECOVERY);
        FrozenControl.clear(target);
        ((AnomalyEffectAccess)target).ssca$anomalyEffects(null);
        var speed = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_MODIFIER);
        ACTIVE.remove(target); cancelZones(target);
    }

    public static void clearAll() {
        for (LivingEntity target : ACTIVE.keySet().toArray(LivingEntity[]::new)) clear(target);
        ACTIVE.clear(); ZONES.clear(); PROCS.clear(); REACTION_DEPTH.remove();
        AnomalyReactionFeedback.clearAll();
    }

    public static List<EffectView> effectSnapshot(LivingEntity target) {
        if (cache(target) == null && !FrozenControl.blocked(target)) return List.of();
        var views = new ArrayList<EffectView>();
        EFFECTS.forEach((id, type) -> {
            var effect = target.getStatusEffect(type);
            if (effect != null) views.add(new EffectView(id, effect.getDuration(), strength(target, type)));
        });
        var recovery = target.getStatusEffect(SscAddon.FROZEN_RECOVERY);
        if (recovery != null) views.add(new EffectView("frozen_recovery", recovery.getDuration(), .25));
        var frozen = target.getStatusEffect(SscAddon.FROZEN);
        if (frozen != null) views.add(new EffectView("ice", frozen.getDuration(), 1));
        return List.copyOf(views);
    }

    public static BudgetSnapshot budgetSnapshot(LivingEntity target) {
        Cache state = cache(target);
        return state == null ? new BudgetSnapshot(0, 0, 0) : new BudgetSnapshot(
                state.fire == null ? 0 : (int)state.fire.remaining, state.frost == null ? 0 : (int)state.frost.remaining,
                state.healing == null ? 0 : state.healing.remaining);
    }

    public static Diagnostics diagnostics() {
        int zones = 0; for (Zone zone : ZONES.values()) if (!zone.cancelled) zones++;
        return new Diagnostics(ACTIVE.size(), zones, PROCS.size(), localQueries, reactionDamageCalls, effectUpdates);
    }
}
