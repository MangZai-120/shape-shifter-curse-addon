package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.PotionUtil;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.screen.PotionBagScreenHandler;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;

import java.util.List;

public class PotionBagItem extends Item {

	/** 快捷投放栏槽位索引（药水包最左侧槽位）。 */
	private static final int QUICK_SLOT = 0;
	/** 投掷型药水的冷却：5 秒（100 tick）。基于药水袋 NBT 世界时间戳，不用 ItemCooldownManager（后者会让 interactItem 直接 PASS，导致冷却期间连药水袋都打不开）。 */
	private static final int THROW_COOLDOWN = 100;
	/** 药水袋投掷冷却结束时刻的 NBT 键（世界 game time）。 */
	private static final String NBT_THROW_CD = "ThrowCooldownEnd";
	/** 饮用型药水的饮用读条时长：比原版直接喝（32 tick）长 15% ≈ 37 tick。 */
	private static final int DRINK_TIME = 37;

	public PotionBagItem(Settings settings) {
		super(settings.maxCount(1));
	}

	/** 药水袋投掷冷却是否未结束（基于世界时间戳，不会阻止 use 被调用，故不影响潜行开包）。 */
	private static boolean isThrowOnCooldown(ItemStack bagStack, World world) {
		NbtCompound nbt = bagStack.getNbt();
		if (nbt == null || !nbt.contains(NBT_THROW_CD)) {
			return false;
		}
		return world.getTime() < nbt.getLong(NBT_THROW_CD);
	}

	/** 设置药水袋投掷冷却：结束时刻 = 当前世界时间 + 5 秒。 */
	private static void setThrowCooldown(ItemStack bagStack, World world) {
		bagStack.getOrCreateNbt().putLong(NBT_THROW_CD, world.getTime() + THROW_COOLDOWN);
	}

	/** 是否为无限压缩能量药水（任意形态）。 */
	private static boolean isInfinite(ItemStack stack) {
		return stack.getItem() instanceof InfiniteEnergyPotionItem;
	}

	/** 投掷型（溅射/滞留），含无限药水的喷溅/滞留形态。 */
	private static boolean isThrowable(ItemStack stack) {
		if (stack.getItem() instanceof SplashPotionItem || stack.getItem() instanceof LingeringPotionItem) {
			return true;
		}
		return stack.getItem() instanceof InfiniteEnergyPotionItem inf
				&& inf.getType() != InfiniteEnergyPotionItem.Type.DRINK;
	}

	/** 饮用型（普通药水或无限药水的饮用形态，排除溅射/滞留）。 */
	private static boolean isDrinkable(ItemStack stack) {
		if (stack.getItem() instanceof InfiniteEnergyPotionItem inf) {
			return inf.getType() == InfiniteEnergyPotionItem.Type.DRINK;
		}
		return !stack.isEmpty() && stack.getItem() instanceof PotionItem && !isThrowable(stack);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		// 潜行 + 右键：打开药水包 GUI
		if (user.isSneaking()) {
			if (!world.isClient) {
				user.openHandledScreen(new NamedScreenHandlerFactory() {
					@Override
					public Text getDisplayName() {
						return Text.translatable("item.ssc_addon.potion_bag");
					}

					@Override
					public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
						return new PotionBagScreenHandler(syncId, inv, stack);
					}
				});
			}
			return TypedActionResult.success(stack);
		}

