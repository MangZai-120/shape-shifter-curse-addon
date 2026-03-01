package net.onixary.shapeShifterCurseFabric.ssc_addon;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
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
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormGroup;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_FallenAllaySP;
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.command.SscAddonCommands;
import net.onixary.shapeShifterCurseFabric.ssc_addon.condition.SscAddonConditions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.config.SSCAddonConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.*;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.AllayClearMarkerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.AllayFriendMarkerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;
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
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPJukebox;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FallenAllayVexTracker;

public class SscAddon implements ModInitializer {

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

    public static final ScreenHandlerType<PotionBagScreenHandler> POTION_BAG_SCREEN_HANDLER = new ScreenHandlerType<>(PotionBagScreenHandler::new, FeatureSet.empty());
    public static final Item POTION_BAG = new PotionBagItem(new Item.Settings().maxCount(1));
    
    public static final EntityType<WaterSpearEntity> WATER_SPEAR_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("ssc_addon", "water_spear"),
            FabricEntityTypeBuilder.<WaterSpearEntity>create(SpawnGroup.MISC, WaterSpearEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(4).trackedUpdateRate(20)
                    .build()
    );

    public static final EntityType<FrostBallEntity> FROST_BALL_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("ssc_addon", "frost_ball"),
            FabricEntityTypeBuilder.<FrostBallEntity>create(SpawnGroup.MISC, FrostBallEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64).trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<FrostStormEntity> FROST_STORM_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier("ssc_addon", "frost_storm"),
            FabricEntityTypeBuilder.<FrostStormEntity>create(SpawnGroup.MISC, FrostStormEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 2.0f))
                    .trackRangeBlocks(64).trackedUpdateRate(10)
                    .build()
    );

    public static final Item SP_UPGRADE_THING = new SpUpgradeItem(new Item.Settings().maxCount(1));
    public static final Item PORTABLE_MOISTURIZER = new PortableMoisturizerItem(new Item.Settings().maxCount(1));
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

    // Evolution Stone and Shards
    public static final Item EVOLUTION_STONE = new EvolutionStoneItem(new Item.Settings().maxCount(1).fireproof());
    public static final Item SHADOW_SHARD = new Item(new Item.Settings().maxCount(1));
    public static final Item NIGHT_VISION_SHARD = new Item(new Item.Settings().maxCount(1));
    public static final Item ENDER_SHARD = new Item(new Item.Settings().maxCount(1));
    public static final Item HUNT_SHARD = new Item(new Item.Settings().maxCount(1));
    public static final Item SCULK_SHARD = new Item(new Item.Settings().maxCount(1));
    public static final Item CORAL_BALL = new Item(new Item.Settings().maxCount(64));
    public static final Item ACTIVE_CORAL_NECKLACE = new ActiveCoralNecklaceItem(new Item.Settings().maxCount(1));

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

    // SP Allay sound events
    public static final Identifier ALLAY_HEAL_MUSIC_ID = new Identifier("ssc_addon", "allay_heal_music");
    public static final Identifier ALLAY_SPEED_MUSIC_ID = new Identifier("ssc_addon", "allay_speed_music");
    public static final SoundEvent ALLAY_HEAL_MUSIC_EVENT = SoundEvent.of(ALLAY_HEAL_MUSIC_ID);
    public static final SoundEvent ALLAY_SPEED_MUSIC_EVENT = SoundEvent.of(ALLAY_SPEED_MUSIC_ID);

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
                        entries.add(SHADOW_SHARD);
                        entries.add(NIGHT_VISION_SHARD);
                        entries.add(ENDER_SHARD);
                        entries.add(HUNT_SHARD);
                        entries.add(SCULK_SHARD);
                        entries.add(CORAL_BALL);
                        entries.add(ACTIVE_CORAL_NECKLACE);
                        entries.add(ALLAY_HEAL_WAND);
                        entries.add(ALLAY_JUKEBOX);
                        entries.add(FRIEND_MARKER);
                        entries.add(CLEAR_FRIEND_MARKER);
                    })
                    .build());

    @Override
    public void onInitialize() {
        /*
        // 旧代码(保留参考) 已拆分为私有方法
        AutoConfig.register(SSCAddonConfig.class, GsonConfigSerializer::new);
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
        registerEntities();
        registerRecipeSerializers();
        registerSoundEvents();
        registerApoliSystems();
        registerForms();
        registerCommands();
        registerTickHandlers();
        registerPlayerEventHandlers();
    }

    // 拆分的私有方法

    private void registerConfig() {
        AutoConfig.register(SSCAddonConfig.class, GsonConfigSerializer::new);
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
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "shadow_shard"), SHADOW_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "night_vision_shard"), NIGHT_VISION_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "ender_shard"), ENDER_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "hunt_shard"), HUNT_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "sculk_shard"), SCULK_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "coral_ball"), CORAL_BALL);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "active_coral_necklace"), ACTIVE_CORAL_NECKLACE);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "allay_heal_wand"), ALLAY_HEAL_WAND);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "allay_jukebox"), ALLAY_JUKEBOX);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "friend_marker"), FRIEND_MARKER);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "clear_friend_marker"), CLEAR_FRIEND_MARKER);
    }

    private void registerEntities() {
        // Registry.register(Registries.ENTITY_TYPE, new Identifier("ssc_addon", "water_spear"), WATER_SPEAR_ENTITY);
        // Registry.register(Registries.ENTITY_TYPE, new Identifier("ssc_addon", "frost_ball"), FROST_BALL_ENTITY);
        // Registry.register(Registries.ENTITY_TYPE, new Identifier("ssc_addon", "frost_storm"), FROST_STORM_ENTITY);
        // Registry.register(Registries.ENTITY_TYPE, new Identifier("ssc_addon", "friend_marker"), FRIEND_MARKER_ENTITY_TYPE);
        // Registry.register(Registries.ENTITY_TYPE, new Identifier("ssc_addon", "clear_friend_marker"), CLEAR_MARKER_ENTITY_TYPE);
    }

    private void registerRecipeSerializers() {
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "refill_moisturizer"), REFILL_MOISTURIZER_SERIALIZER);
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "reload_snowball_launcher"), RELOAD_SNOWBALL_LAUNCHER_SERIALIZER);
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "blizzard_tank_recharge"), BLIZZARD_TANK_RECHARGE_SERIALIZER);
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "sp_upgrade_crafting"), SP_UPGRADE_SERIALIZER);
    }

    private void registerSoundEvents() {
        Registry.register(Registries.SOUND_EVENT, ALLAY_HEAL_MUSIC_ID, ALLAY_HEAL_MUSIC_EVENT);
        Registry.register(Registries.SOUND_EVENT, ALLAY_SPEED_MUSIC_ID, ALLAY_SPEED_MUSIC_EVENT);
    }

    private void registerApoliSystems() {
        SscAddonActions.register();
        SscAddonConditions.register();
        SscAddonPowers.register();
        SscAddonNetworking.registerServerReceivers();
        net.onixary.shapeShifterCurseFabric.ssc_addon.loot.StoryBookLoot.init();
        net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPPortableBeacon.init();
        net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPTotem.init();
        LifesavingCatTailItem.registerLootTable();
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
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SscAddonCommands.register(dispatcher));
    }

    private void registerTickHandlers() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world -> {
            for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                SnowFoxSpMeleeAbility.tick(player);
                SnowFoxSpTeleportAttack.tick(player);
                SnowFoxSpFrostStorm.tick(player);
                AllaySPGroupHeal.tick(player);
                AllaySPJukebox.tick(player);
                FallenAllayVexTracker.tick(player);
            }
        });
    }

    private void registerPlayerEventHandlers() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                newPlayer.getItemCooldownManager().remove(LIFESAVING_CAT_TAIL);
                newPlayer.getItemCooldownManager().remove(PHANTOM_BELL);
            }
        });
    }
}
