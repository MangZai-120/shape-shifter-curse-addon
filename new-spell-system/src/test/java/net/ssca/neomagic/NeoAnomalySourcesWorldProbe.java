package net.ssca.neomagic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.MancianimaTeleport;
import net.jackcooper.shapeShifterCurseAddon.ability.SnowFoxSpTeleportAttack;
import net.jackcooper.shapeShifterCurseAddon.anomaly.*;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritBoltEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager;
import net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import static net.ssca.neomagic.NeoModel.*;

/** Real source lifecycles in an isolated server world; does not claim visual/gameplay acceptance. */
public final class NeoAnomalySourcesWorldProbe {
    private record Hit(UUID target, SpellAnomalyContext context) {}
    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<Entity> ENTITIES = new ArrayList<>();
    private static int checks;

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static void near(double expected, double actual, String reason) {
        check(Math.abs(expected - actual) < 0.00001, reason + ": expected=" + expected + ", actual=" + actual);
    }

    public static void start(TestContext context) {
        context.waitAndRun(70, () -> {
            try { run(context); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    private static ServerPlayerEntity player(TestContext context) throws Exception {
        return NeoFormCastingProbe.player(context, new Identifier("shape-shifter-curse", "original_shifter"), 0);
    }

    private static PigEntity pig(ServerPlayerEntity player, double x, double z) {
        return spawn(player, new PigEntity(EntityType.PIG, player.getServerWorld()), x, z);
    }

    private static PigEntity spawn(ServerPlayerEntity player, PigEntity pig, double x, double z) {
        pig.setAiDisabled(true);
        pig.setNoGravity(true);
        pig.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        pig.setHealth(200);
        pig.refreshPositionAndAngles(player.getX() + x, player.getY(), player.getZ() + z, 0, 0);
        player.getServerWorld().getChunk(pig.getBlockPos());
        check(player.getServerWorld().spawnEntity(pig), "Actual source target spawns");
        ENTITIES.add(pig);
        return pig;
    }

    private static SpellAnomalyContext identity(ServerPlayerEntity player, String spell, String element, int level, double amount) {
        return new SpellAnomalyContext(new Identifier("ssc_addon", spell), element, level, amount, player.getUuid(), UUID.randomUUID());
    }

    private static void cast(ServerPlayerEntity player, String spell, int level) {
        SpellRegistry.get(spell).castAtTarget(player, 0, true, level, ItemStack.EMPTY, null);
        check(SpellAnomalyHits.current() == null, "Named source cast restores its scope");
    }

    private static long count(LivingEntity target, String spell) {
        return HITS.stream().filter(hit -> hit.target().equals(target.getUuid()) && hit.context().spellId().getPath().equals(spell)).count();
    }

    private static double meter(LivingEntity target, String element) {
        return AnomalyManager.snapshot(target).values().getOrDefault(element, 0.0);
    }

    private static void collide(ProjectileEntity projectile, LivingEntity target) throws Exception {
        String name=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().mapMethodName(
                "intermediary","net.minecraft.class_1676","method_7488","(Lnet/minecraft/class_239;)V");
        var method = ProjectileEntity.class.getDeclaredMethod(name, HitResult.class);
        method.setAccessible(true);
        method.invoke(projectile, new EntityHitResult(target));
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> map(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    private static void clearTargets() {
        for (Entity entity : ENTITIES) {
            if (entity instanceof LivingEntity living) AnomalyManager.clear(living);
            entity.discard();
        }
        ENTITIES.clear();
        HITS.clear();
    }

    private static ResourceManager resource(String json) {
        var resource = new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        // ResourceFactory.getResource(Identifier), verified in Yarn 1.20.1 build 10; works in both named and packaged runs.
        String getResourceName = net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().mapMethodName(
                "intermediary", "net.minecraft.class_5912", "method_14486", "(Lnet/minecraft/class_2960;)Ljava/util/Optional;");
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals(getResourceName) && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == Identifier.class && method.getReturnType() == Optional.class)
                        return Optional.of(resource);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static JsonObject config() { return JsonParser.parseString(AnomalyRules.snapshotJson()).getAsJsonObject(); }

    private static void amount(JsonObject root, String id, double... values) {
        var array = new JsonArray();
        for (double value : values) array.add(value);
        root.getAsJsonObject("spells").add("ssc_addon:" + id, array);
    }

    private static void configCases() {
        String original = AnomalyRules.snapshotJson();
        var rules = new AnomalyRules();
        try {
            String[] names = {"fire_bolt", "flame_nova", "meteor", "explosion", "frost_spike", "ice_barrage", "frost_nova",
                    "moonlight_arrow", "curse_mark", "dread_whisper", "corrupt_mist", "beep_sheep", "void_devour", "void_erosion", "death_finale", "summon_lunar_spirit_bolt"};
            double[][] expected = {{120,153,185,218,250},{180,215,250,285,320},{240,275,310,345,380},{500,500,500,500,500},
                    {180,210,240,270,300},{70,83,95,108,120},{280,315,350,385,420},{140,170,200,230,260},
                    {160,190,220,250,280},{200,235,270,305,340},{60,70,80,90,100},{220,255,290,325,360},
                    {210,245,280,315,350},{190,225,260,295,330},{500,500,500,500,500},{25,29,33,36,40}};
            for (int spell=0; spell<names.length; spell++) for (int level=1; level<=5; level++)
                near(expected[spell][level-1], AnomalyRules.amount(names[spell], level), "Explicit approved " + names[spell] + " L" + level);
            for (String name : List.of("frost_armor", "lunar_mend", "lunar_veil", "lunar_phase", "summon_lunar_spirit",
                    "companion_resonance", "space_blink", "space_stride", "space_recall", "pocket_space", "domain"))
                for (int level=1; level<=5; level++) near(0, AnomalyRules.amount(name, level), "Helper has no accumulation: " + name);
            near(200, AnomalyRules.domainInitial(), "Domain initial rule");
            near(50, AnomalyRules.domainPulse(), "Domain pulse rule");
            check(AnomalyRules.domainPulseTicks()==40, "Domain pulse is every forty ticks");
            near(0, AnomalyRules.amount("unconfigured_spell", 1), "Unknown rules never invent free accumulation");
            near(120, AnomalyRules.amount("fire_bolt", 0), "Low level clamps");
            near(250, AnomalyRules.amount("ssc_addon:fire_bolt", 99), "High level clamps and namespaced IDs agree");

            var valid=config();amount(valid,"fire_bolt",7,8,9,10,11);
            rules.reload(resource(valid.toString()));
            near(9, AnomalyRules.amount(SpellRegistry.get("fire_bolt"),3), "Real server reload swaps the hot-path spell lookup");
            AnomalyRules.acceptClientSnapshot(valid.toString());
            near(11, AnomalyRules.clientAmount("fire_bolt",5), "Client mirror accepts the same validated snapshot");
            String accepted=AnomalyRules.snapshotJson();
            for (int scenario=0; scenario<7; scenario++) {
                var bad=JsonParser.parseString(accepted).getAsJsonObject();
                switch (scenario) {
                    case 0 -> amount(bad,"fire_bolt",-1);
                    case 1 -> amount(bad,"fire_bolt",501);
                    case 2 -> bad.getAsJsonObject("spells").remove("ssc_addon:frost_spike");
                    case 3 -> bad.addProperty("version",2);
                    case 4 -> bad.addProperty("domain_pulse_ticks",0);
                    case 5 -> amount(bad,"fire_bolt");
                    case 6 -> bad.getAsJsonObject("spells").add("fire_bolt",bad.getAsJsonObject("spells").get("ssc_addon:fire_bolt"));
                }
                boolean rejected=false;
                try { rules.reload(resource(bad.toString())); } catch (IllegalStateException expectedFailure) { rejected=true; }
                check(rejected && AnomalyRules.snapshotJson().equals(accepted), "Invalid server reload is atomic, scenario " + scenario);
                near(9,AnomalyRules.amount("fire_bolt",3),"Rejected reload retains server numeric table");
                rejected=false;
                try { AnomalyRules.acceptClientSnapshot(bad.toString()); } catch (RuntimeException expectedFailure) { rejected=true; }
                check(rejected,"Client rejects invalid snapshot, scenario " + scenario);
                near(11,AnomalyRules.clientAmount("fire_bolt",5),"Rejected client snapshot retains last valid values");
            }
        } finally {
            rules.reload(resource(original));
            AnomalyRules.acceptClientSnapshot(original);
        }
    }

    private static void summonCases(TestContext test, ServerPlayerEntity owner) throws Exception {
        var target=pig(owner,0,4);
        cast(owner,"summon_lunar_spirit",5);
        var spirits=owner.getServerWorld().getEntitiesByClass(LunarSpiritEntity.class,owner.getBoundingBox().expand(8),
                spirit -> owner.getUuid().equals(spirit.getOwnerUuid()));
        ENTITIES.addAll(spirits);
        check(spirits.size()==3,"Real fifth-level summon creates three spirits");
        var spirit=spirits.get(0);
        var snapshot=spirit.ssca$summonAnomaly();
        check(snapshot!=null && snapshot.level()==5 && snapshot.element().equals("summon") && snapshot.amount()==40,
                "Real summon snapshots fifth-level bolt accumulation");
        check(spirits.stream().allMatch(entity -> snapshot.equals(entity.ssca$summonAnomaly())),"All spirits share the cast UUID");
        var nbt=spirit.writeNbt(new NbtCompound());
        var restored=new LunarSpiritEntity(SscAddon.LUNAR_SPIRIT_ENTITY,owner.getWorld());
        restored.readNbt(nbt);ENTITIES.add(restored);
        check(snapshot.equals(restored.ssca$summonAnomaly()),"Spirit NBT preserves the creating cast and amount");
        var legacyNbt=nbt.copy();legacyNbt.remove("SscaSummonAnomaly");
        var legacy=new LunarSpiritEntity(SscAddon.LUNAR_SPIRIT_ENTITY,owner.getWorld());
        legacy.readNbt(legacyNbt);ENTITIES.add(legacy);
        check(legacy.ssca$summonAnomaly()==null,"Legacy spirit NBT never guesses a snapshot from current equipment");

        spirit.shootBoltAt(target);
        var bolts=owner.getServerWorld().getEntitiesByClass(LunarSpiritBoltEntity.class,spirit.getBoundingBox().expand(4),bolt -> bolt.getOwner()==spirit);
        ENTITIES.addAll(bolts);
        check(bolts.size()==1,"Real spirit fires one labelled bolt");
        var bolt=bolts.get(0);
        check(snapshot.equals(((SpellAnomalyHits.ProjectileData)bolt).ssca$anomaly()),"Launched bolt copies the summoning snapshot");
        var newOwner=player(test);
        spirit.setOwnerUuid(newOwner.getUuid());
        check(spirit.ssca$summonAnomaly()==null,"Ownership transfer clears old future-shot identity");
        check(snapshot.equals(((SpellAnomalyHits.ProjectileData)bolt).ssca$anomaly()),"Already launched bolt keeps its original identity after transfer");
        var resolve=LunarSpiritBoltEntity.class.getDeclaredMethod("resolveOwnerPlayer");resolve.setAccessible(true);
        check(resolve.invoke(bolt)==owner,"Already launched bolt resolves its original player for damage attribution");
        check(!spirit.writeNbt(new NbtCompound()).contains("SscaSummonAnomaly"),"Transferred spirit removes obsolete NBT identity");

        var boltNbt=bolt.writeNbt(new NbtCompound());bolt.discard();
        var restoredBolt=new LunarSpiritBoltEntity(owner.getWorld(),spirit);restoredBolt.readNbt(boltNbt);ENTITIES.add(restoredBolt);
        check(snapshot.equals(((SpellAnomalyHits.ProjectileData)restoredBolt).ssca$anomaly()),"Projectile NBT keeps original owner, level, amount and cast UUID");
        check(resolve.invoke(restoredBolt)==owner,"Restored projectile still attributes damage to original owner");
        HITS.clear();collide(restoredBolt,target);AnomalyManager.drain(owner.getServer());
        check(target.getHealth()<200 && count(target,"summon_lunar_spirit_bolt")==1,"Actual restored bolt damage reports one source hit");
        near(40,meter(target,"summon"),"Real bolt impact accumulates its captured fifth-level value");
        clearTargets();

        cast(owner,"summon_lunar_spirit",1);
        var firstLevel=owner.getServerWorld().getEntitiesByClass(LunarSpiritEntity.class,owner.getBoundingBox().expand(8),
                entity -> owner.getUuid().equals(entity.getOwnerUuid()));
        ENTITIES.addAll(firstLevel);
        check(firstLevel.size()==1 && firstLevel.get(0).ssca$summonAnomaly().level()==1
                && firstLevel.get(0).ssca$summonAnomaly().amount()==25,"Real first-level summon snapshots twenty-five independently of previous fifth-level cast");
        clearTargets();

        // The Neo summon has no approved numeric source value, but remains identifiable as a summon.
        var blueprint=new Blueprint("summon probe",Core.SUMMON,Carrier.AREA,List.of(),Core.SUMMON,1,0,8);
        var quote=NeoRules.quote(blueprint,0);var session=new Session(blueprint,quote);session.complete=true;session.paid=quote.actual();
        var record=new NeoState.Experiment();record.blueprint=blueprint;record.dimension=owner.getWorld().getRegistryKey().getValue().toString();
        Vec3d at=owner.getPos().add(3,0,0);
        var active=new NeoRuntime.Active(session,record,ItemStack.EMPTY,Hand.MAIN_HAND,new NeoEffects.Anchor(at,null),new Random(1));
        active.anomaly=identity(owner,"summon_lunar_spirit","summon",3,0);
        var summon=NeoEffects.class.getDeclaredMethod("summon",ServerPlayerEntity.class,NeoRuntime.Active.class,Vec3d.class,double.class);
        summon.setAccessible(true);
        check((boolean)summon.invoke(null,owner,active,at,12),"Actual Neo summon creation succeeds");
        var wolves=owner.getServerWorld().getEntitiesByClass(WolfEntity.class,owner.getBoundingBox().expand(8),wolf -> owner.getUuid().equals(wolf.getOwnerUuid()));
        ENTITIES.addAll(wolves);check(wolves.size()==1,"Neo source creates one real wolf");
        var wolf=wolves.get(0);var mark=((SpellAnomalyHits.SummonedData)wolf).ssca$summonAnomaly();
        check(mark!=null && mark.amount()==0 && mark.element().equals("summon") && mark.casterUUID().equals(owner.getUuid()),"Neo wolf is tagged as zero-amount summon damage");
        var wolfNbt=wolf.writeNbt(new NbtCompound());
        var savedWolf=new WolfEntity(EntityType.WOLF,owner.getWorld());savedWolf.readNbt(wolfNbt);ENTITIES.add(savedWolf);
        check(mark.equals(((SpellAnomalyHits.SummonedData)savedWolf).ssca$summonAnomaly()),"Generic summon mark survives real wolf NBT");
        clearTargets();
    }

    private static void phase(ServerPlayerEntity owner,int elapsed) throws Exception {
        var fields=map(DomainManager.class,"FIELDS");Object old=fields.get(owner.getUuid());
        var type=old.getClass();var components=type.getRecordComponents();
        Class<?>[] types=Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values=new Object[components.length];
        for (int i=0;i<components.length;i++) {
            var getter=components[i].getAccessor();getter.setAccessible(true);
            values[i]=components[i].getName().equals("startTick")?owner.getServer().getTicks()-elapsed:getter.invoke(old);
        }
        var constructor=type.getDeclaredConstructor(types);constructor.setAccessible(true);
        fields.put(owner.getUuid(),constructor.newInstance(values));
        var tick=DomainManager.class.getDeclaredMethod("tick",MinecraftServer.class);tick.setAccessible(true);tick.invoke(null,owner.getServer());
        AnomalyManager.drain(owner.getServer());
    }

    private static void domainCases(TestContext test, ServerPlayerEntity owner) throws Exception {
        double radius=DomainRules.innerRadius();
        var inside=pig(owner,2,0);var edge=pig(owner,radius,0);var beyond=pig(owner,radius+.2,0);
        var corner=pig(owner,radius-.1,radius-.1);var friendly=pig(owner,3,0);var protectedTarget=pig(owner,4,0);
        var scoreboard=owner.getScoreboard();var team=scoreboard.addTeam("anomaly_"+owner.getUuid().toString().substring(0,8));
        scoreboard.addPlayerToTeam(owner.getEntityName(),team);scoreboard.addPlayerToTeam(friendly.getEntityName(),team);
        owner.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+protectedTarget.getUuid());
        var creative=player(test);creative.refreshPositionAndAngles(owner.getX()+6,owner.getY(),owner.getZ(),0,0);
        String original=AnomalyRules.snapshotJson();
        try {
            HITS.clear();var context=identity(owner,"domain","space",4,0);
            DomainManager.begin(owner);SpellAnomalyHits.with(context,()->DomainManager.activate(owner));AnomalyManager.drain(owner.getServer());
            near(200,meter(inside,"space"),"Domain activation adds initial two hundred");
            near(200,meter(edge,"space"),"Exact spherical boundary is included");
            near(0,meter(beyond,"space"),"Beyond spherical radius is excluded");
            near(0,meter(corner,"space"),"Local box corner outside sphere is excluded");
            near(0,meter(friendly,"space"),"Domain respects scoreboard friendship");
            near(0,meter(protectedTarget,"space"),"Domain respects caster whitelist");
            check(count(owner,"domain")==0,"Domain never accumulates on its caster");
            near(0,meter(creative,"space"),"Domain excludes actual creative player target in explicit whitelist mode");
            var initial=HITS.stream().filter(hit -> hit.target().equals(inside.getUuid())).findFirst().orElseThrow().context();
            phase(owner,39);near(200,meter(inside,"space"),"No domain pressure before forty ticks");
            phase(owner,40);near(250,meter(inside,"space"),"Fortieth tick adds fifty");
            var changed=config();amount(changed,"domain_pulse",10);changed.addProperty("domain_pulse_ticks",1);new AnomalyRules().reload(resource(changed.toString()));
            beyond.refreshPositionAndAngles(owner.getX()+5,owner.getY(),owner.getZ(),0,0);
            phase(owner,79);near(250,meter(inside,"space"),"Existing field retains original forty-tick interval after reload");
            phase(owner,80);near(300,meter(inside,"space"),"Existing field retains captured fifty-point pulse after reload");
            near(50,meter(beyond,"space"),"Entering active domain only receives the next pulse");
            var domainHits=HITS.stream().filter(hit -> hit.target().equals(inside.getUuid())).toList();
            check(domainHits.size()==3 && domainHits.stream().allMatch(hit -> hit.context().castUUID().equals(initial.castUUID()) && hit.context().level()==4),"Initial and periodic production hits retain the one domain cast UUID/level");
        } finally {
            DomainManager.remove(owner);owner.removeScoreboardTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+protectedTarget.getUuid());
            scoreboard.removeTeam(team);new AnomalyRules().reload(resource(original));clearTargets();
        }

        // Exercise the exact PvP predicate used by domain pressure without introducing an unconnected player into entity tracking.
        var other=new ServerPlayerEntity(owner.getServer(),owner.getServerWorld(),new GameProfile(UUID.randomUUID(),"anomaly-pvp"));
        other.refreshPositionAndAngles(owner.getX()+2,owner.getY(),owner.getZ(),0,0);
        boolean previous=owner.getServer().isPvpEnabled();
        String explicitListEntry=WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+UUID.randomUUID();
        try {
            owner.getServer().setPvpEnabled(true);
            check(!FormSpellStyle.legalTarget(owner,other),"Empty default whitelist protects players even when PvP is enabled");
            owner.addCommandTag(explicitListEntry);
            check(!WhitelistUtils.isProtected(owner,other),"Unlisted player becomes hostile only in explicit whitelist mode");
            owner.getServer().setPvpEnabled(false);check(!FormSpellStyle.legalTarget(owner,other),"Domain target predicate rejects PvP when disabled");
            owner.getServer().setPvpEnabled(true);check(FormSpellStyle.legalTarget(owner,other),"Domain target predicate admits hostile players when PvP enabled");
        } finally { owner.removeScoreboardTag(explicitListEntry);owner.getServer().setPvpEnabled(previous); }
    }

    private static void bind(ServerPlayerEntity player) {
        AnomalyEffects.activate("space",player,identity(player,"domain","space",1,500));
        AnomalyEffects.refresh(player);
        check(AnomalyEffects.teleportBlocked(player),"Production space anomaly activates teleport rejection");
    }

    private static void spaceCases(TestContext test, ServerPlayerEntity player) throws Exception {
        bind(player);Vec3d origin=player.getPos();
        check(!SpellRegistry.get("space_blink").canCast(player),"Blink preflight rejects bound caster");
        double burden=NeoRuntime.data(player).burden;int[] callbacks={0};
        check(!SpellChannelManager.start(player,SpellRegistry.get("space_blink"),ScrollData.create("space_blink"),1,true,8881,20,100,()->true,
                target->callbacks[0]++,cooldown->callbacks[0]++),"Shared release-style channel preflight rejects bound teleport");
        check(callbacks[0]==0 && !SpellChannelManager.isCasting(player),"Rejected teleport never opens channel or settles cooldown callback");
        near(burden,NeoRuntime.data(player).burden,"Rejected teleport has no collapse payment");
        cast(player,"space_blink",1);near(0,player.getPos().distanceTo(origin),"Blink execution also rejects binding acquired after targeting");
        cast(player,"space_recall",1);near(0,player.getPos().distanceTo(origin),"Recall execution also rejects binding");
        var mancianima=NeoFormCastingProbe.player(test,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,100);
        var snowFox=NeoFormCastingProbe.player(test,FormIdentifiers.SNOW_FOX_SP,100);
        try {
            bind(mancianima);bind(snowFox);
            check(!MancianimaTeleport.execute(mancianima,(byte)0),"Correct Mancianima form rejects binding before costs");
            check(!SnowFoxSpTeleportAttack.execute(snowFox),"Correct snow fox form rejects binding before costs");
        } finally { AnomalyManager.clear(mancianima);AnomalyManager.clear(snowFox); }
        cast(player,"space_stride",1);check(player.hasStatusEffect(StatusEffects.JUMP_BOOST),"Space binding preserves ordinary jump enhancement");

        var scroll=ScrollData.create("pocket_space");UUID room=PocketSpaceStorage.bind(player.getServer(),scroll);
        PocketSpaceStorage.findRoom(player.getServer(),room).finishGeneration();
        try {
            check(!PocketSpaceManager.canEnter(player),"Pocket preflight rejects bound caster");
            PocketSpaceManager.start(player,room,20);
            check(!map(PocketSpaceManager.class,"CHANNELS").containsKey(player.getUuid()),"Pocket actual channel start rejects binding");
            PocketSpaceManager.enterNow(player,room);
            near(0,player.getPos().distanceTo(origin),"Pocket execution does not move bound caster");
            AnomalyEffects.clear(player);PocketSpaceManager.start(player,room,20);
            check(map(PocketSpaceManager.class,"CHANNELS").containsKey(player.getUuid()),"Same valid room starts when binding clears");
            check(PocketSpaceManager.cancelByKey(player),"Production pocket cancellation cleans up control fixture");
            bind(player);
            var visit=PocketSpaceStorage.visit(player);Vec3d safety=origin.add(1,0,0);
            visit.begin(room,player.getWorld().getRegistryKey(),safety,0,0);
            var leave=PocketSpaceManager.class.getDeclaredMethod("leave",ServerPlayerEntity.class,PocketSpaceStorage.Visit.class);leave.setAccessible(true);leave.invoke(null,player,visit);
            near(0,player.getPos().distanceTo(safety),"Forced pocket safety exit remains available while bound");
            check(!visit.active(),"Forced safety return clears the stored pocket visit");
        } finally {
            PocketSpaceManager.cancelByKey(player);PocketSpaceStorage.visit(player).clear();
            PocketSpaceStorage.unbind(player.getServer(),PocketSpaceStorage.findRoom(player.getServer(),room));
            AnomalyEffects.clear(player);
        }
    }

    private static void statusCases(ServerPlayerEntity player) throws Exception {
        player.setYaw(0);player.setPitch(0);
        var target=pig(player,0,2);
        // Place the target's center on the caster eye line for the real whisper cone.
        target.setPosition(target.getX(),player.getEyeY()-target.getHeight()*.5,target.getZ());
        for (var effect : List.of(StatusEffects.WEAKNESS,StatusEffects.SLOWNESS,StatusEffects.MINING_FATIGUE))
            target.addStatusEffect(new StatusEffectInstance(effect,1000,5));
        HITS.clear();cast(player,"dread_whisper",1);AnomalyManager.drain(player.getServer());
        check(count(target,"dread_whisper")==1,"Stronger existing weakness and slowness still count one whisper hit");
        near(200,meter(target,"curse"),"Existing stronger debuffs still accept the configured whisper accumulation");
        cast(player,"dread_whisper",1);AnomalyManager.drain(player.getServer());
        check(count(target,"dread_whisper")==2,"A repeated legal whisper counts once again rather than once per debuff");
        near(400,meter(target,"curse"),"Repeated stronger-status hits both accumulate");
        HITS.clear();cast(player,"void_erosion",1);AnomalyManager.drain(player.getServer());
        check(count(target,"void_erosion")==1,"Stronger fatigue and weakness produce one erosion hit");
        near(190,meter(target,"void"),"Existing stronger erosion debuffs still accumulate");
        clearTargets();
        var immune=spawn(player,new PigEntity(EntityType.PIG,player.getWorld()) {
            @Override public boolean canHaveStatusEffect(StatusEffectInstance status) {
                return status.getEffectType()!=StatusEffects.WEAKNESS && status.getEffectType()!=StatusEffects.SLOWNESS
                        && status.getEffectType()!=StatusEffects.MINING_FATIGUE && super.canHaveStatusEffect(status);
            }
        },0,2);
        immune.setPosition(immune.getX(),player.getEyeY()-immune.getHeight()*.5,immune.getZ());
        HITS.clear();cast(player,"dread_whisper",1);cast(player,"void_erosion",1);AnomalyManager.drain(player.getServer());
        check(count(immune,"dread_whisper")==0 && count(immune,"void_erosion")==0,"Real status-immune target emits no rejected debuff hit");
        near(0,meter(immune,"curse"),"Status immunity leaves curse meter empty");
        near(0,meter(immune,"void"),"Status immunity leaves void meter empty");
        clearTargets();
    }

    private static void run(TestContext test) throws Exception {
        checks=0;HITS.clear();
        var oldResolver=SpellAnomalyHits.amountResolver();boolean previous=AnomalyManager.setEnabledForTesting(true);
        var owner=player(test);
        SpellAnomalyHits.amountResolver(AnomalyRules::amount);
        try (var listener=SpellAnomalyHits.register((target,context)->HITS.add(new Hit(target.getUuid(),context)))) {
            configCases();summonCases(test,owner);domainCases(test,owner);spaceCases(test,owner);statusCases(owner);
            var report=Map.of("checks",checks,"sources",List.of("config_atomic_reload","moon_spirit_and_projectile_nbt","neo_wolf_nbt",
                    "domain_activation_and_pulses","space_preflight_execution_safety","zero_damage_status_acceptance"),"runtime","isolated Minecraft server world");
            Path output=Path.of("build","reports","anomaly-sources-world.json");Files.createDirectories(output.getParent());Files.writeString(output,NeoState.JSON.toJson(report));
            System.out.println("ANOMALY_SOURCES_WORLD_PASS checks="+checks);
            test.complete();
        } finally {
            clearTargets();DomainManager.remove(owner);AnomalyManager.clear(owner);
            SpellAnomalyHits.amountResolver(oldResolver);AnomalyManager.setEnabledForTesting(previous);
        }
    }
}
