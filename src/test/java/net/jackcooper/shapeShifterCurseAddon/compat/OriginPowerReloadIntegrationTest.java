package net.jackcooper.shapeShifterCurseAddon.compat;

/** Initializes transformed Minecraft classes only; never constructs or starts a game world/server. */
public final class OriginPowerReloadIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("reload.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "origin-reload-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName("net.jackcooper.shapeShifterCurseAddon.compat.OriginPowerReloadIntegrationProbe", true, loader)
                .getMethod("run").invoke(null);
    }
}
