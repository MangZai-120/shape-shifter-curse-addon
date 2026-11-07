package net.jackcooper.shapeShifterCurseAddon.anomaly;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals;
import net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Elemental trigger and reaction cues. Crowd limits affect presentation only, never combat resolution. */
final class AnomalyReactionFeedback {
    private static final Map<ServerWorld, Budget> BUDGETS = new IdentityHashMap<>();
    private record SoundCue(SoundEvent sound, float volume, float pitch) {
        void play(ServerWorld world, LivingEntity target) {
            world.playSound(null, target.getX(), target.getY(), target.getZ(), sound,
                    SoundCategory.PLAYERS, volume, pitch);
        }
    }
    private static final SoundCue DEFAULT_BASE = new SoundCue(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, .8f, 1.25f);
    private static final SoundCue DEFAULT_REACTION = new SoundCue(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, .65f, 1.2f);
    private static final Map<String, SoundCue> BASE_SOUNDS = Map.of(
            // Match FireBoltSpell's launch event, volume and pitch.
            "fire", new SoundCue(SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 1.2f),
            "ice", new SoundCue(SoundEvents.BLOCK_GLASS_BREAK, .8f, .9f),
            "lunar", new SoundCue(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, .8f, 1.2f),
            "curse", new SoundCue(SoundEvents.ENTITY_EVOKER_CAST_SPELL, .7f, .85f),
            "summon", new SoundCue(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, .65f, 1.3f),
            "void", new SoundCue(SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, .8f, .8f),
            "space", new SoundCue(SoundEvents.ENTITY_ENDERMAN_TELEPORT, .8f, 1.1f));
    // Each unordered pair has a one-shot cue distinct from either constituent's base cue.
    private static final Map<String, SoundCue> REACTION_SOUNDS = Map.ofEntries(
            Map.entry("fire+ice", new SoundCue(SoundEvents.BLOCK_FIRE_EXTINGUISH, .85f, 1f)), // 烈霜
            Map.entry("fire+lunar", new SoundCue(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, .7f, 1.05f)), // 灼月
            Map.entry("curse+fire", new SoundCue(SoundEvents.ENTITY_BLAZE_BURN, .75f, .85f)), // 焚咒
            Map.entry("fire+summon", new SoundCue(SoundEvents.ENTITY_VEX_CHARGE, .65f, 1.15f)), // 炎灵
            Map.entry("fire+void", new SoundCue(SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), .55f, 1.25f)), // 湮炎
            Map.entry("fire+space", new SoundCue(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, .7f, .9f)), // 炎域
            Map.entry("ice+lunar", new SoundCue(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, .8f, 1.25f)), // 寒月
            Map.entry("curse+ice", new SoundCue(SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, .6f, 1.2f)), // 寒咒
            Map.entry("ice+summon", new SoundCue(SoundEvents.ITEM_TRIDENT_THROW, .75f, 1.25f)), // 霜猎
            Map.entry("ice+void", new SoundCue(SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, .85f, .75f)), // 碎霜
            Map.entry("ice+space", new SoundCue(SoundEvents.BLOCK_CHAIN_PLACE, .85f, .8f)), // 冰锁
            Map.entry("curse+lunar", new SoundCue(SoundEvents.BLOCK_BEACON_DEACTIVATE, .65f, .85f)), // 黯月
            Map.entry("lunar+summon", new SoundCue(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, .8f, 1.1f)), // 灵月
            Map.entry("lunar+void", new SoundCue(SoundEvents.BLOCK_SCULK_CHARGE, .8f, .7f)), // 蚀月
            Map.entry("lunar+space", new SoundCue(SoundEvents.BLOCK_CONDUIT_ACTIVATE, .65f, 1.25f)), // 月域
            Map.entry("curse+summon", new SoundCue(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, .6f, 1.2f)), // 厄灵
            Map.entry("curse+void", new SoundCue(SoundEvents.ENTITY_WITHER_HURT, .5f, 1.25f)), // 凋零
            Map.entry("curse+space", new SoundCue(SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, .85f, .75f)), // 禁界
            Map.entry("summon+void", new SoundCue(SoundEvents.PARTICLE_SOUL_ESCAPE, .9f, .75f)), // 噬灵
            Map.entry("space+summon", new SoundCue(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, .75f, 1.15f)), // 灵网
            Map.entry("space+void", new SoundCue(SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, .85f, .75f))); // 裂隙

    private static final class Budget {
        long tick = Long.MIN_VALUE, window = Long.MIN_VALUE;
        int bursts, sounds, baseBursts, baseSounds;
        final Map<BlockPos, Integer> cells = new HashMap<>();
        final Map<BlockPos, Integer> baseCells = new HashMap<>();
        final Set<BlockPos> soundCells = new HashSet<>();
        final Set<BlockPos> baseSoundCells = new HashSet<>();
        final Set<UUID> notified = new HashSet<>();

        void advance(long now) {
            if (tick != now) {
                tick = now; bursts = 0; baseBursts = 0; cells.clear(); baseCells.clear();
            }
            if (window != now / 10) {
                window = now / 10; sounds = 0; baseSounds = 0;
                soundCells.clear(); baseSoundCells.clear(); notified.clear();
            }
        }

        boolean visual(BlockPos cell) {
            if (bursts >= 32 || cells.getOrDefault(cell, 0) >= 2) return false;
            bursts++;
            cells.merge(cell, 1, Integer::sum);
            return true;
        }

        boolean baseVisual(BlockPos cell) {
            if (baseBursts >= 16 || baseCells.getOrDefault(cell, 0) >= 2) return false;
            baseBursts++;
            baseCells.merge(cell, 1, Integer::sum);
            return true;
        }
    }

    private AnomalyReactionFeedback() {}

    static void showBase(String element, LivingEntity target) {
        ServerWorld world = (ServerWorld) target.getWorld();
        Budget budget = budget(world);
        BlockPos cell = cell(target.getPos());
        if (budget.baseVisual(cell)) {
            Vec3d center = target.getPos().add(0, target.getHeight() * .5, 0);
            double spread = Math.max(.3, Math.min(1.2, target.getWidth() * .6));
            ParticleEffect particle = particle(element);
            ParticleUtils.spawnParticlesNearby(world, particle, center, 24, spread, .35, spread, .07);
            ring(target, target.getPos().add(0, .12, 0), particle,
                    Math.max(.7, Math.min(1.8, target.getWidth() * .5 + .4)));
        }
        // Separate budgets let an immediate reaction play even when its first element just triggered.
        if (budget.baseSounds < 4 && budget.baseSoundCells.add(cell)) {
            budget.baseSounds++;
            BASE_SOUNDS.getOrDefault(element, DEFAULT_BASE).play(world, target);
        }
    }

    static void show(String first, String second, LivingEntity target, SpellAnomalyContext context) {
        ServerWorld world = (ServerWorld) target.getWorld();
        Budget budget = budget(world);
        BlockPos cell = cell(target.getPos());
        String pair = AnomalyEffects.pair(first, second);
        if (budget.visual(cell)) {
            Vec3d center = target.getPos().add(0, target.getHeight() * .5, 0);
            double spread = Math.max(.3, Math.min(1.2, target.getWidth() * .6));
            ParticleUtils.spawnParticlesNearby(world, particle(first), center, 16, spread, .35, spread, .07);
            ParticleUtils.spawnParticlesNearby(world, particle(second), center, 16, spread, .35, spread, .07);
            double radius = switch (pair) {
                case "fire+ice" -> 5;
                case "curse+fire", "fire+space", "lunar+space", "curse+space", "space+summon", "space+void" -> 3;
                default -> Math.max(.7, Math.min(1.8, target.getWidth() * .5 + .4));
            };
            ring(target, target.getPos().add(0, .12, 0), particle(first), radius);
            ring(target, target.getPos().add(0, .18, 0), particle(second), radius * .92);
        }
        if (budget.sounds < 4 && budget.soundCells.add(cell)) {
            budget.sounds++;
            REACTION_SOUNDS.getOrDefault(pair, DEFAULT_REACTION).play(world, target);
        }
        Text cue = Text.translatable("message.ssc_addon.anomaly.reaction",
                Text.translatable("anomaly.ssc_addon.reaction." + pair.replace('+', '_')).formatted(Formatting.GOLD),
                Text.translatable("anomaly.ssc_addon.element." + first),
                Text.translatable("anomaly.ssc_addon.element." + second));
        if (context != null) {
            ServerPlayerEntity caster = world.getServer().getPlayerManager().getPlayer(context.casterUUID());
            if (caster != null && caster.getWorld() == world) notify(caster, cue, budget);
        }
        if (target instanceof ServerPlayerEntity player) notify(player, cue, budget);
    }

    static void fireZonePulse(LivingEntity anchor, Vec3d center) {
        ServerWorld world = (ServerWorld) anchor.getWorld();
        if (budget(world).visual(cell(center))) ring(anchor, center.add(0, .12, 0), ParticleTypes.FLAME, 3);
    }

    private static void ring(LivingEntity anchor, Vec3d center, ParticleEffect particle, double radius) {
        // One recipe packet per viewer; keep the fixed field center when its original target moves.
        ParametricVisuals.circle(anchor, particle, true, 1, .015, new Vec3d(.04, .04, .04),
                center.subtract(anchor.getPos()), radius, 32, viewer -> viewer.squaredDistanceTo(center) <= 4096, null);
    }

    private static void notify(ServerPlayerEntity player, Text cue, Budget budget) {
        if (budget.notified.size() < 512 && budget.notified.add(player.getUuid())) player.sendMessage(cue, true);
    }

    private static Budget budget(ServerWorld world) {
        Budget budget = BUDGETS.computeIfAbsent(world, ignored -> new Budget());
        budget.advance(world.getTime());
        return budget;
    }

    private static BlockPos cell(Vec3d pos) {
        return BlockPos.ofFloored(Math.floor(pos.x / 4), Math.floor(pos.y / 4), Math.floor(pos.z / 4));
    }

    private static ParticleEffect particle(String element) {
        return switch (element) {
            case "fire" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "lunar" -> ParticleTypes.END_ROD;
            case "curse" -> ParticleTypes.WITCH;
            case "summon" -> ParticleTypes.HAPPY_VILLAGER;
            case "void" -> ParticleTypes.REVERSE_PORTAL;
            case "space" -> ParticleTypes.PORTAL;
            default -> ParticleTypes.ENCHANT;
        };
    }

    static void clear(ServerWorld world) { BUDGETS.remove(world); }
    static void clearAll() { BUDGETS.clear(); }
}
