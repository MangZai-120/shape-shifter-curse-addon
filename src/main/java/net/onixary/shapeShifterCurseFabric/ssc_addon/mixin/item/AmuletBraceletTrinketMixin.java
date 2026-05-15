package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import dev.emi.trinkets.api.SlotReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.trinkets.AmuletBraceletTrinket;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 守御脚环（amulet_bracelet）契灵形态适配：
 * - 允许契灵正常装备脚环（不拦截 canEquip）。
 * - 玩家在契灵形态下、脚环仍佩戴在身上时，行动栏持续刷新红色"无效"提示。
 *   通过软覆盖 Trinket.tick（trinkets 每 tick 对每件已装备 trinket 调用一次）实现。
 *   action bar 文本本身在客户端约 2 秒后淡出，每 tick 重发一次即可保持持续显示。
 * - accessory_power 数据没有为 mancianima 配置 add/remove，因此脚环对契灵在数据层面本就无效，
 *   这里仅补充常驻 UI 反馈。
 *
 * 通过同签名方法软覆盖 SSC 主包 TrinketImpl mixin 提供的默认桥接。
 */
@Mixin(AmuletBraceletTrinket.class)
public abstract class AmuletBraceletTrinketMixin {

	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
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
