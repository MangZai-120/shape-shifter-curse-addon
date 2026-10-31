package net.jackcooper.shapeShifterCurseAddon.item;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.*;

/** 在真实 ServerWorld 和已应用的 mixin 中测试，不替换 SSC 的变形与本能实现。 */
public final class InstinctAnchorWorldProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(float value, float expected, String message) {
        check(Math.abs(value - expected) < .0001f, message + ": " + value);
    }
    private static ServerPlayerEntity player(TestContext t, IForm form) {
        var player = t.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        FormUtils._setForm(player, form);
        return player;
    }
    private static boolean use(ServerPlayerEntity player, Item item, int count) {
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(item, count));
        return item.use(player.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted();
    }
    private static int held(ServerPlayerEntity player) { return player.getMainHandStack().getCount(); }

    public static void start(TestContext t) {
        t.waitAndRun(65, () -> run(t));
    }

    private static void run(TestContext t) {
        var config = ShapeShifterCurseFabric.commonConfig;
        int[] phases = config.curseMoonPhase;
        boolean instant = config.immediatelyTransform, transform = config.enableCursedMoonTransform;
        var world = t.getWorld().getServer().getOverworld();
        long time = world.getTimeOfDay();
        try {
            config.curseMoonPhase = new int[]{0}; config.immediatelyTransform = true; config.enableCursedMoonTransform = true;
            world.setTimeOfDay(6000);
            IForm base = RegPlayerForms.getPlayerForm(new Identifier("shape-shifter-curse", "familiar_fox_1"));
            IForm other = RegPlayerForms.getPlayerForm(new Identifier("shape-shifter-curse", "familiar_fox_2"));
            check(base != null && other != null && !FormUtils.NoInstinct.hasFlag(base) && !FormUtils.LockInstinct.hasFlag(base), "本能可变的原生测试形态存在");
            var p = player(t, base); var q = player(t, base);
            var nativeState = PlayerFormComponent.COMPONENT.get(p);
            nativeState.instinctValue = 23.75f;
            check(use(p, SscAddon.INSTINCT_ANCHOR, 2) && held(p) == 1, "固定成功且生存只消耗一个");
            check(InstinctAnchors.locked(p) && !InstinctAnchors.locked(q), "固定按玩家隔离");
            check(!InstinctUtils.playerInstinctLock.getOrDefault(p.getUuid(), false), "固定不写原版锁表");
            check(!use(p, SscAddon.INSTINCT_ANCHOR, 2) && held(p) == 2, "重复固定不消耗");
            InstinctUtils.addInstinctEffect(p, new Identifier("ssc_addon", "probe"), 5, 1, true);
            InstinctUtils.addInstinctEffect(p, new Identifier("ssc_addon", "probe"), -9, 1, true);
            InstinctUtils.addInstinctEffect(p, new InstinctUtils.InstinctEffect(new Identifier("ssc_addon", "direct"), 8, 1), true);
            InstinctUtils.clearInstinct(p);
            near(nativeState.instinctValue, 23.75f, "即时增减和清零不能改变固定值");
            InstinctUtils.addInstinctEffect(p, new Identifier("ssc_addon", "probe_sustained"), .1f, 20, false);
            float qBefore = PlayerFormComponent.COMPONENT.get(q).instinctValue;
            InstinctUtils.serverTick(p.getServer());
            near(nativeState.instinctValue, 23.75f, "持续本能效果与基础增长均被冻结");
            near(nativeState.instinctRate, 0, "发给客户端的预测速度归零");
            check(PlayerFormComponent.COMPONENT.get(q).instinctValue > qBefore, "另一个玩家仍正常增长");
            nativeState.instinctValue = 100;
            InstinctUtils.serverTick(p.getServer());
            check(nativeState.nowForm.isEquals(base) && nativeState.instinctValue == 100, "达到阈值也不偷偷变形或清零");
            nativeState.instinctValue = 23.75f;
            var history = java.util.List.copyOf(nativeState.formHistory);
            check(!TransformManager.startTransform(p, other, null), "动画变形被拒绝");
            check(!TransformManager.immediatelyTransform(p, other), "即时变形被拒绝");
            check(!TransformManager.forceTransform(p, other, true), "强制变形被拒绝");
            check(nativeState.transformTargetForm == null && nativeState.formHistory.equals(history), "拒绝前不改目标或清空历史");
            FormUtils._setForm(p, other); FormUtils.setForm(p, other); FormUtils._loadForm(p, other, true);
            nativeState.setForm(other); nativeState.setForm(other.getFormID()); nativeState.clear();
            check(nativeState.nowForm.isEquals(base) && nativeState.formHistory.equals(history), "统一入口和组件直写入口保持原形态与历史");
            FormUtils._loadForm(p, base, true);
            check(nativeState.nowForm.isEquals(base), "同一形态重新应用能力不受阻");
            p.setStackInHand(Hand.MAIN_HAND, new ItemStack(RegCustomItem.CATALYST, 2));
            check(!UseItemCallback.EVENT.invoker().interact(p, p.getWorld(), Hand.MAIN_HAND).getResult().isAccepted()
                    && held(p) == 2, "普通催化剂提前拒绝，不消耗材料");
            NbtCompound save = p.writeNbt(new NbtCompound()); save.remove("UUID");
            var loaded = t.createMockCreativeServerPlayerInWorld(); loaded.readNbt(save);
            check(InstinctAnchors.locked(loaded) && PlayerFormComponent.COMPONENT.get(loaded).nowForm.isEquals(base), "真实玩家NBT保存与重新读取同时保留固定和形态");
            near(PlayerFormComponent.COMPONENT.get(loaded).instinctValue, 23.75f, "保存恢复保留本能值");
            var respawn = t.createMockCreativeServerPlayerInWorld(); respawn.copyFrom(p, false);
            check(InstinctAnchors.locked(respawn) && PlayerFormComponent.COMPONENT.get(respawn).nowForm.isEquals(base), "死亡复制保留固定和形态");
            var missing = new NbtCompound();
            var restored = new net.jackcooper.shapeShifterCurseAddon.state.InstinctAnchorComponent();
            restored.readFromNbt(missing); check(!restored.isLocked(), "旧存档缺字段默认未固定");
            world.setTimeOfDay(13000);
            check(CursedMoon.isInCursedMoon(world), "真实原版月夜判定已生效");
            check(!use(p, SscAddon.INSTINCT_RELEASE, 2) && held(p) == 2 && InstinctAnchors.locked(p), "月夜不能解除，也不消耗道具");
            check(!use(q, SscAddon.INSTINCT_ANCHOR, 2) && held(q) == 2 && !InstinctAnchors.locked(q), "月夜不能新增固定");
            CursedMoon.serverTick(p.getServer());
            check(nativeState.nowForm.isEquals(base) && !nativeState.isCursedMoonApplied
                    && nativeState.BeforeCursedMoonAppliedForm == null && nativeState.AfterCursedMoonAppliedForm == null,
                    "已固定者跨月夜不变形，也不产生虚假的退回记录");
            check(PlayerFormComponent.COMPONENT.get(q).isCursedMoonApplied, "未固定玩家的月夜逻辑仍执行");
            InstinctUtils.serverTick(p.getServer());
            near(PlayerFormComponent.COMPONENT.get(q).instinctRate, 0, "未固定玩家仍受原版月夜本能锁保护");
            world.setTimeOfDay(6000); CursedMoon.serverTick(p.getServer());
            check(use(p, SscAddon.INSTINCT_RELEASE, 2) && held(p) == 1 && !InstinctAnchors.locked(p), "天亮后可正常解除且只消耗一个");
            check(nativeState.nowForm.isEquals(base), "解除不执行不存在的月夜退回");
            InstinctUtils.serverTick(p.getServer());
            check(nativeState.instinctValue > 23.75f, "解除后本能值恢复正常增长");
            InstinctUtils.playerInstinctLock.put(p.getUuid(), true);
            check(!use(p, SscAddon.INSTINCT_ANCHOR, 2) && held(p) == 2, "原版本能锁存在时不能新增固定");
            check(!use(p, SscAddon.INSTINCT_RELEASE, 2) && held(p) == 2
                    && InstinctUtils.playerInstinctLock.get(p.getUuid()), "无道具固定时解除不会清掉原版锁");
            InstinctUtils.playerInstinctLock.remove(p.getUuid());
            check(use(p, SscAddon.INSTINCT_ANCHOR, 2), "正常情况下能再次固定");
            InstinctUtils.playerInstinctLock.put(p.getUuid(), true);
            check(use(p, SscAddon.INSTINCT_RELEASE, 2) && InstinctUtils.playerInstinctLock.get(p.getUuid()), "只解除道具来源，不清掉并存原版锁");
            InstinctUtils.playerInstinctLock.remove(p.getUuid());
            nativeState.transformTargetForm = other;
            check(!use(p, SscAddon.INSTINCT_ANCHOR, 2) && held(p) == 2, "变形途中不接受固定");
            nativeState.transformTargetForm = null;
            FormUtils._setForm(p, RegPlayerForms.ORIGINAL_BEFORE_ENABLE);
            check(!use(p, SscAddon.INSTINCT_ANCHOR, 2) && held(p) == 2, "没有本能条的形态不能新增固定");
            FormUtils._setForm(p, base);
            check(TransformManager.immediatelyTransform(p, other) && nativeState.nowForm.isEquals(other), "解除后原版变形入口恢复正常");
            check(p.getServer().getRecipeManager().get(new Identifier("ssc_addon", "instinct_anchor")).isPresent()
                    && p.getServer().getRecipeManager().get(new Identifier("ssc_addon", "instinct_release")).isPresent(), "两个配方已由真实资源加载器读取");
            System.out.println("INSTINCT_ANCHOR_WORLD_PASS checks=" + checks);
            t.complete();
        } catch (Throwable failure) {
            failure.printStackTrace(); throw new RuntimeException(failure);
        } finally {
            config.curseMoonPhase = phases; config.immediatelyTransform = instant; config.enableCursedMoonTransform = transform;
            world.setTimeOfDay(time);
        }
    }
}
