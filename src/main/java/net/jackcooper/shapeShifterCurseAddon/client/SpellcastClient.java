package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;

/**
 * 月尘魔法书施法客户端检测器（jackcooper）。仅在佩戴魔法书时生效。
 * <p>施法键 / 7 直达键：上升沿检测 → C2S 施法包（带槽 index）。切换键 + 滚轮：由
 * {@code SpellcastMouseScrollMixin} 调用 {@link #cycleSelected} 切换当前选中槽并同步服务端。</p>
 * <p><b>法力不足三连击降档（阶段 C §6.4 变体，用户 2026-09-18 定稿）</b>：选中槽为高阶卷轴且
 * 书法力不足时，1 秒内连按三次施法键 → 第三次改为发送「临时降档施放」包（服务端降到付得起的
 * 最高档，本次 CD ×1.2 惩罚）；不写卷轴 NBT、下次施法仍按原档。法力充足时第一次按键即正常施放。</p>
 * <p><b>按住瞄准型法术</b>（{@link Spell#getAimMaxRange()} > 0，如陨火术）：按住施法键时
 * 在准星落点本地粒子圈持续预览（随准星实时移动、半径含等级缩放），松开才发送施法包；
 * 冷却中不预览不发送。直达键仍为按下立即施放。所有伤害/冷却/法力判定在服务端。</p>
 */
@Environment(EnvType.CLIENT)
public final class SpellcastClient {
	private static int selectedSlot = 0;
	private static final boolean[] wasDirectPressed = new boolean[7];
	private static boolean wasCastPressed = false;
	private static int gestureToken;
	private static int gestureKey = -1;
	private static int gestureSlot = -1;
	private static final net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.InputGuard inputGuard =
			new net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.InputGuard();
	private static int cancelToken = -1;
	/** 三连击降档检测：上次按下时刻 + 连按计数（1 秒窗口内累计三次按下沿）。 */
	private static long downgradePressAt = -1L;
	private static int downgradePressCount = 0;

	private SpellcastClient() {
	}

	public static void register() {
		net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(SpellcastClient::onClientTick);
	}

