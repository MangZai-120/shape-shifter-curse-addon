package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
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

public final class SpellChannelManager {
	public static final Identifier STATE = new Identifier("ssc_addon", "spell_channel_state");
	public static final Identifier RELEASE = new Identifier("ssc_addon", "spell_channel_release");
	public static final Identifier CANCEL_HOLD = new Identifier("ssc_addon", "spell_channel_cancel_hold");
	public static final Identifier SUPPRESSION = new Identifier("ssc_addon", "spell_channel_suppression");
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
	 * 被打断不触发（可立刻重试）。书内与 solo 卷轴统一生效（两入口同走 start()）。
	 * 常量为默认值；运行时从 balance 快照（systems.casting.gcd_ticks）读取。 */
	public static final int CAST_INTERVAL_TICKS = 16;

	// 平衡迁移（systems.casting）：GCD/取消保持 tick 运行时从快照读取；快照未初始化回退默认常量
	private static final BalanceReader BAL = new BalanceReader("systems.casting");
	/** 每玩家下次可起手时刻（游戏 tick）。 */
	private static final Map<UUID, Long> NEXT_CAST_OK = new HashMap<>();
	/** 轻量校准包周期（tick）：活跃期内每 20t 只发 token+elapsed+标志位，静态字段仅起手发一次。
	 * 客户端 elapsed 本地推进（原版弓蓄力同款），包量较每 tick 全量降 ~95%。 */
	private static final int CALIBRATE_INTERVAL = 20;

