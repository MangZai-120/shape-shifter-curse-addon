package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.FoxFireBurnEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.BlueFireRingEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.PlayingDeadEffect;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.onixary.shapeShifterCurseFabric.ssc_addon.command.SscAddonCommands;
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.onixary.shapeShifterCurseFabric.ssc_addon.condition.SscAddonConditions;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WaterSpearItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableMoisturizerItem;
import net.minecraft.item.ToolMaterials;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_Axolotl3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_FamiliarFox3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.forms.Form_Allay;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.onixary.shapeShifterCurseFabric.ssc_addon.recipe.RefillMoisturizerRecipe;

import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormGroup;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

public class SscAddon implements ModInitializer {

    public static final StatusEffect FOX_FIRE_BURN = new FoxFireBurnEffect();
    public static final StatusEffect BLUE_FIRE_RING = new BlueFireRingEffect();
    public static final StatusEffect PLAYING_DEAD = new PlayingDeadEffect();
    
    public static final Item SP_UPGRADE_THING = new SpUpgradeItem(new Item.Settings().maxCount(1));
    public static final Item PORTABLE_MOISTURIZER = new PortableMoisturizerItem(new Item.Settings().maxCount(1));
    public static final RecipeSerializer<RefillMoisturizerRecipe> REFILL_MOISTURIZER_SERIALIZER = new SpecialRecipeSerializer<>(RefillMoisturizerRecipe::new);
    // 60 durability like wooden sword, auto-consumed over 60 seconds
    public static final Item WATER_SPEAR = new WaterSpearItem(new Item.Settings().maxCount(1).maxDamage(60));

    @Override
    public void onInitialize() {
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "fox_fire_burn"), FOX_FIRE_BURN);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "playing_dead"), PLAYING_DEAD);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "blue_fire_ring"), BLUE_FIRE_RING);
        
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "sp_upgrade_thing"), SP_UPGRADE_THING);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "portable_moisturizer"), PORTABLE_MOISTURIZER);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "water_spear"), WATER_SPEAR);
        
        Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("ssc_addon", "refill_moisturizer"), REFILL_MOISTURIZER_SERIALIZER);
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(content -> {
            content.add(SP_UPGRADE_THING);
        });
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(content -> {
            content.add(WATER_SPEAR);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(PORTABLE_MOISTURIZER);
        });
        
        SscAddonActions.register();
        SscAddonConditions.register();

        SscAddonNetworking.registerServerReceivers();

        SpAllayMana.register();
        
        // Register SP Forms with custom animation controllers
        Form_Axolotl3 axolotlForm = new Form_Axolotl3(new Identifier("my_addon", "axolotl_sp"));
        axolotlForm.setPhase(PlayerFormPhase.PHASE_SP);
        RegPlayerForms.registerPlayerForm(axolotlForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_axolotl_sp")).addForm(axolotlForm, 5));

        Form_FamiliarFox3 familiarFoxForm = new Form_FamiliarFox3(new Identifier("my_addon", "familiar_fox_sp"));
        familiarFoxForm.setPhase(PlayerFormPhase.PHASE_SP);
        RegPlayerForms.registerPlayerForm(familiarFoxForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_sp")).addForm(familiarFoxForm, 5));

        Form_Allay allayForm = new Form_Allay(new Identifier("my_addon", "form_allay_sp"));
        allayForm.setPhase(PlayerFormPhase.PHASE_SP);
        RegPlayerForms.registerPlayerForm(allayForm);
        RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_form_allay_sp")).addForm(allayForm, 5));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SscAddonCommands.register(dispatcher);
        });

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
            return net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand));
        });
    }
}
