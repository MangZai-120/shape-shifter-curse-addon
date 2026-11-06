package net.ssca.neomagic;

import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.jackcooper.shapeShifterCurseAddon.mixin.TransformManagerAccessor;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.test.TestContext;
import net.onixary.shapeShifterCurseFabric.player_form.utils.*;

public final class NeoTransformCleanupProbe {
    public static void run(TestContext t)throws Exception {
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.AXOLOTL_SP,100);
        var other=NeoFormCastingProbe.player(t,FormIdentifiers.SNOW_FOX_SP,100);
        TransformManager.serverTick(p.getServer());
        var entries=TransformManagerAccessor.ssca$playerData();
        if(!entries.containsKey(p.getUuid())||!entries.containsKey(other.getUuid()))throw new AssertionError("Native transform entries actually created");
        var form=PlayerFormComponent.COMPONENT.get(p).nowForm;
        var original=p.networkHandler;
        var stale=new net.minecraft.server.network.ServerPlayNetworkHandler(p.getServer(),
                new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),p);
        p.networkHandler=original;
        PlayerSessionCleanup.disconnect(stale,p.getServer());
        if(!entries.containsKey(p.getUuid()))throw new AssertionError("Stale connection must not clear the current transform");
        PlayerSessionCleanup.disconnect(original,p.getServer());
        if(entries.containsKey(p.getUuid()))throw new AssertionError("Disconnected entity released from SSC transform cache");
        if(!entries.containsKey(other.getUuid()))throw new AssertionError("Other player's transform is preserved");
        if(PlayerFormComponent.COMPONENT.get(p).nowForm!=form)throw new AssertionError("Persistent form selection is preserved");
        System.out.println("NeoTransformCleanupProbe: 5 checks passed");
    }
}
