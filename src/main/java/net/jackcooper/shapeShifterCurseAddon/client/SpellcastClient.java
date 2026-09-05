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
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;

/**
 * 月尘魔法书施法客户端检测器（jackcooper）。仅在佩戴魔法书时生效。
 * <p>施法键 / 7 直达键：上升沿检测 → C2S 施法包（带槽 index）。切换键 + 滚轮：由
 * {@code SpellcastMouseScrollMixin} 调用 {@link #cycleSelected} 切换当前选中槽并同步服务端。
 * 所有伤害/冷却/法力判定在服务端。</p>
 */
@Environment(EnvType.CLIENT)
public final class SpellcastClient {
	private static int selectedSlot = 0;
	private static final boolean[] wasDirectPressed = new boolean[7];
	private static boolean wasCastPressed = false;

	private SpellcastClient() {
	}

	public static void register() {
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
		if (next < 0) {
			return;
		}
		selectedSlot = next;
		sendSelect(selectedSlot);
	}

	private static void onClientTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			resetKeys();
			return;
		}
		ItemStack book = getEquippedBook();
		if (book == null || book.isEmpty()) {
			resetKeys();
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

		// 施法键：释放当前选中槽
		boolean castPressed = SpellcastKeybindings.KEY_CAST != null && SpellcastKeybindings.KEY_CAST.isPressed();
		if (castPressed && !wasCastPressed) {
			sendCast(selectedSlot);
		}
		wasCastPressed = castPressed;

		// 7 直达键：释放第 N 槽
		for (int i = 0; i < 7; i++) {
			KeyBinding k = SpellcastKeybindings.KEY_DIRECT[i];
			boolean pressed = k != null && k.isPressed();
			if (pressed && !wasDirectPressed[i] && i < count) {
				sendCast(i);
			}
			wasDirectPressed[i] = pressed;
		}
	}

	private static void resetKeys() {
		wasCastPressed = false;
		for (int i = 0; i < 7; i++) {
			wasDirectPressed[i] = false;
		}
	}

	private static void sendCast(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_CAST, buf);
	}

	private static void sendSelect(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_SELECT, buf);
	}
}
