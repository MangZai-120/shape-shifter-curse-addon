package net.jackcooper.shapeShifterCurseAddon.cooldown;

/** Runs the real Fabric-transformed Apoli reload method, without starting a game world. */
public final class SkillCooldownReloadIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(
                java.nio.file.Path.of(System.getProperty("cooldown.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "cooldown-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(SkillCooldownReloadIntegrationTest.class.getName() + "$Probe", true, loader).getMethod("run").invoke(null);
    }

    public static final class Probe {
        public static void run() throws Exception {
            var id = new net.minecraft.util.Identifier("test", "retained");
            var previous = new io.github.apace100.apoli.power.PowerType<>(id, null);
            io.github.apace100.apoli.power.PowerTypeRegistry.register(id, previous);
            var method = io.github.apace100.apoli.power.PowerTypes.class.getDeclaredMethod("apply", java.util.Map.class,
                    net.minecraft.resource.ResourceManager.class, net.minecraft.util.profiler.Profiler.class);
            method.setAccessible(true);
            var invalid = com.google.gson.JsonParser.parseString("{\"type\":\"my_addon:fail_aware_active_self\",\"cooldown\":-1}");
            try {
                method.invoke(new io.github.apace100.apoli.power.PowerTypes(), java.util.Map.of(id, java.util.List.of(invalid)), null, null);
                throw new AssertionError("Invalid reload did not fail");
            } catch (java.lang.reflect.InvocationTargetException ex) {
                if (!(ex.getCause() instanceof IllegalArgumentException)
                        || !ex.getCause().getMessage().contains("Invalid cooldown in power")) throw ex;
            }
            if (io.github.apace100.apoli.power.PowerTypeRegistry.get(id) != previous)
                throw new AssertionError("Invalid reload erased previous registry");
            System.out.println("Fabric-transformed Apoli invalid cooldown reload retains prior registry PASS; live /reload NOT tested.");
        }
    }
}
