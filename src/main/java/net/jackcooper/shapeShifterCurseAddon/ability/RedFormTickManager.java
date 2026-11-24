package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 红使魔 / SP 形态周期逻辑管理器（jackcooper）。
 *
 * <p>原为 {@code RedFormTickMixin} 对 {@code ServerPlayerEntity.tick} 的 HEAD 注入（每玩家每 tick），
 * 2026-09-09 迁移到 {@code ServerTickEvents.START_WORLD_TICK} 世界级事件遍历——
 * 逻辑本就带 {@code player.age % 20 == 0} 一秒门控，行为零差异，删掉最高频注入点。</p>
 *
 * <p>职责（每秒一次，遍历各世界玩家）：</p>
 * <ul>
 *   <li>SP 使魔诅咒之月 5% 概率变身红使魔（10 分钟超时回退，粒子掩护）；</li>
 *   <li>SP 悦灵自动发放/回收治疗杖(槽0)与唱片机(槽1)；</li>
 *   <li>红使魔自动发放/回收药水袋(槽8)（回收时倒出内容物）；</li>
 *   <li>红使魔超时标签清理与回退。</li>
 * </ul>
 */
public final class RedFormTickManager {
	private RedFormTickManager() {
	}

	// 阶段 5：服务端权威快照读取（快照未初始化回退默认值）
	private static final BalanceReader BAL = new BalanceReader("abilities.red_form");

	public static void init() {
		net.onixary.shapeShifterCurseFabric.event.SSCEvent.FORM_CHANGE_END.register((player, previous, current) -> {
			if (!player.getWorld().isClient && previous != null && current != null
					&& previous.getFormID().equals(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_RED)
					&& !current.getFormID().equals(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_RED)) {
				player.getCommandTags().removeIf(tag -> tag.startsWith("ssc_addon_red_expire:"));
			}
		});
	}

