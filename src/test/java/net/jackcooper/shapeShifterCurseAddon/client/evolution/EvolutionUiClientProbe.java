package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.evolution.*;
import net.jackcooper.shapeShifterCurseAddon.ability.WaterSpearLeapManager;
import net.jackcooper.shapeShifterCurseAddon.ability.VortexGuideManager;
import net.jackcooper.shapeShifterCurseAddon.entity.ThrownWaterSpearEntity;
import net.jackcooper.shapeShifterCurseAddon.power.ConditionalManaAttributePower;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.additional_power.ChargePower;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

import java.nio.file.*;
import java.util.*;

/** Real rendered screen and C2S purchase checks in a disposable integrated-server world. */
public final class EvolutionUiClientProbe {
    private static boolean starting, finished;
    private static volatile boolean prepared;
    private static volatile boolean abilitiesChecked;
    private static volatile Throwable failure;
    private static int ticks, stage, wait, checks, beforePoints;
    private static EvolutionScreen screen;
    private static final long START = System.nanoTime();
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void install() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                mc -> org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || server.getPlayerManager().getPlayerList().isEmpty()) return;
            try {
                if (++ticks == 40) {
                    server.getOverworld().setTimeOfDay(6000);
                    server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                    configure(server.getPlayerManager().getPlayerList().get(0), "axolotl");
                    prepared = true;
                }
            } catch (Throwable e) { fail(e); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (finished) return;
                if (System.nanoTime() - START > 240_000_000_000L) throw new AssertionError("UI client timed out");
                if (mc.getOverlay() != null) return;
                if (!starting && mc.currentScreen instanceof TitleScreen) {
                    starting = true;
                    mc.options.pauseOnLostFocus = false;
                    mc.options.getViewDistance().setValue(4);
                    mc.options.getMaxFps().setValue(60);
                    mc.options.getGuiScale().setValue(2); mc.onResolutionChanged();
                    var config = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                    mc.createIntegratedServerLoader().createAndStart("Evolution-UI-" + System.currentTimeMillis(),
                            new LevelInfo("Evolution parchment visual check", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), config),
                            new GeneratorOptions(20261110L, false, false),
                            registry -> registry.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
                    return;
                }
                if (stage == 0 && mc.world != null && mc.player != null && mc.currentScreen != null) mc.setScreen(null);
                if (!prepared || mc.world == null || mc.player == null || ticks < 80) return;
                mc.getToastManager().clear();
                mc.inGameHud.getChatHud().clear(false);
                EvolutionComponent comp = RegEvolutionComponent.EVOLUTION.get(mc.player);
                if (++wait < 15) return;
                wait = 0;
                if (stage == 0) {
                    check(comp.getRoute().equals("axolotl") && comp.isUnlocked("water_spurt"), "fixture synced");
                    artwork(mc);
                    screen = new EvolutionScreen(null); mc.setScreen(screen);
                    stage++;
                } else if (stage == 1) {
                    bounds(mc);
                    save(mc, "axolotl-paper-top.png");
                    var node = EvolutionRegistry.CLIENT.getRoute("axolotl").getNode("water_spear");
                    int size = coordinate("nodeSize", node), x = coordinate("cx", node);
                    Object tree = field("tree");
                    screen.mouseScrolled(box(tree, "x") + 12, box(tree, "y") + 12, -4);
                    check(coordinate("nodeSize", node) == size && coordinate("cx", node) == x, "wheel scrolls without zoom or horizontal motion");
                    stage++;
                } else if (stage == 2) {
                    save(mc, "axolotl-paper-scrolled.png");
                    var node = EvolutionRegistry.CLIENT.getRoute("axolotl").getNode("water_spear");
                    beforePoints = comp.getPoints();
                    var other = EvolutionRegistry.CLIENT.getRoute("axolotl").getNode("vortex_guide");
                    Object card = card(other);
                    screen.mouseClicked(box(card, "x") + box(card, "w") / 2.0, box(card, "bottom") - 4, 0);
                    check(Objects.equals(field("selectedId"), other.id), "node label inside the octagonal frame selects its ability");
                    check(comp.getPoints() == beforePoints, "framed-card selection does not spend points");
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    check(!comp.isUnlocked("water_spear"), "selection does not purchase");
                    check(comp.getPoints() == beforePoints, "selection does not spend points");
                    stage++;
                } else if (stage == 3) {
                    check(!comp.isUnlocked("water_spear") && comp.getPoints() == beforePoints, "selection stays pending only as focus, no C2S purchase");
                    Object button = field("confirm");
                    screen.mouseClicked(box(button, "x") + 8, box(button, "y") + 8, 0);
                    screen.mouseClicked(box(button, "x") + 8, box(button, "y") + 8, 0);
                    stage++;
                } else if (stage == 4) {
                    check(comp.isUnlocked("water_spear"), "confirm unlocks through actual server packet");
                    check(comp.getPoints() == beforePoints - 1, "double click spends exactly one node cost");
                    check(!comp.isUnlocked("vortex_guide"), "purchase isolated to selected node");
                    save(mc, "axolotl-paper-unlocked.png");
                    mc.options.getGuiScale().setValue(3); mc.onResolutionChanged(); stage++;
                } else if (stage == 5) {
                    bounds(mc); save(mc, "axolotl-paper-gui3.png");
                    mc.options.getGuiScale().setValue(4); mc.onResolutionChanged(); stage++;
                } else if (stage == 6) {
                    bounds(mc); save(mc, "axolotl-paper-gui4.png");
                    mc.options.getGuiScale().setValue(2); mc.onResolutionChanged();
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0); stage = 60;
                } else if (stage == 60) {
                    branchVisible("axolotl", "branch_moon_ring");
                    save(mc, "axolotl-paper-branches.png");
                    mc.getServer().execute(() -> { try { configure(mc.getServer().getPlayerManager().getPlayerList().get(0), "familiar_fox"); } catch (Throwable e) { fail(e); } });
                    stage = 7;
                } else if (stage == 7) {
                    check(comp.getRoute().equals("familiar_fox"), "familiar route sync");
                    screen = new EvolutionScreen(null); mc.setScreen(screen); stage++;
                } else if (stage == 8) {
                    bounds(mc); save(mc, "familiar-paper.png");
                    Object tree = field("tree");
                    screen.mouseScrolled(box(tree, "x") + 10, box(tree, "y") + 10, -100); stage++;
                } else if (stage == 9) {
                    branchVisible("familiar_fox", "branch_spirit_lord");
                    save(mc, "familiar-paper-scrolled.png");
                    mc.setScreen(new ArtworkGallery(false)); stage++;
                } else if (stage == 10) {
                    save(mc, "axolotl-png-gallery.png");
                    mc.setScreen(new ArtworkGallery(true)); stage++;
                } else if (stage == 11) {
                    save(mc, "familiar-png-gallery.png");
                    check(EvolutionArtwork.loaded("axolotl") && EvolutionArtwork.loaded("familiar_fox"), "both portraits uploaded from individual PNGs");
                    EvolutionArtwork.clear();
                    check(!EvolutionArtwork.loaded("axolotl") && !EvolutionArtwork.loaded("familiar_fox"), "texture reload clears old PNG handles");
                    mc.setScreen(screen); stage++;
                } else if (stage == 12) {
                    check(EvolutionArtwork.loaded("experience_orb") && EvolutionArtwork.loaded("familiar_fox") && EvolutionArtwork.loaded("background_base")
                            && EvolutionArtwork.loaded(EvolutionArtwork.decoration("familiar_fox")), "icons, shared paper and transparent route decoration reload correctly");
                    org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getHandle(), 2560, 1440);
                    mc.options.getGuiScale().setValue(5); mc.onResolutionChanged();
                    screen = new EvolutionScreen(null); mc.setScreen(screen); stage = 120;
                } else if (stage == 120) {
                    mc.onResolutionChanged();
                    stage = 119;
                } else if (stage == 119) {
                    // Windows can reserve 30px of the requested client height for its window frame.
                    check(mc.getWindow().getWidth() == 2560 && mc.getWindow().getHeight() >= 1400 && mc.getWindow().getScaleFactor() == 5, "wide GUI5 scene matching supplied screenshots: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " scale=" + mc.getWindow().getScaleFactor());
                    bounds(mc); save(mc, "familiar-paper-gui5-top.png");
                    dragShiftedScrollbar();
                    var node = EvolutionRegistry.CLIENT.getRoute("familiar_fox").getNode("mana_system");
                    center(node);
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    stage = 126;
                } else if (stage == 126) {
                    bounds(mc); save(mc, "familiar-paper-gui5-mana-text.png");
                    var node = EvolutionRegistry.CLIENT.getRoute("familiar_fox").getNode("spark");
                    center(node);
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    stage = 121;
                } else if (stage == 121) {
                    bounds(mc); save(mc, "familiar-paper-gui5-three-perks.png");
                    var node = EvolutionRegistry.CLIENT.getRoute("familiar_fox").getNode("buff_immunity");
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    stage = 127;
                } else if (stage == 127) {
                    bounds(mc); save(mc, "familiar-paper-gui5-buff-text.png");
                    mc.getServer().execute(() -> { try { configure(mc.getServer().getPlayerManager().getPlayerList().get(0), "axolotl"); } catch (Throwable e) { fail(e); } });
                    stage = 122;
                } else if (stage == 122) {
                    check(comp.getRoute().equals("axolotl"), "wide-screen axolotl fixture synced");
                    screen = new EvolutionScreen(null); mc.setScreen(screen);
                    check(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking.PACKET_EVO_PURCHASE), "correlated purchase protocol negotiated");
                    beforePoints = comp.getPoints();
                    var node = EvolutionRegistry.CLIENT.getNode("axolotl", "water_spear");
                    center(node);
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                    stage++;
                } else if (stage == 123) {
                    check(comp.isUnlocked("water_spear") && comp.getPoints() == beforePoints - 1,
                            "opening and purchasing in the same client frame both reach the server");
                    check(field("waitingId") == null, "successful purchase clears the pending state");
                    bounds(mc); stage = 125;
                } else if (stage == 125) {
                    save(mc, "axolotl-paper-gui5-top.png");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0); stage = 124;
                } else if (stage == 124) {
                    branchVisible("axolotl", "branch_moon_ring");
                    save(mc, "axolotl-paper-gui5-branches.png");
                    stage = 130;
                    // Reproduce a stale client balance while using the actual purchase transport.
                    mc.getServer().execute(() -> {
                        var player = mc.getServer().getPlayerManager().getPlayerList().get(0);
                        RegEvolutionComponent.EVOLUTION.get(player).setPoints(0);
                        mc.execute(() -> {
                            try {
                                RegEvolutionComponent.EVOLUTION.get(mc.player).setPoints(1);
                                var node = EvolutionRegistry.CLIENT.getNode("axolotl", "vortex_guide");
                                center(node);
                                screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                                check(field("waitingId") != null, "stale client sends a real purchase request");
                                int request = (int) field("waitingRequest");
                                screen.onPurchaseResult(request - 1, 3);
                                check(field("waitingId") != null, "an old response cannot clear the current purchase");
                            } catch (Throwable e) { fail(e); }
                        });
                    });
                } else if (stage == 130) {
                    check(comp.getPoints() == 0 && !comp.isUnlocked("vortex_guide") && field("waitingId") == null,
                            "server denial refreshes balance and clears waiting before the three-second timeout");
                    stage = 131;
                    mc.getServer().execute(() -> {
                        try {
                            var player = mc.getServer().getPlayerManager().getPlayerList().get(0);
                            abilityRevocation(player);
                            effectiveTalentLifecycle(player);
                            abilitiesChecked = true;
                        }
                        catch (Throwable e) { fail(e); }
                    });
                } else if (stage == 131 && abilitiesChecked) {
                    check(EvolutionTalents.isActive(mc.player, "spark"), "actual client sees the synchronized active talent");
                    var clientRoutes = EvolutionTestRoutes.production();
                    try {
                        EvolutionRegistry.clearClient();
                        check(!EvolutionTalents.isActive(mc.player, "spark") && comp.isUnlocked("spark"),
                                "actual client pauses eligibility without deleting synchronized acquisition");
                    } finally { EvolutionRegistry.CLIENT.applyClientSync(clientRoutes); }
                    check(EvolutionTalents.isActive(mc.player, "spark"), "actual client resumes after configuration returns");
                    Files.writeString(Path.of("evolution-ui-result.json"), "{\"passed\":true,\"checks\":" + checks + ",\"gui_scales\":[2,3,4,5],\"wide_resolution\":[" + mc.getWindow().getWidth() + "," + mc.getWindow().getHeight() + "],\"minimum_card_gap\":40,\"complete_labels_inside_inner_frame\":true,\"shifted_scrollbar_drag\":true,\"real_server_purchase\":true,\"png_sprites\":" + (EvolutionArtwork.ICONS.size() + EvolutionArtwork.SKIN.size()) + ",\"themed_backgrounds\":2,\"actual_icon_resolution\":64}");
                    finished = true;
                    System.out.println("EVOLUTION_UI_CLIENT_PASS checks=" + checks);
                    mc.scheduleStop();
                }
            } catch (Throwable e) { fail(e); }
        });
    }
    private static void effectiveTalentLifecycle(ServerPlayerEntity player) throws Exception {
        var routes = EvolutionTestRoutes.production();
        var comp = RegEvolutionComponent.EVOLUTION.get(player);
        comp.unlock("water_spear");
        comp.unlock("vortex_guide");
        player.teleport(player.getServerWorld(), player.getX(), 80, player.getZ(), 0, -30);
        player.setAir(player.getMaxAir());
        WaterSpearLeapManager.onKeyPress(player);
        check(WaterSpearLeapManager.isCharging(player.getUuid()), "spear starts before config removal");
        int spentAir = player.getAir();
        check(spentAir < player.getMaxAir(), "spear actually spends moisture at cast start");
        try {
            EvolutionTestRoutes.loadServer(Map.of());
            WaterSpearLeapManager.tick(player);
            check(!WaterSpearLeapManager.isCharging(player.getUuid()) && !player.hasNoGravity(), "missing route cancels charge and releases gravity without relying on a reload event");
            check(player.getAir() == spentAir && comp.isUnlocked("water_spear"), "paused charge refunds no moisture and retains acquisition");
            WaterSpearLeapManager.onKeyPress(player);
            check(!WaterSpearLeapManager.isCharging(player.getUuid()) && player.getAir() == spentAir, "inactive talent cannot restart or consume resources");
        } finally { EvolutionTestRoutes.loadServer(routes); }

        VortexGuideManager.onKeyPress(player);
        var channelField = VortexGuideManager.class.getDeclaredField("CHANNELING");
        channelField.setAccessible(true);
        Map<?, ?> channels = (Map<?, ?>) channelField.get(null);
        check(channels.containsKey(player.getUuid()), "channel starts with its acquired talent");
        try {
            EvolutionTestRoutes.loadServer(EvolutionTestRoutes.withoutNode(routes, "axolotl", "vortex_guide"));
            VortexGuideManager.tick(player);
            check(!channels.containsKey(player.getUuid()) && !player.hasStatusEffect(StatusEffects.ABSORPTION), "missing node cancels the channel without a completion reward");
        } finally { EvolutionTestRoutes.loadServer(routes); }
        VortexGuideManager.onKeyPress(player);
        for (int i = 0; i < 120 && channels.containsKey(player.getUuid()); i++) VortexGuideManager.tick(player);
        var absorption = player.getStatusEffect(StatusEffects.ABSORPTION);
        check(absorption != null, "restored talent can finish and grant timed absorption");
        int duration = absorption.getDuration();

        WaterSpearLeapManager.onKeyPress(player);
        check(WaterSpearLeapManager.isCharging(player.getUuid()), "restored talent starts a fresh spear charge");
        for (int i = 0; i < 100 && WaterSpearLeapManager.isCharging(player.getUuid()); i++) WaterSpearLeapManager.tick(player);
        var spears = player.getServerWorld().getEntitiesByClass(ThrownWaterSpearEntity.class,
                player.getBoundingBox().expand(5), spear -> spear.getOwner() == player);
        check(spears.size() == 1, "completed charge spawned one actual spear");
        var spear = spears.get(0);
        try {
            EvolutionTestRoutes.loadServer(Map.of());
            VortexGuideManager.tick(player);
            WaterSpearLeapManager.tick(player);
            spear.tick();
            check(!spear.isRemoved(), "already launched spear continues after talent pause");
            check(player.getStatusEffect(StatusEffects.ABSORPTION) == absorption && absorption.getDuration() == duration,
                    "already granted timed absorption remains independent of talent eligibility");
            for (int i = 0; i < 65 && !spear.isRemoved(); i++) spear.tick();
            check(spear.isRemoved(), "launched spear still expires by its normal distance or lifetime");
        } finally { EvolutionTestRoutes.loadServer(routes); }

        var holder = PowerHolderComponent.KEY.get(player);
        PowerType<?> chargeType = PowerTypeRegistry.get(new Identifier("my_addon", "perk_upgrade_axolotl_tidal_pull_charge"));
        var source = new Identifier("test", "effective_talent_lifecycle");
        holder.addPower(chargeType, source);
        // Manual fixture grants must publish the holder before SSC sends per-power charge NBT.
        PowerHolderComponent.sync(player);
        var charge = (ChargePower) holder.getPower(chargeType);
        player.setAir(player.getMaxAir());
        charge.onUse();
        check(charge.isCharging(), "actual mapped SSC Perk begins charging");
        int perkAir = player.getAir();
        try {
            EvolutionTestRoutes.loadServer(EvolutionTestRoutes.withoutNode(routes, "axolotl", "vortex_guide"));
            charge.tick();
            check(!charge.isCharging() && charge.ChargeTime == 0, "mapped SSC charge cancels when its talent becomes inactive");
            check(player.getAir() == perkAir && perkAir < player.getMaxAir(), "mapped Perk cancellation preserves the spent start cost");
        } finally { EvolutionTestRoutes.loadServer(routes); }
        charge.onUse();
        check(charge.isCharging(), "restored talent allows the retained mapped SSC Perk to charge again");
        holder.removePower(chargeType, source);
        PowerHolderComponent.sync(player);

        configure(player, "familiar_fox");
        comp.setUnlockAll(true);
        var boosts = PowerHolderComponent.KEY.get(player).getPowers().stream()
                .filter(power -> power instanceof ConditionalManaAttributePower).toList();
        check(!boosts.isEmpty(), "actual evolved familiar form grants conditional mana powers");
        boosts.forEach(io.github.apace100.apoli.power.Power::tick);
        var mana = ManaUtils.getManaComponent(player);
        check(Math.abs(mana.getMaxMana() - 103) < 0.001, "actual form retains all bonuses including spark with fire ring");
        mana.setMana(103);
        try {
            EvolutionTestRoutes.loadServer(Map.of());
            boosts.forEach(io.github.apace100.apoli.power.Power::tick);
            mana.tick();
            check(Math.abs(mana.getMaxMana() - 33) < 0.001 && Math.abs(mana.getMana() - 33) < 0.001,
                    "pause withdraws continuous bonuses and SSC clamps excess current mana");
        } finally { EvolutionTestRoutes.loadServer(routes); }
        boosts.forEach(io.github.apace100.apoli.power.Power::tick);
        check(Math.abs(mana.getMaxMana() - 103) < 0.001 && Math.abs(mana.getMana() - 33) < 0.001,
                "restoring maximum mana does not refund truncated current mana");
        EvolutionManager.sync(player);
    }

    private static void abilityRevocation(ServerPlayerEntity player) throws Exception {
        var comp = RegEvolutionComponent.EVOLUTION.get(player);
        comp.unlock("water_spear");
        player.setAir(player.getMaxAir());
        net.jackcooper.shapeShifterCurseAddon.ability.WaterSpearLeapManager.onKeyPress(player);
        check(net.jackcooper.shapeShifterCurseAddon.ability.WaterSpearLeapManager.isCharging(player.getUuid()),
                "real player starts the spear charge before talent revocation");
        comp.clearUnlockedNodes();
        net.jackcooper.shapeShifterCurseAddon.ability.WaterSpearLeapManager.tick(player);
        check(!net.jackcooper.shapeShifterCurseAddon.ability.WaterSpearLeapManager.isCharging(player.getUuid()) && !player.hasNoGravity(),
                "revoking the spear talent cancels the charge and releases gravity");
        comp.unlock("vortex_guide");
        net.jackcooper.shapeShifterCurseAddon.ability.VortexGuideManager.onKeyPress(player);
        var channels = net.jackcooper.shapeShifterCurseAddon.ability.VortexGuideManager.class.getDeclaredField("CHANNELING");
        channels.setAccessible(true);
        Map<?, ?> states = (Map<?, ?>) channels.get(null);
        check(states.containsKey(player.getUuid()), "real player starts the vortex channel before talent revocation");
        comp.clearUnlockedNodes();
        net.jackcooper.shapeShifterCurseAddon.ability.VortexGuideManager.tick(player);
        check(!states.containsKey(player.getUuid()), "revoking the vortex talent stops healing and completion rewards");
    }
    private static void center(EvolutionNode node) throws Exception {
        Object tree = field("tree");
        double delta = (coordinate("cy", node) - box(tree, "y") - box(tree, "h") / 2.0) / 24;
        screen.mouseScrolled(box(tree, "x") + 12, box(tree, "y") + 12, -delta);
    }
    private static void branchVisible(String routeId, String nodeId) throws Exception {
        var node = EvolutionRegistry.CLIENT.getRoute(routeId).getNode(nodeId);
        Object tree = field("tree"), card = card(node);
        check(box(card, "y") >= box(tree, "y") && box(card, "bottom") <= box(tree, "bottom"), "final form illustration, label and full double frame visible at scroll end: " + routeId);
    }
    private static Object card(EvolutionNode node) throws Exception {
        return nodeBox("nodeCard", node);
    }
    private static Object nodeBox(String name, EvolutionNode node) throws Exception {
        var method = EvolutionScreen.class.getDeclaredMethod(name, EvolutionNode.class);
        method.setAccessible(true); return method.invoke(screen, node);
    }
    private static Object thumb(Object viewport, double scroll, int contentHeight) throws Exception {
        var method = EvolutionScreen.class.getDeclaredMethod("scrollbarThumb", viewport.getClass(), double.class, int.class);
        method.setAccessible(true); return method.invoke(screen, viewport, scroll, contentHeight);
    }
    private static void dragShiftedScrollbar() throws Exception {
        Object tree = field("tree"), thumb = thumb(tree, (double)field("treeScroll"), (int)field("treeContentHeight"));
        double mx = box(thumb, "right") - 1, my = box(thumb, "y") + box(thumb, "h") / 2.0;
        double before = (double)field("treeScroll");
        check(mx >= box(tree, "right"), "drag begins on shifted part outside the old viewport");
        check(screen.mouseClicked(mx, my, 0) && (boolean)field("draggingThumb"), "shifted thumb has a matching mouse hit region");
        check((double)field("treeScroll") == before, "grabbing the thumb preserves its current scroll position");
        check(screen.mouseDragged(mx, box(tree, "bottom") - 1, 0, 0, box(tree, "bottom") - 1 - my), "shifted thumb handles drag");
        screen.mouseReleased(mx, box(tree, "bottom") - 1, 0);
        check((double)field("treeScroll") > before && !(boolean)field("draggingThumb"), "thumb drag scrolls the tree and releases correctly");
        check(screen.mouseScrolled(mx, box(tree, "y") + 3, 100), "wheel accepts the shifted rail outside the viewport");
        check((double)field("treeScroll") == 0, "wheel over shifted rail returns to top");
    }
    private static void artwork(MinecraftClient mc) throws Exception {
        for (String routeId : List.of("axolotl", "familiar_fox")) {
            check(EvolutionArtwork.hasSprite(routeId), "PNG portrait: " + routeId);
            for (var node : EvolutionRegistry.CLIENT.getRoute(routeId).nodes)
                check(EvolutionArtwork.hasSprite(node.id), "PNG mapped for node " + node.id);
        }
        Set<String> ids = new TreeSet<>(EvolutionArtwork.ICONS);
        ids.addAll(EvolutionArtwork.SKIN);
        for (String id : ids) {
            try (var stream = mc.getResourceManager().getResource(EvolutionArtwork.resource(id)).orElseThrow().getInputStream();
                 var image = net.minecraft.client.texture.NativeImage.read(stream)) {
                check(image.getWidth() == 64 && image.getHeight() == 64, "actual source PNG is 64x64: " + id);
                int count = 0;
                boolean oneInk = true, guttersClear = true;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int pixel = image.getColor(x, y);
                    if (pixel == 0) continue;
                    count++; oneInk &= pixel == EvolutionArtwork.INK_ABGR;
                    guttersClear &= x >= 4 && x < 60 && y >= 4 && y < 60;
                }
                check(count > 16 && count < 2700 && oneInk && guttersClear, "authored ink and transparent gutters: " + id + " pixels=" + count);
                if (id.equals("experience_orb") || id.equals("node_frame") || id.equals("selected_frame"))
                    check(image.getColor(32, 32) == 0, "clear center in " + id);
            }
        }
        try (var stream = mc.getResourceManager().getResource(EvolutionArtwork.resource("background_base")).orElseThrow().getInputStream();
             var image = net.minecraft.client.texture.NativeImage.read(stream)) {
            check(image.getWidth() == 768 && image.getHeight() == 512, "shared axolotl parchment keeps reference proportions");
            check((image.getColor(384, 256) >>> 24) == 255 && (image.getColor(0, 0) >>> 24) == 0, "shared sheet is opaque with transparent outer corners");
        }
        long previousHash = 0;
        for (String id : List.of(EvolutionArtwork.decoration("axolotl"), EvolutionArtwork.decoration("familiar_fox"))) {
            try (var stream = mc.getResourceManager().getResource(EvolutionArtwork.resource(id)).orElseThrow().getInputStream();
                 var image = net.minecraft.client.texture.NativeImage.read(stream)) {
                check(image.getWidth() == 768 && image.getHeight() == 512, "route ornaments use the shared canvas: " + id);
                check((image.getColor(384, 256) >>> 24) == 0 && (image.getColor(0, 0) >>> 24) == 0, "route layer has a transparent center and corner: " + id);
                int visible = 0, paperPixels = 0;
                for (int y = 0; y < 512; y++) for (int x = 0; x < 768; x++) {
                    int pixel = image.getColor(x, y);
                    if ((pixel >>> 24) != 0) visible++;
                    if (pixel == 0xFFA9D4E9) paperPixels++;
                }
                check(visible > 100 && visible < 20000 && paperPixels == 0, "ornaments contain no baked paper fill: " + id);
                long hash = 17;
                for (int y = 0; y < 512; y += 2) for (int x = 0; x < 768; x += 2) hash = hash * 31 + image.getColor(x, y);
                if (previousHash != 0) check(hash != previousHash, "forms keep their own ornaments over the shared paper");
                previousHash = hash;
            }
        }
    }
    private static final class ArtworkGallery extends net.minecraft.client.gui.screen.Screen {
        private final boolean familiar;
        ArtworkGallery(boolean familiar) { super(net.minecraft.text.Text.literal("PNG artwork")); this.familiar = familiar; }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(net.minecraft.client.gui.DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, EvolutionInk.PAPER);
            String[] names = familiar
                    ? new String[]{"familiar_fox", "familiar_fox_base", "mana_system", "spark", "rocket", "buff_immunity", "alchemy", "spirit_vision", "fire_ring", "branch_spirit_lord", "branch_mancianima", "back_arrow"}
                    : new String[]{"axolotl", "axolotl_base", "aquatic_adapt", "water_propel", "water_spurt", "water_spear", "vortex_guide", "become_water", "branch_moon_ring", "branch_stone", "experience_orb", "unlocked_check"};
            int side = Math.min(92, Math.min(width / 4 - 12, height / 3 - 25));
            for (int i = 0; i < names.length; i++) {
                int x = (i % 4 * 2 + 1) * width / 8, y = i / 4 * height / 3 + 10;
                EvolutionArtwork.draw(ctx, names[i], x - side / 2, y, side);
                ctx.drawText(textRenderer, names[i], x - textRenderer.getWidth(names[i]) / 2, y + side + 8, EvolutionInk.INK, false);
            }
        }
    }
    private static void configure(ServerPlayerEntity player, String routeId) {
        var route = EvolutionRegistry.INSTANCE.getRoute(routeId);
        FormUtils._setForm(player, RegPlayerForms.getPlayerForm(route.startForm));
        var comp = RegEvolutionComponent.EVOLUTION.get(player);
        comp.reset(); comp.setRoute(routeId); comp.markMigrated(); comp.setLastTotalExp(player.totalExperience);
        comp.unlock(route.getBaseNodeId()); comp.setExp(EvolutionComponent.levelToTotalExp(25) * 2 + 40);
        for (int level : route.getLevelMilestones()) if (level <= 25) comp.markGrantedLevel(level);
        comp.setPoints(4);
        if (routeId.equals("axolotl")) {
            EvolutionManager.tryUnlock(player, "aquatic_adapt");
            EvolutionManager.tryUnlock(player, "water_propel");
            EvolutionManager.tryUnlock(player, "water_spurt");
        } else {
            EvolutionManager.tryUnlock(player, "mana_system");
            EvolutionManager.tryUnlock(player, "spark");
            EvolutionManager.tryUnlock(player, "rocket");
        }
        EvolutionManager.sync(player);
    }
    private static Object field(String name) throws Exception {
        var f = EvolutionScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen);
    }
    private static int box(Object b, String name) throws Exception {
        var m = b.getClass().getDeclaredMethod(name); m.setAccessible(true); return (int)m.invoke(b);
    }
    private static int coordinate(String name, EvolutionNode node) throws Exception {
        var method = EvolutionScreen.class.getDeclaredMethod(name, EvolutionNode.class); method.setAccessible(true); return (int)method.invoke(screen, node);
    }
    private static void bounds(MinecraftClient mc) throws Exception {
        Object paper = field("paper"), tree = field("tree"), body = field("detailBody"), button = field("confirm"), header = field("header"), details = field("details");
        check(box(paper, "x") > 0 && box(paper, "y") > 0, "paper leaves world visible on top/left");
        check(box(paper, "right") < mc.getWindow().getScaledWidth() && box(paper, "bottom") < mc.getWindow().getScaledHeight(), "paper leaves world visible on bottom/right");
        check(box(body, "h") > 0 && box(tree, "h") > 0, "both scroll viewports positive");
        check(box(button, "y") > box(body, "bottom"), "purchase button never overlaps description");
        check(box(header, "x") >= box(paper, "x") + box(paper, "w") * 5 / 100 && box(header, "y") >= box(paper, "y") + box(paper, "h") * 5 / 100, "whole header inset away from folded corner");
        check(box(header, "bottom") <= box(tree, "y") - 4, "moved header stays clear of tree header divider");
        check(box(button, "w") <= box(details, "w") * .65 && box(button, "x") > box(details, "x") && box(button, "right") < box(details, "right"), "narrow confirmation button has room for both ornaments");
        String decoration = EvolutionArtwork.decoration(RegEvolutionComponent.EVOLUTION.get(mc.player).getRoute());
        for (String background : List.of("background_base", decoration)) {
        try (var stream = mc.getResourceManager().getResource(EvolutionArtwork.resource(background)).orElseThrow().getInputStream();
             var image = net.minecraft.client.texture.NativeImage.read(stream)) {
            int buttonInk = backgroundInk(image, paper, button);
            check(buttonInk == 0, "confirmation button avoids all background ornaments: ink=" + buttonInk + " background=" + background + " paper=" + paper + " button=" + button);
            check(backgroundInk(image, paper, body) == 0, "condition text area has no background decorations");
            check(backgroundInk(image, paper, header) == 0, "portrait and the complete header avoid all painted background marks");
        }
        }
        var failed = EvolutionInk.class.getDeclaredField("paperFailed"); failed.setAccessible(true);
        check(!(boolean)failed.get(null), "generated parchment texture loaded");
        check(EvolutionArtwork.loaded("node_frame") && EvolutionArtwork.loaded("scroll_track") && EvolutionArtwork.loaded("scroll_thumb") && EvolutionArtwork.loaded("scroll_grip"), "painted node frames and scrollbar with grip loaded");
        var activeRoute = EvolutionRegistry.CLIENT.getRoute(RegEvolutionComponent.EVOLUTION.get(mc.player).getRoute());
        Object activeCard = card(activeRoute.getNode((String)field("selectedId")));
        if (box(activeCard, "bottom") >= box(tree, "y") && box(activeCard, "y") <= box(tree, "bottom"))
            check(EvolutionArtwork.loaded("selected_frame"), "selection frame texture loaded when selected perk is in view");
        Object railThumb = thumb(tree, (double)field("treeScroll"), (int)field("treeContentHeight"));
        check(box(railThumb, "x") == box(tree, "right") - 4 && box(railThumb, "w") == 7, "tree scrollbar moves right by half its seven-pixel width");
        check(box(railThumb, "right") < box(details, "x") - 7, "shifted tree scrollbar stays clear of the panel divider");
        spacing(mc);
    }
    private static void spacing(MinecraftClient mc) throws Exception {
        var route = EvolutionRegistry.CLIENT.getRoute(RegEvolutionComponent.EVOLUTION.get(mc.player).getRoute());
        Map<Integer, List<EvolutionNode>> rows = new TreeMap<>();
        for (var node : route.nodes) rows.computeIfAbsent(node.col, ignored -> new ArrayList<>()).add(node);
        String selected = (String)field("selectedId");
        Integer previousBottom = null;
        for (var nodes : rows.values()) {
            nodes.sort(Comparator.comparingInt(n -> n.row));
            int rowTop = Integer.MAX_VALUE, rowBottom = Integer.MIN_VALUE;
            Integer previousRight = null;
            for (var node : nodes) {
                Object card = card(node);
                Object plate = nodeBox("labelPlate", node), content = nodeBox("labelContent", node);
                check(box(content, "x") >= box(plate, "x") + 6 && box(content, "right") <= box(plate, "right") - 6
                        && box(content, "y") >= box(plate, "y") + 4 && box(content, "bottom") <= box(plate, "bottom") - 4,
                        "label text has clear padding inside the double outline: " + node.id);
                var labelMethod = EvolutionScreen.class.getDeclaredMethod("labelLines", EvolutionNode.class);
                labelMethod.setAccessible(true);
                @SuppressWarnings("unchecked") var lines = (List<net.minecraft.text.OrderedText>)labelMethod.invoke(screen, node);
                StringBuilder shown = new StringBuilder();
                boolean fits = lines.size() * 8 <= box(content, "h");
                for (var line : lines) {
                    fits &= mc.textRenderer.getWidth(line) * .8 <= box(content, "w");
                    line.accept((index, style, codepoint) -> { shown.appendCodePoint(codepoint); return true; });
                }
                check(fits, "every wrapped line fits the actual inner label area: " + node.id);
                String fullName = net.minecraft.text.Text.translatable(node.nameKey).getString().replace("技能：", "");
                check(shown.toString().replaceAll("\\s+", "").equals(fullName.replaceAll("\\s+", "")), "long labels preserve their complete name: " + node.id);
                check(box(plate, "x") >= box(card, "x") + 2 && box(plate, "right") <= box(card, "right") - 2
                        && box(plate, "bottom") <= box(card, "bottom") - 2, "multiline plaque remains fully inside its perk card: " + node.id);
                int outer = node.id.equals(selected) ? 4 : 0;
                int left = box(card, "x") - outer, right = box(card, "right") + outer;
                rowTop = Math.min(rowTop, box(card, "y") - outer);
                rowBottom = Math.max(rowBottom, box(card, "bottom") + outer);
                if (previousRight != null) check(left - previousRight >= 6, "same-row cards and selection frames remain separated: " + node.id);
                previousRight = right;
            }
            if (previousBottom != null) check(rowTop - previousBottom >= 40, "consecutive rows keep screenshot-sized clear space, including selected outlines");
            previousBottom = rowBottom;
        }
    }
    private static int backgroundInk(net.minecraft.client.texture.NativeImage image, Object paper, Object region) throws Exception {
        int left = Math.max(0, (box(region, "x") - box(paper, "x")) * image.getWidth() / box(paper, "w"));
        int top = Math.max(0, (box(region, "y") - box(paper, "y")) * image.getHeight() / box(paper, "h"));
        int right = Math.min(image.getWidth(), (int)Math.ceil((box(region, "right") - box(paper, "x")) * image.getWidth() / (double)box(paper, "w")));
        int bottom = Math.min(image.getHeight(), (int)Math.ceil((box(region, "bottom") - box(paper, "y")) * image.getHeight() / (double)box(paper, "h")));
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++)
            if (image.getColor(x, y) == EvolutionArtwork.INK_ABGR) count++;
        return count;
    }
    private static void save(MinecraftClient mc, String name) throws Exception {
        try (var shot = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) { shot.writeTo(Path.of(name)); }
    }
    private static void fail(Throwable error) {
        if (failure != null) return;
        failure = error; finished = true; error.printStackTrace();
        try { Files.writeString(Path.of("evolution-ui-result.json"), "{\"passed\":false,\"checks\":" + checks + "}"); }
        catch (Exception reportError) { error.addSuppressed(reportError); }
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().scheduleStop());
    }
    public static void verifyResult() {
        if (failure != null) throw new AssertionError("Evolution UI live check failed", failure);
        if (!finished) throw new AssertionError("Evolution UI client exited before verification");
    }
}