		// 普通右键：快捷投放栏（最左侧槽位）快速使用一瓶药水
		ItemStack potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT);
		if (potion.isEmpty()) {
			// 快捷栏已空，无法继续使用（不摆手、不消耗）
			return TypedActionResult.pass(stack);
		}

		if (isThrowable(potion)) {
			if (isInfinite(potion)) {
				// 无限药水：仅由自身空瓶充能门控，不占用药水袋投掷冷却
				if (InfiniteEnergyPotionItem.isRecharging(potion, world)) {
					return TypedActionResult.fail(stack);
				}
				if (!world.isClient) {
					throwPotion(world, user, stack, potion);
				}
				return TypedActionResult.success(stack);
			}
			// 普通投掷药水：药水袋自身 5 秒投掷冷却（基于 NBT 世界时间戳，不会阻止潜行开包）
			if (isThrowOnCooldown(stack, world)) {
				return TypedActionResult.fail(stack);
			}
			if (!world.isClient) {
				throwPotion(world, user, stack, potion);
			}
			// 双端均设置冷却时间戳（world.getTime 双端同步），避免客机连点重复发包
			setThrowCooldown(stack, world);
			return TypedActionResult.success(stack);
		}

		// 饮用型：无限药水空瓶充能中则不可饮用
		if (isInfinite(potion) && InfiniteEnergyPotionItem.isRecharging(potion, world)) {
			return TypedActionResult.fail(stack);
		}
		// 开始饮用读条（DRINK 动作 + getMaxUseTime），无冷却
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	/**
	 * 饮用读条完成：施加药水效果并消耗 1 瓶，返还空玻璃瓶；药水袋本身不被消耗。
	 * 仅服务端结算，多人主客机一致。
	 */
	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof PlayerEntity player) {
			ItemStack potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT);
			// 无限压缩能量药水（饮用型）：施加 feed_effect，标记空瓶充能，不消耗数量、不返还玻璃瓶
			if (potion.getItem() instanceof InfiniteEnergyPotionItem inf
					&& inf.getType() == InfiniteEnergyPotionItem.Type.DRINK) {
				if (!InfiniteEnergyPotionItem.isRecharging(potion, world)) {
					RegOtherStatusEffects.FEED_EFFECT.applyInstantEffect(player, player, player, 0, 1.0);
					inf.markUsed(potion, world);
					PotionBagScreenHandler.setStoredStack(stack, QUICK_SLOT, potion);
				}
			} else if (isDrinkable(potion)) {
				// 施加药水效果（瞬时效果立即结算，持续效果加为状态）
				for (StatusEffectInstance effect : PotionUtil.getPotionEffects(potion)) {
					if (effect.getEffectType().isInstant()) {
						effect.getEffectType().applyInstantEffect(player, player, player, effect.getAmplifier(), 1.0);
					} else {
						player.addStatusEffect(new StatusEffectInstance(effect));
					}
				}
				// 正常饮用返还空玻璃瓶（创造模式不返还；背包满则掉落）
				if (!player.getAbilities().creativeMode) {
					ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
					if (!player.getInventory().insertStack(bottle)) {
						player.dropItem(bottle, false);
					}
				}
				// 消耗 1 瓶并写回药水包存储
				potion.decrement(1);
				PotionBagScreenHandler.setStoredStack(stack, QUICK_SLOT, potion);
			}
		}
		return stack; // 药水袋本身不被消耗
	}

	/** 饮用读条时长：仅当快捷栏为饮用型药水时返回 {@link #DRINK_TIME}，否则 0（投掷型/空为即时/无动作）。 */
	@Override
	public int getMaxUseTime(ItemStack stack) {
		return isDrinkable(PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT)) ? DRINK_TIME : 0;
	}

	/** 饮用型药水显示喝药动作（含原版饮用粒子与音效），否则无动作。 */
	@Override
	public UseAction getUseAction(ItemStack stack) {
		return isDrinkable(PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT)) ? UseAction.DRINK : UseAction.NONE;
	}

	/**
	 * 投掷一瓶药水：生成投掷药水弹射物（setItem 让弹射物渲染使用对应药水的纹理），
	 * 并消耗 1 瓶写回药水包存储。仅服务端调用。
	 */
	private void throwPotion(World world, PlayerEntity user, ItemStack bagStack, ItemStack potion) {
		// 无限压缩能量药水（喷溅/滞留型）：复用其自身投掷逻辑，标记空瓶充能而非消耗数量
		if (potion.getItem() instanceof InfiniteEnergyPotionItem inf) {
			InfiniteEnergyPotionItem.playThrowSound(world, user);
			inf.spawnThrownPotion(world, user);
			inf.markUsed(potion, world);
			PotionBagScreenHandler.setStoredStack(bagStack, QUICK_SLOT, potion);
			return;
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS,
				0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
		PotionEntity potionEntity = new PotionEntity(world, user);
		potionEntity.setItem(potion.copyWithCount(1));
		potionEntity.setVelocity(user, user.getPitch(), user.getYaw(), -20.0F, 0.5F, 1.0F);
		world.spawnEntity(potionEntity);
		potion.decrement(1);
		PotionBagScreenHandler.setStoredStack(bagStack, QUICK_SLOT, potion);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.potion_bag.tooltip").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.potion_bag.tooltip.controls").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}
}
