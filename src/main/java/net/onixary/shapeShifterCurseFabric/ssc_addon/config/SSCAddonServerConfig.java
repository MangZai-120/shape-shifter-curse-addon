package net.onixary.shapeShifterCurseFabric.ssc_addon.config;

import java.util.ArrayList;
import java.util.List;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * 服务端配置 - 关乎玩法平衡与权威判定的设置，仅服务器/单机宿主的修改有效。
 * 多人环境下客户端对此配置文件的修改不会被服务器读取。
 */
@Config(name = "ssc_addon_server")
public class SSCAddonServerConfig implements ConfigData {

	// ==================== 白名单总开关 ====================
	@ConfigEntry.Gui.Tooltip(count = 3)
	public boolean whitelistEnabled = true;

	// ==================== 故事书语言（影响服务器宝箱生成的书籍内容） ====================
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public BookLanguage bookLanguage = BookLanguage.CHINESE;

	public enum BookLanguage {
		CHINESE,
		ENGLISH
	}

	// ==================== 全局禁用技能列表 ====================
	@ConfigEntry.Gui.Tooltip
	public List<String> disabledSkills = new ArrayList<>();

	// 注：形态平衡参数（snowFox/allay/anubisWolf/item）已移除，因为它们从未被代码读取，
	// 实际数值仍写死在各个能力 JSON / Java 类里。如需暴露给管理员调整，请单独提需求。

	@SuppressWarnings("unused")
	private static class _RemovedSnowFoxConfig {
		@ConfigEntry.Gui.Tooltip
		public double meleeRange = 10.0;
		@ConfigEntry.Gui.Tooltip
		public double teleportRange = 10.0;
		@ConfigEntry.Gui.Tooltip
		public float meleeDamage = 6.0f;
		@ConfigEntry.Gui.Tooltip
		public float teleportBonusDamage = 3.0f;
		@ConfigEntry.Gui.Tooltip
		public int frostStormRange = 30;
		@ConfigEntry.Gui.Tooltip
		public int frostStormDurationSeconds = 10;
		@ConfigEntry.Gui.Tooltip
		public float frostStormDamagePerSecond = 2.0f;
		@ConfigEntry.Gui.Tooltip
		public int manaCost = 30;
		@ConfigEntry.Gui.Tooltip
		public int cooldownTicks = 600;
	}

	public static class AllayConfig {
		@ConfigEntry.Gui.Tooltip
		public double healWandRange = 20.0;
		@ConfigEntry.Gui.Tooltip
		public float healAmount = 4.0f;
		@ConfigEntry.Gui.Tooltip
		public int healManaCost = 12;
		@ConfigEntry.Gui.Tooltip
		public int healCooldownTicks = 50;
		@ConfigEntry.Gui.Tooltip
		public double totemRange = 20.0;
		@ConfigEntry.Gui.Tooltip
		public double portableBeaconRange = 20.0;
		@ConfigEntry.Gui.Tooltip
		public double jukeboxRange = 20.0;
		@ConfigEntry.Gui.Tooltip
		public int groupHealRange = 15;
		@ConfigEntry.Gui.Tooltip
		public float groupHealAmount = 2.0f;
	}

	public static class AnubisWolfConfig {
		@ConfigEntry.Gui.Tooltip
		public int deathDomainRadius = 24;
		@ConfigEntry.Gui.Tooltip
		public int deathDomainHeight = 9;
		@ConfigEntry.Gui.Tooltip
		public int deathDomainDurationSeconds = 15;
		@ConfigEntry.Gui.Tooltip
		public int deathDomainCooldownTicks = 1000;
		@ConfigEntry.Gui.Tooltip
		public int summonWolfDurationTicks = 600;
		@ConfigEntry.Gui.Tooltip
		public int summonWolfCooldownTicks = 600;
		@ConfigEntry.Gui.Tooltip
		public int soulEnergyMax = 100;
	}

	public static class ItemConfig {
		@ConfigEntry.Gui.Tooltip
		public float waterSpearDamage = 6.0f;
		@ConfigEntry.Gui.Tooltip
		public int waterSpearDurability = 60;
		@ConfigEntry.Gui.Tooltip
		public int snowballLauncherCapacity = 16;
		@ConfigEntry.Gui.Tooltip
		public int portableFridgeSlots = 27;
		@ConfigEntry.Gui.Tooltip
		public int potionBagSlots = 9;
	}
}
