package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;

/**
 * 「击杀赋能」的释放侧（SP使魔 / 红堕落使魔，服务端）。
 *
 * <p>赋能激活期间由原生主动技能入口在服务端选择主/次技能：
 * <ul>
 *   <li>主技能 = 赋能火环：全周期免蓝，与正常火环互斥（状态机见 {@link #tryCastRing}）。</li>
 *   <li>次技能 = SP 吐息或 Red 火球：免蓝、免普通冷却释放一次。</li>
 * </ul>
 * 释放即消耗一次赋能机会，火环持续时间独立计算。
 */
public final class KillEmpowerCast {

	private KillEmpowerCast() {}

	/** 赋能吐息参数（与 power JSON 常规吐息一致：8 格 / 6 伤 ×非玩家倍率 / 灼烧 5s）。 */
	private static final float BREATH_DISTANCE = 8.0f;
	private static final float BREATH_DAMAGE = 6.0f;
	private static final float BREATH_NON_PLAYER_MULT_SP = 3.0f;   // SP使魔 吐息对怪倍率（与正常版 JSON ×3 对齐）
	private static final float BREATH_NON_PLAYER_MULT_RED = 1.5f;  // 红堕落 吐息对怪倍率（同源 ×1.5）
	private static final int BURN_DURATION = 100;

