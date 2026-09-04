package net.jackcooper.shapeShifterCurseAddon;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionManager;
import net.jackcooper.shapeShifterCurseAddon.util.AdvancementUtils;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.compat.ssc192.Compat1_9_2;

public class EvolutionStoneItem extends Item {

	public EvolutionStoneItem(Settings settings) {
		super(settings);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 32;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		user.setCurrentHand(hand);
		return TypedActionResult.consume(user.getStackInHand(hand));
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient && user instanceof PlayerEntity player) {
			Identifier playerFormID = getPlayerFormID(player);

			Identifier targetFormId = null;
			boolean canEvolve = false;

			if (playerFormID != null) {
				// Allow Wild Cat (Feral Cat SP) to evolve to Wild Cat SP
				if (playerFormID.equals(new Identifier("shape-shifter-curse", "feral_cat_sp"))) {
					targetFormId = new Identifier("my_addon", "wild_cat_sp");
					canEvolve = true;
				}
				// Allow Snow Fox 3 (permanent phase) to evolve to Snow Fox SP
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "snow_fox_3"))) {
					targetFormId = new Identifier("my_addon", "snow_fox_sp");
					canEvolve = true;
				}
				// Allow Allay to evolve to SP Allay
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "allay_sp"))) {
					targetFormId = net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.ALLAY_SP;
					canEvolve = true;
				}
				// 允许原版三阶段胡狼使用进化石进化为金沙岚
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "anubis_wolf_3"))) {
					targetFormId = new Identifier("my_addon", "golden_sandstorm_sp");
					canEvolve = true;
				}
				// 允许原版三阶段使魔使用进化石进化为契灵（与月髓环→灵界之主的路径并行存在）
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "familiar_fox_3"))) {
					targetFormId = new Identifier("my_addon", "familiar_fox_mancianima");
					canEvolve = true;
				}
				// 允许进化使魔（SSCA 路线）使用进化石进化为契灵：需 50 级解锁两分支后才允许（门控在下方）
				else if (playerFormID.equals(new Identifier("my_addon", "upgrade_familiar_fox"))) {
					targetFormId = new Identifier("my_addon", "familiar_fox_mancianima");
					canEvolve = true;
				}
				// 允许原版三阶段蝙蝠使用进化石进化为寄生果蝠
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "bat_3"))) {
					targetFormId = new Identifier("my_addon", "bat_parasitic_fruit");
					canEvolve = true;
				}
				// 允许原版三阶段美西螈使用进化石进化为荧光幼灵
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "axolotl_3"))) {
					targetFormId = new Identifier("my_addon", "axolotl_fluorescent");
					canEvolve = true;				}
				// 允许进化美西螈（SSCA 路线）使用进化石进化为荧光幼灵：需 50 级解锁两分支后才允许（门控在下方）
				else if (playerFormID.equals(new Identifier("my_addon", "upgrade_axolotl"))) {
					targetFormId = new Identifier("my_addon", "axolotl_fluorescent");
					canEvolve = true;				}				// 允许原版豹猫永久形态 ocelot_3 使用进化石进化为朔望（与月髓环→风灵并行存在，不同道具不冲突）
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "ocelot_3"))) {
					targetFormId = new Identifier("my_addon", "ocelot_nova");
					canEvolve = true;
				}
				// 允许原版三阶段蜘蛛使用进化石进化为跳蛛（与月髓环→月织蛛并行存在，不同道具不冲突）
				else if (playerFormID.equals(new Identifier("shape-shifter-curse", "spider_3"))) {
					targetFormId = new Identifier("my_addon", "spider_salticidae");
					canEvolve = true;
				}			}

			if (canEvolve) {
				// 进化形态门控（使魔 / 美西螈等所有 SSCA 进化路线起点形态）：必须先解锁全部分支才能用进化石继续进化
				if (player instanceof ServerPlayerEntity spStone
						&& !EvolutionManager.canUpgradeFoxEvolve(spStone)) {
					player.sendMessage(Text.translatable("message.ssc_addon.evolution.fail.branches_locked").formatted(Formatting.RED, Formatting.ITALIC), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
					return stack;
				}
				PlayerFormBase formBase = RegPlayerForms.getPlayerForm(targetFormId);
				if (formBase != null) {
					// 带黑屏淡入淡出动画变身（startTransform），STUN 在动画期间定身
					Compat1_9_2.startTransform(player, formBase, null);
					// 变身演出（黑屏淡入 IN + 淡出 OUT，共 160 tick）期间定身玩家，避免演出过程中走动
					player.addStatusEffect(new StatusEffectInstance(SscAddon.STUN,
							StaticParams.TRANSFORM_FX_DURATION_IN + StaticParams.TRANSFORM_FX_DURATION_OUT, 0, false, false, false));
					player.sendMessage(Text.translatable("message.ssc_addon.evolution_stone.success").formatted(Formatting.GREEN, Formatting.BOLD), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
					if (!player.getAbilities().creativeMode) {
						stack.decrement(1);
					}
					// 成就：哦？玩家的样子......！- 首次使用进化石进化
					AdvancementUtils.grant(player, new Identifier("ssc_addon", "player_form_shock"));
				} else {
					// 走 lang key，避免硬编码（合规 #48）
					player.sendMessage(Text.translatable("message.ssc_addon.evolution_stone.target_not_found", targetFormId).formatted(Formatting.RED), false);
				}
			} else {
				player.sendMessage(Text.translatable("message.ssc_addon.evolution_stone.no_response").formatted(Formatting.RED), true);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
		}
		return stack;
	}

	@Override
	public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable World world, java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		String key = "item.ssc_addon.evolution_stone.tooltip";
		if (I18n.hasTranslation(key)) {
			String translated = I18n.translate(key);
			for (String line : translated.split("\n")) {
				tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
			}
		} else {
			tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
		}
	}

	private Identifier getPlayerFormID(PlayerEntity player) {
		PlayerFormBase currentForm = FormUtils.getCurrentForm(player);
		return currentForm != null ? currentForm.FormID : null;
	}
}
