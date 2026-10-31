package net.jackcooper.shapeShifterCurseAddon.item;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.onixary.shapeShifterCurseFabric.mana.ManaRegistries;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import net.onixary.shapeShifterCurseFabric.status_effects.other_effects.FeedEffect;

/** 在真实服务端世界中核对原版周期、瞬时取整及压缩能量三分之一剂量；由父探针调用。 */
public final class PotionTubeDoseWorldProbe {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void near(double value, double expected, String why) {
        check(Math.abs(value - expected) < 1e-5, why + ": " + value + " != " + expected);
    }

    public static int verify(TestContext t) {
        checks = 0;
        var player = t.createMockCreativeServerPlayerInWorld();
        player.setNoGravity(true);
        ManaUtils.setManaTypeID(player, ManaRegistries.FAMILIAR_FOX_MANA);

        var regen = portion(Potions.REGENERATION, StatusEffects.REGENERATION);
        check(regen.getDuration() == 300 && regen.getAmplifier() == 0, "再生I的900tick按三分之一成为300tick，强度不变");
        var triggers = triggerTicks(regen);
        check(triggers.size() == 6, "再生I试管仍按原生节奏触发六次");
        check(spacing(triggers, 50), "再生I间隔保留原版50tick，不能缩成三分之一间隔");
        player.setHealth(1);
        for (int tick = 0; tick < 300; tick++) regen.update(player, () -> {});
        near(player.getHealth(), 7, "真实原版update在300tick剂量内恢复六点生命");

        var strongRegen = portion(Potions.STRONG_REGENERATION, StatusEffects.REGENERATION);
        check(strongRegen.getDuration() == 150 && strongRegen.getAmplifier() == 1, "再生II保留等级并将450tick缩为150tick");
        check(triggerTicks(strongRegen).size() == 6 && spacing(triggerTicks(strongRegen), 25), "再生II仍每25tick触发");
        var poison = portion(Potions.POISON, StatusEffects.POISON);
        check(poison.getDuration() == 300 && poison.getAmplifier() == 0, "剧毒I持续时间三分之一，等级不变");
        check(triggerTicks(poison).size() == 12 && spacing(triggerTicks(poison), 25), "剧毒I仍按原版每25tick触发");

        player.setHealth(10);
        PotionTubeDose.applyInstant(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1), player, player, 1.0 / 3);
        near(player.getHealth(), 11, "瞬间治疗I按原版int(4/3+0.5)回复一点");
        player.setHealth(10);
        PotionTubeDose.applyInstant(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 1), player, player, 1.0 / 3);
        near(player.getHealth(), 13, "瞬间治疗II按原版int(8/3+0.5)回复三点");
        var cow = EntityType.COW.create(t.getWorld());
        check(cow != null, "创建真实原版非亡灵受术生物");
        cow.setHealth(10);
        PotionTubeDose.applyInstant(new StatusEffectInstance(StatusEffects.INSTANT_DAMAGE, 1), player, cow, 1.0 / 3);
        near(cow.getHealth(), 8, "瞬间伤害I按原版剂量造成两点伤害");
        cow.timeUntilRegen = 0; cow.setHealth(10);
        PotionTubeDose.applyInstant(new StatusEffectInstance(StatusEffects.INSTANT_DAMAGE, 1, 1), player, cow, 1.0 / 3);
        near(cow.getHealth(), 6, "瞬间伤害II按原版剂量造成四点伤害");
        cow.discard();

        resetFeed(player);
        RegOtherStatusEffects.FEED_EFFECT.applyInstantEffect(player, player, player, 0, 1);
        check(player.getHungerManager().getFoodLevel() == 18, "原瓶压缩能量仍恢复八点食物");
        near(player.getHungerManager().getSaturationLevel(), 9.6, "原瓶压缩能量仍增加9.6饱和度");
        near(ManaUtils.getPlayerMana(player), 25, "原瓶压缩能量仍恢复25点mana");

        resetFeed(player);
        PotionTubeDose.applyInstant(new StatusEffectInstance(RegOtherStatusEffects.FEED_EFFECT), player, player, 1.0 / 3);
        check(player.getHungerManager().getFoodLevel() == 13, "压缩能量试管食物采用原生ceil(8/3)=3");
        near(player.getHungerManager().getSaturationLevel(), 3.2, "压缩能量试管饱和度严格按整瓶三分之一恢复");
        near(ManaUtils.getPlayerMana(player), 25.0 / 3, "压缩能量试管mana严格为25/3，不受0.5喷溅下限影响");

        resetFeed(player);
        RegOtherStatusEffects.FEED_EFFECT.applyInstantEffect(player, player, player, 0, .1);
        check(player.getHungerManager().getFoodLevel() == 14, "离开试管上下文后普通喷溅仍保留0.5衰减下限");
        near(player.getHungerManager().getSaturationLevel(), 2.4, "普通低proximity喷溅的饱和度沿用SSC原值");
        near(ManaUtils.getPlayerMana(player), 12.5, "普通低proximity喷溅的mana沿用SSC原值");

        FeedEffect broken = new FeedEffect() {
            @Override public void applyInstantEffect(Entity source, Entity attacker, LivingEntity target, int amplifier, double proximity) {
                throw new IllegalStateException("dose-scope-test");
            }
        };
        try {
            PotionTubeDose.applyInstant(new StatusEffectInstance(broken), player, player, 1.0 / 3);
            throw new AssertionError("故障探针必须抛出异常");
        } catch (IllegalStateException expected) {
            check("dose-scope-test".equals(expected.getMessage()), "保留原生效果异常");
        }
        resetFeed(player);
        RegOtherStatusEffects.FEED_EFFECT.applyInstantEffect(player, player, player, 0, .1);
        check(player.getHungerManager().getFoodLevel() == 14, "即使试管效果抛错也不会残留剂量上下文");

        check(PotionTubeDose.isDivisible(StatusEffects.REGENERATION) && PotionTubeDose.isDivisible(StatusEffects.INSTANT_HEALTH)
                && PotionTubeDose.isDivisible(RegOtherStatusEffects.FEED_EFFECT), "常规持续、治疗与压缩能量支持拆分");
        check(!PotionTubeDose.isDivisible(StatusEffects.SATURATION), "原版饱食瞬时效果无比例入口，标记为特殊");
        System.out.println("POTION_TUBE_DOSE_WORLD_PASS checks=" + checks);
        return checks;
    }

    private static StatusEffectInstance portion(Potion potion, StatusEffect type) {
        var tube = PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION), potion));
        return PotionTestTubes.doseEffects(tube).stream().filter(effect -> effect.getEffectType() == type).findFirst().orElseThrow();
    }

    private static List<Integer> triggerTicks(StatusEffectInstance effect) {
        var ticks = new ArrayList<Integer>();
        for (int elapsed = 0; elapsed < effect.getDuration(); elapsed++) {
            if (effect.getEffectType().canApplyUpdateEffect(effect.getDuration() - elapsed, effect.getAmplifier())) ticks.add(elapsed);
        }
        return ticks;
    }

    private static boolean spacing(List<Integer> ticks, int expected) {
        for (int i = 1; i < ticks.size(); i++) if (ticks.get(i) - ticks.get(i - 1) != expected) return false;
        return true;
    }

    private static void resetFeed(ServerPlayerEntity player) {
        player.getHungerManager().setFoodLevel(10);
        player.getHungerManager().setSaturationLevel(0);
        ManaUtils.setPlayerMana(player, 0);
    }
}
