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
 * 「潮汐之灵」剧情链（阿澪 Aling 解锁，全程服务端权威，状态由 {@link TideSpiritStoryState} 持久化）。
 * <p>机制照搬「月痕之力」（{@link MoonScarStoryManager}），区别：
 * <ol>
 *     <li>前置形态：荧光幼灵（axolotl_fluorescent）而非 sp 使魔。</li>
 *     <li>解锁成就：{@code ssc_addon:tide_spirit_power}（集齐蔚蓝港7本书 + 荧光幼灵形态成就）。</li>
 *     <li>目标形态：阿澪（axolotl_aling），<b>永久</b>（不自动变回，无月髓环免费还原）。</li>
 *     <li>变身后文案：「这是...哪里？我的家呢？」</li>
 * </ol>
 * <p>剧情真睡的唤醒抑制 / 阻止跳夜复用 {@link MoonScarStoryManager#isStorySleeping} 判断
 * （{@code SscPlayerMixin} 据此决定是否顶住诅咒之月强制唤醒、是否阻止跳夜），故本类进入真睡时
 * 同步把 uuid 放入 {@code MoonScarStoryManager.STORY_SLEEPING}（通过 {@link #isStorySleeping} 统一判断）。
 */
public final class TideSpiritStoryManager {
	/** 解锁成就：集齐蔚蓝港7本书 + 荧光幼灵形态。 */
	public static final Identifier TIDE_SPIRIT_POWER_ADV = new Identifier("ssc_addon", "tide_spirit_power");
	private static final int TIP_CHECK_INTERVAL = 20;
	private static final int STORY_SLEEP_DURATION = 100;

	/** 阿澪剧情真睡中的玩家 → 已睡 tick 计数。 */
	private static final Map<UUID, Integer> ALING_SLEEPING = new HashMap<>();

	private TideSpiritStoryManager() {
	}

	/** 供 SscPlayerMixin 统一查询：该玩家是否处于任一剧情真睡中（月痕之力 或 潮汐之灵）。 */
	public static boolean isStorySleeping(UUID playerUuid) {
		return ALING_SLEEPING.containsKey(playerUuid);
	}

	public static void register() {
		// 荧光幼灵在诅咒之月夜晚上床 → 进入「剧情真睡」
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayerEntity player && !player.getWorld().isClient) {
				tryBeginStorySleep(player);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!ALING_SLEEPING.isEmpty()) {
				tickStorySleep(server);
			}
			if (server.getTicks() % TIP_CHECK_INTERVAL == 0) {
				TideSpiritStoryState state = TideSpiritStoryState.get(server);
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					tickTip(player, state);
				}
			}
		});
	}

	private static void tickTip(ServerPlayerEntity player, TideSpiritStoryState state) {
		if (state.tippedPlayers.contains(player.getUuid())) return;
		if (!FormUtils.isAxolotlFluorescent(player)) return;   // 必须处于荧光幼灵
		if (!hasTideSpiritPower(player)) return;               // 必须已解锁潮汐之灵成就
		player.sendMessage(
				Text.translatable("message.ssc_addon.tide_spirit.whisper").formatted(Formatting.AQUA, Formatting.ITALIC),
				false);
		state.tippedPlayers.add(player.getUuid());
		state.markDirty();
	}

	/** START_SLEEPING：荧光幼灵解锁成就后、在诅咒之月夜晚上床 → 进入「剧情真睡」。 */
	private static void tryBeginStorySleep(ServerPlayerEntity player) {
		World world = player.getWorld();
		if (!FormUtils.isAxolotlFluorescent(player)) return;
		if (!hasTideSpiritPower(player)) return;
		if (!(CursedMoon.isCursedMoonDay(world) && CursedMoon.isNight(world))) return;
		ALING_SLEEPING.putIfAbsent(player.getUuid(), 0);
	}

	private static void tickStorySleep(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> it = ALING_SLEEPING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (!player.isSleeping()) {
				it.remove();
				continue;
			}
			int slept = entry.getValue() + 1;
			if (slept < STORY_SLEEP_DURATION) {
				entry.setValue(slept);
				continue;
			}
			it.remove();
			finalizeStorySleep(player);
		}
	}

	/** 「剧情真睡」睡满：叫醒 → 瞬间无动画变阿澪（永久）→ 广播文案。 */
	private static void finalizeStorySleep(ServerPlayerEntity player) {
		if (player.isSleeping()) {
			player.wakeUp(true, true);
		}
		if (!FormUtils.isAxolotlFluorescent(player)) return; // 期间形态已变则不再转化
		IForm alingForm = RegPlayerForms.getPlayerForm(FormIdentifiers.AXOLOTL_ALING);
		if (alingForm == null) return;
		// 起床即变：immediatelyTransform 瞬间换形态、不播放变身动画
		TransformManager.immediatelyTransform(player, alingForm);
		World world = player.getWorld();
		player.sendMessage(
				Text.translatable("message.ssc_addon.tide_spirit.become_aling").formatted(Formatting.AQUA, Formatting.ITALIC),
				false);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundCategory.PLAYERS, 1.0F, 0.8F);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.2F);
	}

	private static boolean hasTideSpiritPower(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) return false;
		Advancement adv = server.getAdvancementLoader().get(TIDE_SPIRIT_POWER_ADV);
		if (adv == null) return false;
		return player.getAdvancementTracker().getProgress(adv).isDone();
	}
}
