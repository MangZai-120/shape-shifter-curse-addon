package net.jackcooper.shapeShifterCurseAddon.compat;

import dev.emi.trinkets.api.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;

/** Registered through Trinkets' public API, replacing the single-backend item callback adapter. */
final class TrinketsEquipmentBridge implements Trinket {
    private final AccessoryItem item;
    private TrinketsEquipmentBridge(AccessoryItem item) {this.item=item;}
    static void register() {
        for(var item:Registries.ITEM)if(DualAccessoryEquipment.manages(new ItemStack(item)))
            TrinketsApi.registerTrinket(item,new TrinketsEquipmentBridge((AccessoryItem)item));
    }
    private static AccessoryItem.SlotData data(SlotReference reference) {
        var type=reference.inventory().getSlotType();return new AccessoryItem.SlotData(new Identifier("trinket",type.getGroup()+"/"+type.getName()),reference.index());
    }
    @Override public void tick(ItemStack stack,SlotReference slot,LivingEntity entity) {DualAccessoryEquipment.tick(item,stack,entity,data(slot),"trinkets");}
    @Override public void onEquip(ItemStack stack,SlotReference slot,LivingEntity entity) {changed(slot,entity);}
    @Override public void onUnequip(ItemStack stack,SlotReference slot,LivingEntity entity) {changed(slot,entity);}
    private static void changed(SlotReference slot,LivingEntity entity) {
        DualAccessoryEquipment.changed(entity,"trinkets",data(slot),slot.inventory().getStack(slot.index()));
    }
    @Override public boolean canEquip(ItemStack stack,SlotReference slot,LivingEntity entity) {return item.canEquip(stack,entity,data(slot));}
    @Override public boolean canUnequip(ItemStack stack,SlotReference slot,LivingEntity entity) {return item.canUnequip(stack,entity,data(slot));}
    @Override public void onBreak(ItemStack stack,SlotReference slot,LivingEntity entity) {item.onBreak(stack,entity,data(slot));}
    @Override public TrinketEnums.DropRule getDropRule(ItemStack stack,SlotReference slot,LivingEntity entity) {return TrinketEnums.DropRule.valueOf(item.getDropRule(stack,entity,data(slot)).name());}
}
