package net.jackcooper.shapeShifterCurseAddon.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Loads the real SSC classes through Fabric and verifies the applied visibility hooks. */
public final class HudCustomizationIntegrationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", Files.readString(Path.of(System.getProperty("hud.testClasspathFile"))));
        System.setProperty("mixin.debug.export", "true");
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "hud-customization-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName(HudCustomizationIntegrationTest.class.getName(), true, loader)
                .getMethod("run", ClassLoader.class).invoke(null, loader);
    }

    public static void run(ClassLoader loader) throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        String root = "net.onixary.shapeShifterCurseFabric.";
        verifyGate(loader, root + "magic.MagicGUIRender");
        verifyGate(loader, root + "mana.FamiliarFoxManaBar");
        verifyGate(loader, root + "mana.InstinctBarLikeManaBar");
        verifyGate(loader, root + "mana.WebResourceBar");
        verifyGate(loader, root + "player_form.utils.InstinctBarRenderer");
        visibilityBehavior();

        ClassNode magic = exported(root + "magic.MagicGUIRender");
        for (MethodNode m : magic.methods) if (m.name.equals("onClientTick")) {
            check(!reads(m, "showSscMagicHud"), "hidden magic HUD does not gate skill input");
        }
        String addon = "net/jackcooper/shapeShifterCurseAddon/client/hud/";
        ClassNode charge = read(loader, addon + "SpellCastHud.class");
        for (MethodNode m : charge.methods) if (m.name.equals("renderUnscaled")) {
            int gate = -1, cleanup = -1, index = 0;
            for (var insn : m.instructions) {
                if (insn instanceof FieldInsnNode f && f.name.equals("showChargeBar")) gate = index;
                if (insn instanceof MethodInsnNode c && c.name.equals("resetExit")) cleanup = index;
                index++;
            }
            check(cleanup >= 0 && gate > cleanup, "charge exit cleanup advances before the visibility gate");
        }
        verifyEarlyVisibility(read(loader, addon + "SpellbookHudRenderer.class"), "showSpellbookHud", "getSlotCount");
        verifyEarlyVisibility(read(loader, addon + "SkillCooldownBarRenderer.class"), "showCdBar", "entriesForHud");
        verifyEarlyVisibility(read(loader, "net/jackcooper/shapeShifterCurseAddon/client/mana/SimpleResourceBarRenderer.class"),
                "showManaBar", "getClientResourceValueAndMax");
        System.out.println("HUD customization integration: " + checks + " actual Fabric/Mixin checks passed. No live GUI acceptance.");
    }

    private static void verifyEarlyVisibility(ClassNode widget, String setting, String contentsQuery) {
        MethodNode render = widget.methods.stream().filter(m -> m.name.equals("onHudRender")).findFirst().orElseThrow();
        boolean readSetting = false, readContents = false, skippedLayout = false;
        for (var insn : render.instructions) {
            if (insn instanceof FieldInsnNode field && field.name.equals(setting)) readSetting = true;
            if (insn instanceof MethodInsnNode call && call.name.equals(contentsQuery)) readContents = true;
            if (readContents && insn.getOpcode() == org.objectweb.asm.Opcodes.RETURN) skippedLayout = true;
            if (insn instanceof MethodInsnNode call && call.owner.endsWith("/HudScaling") && call.name.equals("render")) {
                check(readSetting && skippedLayout, widget.name + " checks visibility and empty content before layout");
                return;
            }
        }
        throw new AssertionError(widget.name + " missing visible HUD scaling");
    }

    private static void verifyGate(ClassLoader loader, String target) throws Exception {
        Class.forName(target, false, loader);
        ClassNode node = exported(target);
        boolean scaled = false;
        for (MethodNode method : node.methods) {
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.endsWith("/HudScaling")
                        && call.name.equals("render")) scaled = true;
            }
        }
        check(scaled, target + " routes rendering through the shared visibility/scaling transaction");
    }

    private static void visibilityBehavior() throws Exception {
        me.shedaniel.autoconfig.AutoConfig.register(
                net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig.class,
                me.shedaniel.autoconfig.serializer.GsonConfigSerializer::new);
        var config = net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();
        String[] fields = {"showInstinctBar", "showManaBar", "showSscMagicHud", "showSscItemStoreHud"};
        int[] sections = {0, 1, 5, 7};
        for (int i = 0; i < fields.length; i++) {
            var field = config.getClass().getField(fields[i]);
            boolean previous = field.getBoolean(config);
            try {
                int[] calls = {0};
                field.setBoolean(config, false);
                HudScaling.render(null, sections[i], () -> calls[0]++);
                check(calls[0] == 0, fields[i] + " prevents drawing");
                field.setBoolean(config, true);
                HudScaling.render(null, sections[i], () -> calls[0]++);
                check(calls[0] == 1, fields[i] + " allows drawing again");
            } finally { field.setBoolean(config, previous); }
        }
    }

    private static boolean reads(MethodNode m, String field) {
        for (var insn : m.instructions) if (insn instanceof FieldInsnNode f && f.name.equals(field)) return true;
        return false;
    }

    private static ClassNode exported(String name) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/" + name.replace('.', '/') + ".class"))).accept(node, 0);
        return node;
    }

    private static ClassNode read(ClassLoader loader, String resource) throws Exception {
        try (var stream = loader.getResourceAsStream(resource)) {
            if (stream == null) throw new AssertionError(resource);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) throw new AssertionError(reason);
    }
}