	private SpellChannelManager() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ACTIVE.isEmpty()) return; // 常态（全服无人读条）零分配早退
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
				// 锁定态（如领域壳扩张后/爆裂红白球生成后，2026-09-23 用户定稿）忽略取消请求：
				// 不置 cancelHeld → cancelTicks 不累加 → 校准包不带取消态，客户端不会一直显示「取消中」。
				if (channel != null && channel.spell.isLockedIn(player)) return;
				if (channel != null && !channel.solo && channel.token == token
						&& (channel.mode == SpellCastingRules.Mode.AUTOMATIC
						|| channel.mode == SpellCastingRules.Mode.RELEASE)) {
					channel.cancelHeld = held;
					if (!held) channel.cancelTicks = 0;
				}
			});
		});
		net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler, server) -> {
			stop(handler.player, true);
			NEXT_CAST_OK.remove(handler.player.getUuid()); // GCD 表断线清理防泄漏
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearSlow(handler.player));
		// 交互打断（2026-09-22 需求）：读条期间玩家主动进行攻击/放块/用物品等交互 →
		// 按自身打断位取消当前施法（interrupt_mode 含位 2 的法术会被打断，如领域 mode 3）。
		// 交互本身照常执行（不 FAIL 不取消——打断施法 ≠ 拦截交互）。
		// 边界：solo 卷轴起手走 item.use()，UseItemCallback 先于其触发且当时无旧会话，不会自咬尾巴；
		// 若书内读条中右键卷轴 → 先打断旧会话再起新 solo 施法（符合「新交互打断旧读条」语义）。
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) cancelSelf(serverPlayer);
			return net.minecraft.util.ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) cancelSelf(serverPlayer);
			return net.minecraft.util.ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) cancelSelf(serverPlayer);
			return net.minecraft.util.ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) cancelSelf(serverPlayer);
			return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Channel channel : java.util.List.copyOf(ACTIVE.values())) stop(channel.player, true);
		});
	}

	public static boolean isCasting(ServerPlayerEntity player) {
		return ACTIVE.containsKey(player.getUuid());
	}

	public static boolean releaseLocked(ServerPlayerEntity player) {
		Channel channel=ACTIVE.get(player.getUuid());
		return channel!=null&&channel.spell.isLockedIn(player);
	}

	/** Skills interrupt wind preparation; the protected release stage rejects the skill itself. */
	public static boolean onSkillAction(ServerPlayerEntity player) {
		if(OcelotSpellLink.windLocked(player))return false;
		if(OcelotSpellLink.wind(player)) {
			stop(player,true,"message.ssc_addon.spell.cast_interrupted_action");
			net.ssca.neomagic.NeoRuntime.cancel(player,"interrupted");
		} else cancelSelf(player);
		return true;
	}

	public static long getNextCastAt(ServerPlayerEntity player) {
		return NEXT_CAST_OK.getOrDefault(player.getUuid(), 0L);
	}

	/** 清掉玩家的施法 GCD 门（/ssc_addon reset_spell_cd 用；不影响正在进行的读条）。 */
	public static void clearCastGate(ServerPlayerEntity player) {
		NEXT_CAST_OK.remove(player.getUuid());
		net.jackcooper.shapeShifterCurseAddon.network.SpellbookStatusSync.sync(player);
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
			Consumer<Vec3d> effect, IntConsumer settleCooldown) {
		return start(player, spell, scroll, level, solo, token, mana, cooldown,
				sourceValid, effect, settleCooldown, () -> {});
	}

	public static boolean start(ServerPlayerEntity player, Spell spell, ItemStack scroll, int level,
			boolean solo, int token, int mana, int cooldown, BooleanSupplier sourceValid,
			Consumer<Vec3d> effect, IntConsumer settleCooldown, Runnable consumeUse) {
		return start(player,spell,scroll,level,solo,token,mana,cooldown,sourceValid,effect,settleCooldown,consumeUse,
                RuneScheme.modifiers(player,scroll,solo?level:ScrollData.getCastLevel(scroll)));
    }
    public static boolean start(ServerPlayerEntity player, Spell spell, ItemStack scroll, int level,
            boolean solo, int token, int mana, int cooldown, BooleanSupplier sourceValid,
            Consumer<Vec3d> effect, IntConsumer settleCooldown, Runnable consumeUse,RuneModifiers modifiers) {
        return startInternal(player,spell,scroll,level,solo,token,mana,cooldown,sourceValid,effect,settleCooldown,consumeUse,modifiers,null);
    }
    /** Native jump-hit proc shares payment, failure, effect, experience and CD settlement with normal casts. */
    public static boolean startTriggered(ServerPlayerEntity player,Spell spell,ItemStack scroll,int level,boolean solo,
            int mana,int cooldown,BooleanSupplier sourceValid,Consumer<Vec3d> effect,IntConsumer settleCooldown,
            RuneModifiers modifiers,SpiderSpellLink.Trigger trigger) {
        return startInternal(player,spell,scroll,level,solo,-1,mana,trigger.cooldown(cooldown),sourceValid,
                target->trigger.effect(player,()->effect.accept(target)),settleCooldown,()->{},modifiers,trigger);
    }
    private static boolean startInternal(ServerPlayerEntity player,Spell spell,ItemStack scroll,int level,
            boolean solo,int token,int mana,int cooldown,BooleanSupplier sourceValid,Consumer<Vec3d> effect,
            IntConsumer settleCooldown,Runnable consumeUse,RuneModifiers modifiers,SpiderSpellLink.Trigger trigger) {
        if (net.ssca.neomagic.NeoRuntime.blocked(player)||net.ssca.neomagic.NeoLegacyCasting.isCasting(player)) return false;
        if (!RuneScheme.validScroll(player,scroll,spell))return false;
        if (ArcaneAnalysis.isUnanalyzed(scroll)) {
			player.sendMessage(Text.translatable("message.ssc_addon.analysis.required"), true);
			return false;
		}
		if (!net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isPlayerReady(player)) {
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.data_syncing"), true);
			return false;
		}
		if (isCasting(player) || !player.isAlive() || player.isSpectator()) return false;
		if (player.hasStatusEffect(SscAddon.PURIFIED)) {
			player.sendMessage(Text.translatable("message.ssc_addon.spell.purified_blocked"), true);
			return false;
		}
		// 领域内禁放指定红色法术（2026-10-21 用户定稿：逐法术开关；判定与「防领域叠加」
		// 同口径 enclosedTrapping——完全体外沿内+扩张壳内，含自己的领域。付费前拦截：零消耗）。
		if (spell.isForbiddenInsideDomain()
				&& DomainManager.enclosedTrapping(player.getServerWorld(), player.getPos())) {
			playFailureSound(player);
			player.sendMessage(spell.getDomainForbiddenMessage(), true);
			return false;
		}
		// 所有法术在捕获目标、创建演出、禁动或渐进扣费之前重验整次消耗。
		// solo 卷轴按次数结算；书内施法必须有有效 JSON 消耗与足够的实际能量。
		if (!spell.getConfig().manaCostConfigured) {
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.cost_unavailable"), true);
			return false;
		}
		net.ssca.neomagic.NeoRuntime.refreshForm(player);
		// GCD：释放生效后 0.8s 内不能开始下一次施法（被打断的可立刻重试）
		long gate = NEXT_CAST_OK.getOrDefault(player.getUuid(), 0L);
        if (trigger==null && player.getWorld().getTime() < gate) {
			playNoManaSound(player); // GCD 间隔内（释放后 0.8s）：火焰熄灭音（与 CD 拒绝同语义）
			player.sendMessage(Text.translatable("message.ssc_addon.spellbook.cast_interval",
					SpellCastFeedback.seconds(gate - player.getWorld().getTime())), true);
			return false;
		}
		ItemStack equipped=solo?null:SpellCastManager.getEquippedBook(player);
		double formationRange=equipped==null||equipped.isEmpty()?1:FormationData.sumSpaceRangeMultiplier(equipped,spell.getElement());
		Vec3d initialTarget=null;
		if (spell.requiresTargetBeforeChannel()) {
			initialTarget=RuneCastContext.with(modifiers,formationRange,()->spell.captureCastTarget(player,level));
			if(initialTarget==null||spell.getCastingMode()!=SpellCastingRules.Mode.RELEASE) {
				player.sendMessage(spell.getCastFailureMessage(player), true);
				return false;
			}
		}
		var baseProfile=spell.getCastingProfile(player,level,solo);
        var style=RuneCastContext.with(modifiers,formationRange,()->trigger==null?FormSpellStyle.plan(player,spell,level,
                baseProfile.ticks(),modifiers.time(baseProfile.ticks())):FormSpellStyle.planTriggered(player,spell,level,
                baseProfile.ticks(),modifiers.time(baseProfile.ticks())));
		double lockRange=RuneCastContext.with(modifiers,formationRange,()->spell.getCastingLockRange(player,level));
		if(!style.pay())return false;
		Channel channel = new Channel(player, spell, scroll, level, solo, token, mana, cooldown,
                sourceValid, effect, settleCooldown, consumeUse,modifiers,style,lockRange,trigger==null?1:trigger.growth());
		if(spell.requiresTargetBeforeChannel())channel.progress.release(token,initialTarget);
		ACTIVE.put(player.getUuid(), channel);
		RuneCastContext.run(channel.modifiers,channel.formationRange,()->spell.onChannelStarted(player, channel.progress.target()));
		channel.bindLock(initialTarget);
		// Jump the effect manager's logical clock too: e.g. instant DeathFinale still needs its completion state.
		if(style.novaQuickCast)RuneCastContext.run(channel.modifiers,channel.formationRange,
				()->spell.tickChannel(player,level,scroll,style.timing.logical(0)));
		applyMovement(player,channel.profile.speedMultiplier());
        if(trigger==null||style.timing.duration()>0) {
		    syncFull(channel);
		    broadcastVisual(channel, true);
		    playChargeSound(channel);
        }
		advance(channel); // Draw failure once at tick 0; a failed draw interrupts at its scheduled pre-lock tick.
		if (solo) net.jackcooper.shapeShifterCurseAddon.network.SpellbookStatusSync.sync(player);
		return true;
	}

	public static void release(ServerPlayerEntity player, int token, boolean solo) {
		Channel channel = ACTIVE.get(player.getUuid());
		if (channel == null || channel.solo != solo || channel.token != token || channel.progress.released()) return;
		if (!ensureValid(channel)) return;
		if (channel.mode == SpellCastingRules.Mode.AUTOMATIC) return;
		if (channel.mode == SpellCastingRules.Mode.CONTINUOUS) {
			stop(player, !channel.progress.started());
			return;
		}
		Vec3d target = RuneCastContext.with(channel.modifiers,channel.formationRange,()->channel.spell.captureCastTarget(player, channel.level));
		if (channel.spell.getAimMaxRange() > 0 && target == null) {
			stop(player, true, channel.spell.getCastFailureMessage(player));
			return;
		}
		if (channel.progress.release(token, target)){channel.bindLock(target);advance(channel);}
	}

	public static void cancelSelf(ServerPlayerEntity player) {
		Channel channel = ACTIVE.get(player.getUuid());
		if (channel != null && !channel.spell.isLockedIn(player)
				&& SpellCastingRules.allowsSelf(channel.interruptMode)) stop(player, true, "message.ssc_addon.spell.cast_interrupted_action");
	}

	public static void onDamaged(ServerPlayerEntity player) {
		Channel channel = ACTIVE.get(player.getUuid());
		if (channel != null && (!player.isAlive()
				|| !channel.spell.isLockedIn(player) && SpellCastingRules.allowsExternal(channel.interruptMode))) stop(player, true, "message.ssc_addon.spell.cast_interrupted_damage");
	}

	/** Purification overrides normal interrupt bits and release locks, with the full quoted CD. */
	public static void onPurified(ServerPlayerEntity player) {
		stop(player, true, Text.translatable("message.ssc_addon.spell.cast_interrupted_purified"), true);
	}

	public static void onFullyControlled(ServerPlayerEntity player) {
		stop(player, true, "message.ssc_addon.spell.cast_interrupted_action");
	}

	private static boolean ensureValid(Channel channel) {
		if(!channel.progress.started()&&channel.player.isAlive()&&channel.player.getWorld().getRegistryKey().equals(channel.dimension)
				&&(channel.targetLock!=null&&channel.targetLock.exceeded(channel.player)
				||channel.guidanceLock!=null&&channel.guidanceLock.exceeded(channel.player))) {
			rangeFailed(channel);return false;
		}
		if(OcelotSpellLink.wind(channel.player)&&(channel.player.isSprinting()||channel.player.isSneaking())) {
			if(channel.spell.isLockedIn(channel.player)){channel.player.setSprinting(false);channel.player.setSneaking(false);}
			else if(OcelotSpellLink.rushing(channel.player)){stop(channel.player,true,"message.ssc_addon.spell.cast_interrupted_action");return false;}
		}
		if (channel.spell instanceof net.jackcooper.shapeShifterCurseAddon.spell.spells.ExplosionSpell
				&& ExplosionManager.suppressIfDomainCovered(channel.player)) {
			channel.domainSuppressed = true;
			stop(channel.player, true, "message.ssc_addon.spell.explosion_domain_suppressed");
			return false;
		}
		if (!channel.collapse.form.equals(net.ssca.neomagic.NeoRuntime.form(channel.player))
				|| channel.player.hasStatusEffect(net.ssca.neomagic.NeoMagic.DISORDER)
				|| channel.player.hasStatusEffect(net.ssca.neomagic.NeoMagic.DISINTEGRATION)
				|| channel.player.hasStatusEffect(net.ssca.neomagic.NeoMagic.BREAKDOWN)) {
			stop(channel.player,true);return false;
		}
		if (net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard.blocked(channel.player)) {
			onFullyControlled(channel.player);
			return false;
		}
		if (channel.player.hasStatusEffect(SscAddon.PURIFIED)) {
			onPurified(channel.player);
			return false;
		}
		if (!valid(channel)) {
			stop(channel.player, true);
			return false;
		}
		return true;
	}

	private static void rangeFailed(Channel channel) {
		ServerPlayerEntity player=channel.player;
		UUID sequence=channel.spell instanceof net.jackcooper.shapeShifterCurseAddon.spell.spells.ExplosionSpell
				?ExplosionManager.chargingSequence(player):null;
		sendSuppression(player,channel.spell.getRarity(channel.level).color.getColorValue(),sequence);
		if(sequence!=null)ExplosionManager.suppressCharge(player);
		channel.domainSuppressed=true; // Shared quiet termination: only the glass/extinguish cue plays.
		channel.collapse.record.failureReason="range_exceeded";
		channel.collapse.session.failed=true;
		stop(player,true,"message.ssc_addon.spell.range_exceeded");
	}

	/** One event at failure, shared by books, standalone scrolls and blueprint preparation. */
	public static void sendSuppression(ServerPlayerEntity player,int color,UUID explosionSequence) {
		var audience=new java.util.HashSet<>(net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player));audience.add(player);
		for(ServerPlayerEntity viewer:audience)if(ServerPlayNetworking.canSend(viewer,SUPPRESSION))
			ServerPlayNetworking.send(viewer,SUPPRESSION,suppressionPacket(player,color,explosionSequence));
	}
	public static PacketByteBuf suppressionPacket(ServerPlayerEntity player,int color,UUID explosionSequence) {
		PacketByteBuf buf=PacketByteBufs.create();buf.writeIdentifier(player.getWorld().getRegistryKey().getValue());
		buf.writeUuid(player.getUuid());buf.writeDouble(player.getX());buf.writeDouble(player.getY());buf.writeDouble(player.getZ());
		buf.writeInt(color);buf.writeBoolean(explosionSequence!=null);if(explosionSequence!=null)buf.writeUuid(explosionSequence);return buf;
	}

	private static boolean valid(Channel channel) {
		ServerPlayerEntity player = channel.player;
		return player.isAlive() && !player.isRemoved() && !player.isSpectator()
				&& player.getWorld().getRegistryKey().equals(channel.dimension) && channel.sourceValid.getAsBoolean()
				&& (!channel.profile.immobilized() || !player.hasVehicle() && !player.hasPassengers())
				&& RuneCastContext.with(channel.modifiers,channel.formationRange,()->channel.spell.canContinueCasting(player, channel.scroll));
	}

	private static void tick(Channel channel) {
		ServerPlayerEntity player = channel.player;
		if (ACTIVE.get(player.getUuid()) != channel) return;
		if (player.getServer().getTicks() <= channel.beganTick) return;
		if (!ensureValid(channel)) return;
		if (channel.cancelHeld && ++channel.cancelTicks >= BAL.i("cancel_hold_ticks", 20)
				&& !channel.spell.isLockedIn(player)
				&& SpellCastingRules.allowsSelf(channel.interruptMode)) {
			stop(player, true, "message.ssc_addon.spell.cast_cancelled");
			return;
		}
		if (channel.progress.started()) {
			if (!channel.spell.tickContinuousCast(player, channel.level, channel.scroll, ++channel.continuousTicks)) {
				stop(player, false);
				return;
			}
		} else {
			channel.collapseAge++;
			channel.progress.tick();
			RuneCastContext.run(channel.modifiers,channel.formationRange,()->channel.spell.tickChannel(player, channel.level, channel.scroll, channel.style.timing.logical(channel.progress.elapsed())));
			advance(channel);
		}
		// 锁定态转换检测：进入锁定的当 tick 立即补发一次校准包（不等 20t 周期）。
		// 爆裂锁定窗口 682-700t 落在 680t/700t 两次周期校准之间，且终止于通道完成（stop 发的是
		// 清除包）——只靠周期包客户端永远收不到 locked=true，倒计时不会变红。
		boolean lockedNow = channel.spell.isLockedIn(player);
		boolean lockBegun = lockedNow && !channel.lockedAnnounced;
		if (lockedNow) channel.lockedAnnounced = true;
		if (ACTIVE.get(player.getUuid()) == channel) {
			// 转换 tick 恰在 20t 网格上时（如领域 200t）周期包已携带 true，不重复补发
			if (lockBegun && channel.progress.elapsed() % CALIBRATE_INTERVAL != 0) sendCalibration(channel);
			syncCalibration(channel);
			if (channel.progress.elapsed() % 20 == 0) playChargeSound(channel);
		}
		if (ACTIVE.get(player.getUuid()) == channel && ++channel.visualTicks % 20 == 0) {
			broadcastVisual(channel, true); // 周期重播：覆盖新进视野的观察者（客户端过期兜底 45t）
		}
	}

	private static void advance(Channel channel) {
		if (ACTIVE.get(channel.player.getUuid()) != channel) return;
		if (!ensureValid(channel)) return;
		if (!channel.collapse.advance(channel.collapseAge)) {
			stop(channel.player, true, net.ssca.neomagic.NeoMagic.text("failed"), true);
			net.ssca.neomagic.NeoRuntime.backlash(channel.player,channel.collapse.session.quote.actual());
			return;
		}
		if (!RuneCastContext.with(channel.modifiers,channel.formationRange,()->channel.spell.readyToRelease(channel.player, channel.scroll)) || !channel.progress.beginEffect()) return;
		if (channel.mode == SpellCastingRules.Mode.CONTINUOUS) {
			// 持续模式起手生效即视为释放生效，GCD 从此起算（持续阶段结束不再重置）
			NEXT_CAST_OK.put(channel.player.getUuid(), channel.player.getWorld().getTime() + BAL.i("gcd_ticks", CAST_INTERVAL_TICKS));
			try {
				channel.collapse.effect(()->channel.effect.accept(channel.collapse.target(channel.progress.target())));
				if(channel.collapse.effectFailed) {stop(channel.player,true,net.ssca.neomagic.NeoMagic.text("failed"),true);net.ssca.neomagic.NeoRuntime.backlash(channel.player,channel.collapse.session.quote.actual());}
				else channel.collapse.finish(true,"released");
			} catch (RuntimeException exception) {
				stop(channel.player, true);
				throw exception;
			}
		} else {
			// 释放生效（beginEffect 成功即将执行效果）：GCD 从此起算
			NEXT_CAST_OK.put(channel.player.getUuid(), channel.player.getWorld().getTime() + BAL.i("gcd_ticks", CAST_INTERVAL_TICKS));
			stop(channel.player,false);
			try {channel.collapse.effect(()->channel.effect.accept(channel.collapse.target(channel.progress.target())));}
			catch(RuntimeException ex){channel.collapse.finish(false,"interrupted");throw ex;}
			if(channel.collapse.effectFailed) {
				channel.collapse.finish(false,"failed");
				net.ssca.neomagic.NeoRuntime.backlash(channel.player,channel.collapse.session.quote.actual());
			} else channel.collapse.finish(true,"released");
		}
	}

	private static void stop(ServerPlayerEntity player, boolean interrupted) {
		stop(player, interrupted, "message.ssc_addon.spell.cast_interrupted");
	}

	private static void stop(ServerPlayerEntity player, boolean interrupted, String reason) {
		stop(player, interrupted, Text.translatable(reason));
	}

	private static void stop(ServerPlayerEntity player, boolean interrupted, Text reason) {
		stop(player, interrupted, reason, false);
	}

	private static void stop(ServerPlayerEntity player, boolean interrupted, Text reason, boolean fullCooldown) {
		Channel channel = ACTIVE.remove(player.getUuid());
		if (channel == null) return;
		channel.style.endCloak();
		if (interrupted) channel.collapse.finish(false,"range_exceeded".equals(channel.collapse.record.failureReason)?"failed":"interrupted");
		clearSlow(player);
		broadcastVisual(channel, false);
		channel.spell.onChannelEnded(player, interrupted,channel.domainSuppressed);
		channel.settleCooldown.accept(SpellCastingRules.cooldownAfterStop(
				channel.spell, channel.cooldown, interrupted, fullCooldown));
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
			if (!channel.domainSuppressed) playFailureSound(player);
			var message = reason.copy();
			// Paid collapse stays on the player; no book mana refund or deduction occurs.
			player.sendMessage(message, true);
		}
		if (interrupted || channel.solo || channel.mode == SpellCastingRules.Mode.CONTINUOUS) {
			net.jackcooper.shapeShifterCurseAddon.network.SpellbookStatusSync.sync(player);
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
		if (!SpellCastingRules.playsChargeSound(channel.profile.ticks())) return;
		if (channel.spell instanceof net.jackcooper.shapeShifterCurseAddon.spell.spells.DeathFinaleSpell) return;
		if (channel.spell instanceof net.jackcooper.shapeShifterCurseAddon.spell.spells.DomainSpell) return;
		// 蓄力嗡嗡声已迁至客户端循环音实例（SpellChargeSoundInstance，HUD STATE 沿驱动，
		// 可截断/可淡出/可立即停）；服务端只保留起手信标激活一次性短音。
		if (channel.progress.elapsed() == 0) {
			ServerPlayerEntity player = channel.player;
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.65f);
		}
	}

	public static void applyMovement(ServerPlayerEntity player,double multiplier) {
		var speed=player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if(speed!=null){speed.removeModifier(SLOW_ID);speed.addTemporaryModifier(new EntityAttributeModifier(SLOW_ID,"Spell casting",multiplier-1,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));}
	}

	public static void clearSlow(ServerPlayerEntity player) {
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
				buf.writeIdentifier(channel.spell.getId());
			}
			ServerPlayNetworking.send(viewer, CAST_VISUAL, buf);
		}
	}

	/** 轻量校准包：仅 token + elapsed + 标志位（客户端本地推进的主纠偏源）。每 20t 一次；
	 * 锁定态转换的当 tick 由 tick() 额外补发一次（sendCalibration）。 */
	private static void syncCalibration(Channel channel) {
		if (channel.progress.elapsed() % CALIBRATE_INTERVAL != 0) return;
		sendCalibration(channel);
	}

	/** 无周期守卫的校准包发送（周期校准与锁定转换补发共用）。 */
	private static void sendCalibration(Channel channel) {
		if (!ServerPlayNetworking.canSend(channel.player, STATE)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(true);
		buf.writeBoolean(false); // full=false：轻量校准包（客户端双格式分派标志，缺失会导致字段错位解包崩溃）
		buf.writeVarInt(channel.token);
		buf.writeVarInt(channel.progress.elapsed());
		buf.writeBoolean(channel.progress.released());
		buf.writeVarInt(channel.cancelTicks);
		buf.writeBoolean(channel.spell.isLockedIn(channel.player)); // 锁定态（进入后客户端红显且忽略取消）
		ServerPlayNetworking.send(channel.player, STATE, buf);
	}

	/** 全量首包（仅 start 时发一次）：静态字段（法术 ID/UUID/时长/模式/布尔组）+ 初始 elapsed。
	 * 后续每 20t 只发轻量校准包（token+elapsed+标志位），elapsed 由客户端本地推进。 */
	private static void syncFull(Channel channel) {
		if (!ServerPlayNetworking.canSend(channel.player, STATE)) return;
		ServerPlayNetworking.send(channel.player, STATE, fullStatePacket(channel));
	}

	private static PacketByteBuf fullStatePacket(Channel channel) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(true);
		buf.writeBoolean(true); // full=true：全量包（full=false 为轻量校准）
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
		buf.writeBoolean(channel.spell.isLockedIn(channel.player)); // 锁定态（进入后客户端红显且忽略取消）
		buf.writeVarInt(channel.level); // 本次有效等级，含临时降档；供客户端射程/半径预览
		buf.writeVarInt(channel.style.timing.lockTick());
		buf.writeFloat((float)channel.style.projectileRange);
		return buf;
	}

	/** 演出管理器在onChannelStarted内读取本次时间快照。 */
	public static FormSpellStyle.Timing animationTiming(ServerPlayerEntity player) {
		var channel=ACTIVE.get(player.getUuid());
		return channel==null?null:channel.style.timing;
	}

    public static double projectileRangeSnapshot(ServerPlayerEntity player) {
        var channel=ACTIVE.get(player.getUuid());
        return channel==null?Double.NaN:channel.style.projectileRange;
    }

	private static final class Channel {
		final ServerPlayerEntity player;
		final Spell spell;
        final FormSpellStyle style;
        final RuneModifiers modifiers;
        final double formationRange;
		final double lockRange;
		CastRangeGuard.Lock targetLock,guidanceLock;
		final ItemStack scroll;
		final int level, token, cooldown, interruptMode;
		final boolean solo;
		final SpellCastingRules.Profile profile;
		final SpellCastingRules.Mode mode;
		final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
		final BooleanSupplier sourceValid;
		final Consumer<Vec3d> effect;
		final IntConsumer settleCooldown;
		final Runnable consumeUse;
		final SpellCastingRules.Progress<Vec3d> progress;
		final net.ssca.neomagic.NeoLegacyCasting collapse;
		final int beganTick;
		int collapseAge;
		int cancelTicks, continuousTicks, visualTicks;
		boolean cancelHeld;
		/** 锁定态已广播标志（进入锁定的当 tick 补发一次校准包，防周期网格漏档）。 */
		boolean lockedAnnounced;
		boolean domainSuppressed;

		Channel(ServerPlayerEntity player, Spell spell, ItemStack scroll, int level, boolean solo, int token,
				int mana, int cooldown, BooleanSupplier sourceValid,
                Consumer<Vec3d> effect, IntConsumer settleCooldown, Runnable consumeUse,RuneModifiers modifiers,FormSpellStyle style,double lockRange,double growthMultiplier) {
            this.style=style;
            this.lockRange=lockRange;
            this.guidanceLock=CastRangeGuard.entity(style.target,style.range);
            this.modifiers=modifiers;
            ItemStack equipped=solo?null:SpellCastManager.getEquippedBook(player);
            this.formationRange=equipped==null||equipped.isEmpty()?1:FormationData.sumSpaceRangeMultiplier(equipped,spell.getElement());
			this.player = player;
			this.spell = spell;
			this.scroll = scroll;
			this.level = level;
			this.solo = solo;
			this.token = token;
			this.cooldown = cooldown;
			this.sourceValid = sourceValid;
			this.effect = target->style.effect(()->RuneCastContext.run(modifiers,this.formationRange,()->effect.accept(target)));
			this.settleCooldown = settleCooldown;
			this.consumeUse = consumeUse;
			var baseProfile=spell.getCastingProfile(player, level, solo);
            this.profile = new SpellCastingRules.Profile(style.timing.duration(),
                    OcelotSpellLink.wind(player)?OcelotSpellLink.walkingMultiplier():baseProfile.speedMultiplier(),
                    !OcelotSpellLink.wind(player)&&baseProfile.immobilized());
			this.mode = spell.getCastingMode();
			this.progress = new SpellCastingRules.Progress<>(this.mode, this.profile.ticks(), token);
			this.beganTick = player.getServer().getTicks();
			int cost = solo ? modifiers.mana(Math.round(spell.getManaCost()*spell.getConfig().manaCostMultiplier(level))) : mana;
			this.collapse = new net.ssca.neomagic.NeoLegacyCasting(player,spell,level,this.profile.ticks(),cost,
                    style.timing.lockTick(),style.collapseMultiplier*growthMultiplier);
            this.collapse.record.moistureSpent=style.paidMoisture();
            style.bindAllay(this.collapse.session.quote.actual(),this.collapse.record,spell.getElement()==FormationElement.CURSE&&spell.supportsCurseChain());
            style.bindBat(this.collapse.record);
            this.collapse.record.novaQuickCast=style.novaQuickCast;
            if(style.frost!=null)style.frost.record(this.collapse.record);
            if(style.healing!=null)style.healing.onPayment(amount->this.collapse.record.moistureSpent+=amount);
			this.interruptMode = spell.getConfig().interruptMode;
			this.dimension = player.getWorld().getRegistryKey();
		}
		void bindLock(Vec3d target) {
			var entity=spell.getCastingLockEntity(player);
			targetLock=entity==null?CastRangeGuard.point(target,lockRange):CastRangeGuard.entity(entity,lockRange);
		}
	}
}
