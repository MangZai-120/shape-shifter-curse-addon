package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.FoxFireBurnEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.BlueFireRingEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.PlayingDeadEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.FrostFreezeEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.FrostFallEffect;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.onixary.shapeShifterCurseFabric.ssc_addon.command.SscAddonCommands;
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.condition.SscAddonConditions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.SscAddonPowers;
import net.minecraft.item.Item;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WaterSpearItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableMoisturizerItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.BlueFireAmuletItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_Axolotl3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_FamiliarFox3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_SnowFoxSP;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP;
//import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_Allay;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.RefillMoisturizerRecipe;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.SpUpgradeRecipe;

import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormGroup;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityDimensions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WaterSpearEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.InvisibilityCloakItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;

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
    public static final Item BLUE_FIRE_AMULET = new BlueFireAmuletItem(new Item.Settings().maxCount(1).fireproof());
    public static final Item INVISIBILITY_CLOAK = new InvisibilityCloakItem(new Item.Settings().maxCount(1).fireproof());
    public static final RecipeSerializer<RefillMoisturizerRecipe> REFILL_MOISTURIZER_SERIALIZER = new SpecialRecipeSerializer<>(RefillMoisturizerRecipe::new);
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

    @Override
    public void onInitialize() {
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "fox_fire_burn"), FOX_FIRE_BURN);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "playing_dead"), PLAYING_DEAD);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "blue_fire_ring"), BLUE_FIRE_RING);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "true_invisibility"), TRUE_INVISIBILITY);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "pre_invisibility"), PRE_INVISIBILITY);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "stun"), STUN);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "guaranteed_crit"), GUARANTEED_CRIT);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "frost_freeze"), FROST_FREEZE);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "frost_fall"), FROST_FALL);
        
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "sp_upgrade_thing"), SP_UPGRADE_THING);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "portable_moisturizer"), PORTABLE_MOISTURIZER);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "blue_fire_amulet"), BLUE_FIRE_AMULET);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "invisibility_cloak"), INVISIBILITY_CLOAK);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "water_spear"), WATER_SPEAR);

        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "evolution_stone"), EVOLUTION_STONE);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "shadow_shard"), SHADOW_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "night_vision_shard"), NIGHT_VISION_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "ender_shard"), ENDER_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "hunt_shard"), HUNT_SHARD);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "sculk_shard"), SCULK_SHARD);
        
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "refill_moisturizer"), REFILL_MOISTURIZER_SERIALIZER);
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "sp_upgrade_crafting"), SP_UPGRADE_SERIALIZER);
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(content -> content.add(SP_UPGRADE_THING));
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(content -> content.add(WATER_SPEAR));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(PORTABLE_MOISTURIZER);
            content.add(BLUE_FIRE_AMULET);
            content.add(INVISIBILITY_CLOAK);
            content.add(EVOLUTION_STONE);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(content -> {
            content.add(SHADOW_SHARD);
            content.add(NIGHT_VISION_SHARD);
            content.add(ENDER_SHARD);
            content.add(HUNT_SHARD);
            content.add(SCULK_SHARD);
        });
        
        SscAddonActions.register();
        SscAddonConditions.register();
        SscAddonPowers.register();

        SscAddonNetworking.registerServerReceivers();

        //SpAllayMana.register();
        
        // Register SP Forms with custom animation controllers
        Form_Axolotl3 axolotlForm = new Form_Axolotl3(new Identifier("my_addon", "axolotl_sp"));
        axolotlForm.setPhase(PlayerFormPhase.PHASE_SP);
        RegPlayerForms.registerPlayerForm(axolotlForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_axolotl_sp")).addForm(axolotlForm, 5));

        Form_FamiliarFox3 familiarFoxForm = new Form_FamiliarFox3(new Identifier("my_addon", "familiar_fox_sp"));
        familiarFoxForm.setPhase(PlayerFormPhase.PHASE_SP);
        RegPlayerForms.registerPlayerForm(familiarFoxForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_sp")).addForm(familiarFoxForm, 5));

        Form_SnowFoxSP snowFoxForm = new Form_SnowFoxSP(new Identifier("my_addon", "snow_fox_sp"));
        snowFoxForm.setPhase(PlayerFormPhase.PHASE_SP);
        RegPlayerForms.registerPlayerForm(snowFoxForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_snow_fox_sp")).addForm(snowFoxForm, 7));

        //Form_Allay allayForm = new Form_Allay(new Identifier("my_addon", "form_allay_sp"));
        //allayForm.setPhase(PlayerFormPhase.PHASE_SP);
        //RegPlayerForms.registerPlayerForm(allayForm);
        //RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_form_allay_sp")).addForm(allayForm, 5));

        Form_FeralCatSP wildCatForm = new Form_FeralCatSP(new Identifier("my_addon", "wild_cat_sp"));
        wildCatForm.setPhase(PlayerFormPhase.PHASE_SP);
        wildCatForm.setBodyType(net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType.FERAL);
        wildCatForm.setCanSneakRush(true);
        RegPlayerForms.registerPlayerForm(wildCatForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_wild_cat_sp")).addForm(wildCatForm, 5));


        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SscAddonCommands.register(dispatcher));

        // Register tick event for SP Snow Fox abilities
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world -> {
            for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility.tick(player);
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack.tick(player);
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm.tick(player);
            }
        });

        /*
        // Tick Event for SP Allay Ability
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_WORLD_TICK.register(world -> {
            for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                net.onixary.shapeShifterCurseFabric.ssc_addon.ability.Ability_AllayHeal.tick(player);
            }
        });

        // Amethyst Consumption for Mana
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
                if (stack.isOf(net.minecraft.item.Items.AMETHYST_SHARD)) {
                    PlayerFormBase currentForm = net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager.getForm(serverPlayer);
                    if (currentForm != null && currentForm.FormID.equals(new Identifier("my_addon", "form_allay_sp"))) {
                        net.onixary.shapeShifterCurseFabric.mana.ManaComponent component = net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getManaComponent(serverPlayer);
                        if (component.getManaTypeID() != null && component.getManaTypeID().equals(SpAllayMana.INSTANCE)) {
                            if (!player.isCreative()) {
                                stack.decrement(1);
                            }
                            component.gainMana(50.0);
                            return net.minecraft.util.TypedActionResult.success(stack);
                        }
                    }
                    }
                }
            }
            return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
        });
        */
    }
}
