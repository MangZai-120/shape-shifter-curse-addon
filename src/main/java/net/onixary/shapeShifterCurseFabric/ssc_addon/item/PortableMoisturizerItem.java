package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.TrinketUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 便携加湿器 —— 美西螈系专属腰带饰品（可升级 1/2/3 级）。
 * 佩戴 + 湿润度(air)未满时自动回复；淋雨露天时暂停回复/消耗、每 2 秒回充 1 秒使用时间；
 * 三级佩戴额外提供全伤害 +15%（伤害侧由 SscAddonLivingEntityMixin 判定）。
 */
public class PortableMoisturizerItem extends AccessoryItem {

	public static final int MAX_LEVEL = 3;

	public PortableMoisturizerItem(Settings settings) {
		super(settings);
	}

	/** 各级使用时间上限（charge，1 charge = 1 秒）：一级 5400、二级 8100(+50%)、三级 10800(+100%)。 */
	public static int getMaxCharge(int level) {
		return switch (level) {
			case 2 -> 8100;
			case 3 -> 10800;
			default -> 5400;
		};
	}

	/** 各级每秒湿润度(air)回复占最大值比例：一级 2%、二级 3%(+50%)、三级 4%(+100%)。 */
	private static double getRecoveryRate(int level) {
		return switch (level) {
			case 2 -> 0.03;
			case 3 -> 0.04;
			default -> 0.02;
		};
	}

	// ===== NBT =====
	public static int getLevel(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains("Level")) return 1;
		return Math.max(1, Math.min(MAX_LEVEL, nbt.getInt("Level")));
	}

	public static void setLevel(ItemStack stack, int level) {
		stack.getOrCreateNbt().putInt("Level", Math.max(1, Math.min(MAX_LEVEL, level)));
	}

	public static int getCharge(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt == null ? 0 : nbt.getInt("Charge");
	}

	public static void setCharge(ItemStack stack, int charge) {
		int max = getMaxCharge(getLevel(stack));
		stack.getOrCreateNbt().putInt("Charge", Math.max(0, Math.min(charge, max)));
	}

	/** 配方充满：按当前等级上限充满使用时间。 */
	public static void setFullCharge(ItemStack stack) {
		stack.getOrCreateNbt().putInt("Charge", getMaxCharge(getLevel(stack)));
	}

	// ===== 装备限制：仅美西螈系可佩戴 =====
	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity, FormUtils::isMoistureDependent);
	}

	// ===== 右键：显示当前状态（饰品自动生效，无需开关） =====
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (!world.isClient) {
			int level = getLevel(stack);
			player.sendMessage(Text.translatable("message.ssc_addon.moisturizer.info", level,
					formatTime(getCharge(stack)), formatTime(getMaxCharge(level))), true);
		}
		return TypedActionResult.success(stack);
	}

	// ===== 佩戴时每 tick 逻辑（服务端） =====
	@Override
	public void accessoryTick(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		if (entity.getWorld().isClient || !(entity instanceof PlayerEntity player)) return;
		if (!FormUtils.isMoistureDependent(player)) return;

		int level = getLevel(stack);

		// 淋雨露天：湿润度由 rain_wetness 回复 → 暂停加湿器回复与消耗；每 2 秒回充 1 秒使用时间
		if (player.getWorld().hasRain(player.getBlockPos())) {
			if (player.age % 40 == 0) {
				int charge = getCharge(stack);
				if (charge < getMaxCharge(level)) setCharge(stack, charge + 1);
			}
			return;
		}

		// 非淋雨：湿润度未满且有充能时，每秒回复并消耗 1 充能
		if (player.age % 20 == 0) {
			int charge = getCharge(stack);
			int maxAir = player.getMaxAir();
			int air = player.getAir();
			if (charge > 0 && air < maxAir) {
				int recovery = (int) Math.ceil(maxAir * getRecoveryRate(level));
				player.setAir(Math.min(air + recovery, maxAir));
				setCharge(stack, charge - 1);
			}
		}
	}

	/** 攻击者是否佩戴了三级加湿器（供伤害 mixin 判定全伤害 +15%，服务端调用；框架无关）。 */
	public static boolean isLevel3Equipped(LivingEntity entity) {
		if (entity == null) return false;
		return TrinketUtils.isWearing(entity, stack ->
				stack.getItem() == SscAddon.PORTABLE_MOISTURIZER && getLevel(stack) >= 3);
	}

	// ===== 物品充能条 =====
	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return Math.round(13.0f * getCharge(stack) / getMaxCharge(getLevel(stack)));
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return 0x31C8CC; // 青蓝色（水/湿润）
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		int level = getLevel(stack);
		tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.level", level).formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.charge",
				formatTime(getCharge(stack)), formatTime(getMaxCharge(level))).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.usage").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.rain").formatted(Formatting.DARK_AQUA));
		if (level >= 3) {
			tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.damage").formatted(Formatting.RED));
		}
		tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.refill").formatted(Formatting.DARK_GRAY));
		tooltip.add(Text.translatable("tooltip.ssc_addon.moisturizer.exclusive").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, world, tooltip, context);
	}

	private static String formatTime(int seconds) {
		return String.format("%02d:%02d", seconds / 60, seconds % 60);
	}
}
