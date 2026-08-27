package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.List;

/**
 * 凋零药水（自定义可堆叠药水，饮用 / 喷溅 / 滞留 三型）。
 *
 * 设计（用户 2026-07-04 确认）：
 * - 任何人都能饮用 / 投掷；制作基础饮用型仍需 SP阿努比斯（item_on_item：凋零玫瑰 + 药水 → 1 瓶）。
 * - 效果：固定凋零 II（amplifier 1），20 秒。饮用型作用于自己；喷溅 / 滞留型投掷后对范围内生物 AOE。
 * - 酿造：饮用型 + 火药 → 喷溅型；喷溅型 + 龙息 → 滞留型（见 BrewingRegistryInfiniteMixin）。
 * - 瓶身带附魔光效（hasGlint）。可叠 3 瓶（maxCount 由注册处控制）。
 * - 不继承 PotionItem，避免被"药水可堆叠"类模组误伤。
 */
public class WitherPotionItem extends Item {

	/** 凋零等级（amplifier 1 = 凋零 II） */
	private static final int WITHER_AMPLIFIER = 1;
	/** 凋零时长（tick）= 20秒 */
	private static final int WITHER_DURATION = 400;
	/** 饮用读条时长，与原版药水一致（32 tick） */
	private static final int DRINK_TIME = 32;
	/** 瓶身 / AOE 着色（凋零暗褐） */
	private static final int POTION_COLOR = 0x4A403A;

	public enum Type {
		DRINK, SPLASH, LINGERING
	}

	private final Type type;

	public WitherPotionItem(Settings settings, Type type) {
		super(settings);
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	/**
	 * 按玩家形态返回凋零药水堆叠上限：使魔系 8 / SP阿努比斯 3 / 其它 1。
	 * 供 Slot.getMaxItemCount / 创造中键复制 / 双击合并 等多处统一调用。
	 */
	public static int getStackLimitFor(PlayerEntity player) {
		if (player == null) {
			return 1;
		}
		if (FormUtils.isFamiliarFoxFamily(player)) {
			return 8;
		}
		if (FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) {
			return 3;
		}
		return 1;
	}

	/** 瓶身附魔光效。 */
	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return type == Type.DRINK ? DRINK_TIME : 0;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return type == Type.DRINK ? UseAction.DRINK : UseAction.NONE;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (type == Type.DRINK) {
			// 饮用型：起手读条，效果在 finishUsing 施加
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}
		// 喷溅 / 滞留型：投掷原版药水弹射物（携带凋零效果），复用原版 AOE / 地面云
		if (!world.isClient) {
			spawnThrownPotion(world, user);
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS,
				0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!user.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (type != Type.DRINK || !(user instanceof PlayerEntity player)) {
			return super.finishUsing(stack, world, user);
		}
		if (!world.isClient) {
			// 固定凋零 II（amplifier 1），20 秒（400t）作用于饮用者自己
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER));
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.PLAYERS, 0.6f, 1.0f);
		}
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		player.incrementStat(Stats.USED.getOrCreateStat(this));
		return stack;
	}

	/** 生成携带凋零效果的原版投掷药水（喷溅 / 滞留），复用原版 AOE / 地面云机制。仅服务端调用。 */
	private void spawnThrownPotion(World world, PlayerEntity user) {
		ItemStack thrown = new ItemStack(type == Type.LINGERING ? Items.LINGERING_POTION : Items.SPLASH_POTION);
		PotionUtil.setCustomPotionEffects(thrown, List.of(
				new StatusEffectInstance(StatusEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER)));
		thrown.getOrCreateNbt().putInt("CustomPotionColor", POTION_COLOR);
		PotionEntity entity = new PotionEntity(world, user);
		entity.setItem(thrown);
		entity.setVelocity(user, user.getPitch(), user.getYaw(), -20.0F, 0.5F, 1.0F);
		world.spawnEntity(entity);
	}
}
