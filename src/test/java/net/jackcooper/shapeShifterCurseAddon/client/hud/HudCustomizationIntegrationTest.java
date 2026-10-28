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
        String root = "net.onixary.shapeShifterCurseFabric.";
        verifyGate(loader, root + "magic.MagicGUIRender", "ssca$hideMagicHud", "showSscMagicHud");
        verifyGate(loader, root + "mana.FamiliarFoxManaBar", "ssca$hideManaHud", "showManaBar");
        verifyGate(loader, root + "mana.InstinctBarLikeManaBar", "ssca$hideManaHud", "showInstinctBar");
        verifyGate(loader, root + "mana.WebResourceBar", "ssca$hideManaHud", "showManaBar");
        verifyGate(loader, root + "player_form.utils.InstinctBarRenderer", "ssc_addon$hideInstinctBarForSP", "showInstinctBar");

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
        ClassNode book = read(loader, addon + "SpellbookHudRenderer.class");
        check(book.methods.stream().anyMatch(m -> m.name.equals("renderUnscaled") && reads(m, "showSpellbookHud")),
                "spellbook rendering honors its own visibility setting");
        System.out.println("HUD customization integration: " + checks + " actual Fabric/Mixin checks passed. No live GUI acceptance.");
    }

    private static void verifyGate(ClassLoader loader, String target, String hook, String field) throws Exception {
        Class.forName(target, false, loader);
        ClassNode node = exported(target);
        // WrapMethod moves the original method body to its private bridge. Inspect the real
        // transformed body containing the HEAD visibility call, not only the public wrapper.
        boolean ordered=false,scaled=false;
        for(MethodNode m:node.methods) {
            int hookIndex=-1,bodyIndex=-1,index=0;
            for(var insn:m.instructions) {
                if(insn instanceof MethodInsnNode call && call.name.contains(hook))hookIndex=index;
                if(insn instanceof MethodInsnNode call && call.owner.endsWith("/HudScaling") && call.name.equals("render"))scaled=true;
                if(insn instanceof FieldInsnNode f && f.owner.equals(node.name) && f.name.equals("mc") && bodyIndex<0)bodyIndex=index;
                index++;
            }
            if(hookIndex>=0 && (bodyIndex<0 || hookIndex<bodyIndex))ordered=true;
        }
        check(ordered,target+" gates before rendering, including wrapped original bodies");
        check(scaled,target+" uses the shared uniform scaling transaction");
        boolean injected=false;
        for (MethodNode m : node.methods) if (m.name.contains(hook)) {
            check(reads(m, field), target + " reads " + field);
            injected = true;
            boolean cancels = false;
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call
                    && call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")
                    && call.name.equals("cancel")) cancels = true;
            check(cancels, "visibility handler cancels rendering");
        }
        check(injected, target + " receives the expected hook");
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
