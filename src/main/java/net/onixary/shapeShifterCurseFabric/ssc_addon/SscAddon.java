package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.FoxFireBurnEffect;
import net.onixary.shapeShifterCurseFabric.ssc_addon.effect.BlueFireRingEffect;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.onixary.shapeShifterCurseFabric.ssc_addon.command.SscAddonCommands;
import net.onixary.shapeShifterCurseFabric.ssc_addon.action.SscAddonActions;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class SscAddon implements ModInitializer {

    public static final StatusEffect FOX_FIRE_BURN = new FoxFireBurnEffect();
    public static final StatusEffect BLUE_FIRE_RING = new BlueFireRingEffect();
    
    public static final Item SP_UPGRADE_THING = new SpUpgradeItem(new Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "fox_fire_burn"), FOX_FIRE_BURN);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "blue_fire_ring"), BLUE_FIRE_RING);
        
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "sp_upgrade_thing"), SP_UPGRADE_THING);
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(content -> {
            content.add(SP_UPGRADE_THING);
        });
        
        SscAddonActions.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SscAddonCommands.register(dispatcher);
        });
    }
}
