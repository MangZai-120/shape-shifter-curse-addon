package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.LaserBeamEntity;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import java.util.List;

public final class FluorescentLaserClientProbe {
    private static final long start = System.nanoTime();
    private static boolean launching, spawned, moved, sawLag, passed;
    private static volatile Throwable failure;
    private static int ticks;
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    public static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (passed || failure != null) return;
            try {
                check(System.nanoTime()-start < 200_000_000_000L, "Timed out waiting for laser tracking");
                if (mc.getOverlay()!=null) return;
                if (!launching && mc.currentScreen instanceof TitleScreen) {
                    launching=true;
                    mc.options.pauseOnLostFocus=false;
                    mc.options.getViewDistance().setValue(4);
                    mc.options.getMaxFps().setValue(60);
                    var data=new DataConfiguration(new DataPackSettings(List.of("vanilla","fabric"),List.of()),DataConfiguration.SAFE_MODE.enabledFeatures());
                    mc.createIntegratedServerLoader().createAndStart("LaserAim-"+System.currentTimeMillis(),
                            new LevelInfo("Laser aim test",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),data),
                            new GeneratorOptions(73L,false,false), r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                }
                if (mc.world==null || mc.player==null || mc.getServer()==null || ++ticks < 40) return;
                if (mc.currentScreen!=null) mc.setScreen(null);
                if (!spawned) {
                    spawned=true;
                    mc.player.setYaw(0);mc.player.setPitch(0);
                    mc.getServer().execute(()-> {
                        try {
                            var p=mc.getServer().getPlayerManager().getPlayerList().get(0);
                            FormUtils.setForm(p,RegPlayerForms.playerForms.get(FormIdentifiers.AXOLOTL_FLUORESCENT));
                            p.setYaw(0);p.setPitch(0);p.setHeadYaw(0);
                            check(p.getServerWorld().spawnEntity(new LaserBeamEntity(p.getServerWorld(),p)), "Spawn regular laser");
                        } catch(Throwable e) { failure=e;mc.execute(mc::scheduleStop); }
                    });
                    return;
                }
                for (var entity:mc.world.getEntities()) if (entity instanceof LaserBeamEntity beam && beam.getTrackedOwnerId()==mc.player.getId()) {
                    Vec3d aim=beam.getBeamAim(1);
                    if (aim.lengthSquared()==0 || beam.age<3) return;
                    double beamYaw=Math.toDegrees(Math.atan2(-aim.x,aim.z));
                    if (!moved) {
                        mc.player.setYaw(0);mc.player.setPitch(0);
                        mc.player.changeLookDirection(20,0);
                        check(Math.abs(mc.player.getYaw()-3)<0.01,"Normal mouse sensitivity inside cone");
                        mc.player.changeLookDirection(3000,0);
                        check(Math.abs(mc.player.getYaw()-60)<0.1,"Transformed mouse code stops a fast flick at 60 degrees");
                        mc.player.changeLookDirection(-20,0);
                        check(Math.abs(mc.player.getYaw()-57)<0.1,"Reverse immediately from limit");
                        check(Math.abs(beamYaw)<0.1,"Local camera did not snap beam");
                        moved=true;
                    } else if (!sawLag && beamYaw>4) {
                        check(Math.abs(mc.player.getYaw()-57)<0.2 && beamYaw<20,"Camera holds while tracked server beam turns");
                        sawLag=true;
                    } else if (sawLag && Math.abs(beamYaw-57)<0.01) {
                        check(Math.abs(mc.player.getYaw()-57)<0.2,"Beam settles on the stationary crosshair");
                        float heldYaw = mc.player.getYaw();
                        Vec3d shown = beam.getBeamAim(mc.getTickDelta());
                        mc.player.changeLookDirection(-1800,220);
                        Vec3d look = mc.player.getRotationVector().normalize();
                        double angle = Math.toDegrees(Math.atan2(shown.crossProduct(look).length(),shown.dotProduct(look)));
                        check(Math.abs(angle-60)<0.02,"Real camera hits the diagonal cone boundary");
                        passed=true;
                        System.out.println("FLUORESCENT_LASER_CLIENT_PASS camera="+heldYaw+" beam="+beamYaw+" diagonal="+angle);
                        mc.scheduleStop();
                    }
                    return;
                }
            } catch(Throwable e) {failure=e;e.printStackTrace();mc.scheduleStop();}
        });
    }
    public static void verifyResult() { if(failure!=null)throw new AssertionError(failure);check(passed,"Incomplete laser camera test"); }
}
