package net.onixary.shapeShifterCurseFabric.ssc_addon.story;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancement.Advancement;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 「月痕之力」剧情链（全程服务端权威，状态由 {@link MoonScarStoryState} 持久化）。
 * <ol>
 *     <li>解锁 {@code ssc_addon:moon_scar_power} 成就且玩家处于 sp 使魔时，悄悄话提示一次，引导其去睡觉。</li>
 *     <li>sp 使魔在诅咒之月夜晚上床睡觉 → 真正进入原版睡眠状态（真躺床、真黑屏过渡、真 {@code isSleeping}）：
	 *     由 mixin 仅顶住诅咒之月的强制唤醒（绕过 SSC 禁睡、让玩家能真睡）、并阻止跳夜（不推进时间）；
 *     睡满约 5 秒后由本模组主动叫醒、瞬间变成 red（不播放变身动画，不跳夜）。
 *     若玩家中途下床 / 被取消睡眠（例如手动起床、離线），演出取消。</li>
 *     <li>处于剧情 red 的玩家，随时随地用月髓环可免费变回 sp 使魔（不消耗物品）。</li>
 * </ol>
 * 形态本身的存档/重生由主包形态系统保证（red 死亡重生仍为 red，变回 sp 后才是 sp）。
 */
public final class MoonScarStoryManager {
	public static final Identifier MOON_SCAR_POWER_ADV = new Identifier("ssc_addon", "moon_scar_power");
	/** 未提示玩家的成就检查降频：每 20 tick（约 1 秒）一次，已提示玩家直接跳过。 */
	private static final int TIP_CHECK_INTERVAL = 20;
	/** 真睡变身所需睡眠时长：100 tick（约 5 秒，= vanilla 睡眠阈值）。睡满后由本模组主动叫醒变 red。 */
	private static final int STORY_SLEEP_DURATION = 100;
	/**
	 * 正在进行「剧情真睡」的玩家 → 已睡 tick 计数。仅服务端主线程访问。
	 * 期间 {@code SscAddonLivingEntityMixin} 仅顶住诅咒之月「强制唤醒」（让玩家真睡不被弹起），
	 * {@code SscPlayerMixin} 让 {@code canResetTimeBySleeping} 返回 false 以阻止跳夜（不推进时间）；
	 * 计数达 STORY_SLEEP_DURATION 后由 {@link #tickStorySleep} 主动叫醒并变身（不跳夜，故不会卡在床上）。
	 */
	private static final Map<UUID, Integer> STORY_SLEEPING = new HashMap<>();

	private MoonScarStoryManager() {
	}

	/** 供 wakeUp 抑制 / canResetTimeBySleeping 抑制 mixin 查询：该玩家是否处于「剧情真睡」中。 */
	public static boolean isStorySleeping(UUID playerUuid) {
		return STORY_SLEEPING.containsKey(playerUuid);
	}

