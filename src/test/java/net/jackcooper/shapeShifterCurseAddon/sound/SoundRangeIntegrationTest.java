package net.jackcooper.shapeShifterCurseAddon.sound;

/** Loads actual Fabric/Mixin classes without starting Minecraft or opening an audio device. */
public final class SoundRangeIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("sound.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "sound-range-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName("net.jackcooper.shapeShifterCurseAddon.sound.SoundRangeIntegrationProbe", true, loader)
                .getMethod("run").invoke(null);
    }
}
