package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils;
import net.onixary.shapeShifterCurseFabric.status_effects.other_effects.FeedEffect;
import org.jetbrains.annotations.Nullable;

/** 试管的瞬时剂量入口；保留原版取整和SSC恢复逻辑，只为本次压缩能量试管限定缩放上下文。 */
public final class PotionTubeDose {
    private static final ThreadLocal<FeedDose> FEED_DOSE = new ThreadLocal<>();

    private PotionTubeDose() {}

    /** 普通持续效果可按时长拆分；瞬时效果仅开放已核对剂量语义的治疗、伤害及压缩能量。 */
    public static boolean isDivisible(StatusEffect effect) {
        if (effect instanceof BaseTransformativeStatusEffect) return false;
        return !effect.isInstant() || effect == StatusEffects.INSTANT_HEALTH
                || effect == StatusEffects.INSTANT_DAMAGE || effect instanceof FeedEffect;
    }

    /** 变形目标NBT具有一次性语义，即使药水效果列表为空也不自动认作普通药水。 */
    public static boolean isDivisible(ItemStack stack) {
        if (stack.isEmpty() || CTPUtils.getCTPFormIDFromNBT(stack.getNbt()) != null) return false;
        return PotionUtil.getPotionEffects(stack).stream().allMatch(effect -> isDivisible(effect.getEffectType()));
    }

    public static void applyInstant(StatusEffectInstance effect, @Nullable PlayerEntity player,
                                    LivingEntity user, double dose) {
        applyInstant(effect, player, player, user, dose);
    }

    public static void applyInstant(StatusEffectInstance effect, @Nullable Entity source,
                                    @Nullable Entity attacker, LivingEntity target, double dose) {
        if (!Double.isFinite(dose) || dose <= 0) return;
        double fraction = Math.min(1, dose);
        StatusEffect type = effect.getEffectType();
        if (!(type instanceof FeedEffect)) {
            // 原版治疗/伤害在此沿用 int(基础值×剂量＋0.5) 的取整及所有原有形态修正。
            type.applyInstantEffect(source, attacker, target, effect.getAmplifier(), fraction);
            return;
        }
        FeedDose previous = FEED_DOSE.get();
        FEED_DOSE.set(new FeedDose(target, fraction));
        try {
            type.applyInstantEffect(source, attacker, target, effect.getAmplifier(), fraction);
        } finally {
            if (previous == null) FEED_DOSE.remove();
            else FEED_DOSE.set(previous);
        }
    }

    /** 只替换本次Feed调用的喷溅距离下限；普通瓶、喷溅和滞留没有该上下文。 */
    public static double feedFraction(LivingEntity target, double original) {
        FeedDose context = FEED_DOSE.get();
        if (context == null || context.target != target || context.claimed) return original;
        context.claimed = true;
        return context.fraction;
    }

    /** 捕获原生ceil前的食物剂量，避免复制SSC的基础数值或取整算法。 */
    public static void captureFeedFood(LivingEntity target, double unroundedFood) {
        FeedDose context = FEED_DOSE.get();
        if (context != null && context.target == target && context.claimed && !context.foodCaptured) {
            context.unroundedFood = unroundedFood;
            context.foodCaptured = true;
        }
    }

    /** 食物仍取原生ceil；饱和度按整瓶线性分剂量，避免食物和modifier双重缩放。 */
    public static float feedSaturation(LivingEntity target, int food, float original) {
        FeedDose context = FEED_DOSE.get();
        if (context == null || context.target != target || !context.foodCaptured || context.saturationClaimed || food <= 0) return original;
        context.saturationClaimed = true;
        double adjusted = context.unroundedFood * original / (food * context.fraction);
        return Double.isFinite(adjusted) ? (float) adjusted : original;
    }

    private static final class FeedDose {
        private final LivingEntity target;
        private final double fraction;
        private boolean claimed, foodCaptured, saturationClaimed;
        private double unroundedFood;
        private FeedDose(LivingEntity target, double fraction) {
            this.target = target;
            this.fraction = fraction;
        }
    }
}
