package net.onixary.shapeShifterCurseFabric.ssc_addon;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
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
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.command.SscAddonCommands;
import net.onixary.shapeShifterCurseFabric.ssc_addon.condition.SscAddonConditions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonClientConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonServerConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.item.SpawnEggItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.AllayClearMarkerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.AllayFriendMarkerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.InfectionSporeBombEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.*;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.*;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.SscAddonPowers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.BlizzardTankRechargeRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.RefillMoisturizerRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.ReloadSnowballLauncherRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.InfiniteEnergyPotionRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.SpUpgradeRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.screen.PotionBagScreenHandler;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSoulEnergy;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormRegen;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Rarity;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.onixary.shapeShifterCurseFabric.ssc_addon.criteria.OnTransformAddonForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ThrownWaterSpearEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ParasiticSeedProjectile;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.TidalOrbEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.LaserBeamEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot;
import net.onixary.shapeShifterCurseFabric.ssc_addon.story.MoonScarStoryManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.story.TideSpiritStoryManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.NineLivesManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.NovaSkillManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticAbsorptionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticCombatTracker;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticSeedEnergyRegen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticSeedFieldManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SeedEnergyEatingHandler;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritLandingSurgeManager;
import net.jackcooper.shapeShifterCurseAddon.event.AddonFormAdvancementHandler;
import net.jackcooper.shapeShifterCurseAddon.event.SscAddonServerEvents;
import net.jackcooper.shapeShifterCurseAddon.event.SscAddonInteractionEvents;
import net.jackcooper.shapeShifterCurseAddon.event.SscAddonPlayerEvents;
import net.jackcooper.shapeShifterCurseAddon.event.WitchFamiliarSpawnHandler;
import net.jackcooper.shapeShifterCurseAddon.event.CursedMoonSpMessageHandler;
import net.jackcooper.shapeShifterCurseAddon.event.FluorescentDodgeHandler;
import net.jackcooper.shapeShifterCurseAddon.event.StorySleepTimeGuardHandler;
import net.jackcooper.shapeShifterCurseAddon.event.VillagerTradeGuardHandler;

public class SscAddon implements ModInitializer {

	// 存储玩家客户端语言设置，用于发送正确语言的消息
	public static final ConcurrentHashMap<UUID, String> PLAYER_LANGUAGES = new ConcurrentHashMap<>();

