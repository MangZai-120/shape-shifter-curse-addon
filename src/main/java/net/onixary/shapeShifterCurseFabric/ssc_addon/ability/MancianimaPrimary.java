package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

/**
 * 契灵 - 主要技能：三段标记。
 *
 * 段位：
 * <ol>
 *   <li>无标记 / 有黄/橙标记但准星不在该目标 → 标记新目标（黄）。
 *       消耗 15 mana；准星无生物 → 5 mana fizzle；触发 5s CD（仅首次）。</li>
 *   <li>橙标的同一目标在准星上 → 升级为红色（恐惧 + 凋零叫，1s 触发间隔，不算 CD）。</li>
 *   <li>已有红标，无论准星在哪 → 启动 2s 引导，引导成功对红标目标造成
 *       20% 剩余生命真伤（上限 20）+ 视觉爆炸；CD +15s。</li>
 * </ol>
 *
 * 白名单：默认机制（{@link WhitelistUtils#isProtected}），白名单成员/被驯服宠物不能被标记。
 * 同时禁止标记劫掠阵营？（设定文件说"无法对劫掠阵营生物使用"——属基础被动，本期暂不做拦截。）
 */
public final class MancianimaPrimary {

	private MancianimaPrimary() {}

	public static final int MARK_MANA_COST = 15;
	public static final int FIZZLE_MANA_COST = 5;
	public static final int FIRST_PRESS_CD = 100;            // 5s
	public static final int SUCCESS_DAMAGE_CD_ADD = 300;     // +15s
	public static final int RED_TRIGGER_INTERVAL = 20;       // 1s 触发间隔（防连点升红）
	public static final double MARK_RANGE = 32.0;
	public static final double RED_LOCK_RANGE = 24.0;
	public static final int CHANNEL_DAMAGE_TICKS = 40;       // 2s 引导
	public static final double DAMAGE_PERCENT = 0.20;
	public static final float DAMAGE_CAP = 20.0f;
	public static final int MANA_REGEN_PAUSE_TICKS = 100;    // 5s
	private static final net.minecraft.util.Identifier MANA_REGEN_PAUSE_RES =
			new net.minecraft.util.Identifier("my_addon", "form_familiar_fox_sp_mana_regen_regen_pause_timer");

	/** 客户端按键调用的入口。 */
	public static void execute(ServerPlayerEntity player) {
		if (!isMancianima(player)) return;
		ServerWorld world = (ServerWorld) player.getWorld();
		long now = world.getTime();

		// 引导中？再次按键 = 取消（设定未明说，默认行为：不响应；这里保持不响应）
		if (MancianimaMarkManager.CHANNELING.containsKey(player.getUuid())) return;

		MancianimaMarkManager.Mark mark = MancianimaMarkManager.getMark(player.getUuid());

		// 段 3：已有红标 → 引导 2s 真伤
		if (mark != null && mark.color == MancianimaMarkManager.MarkColor.RED) {
			LivingEntity tgt = findLivingByUuid(world, mark.targetUuid);
			if (tgt == null || !tgt.isAlive()) return;
			// 阶段冷却：红标刚升级后需等 3s 才能引爆
			if (now - mark.colorSetTick < MancianimaMarkManager.STAGE_GATE_TICKS) {
				int secLeft = (int) Math.max(1L, (long) Math.ceil((MancianimaMarkManager.STAGE_GATE_TICKS - (now - mark.colorSetTick)) / 20.0));
				player.sendMessage(Text.translatable("message.ssc_addon.mancianima.primary.stage_locked_seconds", secLeft), true);
				playMarkFailSound(player);
				return;
			}
			// 不消耗 mana 立即开始引导；CD 在引导成功后加 15s
			MancianimaMarkManager.CHANNELING.put(player.getUuid(),
					new MancianimaMarkManager.ChannelState(mark.targetUuid, now + CHANNEL_DAMAGE_TICKS, 1));
			player.sendMessage(Text.translatable("message.ssc_addon.mancianima.primary.channeling"), true);
			// 全场低响蓄力嗡鸣（让第三方有所感知）
			MancianimaMarkManager.broadcastSoundAtEntity(world, player, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.5f);
			// marker 私聊：明显的"启动"反馈（仅自己能听见）
			MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
			MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.4f);
			return;
		}

		// 段 1/2：raycast 找目标
		LivingEntity target = raycastLiving(player, MARK_RANGE);