	public static void register() {
		// sp 使魔在诅咒之月夜晚上床 → 进入「剧情真睡」（真睡眠、仅顶住 SSC 禁睡、阻止跳夜，睡满约 5 秒后叫醒变 red）。
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayerEntity player && !player.getWorld().isClient) {
				tryBeginStorySleep(player);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// 「剧情真睡」计时：每 tick 推进，睡满后叫醒并变身（仅在有玩家真睡时执行）。
			if (!STORY_SLEEPING.isEmpty()) {
				tickStorySleep(server);
			}
			// 低语提示：解锁成就且为 sp 使魔 → 发一次；降频每 20 tick 检查。
			if (server.getTicks() % TIP_CHECK_INTERVAL == 0) {
				MoonScarStoryState state = MoonScarStoryState.get(server);
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					tickTip(player, state);
				}
			}
		});
	}

	private static void tickTip(ServerPlayerEntity player, MoonScarStoryState state) {
		if (state.tippedPlayers.contains(player.getUuid())) return; // 已提示过，永不再查
		if (!FormUtils.isFamiliarFoxSP(player)) return;             // 必须处于 sp 使魔
		if (!hasMoonScarPower(player)) return;                      // 必须已解锁月痕之力成就
		player.sendMessage(
				Text.translatable("message.ssc_addon.moon_scar.whisper").formatted(Formatting.GRAY, Formatting.ITALIC),
				false);
		state.tippedPlayers.add(player.getUuid());
		state.markDirty();
	}

	/** START_SLEEPING：sp 使魔解锁成就后、在诅咒之月夜晚上床 → 进入「剧情真睡」（不立即变身，让玩家真正睡一觉）。 */
	private static void tryBeginStorySleep(ServerPlayerEntity player) {
		World world = player.getWorld();
		if (!FormUtils.isFamiliarFoxSP(player)) return;
		if (!hasMoonScarPower(player)) return;
		if (!(CursedMoon.isCursedMoonDay(world) && CursedMoon.isNight(world))) return;
		// 加入真睡追踪：mixin 据此仅顶住 SSC 的诅咒之月强制唤醒（让玩家能真睡不被弹起）、并阻止跳夜。
		// 玩家保持原版睡眠状态（原版睡眠本身锁住视角/移动、呈现睡姿与黑屏过渡），不需 STUN。
		STORY_SLEEPING.putIfAbsent(player.getUuid(), 0);
	}

	/** 每 tick 推进「剧情真睡」计时：睡满后叫醒变身；玩家中途下床/離线则取消演出。 */
	private static void tickStorySleep(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> it = STORY_SLEEPING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				it.remove(); // 玩家離线：清理，待下次重新尝试
				continue;
			}
			// 玩家已不在睡眠状态（被其它原因唤醒/下床）：取消演出，不变身。
			// （SSC 的诅咒之月唤醒已被抑制，所以这里不在睡眠 = 玩家自主下床或受到不可抑制的唤醒。）
			if (!player.isSleeping()) {
				it.remove();
				continue;
			}
			int slept = entry.getValue() + 1;
			if (slept < STORY_SLEEP_DURATION) {
				entry.setValue(slept);
				continue;
			}
			// 睡满：先移除标记（解除唤醒抑制 / 恢复 canResetTimeBySleeping），再主动叫醒并变身
			it.remove();
			finalizeStorySleep(server, player);
		}
	}

	/** 「剧情真睡」睡满：主动把玩家从床上叫醒 → 瞬间无动画变 red → 记入剧情 red 并广播低语。 */
	private static void finalizeStorySleep(MinecraftServer server, ServerPlayerEntity player) {
		if (player.isSleeping()) {
			// 主动起床（此时标记已移除，唤醒不再被抑制）；演出期间已阻止 canResetTimeBySleeping，故不跳夜
			player.wakeUp(true, true);
		}
		if (!FormUtils.isFamiliarFoxSP(player)) return; // 期间形态已变则不再转化
		IForm redForm = RegPlayerForms.getPlayerForm(FormIdentifiers.FAMILIAR_FOX_RED);
		if (redForm == null) return;
		// 起床即变：setFormDirectly 瞬间换形态、不播放变身动画
		TransformManager.immediatelyTransform(player, redForm);
		MoonScarStoryState state = MoonScarStoryState.get(server);
		state.storyRedPlayers.add(player.getUuid());
		state.markDirty();
		World world = player.getWorld();
		player.sendMessage(
				Text.translatable("message.ssc_addon.moon_scar.become_red").formatted(Formatting.DARK_RED, Formatting.ITALIC),
				false);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	/**
	 * 月髓环触发：若玩家处于"剧情 red"，免费变回 sp 使魔（不消耗物品），任意时间地点均可。
	 *
	 * @return true 表示已处理（调用方应直接返回、不再走常规升级逻辑、不消耗物品）。
	 */
	public static boolean tryFreeRevertFromStoryRed(PlayerEntity player) {
		if (player.getWorld().isClient) return false;
		if (!(player instanceof ServerPlayerEntity sp)) return false;
		MinecraftServer server = sp.getServer();
		if (server == null) return false;

		MoonScarStoryState state = MoonScarStoryState.get(server);
		if (!state.storyRedPlayers.contains(sp.getUuid())) return false;

		if (!FormUtils.isFamiliarFoxRed(sp)) {
			// 标记与实际形态不一致（异常情况）：清理脏标记并放行常规逻辑，避免卡死
			state.storyRedPlayers.remove(sp.getUuid());
			state.markDirty();
			return false;
		}

		IForm spForm = RegPlayerForms.getPlayerForm(FormIdentifiers.FAMILIAR_FOX_SP);
		if (spForm == null) return false;

		TransformManager.immediatelyTransform(sp, spForm);
		state.storyRedPlayers.remove(sp.getUuid());
		state.markDirty();

		World world = sp.getWorld();
		sp.sendMessage(
				Text.translatable("message.ssc_addon.moon_scar.revert_sp").formatted(Formatting.GRAY, Formatting.ITALIC),
				false);
		world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean hasMoonScarPower(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return false;
		Advancement adv = server.getAdvancementLoader().get(MOON_SCAR_POWER_ADV);
		if (adv == null) return false;
		return player.getAdvancementTracker().getProgress(adv).isDone();
	}
}
