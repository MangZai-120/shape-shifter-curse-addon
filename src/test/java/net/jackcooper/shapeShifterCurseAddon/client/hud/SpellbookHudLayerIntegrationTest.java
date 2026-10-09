package net.jackcooper.shapeShifterCurseAddon.client.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Verifies the applied Fabric/Mixin hook, without opening a Minecraft window. */
public final class SpellbookHudLayerIntegrationTest {
    private static final String WIDGET = "net/jackcooper/shapeShifterCurseAddon/client/hud/SpellbookHudRenderer";
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", Files.readString(Path.of(System.getProperty("hud.testClasspathFile"))));
        System.setProperty("mixin.debug.export", "true");
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "spellbook-hud-layer-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.client.gui.hud.InGameHud", false, loader);

        Path exported = Path.of(".mixin.out", "class", "net", "minecraft", "client", "gui", "hud", "InGameHud.class");
        check(Files.isRegularFile(exported), "Mixin exports the actual transformed InGameHud");
        ClassNode hud = new ClassNode();
        new ClassReader(Files.readAllBytes(exported)).accept(hud, 0);
        int chatCalls = 0;
        int hookCalls = 0;
        int widgetCalls = 0;
        for (MethodNode method : hud.methods) {
            MethodInsnNode previousCall = null;
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                if (method.name.equals("render") && method.desc.equals("(Lnet/minecraft/client/gui/DrawContext;F)V")) {
                    if (call.name.contains("ssca$spellbookBeforeChat")) hookCalls++;
                    if (call.owner.equals("net/minecraft/client/gui/hud/ChatHud") && call.name.equals("render")) {
                        chatCalls++;
                        check(previousCall != null && previousCall.owner.equals(hud.name)
                                && previousCall.name.contains("ssca$spellbookBeforeChat"), "widget hook runs immediately before vanilla chat");
                    }
                }
                if (method.name.contains("ssca$spellbookBeforeChat") && call.owner.equals(WIDGET)
                        && call.name.equals("renderBeforeChat")) widgetCalls++;
                previousCall = call;
            }
        }
        check(chatCalls == 1 && hookCalls == 1 && widgetCalls == 1, "one widget render per vanilla chat render");

        String initializer = "net/jackcooper/shapeShifterCurseAddon/client/SscAddonClient.class";
        try (var stream = loader.getResourceAsStream(initializer)) {
            check(stream != null, "client initializer is available");
            ClassNode client = new ClassNode();
            new ClassReader(stream).accept(client, 0);
            int callbackInstances = 0;
            for (MethodNode method : client.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(WIDGET)
                        && call.name.equals("<init>")) callbackInstances++;
            }
            check(callbackInstances == 0, "old end-of-frame widget callback is removed");
        }
        System.out.println("Spellbook HUD integration: " + checks + " actual Mixin/order checks passed. Live chat rendering NOT tested.");
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) throw new AssertionError(reason);
    }
}
