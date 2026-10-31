package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.*;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.ssca.neomagic.NeoState;

/** 独立真实客户端：通过服务端物品入口验证 CCA、原版本能预测与两张贴图。 */
public final class InstinctAnchorClientProbe {
    private static boolean starting, releasing;
    private static volatile boolean finished;
    private static volatile Throwable failure;
    private static volatile int ticks;
    private static int clientWait, checks;
    private static final long START = System.nanoTime();
    private static ServerPlayerEntity player;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void use(boolean fix) {
        var item = fix ? SscAddon.INSTINCT_ANCHOR : SscAddon.INSTINCT_RELEASE;
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(item, 2));
        check(item.use(player.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted(), "服务端道具入口成功");
        check(player.getMainHandStack().getCount() == 1, "服务端只扣除一个道具");
    }
    public static void install() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { try {
            if (finished) return;
            if (System.nanoTime()-START > 180_000_000_000L) throw new AssertionError("客户端检查超时");
            if (mc.getOverlay()!=null) return;
            if (!starting && mc.currentScreen instanceof TitleScreen) {
                check(net.minecraft.registry.Registries.ITEM.getIds().stream().noneMatch(id->id.getNamespace().equals("ssca_neomagic")),"药水与试管归入附属命名空间，旧实验物品保持移除");
                check(!net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getGlobalReceivers().contains(new Identifier("ssca_neomagic","state_v2")),"真实客户端不再接收旧研究页协议");
                starting = true; mc.options.pauseOnLostFocus = false;
                mc.options.getViewDistance().setValue(4); mc.options.getMaxFps().setValue(60);
                mc.options.getGuiScale().setValue(3); mc.onResolutionChanged();
                net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client().showInstinctBar=true;
                net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.clientConfig.instinctBarPosType=1;
                net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.clientConfig.instinctBarPosOffsetX=14;
                net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.clientConfig.instinctBarPosOffsetY=16;
                var data = new DataConfiguration(new DataPackSettings(java.util.List.of("vanilla","fabric"), java.util.List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                mc.createIntegratedServerLoader().createAndStart("Anchor-"+System.currentTimeMillis(),
                        new LevelInfo("固定道具客户端检查", GameMode.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), data),
                        new GeneratorOptions(20261031L,false,false),
                        r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if (mc.world==null || mc.player==null || ticks < 100) return;
            if (mc.currentScreen!=null) mc.setScreen(null);
            if (!releasing && InstinctAnchors.locked(mc.player)) {
                if (++clientWait < 15) return;
                check(PlayerFormComponent.COMPONENT.get(mc.player).instinctRate == 0, "真实客户端收到零预测速度");
                for (var item : new net.minecraft.item.Item[]{SscAddon.INSTINCT_ANCHOR,SscAddon.INSTINCT_RELEASE}) {
                    var sprite = mc.getItemRenderer().getModel(new ItemStack(item),mc.world,mc.player,0).getParticleSprite();
                    check(!sprite.getContents().getId().getPath().equals("missingno"), "客户端道具模型贴图存在");
                }
                save(mc,"anchor-locked.png");
                releasing = true; clientWait=0;
                mc.getServer().execute(() -> { try { use(false); } catch (Throwable e) { fail(e); } });
            } else if (releasing && !InstinctAnchors.locked(mc.player)) {
                if (++clientWait < 15) return;
                check(PlayerFormComponent.COMPONENT.get(mc.player).instinctRate > 0, "解除同步到客户端并恢复预测");
                save(mc,"anchor-released.png");
                Files.writeString(Path.of("anchor-client-result.json"), NeoState.JSON.toJson(Map.of("checks",checks,"state_synced",true,"screenshots",java.util.List.of("anchor-locked.png","anchor-released.png"))));
                finished=true; System.out.println("INSTINCT_ANCHOR_CLIENT_PASS checks="+checks); mc.scheduleStop();
            }
        } catch (Throwable e) { fail(e); } });
        ServerTickEvents.END_SERVER_TICK.register(server -> { try {
            if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
            ticks++;
            if (ticks==40) {
                player=server.getPlayerManager().getPlayerList().get(0);
                server.getOverworld().setTimeOfDay(6000);
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                FormUtils._setForm(player,RegPlayerForms.getPlayerForm(new Identifier("shape-shifter-curse","familiar_fox_1")));
                PlayerFormComponent.COMPONENT.get(player).instinctValue=37.5f;
                PlayerFormComponent.COMPONENT.get(player).sync();
                player.getInventory().setStack(1,new ItemStack(SscAddon.INSTINCT_RELEASE,2));
            }
            if (ticks==80) use(true);
        } catch (Throwable e) { fail(e); } });
    }
    private static void save(MinecraftClient mc,String name) throws Exception {
        try (var shot=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) { shot.writeTo(Path.of(name)); }
    }
    private static void fail(Throwable e) {
        if (failure!=null) return;
        failure=e; finished=true; e.printStackTrace();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }
    public static void verifyResult() {
        if (failure!=null) throw new AssertionError("固定道具客户端检查失败",failure);
        if (!finished) throw new AssertionError("客户端提前退出");
    }
}
