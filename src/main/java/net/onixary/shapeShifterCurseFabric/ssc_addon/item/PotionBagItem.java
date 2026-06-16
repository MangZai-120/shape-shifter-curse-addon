package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
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
	/**
	 * 普通投掷药水的投掷间隔冷却：5 秒（100 tick）。记录在药水袋 NBT（{@link #NBT_THROW_END}），
	 * 不直接占用 ItemCooldownManager —— 白色遮罩统一由 {@link #inventoryTick} 按「快捷栏药水自身剩余冷却」
	 * 同步，使遮罩长度与所投药水一致（普通药水=投掷间隔；无限药水=各形态充能时长），换药水时自动重置。
	 */
	private static final int THROW_COOLDOWN = 100;
	/** 饮用型药水的饮用读条时长：比原版直接喝（32 tick）长 15% ≈ 37 tick。 */
	private static final int DRINK_TIME = 37;
	/** 药水袋 NBT：普通投掷药水的投掷冷却结束世界时间（game time）。 */
	private static final String NBT_THROW_END = "ThrowEndTime";
	/** 药水袋 NBT：冷却遮罩同步令牌（= 当前快捷栏药水冷却结束的世界时间，0 表示无冷却）。 */
	private static final String NBT_CD_TOKEN = "CdToken";

	public PotionBagItem(Settings settings) {
		super(settings.maxCount(1));
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
				// 无限药水：仅由自身空瓶充能门控；白色遮罩由 inventoryTick 按其充能时长同步
				if (InfiniteEnergyPotionItem.isRecharging(potion, world)) {
					return TypedActionResult.fail(stack);
				}
				if (!world.isClient) {
					throwPotion(world, user, stack, potion); // 内部 markUsed → 写 FullAtTime
				}
				return TypedActionResult.success(stack);
			}
			// 普通投掷药水：药水袋自身投掷间隔冷却（记录在药水袋 NBT，双端一致）
			if (isThrowCoolingDown(stack, world)) {
				return TypedActionResult.fail(stack);
			}
			if (!world.isClient) {
				throwPotion(world, user, stack, potion);
			}
			// 双端记录投掷冷却结束时间（world.getTime 双端同步）；白色遮罩由 inventoryTick 同步
			stack.getOrCreateNbt().putLong(NBT_THROW_END, world.getTime() + THROW_COOLDOWN);
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

	/** 普通投掷药水是否仍在投掷间隔冷却中（基于药水袋 NBT 记录的结束世界时间）。 */
	private static boolean isThrowCoolingDown(ItemStack bag, World world) {
		NbtCompound nbt = bag.getNbt();
		return nbt != null && nbt.contains(NBT_THROW_END) && world.getTime() < nbt.getLong(NBT_THROW_END);
	}

	/**
	 * 每 tick 把「快捷栏槽位药水自身的剩余冷却」同步到药水袋的 ItemCooldownManager 白色遮罩：
	 * <ul>
	 *   <li>无限药水 → 其空瓶充能结束时间（各形态时长不同：饮用 10s / 喷溅 15s / 滞留 20s）</li>
	 *   <li>普通投掷药水 → 药水袋记录的投掷间隔冷却结束时间（5s）</li>
	 *   <li>其它（普通可饮用药水/空）→ 无冷却</li>
	 * </ul>
	 * 以「冷却结束的世界时间」作为同步令牌（{@link #NBT_CD_TOKEN}），仅当令牌变化（换药水 / 再次使用）时
	 * 才重新 set/remove 遮罩，避免每 tick 重置导致遮罩卡满。仅服务端执行，set 会自动同步到客户端遮罩。
	 */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!world.isClient && entity instanceof PlayerEntity player) {
			ItemStack potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT);
			long time = world.getTime();
			long endTime = 0L;
			if (potion.getItem() instanceof InfiniteEnergyPotionItem) {
				endTime = InfiniteEnergyPotionItem.getRechargeEndTime(potion);
			} else if (isThrowable(potion)) {
				NbtCompound nbt = stack.getNbt();
				if (nbt != null && nbt.contains(NBT_THROW_END)) {
					endTime = nbt.getLong(NBT_THROW_END);
				}
			}
			long token = endTime > time ? endTime : 0L; // 已过期视为无冷却
			long lastToken = stack.getNbt() != null ? stack.getNbt().getLong(NBT_CD_TOKEN) : 0L;
			if (token != lastToken) {
				stack.getOrCreateNbt().putLong(NBT_CD_TOKEN, token);
				if (token > 0L) {
					player.getItemCooldownManager().set(this, (int) (token - time));
				} else {
					player.getItemCooldownManager().remove(this);
				}
			}
		}
		super.inventoryTick(stack, world, entity, slot, selected);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.potion_bag.tooltip").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.potion_bag.tooltip.controls").formatted(Formatting.DARK_GRAY));
		super.appendTooltip(stack, world, tooltip, context);
	}

	/**
	 * 物品名实时反映快捷投放栏（槽位 0）的药水：空时显示「药水包（空）」，有药水时显示「药水包（药水名）」。
	 * 名称取自快捷栏药水自身的 {@link ItemStack#getName()}（普通药水含药效后缀，无限药水为其本地名）。
	 */
	@Override
	public Text getName(ItemStack stack) {
		ItemStack potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT);
		if (potion.isEmpty()) {
			return Text.translatable("item.ssc_addon.potion_bag.empty");
		}
		return Text.translatable("item.ssc_addon.potion_bag.named", potion.getName());
	}

	// ====== 快捷收纳：在其它界面把药水直接放入药水袋（交互逻辑与原版收纳袋一致，优先放入非快捷消耗栏） ======

	/** 药水袋槽位总数（与 {@link PotionBagScreenHandler} 一致：槽位 0 为快捷投放栏，1-8 为非快捷消耗栏）。 */
	private static final int BAG_SLOTS = 9;

	/** 是否为可放入药水袋的药水类物品（与药水包 GUI 的可放入规则一致）。 */
	public static boolean isStorable(ItemStack stack) {
		return stack.getItem() instanceof PotionItem
				|| stack.getItem() instanceof SplashPotionItem
				|| stack.getItem() instanceof LingeringPotionItem
				|| stack.getItem() instanceof InfiniteEnergyPotionItem;
	}

	/** 单格最大堆叠：无限能量药水每格 1，其余药水每格 8（与药水包 GUI 槽位规则一致）。 */
	private static int maxPerSlot(ItemStack stack) {
		return stack.getItem() instanceof InfiniteEnergyPotionItem ? 1 : 8;
	}

	/**
	 * 把 {@code source} 中的药水尽量放进药水袋并扣减其数量，返回实际放入的数量。
	 * 放置优先级：先填非快捷消耗栏（槽位 1-8），快捷投放栏（槽位 0）仅在前者放不下时兜底；
	 * 各区间内均先合并到同类未满堆叠，再占用空槽位。堆叠规则与药水包 GUI 一致（普通药水每格 8、无限能量药水每格 1）。
	 */
	public static int insertIntoBag(ItemStack bag, ItemStack source) {
		if (source.isEmpty() || !isStorable(source)) {
			return 0;
		}
		int perSlot = maxPerSlot(source);
		ItemStack[] slots = new ItemStack[BAG_SLOTS];
		for (int i = 0; i < BAG_SLOTS; ++i) {
			slots[i] = PotionBagScreenHandler.getStoredStack(bag, i);
		}
		// 优先非快捷消耗栏（槽位 1-8），快捷投放栏（槽位 0）兜底
		int inserted = fillRange(slots, source, perSlot, 1, BAG_SLOTS);
		inserted += fillRange(slots, source, perSlot, QUICK_SLOT, QUICK_SLOT + 1);
		if (inserted > 0) {
			for (int i = 0; i < BAG_SLOTS; ++i) {
				PotionBagScreenHandler.setStoredStack(bag, i, slots[i]);
			}
		}
		return inserted;
	}

	/** 在 {@code [from, to)} 槽位区间内放入药水：先合并到同类未满堆叠、再占用空槽位；扣减 {@code source} 并返回放入数量。 */
	private static int fillRange(ItemStack[] slots, ItemStack source, int perSlot, int from, int to) {
		int inserted = 0;
		for (int i = from; i < to && !source.isEmpty(); ++i) {
			ItemStack slot = slots[i];
			if (!slot.isEmpty() && slot.getCount() < perSlot && ItemStack.canCombine(slot, source)) {
				int add = Math.min(perSlot - slot.getCount(), source.getCount());
				slot.increment(add);
				source.decrement(add);
				inserted += add;
			}
		}
		for (int i = from; i < to && !source.isEmpty(); ++i) {
			if (slots[i].isEmpty()) {
				int add = Math.min(perSlot, source.getCount());
				slots[i] = source.copyWithCount(add);
				source.decrement(add);
				inserted += add;
			}
		}
		return inserted;
	}
}
