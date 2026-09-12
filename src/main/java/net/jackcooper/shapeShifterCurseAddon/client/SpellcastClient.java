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
import net.minecraft.util.Formatting;
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
 * <p><b>按住瞄准型法术</b>（{@link Spell#getAimMaxRange()} > 0，如陨火术）：按住施法键时
 * 在准星落点本地粒子圈持续预览（随准星实时移动、半径含等级缩放），松开才发送施法包；
 * 冷却中不预览不发送。直达键仍为按下立即施放。所有伤害/冷却/法力判定在服务端。</p>
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

		// 施法键：按住瞄准型（如陨火术）按住时显示落点预览圈，松开发包；其余上升沿立即发包。
		// 按住瞄准型前置校验（仿契灵传送预览）：法力不足或落点无效时按下沿即红字提示、不显示预览、松开不发包
		boolean castPressed = SpellcastKeybindings.KEY_CAST != null && SpellcastKeybindings.KEY_CAST.isPressed();
		boolean isAimSpell = isAimSpell(book, selectedSlot);
		boolean aimReady = false;
		if (isAimSpell) {
			aimReady = isAimReady(client, player, book, selectedSlot);
			if (castPressed && !wasCastPressed && !aimReady) {
				// 按下沿提示一次（防按住期间刷屏）：法力不足 → 「法力不足」；法力够但落点无效（指天）→ 不提示只无预览
				if (client.world != null && SpellbookData.getMana(book) < computeManaCost(book, selectedSlot)) {
					player.sendMessage(Text.translatable("message.ssc_addon.spellbook.no_mana").formatted(Formatting.RED), true);
				}
			}
			if (castPressed && aimReady) {
				updateAimPreview(client, player, book, selectedSlot);
			}
		}
		boolean sendNow = isAimSpell ? (!castPressed && wasCastPressed && aimReady) : (castPressed && !wasCastPressed);
		if (sendNow) {
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

	/** 当前槽是否为按住瞄准型法术（getAimMaxRange>0，如陨火术）。 */
	private static boolean isAimSpell(ItemStack book, int slot) {
		Spell spell = ScrollData.getSpell(SpellbookData.getScroll(book, slot));
		return spell != null && spell.getAimMaxRange() > 0;
	}

	/** 按住瞄准型施法消耗（与服务端 SpellCastManager 同式：含法阵耗蓝倍率）。客户端仅用于预览/提示。 */
	private static int computeManaCost(ItemStack book, int slot) {
		Spell spell = ScrollData.getSpell(SpellbookData.getScroll(book, slot));
		if (spell == null) {
			return Integer.MAX_VALUE;
		}
		return Math.round(spell.getManaCost() * net.jackcooper.shapeShifterCurseAddon.spell.FormationData.sumManaCostMultiplier(book));
	}

	/**
	 * 按住瞄准型前置校验（双端一致的本地复算；服务端施法时仍权威重验）：
	 * CD 就绪 + 法力足够 + 落点命中方块（指天/超距无方块命中 → 无预览、松开不发包）。
	 */
	private static boolean isAimReady(MinecraftClient client, ClientPlayerEntity player, ItemStack book, int slot) {
		if (client.world == null) {
			return false;
		}
		if (SpellbookData.isOnCooldown(book, slot, client.world)) {
			return false;
		}
		if (SpellbookData.getMana(book) < computeManaCost(book, slot)) {
			return false;
		}
		Spell spell = ScrollData.getSpell(SpellbookData.getScroll(book, slot));
		return spell != null && Spell.computeAimImpact(player, spell.getAimMaxRange()) != null;
	}

	/**
	 * 按住瞄准预览（纯客户端本地粒子，零网络开销）：每 2t 在准星落点撒一圈火焰粒子
	 * （与服务端陨火预警圈同视觉语言），随准星实时移动。
	 * 调用方已保证 CD/法力/落点均就绪（{@link #isAimReady}），此处无需重复校验。
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
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_CAST, buf);
	}

	private static void sendSelect(int slot) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(slot);
		ClientPlayNetworking.send(SscAddonNetworking.PACKET_SPELL_SELECT, buf);
	}
}
