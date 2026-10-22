package net.jackcooper.shapeShifterCurseAddon.spell;

/** Runs real Minecraft item/effect/data-tracker code with Fabric mixins, without a game window. */
public final class SpellAuditIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("spellAudit.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "spell-audit-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName("net.jackcooper.shapeShifterCurseAddon.spell.SpellAuditIntegrationProbe", true, loader)
                .getMethod("run").invoke(null);
    }
}
