package net.ssca.neomagic;

import com.google.gson.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoIntegrationProbe {
    private static int checks;
    private static void check(boolean test,String reason){checks++;if(!test)throw new AssertionError(reason);}
    public static void run() throws Exception {
        new NeoMagic().onInitialize();
        int[][] levels={{-1,0},{0,0},{5999,0},{6000,1},{11999,1},{12000,2},{17999,2},{18000,3},{24000,4},{30000,5},{Integer.MAX_VALUE,Integer.MAX_VALUE/6000}};
        for(int[] point:levels){
            check(net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.tier(point[0])==point[1],"Player level boundary: "+point[0]);
            check(net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.bonus(point[0])==point[1]*50,"Each player level grants exactly fifty safety");
        }
        check(net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.progressTen(12000)==0,"Level-up resets within-level progress while retaining total XP");
        for(int level:new int[]{0,1,2,3,100})check(net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.thresholdTen(level)==6000,"Every level costs exactly 600 experience");
        check(net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.progressTen(12678)==678,"Fractional experience carries into the next 600-XP level");
        check(Registries.ITEM.get(NeoMagic.id("guiding_wand"))==NeoMagic.WAND,"Wand registered");
        check(Registries.BLOCK.get(NeoMagic.id("research_table"))==NeoMagic.TABLE,"Independent workbench registered");
        check(NeoMagic.SAMPLES.size()==18,"Seven cores, five carriers, six controls");
        ItemStack wand=new ItemStack(NeoMagic.WAND);var blueprint=new Blueprint("test",Core.THERMAL,Carrier.PROJECTILE,List.of(Control.SPLIT,Control.CONSTRAIN),Core.FROST,2,35,24);
        wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(blueprint));
        check(NeoNetwork.bound(ItemStack.fromNbt(wand.writeNbt(new NbtCompound()))).equals(blueprint),"Real ItemStack NBT roundtrip");
        check(NeoNetwork.bound(new ItemStack(Items.BLAZE_ROD))==null,"Old items do not become new casting carriers");
        var state=new NeoState();UUID playerA=UUID.randomUUID(),playerB=UUID.randomUUID();var a=state.player(playerA);var b=state.player(playerB);
        a.burden=123.25;a.recovery=31;a.spellbookMasteryExpTen=7312;a.hypothesis="hypothesis";a.draft=blueprint;
        a.known.add("core:FROST");a.successful(blueprint);a.cooldowns.put(blueprint.identity(),80);
        check(b.burden==0&&!b.known.contains("core:FROST")&&b.progress.isEmpty(),"Player states do not leak");
        check(a.mastery(blueprint)>0,"Success grows only this spell");
        var other=new Blueprint("test",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,2,35,24);
        check(a.mastery(other)==0,"Different structure has independent adaptation");
        var stronger=new Blueprint("test",Core.THERMAL,Carrier.PROJECTILE,blueprint.controls(),Core.FROST,4,35,24);
        check(a.mastery(stronger)==0,"Low working range does not fully train high output");
        var encoded=state.writeNbt(new NbtCompound());
        check(encoded.getInt("Version")==2&&encoded.getCompound("Players").getKeys().size()==2,"Versioned persistent save");
        var method=NeoState.class.getDeclaredMethod("read",NbtCompound.class);method.setAccessible(true);
        var restored=(NeoState)method.invoke(null,encoded);
        check(restored.player(playerA).burden==123.25&&restored.player(playerA).recovery==31,"Burden and waiting time survive reload");
        check(restored.player(playerA).spellbookMasteryExpTen==7312&&restored.player(playerB).spellbookMasteryExpTen==0,"Player mastery survives save/reload without leaking between UUIDs");
        check(restored.player(playerA).draft.equals(blueprint)&&restored.player(playerA).cooldowns.get(blueprint.identity())==80,"Blueprint and CD survive reload");
        var packet=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();packet.writeNbt(encoded);
        check(packet.readNbt().equals(encoded),"Minecraft packet NBT roundtrip");
        for(int i=0;i<32;i++) {
            var record=new NeoState.Experiment();record.blueprint=blueprint;
            for(int j=0;j<32;j++)record.measurements.add(new NeoState.Measurement("target-"+j,"minecraft:pig","x".repeat(160),9,20,11,0,0));
            a.add(record);
        }
        NbtCompound large=state.writeNbt(new NbtCompound());
        check(large.getCompound("Players").getByteArray(playerA.toString()).length>65535,"Large history exceeds NBT string limit");
        var saved=new java.io.File("large-neomagic-history.dat");NbtIo.writeCompressed(large,saved);
        check(NbtIo.readCompressed(saved).equals(large),"Large history writes and reloads as byte arrays, without 64KiB string corruption");
        var largePacket=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();largePacket.writeNbt(large);
        check(largePacket.readNbt().equals(large),"Large history packet roundtrip");
        var zh=json("assets/my_addon/lang/zh_cn.json");var en=json("assets/my_addon/lang/en_us.json");
        check(zh.keySet().equals(en.keySet()),"Locale keys match");
        for(var entry:zh.entrySet())check(!entry.getValue().getAsString().contains("�"),"UTF8 localization: "+entry.getKey());
        for(var sample:NeoMagic.SAMPLES.entrySet()) {
            var id=Registries.ITEM.getId(sample.getValue());
            check(json("assets/ssca_neomagic/models/item/"+id.getPath()+".json").has("textures"),"Sample model: "+id);
            check(json("data/ssca_neomagic/recipes/"+id.getPath()+".json").has("ingredients"),"Survival acquisition: "+id);
        }
        var rules=json("data/ssca_neomagic/rules/defaults.json");
        check(rules.getAsJsonObject("forms").size()>=57,"Explicit form defaults included");
        for(Core core:Core.values())check(rules.getAsJsonObject("costs").has(core.name()),"Independent core cost: "+core);
        checks+=net.jackcooper.shapeShifterCurseAddon.client.hud.NeoHudChecks.run();
        System.out.println("Neomagic Fabric integration: "+checks+" checks passed (registries, real ItemStack/NBT/packets, persistence, isolation, research and resources). No live multiplayer acceptance.");
    }
    private static JsonObject json(String path) throws IOException {
        try(var stream=NeoIntegrationProbe.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null)throw new IOException("Missing resource: "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
