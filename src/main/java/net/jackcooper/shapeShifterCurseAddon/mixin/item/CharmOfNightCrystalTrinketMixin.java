package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.items.trinkets.CharmOfNightCrystalTrinket;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 黑夜水晶吊坠（charm_of_night_crystal）寄生果蝠适配（饰品后端无关）：
 * - 寄生果蝠固定血量上限、不受日照掉血影响，禁止其装备黑夜水晶吊坠。
 * - canEquip：果蝠形态下无法装入饰品槽。
 * - accessoryTick：若玩家在其它形态先戴上吊坠后再变成果蝠，自动卸下并归还（服务端，延迟到下一 tick 执行避免迭代冲突）。
 *
 * <p><b>注入方式（兼容性关键）</b>：目标是父类 {@link AccessoryItem} 的 {@code canEquip}/
 * {@code accessoryTick}（子类 {@link CharmOfNightCrystalTrinket} 未覆写，虚分派最终落到父类），
 * 用 {@code @Inject} 而非隐式 overwrite——canEquip 命中果蝠时 {@code setReturnValue(false)}，
 * 非果蝠完全透传主包默认（可装备）；accessoryTick 只在吊坠+果蝠时追加卸下逻辑，其余饰品零影响。
 * 旧写法（mixin 子类 + 无注解同名方法 = 隐式 overwrite）会静默顶掉父类实现，已废弃。
 * 卸下操作走 {@link AccessoryUtils#setEntitySlot} 抽象层（"auto" 自动适配当前活动饰品框架），
 * 纯 Curios（经 Kilt 加载）环境同样生效，附属对 trinkets 保持弱依赖。
 */
@Mixin(AccessoryItem.class)
public abstract class CharmOfNightCrystalTrinketMixin {

	@Inject(method = "canEquip", at = @At("HEAD"), cancellable = true, remap = false)
	private void ssc_addon$parasiticFruitCantEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData, CallbackInfoReturnable<Boolean> cir) {
		// instanceof 守卫：只处理黑夜水晶吊坠，其它饰品完全透传主包默认行为
		if (!((Object) this instanceof CharmOfNightCrystalTrinket)) return;
		// 寄生果蝠禁止装备；其它形态保持主包默认（可装备）
		if (FormUtils.isBatParasiticFruit(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "accessoryTick", at = @At("HEAD"), remap = false)
	private void ssc_addon$parasiticFruitAutoUnequip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData, CallbackInfo ci) {
		// instanceof 守卫：只处理黑夜水晶吊坠，其它饰品零开销直接返回
		if (!((Object) this instanceof CharmOfNightCrystalTrinket)) return;
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
