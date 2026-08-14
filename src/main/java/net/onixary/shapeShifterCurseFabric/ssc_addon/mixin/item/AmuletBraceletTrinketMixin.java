package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.items.trinkets.AmuletBraceletTrinket;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 守御脚环（amulet_bracelet）契灵形态适配（饰品后端无关）：
 * - 允许契灵正常装备脚环（不拦截 canEquip）。
 * - 玩家在契灵形态下、脚环仍佩戴在身上时，行动栏持续刷新红色"无效"提示。
 *   action bar 文本本身在客户端约 2 秒后淡出，每 tick 重发一次即可保持持续显示。
 * - accessory_power 数据没有为 mancianima 配置 add/remove，因此脚环对契灵在数据层面本就无效，
 *   这里仅补充常驻 UI 反馈。
 *
 * <p><b>注入方式（兼容性关键）</b>：目标是父类 {@link AccessoryItem#accessoryTick}（子类
 * {@link AmuletBraceletTrinket} 未覆写它，虚分派最终落到父类），用 {@code @Inject(HEAD)}
 * 追加逻辑而<b>不 cancel</b>——主包将来若给 {@code AccessoryItem.accessoryTick} 加真实逻辑
 * （耐久/冷却/buff 等）不会被吞掉。旧写法（mixin 子类 + 无注解同名方法 = 隐式 overwrite）
 * 会静默顶掉父类实现，已废弃。Trinkets / Curios 桥接层（TrinketImpl 等）虚分派照常生效，
 * 纯 Curios（经 Kilt 加载）环境同样适用，附属对 trinkets 保持弱依赖。
 */
@Mixin(AccessoryItem.class)
public abstract class AmuletBraceletTrinketMixin {

	@Inject(method = "accessoryTick", at = @At("HEAD"), remap = false)
	private void ssc_addon$mancianimaBraceletHint(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData, CallbackInfo ci) {
		// instanceof 守卫：只处理守御脚环，其它饰品（附属 19 个 + 主包全部）零开销直接返回
		if (!((Object) this instanceof AmuletBraceletTrinket)) return;
		if (!(entity instanceof PlayerEntity player)) return;
		if (player.getWorld().isClient) return;
		if (!FormUtils.isForm(entity, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return;

		player.sendMessage(
				Text.translatable("item.shape-shifter-curse.amulet_bracelet.cant_equip_mancianima")
						.formatted(Formatting.RED),
				true
		);
	}
}
