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

/** Shared reaction cues. Crowd limits affect presentation only, never combat resolution. */
final class AnomalyReactionFeedback {
    private static final Map<ServerWorld, Budget> BUDGETS = new IdentityHashMap<>();

    private static final class Budget {
        long tick = Long.MIN_VALUE, window = Long.MIN_VALUE;
        int bursts, sounds;
        final Map<BlockPos, Integer> cells = new HashMap<>();
        final Set<BlockPos> soundCells = new HashSet<>();
        final Set<UUID> notified = new HashSet<>();

        void advance(long now) {
            if (tick != now) { tick = now; bursts = 0; cells.clear(); }
            if (window != now / 10) {
                window = now / 10; sounds = 0; soundCells.clear(); notified.clear();
            }
        }

        boolean visual(BlockPos cell) {
            if (bursts >= 32 || cells.getOrDefault(cell, 0) >= 2) return false;
            bursts++;
            cells.merge(cell, 1, Integer::sum);
            return true;
        }
    }

    private AnomalyReactionFeedback() {}

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
            world.playSound(null, target.getX(), target.getY(), target.getZ(), sound(pair), SoundCategory.PLAYERS, .65f, 1.2f);
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

    private static SoundEvent sound(String pair) {
        if (pair.equals("fire+ice")) return SoundEvents.BLOCK_FIRE_EXTINGUISH;
        if (pair.contains("void") || pair.contains("space")) return SoundEvents.ENTITY_ENDERMAN_TELEPORT;
        if (pair.contains("ice")) return SoundEvents.BLOCK_GLASS_BREAK;
        return SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
    }

    static void clear(ServerWorld world) { BUDGETS.remove(world); }
    static void clearAll() { BUDGETS.clear(); }
}
