package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.entity.BridgeWebBullet;
import net.jackcooper.shapeShifterCurseAddon.entity.WebMembraneBullet;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.state.RegSpiderMoonWeaverStateComponent;
import net.jackcooper.shapeShifterCurseAddon.state.SpiderMoonWeaverStateComponent;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.onixary.shapeShifterCurseFabric.blocks.RegCustomBlock;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.RegManaComponent;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 月织蛛「织网术」主技能 - 服务端状态机。
 *
 * <p>单主技能键（sp_primary），客户端边沿检测发包驱动：
 * <ul>
 *   <li><b>潜行双击主键</b> → {@link #toggleMode} 切换 搭路 / 攻击 模式（客户端手势判定，蓄力中禁止切换）。</li>
 *   <li><b>按住主键蓄力 → 松开</b> → {@link #start} / {@link #release} 发动当前模式：</li>
 *   <ul>
 *     <li>搭路模式：不潜行 → 发射附属 {@link BridgeWebBullet} 蛛丝弹（命中方块建蛛丝梯，梯块可替代减速网膜；
 *         命中实体施缠绕）；
 *         潜行 → 脚下平铺 {@link AddonWebBridgeAction} 蛛丝桥（同样可替代网膜）。</li>
 *     <li>攻击模式 → 发 {@link WebMembraneBullet}，命中后按档在半径 3/5/6 格内贴面铺减速蛛网。</li>
 *   </ul>
 * </ul>
 * 蓄力分 3 档：<20t? → tier1、≥40t → tier2、≥60t → tier3（满 3 秒封顶）；边蓄力边耗 mana，
 * mana 不足自动释放。
 *
 * <p>模式存于独立 CCA 组件 {@link SpiderMoonWeaverStateComponent}（0=搭路 / 1=攻击，不挂 origin、跨会话/跨形态/死亡重生保留）；蓄力档位存于
 * 本类服务端 map（瞬态）。CD 由 power form_spider_moon_weaver_web 的 JSON 字段配置。
 */
public final class SpiderMoonWeaverWebManager {

	private static final int MODE_BRIDGE = SpiderMoonWeaverStateComponent.MODE_BRIDGE;
	private static final int MODE_ATTACK = SpiderMoonWeaverStateComponent.MODE_ATTACK;

	// 以下已登记常量均为默认值；运行时从 balance 快照读取（abilities.moon_weaver_web）
	private static final int MAX_TICKS = 60;        // 满档蓄力 3 秒
	private static final int TIER1_TICKS = 20;      // ≥1 秒抵 tier1
	private static final int TIER2_TICKS = 40;      // ≥2 秒进 tier2
	// 释放后 CD 由 power JSON 配置（见类注释），原 CD_TICKS_PER_TIER 常量已删
	private static final double START_MANA = 6.0;   // 起手需 6 mana（沿用原版蜘蛛）
	private static final double MANA_PER_TICK = 0.25;

	// 阶段 5：服务端权威快照读取（快照未初始化回退默认常量）
	private static final BalanceReader BAL = new BalanceReader("abilities.moon_weaver_web");
	/** 统一冷却服务的稳定技能 ID（网络包触发型）。 */
	private static final String SKILL_ID = "my_addon:form_spider_moon_weaver_web";

	/** HUD 门槛同源：织网起手法力（balance 可调；HUD 展示用）。 */
	public static double startManaForHud() { return BAL.d("start_mana", START_MANA); }

	/** UUID -> {已蓄力 tick 数}。服务端权威，多人一致。 */
	private static final Map<UUID, ChargeState> CHARGING = new ConcurrentHashMap<>();
	/** 平铺搭桥蓄力中的玩家（双击长按 / 潜行长按触发），release 时走脚下平铺而非蛛丝弹。 */
	private static final class ChargeState { int ticks; final long castId; ChargeState(long castId) { this.castId = castId; } }
	private static final java.util.Set<UUID> FLAT_CHARGING = ConcurrentHashMap.newKeySet();

	private SpiderMoonWeaverWebManager() {}

	private static boolean isSpiderMoonWeaver(ServerPlayerEntity player) {
		return FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER);
	}

	private static ManaComponent mana(ServerPlayerEntity player) {
		return RegManaComponent.MANA.get(player);
	}

	/** 读取玩家当前模式（0=搭路 / 1=攻击）。 */
	private static int getMode(ServerPlayerEntity player) {
		return RegSpiderMoonWeaverStateComponent.SPIDER_MOON_WEAVER_STATE.get(player).getMode();
	}

	/** 潜行双击主键：切换 搭路 / 攻击 模式（客户端手势判定，蓄力中禁止切换）。 */
	public static void toggleMode(ServerPlayerEntity player) {
		if (!isSpiderMoonWeaver(player)) return;
		if (CHARGING.containsKey(player.getUuid())) return;
		int next = (getMode(player) == MODE_ATTACK) ? MODE_BRIDGE : MODE_ATTACK;
		RegSpiderMoonWeaverStateComponent.SPIDER_MOON_WEAVER_STATE.get(player).setMode(next);
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.8f, next == MODE_ATTACK ? 1.4f : 0.9f);
		player.sendMessage(Text.translatable(next == MODE_ATTACK
				? "message.my_addon.spider_moon_weaver.mode.attack"
				: "message.my_addon.spider_moon_weaver.mode.bridge"), true);
	}

	/** 主键按住开始蓄力（服务端重校验 form / CD / mana）。 */
	public static void start(ServerPlayerEntity player) {
		if (CHARGING.containsKey(player.getUuid())) return;
		if (!isSpiderMoonWeaver(player)) return;
		// 统一服务门禁 + 建档（on_release：松键按档起算）
		if (!beginCharge(player)) return;
		CHARGING.put(player.getUuid(), new ChargeState(net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.currentCastId(player, SKILL_ID)));
		FLAT_CHARGING.remove(player.getUuid()); // 普通蓄力 → 蛛丝弹
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_WOOL_HIT, SoundCategory.PLAYERS, 0.7f, 1.2f);
	}

	/** 主键按住开始「平铺搭桥」蓄力（双击长按 / 潜行长按触发；服务端重校验 form / CD / mana）。 */
	public static void startFlat(ServerPlayerEntity player) {
		if (CHARGING.containsKey(player.getUuid())) return;
		if (!isSpiderMoonWeaver(player)) return;
		if (!beginCharge(player)) return;
		CHARGING.put(player.getUuid(), new ChargeState(net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.currentCastId(player, SKILL_ID)));
		FLAT_CHARGING.add(player.getUuid()); // 平铺蓄力 → 脚下平铺
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_WOOL_HIT, SoundCategory.PLAYERS, 0.7f, 1.0f);
	}

	/** 登记施放；power JSON cooldown = 满档（3 档）CD，松手时按档位比例折算。 */
	private static boolean beginCharge(ServerPlayerEntity player) {
		if (!net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ready(player, SKILL_ID)) return false;
		if (mana(player).getMana() < BAL.d("start_mana", START_MANA)) return false;
		return net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.begin(player, SKILL_ID);
	}

	/** 每服务端 tick 对每个在线玩家调用（挂在 SscAddonServerEvents 世界 tick 循环）。 */
	public static void tick(ServerPlayerEntity player) {
		ChargeState s = CHARGING.get(player.getUuid());
		if (s == null) return;
		if (player.isDead() || !isSpiderMoonWeaver(player)) {
			cancel(player); // 死亡 / 形态丢失 → 取消，不结算
			return;
		}
		if (s.ticks < BAL.i("max_ticks", MAX_TICKS)) {
			double manaPerTick = BAL.d("mana_per_tick", MANA_PER_TICK);
			ManaComponent m = mana(player);
			if (m.getMana() < manaPerTick) {
				release(player); // mana 耗尽 → 自动释放当前档
				return;
			}
			m.consumeMana(manaPerTick);
			s.ticks++;
			ServerWorld sw = (ServerWorld) player.getWorld();
			float chime = tierChimePitch(s.ticks);
			if (chime > 0f) {
				// 跨档瞬间：靠齐原版 SSC 蓄力完成音效（note_block hat+snare 升调）+ cloud 粒子
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, chime);
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), SoundCategory.PLAYERS, 0.8f, chime);
				net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(),
						10, 0.5, 0.5, 0.5, 0.0);
			} else if (s.ticks % 4 == 0) {
				net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(),
						4, 0.4, 0.5, 0.4, 0.0);
			}
		}
		// 达到满档后保持 tier3、不再耗 mana，直到松键释放
	}

	/** 蓄力跨档（20/40/60t）的报音音高（靠齐 SSC：1.0 / 1.19 / 1.414）；非跨档点返回 0。 */
	private static float tierChimePitch(int ticks) {
		if (ticks == BAL.i("tier1_ticks", TIER1_TICKS)) return 1.0f;
		if (ticks == BAL.i("tier2_ticks", TIER2_TICKS)) return 1.19f;
		if (ticks == BAL.i("max_ticks", MAX_TICKS)) return 1.414f;
		return 0f;
	}

	/** 松开主键 / 自动释放：按当前档 + 模式 + 平铺标志发动（档位 CD = tier×cd_per_tier，统一服务结算）。 */
	public static void release(ServerPlayerEntity player) {
		ChargeState s = CHARGING.remove(player.getUuid());
		boolean flat = FLAT_CHARGING.remove(player.getUuid());
		if (s == null) return;
		int ticks = s.ticks;
		int tier = ticks >= BAL.i("max_ticks", MAX_TICKS) ? 3
				: (ticks >= BAL.i("tier2_ticks", TIER2_TICKS) ? 2 : 1);
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.retune(player, SKILL_ID, s.castId,
				(int) Math.ceil(net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.cooldown(player, SKILL_ID) * tier / 3.0));
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.completed(player, SKILL_ID, s.castId);
		if (getMode(player) == MODE_ATTACK) {
			fireAttack(player, tier);
		} else if (flat) {
			fireBridgeFlat(player, tier); // 双击长按 / 潜行长按 → 脚下平铺
		} else {
			fireBridgeShoot(player, tier); // 单击长按 → 发射蛛丝弹
		}
	}

	/** 取消蓄力（不结算、不进 CD：统一服务失败档=0 清记录）。 */
	public static void cancel(ServerPlayerEntity player) {
		ChargeState s = CHARGING.remove(player.getUuid());
		FLAT_CHARGING.remove(player.getUuid());
		net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.cancelled(player, SKILL_ID, s == null ? -1 : s.castId);
	}

	/** 玩家掉线清理，防僵尸 UUID 残留。 */
	public static void onDisconnect(UUID uuid) {
		CHARGING.remove(uuid);
		FLAT_CHARGING.remove(uuid);
	}

	// 攻击模式：发射蛛丝弹（命中铺减速网）
	private static void fireAttack(ServerPlayerEntity player, int tier) {
		WebMembraneBullet bullet = new WebMembraneBullet(player, tier);
		bullet.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 1.6f, 1.0f);
		player.getWorld().spawnEntity(bullet);
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.9f, 0.7f);
	}

	// 搭路模式-平铺：在玩家前方脚同高水平面铺蛛丝桥（平地也能在前方铺出）；桥块可替代减速网膜
	private static void fireBridgeFlat(ServerPlayerEntity player, int tier) {
		int length = tier >= 3 ? 18 : (tier >= 2 ? 14 : 10);
		BlockPos pos = player.getBlockPos(); // 脚同高水平面（空气层），从前方第一格开始能铺上
		Direction dir = player.getHorizontalFacing();
		AddonWebBridgeAction.BuildWebBridge(player.getWorld(), pos, dir,
				new AddonWebBridgeAction.WebBridgeConfig(length, 0), RegCustomBlock.TEMP_WEB_BRIDGE);
		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.6f, 0.8f);
		net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.spawn(sw, player, ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(),
				40, 1.0, 0.5, 1.0, 0.0);
	}

	// 搭路模式-发射：发射附属 BridgeWebBullet 蛛丝弹（命中方块走移植版 BuildWebLadder 建蛛丝梯——梯块可替代网膜；命中实体施缠绕）
	private static void fireBridgeShoot(ServerPlayerEntity player, int tier) {
		BridgeWebBullet bullet = new BridgeWebBullet(player, tier);
		bullet.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 2.0f, 1.0f); // 原版 speed=2, divergence=1
		player.getWorld().spawnEntity(bullet);
	}
}
