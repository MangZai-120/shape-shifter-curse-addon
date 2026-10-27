package net.ssca.neomagic;

/** Bootstrap actual Fabric mixins/registries rather than treating the HTML calculator as game evidence. */
public final class NeoIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path",java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("neomagic.testClasspathFile"))));
        var knot=new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader=knot.init(new String[]{"--version","neomagic-integration","--accessToken","0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants",true,loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap",true,loader).getMethod("initialize").invoke(null);
        Class.forName("net.ssca.neomagic.NeoIntegrationProbe",true,loader).getMethod("run").invoke(null);
    }
}
