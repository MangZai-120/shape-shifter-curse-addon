package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBarDef;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用增强法阵的「形态能量 → 书法术值」转化（jackcooper，服务端每秒结算）。
 *
 * <p>装入魔法书五角星的通用法阵生效：当玩家当前形态持有能量条（SSCA 资源条
 * 悦灵/蝙蝠/阿努比斯/雪狐，或原版 ManaComponent 体系——使魔/蜘蛛/契灵）
 * 且<b>书法术值占比低于阈值</b>时，每秒消耗 3 点形态能量回复 6 点书法术值。</p>
 *
 * <p>阈值按书内<b>等级最高</b>的通用法阵决定（多张不叠加转化速率）：Lv1=20% …
 * Lv5=100%（{@link FormationData#universalThreshold}）。</p>
 *
 * <p>边界：书满/未装备/无能量条/能量不足 3 点/朔望与寄生果蝠（ManaComponent 可能残留误判）
 * 均不动作；能量判定与 {@code UniversalEnergyPotionItem.canRestore} 同源。</p>
 */
public final class UniversalFormationManager {
	private static final Identifier REGEN_BLOCK_MODIFIER_ID =
			new Identifier("ssc_addon", "universal_formation_regen_block");
	private static final ManaUtils.Modifier REGEN_BLOCK_MODIFIER =
			ManaUtils.Modifier.of(null, 0.0D, null);
	private static final Set<UUID> CHARGING_PLAYERS = ConcurrentHashMap.newKeySet();

	private UniversalFormationManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				clearCharging(player);
				tickPlayer(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearCharging(handler.player));
	}

	public static boolean isCharging(PlayerEntity player) {
		return player != null && CHARGING_PLAYERS.contains(player.getUuid());
	}

	private static void markCharging(ServerPlayerEntity player) {
		CHARGING_PLAYERS.add(player.getUuid());
		ManaUtils.addRegenManaModifier(player, REGEN_BLOCK_MODIFIER_ID, REGEN_BLOCK_MODIFIER, true);
	}

	private static void clearCharging(ServerPlayerEntity player) {
		CHARGING_PLAYERS.remove(player.getUuid());
		ManaUtils.removeRegenManaModifier(player, REGEN_BLOCK_MODIFIER_ID, true);
	}

	private static void tickPlayer(ServerPlayerEntity player) {
		// 书未装备 → 不动作
		ItemStack book = TrinketUtils.findFirstEquipped(player, s -> s.getItem() == SscAddon.MOON_DUST_SPELLBOOK);
		if (book == null || book.isEmpty()) {
			return;
		}
		// 书内没有通用法阵 → 不动作（取等级最高的一张决定阈值）
		int bestLevel = 0;
		for (ItemStack formation : SpellbookData.getFormations(book)) {
			if (FormationData.getElement(formation) == FormationElement.UNIVERSAL) {
				bestLevel = Math.max(bestLevel, FormationData.getLevel(formation));
			}
		}
		if (bestLevel <= 0) {
			return;
		}
		// 水位判定：书法术值占比低于阈值才转化（书已满则跳过，防每秒空转写 NBT）
		int mana = SpellbookData.getMana(book);
		int maxMana = SpellbookData.getMaxMana(book);
		double threshold = FormationData.universalThreshold(bestLevel);
		if (maxMana <= 0 || mana >= maxMana * threshold) {
			return;
		}
		// 当前形态无能量条 → 不动作（判定与 UniversalEnergyPotionItem.canRestore 同源；朔望排除）
		ResourceBarDef bar = findEnergyBar(player);
		if (bar == null) {
			return;
		}
		// 转化：耗 3 形态能量 → 回 6 书法术值（能量不足时 consume 返回 false，不回不扣）
		// 朔望形态亲和：回能效率 +33%（6 → 8/秒，耗不变）
		int drain = (int) FormationData.UNIVERSAL_MANA_DRAIN_PER_SEC;
		int restore = (int) FormationData.UNIVERSAL_BOOK_MANA_PER_SEC;
		if (FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)) {
			restore = Math.round(restore * 1.33f); // 朔望亲和：回能 +33%（6 → 8）
		}
		if (!ResourceBars.consume(player, bar, drain)) {
			return;
		}
		SpellbookData.addMana(book, restore);
		markCharging(player);
	}

	/**
	 * 玩家当前持有的能量条（与 {@code UniversalEnergyPotionItem.canRestore} 同源）：
	 * 依次查 SSCA 资源条（果蝠种子条除外），否则原版 ManaComponent 兜底。
	 */
	private static ResourceBarDef findEnergyBar(ServerPlayerEntity player) {
		// 朔望无能量体系；果蝠的种子量不是 mana。两者都需在 ManaComponent 兜底前按形态硬排除。
		if (FormUtils.isForm(player, FormIdentifiers.OCELOT_NOVA)
				|| FormUtils.isForm(player, FormIdentifiers.BAT_PARASITIC_FRUIT)) {
			return null;
		}
		// ——按 BarKeys.ALL 顺序找第一条持有的资源条
		for (ResourceBarDef bar : BarKeys.ALL) {
			if (bar == BarKeys.SEED) {
				continue;
			}
			if (ResourceBars.has(player, bar)) {
				return bar;
			}
		}
		// 原版 mana 体系兜底（使魔/蜘蛛/契灵等 ManaComponent 非空形态）
		if (ResourceBars.has(player, BarKeys.VANILLA_MANA)) {
			return BarKeys.VANILLA_MANA;
		}
		return null;
	}
}
