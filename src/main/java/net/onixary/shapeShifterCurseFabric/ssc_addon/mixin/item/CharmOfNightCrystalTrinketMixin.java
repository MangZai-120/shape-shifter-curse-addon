package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.items.trinkets.CharmOfNightCrystalTrinket;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 黑夜水晶吊坠（charm_of_night_crystal）寄生果蝠适配（饰品后端无关）：
 * - 寄生果蝠固定血量上限、不受日照掉血影响，禁止其装备黑夜水晶吊坠。
 * - canEquip：果蝠形态下无法装入饰品槽。
 * - accessoryTick：若玩家在其它形态先戴上吊坠后再变成果蝠，自动卸下并归还（服务端，延迟到下一 tick 执行避免迭代冲突）。
 *
 * <p>覆写 {@link AccessoryItem} 原生回调（Trinkets / Curios 桥接层最终都会虚分派到它们），
 * 卸下操作走 {@link AccessoryUtils#setEntitySlot} 抽象层（"auto" 自动适配当前活动饰品框架），
 * 不再依赖 Trinkets 的 SlotReference——纯 Curios（经 Kilt 加载）环境同样生效，附属对 trinkets 保持弱依赖。
 * 其它形态（含吸血蝙蝠）不受影响，保持主包默认可装备。
 */
@Mixin(CharmOfNightCrystalTrinket.class)
public abstract class CharmOfNightCrystalTrinketMixin {

	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		// 寄生果蝠禁止装备；其它形态保持主包默认（可装备）
		return !FormUtils.isBatParasiticFruit(entity);
	}

	public void accessoryTick(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		// 玩家在其它形态戴上吊坠后变成寄生果蝠时，自动卸下并归还
		if (!(entity instanceof PlayerEntity player)) return;
		if (player.getWorld().isClient) return;
		if (!FormUtils.isBatParasiticFruit(player)) return;
		MinecraftServer server = player.getServer();
		if (server == null) return;
		// 延迟到下一 tick 执行，避免在饰品框架遍历已装备饰品时修改饰品栏
		server.execute(() -> {
			if (!FormUtils.isBatParasiticFruit(player)) return;
			// 经抽象层扫描全部饰品槽定位本吊坠（后端无关：Trinkets 槽位带分组，Curios 无分组，统一扫描避免解析格式差异）
			var slots = AccessoryUtils.getEntitySlots(player, "auto");
			if (slots == null) return; // 无活动饰品后端（理论到不了这里，防御性返回）
			slots.forEach((slotKey, stacks) -> {
				if (stacks == null) return;
				for (int i = 0; i < stacks.size(); i++) {
					ItemStack current = stacks.get(i);
					if (current == null || current.isEmpty() || current.getItem() != (Object) this) continue;
					AccessoryUtils.setEntitySlot(player, "auto", slotKey.getLeft(), slotKey.getRight(), i, ItemStack.EMPTY);
					if (!player.getInventory().insertStack(current)) {
						player.dropItem(current, false);
					}
					player.sendMessage(
							Text.translatable("msg.my_addon.charm_night_crystal_cant_equip_parasitic_fruit")
									.formatted(Formatting.RED),
							true
					);
					return; // 只卸第一件（饰品同类一般不可叠加，仅占一个槽）
				}
			});
		});
	}
}
