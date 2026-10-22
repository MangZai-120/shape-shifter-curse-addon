package net.jackcooper.shapeShifterCurseAddon.evolution;

/** Real client predicates, server business logic, CCA NBT and Fabric synchronization event; no game window. */
public final class EvolutionSystemIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("evolutionSystem.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "evolution-system-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName("net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionSystemIntegrationProbe", true, loader)
                .getMethod("run").invoke(null);
    }
}
