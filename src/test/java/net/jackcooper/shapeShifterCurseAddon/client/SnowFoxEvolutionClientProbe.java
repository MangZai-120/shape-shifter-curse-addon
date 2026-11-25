package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxMotion;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxState;
import net.jackcooper.shapeShifterCurseAddon.client.evolution.EvolutionScreen;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

public final class SnowFoxEvolutionClientProbe {
    private static int phase, ticks, timer;
    private static boolean passed;
    private static Throwable failure;
    private static final long start=System.nanoTime();
    private static void check(boolean ok,String why) { if (!ok) throw new AssertionError(why); }
    private static void next(int step) { phase=step; timer=0; }
    private static void capture(MinecraftClient mc,String name) throws Exception {
        try (var image=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) {
            image.writeTo(Path.of("snow-fox-"+name+".png"));
        }
    }
    public static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (passed || failure!=null) return;
            try {
                check(System.nanoTime()-start<115_000_000_000L,"Timed out at phase "+phase);
                if (mc.getOverlay()!=null) return;
                if (phase==0 && mc.currentScreen instanceof TitleScreen) {
                    next(1);
                    mc.options.pauseOnLostFocus=false;
                    mc.options.getViewDistance().setValue(4);
                    mc.options.getMaxFps().setValue(60);
                    mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                    var data=new DataConfiguration(new DataPackSettings(List.of("vanilla","fabric"),List.of()),DataConfiguration.SAFE_MODE.enabledFeatures());
                    mc.createIntegratedServerLoader().createAndStart("SnowFox-"+System.currentTimeMillis(),
                            new LevelInfo("Snow Fox",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),data),
                            new GeneratorOptions(73L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (mc.player==null || mc.world==null || mc.getServer()==null || ++ticks<50) return;
                ++timer;
                if (phase==4) mc.options.forwardKey.setPressed(true);
                if (phase>=4) ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind.setPressed(false);
                if (phase>=7) ShapeShifterCurseFabricClient.useActiveSkill1PowerKeybind.setPressed(false);
                var state=SnowFoxMotion.STATE.get(mc.player);
                if (phase>=4 && mc.currentScreen!=null) mc.setScreen(null);
                if (phase>=4 && timer%45==0) {
                    int stage=phase;
                    var server=mc.getServer();
                    server.execute(() -> {
                        var pl=server.getPlayerManager().getPlayerList().get(0);
                        var ss=SnowFoxMotion.STATE.get(pl);
                        System.out.println("SNOW_FOX_CLIENT_STAGE "+stage+" phase="+ss.phase()+" mass="+ss.mass()
                                +" position="+pl.getPos()+" ready="+net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isPlayerReady(pl)
                                +" blocked="+net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard.blocked(pl)
                                +" cd="+net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ready(pl,SnowFoxMotion.ROLL.toString())
                                +" talent="+net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionTalents.isActive(pl,"rolling_charge"));
                    });
                }
                if (phase==1) {
                    if (mc.currentScreen!=null) mc.setScreen(null);
                    mc.options.setPerspective(Perspective.FIRST_PERSON);
                    mc.getServer().execute(() -> {
                        var p=mc.getServer().getPlayerManager().getPlayerList().get(0);
                        var world=p.getServerWorld();
                        FormUtils.setForm(p,RegPlayerForms.getPlayerForm(FormIdentifiers.UPGRADE_SNOW_FOX));
                        EvolutionManager.tickPlayer(p);
                        EvolutionManager.unlockAll(p);
                        world.setTimeOfDay(1000);
                        for (var b:BlockPos.iterate(new BlockPos(-8,80,-8),new BlockPos(8,80,52)))
                            world.setBlockState(b,Blocks.STONE_BRICKS.getDefaultState());
                        p.networkHandler.requestTeleport(.5,81,.5,0,18);
                    });
                    next(2);
                } else if (phase==2 && timer>30) {
                    mc.inGameHud.getChatHud().clear(false);
                    mc.setScreen(new EvolutionScreen(null));
                    next(3);
                } else if (phase==3 && timer>8) {
                    capture(mc,"tree");mc.setScreen(null);
                    mc.player.setYaw(0);mc.player.setPitch(12);
                    ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind.setPressed(true);
                    next(4);
                } else if (phase==4 && state.phase()==SnowFoxState.Phase.IDLE && timer%45==0) {
                    ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind.setPressed(true);
                } else if (phase==4 && state.phase()==SnowFoxState.Phase.SHELL && state.mass()>=50) {
                    check(mc.options.getPerspective()==Perspective.THIRD_PERSON_BACK,"Automatic third-person");
                    check(mc.gameRenderer.getCamera().getPos().distanceTo(mc.player.getEyePos()) > 7, "Frames the full five-block shell");
                    check(Math.abs(mc.player.getPitch()-12)<1, "Steering preserves the chosen view angle");
                    mc.options.forwardKey.setPressed(false);
                    capture(mc,"roll");
                    ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind.setPressed(true);
                    next(5);
                } else if (phase==5 && state.phase()==SnowFoxState.Phase.IDLE && timer>9) {
                    check(mc.options.getPerspective()==Perspective.FIRST_PERSON,"Restores previous view");
                    mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    mc.getServer().execute(() -> {
                        var p=mc.getServer().getPlayerManager().getPlayerList().get(0);
                        p.getAbilities().flying=true; p.sendAbilitiesUpdate();
                        p.networkHandler.requestTeleport(.5,87,12.5,0,48);
                    });
                    next(6);
                } else if (phase==6 && timer>8) {
                    ShapeShifterCurseFabricClient.useActiveSkill1PowerKeybind.setPressed(true);next(7);
                } else if (phase==7 && state.phase()==SnowFoxState.Phase.CHARGE) {
                    next(8);
                } else if (phase==8 && state.phase()==SnowFoxState.Phase.IDLE) {
                    next(9);
                } else if (phase==9 && timer<=28) {
                    if (timer%2==0) capture(mc,"wave-"+timer);
                    if (timer==13) {
                        check(Math.abs(mc.player.getPitch()-48)<1, "Dive preserves look direction");
                        capture(mc,"impact");
                    }
                    if (timer<28) return;
                    passed=true;
                    System.out.println("SNOW_FOX_CLIENT_PASS third-person, mass growth, release, first-person restoration, dive and ice front");
                    mc.scheduleStop();
                }
            } catch(Throwable e) { failure=e; e.printStackTrace(); mc.scheduleStop(); }
        });
    }
    public static void verifyResult() { if(failure!=null) throw new AssertionError(failure); check(passed,"Incomplete client probe"); }
}
