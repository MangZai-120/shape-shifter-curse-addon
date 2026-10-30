package net.ssca.neomagic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.ability.*;
import net.jackcooper.shapeShifterCurseAddon.client.*;
import net.jackcooper.shapeShifterCurseAddon.entity.GhostCatEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import java.nio.file.*;
import java.util.*;

/** Disposable integrated world; real victim client receives the actual ability packets and renders them. */
public final class NeoNightmareLiveProbe {
    private static volatile int ticks,stage;
    private static volatile UUID casterId,ghostId;
    private static volatile boolean finished,attacked,sourceChecked;
    private static volatile Throwable failure;
    private static boolean starting;
    private static int checks,nearHidden,nearVisible,farHidden,revealed;
    private static boolean fogSeen,firstGhostSeen,secondGhostSeen,catSeen;
    private static ServerPlayerEntity caster,target;
    private static Vec3d origin;
    private static float beforeGhost,afterDouble,slowBase;
    private static final Set<String> shots=new HashSet<>();
    private static final Map<String,Integer> shotRequests=new HashMap<>();
    private static final List<Float> nearHeartbeats=new ArrayList<>(),farHeartbeats=new ArrayList<>();
    private static final Set<Long> heartbeatTimes=new HashSet<>();
    private static boolean duplicateHeartbeat;
    private static int clientFrame;
    private static volatile boolean dreamDebuffSeen;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-4,why+": "+a+" != "+b);}
    public static void install(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            clientFrame++;
            if(mc.getOverlay()!=null)return;
            if(!starting&&mc.currentScreen instanceof TitleScreen){starting=true;mc.options.pauseOnLostFocus=false;mc.options.getViewDistance().setValue(6);mc.options.getMaxFps().setValue(120);mc.options.language="zh_cn";
                mc.getSoundManager().registerListener((sound,set)->{if(!sound.getId().equals(net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT.getId())||stage!=2)return;if(!heartbeatTimes.add(mc.world.getTime()))duplicateHeartbeat=true;if(ticks>=225&&ticks<280)nearHeartbeats.add(sound.getVolume());if(ticks>=285&&ticks<320)farHeartbeats.add(sound.getVolume());});
                String name="Nightmare-Acceptance-"+System.currentTimeMillis();
                mc.createIntegratedServerLoader().createAndStart(name,new LevelInfo(name,GameMode.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),DataConfiguration.SAFE_MODE),new GeneratorOptions(20261030L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if(mc.world==null||mc.player==null||finished)return;
            if(ticks>=100&&ticks<140&&mc.player.hasStatusEffect(StatusEffects.WEAKNESS)){
                var debuff=mc.player.getStatusEffect(StatusEffects.WEAKNESS);
                if(debuff.getDuration()>0&&debuff.getDuration()<=27&&debuff.getAmplifier()==0)dreamDebuffSeen=true;
            }
            if(mc.currentScreen!=null)mc.setScreen(null);
            if(stage==2&&ticks>=225&&ticks<280){
                fogSeen|=NightmareFearClient.getFogStrength(0)>.95f;
                boolean hidden=NightmareFearClient.isHidden(casterId);if(hidden){nearHidden++;shot(mc,"02-near-hidden.png");}else{nearVisible++;shot(mc,"03-near-visible.png");}
            }
            if(stage==2&&ticks>=285&&ticks<320){if(NightmareFearClient.isHidden(casterId))farHidden++;shot(mc,"04-far-hidden.png");}
            if(stage==2&&ticks>=335&&ticks<350&&!NightmareFearClient.isHidden(casterId)){revealed++;shot(mc,"05-attack-reveal.png");}
            for(Entity entity:mc.world.getEntities()){
                if(entity instanceof CreeperEntity&&entity.getUuid().equals(ghostId)&&NightmareSpookClient.isGhostVisible(entity.getUuid())){
                    check(!entity.isInvisibleTo(mc.player),"Actual victim client reveals its own ghost");face(mc,entity.getEyePos());
                    if(stage==1){firstGhostSeen=true;shot(mc,"01-ghost-expiry.png");}
                    if(stage==3&&!attacked){secondGhostSeen=true;if(shot(mc,"06-ghost-before-hit.png")){attacked=true;mc.interactionManager.attackEntity(mc.player,entity);mc.player.swingHand(Hand.MAIN_HAND);}}
                }
                if(entity instanceof GhostCatEntity&&NightmareSpookClient.isGhostVisible(entity.getUuid())){catSeen=true;face(mc,entity.getEyePos());shot(mc,"07-cat-pounce.png");}
            }
            if(ticks>=535)shot(mc,"08-after-fear.png");
        }catch(Throwable error){fail(error);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{try{
            if(finished||server.getPlayerManager().getPlayerList().isEmpty())return;
            ticks++;if(ticks==70){
                target=server.getPlayerManager().getPlayerList().get(0);origin=target.getPos();target.changeGameMode(GameMode.SURVIVAL);target.getAbilities().invulnerable=false;target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);target.setHealth(200);target.setNoGravity(true);target.setYaw(0);target.setPitch(0);
                server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,server);server.getOverworld().setTimeOfDay(6000);
                var function=new TestFunction("nightmare","live","ssca_neomagic:empty",800,0,true,t->{});
                var context=new TestContext(new GameTestState(function,BlockRotation.NONE,target.getServerWorld()));caster=context.createMockCreativeServerPlayerInWorld();casterId=caster.getUuid();caster.setNoGravity(true);caster.refreshPositionAndAngles(origin.x,origin.y,origin.z+8,180,0);
                RegPlayerFormComponent.PLAYER_FORM.get(caster).nowForm=RegPlayerForms.playerForms.get(FormIdentifiers.WILD_CAT_NIGHTMARE);caster.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+caster.getUuid());
                net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.applyLayer(caster,new Pair<>(new Identifier("origins","origin"),new Identifier("my_addon","form_wild_cat_nightmare")),false);
                RegPlayerFormComponent.PLAYER_FORM.sync(caster);net.onixary.shapeShifterCurseFabric.integration.origins.registry.ModComponents.ORIGIN.sync(caster);
                slowBase=(float)target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            }
            if(ticks==100){target.timeUntilRegen=0;check(target.damage(target.getDamageSources().playerAttack(caster),6),"First real PvP damage accepted");target.timeUntilRegen=0;check(target.damage(target.getDamageSources().playerAttack(caster),6),"Second real PvP damage accepted");check(NightmareDreamManager.collectDreamTargets(caster,target.getWorld().getTime()).contains(target),"Real player reaches dream threshold");
                float hp=target.getHealth();target.timeUntilRegen=0;check(target.damage(net.jackcooper.shapeShifterCurseAddon.spell.SpellDamageSource.of(target.getDamageSources(),caster),2),"Real player accepts native spell damage");near(hp-target.getHealth(),2.7,"Own dreamed player takes thirty-five percent more spell damage");
                check(net.jackcooper.shapeShifterCurseAddon.spell.NightmareSpellLink.with(caster,()->target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,20,0),caster)),"Actual player receives spell debuff");check(target.getStatusEffect(StatusEffects.WEAKNESS).getDuration()==27,"Actual player debuff duration grows thirty-five percent");beforeGhost=target.getHealth();}
            if(ticks==105){check(NightmareSpookManager.execute(caster),"Player spook creates real ghost");ghostId=ghost(caster).getUuid();stage=1;}
            if(ticks==175){check(dreamDebuffSeen,"Real client received extended debuff through ordinary status synchronization");check(firstGhostSeen,"Ghost was visible in real victim client");check(caster.getServerWorld().getEntity(ghostId)==null,"Unattacked ghost expires");near(target.getHealth(),beforeGhost,"Natural ghost explosion has no real damage");}
            if(ticks==180){check(NightmareFearManager.execute(caster),"Actual fear accepts player target");near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),slowBase*.8,"Player receives twenty-percent slow");check(!caster.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON,100,0),target),"Dreaming player cannot debuff its nightmare caster");stage=2;}
            if(ticks==280)caster.refreshPositionAndAngles(origin.x,origin.y,origin.z+22,180,0);
            if(ticks==330){caster.refreshPositionAndAngles(origin.x,origin.y,origin.z+8,180,0);float hp=target.getHealth();target.timeUntilRegen=0;target.damage(target.getDamageSources().playerAttack(caster),1);near(hp-target.getHealth(),2,"First qualifying fear hit doubles once");afterDouble=target.getHealth();}
            if(ticks==365){target.timeUntilRegen=0;target.damage(target.getDamageSources().playerAttack(caster),1);near(afterDouble-target.getHealth(),1,"Second fear hit uses ordinary damage");beforeGhost=target.getHealth();}
            if(ticks==370){check(NightmareSpookManager.execute(caster),"Second player spook accepted");ghostId=ghost(caster).getUuid();stage=3;}
            if(stage==3&&!sourceChecked&&target.getHealth()<beforeGhost){check(target.getRecentDamageSource()!=null&&target.getRecentDamageSource().getAttacker()==caster,"Ghost cat damage retains the original nightmare caster, not the victim");sourceChecked=true;}
            if(ticks==470){check(attacked&&secondGhostSeen,"Real victim client attacked its ghost via C2S");check(catSeen,"Real client received and rendered the ghost cat");near(beforeGhost-target.getHealth(),12,"Ghost cat delivers twelve damage to player");check(sourceChecked,"Ghost cat attribution checked at the actual hit");}
            if(ticks==530){check(!NightmareFearManager.isFeared(target.getUuid(),target.getWorld().getTime()),"Fear ends after fifteen seconds");check(NightmareFearManager.isDreamImmune(target.getUuid(),target.getWorld().getTime()),"Player enters post-fear dream immunity");near(target.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),slowBase,"Fear slow removed");}
            if(ticks==570){check(fogSeen,"Victim client receives full pink fog");check(nearHidden>0&&nearVisible>0,"Near visibility alternates in real client");check(farHidden>0,"Far nightmare stays hidden in real client");check(revealed>0,"Attack reveal arrives in real client");MinecraftClient.getInstance().execute(()->{try{check(!NightmareFearClient.isFeared()&&NightmareFearClient.getFogStrength(0)==0,"Client clears fear and fog");check(!nearHeartbeats.isEmpty()&&!farHeartbeats.isEmpty(),"Actual victim receives the server heartbeat in both distance regimes");check(Collections.min(nearHeartbeats)>Collections.max(farHeartbeats),"Actual heartbeat volume rises as the nightmare approaches");check(!duplicateHeartbeat,"A heartbeat is not delivered twice in one client world tick");finished=true;var report=new LinkedHashMap<String,Object>();report.put("checks",checks);report.put("near_hidden",nearHidden);report.put("near_visible",nearVisible);report.put("far_hidden",farHidden);report.put("attack_reveal",revealed);report.put("ghost_seen",firstGhostSeen&&secondGhostSeen);report.put("cat_seen",catSeen);report.put("near_heartbeat_volumes",nearHeartbeats);report.put("far_heartbeat_volumes",farHeartbeats);report.put("screenshots",shots);Files.writeString(Path.of("nightmare-result.json"),NeoState.JSON.toJson(report));System.out.println("NEO_NIGHTMARE_LIVE_PASS checks="+checks);MinecraftClient.getInstance().scheduleStop();}catch(Throwable e){fail(e);}});}
        }catch(Throwable error){fail(error);}});
    }
    private static CreeperEntity ghost(ServerPlayerEntity caster){return caster.getServerWorld().getEntitiesByClass(CreeperEntity.class,caster.getBoundingBox().expand(40),e->e.getCommandTags().contains("ssca_ghost_creeper")).stream().findFirst().orElseThrow(()->new AssertionError("No ghost entity"));}
    private static void face(MinecraftClient mc,Vec3d point){Vec3d d=point.subtract(mc.player.getEyePos());mc.player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private static boolean shot(MinecraftClient mc,String name)throws Exception{if(shots.contains(name))return true;int due=shotRequests.computeIfAbsent(name,id->clientFrame+2);if(clientFrame<due)return false;shots.add(name);Files.createDirectories(Path.of("screenshots"));try(var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){image.writeTo(Path.of("screenshots",name));}return true;}
    private static void fail(Throwable error){if(failure!=null)return;failure=error;finished=true;error.printStackTrace();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}
    public static void verifyResult(){if(failure!=null)throw new AssertionError("Nightmare live acceptance failed",failure);if(!finished)throw new AssertionError("Client closed before acceptance completed");}
}
