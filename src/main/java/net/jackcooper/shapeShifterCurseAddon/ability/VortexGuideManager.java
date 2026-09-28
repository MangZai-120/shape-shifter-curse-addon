package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.evolution.AxolotlTree;
import net.jackcooper.shapeShifterCurseAddon.evolution.RegEvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「涡流引导」次技能（sp_secondary）—— 服务端引导状态机。
 *
 * <p>按次技能键开始引导 3 秒：期间移速降低约 50%（缓慢 III），持续回血共 6 颗心（12 生命），
 * 引导完成后额外获得 2 颗黄心（4 吸收）。CD 8 秒，不消耗湿润度。
 * 所有判定在服务端，节点未解锁 / 非本形态不触发；死亡 / 形态丢失中断且不进 CD。</p>
 */
public final class VortexGuideManager {

	// 以下均为默认值；运行时从 balance 快照读取（abilities.vortex_guide）
	private static final int CHANNEL_TICKS = 60;   // 引导 3 秒
	// CD（8 秒）由统一冷却服务 + power JSON 配置管理，原 CD_TICKS 常量已删
	private static final int HEAL_INTERVAL = 10;   // 每 0.5 秒回血一次
	private static final float HEAL_PER_TICK = 2.0f; // 每次回 1 心（共 6 次 = 6 心）
	private static final int ABSORPTION_DURATION = 600; // 黄心持续 30 秒

	/** balance 快照读取（快照未初始化回退默认常量） */
	private static final BalanceReader BAL = new BalanceReader("abilities.vortex_guide");
	/** 统一冷却服务的稳定技能 ID（网络包触发型）。 */
	private static final String SKILL_ID = "my_addon:form_upgrade_axolotl_vortex_guide";

	private static final Map<UUID, ChannelState> CHANNELING = new ConcurrentHashMap<>();

	private static final class ChannelState { int ticks; final long castId; ChannelState(long castId) { this.castId = castId; } }
	private VortexGuideManager() {
	}

	/** 客户端按次技能键（无 payload）。服务端校验并开始引导。 */
	public static void onKeyPress(ServerPlayerEntity player) {
		if (CHANNELING.containsKey(player.getUuid())) return; // 引导中不可重入
		if (!FormUtils.isUpgradeAxolotl(player)) return;
		// 节点门控：未解锁「涡流引导」不触发
		if (!RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(AxolotlTree.NODE_VORTEX_GUIDE)) return;
		if (!net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.begin(player, SKILL_ID)) return; // CD 中

		CHANNELING.put(player.getUuid(), new ChannelState(net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.currentCastId(player, SKILL_ID)));
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.7f, 1.4f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		ChannelState t = CHANNELING.get(player.getUuid());
		if (t == null) return;
		if (player.isDead() || !FormUtils.isUpgradeAxolotl(player)) {
			cancel(player); // 死亡 / 形态丢失 → 中断，不进 CD
			return;
		}
		int tick = ++t.ticks;
		ServerWorld sw = (ServerWorld) player.getWorld();
		int channelTicks = BAL.i("channel_ticks", CHANNEL_TICKS);
		int healInterval = BAL.i("heal_interval", HEAL_INTERVAL);

		// 引导期间减速约 50%（缓慢 III，短时长滚动续期，引导结束即消散）
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 2, false, false, false));

		// 周期回血（共 6 心）
		if (tick % healInterval == 0) {
			player.heal((float) BAL.d("heal_per_tick", HEAL_PER_TICK));
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.6f, 1.4f);
		}

		// 环绕水/心形引导粒子
		double ang = tick * 0.4;
		double r = 0.8;
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.FALLING_WATER,
				player.getX() + Math.cos(ang) * r, player.getY() + 1.0, player.getZ() + Math.sin(ang) * r,
				2, 0.05, 0.2, 0.05, 0.0);
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.HEART,
				player.getX(), player.getY() + 1.4, player.getZ(), 1, 0.3, 0.3, 0.3, 0.0);

		if (tick >= channelTicks) {
			complete(player); // 引导完成 → 2 黄心 + CD
		}
	}

	/** 引导完成：授予 2 黄心（4 吸收）并进入 CD（统一服务 on_release 结算）。 */
	private static void complete(ServerPlayerEntity player) {
		ChannelState state = CHANNELING.remove(player.getUuid());
        if (state == null) return;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, BAL.i("absorption_duration", ABSORPTION_DURATION), 0, false, false, true));
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.completed(player, SKILL_ID, state.castId);
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.6f);
		// owner 打标：仅本人第一人称避让
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.6, 0.6, 0.6, 0.5);
	}

	/** 中断（不进 CD、不给黄心）。 */
	public static void cancel(ServerPlayerEntity player) {
		ChannelState state = CHANNELING.remove(player.getUuid());
        if (state == null) return;
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.cancelled(player, SKILL_ID, state.castId);
	}

	public static void onPlayerDisconnect(UUID uuid) {
		CHANNELING.remove(uuid);
	}
}
