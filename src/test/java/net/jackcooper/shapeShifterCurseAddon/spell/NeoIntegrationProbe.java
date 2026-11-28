package net.jackcooper.shapeShifterCurseAddon.spell;

import net.jackcooper.shapeShifterCurseAddon.network.NeoHudNetworkChecks;

import com.google.gson.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static net.jackcooper.shapeShifterCurseAddon.spell.NeoModel.*;

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
        check(Registries.ITEM.getIds().stream().noneMatch(id->id.getNamespace().equals("ssca_neomagic")),"Potion items use the main addon namespace; research items stay retired");
        for(String item:Set.of("moon_dust_vial","moon_dust_bottle","moon_dust_splash","test_tube","test_tube_potion"))check(Registries.ITEM.containsId(new net.minecraft.util.Identifier("ssc_addon",item)),"Canonical addon potion item exists: "+item);
        check(Registries.BLOCK.getIds().stream().noneMatch(id->id.getNamespace().equals("ssca_neomagic")),"Retired experimental blocks are not registered");
        check(Registries.ITEM_GROUP.getIds().stream().noneMatch(id->id.getNamespace().equals("ssca_neomagic")),"Retired creative group is absent");
        for(String name:java.util.List.of("guiding_wand","observation_notebook","blueprint","research_table","observation_target","sample_thermal","sample_frost","sample_lunar","sample_curse","sample_summon","sample_void","sample_space","carrier_self","carrier_contact","carrier_projectile","carrier_area","carrier_lock","control_split","control_merge","control_store","control_constrain","control_bridge","control_convert")) {
            check(NeoIntegrationProbe.class.getClassLoader().getResource("data/ssc_addon/recipes/"+name+".json")==null,"Retired recipe absent: "+name);
            check(NeoIntegrationProbe.class.getClassLoader().getResource("assets/ssc_addon/models/item/"+name+".json")==null,"Retired item model absent: "+name);
        }
        check(Registries.STATUS_EFFECT.containsId(NeoMagic.id("breakdown")),"Live collapse status backend remains registered");
        ItemStack wand=new ItemStack(net.jackcooper.shapeShifterCurseAddon.spell.NeoTestFixtures.WAND);var blueprint=new Blueprint("test",Core.THERMAL,Carrier.PROJECTILE,List.of(Control.SPLIT,Control.CONSTRAIN),Core.FROST,2,35,24);
        wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(blueprint));
        check(net.jackcooper.shapeShifterCurseAddon.spell.NeoResearchFixtures.bound(ItemStack.fromNbt(wand.writeNbt(new NbtCompound()))).equals(blueprint),"Real ItemStack NBT roundtrip");
        check(net.jackcooper.shapeShifterCurseAddon.spell.NeoResearchFixtures.bound(new ItemStack(Items.BLAZE_ROD))==null,"Old items do not become new casting carriers");
        var state=new NeoState();UUID playerA=UUID.randomUUID(),playerB=UUID.randomUUID();var a=state.player(playerA);var b=state.player(playerB);
        a.burden=123.25;a.recovery=31;a.spellbookMasteryExpTen=7312;a.hypothesis="hypothesis";a.draft=blueprint;
        state.tutorialPolicyInitialized=true;state.tutorialAutoEnabled=true;a.spellTutorialSeen=true;
        a.known.add("core:FROST");a.successful(blueprint);a.cooldowns.put(blueprint.identity(),80);
        check(b.burden==0&&!b.known.contains("core:FROST")&&b.progress.isEmpty(),"Player states do not leak");
        check(a.mastery(blueprint)>0,"Success grows only this spell");
        var other=new Blueprint("test",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,2,35,24);
        check(a.mastery(other)==0,"Different structure has independent adaptation");
        var stronger=new Blueprint("test",Core.THERMAL,Carrier.PROJECTILE,blueprint.controls(),Core.FROST,4,35,24);
        check(a.mastery(stronger)==0,"Low working range does not fully train high output");
        var witchcraft = new net.jackcooper.shapeShifterCurseAddon.spell.spells.FalseWitchcraftSpell();
        witchcraft.ssc_addon$applyConfig(net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig.fromJson(
                json("data/ssc_addon/spells/false_witchcraft.json")));
        var cast = new NeoState.Experiment();
        cast.spell = witchcraft.getId().toString();
        cast.blueprint = NeoLegacyCasting.descriptor(witchcraft, 2, 50);
        cast.form = "shape-shifter-curse:axolotl_3";
        a.add(cast);
        check(cast.blueprint.core() == Core.VOID && cast.blueprint.carrier() == Carrier.SELF,
                "False witchcraft records its actual self target");
        check(errors(cast.blueprint).contains("carrier") && !validRecordedBlueprint(cast.blueprint, ""),
                "Void/self is still invalid for a research draft or an unnamed blueprint cast");
        var malformed = new Blueprint("false_witchcraft", Core.VOID, Carrier.SELF, List.of(), Core.FROST, 9, 50, 32);
        check(!validRecordedBlueprint(malformed, cast.spell), "Book cast history still validates descriptor bounds");
        var encoded=state.writeNbt(new NbtCompound());
        check(encoded.getInt("Version")==2&&encoded.getCompound("Players").getKeys().size()==2,"Versioned persistent save");
        var method=NeoState.class.getDeclaredMethod("read",NbtCompound.class);method.setAccessible(true);
        var restored=(NeoState)method.invoke(null,encoded);
        var archived = restored.player(playerA).records.get(0);
        check(archived.id.equals(cast.id) && archived.spell.equals(cast.spell) && archived.blueprint.equals(cast.blueprint),
                "False witchcraft history survives the actual save reader with its original identity and targeting");
        check(restored.tutorialPolicyInitialized&&restored.tutorialAutoEnabled,"New-world tutorial policy survives reload");
        check(restored.player(playerA).spellTutorialSeen&&!restored.player(playerB).spellTutorialSeen,"Tutorial viewing is isolated per player and persists");
        check(!new NeoState().player(playerA).spellTutorialSeen,"Another world does not inherit tutorial completion");
        var legacy=encoded.copy();legacy.remove("TutorialPolicyInitialized");legacy.remove("TutorialAutoEnabled");
        var oldPlayer=JsonParser.parseString(new String(legacy.getCompound("Players").getByteArray(playerA.toString()),StandardCharsets.UTF_8)).getAsJsonObject();
        oldPlayer.remove("spellTutorialSeen");legacy.getCompound("Players").putByteArray(playerA.toString(),oldPlayer.toString().getBytes(StandardCharsets.UTF_8));
        var legacyState=(NeoState)method.invoke(null,legacy);
        check(!legacyState.tutorialPolicyInitialized&&!legacyState.tutorialAutoEnabled&&!legacyState.player(playerA).spellTutorialSeen,
                "Old save fields default safely without enabling automatic tutorial");
        check(legacyState.player(playerA).burden==123.25&&legacyState.player(playerA).spellbookMasteryExpTen==7312,"Tutorial migration preserves existing spell data");
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
        var zh=json("assets/ssc_addon/lang/zh_cn.json");var en=json("assets/ssc_addon/lang/en_us.json");
        check(zh.keySet().equals(en.keySet()),"Locale keys match");
        for(var entry:zh.entrySet())check(!entry.getValue().getAsString().contains("�"),"UTF8 localization: "+entry.getKey());
        var rules=json("data/ssc_addon/rules/defaults.json");
        check(rules.getAsJsonObject("forms").size()>=57,"Explicit form defaults included");
        for(Core core:Core.values())check(rules.getAsJsonObject("costs").has(core.name()),"Independent core cost: "+core);
        checks+=net.jackcooper.shapeShifterCurseAddon.client.hud.NeoHudChecks.run();
        checks+=NeoHudNetworkChecks.run();
        System.out.println("Neomagic Fabric integration: "+checks+" checks passed (registries, real ItemStack/NBT/packets, persistence, isolation, research and resources). No live multiplayer acceptance.");
    }
    private static JsonObject json(String path) throws IOException {
        try(var stream=NeoIntegrationProbe.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null)throw new IOException("Missing resource: "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
