package net.jackcooper.shapeShifterCurseAddon.client.particle;

import net.jackcooper.shapeShifterCurseAddon.client.particle.ParticleAvoidance.Strength;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Kind;
import net.jackcooper.shapeShifterCurseAddon.network.VisualRecipe.Particle;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Random;

/** Camera movement, perspective/ownership isolation, protected cues and actual 1.20.1 injection targets. */
public final class ParticleAvoidanceTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (Strength strength : Strength.values()) {
            for (float size : new float[]{0.04f, 0.12f, 0.5f, 1.2f}) {
                float previous = 0;
                // 重度档 16 格 + 2 格恢复带，扫描须覆盖 18.5 格外（步进 0.01 × 1900 = 19 格）
                for (int step = 0; step <= 1900; step++) {
                    double distance = step / 100.0;
                    float visible = ParticleAvoidance.visibility(strength, true, true, distance, size);
                    // 近距影子区（< NEAR_ZONE 常量 25%）到主段起点（0）是设计落差：跳过该边界的单调检查
                    boolean nearBoundary = distance >= ParticleAvoidance.NEAR_ZONE - 0.02
                            && distance <= ParticleAvoidance.NEAR_ZONE + 0.02;
                    check(Float.isFinite(visible) && visible <= 1, "Visibility stays finite");
                    if (!nearBoundary) check(visible >= previous, "Smooth distance ordering");
                    if (step > 0 && !nearBoundary) check(visible - previous < 0.03, "No hard cut at the avoidance boundary");
                    previous = visible;
                    check(ParticleAvoidance.visibility(strength, false, true, distance, size) == 1,
                            "Other players and untagged cues are untouched, even at the camera");
                    check(ParticleAvoidance.visibility(strength, true, false, distance, size) == 1,
                            "Switching to third-person restores the same live particle immediately");
                }
                check(previous == 1, "Distant effects remain complete");
                if (strength != Strength.OFF) {
                    check(ParticleAvoidance.visibility(strength, true, true, 0, size)
                            == ParticleAvoidance.NEAR_VISIBILITY, "Camera-close zone shows shadow-level 25% (20% sampled at render layer)");
                }
            }
        }
        double[] ranges = {0, 4, 10, 16};
        for (Strength strength : Strength.values()) {
            check(strength.radius() == ranges[strength.ordinal()], "User-specified 4/10/16-block ranges");
            if (strength == Strength.OFF) continue;
            for (float size : new float[]{0, 0.12f, 1.2f, 20}) {
                // 恢复带：半径处恰为 50%，半径+2 格外完全恢复
                float atEdge = ParticleAvoidance.visibility(strength, true, true, strength.radius(), size);
                check(atEdge > 0.49f && atEdge < 0.51f, "Edge keeps 50% visibility at the advertised radius");
                check(ParticleAvoidance.visibility(strength, true, true, strength.radius() + 2.1, size) == 1,
                        "Fully restored just past the recovery band");
                check(ParticleAvoidance.visibility(strength, true, true, strength.radius() - 0.5, size) < 0.5f,
                        "Fade remains stronger just inside the advertised radius");
            }
            check(ParticleAvoidance.visibility(strength, true, true, 0, 0) == ParticleAvoidance.NEAR_VISIBILITY,
                    "Near zone keeps shadow visibility (render layer samples 20%)");
        }
        // 近距抽样：按 identity hash 稳定判定，保留率约 20%（大样本容差 ±5%）
        int kept = 0, total = 10000;
        for (int i = 0; i < total; i++) {
            if (ParticleAvoidance.keepNearSample(new Object())) kept++;
        }
        check(kept > total * 0.15 && kept < total * 0.25, "Near-zone sampling keeps roughly 20% of particles");
        check(ParticleAvoidance.keepNearSample(ParticleAvoidanceTest.class) == ParticleAvoidance.keepNearSample(ParticleAvoidanceTest.class),
                "Sampling is stable per particle (no flicker across frames)");
        for (int step = 0; step < 500; step++) {
            double d = step / 100.0;
            float light = ParticleAvoidance.visibility(Strength.LIGHT, true, true, d, 0.2f);
            float standard = ParticleAvoidance.visibility(Strength.STANDARD, true, true, d, 0.2f);
            float strong = ParticleAvoidance.visibility(Strength.STRONG, true, true, d, 0.2f);
            check(strong <= standard && standard <= light, "Strength increases avoidance consistently");
            check(ParticleAvoidance.visibility(Strength.OFF, true, true, d, 0.2f) == 1, "Off fully restores originals");
            check(ParticleAvoidance.visibility(Strength.STANDARD, true, true, d, 1.2f) <= standard,
                    "Large smoke cannot bypass camera protection through a distant center");
        }
        for (Kind kind : Kind.values()) {
            int[] decorations = {0}, cues = {0};
            for (int age = 0; age < 120; age++) {
                VisualRecipe.emit(kind, age, age, 120, 0.4f, 0.35f, 3, 8, new Random(age), batch -> {
                    boolean decoration = VisualRecipe.isDecoration(kind, batch);
                    if (decoration) decorations[0]++; else cues[0]++;
                    if (batch.particle() == Particle.LETHAL_DUST || batch.particle() == Particle.EDGE_DUST
                            || kind == Kind.SEED_FIELD) check(!decoration, "Danger/field boundaries stay visible");
                    if ((kind == Kind.RED_RING || kind == Kind.AMULET_RING || kind == Kind.EMPOWERED_AMULET_RING)
                            && batch.count() == 1 && batch.particle() != Particle.LAVA)
                        check(decoration, "RED ring flames must be eligible for near-camera blending");
                });
            }
            check(kind == Kind.SEED_FIELD ? decorations[0] == 0 : decorations[0] > 0, "Intended recipe coverage");
            if (kind == Kind.NOVA_CHARGE || kind == Kind.SEED_FIELD)
                check(cues[0] > 0, "Recipe retains its protected cues");
        }
        renderRouting();
        renderOpacity();
        configUi();
        injectionTargets();
        System.out.println("Particle avoidance: " + checks + " checks passed (policy, cues, 1.20.1 bytecode targets). No in-game render capture.");
    }

    private static void renderRouting() {
        Object nearFlame = new Object(), farFlame = new Object(), enemyFlame = new Object();
        Object nearSoul = new Object(), lunarDust = new Object(), marker = new Object(), terrain = new Object();
        java.util.Map<String, java.util.Queue<Object>> simulation = new java.util.HashMap<>();
        simulation.put("opaque", new java.util.ArrayDeque<>(java.util.List.of(nearFlame, farFlame, enemyFlame)));
        simulation.put("lit", new java.util.ArrayDeque<>(java.util.List.of(nearSoul)));
        simulation.put("translucent", new java.util.ArrayDeque<>(java.util.List.of(lunarDust, marker)));
        simulation.put("terrain", new java.util.ArrayDeque<>(java.util.List.of(terrain)));
        var frame = ParticleRenderRouting.forFrame(simulation, "opaque", "lit", "translucent",
                p -> p == nearFlame || p == nearSoul);
        check(java.util.List.copyOf(frame.get("opaque")).equals(java.util.List.of(farFlame, enemyFlame)),
                "Only own nearby opaque particles leave the opaque render batch");
        check(java.util.List.copyOf(frame.get("translucent")).equals(java.util.List.of(lunarDust, marker, nearFlame, nearSoul)),
                "Fire and soul sprites enter the real alpha-blended batch exactly once");
        check(frame.get("lit").isEmpty() && frame.get("terrain") == simulation.get("terrain"),
                "Lit particles route correctly; terrain atlas is never replaced");
        check(java.util.List.copyOf(simulation.get("opaque")).equals(java.util.List.of(nearFlame, farFlame, enemyFlame))
                && simulation.get("lit").contains(nearSoul) && simulation.get("translucent").size() == 2,
                "Rendering must not mutate the queues used for ticking and lifetime");
        check(frame.values().stream().mapToInt(java.util.Queue::size).sum()
                == simulation.values().stream().mapToInt(java.util.Queue::size).sum(), "No particle duplicated or lost");
        var restored = ParticleRenderRouting.forFrame(simulation, "opaque", "lit", "translucent", p -> false);
        check(restored == simulation, "Camera/view/setting change restores original batches on the next frame");
        float fireAlpha = ParticleAvoidance.visibility(Strength.STANDARD, true, true, 1.4, 0.12f);
        check(fireAlpha == ParticleAvoidance.NEAR_VISIBILITY, "Own RED fire inside the near zone shows shadow-level visibility");
        check(ParticleAvoidance.visibility(Strength.STANDARD, true, true, 10, 0) == 0.5f,
                "Medium zero-size particles keep 50% at the 10-block edge");
        check(ParticleAvoidance.visibility(Strength.STANDARD, true, true, 10, 0.12f) > 0.49f
                && ParticleAvoidance.visibility(Strength.STANDARD, true, true, 10, 0.12f) < 0.51f,
                "Medium fire keeps 50% at the 10-block edge");
        check(ParticleAvoidance.visibility(Strength.STANDARD, true, true, 12.2, 0.12f) == 1,
                "Medium fire restores fully past the recovery band");
        simulation.remove("translucent");
        var onlyOpaque = ParticleRenderRouting.forFrame(simulation, "opaque", "lit", "translucent", p -> p == nearFlame);
        check(onlyOpaque.containsKey("translucent") && onlyOpaque.get("translucent").contains(nearFlame),
                "A translucent render batch exists even if no translucent particle was originally spawned");
    }

    private static void renderOpacity() {
        class Sample implements OwnedDecoration {
            float alpha = 0.8f;
            public void ssca$setDecorationOwner(java.util.UUID owner) {}
            public java.util.UUID ssca$getDecorationOwner() { return null; }
            public double ssca$cameraDistance(net.minecraft.client.render.Camera camera, float delta) { return 0; }
            public float ssca$cameraVisibility(net.minecraft.client.render.Camera camera, float delta) { return 1; }
            public float ssca$getRenderAlpha() { return alpha; }
            public void ssca$setRenderAlpha(float value) { alpha = value; }
        }
        var sample = new Sample();
        for (int frame = 0; frame < 100; frame++) {
            ParticleOpacity.draw(sample, 0.25f, () -> {
                check(sample.alpha == 0.2f, "Vanilla draw reads faded alpha");
                int packedAlpha = (int) (sample.alpha * 255);
                check(packedAlpha == 51, "Packed vertex renderer reads the same faded field without color() calls");
            });
            check(sample.alpha == 0.8f, "Repeated frames must not permanently compound fading");
        }
        ParticleOpacity.draw(sample, 0, () -> { throw new AssertionError("Hidden particles must submit no geometry"); });
        ParticleOpacity.draw(sample, 1, () -> check(sample.alpha == 0.8f, "Off/third-person retains lifetime alpha"));
        var failure = new IllegalStateException("draw failed");
        try {
            ParticleOpacity.draw(sample, 0.5f, () -> { throw failure; });
            throw new AssertionError("Draw failures must propagate");
        } catch (IllegalStateException expected) {
            check(expected == failure && sample.alpha == 0.8f, "Even failed draws restore original state");
        }
    }

    private static void configUi() throws Exception {
        var config = net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig.class;
        for (var field : config.getFields()) if (java.lang.reflect.Modifier.isStatic(field.getModifiers()))
            check(field.isAnnotationPresent(me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Excluded.class),
                    "Internal constants must not become editable AutoConfig fields: " + field.getName());
        var gson = new com.google.gson.Gson();
        for (String language : new String[]{"zh_cn", "en_us"}) {
            try (var stream = ParticleAvoidanceTest.class.getResourceAsStream("/assets/my_addon/lang/" + language + ".json")) {
                var translations = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                        java.util.Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (Strength strength : Strength.values()) {
                    check(translations.has(strength.toString()), "AutoConfig enum name resolves in " + language);
                    check(gson.toJson(strength).equals("\"" + strength.name() + "\""), "UI translation preserves saved enum values");
                    check(gson.fromJson("\"" + strength.name() + "\"", Strength.class) == strength, "Existing config remains readable");
                }
            }
        }
    }

    private static void injectionTargets() throws Exception {
        var manager = readClass("net/minecraft/client/particle/ParticleManager");
        var render = manager.methods.stream().filter(m -> m.name.equals("renderParticles")).findFirst().orElseThrow();
        int draws = 0;
        for (var instruction : render.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/particle/Particle")
                    && call.name.equals("buildGeometry") && call.desc.equals(
                    "(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V")) draws++;
        }
        check(draws == 1, "Opacity wraps the manager's draw call, outside any overwritten billboard renderer");
        var spawn = manager.methods.stream().filter(m -> m.name.equals("addParticle")
                && m.desc.equals("(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;"))
                .findFirst().orElseThrow();
        boolean returns = false;
        for (var instruction : spawn.instructions) if (instruction.getOpcode() == Opcodes.ARETURN) returns = true;
        check(returns, "Particle tagging has an actual RETURN injection site");
    }

    private static ClassNode readClass(String name) throws Exception {
        try (var stream = ParticleAvoidanceTest.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing Minecraft class " + name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
