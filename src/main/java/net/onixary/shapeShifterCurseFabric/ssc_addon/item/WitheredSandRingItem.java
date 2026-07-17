package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枯沙指环 - SP金沙岚专属饰品（戒指槽）
 * 效果：侵蚀烙印的被动爆发与引爆伤害上限提高30%（20 → 26）
 * 获取途径：沙漠神殿战利品箱，15%概率
 */
public class WitheredSandRingItem extends TrinketItem {

	/** 服务端装备状态追踪：玩家UUID -> 最后一次tick的游戏时间 */
	private static final ConcurrentHashMap<UUID, Long> EQUIPPED_PLAYERS = new ConcurrentHashMap<>();

	public WitheredSandRingItem(Settings settings) {
		super(settings);
	}

	/**
	 * 注册到沙漠神殿战利品表（15%概率，1个）
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
			if (id.equals(new Identifier("minecraft", "chests/desert_pyramid"))) {
				LootPool.Builder poolBuilder = LootPool.builder()
						.rolls(ConstantLootNumberProvider.create(1.0F))
						.conditionally(RandomChanceLootCondition.builder(0.15F))
						.with(ItemEntry.builder(SscAddon.WITHERED_SAND_RING));
				tableBuilder.pool(poolBuilder);
			}
		});
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
	 * 检查玩家是否装备了枯沙指环（基于tick回调追踪，比isEquipped API更可靠）
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
		tooltip.add(Text.translatable("item.ssc_addon.withered_sand_ring.tooltip_1").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.ssc_addon.withered_sand_ring.tooltip_2").formatted(Formatting.GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
