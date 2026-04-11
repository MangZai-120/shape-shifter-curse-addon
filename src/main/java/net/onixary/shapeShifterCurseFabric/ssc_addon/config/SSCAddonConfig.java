package net.onixary.shapeShifterCurseFabric.ssc_addon.config;

import java.util.ArrayList;
import java.util.List;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "ssc_addon")
public class SSCAddonConfig implements ConfigData {

	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public BookLanguage bookLanguage = BookLanguage.CHINESE;

	@ConfigEntry.Gui.Tooltip
	public boolean showCdBar = true;

	@ConfigEntry.Gui.Tooltip
	public boolean showCdSeconds = true;

	// Globally disabled skills (format: "form:skill", e.g., "snow_fox:melee_primary")
	@ConfigEntry.Gui.Tooltip
	public List<String> disabledSkills = new ArrayList<>();

	@ConfigEntry.Gui.CollapsibleObject(startExpanded = false)
	public SnowFoxConfig snowFox = new SnowFoxConfig();
	@ConfigEntry.Gui.CollapsibleObject(startExpanded = false)
	public AllayConfig allay = new AllayConfig();
	@ConfigEntry.Gui.CollapsibleObject(startExpanded = false)
	public AnubisWolfConfig anubisWolf = new AnubisWolfConfig();
	@ConfigEntry.Gui.CollapsibleObject(startExpanded = false)
	public ItemConfig item = new ItemConfig();

	public enum BookLanguage {
		CHINESE,
		ENGLISH
	}

	public static class SnowFoxConfig {
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
