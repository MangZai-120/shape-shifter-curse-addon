package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
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
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public class RedFormTickMixin {

	// 捕获玩家客户端语言设置，存入SscAddon.PLAYER_LANGUAGES
	@Inject(method = "setClientSettings", at = @At("HEAD"))
	private void onSetClientSettings(ClientSettingsC2SPacket packet, CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		SscAddon.PLAYER_LANGUAGES.put(player.getUuid(), packet.language());
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

		// Performance check: Only run logic every 20 ticks (1 second)
		if (player.age % 20 != 0) return;

		boolean isCursedMoon = CursedMoon.isCursedMoonDay(player.getWorld());

		// Reset the attempt tag if it is not Cursed Moon
		if (!isCursedMoon && player.getCommandTags().contains("ssc_addon_red_attempted")) {
			player.getCommandTags().remove("ssc_addon_red_attempted");
		}

		// Potion Bag Logic
		IForm currentForm = FormUtils.getCurrentForm(player);
		boolean isRedForm = currentForm != null && currentForm.getFormID().equals(new Identifier("my_addon", "familiar_fox_red"));

		// SP Form + Cursed Moon Transformation Logic
		if (currentForm != null && currentForm.getFormID().equals(new Identifier("my_addon", "familiar_fox_sp")) && isCursedMoon && !player.getCommandTags().contains("ssc_addon_red_attempted")) {
			player.addCommandTag("ssc_addon_red_attempted");
			// 5% Chance to transform to Red
			if (player.getRandom().nextFloat() < 0.05f) {
				Identifier redFormId = new Identifier("my_addon", "familiar_fox_red");
				IForm redForm = RegPlayerForms.getPlayerForm(redFormId);
				if (redForm != null) {
					TransformManager.immediatelyTransform(player, redForm);

					// 10 Minutes = 12000 ticks
					long expireTime = player.getWorld().getTime() + 12000;
					player.addCommandTag("ssc_addon_red_expire:" + expireTime);

					player.sendMessage(Text.translatable("message.ssc_addon.red_transformation_special").formatted(Formatting.GREEN), false);
					player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0F, 1.0F);
					return; // Exit after successful transformation
				}
			}
		}

		// === SP Allay Form: Auto-grant heal wand (slot 0) and jukebox (slot 1) ===
		boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(new Identifier("my_addon", "allay_sp"));
		if (isAllaySp) {
			placeFormItemSafe(player, 0, SscAddon.ALLAY_HEAL_WAND);
			placeFormItemSafe(player, 1, SscAddon.ALLAY_JUKEBOX);
		} else {
			// Not Allay SP: Remove any allay items found
			for (int i = 0; i < player.getInventory().size(); ++i) {
				ItemStack stack = player.getInventory().getStack(i);
				if (stack.isOf(SscAddon.ALLAY_HEAL_WAND)) {
					player.getInventory().setStack(i, ItemStack.EMPTY);
				} else if (stack.isOf(SscAddon.ALLAY_JUKEBOX)) {
					player.getInventory().setStack(i, ItemStack.EMPTY);
				}
			}
		}

		if (isRedForm) {
			placeFormItemSafe(player, 8, SscAddon.POTION_BAG);
		} else {
			// Not Red Form: Remove any Potion Bag found
			for (int i = 0; i < player.getInventory().size(); ++i) {
				ItemStack stack = player.getInventory().getStack(i);
				if (stack.isOf(SscAddon.POTION_BAG)) {
					// Found a bag, drop its contents
					if (stack.getNbt() != null && stack.hasNbt() && stack.getNbt().contains("Items", 9)) {
						NbtList list = stack.getNbt().getList("Items", 10);
						for (int j = 0; j < list.size(); ++j) {
							NbtCompound itemTag = list.getCompound(j);
							ItemStack contentStack = ItemStack.fromNbt(itemTag);
							if (!contentStack.isEmpty()) {
								player.dropItem(contentStack, false, true);
							}
						}
					}
					// Remove bag itself
					player.getInventory().setStack(i, ItemStack.EMPTY);
				}
			}
		}

		Set<String> tagsToRemove = new HashSet<>();
		boolean shouldRevert = false;
		long currentTime = player.getWorld().getTime();

		for (String tag : player.getCommandTags()) {
			if (tag.startsWith("ssc_addon_red_expire:")) {
				try {
					long expireTime = Long.parseLong(tag.split(":")[1]);

					// Remaining time logic: simply check for expiration
					if (currentTime >= expireTime) {
						shouldRevert = true;
						tagsToRemove.add(tag);
					}
				} catch (NumberFormatException ignored) {
					tagsToRemove.add(tag); // Invalid tag, remove it
				}
			}
		}

		if (!tagsToRemove.isEmpty()) {
			for (String tag : tagsToRemove) {
				player.getCommandTags().remove(tag);
			}
		}

		if (shouldRevert) {
			Identifier spFormId = new Identifier("my_addon", "familiar_fox_sp");
			IForm spForm = RegPlayerForms.getPlayerForm(spFormId);
			if (spForm != null) {
				// Use setFormDirectly instead of handleDirectTransform to avoid animation
				TransformManager.immediatelyTransform(player, spForm);

				// Spawn a large amount of white particles to cover the player
				if (player.getWorld() instanceof ServerWorld serverWorld) {
					// 100 CLOUD particles + 50 POOF particles
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 100, 0.5, 1.0, 0.5, 0.1);
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
				}


				// Clear the negative effects immediately (just in case they were applied, though we removed that logic)
				player.removeStatusEffect(StatusEffects.SLOWNESS);
				player.removeStatusEffect(StatusEffects.JUMP_BOOST);

				// Send timeout message
				player.sendMessage(Text.translatable("message.ssc_addon.red_revert_timeout").formatted(Formatting.GREEN), false);
			}
		}
	}

	/**
	 * 安全地把指定形态物品放进固定槽位，避免覆盖玩家原有物品。
	 * 严格按用户要求执行：
	 *   1. 检测目标槽位是否已经是该形态物品；是则直接返回，什么都不做。
	 *   2. 把原物品取出（copy 后清空源槽），先尝试合并到背包内已存在的同种物品堆（包含快捷栏）。
	 *   3. 若仍有剩余，尝试塞入背包任意空槽（已排除目标槽位）。
	 *   4. 仍剩余则丢到地上，绝对不会静默删除。
	 * 最后把形态物品放进目标槽位。
	 */
	@org.spongepowered.asm.mixin.Unique
	private void placeFormItemSafe(ServerPlayerEntity player, int targetSlot, Item formItem) {
		PlayerInventory inv = player.getInventory();
		ItemStack existing = inv.getStack(targetSlot);

		// Step 1: 目标槽位已是该物品 → 不动
		if (existing.isOf(formItem)) return;

		// 先把原物品 copy 出来，并清空源槽，以便插入算法不会再把它放回原位
		ItemStack moved = existing.copy();
		inv.setStack(targetSlot, ItemStack.EMPTY);

		if (!moved.isEmpty()) {
			// Step 2: 优先合并到背包内同种物品（遍历整个主物品栏 0~35，含快捷栏，跳过 targetSlot）
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

			// Step 3: 还有剩余 → 找空槽（排除 targetSlot）
			if (!moved.isEmpty()) {
				for (int i = 0; i < inv.main.size() && !moved.isEmpty(); ++i) {
					if (i == targetSlot) continue;
					if (!inv.main.get(i).isEmpty()) continue;
					inv.main.set(i, moved.copy());
					moved.setCount(0);
				}
			}

			// Step 4: 仍有剩余 → 丢到地上
			if (!moved.isEmpty()) {
				player.dropItem(moved, false, true);
			}
		}

		// 最后放入形态物品
		inv.setStack(targetSlot, new ItemStack(formItem));
	}
}
