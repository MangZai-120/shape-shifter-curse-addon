package net.jackcooper.shapeShifterCurseAddon.network.visual;

/** Registry-backed particle codecs need Fabric's access transformations, as in the other integration suites. */
public final class VisualRecipeTestBootstrap {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("visual.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "visual-recipe-checks", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName(args[0], true, loader).getMethod("main", String[].class).invoke(null, (Object)new String[0]);
    }
}
