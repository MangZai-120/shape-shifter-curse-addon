package net.ssca.neomagic;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.*;
import io.github.apace100.apoli.util.HudRender;
import net.jackcooper.shapeShifterCurseAddon.network.CountdownSync;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.additional_power.LevitatePower;

/** Native resource actions and real outbound sends, including the unmodified legacy path. */
public final class NeoCountdownSyncProbe {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class CountingConnection extends ClientConnection {
        int powerPackets;
        CountingConnection(){super(NetworkSide.SERVERBOUND);}
        @Override public void send(Packet<?> packet,PacketCallbacks callbacks){
            if(packet instanceof CustomPayloadS2CPacket p&&p.getChannel().toString().equals("apoli:sync_power"))powerPackets++;
        }
    }
    public static void run(TestContext t)throws Exception {
        var p=NeoFormCastingProbe.player(t,FormIdentifiers.ALLAY_SP,100);
        var source=new Identifier("ssca_neomagic","sync_probe");int[] minActions={0},referenceActions={0};
        var counterId=new Identifier("ssca_neomagic","probe_counter");
        var nativeId=new Identifier("ssca_neomagic","probe_levitate");
        var customId=new Identifier("ssca_neomagic","probe_custom_levitate");
        var counter=new PowerType<ResourcePower>(counterId,null){
            @Override public ResourcePower create(LivingEntity e){return new ResourcePower(this,e,HudRender.DONT_RENDER,0,0,10,x->minActions[0]++,null);}
        };
        var nativeType=new PowerType<LevitatePower>(nativeId,null){@Override public LevitatePower create(LivingEntity e){return new LevitatePower(this,e);}};
        var customType=new PowerType<LevitatePower>(customId,null){@Override public LevitatePower create(LivingEntity e){return new LevitatePower(this,e){
            @Override public net.minecraft.nbt.NbtElement toTag(){var n=new net.minecraft.nbt.NbtCompound();n.putInt("CustomState",1);return n;}
        };}};
        for(var type:java.util.List.of(counter,nativeType,customType))PowerTypeRegistry.register(type.getIdentifier(),type);
        var powers=PowerHolderComponent.KEY.get(p);var previous=p.networkHandler;var connection=new CountingConnection();
        try {
            for(var type:java.util.List.of(counter,nativeType,customType))powers.addPower(type,source);
            p.networkHandler=new ServerPlayNetworkHandler(p.getServer(),connection,p);
            var resource=counter.get(p);var reference=new ResourcePower(counter,p,HudRender.DONT_RENDER,0,0,10,x->referenceActions[0]++,null);
            for(int i=0;i<20;i++){CountdownSync.decrement(p,counter);reference.setValue(reference.getValue()-1);}
            check(resource.getValue()==0,"Idle resource remains at the native floor");
            check(minActions[0]==referenceActions[0],"No-op optimization preserves every native min_action");
            check(connection.powerPackets==0,"Idle zero resource sends no repeated packet");
            resource.setValue(3);
            CountdownSync.decrement(p,counter);
            check(resource.getValue()==2&&connection.powerPackets==1,"Legacy clients receive one authoritative changed value, not two");
            CountdownSync.decrement(p,counter);CountdownSync.decrement(p,counter);
            check(resource.getValue()==0&&connection.powerPackets==3,"Final zero is sent exactly once on the changing path");
            var flush=CountdownSync.class.getDeclaredMethod("flush",net.minecraft.server.MinecraftServer.class);flush.setAccessible(true);flush.invoke(null,p.getServer());
            for(int i=0;i<20;i++)CountdownSync.decrement(p,counter);
            check(connection.powerPackets==3,"Settled zero remains silent after the final clock anchor");
            p.setNoGravity(false);p.fallDistance=7;
            var levitate=nativeType.get(p);levitate.onUse();levitate.tick();
            check(p.hasNoGravity()&&p.fallDistance==0,"Native levitation and fall-distance behavior still run");
            check(connection.powerPackets==3,"Native empty levitation serialization sends no state-free packet");
            PowerHolderComponent.syncPower(p,customType);
            check(connection.powerPackets==4,"A levitation subclass with its own state still synchronizes");
        } finally {
            p.networkHandler=previous;powers.removeAllPowersFromSource(source);
            var remove=PowerTypeRegistry.class.getDeclaredMethod("remove",Identifier.class);remove.setAccessible(true);
            for(var id:java.util.List.of(counterId,nativeId,customId))remove.invoke(null,id);
        }
        System.out.println("NeoCountdownSyncProbe: "+checks+" checks passed");
    }
}