	public static void tick(ServerPlayerEntity player) {
		// 一秒门控（与原 per-player tick 注入一致）
		if (player.age % 20 != 0) {
			return;
		}

		boolean isCursedMoon = CursedMoon.isCursedMoonDay(player.getWorld());

		// 非诅咒之月清除变身尝试标记
		if (!isCursedMoon && player.getCommandTags().contains("ssc_addon_red_attempted")) {
			player.getCommandTags().remove("ssc_addon_red_attempted");
		}

		// 药水袋逻辑标记
		IForm currentForm = FormUtils.getCurrentForm(player);
		boolean isRedForm = currentForm != null && currentForm.getFormID().equals(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_RED);

		// SP 形态 + 诅咒之月变身红使魔（5%）
		if (currentForm != null && currentForm.getFormID().equals(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_SP) && isCursedMoon && !player.getCommandTags().contains("ssc_addon_red_attempted")) {
			player.addCommandTag("ssc_addon_red_attempted");
			if (player.getRandom().nextFloat() < BAL.d("transform_chance", 0.05)) {
				Identifier redFormId = net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_RED;
				IForm redForm = RegPlayerForms.getPlayerForm(redFormId);
				if (redForm != null) {
					TransformManager.immediatelyTransform(player, redForm);

					// 10 分钟 = 12000 tick（默认；balance 可覆盖 duration_ticks）
					long expireTime = player.getWorld().getTime() + BAL.i("duration_ticks", 12000);
					player.addCommandTag("ssc_addon_red_expire:" + expireTime);

					player.sendMessage(Text.translatable("message.ssc_addon.red_transformation_special").formatted(Formatting.GREEN), false);
					AddonSoundRange.play(player.getWorld(), null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0F, 1.0F);
					return; // 变身成功后直接返回
				}
			}
		}

		boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.ALLAY_SP);
		if (isAllaySp) {
			placeFormItemSafe(player, 0, SscAddon.ALLAY_HEAL_WAND);
			placeFormItemSafe(player, 1, SscAddon.ALLAY_JUKEBOX);
		}

		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); ++slot) {
			ItemStack stack = inventory.getStack(slot);
			if (!isAllaySp && (stack.isOf(SscAddon.ALLAY_HEAL_WAND) || stack.isOf(SscAddon.ALLAY_JUKEBOX))) {
				inventory.setStack(slot, ItemStack.EMPTY);
			} else if (!isRedForm && stack.isOf(SscAddon.POTION_BAG)) {
				dropBagContents(player, stack);
				inventory.setStack(slot, ItemStack.EMPTY);
			}
		}

		// 先回收悦灵物品，再给红使魔发袋；腾出的空位可接收原第九格物品。
		if (isRedForm) {
			placeFormItemSafe(player, 8, SscAddon.POTION_BAG);
		}

		// 红使魔超时回退
		Set<String> tagsToRemove = new HashSet<>();
		boolean shouldRevert = false;
		long currentTime = player.getWorld().getTime();

		for (String tag : player.getCommandTags()) {
			if (tag.startsWith("ssc_addon_red_expire:")) {
				var formState = net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent.PLAYER_FORM.get(player);
				boolean pendingRed = formState.transformTargetForm != null
						&& formState.transformTargetForm.getFormID().equals(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_RED);
				if (!isRedForm) {
					if (!pendingRed) tagsToRemove.add(tag);
					continue;
				}
				try {
					long expireTime = Long.parseLong(tag.split(":")[1]);
					if (currentTime >= expireTime) {
						shouldRevert = true;
						tagsToRemove.add(tag);
					}
				} catch (NumberFormatException ignored) {
					tagsToRemove.add(tag); // 无效标签直接移除
				}
			}
		}

		if (!tagsToRemove.isEmpty()) {
			for (String tag : tagsToRemove) {
				player.getCommandTags().remove(tag);
			}
		}

		if (shouldRevert) {
			Identifier spFormId = net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_SP;
			IForm spForm = RegPlayerForms.getPlayerForm(spFormId);
			if (spForm != null) {
				// 用 immediatelyTransform 避免动画
				TransformManager.immediatelyTransform(player, spForm);

				// 白色粒子大量覆盖玩家（owner 打标：仅本人第一人称避让，变身演出他人视角原样）
				if (player.getWorld() instanceof ServerWorld serverWorld) {
					net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.forced(serverWorld, player, ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 100, 0.5, 1.0, 0.5, 0.1, 512);
					net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.forced(serverWorld, player, ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1, 512);
				}

				player.removeStatusEffect(StatusEffects.SLOWNESS);
				player.removeStatusEffect(StatusEffects.JUMP_BOOST);

				player.sendMessage(Text.translatable("message.ssc_addon.red_revert_timeout").formatted(Formatting.GREEN), false);
			}
		}
	}

	private static void dropBagContents(ServerPlayerEntity player, ItemStack bag) {
		NbtCompound nbt = bag.getNbt();
		if (nbt == null) return;
		NbtList contents = nbt.getList("Items", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < contents.size(); ++i) {
			ItemStack stack = ItemStack.fromNbt(contents.getCompound(i));
			if (!stack.isEmpty()) player.dropItem(stack, false, true);
		}
	}

	/**
	 * 安全地把指定形态物品放进固定槽位，避免覆盖玩家原有物品：
	 * ①目标槽已是该物品则不动；②原物品合并进背包同种堆；③剩余找空槽；④仍剩余丢地上，绝不静默删除。
	 */
	private static void placeFormItemSafe(ServerPlayerEntity player, int targetSlot, Item formItem) {
		PlayerInventory inv = player.getInventory();
		ItemStack existing = inv.getStack(targetSlot);

		if (existing.isOf(formItem)) {
			return;
		}

		ItemStack moved = existing.copy();
		inv.setStack(targetSlot, ItemStack.EMPTY);

		if (!moved.isEmpty()) {
			for (int i = 0; i < inv.main.size() && !moved.isEmpty(); ++i) {
				if (i == targetSlot) continue;
				ItemStack slotStack = inv.main.get(i);
				if (slotStack.isEmpty()) continue;
				if (!ItemStack.canCombine(slotStack, moved)) continue;
				int room = slotStack.getMaxCount() - slotStack.getCount();
				if (room <= 0) continue;
				int merge = Math.min(room, moved.getCount());
				slotStack.increment(merge);
				moved.decrement(merge);
			}

			if (!moved.isEmpty()) {
				for (int i = 0; i < inv.main.size() && !moved.isEmpty(); ++i) {
					if (i == targetSlot) continue;
					if (!inv.main.get(i).isEmpty()) continue;
					inv.main.set(i, moved.copy());
					moved.setCount(0);
				}
			}

			if (!moved.isEmpty()) {
				// 背包已满。整袋禁止丢弃，其他形态占位的旧袋必须在这里倒出。
				if (moved.isOf(SscAddon.POTION_BAG)) dropBagContents(player, moved);
				else player.dropItem(moved, false, true);
			}
		}

		inv.setStack(targetSlot, new ItemStack(formItem));
	}
}
