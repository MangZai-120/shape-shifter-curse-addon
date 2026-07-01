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
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.NormalGroup;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.NoInstinct;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.NoCursedMoonEffect;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.SpecialForm;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.InhibitorImmune;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.HasSlowFall;
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
	public static final StatusEffect BAT_REGEN = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.BatRegenEffect();
	public static final StatusEffect BAT_POISON = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.BatPoisonEffect();
	public static final StatusEffect BAT_ABSORPTION = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.BatAbsorptionEffect();
	// 幽雾化形 - 雾化状态标记效果
	public static final StatusEffect MIST_FORM = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.MistFormEffect();
	// 幽雾化形 - 凝聚爆破蓄力标记效果（客户端据此减速 50%）
	public static final StatusEffect MIST_CHARGING = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.MistChargingEffect();
	public static final StatusEffect SAND_BLIND = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.SandBlindEffect();
	// 失聪：客机 SoundManagerDeafenMixin 据此静音受影响玩家自身的所有声音
	public static final StatusEffect DEAFEN = new net.onixary.shapeShifterCurseFabric.ssc_addon.effect.DeafenEffect();
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
	);
	// red 狐火火球投射物
	public static final EntityType<net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity> FOX_FIREBALL_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "fox_fireball"),
			FabricEntityTypeBuilder.<net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity>create(SpawnGroup.MISC, net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(64).trackedUpdateRate(2)
					.build()
	);
	// 寄生果蝠「感染孢子炸弹」投掷物
	public static final EntityType<InfectionSporeBombEntity> INFECTION_SPORE_BOMB_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "infection_spore_bomb"),
			FabricEntityTypeBuilder.<InfectionSporeBombEntity>create(SpawnGroup.MISC, InfectionSporeBombEntity::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(64).trackedUpdateRate(10)
					.build()
	);
	// 寄生果蝠主技能「灵果寄生」投掷物
	public static final EntityType<net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ParasiticSeedProjectile> PARASITIC_SEED_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier("ssc_addon", "parasitic_seed"),
			FabricEntityTypeBuilder.<net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ParasiticSeedProjectile>create(SpawnGroup.MISC, net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ParasiticSeedProjectile::new)
					.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
					.trackRangeBlocks(64).trackedUpdateRate(10)
					.build()
	);
	public static final ScreenHandlerType<PotionBagScreenHandler> POTION_BAG_SCREEN_HANDLER = new ScreenHandlerType<>(PotionBagScreenHandler::new, FeatureSet.empty());
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
	// 吸血蝙蝠 / 果蝠 专属饰品（半好半坏）
	public static final Item BLOOD_GARNET = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.BloodGarnetItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item BLOODLUST_RING = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.BloodlustRingItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item HUMUS_RING = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.HumusRingItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item TWIN_POD = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.TwinPodItem(new Item.Settings().maxCount(1).fireproof());
	public static final RecipeSerializer<RefillMoisturizerRecipe> REFILL_MOISTURIZER_SERIALIZER = new SpecialRecipeSerializer<>(RefillMoisturizerRecipe::new);
	public static final RecipeSerializer<ReloadSnowballLauncherRecipe> RELOAD_SNOWBALL_LAUNCHER_SERIALIZER = new SpecialRecipeSerializer<>(ReloadSnowballLauncherRecipe::new);
	public static final RecipeSerializer<BlizzardTankRechargeRecipe> BLIZZARD_TANK_RECHARGE_SERIALIZER = new SpecialRecipeSerializer<>(BlizzardTankRechargeRecipe::new);
	public static final RecipeSerializer<SpUpgradeRecipe> SP_UPGRADE_SERIALIZER = new SpecialRecipeSerializer<>(SpUpgradeRecipe::new);
	// 60 durability like wooden sword, auto-consumed over 60 seconds
	public static final Item WATER_SPEAR = new WaterSpearItem(new Item.Settings().maxCount(1).maxDamage(60));
	// SP美西螈水矛合成内部冷却（服务端权威）：UUID -> 冷却结束的服务器 tick；与箭冷却条显示同步
	private static final java.util.Map<java.util.UUID, Long> WATER_SPEAR_CRAFT_CD = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int WATER_SPEAR_CRAFT_CD_TICKS = 70; // 3.5 秒（与 Apoli 合成能力 cooldown 对齐；水矛消失后起算）
	// [DEBUG] 水矛合成监测日志
	private static final org.slf4j.Logger WS_DBG = org.slf4j.LoggerFactory.getLogger("WaterSpearDebug");
	// [DEBUG] 每玩家上次水矛数（用于监测水矛出现时刻）
	private static final java.util.Map<java.util.UUID, Integer> WS_LAST_SPEAR_COUNT = new java.util.concurrent.ConcurrentHashMap<>();
	// Evolution Stone
	public static final Item EVOLUTION_STONE = new EvolutionStoneItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item CORAL_BALL = new Item(new Item.Settings().maxCount(64));
	public static final Item ACTIVE_CORAL_NECKLACE = new ActiveCoralNecklaceItem(new Item.Settings().maxCount(1));
	public static final Item ANUBIS_CRYSTAL = new AnubisCrystalItem(new Item.Settings().maxCount(1).fireproof());
	public static final Item ANKH_STONE = new AnkhStoneItem(new Item.Settings().maxCount(1).fireproof());
	// 契灵专属：绑定脚环（feet/aglet 槽，与守御脚环互斥）
	public static final Item BINDING_ANKLET = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.BindingAnkletItem(new Item.Settings().maxCount(1).fireproof());
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
	// 无限压缩能量药水（饮用/喷溅/滞留三型；使用后空瓶自充能，效果同压缩能量药水 feed_potion）
	public static final Item INFINITE_ENERGY_POTION = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem(
			new Item.Settings().maxCount(1), net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem.Type.DRINK);
	public static final Item INFINITE_ENERGY_POTION_SPLASH = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem(
			new Item.Settings().maxCount(1), net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem.Type.SPLASH);
	public static final Item INFINITE_ENERGY_POTION_LINGERING = new net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem(
			new Item.Settings().maxCount(1), net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem.Type.LINGERING);
	public static final RecipeSerializer<InfiniteEnergyPotionRecipe> INFINITE_ENERGY_POTION_SERIALIZER = new SpecialRecipeSerializer<>(InfiniteEnergyPotionRecipe::new);
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
						entries.add(BINDING_ANKLET);
						entries.add(EROSION_SAND_PRISM);
						entries.add(WITHERED_SAND_RING);
						entries.add(BLOOD_GARNET);
						entries.add(BLOODLUST_RING);
						entries.add(HUMUS_RING);
						entries.add(TWIN_POD);
						entries.add(ALLAY_HEAL_WAND);
						entries.add(ALLAY_JUKEBOX);
						entries.add(FRIEND_MARKER);
						entries.add(CLEAR_FRIEND_MARKER);
						entries.add(WITCH_FAMILIAR_SPAWN_EGG);
						entries.add(INFINITE_ENERGY_POTION);
						entries.add(INFINITE_ENERGY_POTION_SPLASH);
						entries.add(INFINITE_ENERGY_POTION_LINGERING);
					})
					.build());
	// SP Allay sound events
	public static final Identifier ALLAY_HEAL_MUSIC_ID = new Identifier("ssc_addon", "allay_heal_music");
	public static final Identifier ALLAY_SPEED_MUSIC_ID = new Identifier("ssc_addon", "allay_speed_music");
	public static final SoundEvent ALLAY_HEAL_MUSIC_EVENT = SoundEvent.of(ALLAY_HEAL_MUSIC_ID);
	public static final SoundEvent ALLAY_SPEED_MUSIC_EVENT = SoundEvent.of(ALLAY_SPEED_MUSIC_ID);

	// 附属形态切换成就触发器（统一一个 Criterion，不同 advancement JSON 用 form_id 条件区分）
	public static final net.onixary.shapeShifterCurseFabric.ssc_addon.criteria.OnTransformAddonForm ON_TRANSFORM_ADDON_FORM =
			net.minecraft.advancement.criterion.Criteria.register(new net.onixary.shapeShifterCurseFabric.ssc_addon.criteria.OnTransformAddonForm());

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
		registerStunOrphanCleanup();
		registerFeralBodyYawSync();
		registerServerLifecycleHandlers();
		registerMancianimaEvents();
		AnubisWolfSpSoulEnergy.registerEvents();
		GoldenSandstormRegen.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager.register();
		net.onixary.shapeShifterCurseFabric.ssc_addon.story.MoonScarStoryManager.register();
		// SSCA 进化路线数据驱动加载器（datapack reload，扫描 data/<ns>/ssca_evolution/routes/*.json）
		net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.SERVER_DATA)
				.registerReloadListener(net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry.INSTANCE);
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
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "bat_regen"), BAT_REGEN);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "bat_poison"), BAT_POISON);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "bat_absorption"), BAT_ABSORPTION);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "mist_form"), MIST_FORM);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "mist_charging"), MIST_CHARGING);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "sand_blind"), SAND_BLIND);
		Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "deafen"), DEAFEN);
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
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "blood_garnet"), BLOOD_GARNET);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "bloodlust_ring"), BLOODLUST_RING);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "humus_ring"), HUMUS_RING);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "twin_pod"), TWIN_POD);
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
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "binding_anklet"), BINDING_ANKLET);
		net.onixary.shapeShifterCurseFabric.ssc_addon.item.BindingAnkletItem.registerLootTable();
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "erosion_sand_prism"), EROSION_SAND_PRISM);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "withered_sand_ring"), WITHERED_SAND_RING);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "allay_heal_wand"), ALLAY_HEAL_WAND);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "allay_jukebox"), ALLAY_JUKEBOX);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "friend_marker"), FRIEND_MARKER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "clear_friend_marker"), CLEAR_FRIEND_MARKER);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "witch_familiar_spawn_egg"), WITCH_FAMILIAR_SPAWN_EGG);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "infinite_energy_potion"), INFINITE_ENERGY_POTION);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "infinite_energy_potion_splash"), INFINITE_ENERGY_POTION_SPLASH);
		Registry.register(Registries.ITEM, new Identifier("ssc_addon", "infinite_energy_potion_lingering"), INFINITE_ENERGY_POTION_LINGERING);
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
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticSeedFieldManager.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticCombatTracker.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticAbsorptionManager.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticSeedEnergyRegen.init();
		net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SeedEnergyEatingHandler.register();
		LifesavingCatTailItem.registerLootTable();
		AnkhStoneItem.registerLootTable();
		AnubisCrystalItem.registerLootTable();
		ErosionSandPrismItem.registerLootTable();
		WitheredSandRingItem.registerLootTable();
		net.onixary.shapeShifterCurseFabric.ssc_addon.item.BloodGarnetItem.registerLootTable();
		net.onixary.shapeShifterCurseFabric.ssc_addon.item.BloodlustRingItem.registerLootTable();
		net.onixary.shapeShifterCurseFabric.ssc_addon.item.HumusRingItem.registerLootTable();
	}

	private void registerForms() {
		Form_Axolotl3 axolotlForm = new Form_Axolotl3(FormIdentifiers.AXOLOTL_SP);
		axolotlForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 美西螈SP为人形不缩放(scale=1.0)，但仍需 RESET 兜底清除变身前残留的缩放值
		axolotlForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(1.0f, 1.0f));
		RegPlayerForms.registerPlayerForm(axolotlForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_axolotl_sp")).registerForm(1, 5, axolotlForm));

		Form_FamiliarFox3 familiarFoxForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_SP);
		familiarFoxForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（值与 origin power form_familiar_fox_sp_scale 一致）
		familiarFoxForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.5f, 0.6f));

		RegPlayerForms.registerPlayerForm(familiarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_familiar_fox_sp")).registerForm(1, 5, familiarFoxForm));

		// 进化使魔（复用使魔模型/动画，能力按进化解锁——批次2 形态骨架）
		Form_FamiliarFox3 upgradeFamiliarFoxForm = new Form_FamiliarFox3(FormIdentifiers.UPGRADE_FAMILIAR_FOX);
		upgradeFamiliarFoxForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 进化使魔为四足形态，变身后重置玩家缩放到本形态大小（值与 origin power form_familiar_fox_sp_scale 一致）
		upgradeFamiliarFoxForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.5f, 0.6f));

		RegPlayerForms.registerPlayerForm(upgradeFamiliarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_upgrade_familiar_fox")).registerForm(1, 5, upgradeFamiliarFoxForm));

		// 契灵（Mancianima）—— 复用使魔模型/动画，经月髓环/进化石进化获得。
		// 之前是纯数据驱动(ssc_form json)，但原版新版 DynamicForm 缺 originLayerID 字段会 NPE 致其注册失败消失，
		// 故改为与其它 SP 形态一致的代码注册（不再依赖数据驱动），模型由 FormID 查 ssc_form_model 自动得到契灵外观。
		Form_FamiliarFox3 mancianimaForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
		mancianimaForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（值与 origin power form_familiar_fox_3_scale 一致）
		mancianimaForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.45f, 0.6f));

		RegPlayerForms.registerPlayerForm(mancianimaForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_familiar_fox_mancianima")).registerForm(1, 5, mancianimaForm));

		Form_FamiliarFoxRed familiarFoxRedForm = new Form_FamiliarFoxRed(FormIdentifiers.FAMILIAR_FOX_RED);
		familiarFoxRedForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（值与 origin power form_familiar_fox_red_scale 一致）
		familiarFoxRedForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.55f, 0.6f));

		RegPlayerForms.registerPlayerForm(familiarFoxRedForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_familiar_fox_red")).registerForm(1, 5, familiarFoxRedForm));

		Form_SnowFoxSP snowFoxForm = new Form_SnowFoxSP(FormIdentifiers.SNOW_FOX_SP);
		snowFoxForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（值与 origin power form_familiar_fox_3_scale 一致）
		snowFoxForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.45f, 0.6f));

		RegPlayerForms.registerPlayerForm(snowFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_snow_fox_sp")).registerForm(1, 7, snowFoxForm));

		Form_Allay allayForm = new Form_Allay(FormIdentifiers.ALLAY_SP);
		allayForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 悦灵缩放与原版 ALLAY_SP 代码注册一致(scale=0.35, eye_scale=1.0 保持正常视角高度)
		allayForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.35f, 1.0f));
		RegPlayerForms.registerPlayerForm(allayForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_allay_sp")).registerForm(1, 8, allayForm));

		Form_FeralCatSP wildCatForm = new Form_FeralCatSP(FormIdentifiers.WILD_CAT_SP);
		wildCatForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		wildCatForm.canSneakRush = true;
		// 四足形态变身后重置玩家缩放到本形态大小（值与原版野猫 form_feral_cat_sp_scale 一致）
		wildCatForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.55f, 0.6f));

		RegPlayerForms.registerPlayerForm(wildCatForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_wild_cat_sp")).registerForm(1, 5, wildCatForm));

		// Fallen Allay SP
		Form_FallenAllaySP fallenAllayForm = new Form_FallenAllaySP(FormIdentifiers.FALLEN_ALLAY_SP);
		fallenAllayForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 堕落悦灵复用悦灵模型，缩放与原版 ALLAY_SP 一致(scale=0.35, eye_scale=1.0)
		fallenAllayForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.35f, 1.0f));
		RegPlayerForms.registerPlayerForm(fallenAllayForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_fallen_allay_sp")).registerForm(1, 8, fallenAllayForm));

		// Anubis Wolf SP
		Form_AnubisWolfSP anubisWolfForm = new Form_AnubisWolfSP(FormIdentifiers.ANUBIS_WOLF_SP);
		anubisWolfForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		anubisWolfForm.canSneakRush = true;
		// 四足形态变身后重置玩家缩放到本形态大小（值与 origin power form_anubis_wolf_3_scale 一致）
		anubisWolfForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.8f, 0.6f));

		RegPlayerForms.registerPlayerForm(anubisWolfForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_anubis_wolf_sp")).registerForm(1, 12, anubisWolfForm));

		// Golden Sandstorm SP (金沙岚)
		Form_GoldenSandstormSP goldenSandstormForm = new Form_GoldenSandstormSP(FormIdentifiers.GOLDEN_SANDSTORM_SP);
		goldenSandstormForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		goldenSandstormForm.canSneakRush = true;
		// 金沙岚复用阿努比斯之狼四足模型，缩放与原版 ANUBIS_WOLF_3 一致(scale=0.8, eye_scale=0.6)
		goldenSandstormForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.8f, 0.6f));
		RegPlayerForms.registerPlayerForm(goldenSandstormForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_golden_sandstorm_sp")).registerForm(1, 12, goldenSandstormForm));

		// 吸血蝙蝠（Desmodus）SP形态 - 复用蝙蝠模型/动画，经月髓环在诅咒之月夜进化获得
		Form_BatDesmodus batDesmodusForm = new Form_BatDesmodus(FormIdentifiers.BAT_DESMODUS);
		batDesmodusForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune, HasSlowFall);
		// 蝙蝠缩放需与原版 bat_3 一致（宽度/高度0.5、眼睛/碰撞箱0.6），否则保持上个形态大小不缩放
		batDesmodusForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.5f, 0.6f));
		RegPlayerForms.registerPlayerForm(batDesmodusForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_bat_desmodus")).registerForm(1, 12, batDesmodusForm));

		// 寄生果蝠 - 原版三阶段蝙蝠使用进化石进化获得，复用蝙蝠模型/动画
		Form_BatParasiticFruit batParasiticFruitForm = new Form_BatParasiticFruit(FormIdentifiers.BAT_PARASITIC_FRUIT);
		batParasiticFruitForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune, HasSlowFall);
		// 蝙蝠缩放需与原版 bat_3 一致（宽度/高度0.5、眼睛/碰撞箱0.6），否则保持上个形态大小不缩放
		batParasiticFruitForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.5f, 0.6f));
		RegPlayerForms.registerPlayerForm(batParasiticFruitForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_bat_parasitic_fruit")).registerForm(1, 12, batParasiticFruitForm));
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SscAddonCommands.register(dispatcher));
	}

	private void registerTickHandlers() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world -> {
			// 在服务器线程上处理断线玩家的领域方块还原
			if (world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) {
				AnubisWolfSpDeathDomain.tickCleanup();
				// 契灵：每秒清理过期的恐惧减速 modifier
				if (world.getServer().getTicks() % 20 == 0) {
					net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive
							.serverGlobalFleeCleanup(world.getServer());
					// 契灵劫掠军组生命周期推进（LINGER → MARCH → 脱适清理）
					net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive
							.tickRaiderGroups(world.getServer());
				}
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
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.tick(player);
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.tick(player);
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager.tick(player);
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.PlayDeadAbsorptionManager.tick(player);
				net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager.tickPlayer(player);
			}
		});
	}

	/**
	 * 兜底：清除残留的「定身(STUN)」攻击力/移速孤儿属性修正。
	 * STUN 用固定 UUID 的属性修正实现「攻击力 -100% / 移速 -100%」。由于
	 * GENERIC_ATTACK_DAMAGE 不是被同步追踪的属性、且换形态时不会被重建，一旦 STUN 经
	 * 非正常路径（如换形态清状态效果）被移除而未触发 onStatusEffectRemoved，这个 -100%
	 * 修正会以孤儿形式残留在玩家身上，导致「任意武器0伤、无图标、跨形态保留、过会才自愈」的bug。
	 * 此处每服务端 tick 对在线玩家做校正：没有 STUN 效果却仍带 STUN 的固定 UUID 修正 → 立即移除。
	 * （同时清理已存在于老存档的孤儿残留。）
	 */
	private void registerStunOrphanCleanup() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				// [DEBUG] 水矛出现监测 + 硬上限：背包最多 1 把水矛，多余立即移除（兜底任何未知产出路径）
				if (net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isAxolotlSP(player)) {
					net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
					int wsCnt = 0;
					for (int i = 0; i < inv.size(); i++) {
						if (inv.getStack(i).isOf(WATER_SPEAR)) {
							wsCnt++;
							if (wsCnt > 1) {
								inv.setStack(i, net.minecraft.item.ItemStack.EMPTY);
								WS_DBG.warn("[WS-CAP] 移除多余水矛 slot={} @tick {}", i, server.getTicks());
								wsCnt--;
							}
						}
					}
					Integer wsPrev = WS_LAST_SPEAR_COUNT.put(player.getUuid(), wsCnt);
					if (wsPrev != null && wsCnt > wsPrev) {
						long wsT = server.getTicks();
						Long wsUntil = WATER_SPEAR_CRAFT_CD.get(player.getUuid());
						WS_DBG.warn("[WS-MONITOR] 水矛数 {}->{} @tick {} ; internalCD until={} cooling={} ; arrowCD={}",
								wsPrev, wsCnt, wsT, wsUntil, (wsUntil != null && wsT < wsUntil),
								player.getItemCooldownManager().isCoolingDown(net.minecraft.item.Items.ARROW));
					}
					// 水矛从「有」变「无」(扛出/消耗) → 重启 Apoli 合成冷却，使「合成CD」从水矛消失那刻起算
					// 否则持矛期间 active_self 的 cooldown 会走完，扛出后可立即秒合成（用户反馈的 bug）
					if (wsPrev != null && wsPrev > 0 && wsCnt == 0) {
						long wsT = server.getTicks();
						WATER_SPEAR_CRAFT_CD.put(player.getUuid(), wsT + WATER_SPEAR_CRAFT_CD_TICKS);
						player.getItemCooldownManager().set(net.minecraft.item.Items.ARROW, WATER_SPEAR_CRAFT_CD_TICKS);
						net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils.resetCooldown(player,
								new net.minecraft.util.Identifier("my_addon", "form_axolotl_sp_water_spear_craft_spear"));
						WS_DBG.warn("[WS-CD] 水矛消失 @tick {} → 重启合成冷却(从消失起算 {}t)", wsT, WATER_SPEAR_CRAFT_CD_TICKS);
					}
				}
				if (player.hasStatusEffect(STUN)) continue;
				net.minecraft.entity.attribute.EntityAttributeInstance atk =
						player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
				if (atk != null && atk.getModifier(StunEffect.ATTACK_MODIFIER_UUID) != null) {
					atk.removeModifier(StunEffect.ATTACK_MODIFIER_UUID);
				}
				net.minecraft.entity.attribute.EntityAttributeInstance spd =
						player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
				if (spd != null && spd.getModifier(StunEffect.SPEED_MODIFIER_UUID) != null) {
					spd.removeModifier(StunEffect.SPEED_MODIFIER_UUID);
				}
			}
		});
	}

	/**
	 * 修复多人下客机看主机时四足(FERAL)形态头部偶尔「转过身后」的视觉异常。
	 * 根因：vanilla 服务端 ServerPlayerEntity.bodyYaw 只在玩家「移动」时才被 tickHeadTurn 拉向 headYaw。
	 * 玩家站着只转鼠标时，移动包只上报 pos+yaw(=headYaw)+pitch，不带 bodyYaw，服务端 bodyYaw 保持陈旧值；
	 * 服务端再把「新 headYaw + 陈旧 bodyYaw」一起发给远端客机，远端 OtherClientPlayerEntity 直接采信，
	 * head−body 夹角于是很大。人形头骨绕颈部偏转视觉不明显，但四足形态头骨水平前伸，看上去就是「头扭过身后」。
	 * 主机走一步路 → 服务端 bodyYaw 被 tickHeadTurn 拉正 → 自愈。生物 bodyYaw 由服务端持续维护所以不受影响。
	 * 这里每服务端 tick 给已激活 Mod 的 FERAL 形态玩家补一个 tickHeadTurn 等效收敛：把 bodyYaw 限速拉向 headYaw，
	 * 并夹住头身夹角 ≤ 75°（与 vanilla LivingEntity.tickHeadTurn 一致），使服务端发出的 bodyYaw 不再陈旧。
	 * 仅作用于玩家自身的 bodyYaw（服务端权威字段），主客机都靠它，零客机预测冲突。
	 */
	private void registerFeralBodyYawSync() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				net.onixary.shapeShifterCurseFabric.player_form.IForm form =
						net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.getPlayerForm(player);
				if (form == null
						|| form.getBodyType() != net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType.FERAL) {
					continue;
				}
				// 把 bodyYaw 朝 headYaw 收敛（vanilla tickHeadTurn 同款：限速 + 夹角钳制）。
				float headYaw = player.getHeadYaw();
				float bodyYaw = player.bodyYaw;
				float diff = net.minecraft.util.math.MathHelper.wrapDegrees(headYaw - bodyYaw);
				// 头身夹角钳制到 ±75°（超出部分立即并入身体朝向，避免极端扭头）
				float clampedDiff = net.minecraft.util.math.MathHelper.clamp(diff, -75.0f, 75.0f);
				float overflow = diff - clampedDiff;
				// 收敛速度：每 tick 最多转 10°，模拟身体平滑跟随视角
				float step = net.minecraft.util.math.MathHelper.clamp(clampedDiff, -10.0f, 10.0f);
				float newBodyYaw = bodyYaw + step + overflow;
				if (newBodyYaw != bodyYaw) {
					player.bodyYaw = newBodyYaw;
					player.prevBodyYaw = newBodyYaw;
				}
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
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.clearAll();
			UndeadNeutralState.clearAll();
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.clearAll();
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
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.BatDesmodusBloodThirst.clearAll();
			UndeadNeutralState.clearAll();
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.clearAll();
			net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions.clearAll();
			System.out.println("[SSC_ADDON] END_DATA_PACK_RELOAD ability state cleared");
		});
	}

	/**
	 * 契灵相关 Fabric 事件注册：
	 * - 死亡事件：村民/商人击杀掉落 + 袭击目标完成检测
	 * - 实体交互：唤魔者 + 下界之星 → 2 个不死图腾
	 */
	private void registerMancianimaEvents() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// 1. 袭击目标死亡检测
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.onAssaultTargetDeath(entity);
			// 2. 村民/商人 被契灵击杀 → 掉落
			if (entity instanceof net.minecraft.entity.passive.MerchantEntity merchant
					&& source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity killer
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(killer,
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive
						.onMerchantKilledByMancianima(merchant, killer);
			}
		});

		// 唤魔者 + 下界之星 → 2 个不死图腾
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient()) return net.minecraft.util.ActionResult.PASS;
			if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
			if (!net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(sp,
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				return net.minecraft.util.ActionResult.PASS;
			}
			// 契灵不能与村民/商人交易
			if (entity instanceof net.minecraft.entity.passive.MerchantEntity) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			if (!(entity instanceof net.minecraft.entity.mob.EvokerEntity)) return net.minecraft.util.ActionResult.PASS;
			net.minecraft.item.ItemStack stack = sp.getStackInHand(hand);
			if (!stack.isOf(net.minecraft.item.Items.NETHER_STAR)) return net.minecraft.util.ActionResult.PASS;
			if (!sp.getAbilities().creativeMode) stack.decrement(1);
			net.minecraft.item.ItemStack reward = new net.minecraft.item.ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING, 2);
			if (!sp.getInventory().insertStack(reward)) {
				sp.dropItem(reward, false);
			}
			if (sp.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
				sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						net.minecraft.sound.SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON,
						sp.getSoundCategory(), 1.0f, 1.2f);
			}
			return net.minecraft.util.ActionResult.SUCCESS;
		});

		// 契灵敲钟触发村庄袭击（1 MC 天 1 次）
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient()) return net.minecraft.util.ActionResult.PASS;
			if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
			if (!net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(sp,
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				return net.minecraft.util.ActionResult.PASS;
			}
			net.minecraft.block.BlockState state = world.getBlockState(hitResult.getBlockPos());
			if (!(state.getBlock() instanceof net.minecraft.block.BellBlock)) return net.minecraft.util.ActionResult.PASS;
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.tryTriggerAssaultByBell(sp);
			// 让钟声照常播放
			return net.minecraft.util.ActionResult.PASS;
		});

		// 吸血蝙蝠血雾期间禁用一切右键交互（用物品/放方块/与生物互动/吃喝/盾牌副手等）
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {			if (player.hasStatusEffect(MIST_FORM)
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(player,
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.BAT_DESMODUS)) {
				return net.minecraft.util.TypedActionResult.fail(player.getStackInHand(hand));
			}			return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
		});

		WS_DBG.info("[WS] ===== DEBUG BUILD LOADED (v2): 水矛合成+最多1把 监测启用 =====");
		// SP美西螈：选中快捷栏(主手)为空 + 副手持箭 + 右键 → 消耗 1 支箭“合成”获得水矛（5 秒CD；身上最多 1 把）
		// 注：主手为空时 MC 只触发副手(OFF_HAND)交互，故用副手回调
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			boolean axo = net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isAxolotlSP(player);
			net.minecraft.item.ItemStack mainStack = player.getMainHandStack();
			net.minecraft.item.ItemStack offStack = player.getOffHandStack();
			boolean arrowCd = player.getItemCooldownManager().isCoolingDown(net.minecraft.item.Items.ARROW);
			int spearCount = 0;
			if (axo) {
				net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
				for (int i = 0; i < inv.size(); i++) {
					if (inv.getStack(i).isOf(WATER_SPEAR)) spearCount++;
				}
				WS_DBG.info("[WS] side={} hand={} main={} mainEmpty={} off={} offIsArrow={} arrowCD={} spearInInv={}",
						world.isClient() ? "CLIENT" : "SERVER", hand,
						net.minecraft.registry.Registries.ITEM.getId(mainStack.getItem()), mainStack.isEmpty(),
						net.minecraft.registry.Registries.ITEM.getId(offStack.getItem()), offStack.isOf(net.minecraft.item.Items.ARROW), arrowCd, spearCount);
			}
			if (hand != net.minecraft.util.Hand.OFF_HAND || !axo || !mainStack.isEmpty()
					|| !offStack.isOf(net.minecraft.item.Items.ARROW)) {
				return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
			}
			// 身上最多一把水矛：背包已有则不合成
			if (spearCount > 0) {
				WS_DBG.info("[WS][{}] BLOCKED: already has {} water_spear (max 1)", world.isClient() ? "CLIENT" : "SERVER", spearCount);
				return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
			}
			if (world.isClient()) {
				WS_DBG.info("[WS][CLIENT] gate-passed arrowCD={} -> {}", arrowCd, arrowCd ? "PASS(cooling)" : "SUCCESS");
				return arrowCd ? net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand))
						: net.minecraft.util.TypedActionResult.success(player.getStackInHand(hand));
			}
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
				int slot = sp.getInventory().selectedSlot;
				net.minecraft.item.ItemStack selStack = sp.getInventory().getStack(slot);
				int srvSpears = 0;
				for (int i = 0; i < sp.getInventory().size(); i++) {
					if (sp.getInventory().getStack(i).isOf(WATER_SPEAR)) srvSpears++;
				}
				long now = sp.getServer().getTicks();
				Long until = WATER_SPEAR_CRAFT_CD.get(sp.getUuid());
				boolean cooling = until != null && now < until;
				WS_DBG.info("[WS][SERVER] gate cooling={} now={} until={} selSlot={} selStack={} selEmpty={} srvSpears={}",
						cooling, now, until, slot,
						net.minecraft.registry.Registries.ITEM.getId(selStack.getItem()), selStack.isEmpty(), srvSpears);
				if (cooling) {
					return net.minecraft.util.TypedActionResult.pass(sp.getStackInHand(hand));
				}
				// 服务端二次硬校验（防御）：选中槽必须真空、且身上无水矛
				if (!selStack.isEmpty()) {
					WS_DBG.warn("[WS][SERVER] ABORT: 选中槽非空({})，不合成", net.minecraft.registry.Registries.ITEM.getId(selStack.getItem()));
					return net.minecraft.util.TypedActionResult.pass(sp.getStackInHand(hand));
				}
				if (srvSpears > 0) {
					WS_DBG.warn("[WS][SERVER] ABORT: 身上已有 {} 把水矛", srvSpears);
					return net.minecraft.util.TypedActionResult.pass(sp.getStackInHand(hand));
				}
				sp.getOffHandStack().decrement(1);
				net.minecraft.item.ItemStack spear = new net.minecraft.item.ItemStack(WATER_SPEAR);
				sp.getInventory().setStack(slot, spear);
				sp.getInventory().markDirty();
				WS_DBG.info("[WS][SERVER] >>> CRAFTED into selSlot={} ; offhandEmptyNow={} (CD改为水矛消失后触发)", slot, sp.getOffHandStack().isEmpty());
				if (sp.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
					sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
							net.minecraft.sound.SoundEvents.ITEM_BOTTLE_FILL, sp.getSoundCategory(), 0.8f, 1.0f);
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnWaterBurst(sw, sp.getX(), sp.getY() + 1.0, sp.getZ(), 0.5);
				}
				return net.minecraft.util.TypedActionResult.success(sp.getStackInHand(hand));
			}
			return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.hasStatusEffect(MIST_FORM)
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(player,
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.BAT_DESMODUS)) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasStatusEffect(MIST_FORM)
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(player,
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.BAT_DESMODUS)) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		// 同时禁用左键破坏方块
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.hasStatusEffect(MIST_FORM)
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(player,
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.BAT_DESMODUS)) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
		});
		// 禁用左键攻击实体（含挥剑/普攻起手动作本身）
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasStatusEffect(MIST_FORM)
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(player,
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.BAT_DESMODUS)) {
				return net.minecraft.util.ActionResult.FAIL;
			}
			return net.minecraft.util.ActionResult.PASS;
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
		// 兜底：玩家加入服务器时清理孤儿 mana 数据，修复老存档残留导致能量条不消失的 bug
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.StaleManaCleaner.register();

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
			// 重连/换维度回归后：强制把契灵标记 + 金沙岚侵蚀印记状态重新同步给客户端，
			// 避免重连后客户端 HUD/渲染缓存为空，直到下一次状态变更才被动恢复。
			server.execute(() -> {
				try { net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager.resyncToPlayer(player); } catch (Throwable ignored) {}
				try { net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand.resyncToPlayer(player); } catch (Throwable ignored) {}
			});
			String welcomeTag = "ssc_addon_welcomed";
			if (!player.getCommandTags().contains(welcomeTag)) {
				player.addCommandTag(welcomeTag);
				final java.util.UUID playerUuid = player.getUuid();
				java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
						.execute(() -> {
							// 3 秒延迟到达时服务端可能已关闭/重启 —— 防御性检查，避免 IllegalStateException
							if (!server.isRunning()) return;
							server.execute(() -> {
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
							});
						});
			}
		});

		// #13 修复：后加入的客机看「先在场玩家(含主机)」是 vanilla 玩家模型而非形态模型。
		// 根因：主包 PlayerFormComponent 是 Cardinal Components 的「玩家组件」(registerForPlayers)，
		// CCA 只在玩家「自己登录」时做一次初始同步，不会在其它玩家开始追踪该玩家时自动补发，
		// 导致新观察者追踪到先在场玩家时拿不到其形态数据，于是渲染成原版模型。
		// 方案：监听实体「开始追踪」事件——任一玩家开始追踪另一名玩家时，对被追踪玩家重发其形态组件，
		// 让新观察者(以及跨维度/远距离重新进入视野的玩家)及时拿到正确形态。
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
			if (trackedEntity instanceof net.minecraft.server.network.ServerPlayerEntity tracked) {
				try {
					net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent.PLAYER_FORM.sync(tracked);
				} catch (Throwable ignored) {
					// 极端时序下组件容器可能尚未就绪，忽略即可，下次状态变更会自动同步
				}
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
			// 灵魂能量：Apoli resource 本身会随玩家NBT持久化，不再在断线时清零
			ErosionSandPrismItem.clearPlayer(uuid);
			WitheredSandRingItem.clearPlayer(uuid);
			AllaySPJukebox.onPlayerDisconnect(handler.player);
			UndeadNeutralState.clearPlayer(uuid);
			net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions.clearPlayer(uuid);
			PLAYER_LANGUAGES.remove(uuid);
			// 契灵：清理袭击 bossBar + raid 状态，防止 bossBar 残留与 Map 泄漏
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive.onPlayerDisconnect(uuid);
			// 白名单 GUI 限频表：移除退出玩家的时间戳，防止僵尸 UUID 积累
			net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking.onPlayerDisconnect(uuid);
			System.out.println("[SSC_ADDON] DISCONNECT cleanup completed");
		});
	}






}
