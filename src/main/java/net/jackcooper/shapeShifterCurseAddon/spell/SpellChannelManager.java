package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public final class SpellChannelManager {
	public static final Identifier STATE = new Identifier("ssc_addon", "spell_channel_state");
	public static final Identifier RELEASE = new Identifier("ssc_addon", "spell_channel_release");
	public static final Identifier CANCEL_HOLD = new Identifier("ssc_addon", "spell_channel_cancel_hold");
	/** S2C 施法视觉状态广播（jackcooper）：UUID、活动态、抬手标志、系别色与稀有度色，受众 = 追踪者 + 本人。
	 * 客户端据此做人形态举手动画（FERAL 兽形不举手）；转身/视角不受限，用原版机制。
	 * 活动期每 20t 重播，客户端 45t 过期兑底。 */
	public static final Identifier CAST_VISUAL = new Identifier("ssc_addon", "spell_cast_visual");
	/** 短档不抬手，但仍显示脚下法阵。 */
	private static final java.util.Set<SpellCastingRules.Tier> NO_ARM_POSE_TIERS =
			java.util.Set.of(SpellCastingRules.Tier.INSTANT,
					SpellCastingRules.Tier.BASIC_1, SpellCastingRules.Tier.BASIC_2);
	private static final UUID SLOW_ID = UUID.fromString("e178a586-4244-4fa7-a8b2-38a6a436d1d5");
	private static final Map<UUID, Channel> ACTIVE = new HashMap<>();
	private static UUID clientImmobile;
	/** 释放→起手全局间隔（GCD，2026-09-19 用户定稿 0.8s=16t）：释放生效后 16t 内拒绝新起手；
	 * 被打断不触发（可立刻重试）。书内与 solo 卷轴统一生效（两入口同走 start()）。 */
	public static final int CAST_INTERVAL_TICKS = 16;
	/** 每玩家下次可起手时刻（游戏 tick）。 */
	private static final Map<UUID, Long> NEXT_CAST_OK = new HashMap<>();

	private SpellChannelManager() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Channel channel : java.util.List.copyOf(ACTIVE.values())) tick(channel);
		});
		ServerPlayNetworking.registerGlobalReceiver(RELEASE, (server, player, handler, buf, sender) -> {
			int token = buf.readVarInt();
			server.execute(() -> release(player, token, false));
		});
		ServerPlayNetworking.registerGlobalReceiver(CANCEL_HOLD, (server, player, handler, buf, sender) -> {
			int token = buf.readVarInt();
			boolean held = buf.readBoolean();
			server.execute(() -> {
				Channel channel = ACTIVE.get(player.getUuid());
				if (channel != null && !channel.solo && channel.token == token
						&& channel.mode == SpellCastingRules.Mode.AUTOMATIC) {
					channel.cancelHeld = held;
					if (!held) channel.cancelTicks = 0;
				}
			});
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			stop(handler.player, true);
			NEXT_CAST_OK.remove(handler.player.getUuid()); // GCD 表断线清理防泄漏
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearSlow(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Channel channel : java.util.List.copyOf(ACTIVE.values())) stop(channel.player, true);
		});
	}

	public static boolean isCasting(ServerPlayerEntity player) {
		return ACTIVE.containsKey(player.getUuid());
	}

	public static void setClientImmobile(UUID player) {
		clientImmobile = player;
	}

	public static boolean isImmobile(LivingEntity entity) {
		if (entity.getWorld().isClient) return entity.getUuid().equals(clientImmobile);
		Channel channel = ACTIVE.get(entity.getUuid());
		return channel != null && channel.profile.immobilized();
	}

	public static boolean start(ServerPlayerEntity player, Spell spell, ItemStack scroll, int level,
			boolean solo, int token, int mana, int cooldown, BooleanSupplier sourceValid,
			IntPredicate payTo, Consumer<Vec3d> effect, IntConsumer settleCooldown) {
		return start(player, spell, scroll, level, solo, token, mana, cooldown,
				sourceValid, payTo, effect, settleCooldown, () -> {});
	}

	public static boolean start(ServerPlayerEntity player, Spell spell, ItemStack scroll, int level,
			boolean solo, int token, int mana, int cooldown, BooleanSupplier sourceValid,
			IntPredicate payTo, Consumer<Vec3d> effect, IntConsumer settleCooldown, Runnable consumeUse) {
		if (isCasting(player) || !player.isAlive() || player.isSpectator()) return false;
		// GCD：释放生效后 0.8s 内不能开始下一次施法（被打断的可立刻重试）
		long gate = NEXT_CAST_OK.getOrDefault(player.getUuid(), 0L);
		if (player.getWorld().getTime() < gate) {
			playNoManaSound(player); // GCD 间隔内（释放后 0.8s）：火焰熄灭音（与 CD 拒绝同语义）
			return false;
		}
		Channel channel = new Channel(player, spell, scroll, level, solo, token, mana, cooldown,
				sourceValid, payTo, effect, settleCooldown, consumeUse);
		ACTIVE.put(player.getUuid(), channel);
		var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW_ID);
			speed.addTemporaryModifier(new EntityAttributeModifier(SLOW_ID, "Spell casting",
					channel.profile.speedMultiplier() - 1, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		sync(channel);
		broadcastVisual(channel, true);
		playChargeSound(channel);
		if (channel.profile.ticks() == 0) advance(channel);
		return true;
	}

	public static void release(ServerPlayerEntity player, int token, boolean solo) {
		Channel channel = ACTIVE.get(player.getUuid());
		if (channel == null || channel.solo != solo || channel.token != token || channel.progress.released()) return;
		if (!valid(channel)) { stop(player, true); return; }
		if (channel.mode == SpellCastingRules.Mode.AUTOMATIC) return;
		if (channel.mode == SpellCastingRules.Mode.CONTINUOUS) {
			stop(player, !channel.progress.started());
			return;
		}
		Vec3d target = channel.spell.captureCastTarget(player, channel.level);
		if (channel.spell.getAimMaxRange() > 0 && target == null) {
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_target"), true);
			stop(player, true);
			return;
		}
		if (channel.progress.release(token, target)) advance(channel);
	}

	public static void cancelSelf(ServerPlayerEntity player) {
		Channel channel = ACTIVE.get(player.getUuid());
		if (channel != null && SpellCastingRules.allowsSelf(channel.interruptMode)) stop(player, true);
	}

	public static void onDamaged(ServerPlayerEntity player) {
		Channel channel = ACTIVE.get(player.getUuid());
		if (channel != null && (!player.isAlive() || SpellCastingRules.allowsExternal(channel.interruptMode))) stop(player, true);
	}

	private static boolean valid(Channel channel) {
		ServerPlayerEntity player = channel.player;
		return player.isAlive() && !player.isRemoved() && !player.isSpectator()
				&& player.getWorld().getRegistryKey().equals(channel.dimension) && channel.sourceValid.getAsBoolean()
				&& (!channel.profile.immobilized() || !player.hasVehicle() && !player.hasPassengers())
				&& channel.spell.canContinueCasting(player, channel.scroll);
	}

	private static void tick(Channel channel) {
		ServerPlayerEntity player = channel.player;
		if (ACTIVE.get(player.getUuid()) != channel) return;
		if (!valid(channel)) {
			stop(player, true);
			return;
		}
		if (channel.cancelHeld && ++channel.cancelTicks >= 20
				&& SpellCastingRules.allowsSelf(channel.interruptMode)) {
			stop(player, true);
			return;
		}
		if (channel.progress.started()) {
			if (!channel.spell.tickContinuousCast(player, channel.level, channel.scroll, ++channel.continuousTicks)) {
				stop(player, false);
				return;
			}
		} else {
			channel.progress.tick();
			advance(channel);
		}
		if (ACTIVE.get(player.getUuid()) == channel) {
			sync(channel);
			if (channel.progress.elapsed() % 20 == 0) playChargeSound(channel);
		}
		if (ACTIVE.get(player.getUuid()) == channel && ++channel.visualTicks % 20 == 0) {
			broadcastVisual(channel, true); // 周期重播：覆盖新进视野的观察者（客户端过期兜底 45t）
		}
	}

	private static void advance(Channel channel) {
		if (ACTIVE.get(channel.player.getUuid()) != channel) return;
		if (!valid(channel)) { stop(channel.player, true); return; }
		int due = SpellCastingRules.cumulativeMana(channel.mana, channel.progress.elapsed(), channel.profile.ticks());
		if (due > channel.paid) {
			if (!channel.payTo.test(due)) {
				channel.player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana"), true);
				playNoManaSound(channel.player); // 法力中途耗尽：熄灭音（读条中断处理仍走 stop）
				stop(channel.player, true);
				return;
			}
			channel.paid = due;
			FormCastingStyle.markManaSpend(channel.player);
		}
		if (!channel.spell.readyToRelease(channel.player, channel.scroll) || !channel.progress.beginEffect()) return;
		if (channel.mode == SpellCastingRules.Mode.CONTINUOUS) {
			// 持续模式起手生效即视为释放生效，GCD 从此起算（持续阶段结束不再重置）
			NEXT_CAST_OK.put(channel.player.getUuid(), channel.player.getWorld().getTime() + CAST_INTERVAL_TICKS);
			try {
				channel.effect.accept(channel.progress.target());
			} catch (RuntimeException exception) {
				stop(channel.player, true);
				throw exception;
			}
		} else {
			// 释放生效（beginEffect 成功即将执行效果）：GCD 从此起算
			NEXT_CAST_OK.put(channel.player.getUuid(), channel.player.getWorld().getTime() + CAST_INTERVAL_TICKS);
			stop(channel.player, false);
			channel.effect.accept(channel.progress.target());
		}
	}

	private static void stop(ServerPlayerEntity player, boolean interrupted) {
		Channel channel = ACTIVE.remove(player.getUuid());
		if (channel == null) return;
		clearSlow(player);
		broadcastVisual(channel, false);
		channel.settleCooldown.accept(interrupted
				? SpellCastingRules.interruptedCooldown(channel.cooldown) : channel.cooldown);
		if (!interrupted || channel.progress.started()) channel.consumeUse.run();
		if (channel.progress.started() && channel.mode == SpellCastingRules.Mode.CONTINUOUS) {
			channel.spell.endContinuousCast(player, channel.level, interrupted);
		}
		if (ServerPlayNetworking.canSend(player, STATE)) {
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeBoolean(false);
			ServerPlayNetworking.send(player, STATE, buf);
		}
		if (interrupted) {
			playFailureSound(player);
			player.sendMessage(Text.translatable("message.ssc_addon.spell.cast_interrupted"), true);
		}
	}

	public static void playFailureSound(ServerPlayerEntity player) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.6f);
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.65f);
	}

	/** 法力不足专用音（2026-09-19 用户定稿）：火焰熄灭嘶声——「法力枯竭」语义，与通用失败音区分。 */
	public static void playNoManaSound(ServerPlayerEntity player) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.PLAYERS, 0.9f, 0.9f);
	}

	private static void playChargeSound(Channel channel) {
		// 蓄力嗡嗡声已迁至客户端循环音实例（SpellChargeSoundInstance，HUD STATE 沿驱动，
		// 可截断/可淡出/可立即停）；服务端只保留起手信标激活一次性短音。
		if (channel.progress.elapsed() == 0) {
			ServerPlayerEntity player = channel.player;
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.65f);
		}
	}

	private static void clearSlow(ServerPlayerEntity player) {
		var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(SLOW_ID);
	}

	/** 所有施法档位均广播法阵，抬手单独按档位控制。 */
	private static void broadcastVisual(Channel channel, boolean active) {
		java.util.Set<ServerPlayerEntity> audience =
				new java.util.HashSet<>(net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(channel.player));
		audience.add(channel.player);
		for (ServerPlayerEntity viewer : audience) {
			if (!ServerPlayNetworking.canSend(viewer, CAST_VISUAL)) continue;
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeUuid(channel.player.getUuid());
			buf.writeBoolean(active);
			if (active) {
				buf.writeBoolean(!NO_ARM_POSE_TIERS.contains(channel.spell.getConfig().spellTier));
				buf.writeInt(channel.spell.getElement().color);
				buf.writeInt(channel.spell.getRarity(channel.level).color.getColorValue());
			}
			ServerPlayNetworking.send(viewer, CAST_VISUAL, buf);
		}
	}

	private static void sync(Channel channel) {
		if (!ServerPlayNetworking.canSend(channel.player, STATE)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(true);
		buf.writeIdentifier(channel.spell.getId());
		buf.writeUuid(channel.player.getUuid());
		buf.writeVarInt(channel.token);
		buf.writeVarInt(channel.progress.elapsed());
		buf.writeVarInt(channel.profile.ticks());
		buf.writeEnumConstant(channel.mode);
		buf.writeBoolean(channel.progress.released());
		buf.writeBoolean(channel.profile.immobilized());
		buf.writeBoolean(channel.solo);
		buf.writeBoolean(channel.progress.started());
		buf.writeVarInt(channel.cancelTicks);
		ServerPlayNetworking.send(channel.player, STATE, buf);
	}

	private static final class Channel {
		final ServerPlayerEntity player;
		final Spell spell;
		final ItemStack scroll;
		final int level, token, mana, cooldown, interruptMode;
		final boolean solo;
		final SpellCastingRules.Profile profile;
		final SpellCastingRules.Mode mode;
		final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
		final BooleanSupplier sourceValid;
		final IntPredicate payTo;
		final Consumer<Vec3d> effect;
		final IntConsumer settleCooldown;
		final Runnable consumeUse;
		final SpellCastingRules.Progress<Vec3d> progress;
		int paid, cancelTicks, continuousTicks, visualTicks;
		boolean cancelHeld;

		Channel(ServerPlayerEntity player, Spell spell, ItemStack scroll, int level, boolean solo, int token,
				int mana, int cooldown, BooleanSupplier sourceValid, IntPredicate payTo,
				Consumer<Vec3d> effect, IntConsumer settleCooldown, Runnable consumeUse) {
			this.player = player;
			this.spell = spell;
			this.scroll = scroll;
			this.level = level;
			this.solo = solo;
			this.token = token;
			this.mana = mana;
			this.cooldown = cooldown;
			this.sourceValid = sourceValid;
			this.payTo = payTo;
			this.effect = effect;
			this.settleCooldown = settleCooldown;
			this.consumeUse = consumeUse;
			this.profile = spell.getCastingProfile(player, level, solo);
			this.mode = spell.getCastingMode();
			this.progress = new SpellCastingRules.Progress<>(this.mode, this.profile.ticks(), token);
			this.interruptMode = spell.getConfig().interruptMode;
			this.dimension = player.getWorld().getRegistryKey();
		}
	}
}