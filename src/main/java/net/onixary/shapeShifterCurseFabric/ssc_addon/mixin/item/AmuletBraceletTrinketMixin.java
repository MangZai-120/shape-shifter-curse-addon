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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 守御脚环（amulet_bracelet）契灵形态适配：
 * - 允许契灵形态正常装备脚环（不再拦截 canEquip）。
 * - 装备瞬间向契灵玩家弹出行动栏提示，告知该形态下脚环无效果。
 * - accessory_power 数据本身就没有为 mancianima 形态配置 add/remove，所以脚环对契灵自然没有任何实际效果，
 *   这里只补充一个"戴上后弹出文字提示"的反馈。
 *
 * 通过同签名方法软覆盖 SSC 主包 TrinketImpl mixin 提供的 Trinket.onEquip 桥接。
 * trinkets 在登录、打开背包、装备同步等场景会反复触发 onEquip，因此使用最小间隔节流，避免提示连续刷屏。
 */
@Mixin(AmuletBraceletTrinket.class)
public abstract class AmuletBraceletTrinketMixin {

	/** 玩家 UUID -> 上次弹出提示的游戏时间（tick）。用于节流，防止 trinkets 反复同步导致提示刷屏。 */
	private static final ConcurrentHashMap<UUID, Long> ssca$lastNotifyTick = new ConcurrentHashMap<>();
	/** 节流间隔：100 tick = 5 秒，足以覆盖 trinkets 同步抖动且不影响真正的"再次装备"场景。 */
	private static final long ssca$NOTIFY_COOLDOWN_TICKS = 100L;

	public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (!(entity instanceof PlayerEntity player)) return;
		if (player.getWorld().isClient) return;
		if (!FormUtils.isForm(entity, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return;

		long now = player.getWorld().getTime();
		Long last = ssca$lastNotifyTick.get(player.getUuid());
		if (last != null && now - last < ssca$NOTIFY_COOLDOWN_TICKS) return;
		ssca$lastNotifyTick.put(player.getUuid(), now);

		player.sendMessage(
				Text.translatable("item.shape-shifter-curse.amulet_bracelet.cant_equip_mancianima")
						.formatted(Formatting.RED),
				true
		);
	}
}
