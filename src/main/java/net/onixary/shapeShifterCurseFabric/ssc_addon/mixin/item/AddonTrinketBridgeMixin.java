package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

/**
 * SSCA 饰品 Trinkets 桥接兜底（jackcooper）—— 主包 {@code TrinketImpl} 的条件等价替代。
 *
 * <p><b>背景</b>：主包 MixinConfigPlugin 仅在「饰品最高优先级插件 == trinkets」时应用
 * {@code accessory.TrinketImpl}（把 AccessoryItem 桥成 Trinket 接口，承担 canEquip /
 * canUnequip / accessoryTick / onEquip 等回调）。当环境装有 Kilt 转载的 Forge Curios 时
 * {@code isModLoaded("curios")} 恒真且其声明优先级 2000 &gt; trinkets 1000 → 主包
 * TrinketImpl 被跳过；但主包 Curios 侧实现（CurioUtils 等）是<b>空壳 stub</b> →
 * 两个后端全断：canEquip 无人调用（佩戴限制失效）、accessoryTick 停摆、饰品查询全空。</p>
 *
 * <p><b>互斥条件（防冲突关键）</b>：本 mixin 由 {@code SscAddonMixinConfigPlugin} 登记——
 * 仅当「trinkets API 可用（含 tclayer 兼容层）且主包 TrinketImpl 会被跳过」
 * （{@code AccessoryPriorityUtils.getHighestPriorityPlugin() != "trinkets"}）时应用。
 * 纯 Trinkets 环境主包 TrinketImpl 照常生效、本 mixin 不应用，二者严格互补互斥，
 * 绝不对同一目标类叠加两份 Trinket 接口实现。</p>
 *
 * <p>实现逐方法对齐主包 TrinketImpl（slotDataCache / getSlotData 逻辑一致），
 * 保证行为完全一致。</p>
 */
@Mixin(AccessoryItem.class)
public abstract class AddonTrinketBridgeMixin implements Trinket {

	@Unique
	private static final HashMap<Integer, AccessoryItem.SlotData> ssca$slotDataCache = new HashMap<>();

	@Unique
	private AccessoryItem.SlotData ssca$getSlotData(SlotReference slot) {
		if (ssca$slotDataCache.containsKey(slot.hashCode())) {
			return ssca$slotDataCache.get(slot.hashCode());
		}
		SlotType slotType = slot.inventory().getSlotType();
		AccessoryItem.SlotData data = new AccessoryItem.SlotData(
				new Identifier("trinket", "%s/%s".formatted(slotType.getGroup(), slotType.getName())), slot.index());
		ssca$slotDataCache.put(slot.hashCode(), data);
		return data;
	}

	@Inject(method = "accessoryInit", at = @At("HEAD"), cancellable = true, remap = false)
	private void ssca$registerTrinket(net.minecraft.item.Item.Settings settings, CallbackInfo ci) {
		AccessoryItem realThis = ((AccessoryItem) (Object) this);
		if (realThis instanceof Trinket trinket) {
			TrinketsApi.registerTrinket(realThis, trinket);
		}
	}

	@Override
	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
		((AccessoryItem) (Object) this).accessoryTick(stack, entity, ssca$getSlotData(slot));
	}

	@Override
	public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		((AccessoryItem) (Object) this).onEquip(stack, entity, ssca$getSlotData(slot));
	}

	@Override
	public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		((AccessoryItem) (Object) this).onUnequip(stack, entity, ssca$getSlotData(slot));
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return ((AccessoryItem) (Object) this).canEquip(stack, entity, ssca$getSlotData(slot));
	}

	@Override
	public boolean canUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return ((AccessoryItem) (Object) this).canUnequip(stack, entity, ssca$getSlotData(slot));
	}

	@Override
	public void onBreak(ItemStack stack, SlotReference slot, LivingEntity entity) {
		((AccessoryItem) (Object) this).onBreak(stack, entity, ssca$getSlotData(slot));
	}

	@Override
	public dev.emi.trinkets.api.TrinketEnums.DropRule getDropRule(ItemStack stack, SlotReference slot, LivingEntity entity) {
		AccessoryItem.DropRule dropRule = ((AccessoryItem) (Object) this).getDropRule(stack, entity, ssca$getSlotData(slot));
		return switch (dropRule) {
			case KEEP -> dev.emi.trinkets.api.TrinketEnums.DropRule.KEEP;
			case DROP -> dev.emi.trinkets.api.TrinketEnums.DropRule.DROP;
			case DESTROY -> dev.emi.trinkets.api.TrinketEnums.DropRule.DESTROY;
			default -> dev.emi.trinkets.api.TrinketEnums.DropRule.DEFAULT;
		};
	}
}
