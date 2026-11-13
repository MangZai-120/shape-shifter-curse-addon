package net.jackcooper.shapeShifterCurseAddon.mixin.item;

import net.jackcooper.shapeShifterCurseAddon.compat.DualAccessoryEquipment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(value=AccessoryUtils.class,remap=false)
public class DualAccessoryQueriesMixin {
    @Inject(method="getEntitySlots",at=@At("HEAD"),cancellable=true)
    private static void ssca$all(LivingEntity entity,String backend,CallbackInfoReturnable<Map<Pair<String,String>,List<ItemStack>>> cir) {
        if(backend==null||backend.equals("auto"))cir.setReturnValue(DualAccessoryEquipment.merged(entity));
    }
    @Inject(method="getEntitySlot(Lnet/minecraft/entity/LivingEntity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Lnet/minecraft/item/ItemStack;",at=@At("HEAD"),cancellable=true,remap=true)
    private static void ssca$one(LivingEntity entity,String backend,String group,String name,int index,CallbackInfoReturnable<ItemStack> cir) {
        if(backend==null||backend.equals("auto")) {var slot=DualAccessoryEquipment.find(entity,group,name,index);cir.setReturnValue(slot==null?null:slot.stack());}
    }
    @Inject(method="getEntitySlot(Lnet/minecraft/entity/LivingEntity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;",at=@At("HEAD"),cancellable=true,remap=true)
    private static void ssca$list(LivingEntity entity,String backend,String group,String name,CallbackInfoReturnable<List<ItemStack>> cir) {
        if(backend!=null&&!backend.equals("auto"))return;
        var result=new ArrayList<ItemStack>();
        for(int i=0;;i++) {var slot=DualAccessoryEquipment.find(entity,group,name,i);if(slot==null)break;result.add(slot.stack());}
        cir.setReturnValue(result);
    }
    @Inject(method="setEntitySlot",at=@At("HEAD"),cancellable=true)
    private static void ssca$write(LivingEntity entity,String backend,String group,String name,int index,ItemStack stack,CallbackInfo ci) {
        DualAccessoryEquipment.changed(entity);
        if(backend!=null&&!backend.equals("auto"))return;
        var slot=DualAccessoryEquipment.find(entity,group,name,index);
        if(slot!=null)AccessoryUtils.activeAccessoryModInterfaces.get(slot.backend()).setEntitySlot(entity,slot.group(),slot.name(),slot.index(),stack);
        DualAccessoryEquipment.changed(entity);
        ci.cancel();
    }
    @Inject(method="setEntitySlot",at=@At("RETURN"))
    private static void ssca$written(LivingEntity entity,String backend,String group,String name,int index,ItemStack stack,CallbackInfo ci) {
        DualAccessoryEquipment.changed(entity);
    }
    @Inject(method={"onPlayerEquip","onPlayerUnEquip"},at=@At("HEAD"),cancellable=true)
    private static void ssca$effectiveEvents(PlayerEntity player,Identifier id,String backend,CallbackInfo ci) {
        if(DualAccessoryEquipment.initialized()&&DualAccessoryEquipment.manages(id)) {DualAccessoryEquipment.changed(player);ci.cancel();}
    }
}
