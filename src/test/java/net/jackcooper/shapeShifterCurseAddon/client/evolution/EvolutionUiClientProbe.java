package net.jackcooper.shapeShifterCurseAddon.client.evolution;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.evolution.*;
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
                    var node = EvolutionRegistry.INSTANCE.getRoute("axolotl").getNode("water_spear");
                    int size = coordinate("nodeSize", node), x = coordinate("cx", node);
                    Object tree = field("tree");
                    screen.mouseScrolled(box(tree, "x") + 12, box(tree, "y") + 12, -4);
                    check(coordinate("nodeSize", node) == size && coordinate("cx", node) == x, "wheel scrolls without zoom or horizontal motion");
                    stage++;
                } else if (stage == 2) {
                    save(mc, "axolotl-paper-scrolled.png");
                    var node = EvolutionRegistry.INSTANCE.getRoute("axolotl").getNode("water_spear");
                    beforePoints = comp.getPoints();
                    var other = EvolutionRegistry.INSTANCE.getRoute("axolotl").getNode("vortex_guide");
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
                    var node = EvolutionRegistry.INSTANCE.getRoute("familiar_fox").getNode("mana_system");
                    center(node);
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    stage = 126;
                } else if (stage == 126) {
                    bounds(mc); save(mc, "familiar-paper-gui5-mana-text.png");
                    var node = EvolutionRegistry.INSTANCE.getRoute("familiar_fox").getNode("spark");
                    center(node);
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    stage = 121;
                } else if (stage == 121) {
                    bounds(mc); save(mc, "familiar-paper-gui5-three-perks.png");
                    var node = EvolutionRegistry.INSTANCE.getRoute("familiar_fox").getNode("buff_immunity");
                    screen.mouseClicked(coordinate("cx", node), coordinate("cy", node), 0);
                    stage = 127;
                } else if (stage == 127) {
                    bounds(mc); save(mc, "familiar-paper-gui5-buff-text.png");
                    mc.getServer().execute(() -> { try { configure(mc.getServer().getPlayerManager().getPlayerList().get(0), "axolotl"); } catch (Throwable e) { fail(e); } });
                    stage = 122;
                } else if (stage == 122) {
                    check(comp.getRoute().equals("axolotl"), "wide-screen axolotl fixture synced");
                    screen = new EvolutionScreen(null); mc.setScreen(screen); stage++;
                } else if (stage == 123) {
                    bounds(mc); stage = 125;
                } else if (stage == 125) {
                    save(mc, "axolotl-paper-gui5-top.png");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END, 0, 0); stage = 124;
                } else {
                    branchVisible("axolotl", "branch_moon_ring");
                    save(mc, "axolotl-paper-gui5-branches.png");
                    Files.writeString(Path.of("evolution-ui-result.json"), "{\"passed\":true,\"checks\":" + checks + ",\"gui_scales\":[2,3,4,5],\"wide_resolution\":[" + mc.getWindow().getWidth() + "," + mc.getWindow().getHeight() + "],\"minimum_card_gap\":40,\"complete_labels_inside_inner_frame\":true,\"shifted_scrollbar_drag\":true,\"real_server_purchase\":true,\"png_sprites\":" + (EvolutionArtwork.ICONS.size() + EvolutionArtwork.SKIN.size()) + ",\"themed_backgrounds\":2,\"actual_icon_resolution\":64}");
                    finished = true;
                    System.out.println("EVOLUTION_UI_CLIENT_PASS checks=" + checks);
                    mc.scheduleStop();
                }
            } catch (Throwable e) { fail(e); }
        });
    }
    private static void center(EvolutionNode node) throws Exception {
        Object tree = field("tree");
        double delta = (coordinate("cy", node) - box(tree, "y") - box(tree, "h") / 2.0) / 24;
        screen.mouseScrolled(box(tree, "x") + 12, box(tree, "y") + 12, -delta);
    }
    private static void branchVisible(String routeId, String nodeId) throws Exception {
        var node = EvolutionRegistry.INSTANCE.getRoute(routeId).getNode(nodeId);
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
            for (var node : EvolutionRegistry.INSTANCE.getRoute(routeId).nodes)
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
        var activeRoute = EvolutionRegistry.INSTANCE.getRoute(RegEvolutionComponent.EVOLUTION.get(mc.player).getRoute());
        Object activeCard = card(activeRoute.getNode((String)field("selectedId")));
        if (box(activeCard, "bottom") >= box(tree, "y") && box(activeCard, "y") <= box(tree, "bottom"))
            check(EvolutionArtwork.loaded("selected_frame"), "selection frame texture loaded when selected perk is in view");
        Object railThumb = thumb(tree, (double)field("treeScroll"), (int)field("treeContentHeight"));
        check(box(railThumb, "x") == box(tree, "right") - 4 && box(railThumb, "w") == 7, "tree scrollbar moves right by half its seven-pixel width");
        check(box(railThumb, "right") < box(details, "x") - 7, "shifted tree scrollbar stays clear of the panel divider");
        spacing(mc);
    }
    private static void spacing(MinecraftClient mc) throws Exception {
        var route = EvolutionRegistry.INSTANCE.getRoute(RegEvolutionComponent.EVOLUTION.get(mc.player).getRoute());
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
