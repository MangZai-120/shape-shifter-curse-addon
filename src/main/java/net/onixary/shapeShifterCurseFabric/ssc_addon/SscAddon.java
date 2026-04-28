package net.onixary.shapeShifterCurseFabric.ssc_addon;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemGroup;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormGroup;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP;
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.command.SscAddonCommands;
import net.onixary.shapeShifterCurseFabric.ssc_addon.condition.SscAddonConditions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonServerConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.*;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.world.Heightmap;
import net.minecraft.item.SpawnEggItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.AllayClearMarkerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.AllayFriendMarkerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.*;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.*;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.SscAddonPowers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.BlizzardTankRechargeRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.RefillMoisturizerRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.ReloadSnowballLauncherRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.SpUpgradeRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.screen.PotionBagScreenHandler;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSoulEnergy;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSummonWolves;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormRegen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormWitherSand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import net.onixary.shapeShifterCurseFabric.additional_power.VirtualTotemPower;
import io.github.apace100.apoli.component.PowerHolderComponent;

public class SscAddon implements ModInitializer {

	// 存储玩家客户端语言设置，用于发送正确语言的消息
	public static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, String> PLAYER_LANGUAGES = new java.util.concurrent.ConcurrentHashMap<>();

	public static final StatusEffect FOX_FIRE_BURN = new FoxFireBurnEffect();
	public static final StatusEffect BLUE_FIRE_RING = new BlueFireRingEffect();
	public static final StatusEffect PLAYING_DEAD = new PlayingDeadEffect();
	public static final StatusEffect TRUE_INVISIBILITY = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.TrueInvisibilityEffect();
	public static final StatusEffect PRE_INVISIBILITY = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.PreInvisibilityEffect();
	public static final StatusEffect STUN = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.StunEffect();
	public static final StatusEffect GUARANTEED_CRIT = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.GuaranteedCritEffect();
	public static final StatusEffect FROST_FREEZE = new FrostFreezeEffect();
	public static final StatusEffect FROST_FALL = new FrostFallEffect();
	public static final StatusEffect PURIFIED = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.PurifiedEffect();
	public static final StatusEffect SAND_BLIND = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.SandBlindEffect();
	/** 侵蚀烙印标记效果 - 1层(黄色) */
	public static final StatusEffect EROSION_BRAND_MARKER_1 = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.ErosionBrandMarkerEffect(0xFFD700);
	/** 侵蚀烙印标记效果 - 2层(橙色) */
	public static final StatusEffect EROSION_BRAND_MARKER_2 = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.ErosionBrandMarkerEffect(0xFF8C00);
	/** 侵蚀烙印标记效果 - 3层(红色) */
	public static final StatusEffect EROSION_BRAND_MARKER_3 = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.ErosionBrandMarkerEffect(0xDC143C);
	public static final Item POTION_BAG = new PotionBagItem(new Item.Settings().maxCount(1));
	public static final EntityType<FrostBallEntity> FROST_BALL_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "frost_ball"),
			FabricEntityTypeBuilder.<FrostBallEntity>create(SpawnGroup.MISC, FrostBallEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(64).trackedUpdateRate(10)
					.build()
	);	public static final ScreenHandlerType<PotionBagScreenHandler> POTION_BAG_SCREEN_HANDLER = new ScreenHandlerType<>(PotionBagScreenHandler::new, FeatureSet.empty());
	public static final EntityType<FrostStormEntity> FROST_STORM_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "frost_storm"),
			FabricEntityTypeBuilder.<FrostStormEntity>create(SpawnGroup.MISC, FrostStormEntity::new)
					.dimensions(EntityDimensions.fixed(1.0f, 2.0f))
					.trackRangeBlocks(64).trackedUpdateRate(10)
					.build()
	);
	public static final Item SP_UPGRADE_THING = new SpUpgradeItem(new Item.Settings().maxCount(1));
	public static final Item PORTABLE_MOISTURIZER = new PortableMoisturizerItem(new Item.Settings().maxCount(1));	public static final EntityType<WaterSpearEntity> WATER_SPEAR_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "water_spear"),
			FabricEntityTypeBuilder.<WaterSpearEntity>create(SpawnGroup.MISC, WaterSpearEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.5f))
					.trackRangeBlocks(4).trackedUpdateRate(20)
					.build()
	);
	public static final Item SNOWBALL_LAUNCHER = new SnowballLauncherItem(new Item.Settings().maxCount(1));
	public static final Item PORTABLE_FRIDGE = new PortableFridgeItem(new Item.Settings().maxCount(1));
	public static final Item BLUE_FIRE_AMULET = new BlueFireAmuletItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item INVISIBILITY_CLOAK = new InvisibilityCloakItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item LIFESAVING_CAT_TAIL = new LifesavingCatTailItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item PHANTOM_BELL = new PhantomBellItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item FROST_AMULET = new FrostAmuletItem(new Item.Settings().maxCount(1).fireproof());
	public static final RecipeSerializer<RefillMoisturizerRecipe> REFILL_MOISTURIZER_SERIALIZER = new SpecialRecipeSerializer<>(RefillMoisturizerRecipe::new);
	public static final RecipeSerializer<ReloadSnowballLauncherRecipe> RELOAD_SNOWBALL_LAUNCHER_SERIALIZER = new SpecialRecipeSerializer<>(ReloadSnowballLauncherRecipe::new);
	public static final RecipeSerializer<BlizzardTankRechargeRecipe> BLIZZARD_TANK_RECHARGE_SERIALIZER = new SpecialRecipeSerializer<>(BlizzardTankRechargeRecipe::new);
	public static final RecipeSerializer<SpUpgradeRecipe> SP_UPGRADE_SERIALIZER = new SpecialRecipeSerializer<>(SpUpgradeRecipe::new);
	// 60 durability like wooden sword, auto-consumed over 60 seconds
	public static final Item WATER_SPEAR = new WaterSpearItem(new Item.Settings().maxCount(1).maxDamage(60));
	// Evolution Stone
	public static final Item EVOLUTION_STONE = new EvolutionStoneItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item CORAL_BALL = new Item(new Item.Settings().maxCount(64));
	public static final Item ACTIVE_CORAL_NECKLACE = new ActiveCoralNecklaceItem(new Item.Settings().maxCount(1));
	public static final Item ANUBIS_CRYSTAL = new AnubisCrystalItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item ANKH_STONE = new AnkhStoneItem(new Item.Settings().maxCount(1).fireproof());
	// SP Golden Sandstorm items
	public static final Item EROSION_SAND_PRISM = new ErosionSandPrismItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item WITHERED_SAND_RING = new WitheredSandRingItem(new Item.Settings().maxCount(1).fireproof());
	// SP Allay items
	public static final Item ALLAY_HEAL_WAND = new AllayHealWandItem(new Item.Settings().maxCount(1));
	public static final Item ALLAY_JUKEBOX = new AllayJukeboxItem(new Item.Settings().maxCount(1));
	public static final Item FRIEND_MARKER = new AllayFriendMarkerItem(new Item.Settings().maxCount(64));
	public static final Item CLEAR_FRIEND_MARKER = new AllayClearMarkerItem(new Item.Settings().maxCount(64));
	// Entities
	public static final EntityType<AllayFriendMarkerEntity> FRIEND_MARKER_ENTITY_TYPE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "friend_marker"),
			FabricEntityTypeBuilder.<AllayFriendMarkerEntity>create(SpawnGroup.MISC, AllayFriendMarkerEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(4).trackedUpdateRate(10)
					.build()
	);
	public static final EntityType<AllayClearMarkerEntity> CLEAR_MARKER_ENTITY_TYPE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "clear_friend_marker"),
			FabricEntityTypeBuilder.<AllayClearMarkerEntity>create(SpawnGroup.MISC, AllayClearMarkerEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(4).trackedUpdateRate(10)
					.build()
	);
	// 女巫使魔实体
	public static final EntityType<WitchFamiliarEntity> WITCH_FAMILIAR_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "witch_familiar"),
			FabricEntityTypeBuilder.<WitchFamiliarEntity>create(SpawnGroup.MONSTER, WitchFamiliarEntity::new)
					.dimensions(EntityDimensions.fixed(0.5f, 0.7f))
					.trackRangeBlocks(64).trackedUpdateRate(3)
					.build()
	);
	// 女巫使魔怪物蛋（主色狐狸沙棕 #D5B48F，次色青蓝 #31C8CC）
	public static final Item WITCH_FAMILIAR_SPAWN_EGG = new SpawnEggItem(WITCH_FAMILIAR_ENTITY, 0xD5B48F, 0x31C8CC, new Item.Settings());
	public static final ItemGroup SSC_ADDON_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier("ssc_addon", "group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.ssc_addon.group"))
					.icon(() -> new net.minecraft.item.ItemStack(SP_UPGRADE_THING))
					.entries((displayContext, entries) -> {
						entries.add(SP_UPGRADE_THING);
						entries.add(EVOLUTION_STONE);
						entries.add(LIFESAVING_CAT_TAIL);
						entries.add(PHANTOM_BELL);
						entries.add(FROST_AMULET);
						entries.add(BLUE_FIRE_AMULET);
						entries.add(INVISIBILITY_CLOAK);
						entries.add(PORTABLE_MOISTURIZER);
						entries.add(PORTABLE_FRIDGE);
						entries.add(SNOWBALL_LAUNCHER);
						entries.add(WATER_SPEAR);
						entries.add(CORAL_BALL);
						entries.add(ACTIVE_CORAL_NECKLACE);
						entries.add(ANUBIS_CRYSTAL);
						entries.add(ANKH_STONE);
						entries.add(EROSION_SAND_PRISM);
						entries.add(WITHERED_SAND_RING);
						entries.add(ALLAY_HEAL_WAND);
						entries.add(ALLAY_JUKEBOX);
						entries.add(FRIEND_MARKER);
						entries.add(CLEAR_FRIEND_MARKER);
						entries.add(WITCH_FAMILIAR_SPAWN_EGG);
					})
					.build());
	// SP Allay sound events
	public static final Identifier ALLAY_HEAL_MUSIC_ID = new Identifier("ssc_addon", "allay_heal_music");
	public static final Identifier ALLAY_SPEED_MUSIC_ID = new Identifier("ssc_addon", "allay_speed_music");
	public static final SoundEvent ALLAY_HEAL_MUSIC_EVENT = SoundEvent.of(ALLAY_HEAL_MUSIC_ID);
	public static final SoundEvent ALLAY_SPEED_MUSIC_EVENT = SoundEvent.of(ALLAY_SPEED_MUSIC_ID);

	@Override
	public void onInitialize() {
        /*
        // 旧代码(保留参考) 已拆分为私有方法
        AutoConfig.register(SSCAddonClientConfig.class, GsonConfigSerializer::new);
        AutoConfig.register(SSCAddonServerConfig.class, GsonConfigSerializer::new);
        // 注册状态效果
        // 注册物品
        // 注册实体
        // 注册配方
        // 注册技能
        // 注册形态
        // 注册命令
        // 注册Tick事件
        */

		// 新代码
		registerConfig();
		registerStatusEffects();
		registerItems();
		registerRecipeSerializers();
		registerSoundEvents();
		registerEntityAttributes();
		registerApoliSystems();
		registerForms();
		registerCommands();
		registerTickHandlers();
		registerEntitySpawnHandlers();
		registerPlayerEventHandlers();
		registerServerLifecycleHandlers();
		AnubisWolfSpSoulEnergy.registerEvents();
		GoldenSandstormRegen.init();
	}



	private void registerConfig() {
		AutoConfig.register(SSCAddonClientConfig.class, GsonConfigSerializer::new);
		AutoConfig.register(SSCAddonServerConfig.class, GsonConfigSerializer::new);
	}

	private void registerStatusEffects() {
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "fox_fire_burn"), FOX_FIRE_BURN);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "playing_dead"), PLAYING_DEAD);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "blue_fire_ring"), BLUE_FIRE_RING);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "true_invisibility"), TRUE_INVISIBILITY);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "pre_invisibility"), PRE_INVISIBILITY);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "stun"), STUN);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "guaranteed_crit"), GUARANTEED_CRIT);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "frost_freeze"), FROST_FREEZE);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "frost_fall"), FROST_FALL);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "purified"), PURIFIED);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "sand_blind"), SAND_BLIND);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "erosion_brand_marker_1"), EROSION_BRAND_MARKER_1);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "erosion_brand_marker_2"), EROSION_BRAND_MARKER_2);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "erosion_brand_marker_3"), EROSION_BRAND_MARKER_3);
	}

	private void registerItems() {
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "sp_upgrade_thing"), SP_UPGRADE_THING);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "portable_moisturizer"), PORTABLE_MOISTURIZER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "snowball_launcher"), SNOWBALL_LAUNCHER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "portable_fridge"), PORTABLE_FRIDGE);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "blue_fire_amulet"), BLUE_FIRE_AMULET);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "frost_amulet"), FROST_AMULET);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "invisibility_cloak"), INVISIBILITY_CLOAK);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "lifesaving_cat_tail"), LIFESAVING_CAT_TAIL);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "phantom_bell"), PHANTOM_BELL);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "water_spear"), WATER_SPEAR);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "potion_bag"), POTION_BAG);
		Registry.register(Registries.SCREEN_HANDLER, new Identifier("ssc_addon", "potion_bag"), POTION_BAG_SCREEN_HANDLER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "evolution_stone"), EVOLUTION_STONE);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "coral_ball"), CORAL_BALL);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "active_coral_necklace"), ACTIVE_CORAL_NECKLACE);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "anubis_crystal"), ANUBIS_CRYSTAL);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "ankh_stone"), ANKH_STONE);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "erosion_sand_prism"), EROSION_SAND_PRISM);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "withered_sand_ring"), WITHERED_SAND_RING);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "allay_heal_wand"), ALLAY_HEAL_WAND);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "allay_jukebox"), ALLAY_JUKEBOX);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "friend_marker"), FRIEND_MARKER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "clear_friend_marker"), CLEAR_FRIEND_MARKER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "witch_familiar_spawn_egg"), WITCH_FAMILIAR_SPAWN_EGG);
	}

	private void registerRecipeSerializers() {
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "refill_moisturizer"), REFILL_MOISTURIZER_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "reload_snowball_launcher"), RELOAD_SNOWBALL_LAUNCHER_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "blizzard_tank_recharge"), BLIZZARD_TANK_RECHARGE_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "sp_upgrade_crafting"), SP_UPGRADE_SERIALIZER);
	}

	// 拆分的私有方法

	private void registerSoundEvents() {
		Registry.register(Registries.SOUND_EVENT, ALLAY_HEAL_MUSIC_ID, ALLAY_HEAL_MUSIC_EVENT);
		Registry.register(Registries.SOUND_EVENT, ALLAY_SPEED_MUSIC_ID, ALLAY_SPEED_MUSIC_EVENT);
	}

	private void registerEntityAttributes() {
		FabricDefaultAttributeRegistry.register(WITCH_FAMILIAR_ENTITY, WitchFamiliarEntity.createWitchFamiliarAttributes());
	}

	private void registerApoliSystems() {
		SscAddonActions.register();
		SscAddonConditions.register();
		SscAddonPowers.register();
		SscAddonNetworking.registerServerReceivers();
		net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem.init();
		LifesavingCatTailItem.registerLootTable();
		AnkhStoneItem.registerLootTable();
	}

	private void registerForms() {
		Form_Axolotl3 axolotlForm = new Form_Axolotl3(FormIdentifiers.AXOLOTL_SP);
		axolotlForm.setPhase(PlayerFormPhase.PHASE_SP);
		RegPlayerForms.registerPlayerForm(axolotlForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_axolotl_sp")).addForm(axolotlForm, 5));

		Form_FamiliarFox3 familiarFoxForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_SP);
		familiarFoxForm.setPhase(PlayerFormPhase.PHASE_SP);
		RegPlayerForms.registerPlayerForm(familiarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_sp")).addForm(familiarFoxForm, 5));

		Form_FamiliarFoxRed familiarFoxRedForm = new Form_FamiliarFoxRed(FormIdentifiers.FAMILIAR_FOX_RED);
		familiarFoxRedForm.setPhase(PlayerFormPhase.PHASE_SP);
		RegPlayerForms.registerPlayerForm(familiarFoxRedForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_red")).addForm(familiarFoxRedForm, 5));

		Form_SnowFoxSP snowFoxForm = new Form_SnowFoxSP(FormIdentifiers.SNOW_FOX_SP);
		snowFoxForm.setPhase(PlayerFormPhase.PHASE_SP);
		RegPlayerForms.registerPlayerForm(snowFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_snow_fox_sp")).addForm(snowFoxForm, 7));

		Form_Allay allayForm = new Form_Allay(FormIdentifiers.ALLAY_SP);
		allayForm.setPhase(PlayerFormPhase.PHASE_SP);
		RegPlayerForms.registerPlayerForm(allayForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_allay_sp")).addForm(allayForm, 8));

		Form_FeralCatSP wildCatForm = new Form_FeralCatSP(FormIdentifiers.WILD_CAT_SP);
		wildCatForm.setPhase(PlayerFormPhase.PHASE_SP);
		wildCatForm.setBodyType(net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType.FERAL);
		wildCatForm.setCanSneakRush(true);
		RegPlayerForms.registerPlayerForm(wildCatForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_wild_cat_sp")).addForm(wildCatForm, 5));

		// Fallen Allay SP
		Form_FallenAllaySP fallenAllayForm = new Form_FallenAllaySP(FormIdentifiers.FALLEN_ALLAY_SP);
		fallenAllayForm.setPhase(PlayerFormPhase.PHASE_SP);
		RegPlayerForms.registerPlayerForm(fallenAllayForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_fallen_allay_sp")).addForm(fallenAllayForm, 8));

		// Anubis Wolf SP
		Form_AnubisWolfSP anubisWolfForm = new Form_AnubisWolfSP(FormIdentifiers.ANUBIS_WOLF_SP);
		anubisWolfForm.setPhase(PlayerFormPhase.PHASE_SP);
		anubisWolfForm.setCanSneakRush(true);
		RegPlayerForms.registerPlayerForm(anubisWolfForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_anubis_wolf_sp")).addForm(anubisWolfForm, 12));

		// Golden Sandstorm SP (金沙岚)
		Form_GoldenSandstormSP goldenSandstormForm = new Form_GoldenSandstormSP(FormIdentifiers.GOLDEN_SANDSTORM_SP);
		goldenSandstormForm.setPhase(PlayerFormPhase.PHASE_SP);
		goldenSandstormForm.setCanSneakRush(true);
		RegPlayerForms.registerPlayerForm(goldenSandstormForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_golden_sandstorm_sp")).addForm(goldenSandstormForm, 12));
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SscAddonCommands.register(dispatcher));
	}

	private void registerTickHandlers() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world -> {
			// 在服务器线程上处理断线玩家的领域方块还原
			if (world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) {
				AnubisWolfSpDeathDomain.tickCleanup();
			}
			for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
				// 修复局域网多人游戏中远程玩家的自定义可食用物品Map未在服务端刷新的问题
				// 原版mod在集成服务器(EnvType.CLIENT)环境下跳过了OnServerTick，导致非主机玩家无法食用自定义食物（如悦灵吃紫水晶）
				if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT
						&& player.age % 100 == 0) {
					net.onixary.shapeShifterCurseFabric.util.CustomEdibleUtils.ReloadPlayerCustomEdible(player);
				}
				SnowFoxSpMeleeAbility.tick(player);
				SnowFoxSpTeleportAttack.tick(player);
				SnowFoxSpFrostStorm.tick(player);
				AllaySPGroupHeal.tick(player);
				AllaySPJukebox.tick(player);
				AnubisWolfSpDeathDomain.tick(player);
				AnubisWolfSpSummonWolves.tick(player);
				GoldenSandstormErosionBrand.tick(player);
				GoldenSandstormWitherSand.tick(player);
				GoldenSandstormRegen.tick(player);
			}
		});
	}

	private void registerServerLifecycleHandlers() {
		// 服务器启动时清除所有技能静态状态（在世界加载之前触发）
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			System.out.println("[SSC_ADDON] SERVER_STARTING event fired, clearing all ability static state");
			SnowFoxSpMeleeAbility.clearAll();
			SnowFoxSpTeleportAttack.clearAll();
			SnowFoxSpFrostStorm.clearAll();
			AnubisWolfSpDeathDomain.clearAll();
			AnubisWolfSpSummonWolves.clearAll();
			AllaySPTotem.clearAll();
			GoldenSandstormErosionBrand.clearAll();
			GoldenSandstormWitherSand.clearAll(server);
			GoldenSandstormRegen.clearAll();
			UndeadNeutralState.clearAll();
			net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions.clearAll();
			System.out.println("[SSC_ADDON] SERVER_STARTING ability state cleared");
		});
		// 服务器关闭前还原所有死亡领域方块（在世界存档之前触发）
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			System.out.println("[SSC_ADDON] SERVER_STOPPING event fired, calling forceRestoreAll");
			AnubisWolfSpDeathDomain.forceRestoreAll();
			System.out.println("[SSC_ADDON] SERVER_STOPPING forceRestoreAll completed");
		});
		// 数据包重新加载成功后清除所有技能静态状态
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (!success) {
				System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD: reload failed, skipping ability state clear");
				return;
			}
			System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD: reload successful, clearing all ability static state");
			SnowFoxSpMeleeAbility.clearAll();
			SnowFoxSpTeleportAttack.clearAll();
			SnowFoxSpFrostStorm.clearAll();
			// reload 可能遇到玩家正在释放领域，必须先强制还原方块再清状态，避免世界里残留灵魂沙
			AnubisWolfSpDeathDomain.forceRestoreAll();
			AnubisWolfSpSummonWolves.clearAll();
			AllaySPTotem.clearAll();
			GoldenSandstormErosionBrand.clearAll();
			GoldenSandstormWitherSand.clearAll(server);
			GoldenSandstormRegen.clearAll();
			UndeadNeutralState.clearAll();
			net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions.clearAll();
			System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD ability state cleared");
		});
	}

	/**
	 * 女巫使魔伴生逻辑 + 野外自然生成注册
	 * - 袭击中生成的女巫会在附近生成1-3只女巫使魔
	 * - 野外极低概率自然生成无主使魔（会自动寻找附近女巫认主）
	 * 使用命令标签确保每只女巫只检查一次（重新加载区块不会重复触发）
	 */
	private void registerEntitySpawnHandlers() {
		// 野外自然生成（末影人权重10的一半=5）
		SpawnRestriction.register(WITCH_FAMILIAR_ENTITY, SpawnRestriction.Location.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		BiomeModifications.addSpawn(
				BiomeSelectors.foundInOverworld(),
				SpawnGroup.MONSTER,
				WITCH_FAMILIAR_ENTITY,
				5,    // 末影人权重10的一半
				1, 1  // 最小/最大成组数量
		);

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof net.minecraft.entity.mob.WitchEntity witch)) return;
			// 每只女巫只检查一次
			if (witch.getCommandTags().contains("ssc_familiar_checked")) return;
			witch.addCommandTag("ssc_familiar_checked");

			// 仅袭击中生成的女巫才会伴生使魔
			if (!witch.hasActiveRaid()) return;

			// 随机生成1-3只使魔
			int count = 1 + witch.getRandom().nextInt(3);
			for (int i = 0; i < count; i++) {
				WitchFamiliarEntity familiar = WITCH_FAMILIAR_ENTITY.create(world);
				if (familiar == null) continue;

				// 设置主人为该女巫
				familiar.setOwnerUuid(witch.getUuid());

				double offsetX = (witch.getRandom().nextDouble() - 0.5) * 3.0;
				double offsetZ = (witch.getRandom().nextDouble() - 0.5) * 3.0;
				familiar.refreshPositionAndAngles(
						witch.getX() + offsetX,
						witch.getY(),
						witch.getZ() + offsetZ,
						witch.getRandom().nextFloat() * 360f,
						0f
				);
				world.spawnEntity(familiar);
			}
		});
	}

	private void registerPlayerEventHandlers() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				newPlayer.getItemCooldownManager().remove(LIFESAVING_CAT_TAIL);
				newPlayer.getItemCooldownManager().remove(PHANTOM_BELL);

				// SP阿努比斯之狼：死亡后重置亡灵不死被动冷却
				if (FormUtils.isAnubisWolfSP(newPlayer)) {
					for (VirtualTotemPower power : PowerHolderComponent.getPowers(newPlayer, VirtualTotemPower.class)) {
						if (power.getRemainingTicks() > 0) {
							power.modify(-power.getRemainingTicks());
							PowerHolderComponent.syncPower(newPlayer, power.getType());
						}
					}
				}
			}
		});

		// 玩家首次进入世界时发送欢迎消息（延迟3秒，等待客户端语言设置到达服务端后根据语言发送对应文本）
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			String welcomeTag = "ssc_addon_welcomed";
			if (!player.getCommandTags().contains(welcomeTag)) {
				player.addCommandTag(welcomeTag);
				final java.util.UUID playerUuid = player.getUuid();
				java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
						.execute(() -> server.execute(() -> {
							var p = server.getPlayerManager().getPlayer(playerUuid);
							if (p == null) return;
							String url = "https://github.com/MangZai-120/shape-shifter-curse-addon/issues";
							String wikiUrl = "https://www.mcmod.cn/class/24327.html";
							// 根据玩家客户端语言选择显示文本
							String lang = PLAYER_LANGUAGES.getOrDefault(playerUuid, "en_us");
							boolean isChinese = lang.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
							MutableText githubLink = Text.literal(url)
									.setStyle(Style.EMPTY
											.withColor(Formatting.AQUA)
											.withUnderline(true)
											.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
							MutableText wikiLink = Text.literal(wikiUrl)
									.setStyle(Style.EMPTY
											.withColor(Formatting.AQUA)
											.withUnderline(true)
											.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, wikiUrl)));
							if (isChinese) {
								// 第一行：欢迎+百科链接+bug说明+GitHub链接+崩溃说明
								p.sendMessage(Text.empty()
										.append(Text.literal("欢迎游玩幻形者诅咒扩展，游玩教程在MC百科上：").formatted(Formatting.GOLD))
										.append(wikiLink)
										.append(Text.literal("。由于作者水平有限，模组难免会有bug，如有bug请将日志发到GitHub上：").formatted(Formatting.GOLD))
										.append(githubLink)
										.append(Text.literal("；若后续更新版本导致崩溃请将崩溃日志以及必要信息文件发到模组的GitHub上，谢谢！").formatted(Formatting.GOLD)));
								// 第二行：请不要只发送照片（蓄意空格）
								p.sendMessage(Text.literal("请 不 要 只 发 送 照 片 过 来 谢 谢！").formatted(Formatting.RED));
								// 第三行：ps提示
								p.sendMessage(Text.literal("ps：此对话只显示这一次").formatted(Formatting.GRAY));
							} else {
								p.sendMessage(Text.empty()
										.append(Text.literal("Welcome to Shape Shifter's Curse Addon! Tutorial is available on MCMOD Wiki: ").formatted(Formatting.GOLD))
										.append(wikiLink)
										.append(Text.literal(". Due to the author's limited expertise, the mod may have bugs. If you encounter any, please submit your logs on GitHub: ").formatted(Formatting.GOLD))
										.append(githubLink)
										.append(Text.literal("; If a future update causes a crash, please submit the crash log and necessary info files to the mod's GitHub, thank you!").formatted(Formatting.GOLD)));
								p.sendMessage(Text.literal("Please do NOT only send screenshots, thank you!").formatted(Formatting.RED));
								p.sendMessage(Text.literal("PS: This message will only be shown once.").formatted(Formatting.GRAY));
							}
						}));
			}
		});

		// 玩家断线时清理所有静态状态Map，防止内存泄漏和重连后状态错乱
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			java.util.UUID uuid = handler.player.getUuid();
			System.out.println("[SSC_ADDON] DISCONNECT event fired for player: " + handler.player.getName().getString());
			SnowFoxSpMeleeAbility.clearPlayer(uuid);
			SnowFoxSpTeleportAttack.clearPlayer(uuid);
			SnowFoxSpFrostStorm.clearPlayer(uuid);
			AnubisWolfSpDeathDomain.clearPlayer(handler.player);
			AnubisWolfSpSummonWolves.clearPlayer(uuid);
			AllaySPTotem.clearPlayer(handler.player);
			GoldenSandstormErosionBrand.clearPlayer(handler.player);
			GoldenSandstormWitherSand.clearPlayer(handler.player);
			GoldenSandstormRegen.clearPlayer(uuid);
			AnubisWolfSpSoulEnergy.clearPlayer(handler.player);
			ErosionSandPrismItem.clearPlayer(uuid);
			WitheredSandRingItem.clearPlayer(uuid);
			AllaySPJukebox.onPlayerDisconnect(handler.player);
			UndeadNeutralState.clearPlayer(uuid);
			net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions.clearPlayer(uuid);
			PLAYER_LANGUAGES.remove(uuid);
			System.out.println("[SSC_ADDON] DISCONNECT cleanup completed");
		});
	}






}
