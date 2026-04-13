package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蚀沙棱晶 - SP金沙岚专属饰品（项链槽）
 * 效果：引爆烙印时将1层烙印扩散给被引爆目标周围5格内的其它目标
 * 副作用：烙印叠加冷却从1秒变为1.3秒
 * 扩散内置CD：5秒
 */
public class ErosionSandPrismItem extends TrinketItem {

	/** 服务端装备状态追踪：玩家UUID -> 最后一次tick的游戏时间 */
	private static final ConcurrentHashMap<UUID, Long> EQUIPPED_PLAYERS = new ConcurrentHashMap<>();

	public ErosionSandPrismItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isGoldenSandstormSP(entity);
	}

	@Override
	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof ServerPlayerEntity player) {
			EQUIPPED_PLAYERS.put(player.getUuid(), entity.getWorld().getTime());
		}
	}

	@Override
	public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof ServerPlayerEntity player) {
			EQUIPPED_PLAYERS.put(player.getUuid(), entity.getWorld().getTime());
		}
	}

	@Override
	public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof ServerPlayerEntity) {
			EQUIPPED_PLAYERS.remove(entity.getUuid());
		}
	}

	/**
	 * 检查玩家是否装备了蚀沙棱晶（基于tick回调追踪，比isEquipped API更可靠）
	 */
	public static boolean isEquippedBy(ServerPlayerEntity player) {
		Long lastTick = EQUIPPED_PLAYERS.get(player.getUuid());
		if (lastTick == null) return false;
		// 超过3tick未更新视为已卸下（容错）
		return Math.abs(player.getWorld().getTime() - lastTick) <= 3;
	}

	/** 清理玩家数据（退出/切换形态时调用） */
	public static void clearPlayer(UUID uuid) {
		EQUIPPED_PLAYERS.remove(uuid);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.erosion_sand_prism.tooltip_1").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.ssc_addon.erosion_sand_prism.tooltip_2").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.erosion_sand_prism.tooltip_3").formatted(Formatting.RED));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