	/** 赋能火环伤害跳（KillEmpowerManager 每 16t 调用一次；与正常环 effects_loop 同节奏同半径）。 */
	public static void tickEmpowerRing(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getWorld();
		boolean redForm = FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED);
		// 蓝火护符修正（每跳实时读取，戴/摘即时生效）：伤害 +4、范围 -40%
		boolean amulet = TrinketUtils.isWearing(player, SscAddon.BLUE_FIRE_AMULET);
		final float base;
		if (redForm) base = amulet ? 9.0f : 5.0f; else base = amulet ? 8.0f : 4.0f;	// Red 环基伤 5→9；SP 环基伤 4→8（护符 +4）
		final double ringRadius = amulet ? 3.6 : 6.0;
		Box box = player.getBoundingBox().expand(ringRadius);
		RegistryKey<DamageType> genericKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "generic"));
		world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator()).forEach(e -> {
			if (WhitelistUtils.isProtected(player, e)) return;
			if (player.squaredDistanceTo(e) > ringRadius * ringRadius) return;
			float dmg = e instanceof net.minecraft.entity.player.PlayerEntity ? base : base * 2.0f;	// 对非玩家（NPC/怪物）×2
			e.timeUntilRegen = 0;	// 命中前清：不被普攻无敌帧吞
			if (e.damage(e.getDamageSources().create(genericKey, player, player), dmg)) {
				e.timeUntilRegen = 0;	// 命中后清：不留无敌帧反吞普攻
				e.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, redForm ? 80 : 60, 0), player);
				KillEmpowerManager.trackBurn(player, e, redForm ? 80 : 60, false);
				e.setFireTicks(redForm ? 80 : 60);
			}
		});
	}

	/** 判断正常火环（非赋能环）是否正在释放：正常环开时 toggle_state=1；赋能环不置 toggle。客户端可读。 */
	public static boolean isNormalRingActive(net.minecraft.entity.player.PlayerEntity player) {
		boolean red = FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED);
		// 4 个 Identifier 为静态常量
		Identifier toggleId = red ? TOGGLE_RED : TOGGLE_SP;
		Identifier amuletToggleId = red ? AMULET_TOGGLE_RED : AMULET_TOGGLE_SP;
		return PowerUtils.getClientResourceValue(player, toggleId) == 1
				|| PowerUtils.getClientResourceValue(player, amuletToggleId) == 1;
	}

	/** 火环 toggle 资源 id 常量（红/SP × 普通/护符，避免 HUD 每帧 new Identifier）。 */
	private static final Identifier TOGGLE_SP = new Identifier("my_addon", "form_familiar_fox_sp_blue_fire_ring_toggle_state");
	private static final Identifier TOGGLE_RED = new Identifier("my_addon", "form_familiar_fox_red_blue_fire_ring_toggle_state");
	private static final Identifier AMULET_TOGGLE_SP = new Identifier("my_addon", "form_familiar_fox_sp_blue_fire_ring_amulet_toggle_state");
	private static final Identifier AMULET_TOGGLE_RED = new Identifier("my_addon", "form_familiar_fox_red_blue_fire_ring_amulet_toggle_state");

	/** 客户端按主技能键（赋能态）。返回 true 表示已处理。 */
	public static boolean tryCastRing(ServerPlayerEntity player) {
		if (!KillEmpowerManager.isEmpowerForm(player)) return false;
		KillEmpowerState state = KillEmpowerManager.readState(player);
		if (!state.hasReady() && !state.hasRing()) return false;

		ServerWorld world = (ServerWorld) player.getWorld();
		boolean redForm = FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED);

		if (state.hasRing()) {
			player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
			KillEmpowerManager.writeState(player, state.stopRing());
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
			return true;
		}

		// STATE_READY：正常环（含护符环）开着 → 先关环进对应 CD（赋能保留，遮罩仍隐藏）；否则直接开赋能环
		boolean normalRingOn = player.hasStatusEffect(SscAddon.BLUE_FIRE_RING);
		if (normalRingOn) {
			player.removeStatusEffect(SscAddon.BLUE_FIRE_RING);
			boolean amuletRingOn = PowerUtils.getResourceValue(player, new Identifier("my_addon",
					(redForm ? "form_familiar_fox_red" : "form_familiar_fox_sp") + "_blue_fire_ring_amulet_toggle_state")) == 1;
			int ringCd = amuletRingOn ? 560 : 400;	// 关哪套环就进哪套 CD
			PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, ringCd);
			if (amuletRingOn) {
				PowerUtils.setResourceValueAndSync(player, new Identifier("my_addon",
						(redForm ? "form_familiar_fox_red" : "form_familiar_fox_sp") + "_blue_fire_ring_amulet_toggle_state"), 0);
				PowerUtils.setResourceValueAndSync(player, new Identifier("my_addon",
						(redForm ? "form_familiar_fox_red" : "form_familiar_fox_sp") + "_blue_fire_ring_amulet_cooldown_timer"), ringCd);
			} else if (redForm) {
				PowerUtils.setResourceValueAndSync(player, new Identifier("my_addon", "form_familiar_fox_red_blue_fire_ring_toggle_state"), 0);
				PowerUtils.setResourceValueAndSync(player, new Identifier("my_addon", "form_familiar_fox_red_blue_fire_ring_cooldown_timer"), ringCd);
			} else {
				PowerUtils.setResourceValueAndSync(player, new Identifier("my_addon", "form_familiar_fox_sp_blue_fire_ring_toggle_state"), 0);
				PowerUtils.setResourceValueAndSync(player, new Identifier("my_addon", "form_familiar_fox_sp_blue_fire_ring_cooldown_timer"), ringCd);
			}
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f);
			return true; // 本次按键只关环；赋能仍在（下次按键开赋能环）
		}

		// 蓝火护符修正：持续时间 -15%（释放瞬间读取；SP 232→197、Red 280→238）
		boolean amulet = TrinketUtils.isWearing(player, SscAddon.BLUE_FIRE_AMULET);
		int fullRingTicks = redForm ? KillEmpowerManager.EMPOWER_RING_TICKS_RED : KillEmpowerManager.EMPOWER_RING_TICKS_SP;
		if (amulet) fullRingTicks = Math.round(fullRingTicks * 0.85f);
		player.addStatusEffect(new StatusEffectInstance(SscAddon.BLUE_FIRE_RING, fullRingTicks, 0, true, false, true));
		StatusEffectInstance ringEffect = player.getStatusEffect(SscAddon.BLUE_FIRE_RING);
		if (ringEffect == null || ringEffect.getDuration() <= 0) return true;
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.EMPOWER_RING_DURATION, ringEffect.getDuration());
		KillEmpowerManager.writeState(player, state.startRing(ringEffect.getDuration()));
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.5f, 1.0f);
		return true;
	}

	/** 客户端按次技能键（赋能态）。返回 true 表示已处理（赋能吐息已释放）。 */
	public static boolean tryCastBreath(ServerPlayerEntity player) {
		if (!KillEmpowerManager.isEmpowerForm(player)) return false;
		KillEmpowerState state = KillEmpowerManager.readState(player);
		if (!state.hasReady()) return false;

		ServerWorld world = (ServerWorld) player.getWorld();
		boolean redForm = FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_RED);
		float nonPlayerMult = redForm ? BREATH_NON_PLAYER_MULT_RED : BREATH_NON_PLAYER_MULT_SP;

		// 消耗赋能（标记赋能击杀 → 本次吐息的击杀不触发新赋能）
		KillEmpowerManager.writeState(player, state.consume());

		if (redForm) {
			net.jackcooper.shapeShifterCurseAddon.action.SscAddonActions.launchFoxFireball(player, true);
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_FOX_SCREECH, SoundCategory.PLAYERS, 1.0f, 1.0f);
			return true;
		}

		// 免费释放：不扣 mana、不检查 CD/mana 条件；原 CD 若在走不受影响（不重置）
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		RegistryKey<DamageType> magicKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "magic"));
		Box box = player.getBoundingBox().expand(BREATH_DISTANCE).stretch(look.multiply(BREATH_DISTANCE));
		world.getEntitiesByClass(LivingEntity.class, box, t -> t != player && t.isAlive() && !t.isSpectator()).forEach(t -> {
			if (WhitelistUtils.isProtected(player, t)) return;
			Vec3d toT = t.getPos().add(0, t.getHeight() / 2.0, 0).subtract(eye).normalize();
			if (look.dotProduct(toT) > 0.8 && player.squaredDistanceTo(t) < BREATH_DISTANCE * BREATH_DISTANCE) {
				float dmg = t instanceof net.minecraft.entity.player.PlayerEntity ? BREATH_DAMAGE : BREATH_DAMAGE * nonPlayerMult;
				t.timeUntilRegen = 0;	// 命中前清：不被普攻无敌帧吞
				if (t.damage(t.getDamageSources().create(magicKey, player, player), dmg)) {
					t.timeUntilRegen = 0;	// 命中后清：不留无敌帧反吞普攻
					t.addStatusEffect(new StatusEffectInstance(SscAddon.FOX_FIRE_BURN, BURN_DURATION, 0), player);
					KillEmpowerManager.trackBurn(player, t, BURN_DURATION, false);
					if (t instanceof net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor acc) {
						acc.sscAddon$setIgniterUuid(player.getUuid());
					}
				}
			}
		});
		// 表现复刻 power JSON：前方多层魂火粒子 + 火焰充能/狐狸叫音效
		for (int d = 1; d <= 5; d++) {
			double spread = 0.2 * d;
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
					player.getX() + look.x * d, player.getY() + 1.2, player.getZ() + look.z * d,
					4 + d * 3, spread, 0.4 + spread * 0.5, spread, 0.04);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_FOX_SCREECH, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return true;
	}
}
