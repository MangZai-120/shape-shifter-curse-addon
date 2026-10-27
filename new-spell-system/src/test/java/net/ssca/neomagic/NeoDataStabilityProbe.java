package net.ssca.neomagic;

import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import java.nio.file.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoDataStabilityProbe {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static final LinkedHashSet<String> ids=new LinkedHashSet<>();
    private static void enumerate(Core core,List<Control> path) {
        var b=new Blueprint("",core,Carrier.PROJECTILE,path,core==Core.FROST?Core.THERMAL:Core.FROST,1,40,16);
        if(errors(b).isEmpty())ids.add(b.identity());
        if(path.size()==6)return;
        for(Control c:Control.values()){var next=new ArrayList<>(path);next.add(c);enumerate(core,next);}
    }
    public static void run()throws Exception {
        new NeoMagic().onInitialize();
        for(Core core:List.of(Core.THERMAL,Core.FROST,Core.LUNAR,Core.CURSE,Core.VOID))enumerate(core,List.of());
        check(ids.size()>12000,"At least 12000 actually valid blueprint identities");
        System.out.println("Stability data: enumerated "+ids.size()+" valid identities");
        var state=new NeoState();var data=state.player(UUID.randomUUID());
        for(String id:ids.stream().limit(12000).toList())data.progress.put(id,new double[]{100000,100000,100000,100000});
        byte[] wire=NeoNetwork.encodeData(data);var message=new NbtCompound();message.putByteArray("DataJson",wire);var buffer=PacketByteBufs.create();
        try {buffer.writeNbt(message);new CustomPayloadS2CPacket(NeoNetwork.STATE,buffer);check(true,"Large legitimate research snapshot fits Minecraft custom payload limit");}
        finally {buffer.release();}
        check(new String(NeoPayload.unpack(wire),java.nio.charset.StandardCharsets.UTF_8).equals(NeoState.JSON.toJson(data)),"Compressed snapshot is lossless");
        boolean rejected=false;try{NeoPayload.unpack(wire,1024);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Decompression expansion is bounded");
        rejected=false;try{NeoPayload.unpack(Arrays.copyOf(wire,wire.length/2));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Truncated snapshots are rejected");
        Random random=new Random(20261027);
        long fuzzStarted=System.nanoTime();
        for(int i=0;i<20000;i++) {
            var input=PacketByteBufs.create();
            try {byte[] bytes=new byte[random.nextInt(256)];random.nextBytes(bytes);input.writeBytes(bytes);check(NeoNetwork.readRequest(input)==null,"Malformed requests do not escape the parser");}
            finally{input.release();}
        }
        System.out.println("Stability data: 20000 malformed request parses completed in "+(System.nanoTime()-fuzzStarted)/1e9+" seconds");
        for(String action:List.of("notes","draft","baseline","equip","craft","close")) {
            var valid=new NbtCompound();valid.putUuid("Token",UUID.randomUUID());valid.putString("Action",action);valid.putString("Blueprint",NeoState.JSON.toJson(Blueprint.starter()));
            valid.putString("Hypothesis","中文😀\u0000");var input=PacketByteBufs.create();
            try{input.writeNbt(valid);check(valid.equals(NeoNetwork.readRequest(input)),"Valid Unicode and modified UTF-8 request survives parsing");}finally{input.release();}
        }
        var oversized=PacketByteBufs.create();try{oversized.writeZero(16385);check(NeoNetwork.readRequest(oversized)==null,"Oversized request rejected before NBT parse");}finally{oversized.release();}
        var trailing=PacketByteBufs.create();try{trailing.writeNbt(new NbtCompound());trailing.writeByte(1);check(NeoNetwork.readRequest(trailing)==null,"Trailing request data rejected");}finally{trailing.release();}
        for(int player=0;player<32;player++) {
            var d=state.player(new UUID(42,player));d.burden=player+.25;d.recovery=player;
            for(int j=0;j<2000;j++) {
                var record=new NeoState.Experiment();record.blueprint=Blueprint.starter();record.form="shape-shifter-curse:original_shifter";
                d.add(record);if(j==4)d.citations=List.of(record.id);
            }
            check(d.records.size()==32&&d.records.stream().anyMatch(e->d.citations.contains(e.id)),"History remains bounded and cited evidence retained");
        }
        var read=NeoState.class.getDeclaredMethod("read",NbtCompound.class);read.setAccessible(true);
        for(int cycle=0;cycle<32;cycle++) {
            NbtCompound n=state.writeNbt(new NbtCompound());var file=Path.of("roundtrip.dat").toFile();NbtIo.writeCompressed(n,file);
            state=(NeoState)read.invoke(null,NbtIo.readCompressed(file));
            for(int p=0;p<32;p++)check(state.player(new UUID(42,p)).burden==p+.25,"Repeated saved state does not mix players");
        }
        System.out.println("Stability data: 32 persistent-state disk roundtrips completed");
        for(String corrupt:List.of("{\"records\":null}","{\"progress\":{\"x\":[1]}}","{\"citations\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}")) {
            var n=new NbtCompound();n.putInt("Version",2);var people=new NbtCompound();people.putString(UUID.randomUUID().toString(),corrupt);n.put("Players",people);
            rejected=false;try{read.invoke(null,n);}catch(java.lang.reflect.InvocationTargetException expected){rejected=true;}check(rejected,"Corrupt save rejected without silently replacing data");
        }
        Files.writeString(Path.of("data-stress.json"),"{\"passed\":true,\"legitimate_spell_identities\":12000,\"snapshot_raw_bytes\":"+NeoState.JSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8).length+",\"snapshot_wire_bytes\":"+wire.length+",\"players\":32,\"history_insertions\":64000,\"disk_roundtrips\":32,\"malformed_requests\":20000,\"checks\":"+checks+"}");
        System.out.println("Neomagic data stability passed: "+checks+" checks, snapshot bytes="+wire.length);
    }
}
