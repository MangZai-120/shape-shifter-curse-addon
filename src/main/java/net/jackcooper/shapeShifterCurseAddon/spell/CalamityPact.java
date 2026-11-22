package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.minion.IMinion;

public final class CalamityPact {
    private record Pact(ServerWorld world, long endsAt, float fraction) {}
    public static final RegistryKey<DamageType> TRANSFER =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("ssc_addon", "calamity_pact"));
    private static final String FALLEN_VEX_TAG = "ssc_fallen_allay_vex";
    private static final Map<ServerWorld, Set<LivingEntity>> candidates = new HashMap<>();
    private static final Map<ServerPlayerEntity, Pact> active = new HashMap<>();

    private CalamityPact() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity living && (living instanceof LunarSpiritEntity
                    || living instanceof IMinion<?>
                    || living instanceof WolfEntity && living.getCommandTags().contains(NeoEffects.SUMMON_TAG)
                    || living instanceof VexEntity && living.getCommandTags().contains(FALLEN_VEX_TAG))) {
                candidates.computeIfAbsent(world, key -> new HashSet<>()).add(living);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register(CalamityPact::onGone);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onGone(entity, (ServerWorld) entity.getWorld()));
        ServerTickEvents.END_SERVER_TICK.register(server -> active.entrySet().removeIf(entry ->
                !valid(entry.getKey(), entry.getValue()) || !hasSummon(entry.getKey())));
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            candidates.remove(world);
            active.values().removeIf(pact -> pact.world == world);
        });
    }

    private static void onGone(Entity entity, ServerWorld world) {
        if (entity instanceof ServerPlayerEntity player) active.remove(player);
        var loaded = candidates.get(world);
        if (loaded != null && loaded.remove(entity)) {
            active.entrySet().removeIf(entry -> entry.getValue().world == world && !hasSummon(entry.getKey()));
        }
    }

    private static boolean ownedBy(LivingEntity entity, UUID owner) {
        if (entity instanceof LunarSpiritEntity spirit) return owner.equals(spirit.getOwnerUuid());
        if (entity instanceof IMinion<?> minion) return owner.equals(minion.getMinionOwnerUUID());
        if (entity instanceof WolfEntity wolf) return wolf.getCommandTags().contains(NeoEffects.SUMMON_TAG)
                && owner.equals(wolf.getOwnerUuid());
        return entity.getCommandTags().contains(FALLEN_VEX_TAG)
                && entity.getCommandTags().contains("owner:" + owner);
    }

    private static Stream<LivingEntity> summons(ServerPlayerEntity caster) {
        UUID owner = caster.getUuid();
        return candidates.getOrDefault(caster.getServerWorld(), Set.of()).stream()
                .filter(entity -> entity.isAlive() && !entity.isRemoved() && !entity.isInvulnerable() && ownedBy(entity, owner));
    }

    public static boolean hasSummon(ServerPlayerEntity caster) {
        return summons(caster).findAny().isPresent();
    }

    public static void start(ServerPlayerEntity caster, int ticks, float fraction) {
        if (!hasSummon(caster)) return;
        ServerWorld world = caster.getServerWorld();
        active.put(caster, new Pact(world, world.getTime() + ticks, fraction));
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, 0.95F);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9F, 0.7F);
        world.spawnParticles(ParticleTypes.ENCHANT, caster.getX(), caster.getBodyY(0.6), caster.getZ(), 20, 0.4, 0.35, 0.4, 0.1);
    }

    private static boolean valid(ServerPlayerEntity player, Pact pact) {
        return player.isAlive() && !player.isRemoved() && player.getWorld() == pact.world && pact.world.getTime() < pact.endsAt;
    }

    public static float distributeDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        Pact pact = active.get(player);
        if (pact == null || !valid(player, pact) || amount <= 0 || player.isInvulnerableTo(source)
                || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        // Death callbacks can remove the pact during this hit. Its original recipients still get their shares.
        var recipients = summons(player).toList();
        if (recipients.isEmpty()) {
            active.remove(player);
            return amount;
        }
        float share = amount * pact.fraction / recipients.size();
        DamageSource transferred = pact.world.getDamageSources().create(TRANSFER);
        int paidShares = 0;
        for (var summon : recipients) {
            if (summon.damage(transferred, share)) {
                paidShares++;
                pact.world.spawnParticles(ParticleTypes.ENCHANT, summon.getX(), summon.getBodyY(0.6), summon.getZ(),
                        5, 0.22, 0.2, 0.22, 0.04);
            }
        }
        if (paidShares == 0) return amount;
        pact.world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT,
                SoundCategory.PLAYERS, 0.75F, 0.8F);
        // The original hit continues through vanilla armor, absorption and hurt feedback.
        return amount * (1.0F - pact.fraction * paidShares / recipients.size());
    }
}
