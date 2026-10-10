package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.onixary.shapeShifterCurseFabric.minion.IMinion;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import java.util.UUID;

/** Server-only PvE additions. Every offensive addition rechecks the caster's whitelist. */
public final class NpcCombatEnhancements {
    private static final BalanceReader BAL = new BalanceReader("abilities.npc_combat");
    private NpcCombatEnhancements() { }

    public static double value(String key, double fallback) { return BAL.d(key, fallback); }
    public static boolean isEnemyNpc(Entity caster, LivingEntity target) {
        return !target.getWorld().isClient && !(target instanceof PlayerEntity)
                && caster instanceof ServerPlayerEntity player && caster.getWorld() == target.getWorld()
                && !WhitelistUtils.isProtected(player, target);
    }
    public static float scale(Entity caster, LivingEntity target, float amount, String key, double fallback) {
        return isEnemyNpc(caster, target) ? amount * (float) value(key, fallback) : amount;
    }
    public static int duration(Entity caster, LivingEntity target, int ticks, String key, double fallback) {
        return isEnemyNpc(caster, target) ? Math.max(1, (int) Math.round(ticks * value(key, fallback))) : ticks;
    }
    public static float fixedDamage(Entity caster, Entity target, float amount) {
        return target instanceof LivingEntity living
                ? scale(caster, living, amount, "mancianima_fixed_mul", 2.0) : amount;
    }
    public static float primaryCap(Entity caster, LivingEntity target, float original) {
        return isEnemyNpc(caster, target) ? (float) value("mancianima_primary_cap", 40.0) : original;
    }
    public static float novaMaxDamage() { return (float) value("nova_explosion", 100.0); }