	/** 客户端当前佩戴的魔法书（未装备返回 null）。 */
	public static ItemStack getEquippedBook() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return null;
		}
		return TrinketUtils.findFirstEquipped(mc.player, s -> s.getItem() == SscAddon.MOON_DUST_SPELLBOOK);
	}

	public static boolean hasBookEquipped() {
		ItemStack book = getEquippedBook();
		return book != null && !book.isEmpty();
	}

	public static boolean isSwitchKeyDown() {
		return SpellcastKeybindings.KEY_SWITCH != null && SpellcastKeybindings.KEY_SWITCH.isPressed();
	}

	public static int getSelectedSlot() {
		return selectedSlot;
	}

	/** 滚轮切换当前选中槽（首尾相连）。dir=+1 下一个，-1 上一个。由滚轮 mixin 调用。 */
	public static void cycleSelected(int dir) {
		ItemStack book = getEquippedBook();
		if (book == null || book.isEmpty()) {
			return;
		}
		// 只在已装备卷轴的槽之间循环（跳过空槽）；全空不动作
		int next = SpellbookData.nextFilledSlot(book, selectedSlot, dir);
		if (next < 0 || next == selectedSlot) {
			return;
		}
		selectedSlot = next;
		sendSelect(selectedSlot);
		cancelInputForSelection();
	}

	/**
	 * 循环当前选中槽的施放档位（低阶选档，阶段 C §6.4）。
	 * 循环序：满档 → 满档-1 → … → 1 → 回满档；一级卷轴无意义不动作。
	 * 服务端权威写入，这里只发请求。（保留供潜行+施法键等后续入口复用）
	 */
	public static void cycleCastLevel(int slot) {
		ItemStack book = getEquippedBook();
		if (book == null || book.isEmpty()) {
			return;
		}
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		if (ScrollData.getSpell(scroll) == null) {
			return;
		}
		int scrollLevel = ScrollData.getLevel(scroll);
		if (scrollLevel <= 1) {
			return; // 一级卷轴无可降档
		}
		int current = ScrollData.getCastLevel(scroll);
		int next = current <= 1 ? scrollLevel : current - 1;
		sendSetCastLevel(slot, next);
	}

	/**
	 * 法力不足时的三连击处理：返回 true = 已触发临时降档施放（本次按键不再走正常施法路径）。
	 * 第三次按下沿发「降档施放」包（服务端自动降到付得起的最高档 + CD ×1.2 惩罚）；
	 * 前两次仅计数并提示剩余次数。窗口 1 秒，超窗重置。
	 */
	private static boolean handleTriplePressDowngrade(ClientPlayerEntity player, ItemStack book, int slot) {
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		if (ScrollData.getSpell(scroll) == null || ScrollData.getLevel(scroll) <= 1) {
			return false; // 无魔法或一级卷轴：不提供降档
		}
		if (ScrollData.isOnCooldown(scroll, player.getWorld())) {
			return false; // CD 中不计数
		}
		long now = System.currentTimeMillis();
		if (now - downgradePressAt > 1000L) {
			downgradePressCount = 0; // 超窗重置
		}
		downgradePressAt = now;
		downgradePressCount++;
		if (downgradePressCount >= 3) {
			downgradePressCount = 0;
			sendCastDowngraded(slot);
			return true;
		}
		player.sendMessage(Text.translatable("message.ssc_addon.spell.downgrade_hint",
				3 - downgradePressCount), true);
		return false;
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			resetKeys();
			net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud.clear();
			return;
		}
		boolean castPressed = SpellcastKeybindings.KEY_CAST != null && SpellcastKeybindings.KEY_CAST.isPressed();
		boolean anyPressed = castPressed;
		for (KeyBinding key : SpellcastKeybindings.KEY_DIRECT) anyPressed |= key != null && key.isPressed();
		if (client.currentScreen != null) {
			if (gestureKey >= 0) sendRelease(gestureToken);
			if (cancelToken >= 0) sendCancelHold(cancelToken, false);
			cancelToken = -1;
			gestureKey = -1;
			inputGuard.block();
		}
		if (inputGuard.consume(anyPressed || client.currentScreen != null)) {
			updatePressedKeys(castPressed);
			return;
		}
		if (cancelToken >= 0 && !castPressed) {
			sendCancelHold(cancelToken, false);
			cancelToken = -1;
		}
		ItemStack book = getEquippedBook();
		if (book == null || book.isEmpty()) {
			if (gestureKey >= 0 && !keyPressed(gestureKey)) {
				sendRelease(gestureToken);
				gestureKey = -1;
			}
			updatePressedKeys(castPressed);
			return;
		}
		int count = SpellbookData.getSlotCount(book);
		if (selectedSlot >= count) {
			selectedSlot = 0;
		}
		// 选中槽为空（被取走卷轴 / 换书 / 初始化）→ 归位到第一个非空槽，保证 HUD 与施法键始终指向有魔法的槽
		if (!SpellbookData.hasScroll(book, selectedSlot)) {
			int first = SpellbookData.firstFilledSlot(book);
			if (first >= 0) {
				selectedSlot = first;
			}
		}

		var active = net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud.getState();
		boolean handled = false;
		for (int index = 0; index < 7; index++) {
			if (!keyPressed(index + 1) || wasDirectPressed[index] || index >= count) continue;
			if (index != selectedSlot && (active != null || gestureKey >= 0)) {
				selectedSlot = index;
				sendSelect(index);
				cancelInputForSelection();
			} else if (active == null && gestureKey < 0) {
				selectedSlot = index;
				sendSelect(index);
				startGesture(player, book, index, index + 1);
			}
			handled = true;
			break;
		}
		if (gestureKey >= 0 && !keyPressed(gestureKey)) {
			sendRelease(gestureToken);
			gestureKey = -1;
		}
		if (!handled && castPressed && !wasCastPressed) {
			if (active != null) {
				if (!active.solo() && active.mode() == net.jackcooper.shapeShifterCurseAddon.spell.SpellCastingRules.Mode.AUTOMATIC) {
					cancelToken = active.token();
					sendCancelHold(cancelToken, true);
					// 本地即时红字反馈（服务端 cancelTicks 校准包最多 20t 后才到）
					net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud.markLocalCancelling();
				}
			} else if (gestureKey < 0) startGesture(player, book, selectedSlot, 0);
		}
		if (gestureKey >= 0 && gestureSlot >= 0 && active != null && active.token() == gestureToken
				&& !active.released() && isAimSpell(book, gestureSlot)) {
			updateAimPreview(client, player, book, gestureSlot);
		}
		updatePressedKeys(castPressed);
	}

	private static void startGesture(ClientPlayerEntity player, ItemStack book, int slot, int key) {
		gestureToken = (gestureToken + 1) & Integer.MAX_VALUE;
		gestureKey = key;
		gestureSlot = slot;
		if (!hasSharedStyle(player) && SpellbookData.getMana(book) < computeManaCost(book, slot)
				&& handleTriplePressDowngrade(player, book, slot)) return;
		sendCast(slot);
	}

	private static boolean keyPressed(int key) {
		KeyBinding binding = key == 0 ? SpellcastKeybindings.KEY_CAST : SpellcastKeybindings.KEY_DIRECT[key - 1];
		return binding != null && binding.isPressed();
	}

	private static void updatePressedKeys(boolean castPressed) {
		wasCastPressed = castPressed;
		for (int index = 0; index < 7; index++) wasDirectPressed[index] = keyPressed(index + 1);
	}

	private static void cancelInputForSelection() {
		if (gestureKey >= 0) sendRelease(gestureToken);
		if (cancelToken >= 0) sendCancelHold(cancelToken, false);
		gestureKey = -1;
		cancelToken = -1;
		downgradePressCount = 0;
		inputGuard.block();
	}

	private static void resetKeys() {
		gestureKey = -1;
		gestureSlot = -1;
		cancelToken = -1;
		inputGuard.reset();
		downgradePressCount = 0;
		wasCastPressed = false;
		for (int i = 0; i < 7; i++) {
			wasDirectPressed[i] = false;
		}
	}

	/** 分担型形态（雪狐/悦灵/使魔系）：书不足时不拦预检，由服务端权威判书+条合计。
	 * 客户端无法读服务端 Apoli resource 时 has() 会安全返回 false → 预检退化为旧逻辑，不影响单机。 */
	private static boolean hasSharedStyle(net.minecraft.client.network.ClientPlayerEntity player) {
		try {
			return net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,
					net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SNOW_FOX_SP)
					|| net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,
							net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.ALLAY_SP)
					|| net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,
							net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_SP)
					|| net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,
							net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.UPGRADE_FAMILIAR_FOX)
					|| net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,
							net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_RED)
					|| net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,
							net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
		} catch (Exception e) {
			return false;
		}
	}

	/** 当前槽是否为按住瞄准型法术（getAimMaxRange>0，如陨火术）。 */
	private static boolean isAimSpell(ItemStack book, int slot) {
		Spell spell = ScrollData.getSpell(SpellbookData.getScroll(book, slot));
		return spell != null && spell.getAimMaxRange() > 0;
	}

	/** 使用服务端同一耗蓝公式，包含法阵、等级、形态亲和及潮汐条件；仅用于预览和预检。 */
	private static int computeManaCost(ItemStack book, int slot) {
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		Spell spell = ScrollData.getSpell(scroll);
		if (spell == null) {
			return Integer.MAX_VALUE;
		}
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		return player == null ? Integer.MAX_VALUE
				: net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.finalManaCost(
						spell, book, player, ScrollData.getCastLevel(scroll));
	}

	/**
	 * 按住瞄准预览（纯客户端本地粒子，零网络开销）：每 2t 在准星落点撒一圈火焰粒子
	 * （与服务端陨火预警圈同视觉语言），随准星实时移动。
	 * 调用方已保证 CD/法力/落点均就绪（服务端施法时权威重验），此处无需重复校验。
	 */
	private static void updateAimPreview(MinecraftClient client, ClientPlayerEntity player, ItemStack book, int slot) {
		if (client.world == null || client.world.getTime() % 2 != 0) {
			return;
		}
		ItemStack scroll = SpellbookData.getScroll(book, slot);
		Spell spell = ScrollData.getSpell(scroll);
		if (spell == null) {
			return;
		}
		double radius = spell.getAimRadius(ScrollData.getLevel(scroll));
		if (radius <= 0) {
			return;
		}
		Vec3d impact = Spell.computeAimImpact(player, spell.getAimMaxRange());
		if (impact == null) {
			return;
		}
		// 圈粒子按法术系别取色（火=火焰 / 虚无=传送门紫 / 其它默认火焰）
		net.minecraft.particle.ParticleEffect ringParticle = ParticleTypes.FLAME;
		if (spell.getElement() == net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.VOID) {
			ringParticle = ParticleTypes.PORTAL;
		}
		// 火焰圈勾勒 AOE 范围（旋转角随时间缓慢流动，与服务端预警圈同款动感）
		int ringCount = (int) Math.min(40, Math.max(16, radius * 10));
		double baseAngle = (client.world.getTime() / 2) * 0.15;
		for (int i = 0; i < ringCount; i++) {
			double angle = (i * 2 * Math.PI / ringCount) + baseAngle;
			client.world.addParticle(ringParticle,
					impact.x + Math.cos(angle) * radius * 0.95,
					impact.y + 0.1,
					impact.z + Math.sin(angle) * radius * 0.95,
					0.0, 0.0, 0.0);
		}
		// 中心烟柱标记落心
		client.world.addParticle(ParticleTypes.LARGE_SMOKE, impact.x, impact.y + 0.3, impact.z, 0, 0.01, 0);
	}

	private static void sendCast(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		buf.writeVarInt(gestureToken);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_CAST, buf);
	}

	private static void sendSelect(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_SELECT, buf);
	}

	private static void sendSetCastLevel(int slot, int castLevel) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		buf.writeVarInt(castLevel);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_SET_CAST_LEVEL, buf);
	}

	/** 三连击触发的临时降档施放（服务端自动选付得起的最高档 + CD ×1.2 惩罚）。 */
	private static void sendCastDowngraded(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		buf.writeVarInt(gestureToken);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_CAST_DOWNGRADED, buf);
	}

	private static void sendRelease(int token) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(token);
		ClientPlayNetworking.send(net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager.RELEASE, buf);
	}

	private static void sendCancelHold(int token, boolean held) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(token);
		buf.writeBoolean(held);
		ClientPlayNetworking.send(net.jackcooper.shapeShifterCurseAddon.spell.SpellChannelManager.CANCEL_HOLD, buf);
	}
}
