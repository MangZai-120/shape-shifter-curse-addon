package net.ssca.neomagic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.level.storage.LevelStorage;
import java.nio.file.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoWorldProbe {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void run() throws Exception {
        for(ModInitializer mod:FabricLoader.getInstance().getEntrypoints("main",ModInitializer.class))mod.onInitialize();
        var storage=LevelStorage.create(Path.of("saves"));var save=storage.createSession("neo-"+System.currentTimeMillis());
        var directory=save.getDirectory(WorldSavePath.GENERATED).resolve("ssca_neomagic/structures");Files.createDirectories(directory);
        NbtCompound empty=new NbtCompound();empty.putInt("DataVersion",3465);NbtList size=new NbtList();for(int i=0;i<3;i++)size.add(NbtInt.of(12));empty.put("size",size);
        empty.put("entities",new NbtList());empty.put("blocks",new NbtList());NbtList palette=new NbtList();NbtCompound air=new NbtCompound();air.putString("Name","minecraft:air");palette.add(air);empty.put("palette",palette);
        NbtIo.writeCompressed(empty,directory.resolve("empty.nbt").toFile());
        var test=new TestFunction("neomagic","world_lifecycle","ssca_neomagic:empty",300,0,true,NeoWorldProbe::test);
        var batch=new GameTestBatch("neomagic",List.of(test),world->{},world->{});
        MinecraftServer.startServer(thread->TestServer.create(thread,save,VanillaDataPackProvider.createManager(save),List.of(batch),new BlockPos(0,64,0)));
    }
    private static NeoRuntime.Active active(ServerPlayerEntity p,Blueprint b,Vec3d point,Entity target) {
        var q=quote(b,0,NeoRules.cost(b.core()));var s=new Session(b,q);s.complete=true;s.paid=q.actual();
        var r=new NeoState.Experiment();r.blueprint=b;r.actual=q.actual();r.normal=q.normal();r.form=NeoRuntime.form(p);r.dimension=p.getWorld().getRegistryKey().getValue().toString();
        return new NeoRuntime.Active(s,r,new ItemStack(NeoMagic.WAND),Hand.MAIN_HAND,new NeoEffects.Anchor(point,target==null?null:target.getUuid()));
    }
    private static void test(TestContext test) {
        ServerPlayerEntity p=test.createMockCreativeServerPlayerInWorld();p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        test.waitAndRun(70,()->effects(test,p));
    }
    private static void effects(TestContext test,ServerPlayerEntity p) {
        try {
            var world=test.getWorld();
            Vec3d origin=test.getAbsolutePos(new BlockPos(3,2,3)).toCenterPos();p.refreshPositionAndAngles(origin.x,origin.y,origin.z,0,0);
            for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(origin).add(-6,-1,-6),BlockPos.ofFloored(origin).add(6,-1,6)))world.setBlockState(pos,Blocks.STONE.getDefaultState());
            NeoRuntime.refreshForm(p);var d=NeoRuntime.data(p);d.known.addAll(NeoMagic.SAMPLES.keySet());
            d.burden=150;NeoRuntime.relieve(p,20);check(d.burden==130,"Potion relief consumes the same C");
            var b=new Blueprint("heal",Core.LUNAR,Carrier.SELF,List.of(),Core.VOID,1,0,4);
            p.setHealth(5);var heal=active(p,b,origin,p);check(NeoEffects.release(p,heal),"Self heal releases");check(p.getHealth()>5&&heal.record.healing>0,"Actual healing and measurement");
            p.setHealth(20);p.getAbilities().invulnerable=false;
            var reverse=active(p,b,origin,p);reverse.session.reversed=true;
            // Hostile self effects are allowed only for explicit healing inversion.
            check(NeoEffects.release(p,reverse)&&p.getHealth()<20&&reverse.record.damage>0,"Healing inversion actually damages self");
            p.timeUntilRegen=0;p.setHealth(20);p.setPitch(90);
            var reversedArea=active(p,new Blueprint("reverse area",Core.LUNAR,Carrier.AREA,List.of(),Core.VOID,1,0,1),origin,p);reversedArea.session.reversed=true;
            check(NeoEffects.release(p,reversedArea)&&p.getHealth()<20,"Area healing inversion includes the caster instead of granting self immunity");
            var farA=EntityType.PIG.create(world);var farB=EntityType.PIG.create(world);
            farA.refreshPositionAndAngles(origin.x-5,origin.y,origin.z,0,0);farB.refreshPositionAndAngles(origin.x+5,origin.y,origin.z,0,0);
            world.spawnEntity(farA);world.spawnEntity(farB);farA.setHealth(1);farB.setHealth(1);
            var splitArea=active(p,new Blueprint("split area",Core.LUNAR,Carrier.AREA,List.of(Control.SPLIT),Core.VOID,1,0,1),origin,p);
            check(NeoEffects.release(p,splitArea)&&farA.getHealth()>1&&farB.getHealth()>1&&splitArea.record.arrived<=9.00001,"Area split actually widens coverage without copying the total budget");
            farA.discard();farB.discard();
            var contact=new Blueprint("heat",Core.THERMAL,Carrier.CONTACT,List.of(),Core.FROST,1,0,4);
            var pig=EntityType.PIG.create(world);pig.refreshPositionAndAngles(origin.x,origin.y,origin.z+2,0,0);world.spawnEntity(pig);
            p.setPitch(25);
            var hit=active(p,contact,pig.getPos(),pig);float health=pig.getHealth();
            check(NeoEffects.release(p,hit),"Contact releases");check(pig.getHealth()<health&&hit.record.damage>0,"Real target damage recorded");
            pig.discard();
            for(Core core:List.of(Core.FROST,Core.CURSE,Core.VOID)) {
                var victim=EntityType.PIG.create(world);victim.refreshPositionAndAngles(origin.x,origin.y,origin.z+2,0,0);world.spawnEntity(victim);
                var spell=new Blueprint("adapter",core,Carrier.CONTACT,List.of(),Core.FROST,1,0,4);
                var effect=active(p,spell,victim.getPos(),victim);
                if(core==Core.VOID)victim.setAbsorptionAmount(4);
                check(NeoEffects.release(p,effect)&&effect.record.arrived>0,"Effect adapter reaches target: "+core);
                if(core==Core.FROST)check(victim.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"Frost applies control");
                if(core==Core.CURSE)check(victim.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS),"Curse attaches lifetime status");
                if(core==Core.VOID)check(victim.getAbsorptionAmount()<4,"Erosion consumes actual absorption");
                victim.discard();
            }
            var boltVictim=EntityType.PIG.create(world);boltVictim.refreshPositionAndAngles(origin.x,origin.y,origin.z+2,0,0);world.spawnEntity(boltVictim);
            var projectile=new Blueprint("bolt",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,1,0,8);
            var bolt=active(p,projectile,boltVictim.getPos(),boltVictim);check(NeoEffects.release(p,bolt),"Projectile emitted");
            d.burden=10000;for(int i=0;i<10;i++)NeoEffects.tick(p.getServer());
            check(bolt.record.damage>0&&bolt.record.pending==0,"Real projectile collision resolves even after burden changes");
            check(bolt.session.checkpoints.isEmpty(),"Released effects do not run new collapse draws");boltVictim.discard();d.burden=0;
            var summon=new Blueprint("summon",Core.SUMMON,Carrier.AREA,List.of(Control.SPLIT),Core.FROST,1,0,8);
            var a=active(p,summon,origin.add(0,0,4),null);p.setPitch(40);
            check(NeoEffects.release(p,a),"Summons spawn in real world");check(a.record.summons>0,"Summons survive ENTITY_LOAD callback");
            var store=new Blueprint("store",Core.LUNAR,Carrier.SELF,List.of(Control.STORE),Core.VOID,1,0,4);
            var stored=active(p,store,origin,p);check(NeoEffects.release(p,stored)&&NeoEffects.hasStored(p.getUuid()),"Stored structure created");
            p.setHealth(5);NeoEffects.trigger(p);check(!NeoEffects.hasStored(p.getUuid())&&p.getHealth()>5,"Stored trigger has real effect");
            var space=new Blueprint("space",Core.SPACE,Carrier.SELF,List.of(),Core.FROST,1,0,8);p.setPitch(0);
            Vec3d prior=p.getPos();var teleport=active(p,space,origin,p);
            check(NeoEffects.release(p,teleport)&&p.getPos().distanceTo(prior)>0,"Actual space relocation");
            prior=p.getPos();var deviated=active(p,space,origin,p);deviated.session.attenuation=.5;deviated.session.yaw=20;
            check(NeoEffects.release(p,deviated)&&p.getPos().distanceTo(prior)<4,"Teleport attenuation and deviation consumed");
            d.burden=0;var wand=new ItemStack(NeoMagic.WAND);var timed=new Blueprint("timed",Core.LUNAR,Carrier.SELF,List.of(),Core.VOID,1,40,4);
            wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(timed));p.setStackInHand(Hand.MAIN_HAND,wand);
            NeoRuntime.start(p,Hand.MAIN_HAND);check(NeoRuntime.ACTIVE.containsKey(p.getUuid()),"Real runtime creates isolated cast");
            NeoRuntime.cancel(p,"cancelled");check(!NeoRuntime.ACTIVE.containsKey(p.getUuid())&&d.cooldowns.containsKey(timed.identity()),"Cancel ends cast and creates CD");
            craftAuthority(p);
            NeoState.get(p.getServer()).markDirty();world.getPersistentStateManager().save();
            check(d.known.size()==18,"Research remains separate and complete");
            NeoEffects.clear();System.out.println("Neomagic GameTest: "+checks+" checks passed in a real ServerWorld (damage, healing, summons, storage, runtime, relief, persistence).");test.complete();
        } catch(Throwable failure){failure.printStackTrace();throw new RuntimeException(failure);}
    }
    private static void craftAuthority(ServerPlayerEntity p)throws Exception {
        BlockPos table=p.getBlockPos().add(-2,0,0);p.getWorld().setBlockState(table,NeoMagic.TABLE.getDefaultState());NeoNetwork.open(p,table);
        var field=NeoNetwork.class.getDeclaredField("OPEN");field.setAccessible(true);Object context=((Map<?,?>)field.get(null)).get(p.getUuid());
        var tokenMethod=context.getClass().getDeclaredMethod("token");tokenMethod.setAccessible(true);UUID token=(UUID)tokenMethod.invoke(context);
        var dispatch=NeoNetwork.class.getDeclaredMethod("handle",ServerPlayerEntity.class,NbtCompound.class);dispatch.setAccessible(true);
        var blueprint=new Blueprint("authority",Core.THERMAL,Carrier.PROJECTILE,List.of(),Core.FROST,1,40,8);
        p.getInventory().offerOrDrop(new ItemStack(Items.PAPER));p.getInventory().offerOrDrop(new ItemStack(Items.AMETHYST_SHARD));
        NbtCompound request=new NbtCompound();request.putString("Action","craft");request.putString("Blueprint",NeoState.JSON.toJson(blueprint));request.putUuid("Token",UUID.randomUUID());
        dispatch.invoke(null,p,request);check(count(p,NeoMagic.BLUEPRINT)==0&&count(p,Items.PAPER)==1,"Wrong session token cannot craft or consume materials");
        request.putUuid("Token",token);dispatch.invoke(null,p,request);
        check(count(p,NeoMagic.BLUEPRINT)==1,"Production server action creates real blueprint");
        int expected=p.isCreative()?1:0;
        check(count(p,Items.PAPER)==expected&&count(p,Items.AMETHYST_SHARD)==expected,"Crafting respects exact survival cost and the GameTest creative player's exemption");
        dispatch.invoke(null,p,request);check(count(p,NeoMagic.BLUEPRINT)==1,"Repeated action cannot duplicate blueprint");NeoNetwork.close(p);
    }
    private static int count(ServerPlayerEntity p,Item item) {
        int total=0;for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item))total+=p.getInventory().getStack(i).getCount();return total;
    }
}
