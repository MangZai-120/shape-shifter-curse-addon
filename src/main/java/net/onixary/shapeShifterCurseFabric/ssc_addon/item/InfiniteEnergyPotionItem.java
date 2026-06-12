package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.PotionUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 无限压缩能量药水：使用后进入「空瓶」自充能状态，充能完成后自动恢复满瓶可再次使用。
 * 充能基于世界游戏时间戳（NBT 记录充满时刻），任意位置自动充能、无需 tick，跨维度一致。
 * 效果与压缩能量药水（feed_potion）完全一致：饮用施加 FEED_EFFECT；喷溅/滞留复用原版投掷药水行为。
 * 特意不继承 PotionItem（避免被“药水可堆叠”类模组对所有 PotionItem 放开堆叠而变得可叠加）；酿造通过主包数据驱动 dynamic_brewing_recipes 实现。
 */
public class InfiniteEnergyPotionItem extends Item {

	/** 三种形态及其充能时长。 */
	public enum Type {
		DRINK(200),     // 饮用：恢复 10 秒
		SPLASH(300),    // 喷溅：恢复 15 秒
		LINGERING(400); // 滞留：恢复 20 秒

		public final int rechargeTicks;

		Type(int ticks) {
			this.rechargeTicks = ticks;
		}
	}

	/** 充满时刻的世界游戏时间（game time）；存在且大于当前时间 = 空瓶充能中。 */
	private static final String NBT_FULL_AT = "FullAtTime";
	/** 饮用读条时长，与原版药水一致（32 tick）。 */
	private static final int DRINK_TIME = 32;

	private final Type type;

	public InfiniteEnergyPotionItem(Settings settings, Type type) {
		super(settings);
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	/** 是否处于空瓶充能中（基于世界游戏时间戳，任意位置自动充能，无需 tick）。 */
	public static boolean isRecharging(ItemStack stack, @Nullable World world) {
		if (world == null) {
			return false;
		}
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(NBT_FULL_AT)) {
			return false;
		}
		return world.getTime() < nbt.getLong(NBT_FULL_AT);
	}

	/** 仅凭 NBT 判断是否标记为空瓶（无世界对象时用于命名/物品栏显示；充满后由 inventoryTick 清除标记）。 */
	public static boolean isEmptyByNbt(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.contains(NBT_FULL_AT);
	}

	/** 标记一次使用：写入充满时刻 = 当前世界时间 + 充能时长（进入空瓶状态）。供本类与药水袋调用。 */
	public void markUsed(ItemStack stack, World world) {
		stack.getOrCreateNbt().putLong(NBT_FULL_AT, world.getTime() + type.rechargeTicks);
	}

	/** 生成原版投掷药水弹射物并携带压缩能量药水(feed_potion)，复用原版喷溅/滞留范围效果。仅服务端调用。 */
	public void spawnThrownPotion(World world, PlayerEntity user) {
		ItemStack thrown = PotionUtil.setPotion(
				new ItemStack(type == Type.LINGERING ? Items.LINGERING_POTION : Items.SPLASH_POTION),
				RegCustomPotions.FEED_POTION);
		PotionEntity entity = new PotionEntity(world, user);
		entity.setItem(thrown);
		entity.setVelocity(user, user.getPitch(), user.getYaw(), -20.0F, 0.5F, 1.0F);
		world.spawnEntity(entity);
	}

	/** 投掷音效（喷溅/滞留通用）。 */
	public static void playThrowSound(World world, PlayerEntity user) {
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS,
				0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		// 空瓶充能中：不可使用
		if (isRecharging(stack, world)) {
			return TypedActionResult.pass(stack);
		}
		if (type == Type.DRINK) {
			// 开始饮用读条
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}
		// 投掷型（喷溅/滞留）
		if (!world.isClient) {
			spawnThrownPotion(world, user);
		}
		// 双端均标记空瓶（world.getTime 双端同步），客户端即时显示空瓶材质
		markUsed(stack, world);
		playThrowSound(world, user);
		return TypedActionResult.success(stack);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof PlayerEntity player) {
			// 施加与压缩能量药水一致的效果（瞬时：+8 饱食 +0.6 饱和，使魔额外 +25 魔力）
			RegOtherStatusEffects.FEED_EFFECT.applyInstantEffect(player, player, player, 0, 1.0);
		}
		// 双端均标记空瓶，确保客户端立即显示空瓶材质与名称
		markUsed(stack, world);
		return stack;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return type == Type.DRINK ? DRINK_TIME : 0;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return type == Type.DRINK ? UseAction.DRINK : UseAction.NONE;
	}

	/**
	 * 若已充满则清除空瓶标记（NBT 内只剩它则整体清空），使名称/材质恢复满瓶。
	 * 供本类 inventoryTick 与药水袋显示清理共用（药水袋内存储物不走 inventoryTick，需主动清理）。
	 *
	 * @return 是否发生了清除
	 */
	public static boolean clearRechargeMarkIfDone(ItemStack stack, World world) {
		if (world == null) {
			return false;
		}
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.contains(NBT_FULL_AT) && world.getTime() >= nbt.getLong(NBT_FULL_AT)) {
			nbt.remove(NBT_FULL_AT);
			if (nbt.isEmpty()) {
				stack.setNbt(null);
			}
			return true;
		}
		return false;
	}

	/** 充满后清除空瓶标记，使物品栏内名称/材质恢复满瓶（任意位置自动充能由 isRecharging 时间戳保证）。 */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (!world.isClient) {
			clearRechargeMarkIfDone(stack, world);
		}
		super.inventoryTick(stack, world, entity, slot, selected);
	}

	/** 附魔光效。 */
	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	/** 满/空显示不同名称（自定义命名由 ItemStack#getName 在调用本方法前拦截）。 */
	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(this.getTranslationKey() + (isEmptyByNbt(stack) ? ".empty" : ""));
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("item.ssc_addon.infinite_energy_potion.tooltip").formatted(Formatting.GRAY));
		if (isRecharging(stack, world)) {
			NbtCompound nbt = stack.getNbt();
			long remain = nbt == null ? 0 : nbt.getLong(NBT_FULL_AT) - world.getTime();
			int sec = (int) Math.ceil(Math.max(0, remain) / 20.0);
			tooltip.add(Text.translatable("item.ssc_addon.infinite_energy_potion.tooltip.recharging", sec)
					.formatted(Formatting.AQUA));
		} else {
			tooltip.add(Text.translatable("item.ssc_addon.infinite_energy_potion.tooltip.ready")
					.formatted(Formatting.GREEN));
		}
		// 不调用 super.appendTooltip：PotionItem 会按空药水追加“无效果”提示
	}
}
