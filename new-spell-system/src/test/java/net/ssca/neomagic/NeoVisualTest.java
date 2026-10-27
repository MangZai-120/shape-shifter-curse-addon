package net.ssca.neomagic;

/** Renders the actual Minecraft screen using test fixtures and a disposable client directory. */
public final class NeoVisualTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path",java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("neomagic.testClasspathFile"))));
        java.nio.file.Files.writeString(java.nio.file.Path.of("options.txt"),"lang:zh_cn\nguiScale:2\nonboardAccessibility:false\n");
        String[] options={"--version","neomagic-visual","--accessToken","0","--username","NeoReview","--uuid","00000000000000000000000000000001","--assetsDir",System.getProperty("neomagic.assets"),"--assetIndex","1.20.1-5","--gameDir",new java.io.File(".").getAbsolutePath(),"--width","1440","--height","1000"};
        var knot=new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);var loader=knot.init(options);
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.ssca.neomagic.NeoVisualProbe",true,loader).getMethod("install").invoke(null);
        Class.forName("net.minecraft.client.main.Main",true,loader).getMethod("main",String[].class).invoke(null,(Object)options);
    }
}
