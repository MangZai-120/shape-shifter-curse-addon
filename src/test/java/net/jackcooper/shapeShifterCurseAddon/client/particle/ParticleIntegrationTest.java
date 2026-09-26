package net.jackcooper.shapeShifterCurseAddon.client.particle;

/** Isolate actual Minecraft/Mixin classes from the Gradle launcher class loader. */
public final class ParticleIntegrationTest {
    public static void main(String[] args) throws Exception {
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "particle-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName("net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleIntegrationProbe", true, loader)
                .getMethod("run").invoke(null);
    }
}