    public static void applyPurification(Entity caster, LivingEntity target) {
        if (!isEnemyNpc(caster, target) || !FormUtils.isAllaySP((LivingEntity) caster)) return;
        int ticks = BAL.i("allay_purify_duration", 300);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), caster);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 0), caster);
    }
    public static void applyFallenScream(ServerPlayerEntity caster, LivingEntity target, int ticks) {
        if (WhitelistUtils.isProtected(caster, target)) return;
        int actualTicks = duration(caster, target, ticks, "fallen_glow_duration_mul", 2.0);
        StatusEffectInstance glow = new StatusEffectInstance(StatusEffects.GLOWING, actualTicks, 0);
        boolean applied = target.addStatusEffect(glow, caster);
        if (isEnemyNpc(caster, target) && markApplies(caster, target, glow, applied)) {
            state(target).markFallen(caster.getUuid(), target.getWorld().getTime(), actualTicks);
        }
    }
    public static void applyWildCatStun(Entity caster, LivingEntity target, int ticks) {
        if (caster instanceof ServerPlayerEntity player && WhitelistUtils.isProtected(player, target)) return;
        boolean enhanced = isEnemyNpc(caster, target) && FormUtils.isWildCatSP((LivingEntity) caster);
        int actualTicks = enhanced ? duration(caster, target, ticks, "wild_cat_stun_duration_mul", 2.0) : ticks;
        StatusEffectInstance stun = new StatusEffectInstance(SscAddon.STUN, actualTicks, 0, false, false, true);
        boolean applied = target.addStatusEffect(stun, caster);
        if (enhanced && markApplies(caster, target, stun, applied)) {
            state(target).markWildCat(caster.getUuid(), target.getWorld().getTime(), actualTicks);
        }
    }

    private static boolean markApplies(Entity caster, LivingEntity target, StatusEffectInstance effect, boolean applied) {
        // Vanilla returns false when an equal/longer effect already exists; the cast still marks that NPC.
        return applied || target.hasStatusEffect(effect.getEffectType()) && target.canHaveStatusEffect(effect)
                && !DomainManager.blocksEffect(target, caster);
    }
    public static StatusEffectInstance enhanceFruitDebuff(LivingEntity target, StatusEffectInstance effect, Entity source) {
        if (!isEnemyNpc(source, target) || !FormUtils.isForm((LivingEntity) source, FormIdentifiers.BAT_PARASITIC_FRUIT)
                || effect.getEffectType().getCategory() != StatusEffectCategory.HARMFUL) return effect;
        return new StatusEffectInstance(effect.getEffectType(), effect.getDuration(),
                effect.getAmplifier() + BAL.i("fruit_debuff_level_bonus", 1), effect.isAmbient(),
                effect.shouldShowParticles(), effect.shouldShowIcon(), null, effect.getFactorCalculationData());
    }

    public static NpcCombatState state(LivingEntity target) {
        return ((NpcCombatStateAccess) target).ssca$getNpcCombatState();
    }
    /** Pets, SSC minions and tagged summons all resolve to the actual online owner. */
    public static ServerPlayerEntity owner(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) return player;
        if (entity == null || entity.getServer() == null) return null;
        UUID uuid = entity instanceof TameableEntity tame ? tame.getOwnerUuid()
                : entity instanceof IMinion minion ? minion.getMinionOwnerUUID() : null;
        if (uuid == null) {
            for (String tag : entity.getCommandTags()) {
                String raw = tag.startsWith("ssc_owner:") ? tag.substring(10)
                        : tag.startsWith("owner:") ? tag.substring(6) : null;
                if (raw == null) continue;
                try { uuid = UUID.fromString(raw); break; }
                catch (IllegalArgumentException ignored) { }
            }
        }
        return uuid == null ? null : entity.getServer().getPlayerManager().getPlayer(uuid);
    }

    private static boolean isFallenSummon(Entity attacker, ServerPlayerEntity owner) {
        if (attacker instanceof VexEntity && attacker.getCommandTags().contains("ssc_fallen_allay_vex")) return true;
        if (!FormUtils.isForm(owner, FormIdentifiers.FALLEN_ALLAY_SP)) return false;
        return attacker instanceof IMinion || !(attacker instanceof TameableEntity)
                && attacker.getCommandTags().stream().anyMatch(tag -> tag.startsWith("owner:") || tag.startsWith("ssc_owner:"));
    }

    public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
        if (target.getWorld().isClient || amount <= 0 || source == null) return amount;
        Entity attacker = source.getAttacker();
        ServerPlayerEntity dealer = owner(attacker);
        boolean enemyNpc = isEnemyNpc(dealer, target);
        if (enemyNpc) {
            if (attacker == dealer) {
                if (FormUtils.isAxolotlFluorescent(dealer)) amount *= (float) value("fluorescent_damage", 1.5);
                if (FormUtils.isAnyForm(dealer, FormIdentifiers.SNOW_FOX_SP, FormIdentifiers.SNOW_FOX_FROSTSPINE))
                    amount *= (float) value("snow_fox_damage", 1.5);
                if (FormUtils.isForm(dealer, FormIdentifiers.FALLEN_ALLAY_SP)) {
                    amount *= (float) value("fallen_damage", 1.5);
                    if (target.hasStatusEffect(StatusEffects.GLOWING)
                            && state(target).hasFallen(dealer.getUuid(), target.getWorld().getTime()))
                        amount *= (float) value("fallen_mark_dealt", 1.25);
                }
                if (FormUtils.isAnubisWolfSP(dealer) && AnubisWolfSpDeathDomain.containsForNpcCombat(dealer, target))
                    amount *= (float) value("anubis_domain_dealt", 1.2);
                if (FormUtils.isForm(dealer, FormIdentifiers.SPIDER_MOON_WEAVER)
                        && target.hasStatusEffect(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT))
                    amount *= (float) value("moon_cocoon_dealt", 1.25);
            } else if (isFallenSummon(attacker, dealer)) {
                amount *= (float) value("fallen_damage", 1.5);
            }
        }
        // Wild-cat exposure doubles all sources, including environment, once (multiple cats do not stack).
        if (!(target instanceof PlayerEntity) && target.hasStatusEffect(SscAddon.STUN)
                && (dealer == null || !WhitelistUtils.isProtected(dealer, target))) {
            for (UUID casterId : state(target).wildCats(target.getWorld().getTime())) {
                if (!WhitelistUtils.isProtected(casterId, (ServerWorld) target.getWorld(), target)) {
                    amount *= (float) value("wild_cat_stun_taken", 2.0);
                    break;
                }
            }
        }
        if (attacker instanceof LivingEntity npc && !(npc instanceof PlayerEntity)) {
            ServerPlayerEntity defender = owner(target);
            if (defender != null && isEnemyNpc(defender, npc)) {
                if (FormUtils.isAnubisWolfSP(defender)) amount *= (float) value("anubis_taken", 0.5);
                if (target == defender) {
                    if (FormUtils.isForm(defender, FormIdentifiers.OCELOT_NOVA)) amount *= (float) value("nova_taken", 0.75);
                    if (FormUtils.isForm(defender, FormIdentifiers.FALLEN_ALLAY_SP) && npc.hasStatusEffect(StatusEffects.GLOWING)
                            && state(npc).hasFallen(defender.getUuid(), npc.getWorld().getTime()))
                        amount *= (float) value("fallen_mark_taken", 0.75);
                }
            }
        }
        return amount;
    }
}
