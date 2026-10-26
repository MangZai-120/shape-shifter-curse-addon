package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** 跳蛛共用原蜘蛛购买记录，效果接入跳杀和毒液，不引入蛛网法力。 */
public final class SpiderSalticidaePerks {
    public static final Identifier FEEDING_1 = addon("perk_spider_salticidae_feeding_1");
    public static final Identifier FEEDING_2 = addon("perk_spider_salticidae_feeding_2");
    public static final Identifier CHARGE = addon("perk_spider_salticidae_charge");
    public static final Identifier SENSE = addon("perk_spider_salticidae_sense");
    public static final Identifier SENSE_CHARGING = addon("perk_spider_salticidae_sense_charging");
    public static final Identifier RAPID = addon("perk_spider_salticidae_rapid");
    public static final Identifier STABLE = addon("perk_spider_salticidae_stable");
    public static final Identifier RECOVERY_1 = addon("perk_spider_salticidae_recovery_1");
    public static final Identifier RECOVERY_2 = addon("perk_spider_salticidae_recovery_2");
    private static final double KILL_HEAL_1 = 2.0, KILL_HEAL_2 = 4.0;
    private static final double CHARGE_TIME_MULTIPLIER = 0.7;
    private static final double JUMP_COOLDOWN_MULTIPLIER_1 = 0.85, JUMP_COOLDOWN_MULTIPLIER_2 = 0.75;
    private static final double VENOM_COOLDOWN_MULTIPLIER = 0.7;
    private static final double VENOM_DAMAGE_MULTIPLIER = 1.25;
    private static final int STRENGTH_TICKS = 60;
    private static final BalanceReader BAL = new BalanceReader("abilities.salticidae_perks");

    private SpiderSalticidaePerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.SPIDER_SALTICIDAE.equals(form.getFormID());
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_Spider3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_Spider3PerkTree);
        if (purchased != null && (RegPerks.P_SpiderCocoonDigestion_1.equals(perk.getID()) && purchased.contains(RegPerks.P_SpiderCocoonDigestion_2)
                || RegPerks.P_SpiderSilkSecretion_1.equals(perk.getID()) && purchased.contains(RegPerks.P_SpiderSilkSecretion_2))) return true;
        var source = form.getFormLayer().getRight();
        for (var removed : perk.powerRemove) FormUtils.removePower(player, removed, source);
        if (RegPerks.P_SpiderCocoonDigestion_2.equals(perk.getID())) FormUtils.removePower(player, FEEDING_1, source);
        if (RegPerks.P_SpiderSilkSecretion_2.equals(perk.getID())) FormUtils.removePower(player, RECOVERY_1, source);
        for (var added : perk.powerAdd) {
            Identifier mapped = switch (added.getPath()) {
                case "perks/spider_cocoon_digestion_1" -> FEEDING_1;
                case "perks/spider_cocoon_digestion_2" -> FEEDING_2;
                case "perks/spider_bridge_weaver" -> CHARGE;
                case "perks/spider_perceptual_web" -> SENSE;
                case "perks/spider_rapid_spinner" -> RAPID;
                case "perks/spider_stabilized_projectile" -> STABLE;
                case "perks/spider_silk_secretion_1" -> RECOVERY_1;
                case "perks/spider_silk_secretion_2" -> RECOVERY_2;
                default -> added;
            };
            FormUtils.applyPower(player, mapped, source);
        }
        return true;
    }

    public static boolean enabled(PlayerEntity player, Identifier power) {
        return isSupported(FormUtils.getPlayerForm(player)) && PowerTypeRegistry.contains(power)
                && PowerHolderComponent.KEY.get(player).hasPower(PowerTypeRegistry.get(power));
    }

    public static int chargeTicks(PlayerEntity player, int base) {
        return Math.max(1, scaleTicks(base, enabled(player, CHARGE) ? BAL.d("charge_time_multiplier", CHARGE_TIME_MULTIPLIER) : 1.0));
    }

    public static float killHealing(PlayerEntity player) {
        return (float) (enabled(player, FEEDING_2) ? BAL.d("kill_heal_2", KILL_HEAL_2)
                : enabled(player, FEEDING_1) ? BAL.d("kill_heal_1", KILL_HEAL_1) : 0.0);
    }

    public static int strengthTicks(PlayerEntity player) {
        return enabled(player, SENSE) ? BAL.i("strength_ticks", STRENGTH_TICKS) : 0;
    }

    public static double venomMultiplier(PlayerEntity player) {
        return enabled(player, STABLE) ? BAL.d("venom_damage_multiplier", VENOM_DAMAGE_MULTIPLIER) : 1.0;
    }

    /** 在统一服务中按本次购买状态冻结成功／失败冷却，保留JSON起算模式。 */
    public static boolean beginSkill(ServerPlayerEntity player, String skill, boolean venom) {
        if (!isSupported(FormUtils.getPlayerForm(player))) return false;
        var spec = SkillCooldowns.spec(player, skill);
        if (spec == null) return false;
        double multiplier = venom ? (enabled(player, RAPID) ? BAL.d("venom_cooldown_multiplier", VENOM_COOLDOWN_MULTIPLIER) : 1.0)
                : enabled(player, RECOVERY_2) ? BAL.d("jump_cooldown_multiplier_2", JUMP_COOLDOWN_MULTIPLIER_2)
                : enabled(player, RECOVERY_1) ? BAL.d("jump_cooldown_multiplier_1", JUMP_COOLDOWN_MULTIPLIER_1) : 1.0;
        var original = spec.resolve();
        var config = new SkillCastManager.ResolvedConfig(scaleTicks(original.cooldown, multiplier),
                scaleTicks(original.failCooldown, multiplier), original.cooldownStart);
        return SkillCastManager.get(player.getServerWorld()).begin(player, skill, config) >= 0;
    }

    /** 仅同步蓄力状态变化；也会清除重连后留下的旧资源值。 */
    public static void setSensing(ServerPlayerEntity player, boolean charging) {
        if (!enabled(player, SENSE_CHARGING)) return;
        int value = charging ? 1 : 0;
        if (PowerUtils.getResourceValue(player, SENSE_CHARGING) != value) PowerUtils.setResourceValueAndSync(player, SENSE_CHARGING, value);
    }

    public static void rewardDirectHit(ServerPlayerEntity player, LivingEntity target, float healthLost, float healing, int strengthTicks) {
        if (healthLost <= 0 || !isSupported(FormUtils.getPlayerForm(player)) || WhitelistUtils.isProtected(player, target)) return;
        if (strengthTicks > 0) player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, strengthTicks, 1));
        if (healing > 0 && !target.isAlive()) player.heal(healing);
    }

    private static int scaleTicks(int base, double multiplier) { return Math.max(0, (int) Math.ceil(base * multiplier)); }
    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
}
