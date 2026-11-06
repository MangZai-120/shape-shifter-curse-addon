package net.ssca.neomagic;

/** Own disposable GameTest world. Does not open a user's save or listen for network players. */
public final class NeoWorldTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path",java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("neomagic.testClasspathFile"))));
        var knot=new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.SERVER);
        var loader=knot.init(new String[]{"nogui"});Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants",true,loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap",true,loader).getMethod("initialize").invoke(null);
        Class.forName("net.ssca.neomagic.NeoWorldProbe",true,loader).getMethod("run").invoke(null);
    }
}