		// 段 2：橙标的同一目标
		if (mark != null && mark.color == MancianimaMarkManager.MarkColor.ORANGE
				&& target != null && target.getUuid().equals(mark.targetUuid)) {
			// 阶段冷却：首次标记后需等 3s 才能升红
			if (now - mark.colorSetTick < MancianimaMarkManager.STAGE_GATE_TICKS) {
				int secLeft = (int) Math.max(1L, (long) Math.ceil((MancianimaMarkManager.STAGE_GATE_TICKS - (now - mark.colorSetTick)) / 20.0));
				player.sendMessage(Text.translatable("message.ssc_addon.mancianima.primary.stage_locked_seconds", secLeft), true);
				playMarkFailSound(player);
				return;
			}
			// 1s 触发间隔通过 SP_PRIMARY_CD 是否>0 来检测：但此段不应用 CD ⇒ 用单独资源不太划算。
			// 简化：若上次红升时间未到（用 RED_LOCKOUT），由 upgradeToRed 内部判定。
			boolean ok = MancianimaMarkManager.upgradeToRed(player, target);
			if (!ok) {
				player.sendMessage(Text.translatable("message.ssc_addon.mancianima.primary.red_locked"), true);
				return;
			}
			// 伤害与音效
			// 红升成功 - marker 多层叠加反馈音（个人听见，清晰提示第二次命中）
			MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
			MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 0.7f);
			MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 0.9f);
			MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1.4f);
			// 目标处也广播一下，让受击者及周围玩家能听见二段升红
			MancianimaMarkManager.broadcastSoundAtEntity(world, target, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.4f);
			if (target instanceof ServerPlayerEntity sp) {
				MancianimaMarkManager.playSoundToPlayer(sp, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
				MancianimaMarkManager.playSoundToPlayer(sp, SoundEvents.ENTITY_WITHER_AMBIENT, 0.7f, 1.0f);
			} else {
				MancianimaMarkManager.broadcastSoundAtEntity(world, target, SoundEvents.ENTITY_WITHER_AMBIENT, 0.7f, 1.0f);
			}
			// 段 2 不消耗 CD、不消耗 mana
			return;
		}

		// 段 1：标记新目标（或当前没有任何标记）
		// 检查 CD（如果当前在 CD 中，第一段直接拒绝）
		int cd = PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD);
		if (cd > 0) {
			playMarkFailSound(player);
			return;
		}

		if (target == null) {
			// 无目标 fizzle
			ManaComponent mana = ManaUtils.getManaComponent(player);
			if (mana == null || mana.getMana() < FIZZLE_MANA_COST) {
				player.sendMessage(Text.translatable("message.ssc_addon.mancianima.no_mana"), true);
				playMarkFailSound(player);
				return;
			}
			mana.setMana(mana.getMana() - FIZZLE_MANA_COST);
			pauseManaRegen(player);
			playMarkFailSound(player);
			PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, FIRST_PRESS_CD);
			return;
		}

		// 白名单 / 不能标记自己
		if (target == player) { playMarkFailSound(player); return; }
		if (WhitelistUtils.isProtected(player, target)) {
			player.sendMessage(Text.translatable("message.ssc_addon.mancianima.target_protected"), true);
			playMarkFailSound(player);
			return;
		}

		ManaComponent mana = ManaUtils.getManaComponent(player);
		if (mana == null || mana.getMana() < MARK_MANA_COST) {
			player.sendMessage(Text.translatable("message.ssc_addon.mancianima.no_mana"), true);
			playMarkFailSound(player);
			return;
		}
		mana.setMana(mana.getMana() - MARK_MANA_COST);
		pauseManaRegen(player);

		MancianimaMarkManager.setMark(player, target, MancianimaMarkManager.MarkColor.YELLOW);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, FIRST_PRESS_CD);

		// 标记成功 - 仅 marker 自己能听到的反馈音（多层叠加，增强可辨识度）
		MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
		MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.7f, 1.8f);
		MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, 0.6f, 1.4f);

		if (target instanceof ServerPlayerEntity sp) {
			MancianimaMarkManager.playSoundToPlayer(sp, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 2.0f);
		} else {
			MancianimaMarkManager.broadcastSoundAtEntity(world, target, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.8f, 2.0f);
		}
	}

	/** 由 MancianimaMarkManager 引导 tick 末尾调用：执行真伤与爆炸特效。 */
	public static void executeChannelComplete(ServerPlayerEntity marker, LivingEntity target) {
		if (target == null || !target.isAlive()) return;
		ServerWorld world = (ServerWorld) marker.getWorld();
		// 真伤计算
		float dmg = (float) Math.min(DAMAGE_CAP, target.getHealth() * DAMAGE_PERCENT);
		// 伤害归属：使用 indirectMagic 以 marker 为攻击者（生物会什7并带 tag bypasses_armor不被护甲减免）
		DamageSource src = world.getDamageSources().indirectMagic(marker, marker);
		target.damage(src, dmg);

		// AOE：以 target 为中心 3 格半径内的其他生物受到 50% 伤害（跳过 marker 自己 + 主目标 + 白名单）
		float aoeDmg = dmg * 0.5f;
		double aoeRadius = 3.0;
		Box aoeBox = new Box(
				target.getX() - aoeRadius, target.getY() - aoeRadius, target.getZ() - aoeRadius,
				target.getX() + aoeRadius, target.getY() + aoeRadius, target.getZ() + aoeRadius);
		for (Entity e : world.getOtherEntities(null, aoeBox, EntityPredicates.EXCEPT_SPECTATOR)) {
			if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
			if (le == target || le == marker) continue;
			if (e.squaredDistanceTo(target) > aoeRadius * aoeRadius) continue;
			if (WhitelistUtils.isProtected(marker, le)) continue;
			le.damage(src, aoeDmg);
		}

		// 爆炸视觉（不造成实际伤害）
		double cx = target.getX(), cy = target.getY() + target.getHeight() / 2.0, cz = target.getZ();
		// 主爆炸（大型）
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 2, 0.3, 0.3, 0.3, 0);
		// 灵魂烈焰球（紫黑契灵主题）
		world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 80, 1.5, 1.5, 1.5, 0.15);
		world.spawnParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 30, 1.2, 1.2, 1.2, 0.1);
		world.spawnParticles(ParticleTypes.DRAGON_BREATH, cx, cy, cz, 40, 1.5, 0.8, 1.5, 0.05);
		// AOE 边缘指示（3 格半径周围环形粒子）
		double aoeR = 3.0;
		for (int i = 0; i < 32; i++) {
			double a = i * Math.PI * 2.0 / 32.0;
			double px = cx + Math.cos(a) * aoeR;
			double pz = cz + Math.sin(a) * aoeR;
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, px, cy - 0.5, pz, 1, 0, 0.1, 0, 0);
			world.spawnParticles(ParticleTypes.FLAME, px, cy, pz, 1, 0, 0.05, 0, 0);
		}
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		world.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 0.8f);
		// 龙振翅音效：以 marker 为中心广播，渲染“斩杀”气氛
		world.playSound(null, marker.getX(), marker.getY(), marker.getZ(),
				SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.2f, 0.8f);
		// 成功 CD +15s
		int cur = PowerUtils.getResourceValue(marker, FormIdentifiers.SP_PRIMARY_CD);
		PowerUtils.setResourceValueAndSync(marker, FormIdentifiers.SP_PRIMARY_CD, cur + SUCCESS_DAMAGE_CD_ADD);
		// 斩杀完成：补满 mana 能量
		ManaUtils.setPlayerMana(marker, ManaUtils.getPlayerMaxMana(marker));
		// 红标使命达成 → 清除（已造成伤害，进入下一轮）
		MancianimaMarkManager.clearMark(world.getServer(), marker.getUuid());
	}

	// ============== Helpers ==============

	private static boolean isMancianima(PlayerEntity player) {
		PlayerFormBase form = FormAbilityManager.getForm((ServerPlayerEntity) player);
		return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.FormID);
	}

	private static void pauseManaRegen(ServerPlayerEntity player) {
		PowerUtils.setResourceValueAndSync(player, MANA_REGEN_PAUSE_RES, MANA_REGEN_PAUSE_TICKS);
	}

	/** 标记失败提示音（仅 marker 自己听到，统一格式） */
	private static void playMarkFailSound(ServerPlayerEntity player) {
		MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.0f);
		MancianimaMarkManager.playSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.6f);
	}

	/** 准星射线找最近 LivingEntity（穿透白名单友军 → 找其后第一个非白名单生物）。 */
	private static LivingEntity raycastLiving(ServerPlayerEntity player, double maxRange) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector().normalize();
		Vec3d end = eye.add(look.multiply(maxRange));
		ServerWorld world = (ServerWorld) player.getWorld();
		// 方块 raycast：先确定视线被方块阻挡的距离（视野内才能标记）
		BlockHitResult blockHit = world.raycast(new RaycastContext(
				eye, end,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player));
		double blockDistSq = blockHit.getType() == HitResult.Type.MISS
				? Double.MAX_VALUE
				: eye.squaredDistanceTo(blockHit.getPos());
		Box searchBox = new Box(eye, end).expand(1.0);
		// 收集射线 candidates 全部，按命中距离排序，跳过白名单/不可攻击/玩家友军
		double bestDist = Double.MAX_VALUE;
		LivingEntity best = null;
		for (Entity e : world.getOtherEntities(player, searchBox, EntityPredicates.EXCEPT_SPECTATOR)) {
			if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
			Box ebox = e.getBoundingBox().expand(0.3);
			java.util.Optional<Vec3d> hit = ebox.raycast(eye, end);
			if (hit.isEmpty()) continue;
			double d = eye.squaredDistanceTo(hit.get());
			if (d >= bestDist) continue;
			// 视野遮挡：若该实体命中点比方块碰撞点更远 → 隔墙不可标记
			if (d > blockDistSq) continue;
			// 跳过白名单：但仍允许"穿透友军"找到下一个；这里实现穿透：把白名单视为透明
			if (WhitelistUtils.isProtected(player, le)) continue;
			bestDist = d;
			best = le;
		}
		return best;
	}

	private static LivingEntity findLivingByUuid(ServerWorld world, java.util.UUID uuid) {
		Entity e = world.getEntity(uuid);
		if (e instanceof LivingEntity le) return le;
		// 遍历所有维度
		if (world.getServer() != null) {
			for (ServerWorld w : world.getServer().getWorlds()) {
				Entity en = w.getEntity(uuid);
				if (en instanceof LivingEntity le2) return le2;
			}
		}
		return null;
	}
}
