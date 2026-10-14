package net.jackcooper.shapeShifterCurseAddon.evolution;

/** Runs the real Fabric/Apoli factories and SSC mana component without starting a game world. */
public final class EvolutionManaIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("evolutionMana.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.SERVER);
        var loader = knot.init(new String[]{"--version", "evolution-mana-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName("net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionManaIntegrationProbe", true, loader)
                .getMethod("run").invoke(null);
    }
}
