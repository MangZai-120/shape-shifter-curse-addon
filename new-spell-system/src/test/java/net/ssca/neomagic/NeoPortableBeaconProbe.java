package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.jackcooper.shapeShifterCurseAddon.util.*;
import net.minecraft.item.*;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/** Preserve beacon drain/aura and all native inventory sections after removing scan allocations. */
public final class NeoPortableBeaconProbe {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void run(TestContext t)throws Exception {
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.ALLAY_SP,100);
        var source=new Identifier("ssca_neomagic","beacon_scan_test");
        var active=new Identifier("my_addon","form_allay_sp_beacon_active");
        var mana=FormIdentifiers.ALLAY_MANA_RESOURCE;
        var powers=io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(p);
        for(var id:java.util.List.of(active,mana))powers.addPower(io.github.apace100.apoli.power.PowerTypeRegistry.get(id),source);
        PowerUtils.setResourceValueAndSync(p,mana,10);
        var beacon=new ItemStack(Items.BEACON);beacon.getOrCreateNbt().putString("Keep","untouched");
        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,beacon);
        AllaySPPortableBeacon.toggleBeacon(p);
        check(AllaySPPortableBeacon.isActiveStack(beacon),"Beacon activates and marks its real stack");
        check(p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE),"Beacon still grants its original aura immediately");
        var tick=AllaySPPortableBeacon.class.getDeclaredMethod("tick",net.minecraft.server.network.ServerPlayerEntity.class);tick.setAccessible(true);
        int initial=PowerUtils.getResourceValue(p,mana);
        p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,ItemStack.EMPTY);p.getInventory().main.set(35,beacon);
        for(int i=0;i<59;i++)tick.invoke(null,p);
        check(PowerUtils.getResourceValue(p,mana)==initial,"No early beacon energy debit");
        p.getInventory().main.set(35,ItemStack.EMPTY);p.getInventory().armor.set(2,beacon);tick.invoke(null,p);
        check(PowerUtils.getResourceValue(p,mana)==initial-1,"Beacon in armor remains carried and pays exactly once at 60 ticks");
        p.getInventory().armor.set(2,ItemStack.EMPTY);p.getInventory().offHand.set(0,beacon);tick.invoke(null,p);
        check(PowerUtils.getResourceValue(p,active)==1,"Moving the active beacon to offhand keeps its session");
        AllaySPPortableBeacon.deactivateBeacon(p);
        check(!AllaySPPortableBeacon.isActiveStack(beacon)&&"untouched".equals(beacon.getNbt().getString("Keep")),"Deactivation preserves unrelated NBT");
        for(var stacks:java.util.List.of(p.getInventory().main,p.getInventory().armor,p.getInventory().offHand)){
            var stale=beacon.copy();stale.getOrCreateNbt().putUuid("ssca_beacon_session",java.util.UUID.randomUUID());stale.getOrCreateNbt().putUuid("ssca_beacon_owner",p.getUuid());
            stacks.set(stacks.size()-1,stale);
        }
        tick.invoke(null,p);
        for(var stacks:java.util.List.of(p.getInventory().main,p.getInventory().armor,p.getInventory().offHand))check(!AllaySPPortableBeacon.isActiveStack(stacks.get(stacks.size()-1)),"Stale marker removed from each native inventory section without delay");
        powers.removeAllPowersFromSource(source);
        System.out.println("NeoPortableBeaconProbe: "+checks+" checks passed");
    }
}
