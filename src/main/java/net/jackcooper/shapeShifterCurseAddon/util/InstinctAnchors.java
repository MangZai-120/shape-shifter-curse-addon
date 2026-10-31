package net.jackcooper.shapeShifterCurseAddon.util;

import net.jackcooper.shapeShifterCurseAddon.mixin.TransformManagerAccessor;
import net.jackcooper.shapeShifterCurseAddon.state.RegInstinctAnchorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;

/** 服务端判定固定来源；无额外轮询、玩家缓存或逐 tick 网络包。 */
public final class InstinctAnchors {
    private InstinctAnchors() { }

    public enum Result { FIXED, RELEASED, CURSED_MOON, NATIVE_LOCKED, TRANSFORMING, ALREADY_FIXED, NOT_FIXED }

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (!locked(player)) return TypedActionResult.pass(stack);
            String item = Registries.ITEM.getId(stack.getItem()).toString();
            boolean changesForm = switch (item) {
                case "shape-shifter-curse:catalyst", "shape-shifter-curse:powerful_catalyst",
                     "shape-shifter-curse:inhibitor", "shape-shifter-curse:powerful_inhibitor",
                     "shape-shifter-curse:creative_inhibitor", "shape-shifter-curse:moondust_matrix",
                     "shape-shifter-curse:untreated_moondust", "ssc_addon:sp_upgrade_thing",
                     "ssc_addon:evolution_stone" -> true;
                default -> false;
            };
            if (!changesForm) changesForm = PotionUtil.getPotionEffects(stack).stream()
                    .anyMatch(effect -> effect.getEffectType() instanceof BaseTransformativeStatusEffect);
            if (!changesForm) return TypedActionResult.pass(stack);
            if (!world.isClient) player.sendMessage(Text.translatable("message.ssc_addon.instinct_anchor.blocked_item"), true);
            return TypedActionResult.fail(stack);
        });
    }

    public static boolean locked(PlayerEntity player) {
        if (player == null) return false;
        // 部分自动化与兼容层使用精简模拟玩家，未附加此组件时按未固定处理。
        var state = RegInstinctAnchorComponent.ANCHOR.getNullable(player);
        return state != null && state.isLocked();
    }

    public static boolean blocksChange(PlayerEntity player, IForm target) {
        return player instanceof ServerPlayerEntity && locked(player)
                && target != null && !target.isPlayerForm(player);
    }

    public static Result apply(ServerPlayerEntity player, boolean fix) {
        // SSC 的周期结算使用主世界月相，跨维度也必须服从同一判定。
        var overworld = player.getServer().getOverworld();
        if (CursedMoon.isInCursedMoon(overworld)) return Result.CURSED_MOON;
        var nativeState = PlayerFormComponent.COMPONENT.get(player);
        var animation = TransformManagerAccessor.ssca$playerData().get(player.getUuid());
        if (nativeState.transformTargetForm != null || animation != null && animation.transformTimer >= 0)
            return Result.TRANSFORMING;
        var state = RegInstinctAnchorComponent.ANCHOR.get(player);
        if (fix) {
            if (state.isLocked()) return Result.ALREADY_FIXED;
            IForm form = nativeState.nowForm;
            if (nativeState.isCursedMoonApplied || FormUtils.NoInstinct.hasFlag(form)
                    || FormUtils.LockInstinct.hasFlag(form)
                    || InstinctUtils.playerInstinctLock.getOrDefault(player.getUuid(), false))
                return Result.NATIVE_LOCKED;
            state.setLocked(true);
            nativeState.instinctRate = 0;
        } else {
            if (!state.isLocked()) return Result.NOT_FIXED;
            state.setLocked(false);
            // 只移除自己的固定。原版的月相、动画锁和形态固有锁均由 SSC 自己管理。
            CursedMoon.applyEndCursedMoonEffect(overworld, player);
        }
        RegInstinctAnchorComponent.ANCHOR.sync(player);
        nativeState.sync();
        return fix ? Result.FIXED : Result.RELEASED;
    }
}
