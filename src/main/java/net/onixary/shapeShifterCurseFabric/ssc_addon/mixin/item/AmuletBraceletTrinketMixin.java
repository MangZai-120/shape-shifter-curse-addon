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

/**
 * 守御脚环（amulet_bracelet）契灵形态适配（饰品后端无关）：
 * - 允许契灵正常装备脚环（不拦截 canEquip）。
 * - 玩家在契灵形态下、脚环仍佩戴在身上时，行动栏持续刷新红色"无效"提示。
 *   action bar 文本本身在客户端约 2 秒后淡出，每 tick 重发一次即可保持持续显示。
 * - accessory_power 数据没有为 mancianima 配置 add/remove，因此脚环对契灵在数据层面本就无效，
 *   这里仅补充常驻 UI 反馈。
 *
 * <p>覆写 {@link AccessoryItem#accessoryTick}（Trinkets / Curios 桥接层最终都会虚分派到它），
 * 不再依赖 Trinkets 的 SlotReference——纯 Curios（经 Kilt 加载）环境同样生效，
 * 附属对 trinkets 保持弱依赖。
 */
@Mixin(AmuletBraceletTrinket.class)
public abstract class AmuletBraceletTrinketMixin {

	public void accessoryTick(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
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