	public static final StatusEffect FOX_FIRE_BURN = new FoxFireBurnEffect();
	public static final StatusEffect BLUE_FIRE_RING = new BlueFireRingEffect();
	public static final StatusEffect PLAYING_DEAD = new PlayingDeadEffect();
	public static final StatusEffect TRUE_INVISIBILITY = new TrueInvisibilityEffect();
	public static final StatusEffect PRE_INVISIBILITY = new PreInvisibilityEffect();
	public static final StatusEffect STUN = new StunEffect();
	public static final StatusEffect ROOTED = new RootedEffect();
	public static final StatusEffect GUARANTEED_CRIT = new GuaranteedCritEffect();
	public static final StatusEffect FROST_FREEZE = new FrostFreezeEffect();
	public static final StatusEffect FROST_FALL = new FrostFallEffect();
	public static final StatusEffect PURIFIED = new PurifiedEffect();
	public static final StatusEffect BAT_REGEN = new BatRegenEffect();
	public static final StatusEffect BAT_POISON = new BatPoisonEffect();
	public static final StatusEffect BAT_ABSORPTION = new BatAbsorptionEffect();
	// 幽雾化形 - 雾化状态标记效果
	public static final StatusEffect MIST_FORM = new MistFormEffect();
	// 幽雾化形 - 凝聚爆破蓄力标记效果（客户端据此减速 50%）
	public static final StatusEffect MIST_CHARGING = new MistChargingEffect();
	public static final StatusEffect SAND_BLIND = new SandBlindEffect();
	// 失聪：客机 SoundManagerDeafenMixin 据此静音受影响玩家自身的所有声音
	public static final StatusEffect DEAFEN = new DeafenEffect();
	// 潮汐波动吸附减速（荧光幼灵）- 15% 移速降低
	public static final StatusEffect TIDAL_SLOW = new TidalSlowEffect();
	/** 侵蚀烙印标记效果 - 1层(黄色) */
	public static final StatusEffect EROSION_BRAND_MARKER_1 = new ErosionBrandMarkerEffect(0xFFD700);
	/** 侵蚀烙印标记效果 - 2层(橙色) */
	public static final StatusEffect EROSION_BRAND_MARKER_2 = new ErosionBrandMarkerEffect(0xFF8C00);
	/** 侵蚀烙印标记效果 - 3层(红色) */
	public static final StatusEffect EROSION_BRAND_MARKER_3 = new ErosionBrandMarkerEffect(0xDC143C);
	public static final Item POTION_BAG = new PotionBagItem(new Item.Settings().maxCount(1));
	public static final EntityType<FrostBallEntity> FROST_BALL_ENTITY =
			registerEntity("frost_ball", SpawnGroup.MISC, FrostBallEntity::new, 0.25f, 0.25f, 64, 10);
	// 进化美西螈「投掷水矛」直线水矛投射物（无重力匀速）
	public static final EntityType<ThrownWaterSpearEntity> THROWN_WATER_SPEAR_ENTITY =
			registerEntity("thrown_water_spear", SpawnGroup.MISC, ThrownWaterSpearEntity::new, 0.4f, 0.4f, 64, 10);
	// red 狐火火球投射物
	public static final EntityType<FoxFireballEntity> FOX_FIREBALL_ENTITY =
			registerEntity("fox_fireball", SpawnGroup.MISC, FoxFireballEntity::new, 0.25f, 0.25f, 64, 2);
	// 寄生果蝠「感染孢子炸弹」投掷物
	public static final EntityType<InfectionSporeBombEntity> INFECTION_SPORE_BOMB_ENTITY =
			registerEntity("infection_spore_bomb", SpawnGroup.MISC, InfectionSporeBombEntity::new, 0.25f, 0.25f, 64, 10);
	// 寄生果蝠主技能「灵果寄生」投掷物
	public static final EntityType<ParasiticSeedProjectile> PARASITIC_SEED_ENTITY =
			registerEntity("parasitic_seed", SpawnGroup.MISC, ParasiticSeedProjectile::new, 0.25f, 0.25f, 64, 10);
	public static final ScreenHandlerType<PotionBagScreenHandler> POTION_BAG_SCREEN_HANDLER = new ScreenHandlerType<>(PotionBagScreenHandler::new, FeatureSet.empty());
	public static final EntityType<FrostStormEntity> FROST_STORM_ENTITY =
			registerEntity("frost_storm", SpawnGroup.MISC, FrostStormEntity::new, 1.0f, 2.0f, 64, 10);
	// 荧光幼灵 - 潮汐波动粒子球实体
	public static final EntityType<TidalOrbEntity> TIDAL_ORB_ENTITY =
			registerEntity("tidal_orb", SpawnGroup.MISC, TidalOrbEntity::new, 0.5f, 0.5f, 64, 1);
	// 荧光幼灵 - 法阵激光实体
	public static final EntityType<LaserBeamEntity> LASER_BEAM_ENTITY =
			registerEntity("laser_beam", SpawnGroup.MISC, LaserBeamEntity::new, 0.5f, 0.5f, 96, 1);
	public static final Item SP_UPGRADE_THING = new SpUpgradeItem(new Item.Settings().maxCount(1));
	public static final Item PORTABLE_MOISTURIZER = new PortableMoisturizerItem(new Item.Settings().maxCount(1));
	public static final EntityType<WaterSpearEntity> WATER_SPEAR_ENTITY =
			registerEntity("water_spear", SpawnGroup.MISC, WaterSpearEntity::new, 0.5f, 0.5f, 4, 20);
	public static final Item SNOWBALL_LAUNCHER = new SnowballLauncherItem(new Item.Settings().maxCount(1));
	public static final Item PORTABLE_FRIDGE = new PortableFridgeItem(new Item.Settings().maxCount(1));
	public static final Item BLUE_FIRE_AMULET = new BlueFireAmuletItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item INVISIBILITY_CLOAK = new InvisibilityCloakItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item LIFESAVING_CAT_TAIL = new LifesavingCatTailItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item PHANTOM_BELL = new PhantomBellItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item FROST_AMULET = new FrostAmuletItem(new Item.Settings().maxCount(1).fireproof());
	// 吸血蝙蝠 / 果蝠 专属饰品（半好半坏）
	public static final Item BLOOD_GARNET = new BloodGarnetItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item BLOODLUST_RING = new BloodlustRingItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item HUMUS_RING = new HumusRingItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item TWIN_POD = new TwinPodItem(new Item.Settings().maxCount(1).fireproof());
	public static final RecipeSerializer<RefillMoisturizerRecipe> REFILL_MOISTURIZER_SERIALIZER = new SpecialRecipeSerializer<>(RefillMoisturizerRecipe::new);
	public static final RecipeSerializer<ReloadSnowballLauncherRecipe> RELOAD_SNOWBALL_LAUNCHER_SERIALIZER = new SpecialRecipeSerializer<>(ReloadSnowballLauncherRecipe::new);
	public static final RecipeSerializer<BlizzardTankRechargeRecipe> BLIZZARD_TANK_RECHARGE_SERIALIZER = new SpecialRecipeSerializer<>(BlizzardTankRechargeRecipe::new);
	public static final RecipeSerializer<SpUpgradeRecipe> SP_UPGRADE_SERIALIZER = new SpecialRecipeSerializer<>(SpUpgradeRecipe::new);
	// 60 durability like wooden sword, auto-consumed over 60 seconds
	public static final Item WATER_SPEAR = new WaterSpearItem(new Item.Settings().maxCount(1).maxDamage(60));
	// SP美西螈水矛合成内部冷却（服务端权威）：UUID -> 冷却结束的服务器 tick；与箭冷却条显示同步
	// 注：以下 4 个水矛调试/冷却字段为 public，供拆分出去的事件类（水矛监测/合成逻辑）跨类访问
	public static final Map<UUID, Long> WATER_SPEAR_CRAFT_CD = new ConcurrentHashMap<>();
	public static final int WATER_SPEAR_CRAFT_CD_TICKS = 70; // 3.5 秒（与 Apoli 合成能力 cooldown 对齐；水矛消失后起算）
	// [DEBUG] 水矛合成监测日志
	public static final Logger WS_DBG = LoggerFactory.getLogger("WaterSpearDebug");
	// [DEBUG] 每玩家上次水矛数（用于监测水矛出现时刻）
	public static final Map<UUID, Integer> WS_LAST_SPEAR_COUNT = new ConcurrentHashMap<>();
	// Evolution Stone
	public static final Item EVOLUTION_STONE = new EvolutionStoneItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item CORAL_BALL = new Item(new Item.Settings().maxCount(64));
	public static final Item ACTIVE_CORAL_NECKLACE = new ActiveCoralNecklaceItem(new Item.Settings().maxCount(1));
	// 风灵专属项链：加快疾风连爪耐力回复；朔望专属项链：强化九命复活
	public static final Item WIND_SPIRIT_STAMINA_NECKLACE = new WindSpiritStaminaNecklaceItem(new Item.Settings().maxCount(1));
	public static final Item NOVA_REVIVE_NECKLACE = new NovaReviveNecklaceItem(new Item.Settings().maxCount(1));
	public static final Item ANUBIS_CRYSTAL = new AnubisCrystalItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item ANKH_STONE = new AnkhStoneItem(new Item.Settings().maxCount(1).fireproof());
	// 契灵专属：绑定脚环（feet/aglet 槽，与守御脚环互斥）
	public static final Item BINDING_ANKLET = new BindingAnkletItem(new Item.Settings().maxCount(1).fireproof());
	// SP Golden Sandstorm items
	public static final Item EROSION_SAND_PRISM = new ErosionSandPrismItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item WITHERED_SAND_RING = new WitheredSandRingItem(new Item.Settings().maxCount(1).fireproof());
	// SP Allay items
	public static final Item ALLAY_HEAL_WAND = new AllayHealWandItem(new Item.Settings().maxCount(1));
	public static final Item ALLAY_JUKEBOX = new AllayJukeboxItem(new Item.Settings().maxCount(1));
	public static final Item FRIEND_MARKER = new AllayFriendMarkerItem(new Item.Settings().maxCount(64));
	public static final Item CLEAR_FRIEND_MARKER = new AllayClearMarkerItem(new Item.Settings().maxCount(64));
	// Entities
	public static final EntityType<AllayFriendMarkerEntity> FRIEND_MARKER_ENTITY_TYPE =
			registerEntity("friend_marker", SpawnGroup.MISC, AllayFriendMarkerEntity::new, 0.25f, 0.25f, 4, 10);
	public static final EntityType<AllayClearMarkerEntity> CLEAR_MARKER_ENTITY_TYPE =
			registerEntity("clear_friend_marker", SpawnGroup.MISC, AllayClearMarkerEntity::new, 0.25f, 0.25f, 4, 10);
	// 女巫使魔实体
	public static final EntityType<WitchFamiliarEntity> WITCH_FAMILIAR_ENTITY =
			registerEntity("witch_familiar", SpawnGroup.MONSTER, WitchFamiliarEntity::new, 0.5f, 0.7f, 64, 3);
	// 女巫使魔怪物蛋（主色狐狸沙棕 #D5B48F，次色青蓝 #31C8CC）
	public static final Item WITCH_FAMILIAR_SPAWN_EGG = new SpawnEggItem(WITCH_FAMILIAR_ENTITY, 0xD5B48F, 0x31C8CC, new Item.Settings());
	// 无限压缩能量药水（饮用/喷溅/滞留三型；使用后空瓶自充能，效果同压缩能量药水 feed_potion）
	public static final Item INFINITE_ENERGY_POTION = new InfiniteEnergyPotionItem(
			new Item.Settings().maxCount(1), InfiniteEnergyPotionItem.Type.DRINK);
	public static final Item INFINITE_ENERGY_POTION_SPLASH = new InfiniteEnergyPotionItem(
			new Item.Settings().maxCount(1), InfiniteEnergyPotionItem.Type.SPLASH);
	public static final Item INFINITE_ENERGY_POTION_LINGERING = new InfiniteEnergyPotionItem(
			new Item.Settings().maxCount(1), InfiniteEnergyPotionItem.Type.LINGERING);
	// 凋零药水（饮用/喷溅/滞留三型，任何人可用，凋零II 20秒；瓶身附魔光效）
	// 堆叠：默认不可叠(maxCount 1)；使魔系叠8 / SP阿努比斯叠3（由 WitherPotionStackMixin 按形态抬高）
	public static final Item WITHER_POTION = new WitherPotionItem(
			new Item.Settings().maxCount(1), WitherPotionItem.Type.DRINK);
	public static final Item WITHER_POTION_SPLASH = new WitherPotionItem(
			new Item.Settings().maxCount(1), WitherPotionItem.Type.SPLASH);
	public static final Item WITHER_POTION_LINGERING = new WitherPotionItem(
			new Item.Settings().maxCount(1), WitherPotionItem.Type.LINGERING);
	public static final RecipeSerializer<InfiniteEnergyPotionRecipe> INFINITE_ENERGY_POTION_SERIALIZER = new SpecialRecipeSerializer<>(InfiniteEnergyPotionRecipe::new);
	// 幻形之梦 音乐唱片（Shape Shifter's Dream）：流式音效 + vanilla 唱片物品，145 秒
	public static final Identifier SHAPE_SHIFTERS_DREAM_ID = new Identifier("ssc_addon", "shape_shifters_dream");
	public static final SoundEvent SHAPE_SHIFTERS_DREAM_EVENT = SoundEvent.of(SHAPE_SHIFTERS_DREAM_ID);
	public static final Item MUSIC_DISC_SHAPE_SHIFTERS_DREAM = new MusicDiscItem(
			15, SHAPE_SHIFTERS_DREAM_EVENT, new Item.Settings().maxCount(1).rarity(Rarity.RARE), 145);
	public static final ItemGroup SSC_ADDON_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier("ssc_addon", "group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.ssc_addon.group"))
					.icon(() -> new ItemStack(SP_UPGRADE_THING))
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
						entries.add(WIND_SPIRIT_STAMINA_NECKLACE);
						entries.add(NOVA_REVIVE_NECKLACE);
						entries.add(ANUBIS_CRYSTAL);
						entries.add(ANKH_STONE);
						entries.add(BINDING_ANKLET);
						entries.add(EROSION_SAND_PRISM);
						entries.add(WITHERED_SAND_RING);
						entries.add(BLOOD_GARNET);
						entries.add(BLOODLUST_RING);
						entries.add(HUMUS_RING);
						entries.add(TWIN_POD);
						entries.add(ALLAY_HEAL_WAND);
						entries.add(ALLAY_JUKEBOX);
						entries.add(MUSIC_DISC_SHAPE_SHIFTERS_DREAM);
						entries.add(FRIEND_MARKER);
						entries.add(CLEAR_FRIEND_MARKER);
						entries.add(WITCH_FAMILIAR_SPAWN_EGG);
						entries.add(INFINITE_ENERGY_POTION);
						entries.add(INFINITE_ENERGY_POTION_SPLASH);
						entries.add(INFINITE_ENERGY_POTION_LINGERING);
						// 凋零药水（饮用/喷溅/滞留）
						entries.add(WITHER_POTION);
						entries.add(WITHER_POTION_SPLASH);
						entries.add(WITHER_POTION_LINGERING);
					})
					.build());
	// SP Allay sound events
	public static final Identifier ALLAY_HEAL_MUSIC_ID = new Identifier("ssc_addon", "allay_heal_music");
	public static final Identifier ALLAY_SPEED_MUSIC_ID = new Identifier("ssc_addon", "allay_speed_music");
	public static final SoundEvent ALLAY_HEAL_MUSIC_EVENT = SoundEvent.of(ALLAY_HEAL_MUSIC_ID);
	public static final SoundEvent ALLAY_SPEED_MUSIC_EVENT = SoundEvent.of(ALLAY_SPEED_MUSIC_ID);

	// 附属形态切换成就触发器（统一一个 Criterion，不同 advancement JSON 用 form_id 条件区分）
	public static final OnTransformAddonForm ON_TRANSFORM_ADDON_FORM =
			Criteria.register(new OnTransformAddonForm());

	@Override
	public void onInitialize() {
		registerConfig();
		registerStatusEffects();
		registerItems();
		registerRecipeSerializers();
		registerSoundEvents();
		registerEntityAttributes();
		registerApoliSystems();
		SscAddonForms.register();
		registerCommands();
		SscAddonServerEvents.registerTickHandlers();
		WitchFamiliarSpawnHandler.register();
		SscAddonPlayerEvents.register();
		SscAddonServerEvents.registerStunOrphanCleanup();
		// 风灵被动：落地风涌（事件监听）；风压领域由 mixin（WindSpiritProjectilePressureMixin）驱动
		WindSpiritLandingSurgeManager.register();
		SscAddonServerEvents.registerFeralBodyYawSync();
		SscAddonServerEvents.registerServerLifecycleHandlers();
		SscAddonInteractionEvents.register();
		AnubisWolfSpSoulEnergy.registerEvents();
		GoldenSandstormRegen.init();
		MancianimaMarkManager.register();
		MoonScarStoryManager.register();
		TideSpiritStoryManager.register();
		// 原版官方事件监听（由 mixin 迁移而来）：诅咒之月 SP 形态提示 + 附属形态变身成就
		CursedMoonSpMessageHandler.register();
		AddonFormAdvancementHandler.register();
		VillagerTradeGuardHandler.register();
		FluorescentDodgeHandler.register();
		StorySleepTimeGuardHandler.register();
		// SSCA 进化路线数据驱动加载器（datapack reload，扫描 data/<ns>/ssca_evolution/routes/*.json）
		ResourceManagerHelper.get(ResourceType.SERVER_DATA)
				.registerReloadListener(EvolutionRegistry.INSTANCE);
	}



	private void registerConfig() {
		AutoConfig.register(SSCAddonClientConfig.class, GsonConfigSerializer::new);
		AutoConfig.register(SSCAddonServerConfig.class, GsonConfigSerializer::new);
	}

	private void registerStatusEffects() {
		registerEffect("fox_fire_burn", FOX_FIRE_BURN);
		registerEffect("playing_dead", PLAYING_DEAD);
		registerEffect("blue_fire_ring", BLUE_FIRE_RING);
		registerEffect("true_invisibility", TRUE_INVISIBILITY);
		registerEffect("pre_invisibility", PRE_INVISIBILITY);
		registerEffect("stun", STUN);
		registerEffect("rooted", ROOTED);
		registerEffect("guaranteed_crit", GUARANTEED_CRIT);
		registerEffect("frost_freeze", FROST_FREEZE);
		registerEffect("frost_fall", FROST_FALL);
		registerEffect("purified", PURIFIED);
		registerEffect("bat_regen", BAT_REGEN);
		registerEffect("bat_poison", BAT_POISON);
		registerEffect("bat_absorption", BAT_ABSORPTION);
		registerEffect("mist_form", MIST_FORM);
		registerEffect("mist_charging", MIST_CHARGING);
		registerEffect("sand_blind", SAND_BLIND);
		registerEffect("deafen", DEAFEN);
		registerEffect("erosion_brand_marker_1", EROSION_BRAND_MARKER_1);
		registerEffect("erosion_brand_marker_2", EROSION_BRAND_MARKER_2);
		registerEffect("erosion_brand_marker_3", EROSION_BRAND_MARKER_3);
		registerEffect("tidal_slow", TIDAL_SLOW);
	}

	private void registerItems() {
		registerItem("sp_upgrade_thing", SP_UPGRADE_THING);
		registerItem("portable_moisturizer", PORTABLE_MOISTURIZER);
		registerItem("snowball_launcher", SNOWBALL_LAUNCHER);
		registerItem("portable_fridge", PORTABLE_FRIDGE);
		registerItem("blue_fire_amulet", BLUE_FIRE_AMULET);
		registerItem("frost_amulet", FROST_AMULET);
		registerItem("blood_garnet", BLOOD_GARNET);
		registerItem("bloodlust_ring", BLOODLUST_RING);
		registerItem("humus_ring", HUMUS_RING);
		registerItem("twin_pod", TWIN_POD);
		registerItem("invisibility_cloak", INVISIBILITY_CLOAK);
		registerItem("lifesaving_cat_tail", LIFESAVING_CAT_TAIL);
		registerItem("phantom_bell", PHANTOM_BELL);
		registerItem("water_spear", WATER_SPEAR);
		registerItem("potion_bag", POTION_BAG);
		Registry.register(Registries.SCREEN_HANDLER, new Identifier("ssc_addon", "potion_bag"), POTION_BAG_SCREEN_HANDLER);
		registerItem("evolution_stone", EVOLUTION_STONE);
		registerItem("coral_ball", CORAL_BALL);
		registerItem("active_coral_necklace", ACTIVE_CORAL_NECKLACE);
		registerItem("wind_spirit_stamina_necklace", WIND_SPIRIT_STAMINA_NECKLACE);
		registerItem("nova_revive_necklace", NOVA_REVIVE_NECKLACE);
		registerItem("anubis_crystal", ANUBIS_CRYSTAL);
		registerItem("ankh_stone", ANKH_STONE);
		registerItem("binding_anklet", BINDING_ANKLET);
		BindingAnkletItem.registerLootTable();
		registerItem("erosion_sand_prism", EROSION_SAND_PRISM);
		registerItem("withered_sand_ring", WITHERED_SAND_RING);
		registerItem("allay_heal_wand", ALLAY_HEAL_WAND);
		registerItem("allay_jukebox", ALLAY_JUKEBOX);
		registerItem("friend_marker", FRIEND_MARKER);
		registerItem("clear_friend_marker", CLEAR_FRIEND_MARKER);
		registerItem("witch_familiar_spawn_egg", WITCH_FAMILIAR_SPAWN_EGG);
		registerItem("infinite_energy_potion", INFINITE_ENERGY_POTION);
		registerItem("infinite_energy_potion_splash", INFINITE_ENERGY_POTION_SPLASH);
		registerItem("infinite_energy_potion_lingering", INFINITE_ENERGY_POTION_LINGERING);
		registerItem("wither_potion", WITHER_POTION);
		registerItem("wither_potion_splash", WITHER_POTION_SPLASH);
		registerItem("wither_potion_lingering", WITHER_POTION_LINGERING);
		registerItem("music_disc_shape_shifters_dream", MUSIC_DISC_SHAPE_SHIFTERS_DREAM);
		// 酿造（饮用+火药→喷溅；喷溅+龙息→滞留）完全由 BrewingRegistryInfiniteMixin 接管：
		// 直接拦截 hasRecipe/craft 驱动产出，槽位放行由 BrewingStandInfinitePotionMixin 处理。
		// 旧的 ITEM_RECIPES 注册需构造 PotionBrewing$Mix，在 Forge/Sinytra Connector 下构造签名不同会崩溃，已移除。
	}

	private void registerRecipeSerializers() {
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "refill_moisturizer"), REFILL_MOISTURIZER_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "reload_snowball_launcher"), RELOAD_SNOWBALL_LAUNCHER_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "blizzard_tank_recharge"), BLIZZARD_TANK_RECHARGE_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "sp_upgrade_crafting"), SP_UPGRADE_SERIALIZER);
		Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "infinite_energy_potion_crafting"), INFINITE_ENERGY_POTION_SERIALIZER);
	}

	// 拆分的私有方法

	private void registerSoundEvents() {
		Registry.register(Registries.SOUND_EVENT, ALLAY_HEAL_MUSIC_ID, ALLAY_HEAL_MUSIC_EVENT);
		Registry.register(Registries.SOUND_EVENT, ALLAY_SPEED_MUSIC_ID, ALLAY_SPEED_MUSIC_EVENT);
		Registry.register(Registries.SOUND_EVENT, SHAPE_SHIFTERS_DREAM_ID, SHAPE_SHIFTERS_DREAM_EVENT);
	}

	private void registerEntityAttributes() {
		FabricDefaultAttributeRegistry.register(WITCH_FAMILIAR_ENTITY, WitchFamiliarEntity.createWitchFamiliarAttributes());
	}

	// 注册辅助方法（消除重复的 Registry.register 样板）
	private static <T extends Entity> EntityType<T> registerEntity(String id, SpawnGroup group,
			EntityType.EntityFactory<T> factory, float width, float height, int trackRange, int updateRate) {
		return Registry.register(Registries.ENTITY_TYPE, new Identifier("ssc_addon", id),
				FabricEntityTypeBuilder.<T>create(group, factory)
						.dimensions(EntityDimensions.fixed(width, height))
						.trackRangeBlocks(trackRange).trackedUpdateRate(updateRate)
						.build());
	}

	private static void registerItem(String id, Item item) {
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", id), item);
	}

	private static void registerEffect(String id, StatusEffect effect) {
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", id), effect);
	}

	private void registerApoliSystems() {
		SscAddonActions.register();
		SscAddonConditions.register();
		SscAddonPowers.register();
		SscAddonNetworking.registerServerReceivers();
		StoryBookLoot.init();
		AllaySPTotem.init();
		InfectionSporeManager.init();
		ParasiticSeedFieldManager.init();
		ParasiticCombatTracker.init();
		ParasiticAbsorptionManager.init();
		ParasiticSeedEnergyRegen.init();
		NineLivesManager.init();
		NovaSkillManager.init();
		SeedEnergyEatingHandler.register();
		LifesavingCatTailItem.registerLootTable();
		AnkhStoneItem.registerLootTable();
		AnubisCrystalItem.registerLootTable();
		ErosionSandPrismItem.registerLootTable();
		WitheredSandRingItem.registerLootTable();
		BloodGarnetItem.registerLootTable();
		BloodlustRingItem.registerLootTable();
		HumusRingItem.registerLootTable();
		net.jackcooper.shapeShifterCurseAddon.loot.EvolutionItemsLoot.register();
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SscAddonCommands.register(dispatcher));
	}
}
