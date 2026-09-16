package net.jackcooper.shapeShifterCurseAddon.item;

import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBarDef;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.effect.UniversalEnergyEffect;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 通用能量药水（jackcooper）：饮用回复 25 点魔力，判定逻辑与 SSC 压缩能量药水（feed_potion）同源。
 *
 * <p>持有 SSCA 资源条（悦灵 mana / 蝙蝠血 / 阿努比斯灵魂 / 雪狐寒霜）的形态回复对应资源条
 * （恒 25）；寄生果蝠的种子条上限仅 10，固定回复 2 点；使魔 / 蜘蛛系 / 契灵（原版
 * ManaComponent 体系，契灵挂 familiar_fox_mana 的 mana_type_power）回复标准 mana 条——与
 * 压缩能量药水的 familiar_fox_mana 判定同源，但数值恒为 25 且不加饥饿（压缩能量药水饮用
 * 另 +8 饥饿 / +0.6 饱和）。朔望与无能量体系的形态（人类）饮用只消耗药水不回复，并动作栏提示。
 *
 * <p>实现参照项目内 {@code InfiniteEnergyPotionItem}：不继承 PotionItem（避免可堆叠类模组放开叠加），
 * finishUsing 服务端判定后回复，饮毕返还空玻璃瓶。
 * 由能量装瓶器产出（{@code EnergyBottlerBlockEntity.makeEnergyBottle}）；
 * 喷溅 / 滞留型由酿造台转换（饮用+火药→喷溅；喷溅+龙息→滞留，见 BrewingStandInfinitePotionMixin），
 * 投掷时生成携带 {@link UniversalEnergyEffect} 的原版投掷药水弹射物，落点 AOE 回复（距离衰减下限 0.5）。
 */
public class UniversalEnergyPotionItem extends Item {

	/** 回复的 mana 点数。 */
	public static final double MANA_RESTORE = 25.0;

	/** 三种瓶型。 */
	public enum Type {
		DRINK, SPLASH, LINGERING
	}

	/** 饮用读条时长，与原版药水一致（32 tick）。 */
	private static final int DRINK_TIME = 32;

	private final Type type;

	public UniversalEnergyPotionItem(Settings settings, Type type) {
		super(settings);
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	/**
	 * 是否可从此药水受益（判定思路与压缩能量药水同源，不再依赖手动挂的标记 power）：
	 * 持有任意 SSCA 资源条（悦灵 mana / 蝙蝠血 / 阿努比斯灵魂 / 雪狐寒霜 / 果蝠种子），
	 * 或身上存在原版 mana 类型（ManaComponent 非空——使魔系 familiar_fox_mana /
	 * 蜘蛛系 spider_mana 的所有阶段自动覆盖，含原版使魔 2/3 阶、红使魔、进化使魔、
	 * SP 使魔、月织蛛、契灵等）。朔望（ocelot_nova）无能量体系，但切形态后
	 * ManaComponent 可能残留上一形态的 mana_type 导致误判，显式排除。
	 */
	public static boolean canRestore(LivingEntity entity) {
		if (!(entity instanceof PlayerEntity player)) {
			return false;
		}
		// SSCA 资源条形态：持有哪条回复哪条
		for (ResourceBarDef bar : BarKeys.ALL) {
			if (ResourceBars.has(player, bar)) {
				return true;
			}
		}
		// 朔望（ocelot_nova）：无能量体系；ManaComponent 若残留旧形态 mana_type 会误判，显式排除
		if (net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(
				entity, net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_NOVA)) {
			return false;
		}
		// 原版 mana 体系兜底：有任何 mana_type 即可（仅玩家有 ManaComponent；契灵走此分支）
		try {
			return net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerManaTypeID(player) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 回复 mana：依次检查各 apoli resource 型能量条（悦灵 mana / 蝙蝠血 / 阿努比斯灵魂 / 雪狐寒霜），
	 * 持有哪个就给哪个加值（clamp 到各自 max）；都不是则走原版 ManaComponent（使魔系标准 mana 条）。
	 * 全部经统一门面 {@link ResourceBars}（SSCA-ResourceKit）。
 * 回复量按 scale 缩放（饮用=1.0；喷溅 / 滞留按距离衰减 ≥0.5）。
 */
	public static void restoreManaScaled(net.minecraft.server.network.ServerPlayerEntity player, double scale) {
		double amount = MANA_RESTORE * scale;
		// 寄生果蝠：种子条上限仅 10，固定回 2 点（喝一瓶约 1/5 条，与其它形态 25 点的体感比例相当）
		if (ResourceBars.has(player, BarKeys.SEED)) {
			ResourceBars.gain(player, BarKeys.SEED, (int) Math.max(1, Math.round(2 * scale)));
			return;
		}
		for (ResourceBarDef bar : BarKeys.ALL) {
			if (ResourceBars.has(player, bar)) {
				ResourceBars.gain(player, bar, (int) Math.round(amount));
				return;
			}
		}
		// 标准型：原版 ManaComponent（gainMana 内部 clamp 到 max；含契灵的 familiar_fox_mana）
		net.onixary.shapeShifterCurseFabric.mana.ManaUtils.gainPlayerMana(player, amount);
	}

	private static void restoreMana(net.minecraft.server.network.ServerPlayerEntity player) {
		restoreManaScaled(player, 1.0);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			if (canRestore(user)) {
				// 与压缩能量药水一致：仅回复，无额外完成音效（饮用音由 UseAction.DRINK 原生提供）
				restoreMana(serverPlayer);
			} else {
				// 无门控 power：不回复，动作栏提示（走 lang key）
				serverPlayer.sendMessage(Text.translatable("message.ssc_addon.universal_potion.no_effect"), true);
			}
		}
		if (user instanceof PlayerEntity playerEntity) {
			playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
			if (!playerEntity.getAbilities().creativeMode) {
				stack.decrement(1);
				ItemStack glassBottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!playerEntity.getInventory().insertStack(glassBottle)) {
					playerEntity.dropItem(glassBottle, false);
				}
			}
		} else {
			stack.decrement(1);
		}
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

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (type == Type.DRINK) {
			// 饮用型：起手读条，效果在 finishUsing 服务端结算
			return net.minecraft.item.ItemUsage.consumeHeldItem(world, user, hand);
		}
		// 喷溅 / 滞留型：投掷携带通用能量效果的原版药水弹射物，复用原版 AOE / 滞留云机制（仅服务端生成）
		if (!world.isClient) {
			spawnThrownPotion(world, user);
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS,
				0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!user.getAbilities().creativeMode) {
			user.getStackInHand(hand).decrement(1);
		}
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
	}

	/** 生成携带通用能量效果的原版投掷药水（喷溅 / 滞留）。仅服务端调用。 */
	private void spawnThrownPotion(World world, PlayerEntity user) {
		ItemStack thrown = PotionUtil.setPotion(
				new ItemStack(type == Type.LINGERING ? Items.LINGERING_POTION : Items.SPLASH_POTION),
				net.jackcooper.shapeShifterCurseAddon.SscAddon.UNIVERSAL_ENERGY_POTION_TYPE);
		thrown.getOrCreateNbt().putInt("CustomPotionColor", UniversalEnergyEffect.POTION_COLOR);
		PotionEntity entity = new PotionEntity(world, user);
		entity.setItem(thrown);
		entity.setVelocity(user, user.getPitch(), user.getYaw(), -20.0F, 0.5F, 1.0F);
		world.spawnEntity(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(Text.translatable("tooltip.ssc_addon.universal_potion").formatted(net.minecraft.util.Formatting.AQUA));
	}
}
