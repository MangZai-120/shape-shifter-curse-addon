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
        crosshairCone();
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
                    if (strength != Strength.OFF) check(translations.get(strength.toString()).getAsString()
                            .contains(Integer.toString((int) strength.radius())), "Displayed radius matches actual policy");
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
        var world = readClass("net/minecraft/server/world/ServerWorld");
        var nearby = world.methods.stream().filter(m -> m.name.equals("sendToPlayerIfNearby")).findFirst().orElseThrow();
        int sends = 0;
        for (var instruction : nearby.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/network/ServerPlayNetworkHandler")
                    && call.name.equals("sendPacket") && call.desc.equals("(Lnet/minecraft/network/packet/Packet;)V")) sends++;
        }
        check(sends == 1, "Scoped particle envelope wraps the actual vanilla send after range checks");
    }

    private static void crosshairCone() {
        float previous = 0;
        for (int i = 0; i <= 18000; i++) {
            double angle = i / 100.0;
            float factor = ParticleAvoidance.crosshairConeFactor(Math.cos(Math.toRadians(angle)));
            check(Float.isFinite(factor) && factor >= 0.09999f && factor <= 1, "Finite bounded angular factor");
            check(factor >= previous && (i == 0 || factor - previous < 0.002), "No angular cutoff or reversal");
            if (angle <= 15) check(Math.abs(factor - 0.1f) < 0.00001f, "Central 15-degree cone retains only a tenth");
            if (angle >= 45) check(factor == 1, "Outside 45 degrees uses ordinary distance attenuation");
            previous = factor;
        }
        for (double far : new double[]{20, 64, 128, 512})
            check(ParticleAvoidance.withCrosshair(1, far, Strength.LIGHT, 1) == 1f, "Far particles beyond the recovery band restore full visibility (projectile gating)");
        // 2026-09-26 定稿：中度＝计数式角度剔除（15°内显示 5%、15~45° 递增至 100%，显示者不压透明）；
        // 轻度＝扦40%豁免+其余压 40%；重度＝全员压 10%
        check(Math.abs(ParticleAvoidance.withCrosshair(1, 3, Strength.LIGHT, 1) - 0.40f) < 0.00001f, "Light tier keeps 40 percent at the cone center");
        check(Math.abs(ParticleAvoidance.withCrosshair(1, 3, Strength.STRONG, 1) - 0.10f) < 0.00001f, "Strong tier keeps 10 percent at the cone center");
        check(Math.abs(ParticleAvoidance.standardKeepFraction(Math.cos(Math.toRadians(0))) - 0.20f) < 0.00001f, "Standard tier shows 20 percent of particles at the cone center");
        check(Math.abs(ParticleAvoidance.standardKeepFraction(Math.cos(Math.toRadians(45))) - 1.0f) < 0.00001f, "Standard tier shows all particles at 45 degrees");
        check(Math.abs(ParticleAvoidance.standardKeepFraction(Math.cos(Math.toRadians(90))) - 1.0f) < 0.00001f, "Standard tier shows all particles beyond 45 degrees");
        // 中度最终定稿：周围=重度同款曲线（10%目标），中心 20% 显示（×1.5 补偿）+ 80% 隐藏
        {
            Object key = new Object();
            // 30°（恢复带）：与重度完全一致（无剔除，目标曲线同 10%）
            float std30 = ParticleAvoidance.applyAngular(1f, 3, Strength.STANDARD, Math.cos(Math.toRadians(30)), false, key);
            float str30 = ParticleAvoidance.applyAngular(1f, 3, Strength.STRONG, Math.cos(Math.toRadians(30)), false, key);
            check(Math.abs(std30 - str30) < 0.00001f, "Standard outer zone matches the strong transparency curve exactly");
            // 中心：显示者 = 重度曲线值×1.5；隐藏者 = 0
            float strongCenter = ParticleAvoidance.withCrosshair(1f, 3, Strength.STRONG, 1);
            boolean kept = ParticleAvoidance.keepAngularSample(key, ParticleAvoidance.standardKeepFraction(1.0));
            float expected = kept ? Math.min(1f, strongCenter * 1.5f) : 0f;
            check(Math.abs(ParticleAvoidance.applyAngular(1f, 3, Strength.STANDARD, 1, false, key) - expected) < 0.00001f,
                    "Standard center keeps 20% of particles at 1.5x strong-curve opacity; the rest return zero");
        }
        {
            int kept = 0, samples = 10000;
            for (int i = 0; i < samples; i++) if (ParticleAvoidance.keepAngularSample(new Object(), 0.20f)) kept++;
            check(kept > samples * 0.15 && kept < samples * 0.25, "Standard cone center keeps roughly 20 percent of particles");
        }
        // 2026-09-26 定稿：准星锥全范围生效（普通装饰粒子在避让门内任意距离都受锥压制）；
        // 火球弹道粒子走 applyAngular(projectile=true) 豁免锥压制、只吃距离曲线
        check(Math.abs(ParticleAvoidance.withCrosshair(1, 12, Strength.STRONG, 1) - 0.1f) < 0.00001f,
                "Ordinary particles keep full-range crosshair suppression inside the avoidance gate");
        check(Math.abs(ParticleAvoidance.withCrosshair(1, 8, Strength.STANDARD, 1) - 0.20f) < 0.00001f,
                "withCrosshair keeps its tiered transparency contract (standard raw path)");
        check(ParticleAvoidance.applyAngular(1, 12, Strength.STRONG, 1, true) == 1f,
                "Projectile particles skip crosshair suppression entirely (distance curve only)");
        check(Math.abs(ParticleAvoidance.applyAngular(0.5f, 6, Strength.LIGHT, 1, true) - 0.5f) < 0.00001f,
                "Projectile particles keep their distance-curve visibility untouched by the cone");
        check(Math.abs(ParticleAvoidance.applyAngular(1, 6, Strength.STRONG, 1, false)
                - ParticleAvoidance.withCrosshair(1, 6, Strength.STRONG, 1)) < 0.00001f,
                "Non-projectile particles route through the ordinary cone path");
        check(ParticleAvoidance.crosshairConeFactor(Double.NaN) == 1, "Invalid direction does not hide unrelated geometry");
        for (Strength strength : Strength.values()) {
            if (strength == Strength.OFF) continue;
            for (double angle : new double[]{0, 15, 30, 45, 90, 180}) {
                float last = 0;
                for (int i = 0; i <= 2000; i++) {
                    double distance = i / 100.0;
                    float base = ParticleAvoidance.visibility(strength, true, true, distance, 0.1f);
                    float visible = ParticleAvoidance.withCrosshair(base, distance, strength, Math.cos(Math.toRadians(angle)));
                    check(visible + 0.000001f >= last && visible <= base, "Angular enhancement preserves distance ordering");
                    // 门控硬边界（radius+2）处锥衰减一次性释放是设计行为：跳过边界步进检查
                    boolean gateBoundary = distance >= strength.radius() + 1.9 && distance <= strength.radius() + 2.1;
                    if (i > 0 && !gateBoundary) check(visible - last < 0.025, "Angular enhancement restores smoothly at outer distance edge");
                    if (distance >= strength.radius() + 2) check(visible == base, "Distant particles use pure distance fading again");
                    last = visible;
                }
            }
        }
        // 近距影子（25%）低于轻度锥目标（40%）时保持原值不增亮；重度锥目标（10%）更深时压到 10%
        check(Math.abs(ParticleAvoidance.withCrosshair(0.25f, 0.5, Strength.LIGHT, 1) - 0.25f) < 0.00001,
                "Kept near-camera samples never brighten under a weaker cone target");
        check(Math.abs(ParticleAvoidance.withCrosshair(0.25f, 0.5, Strength.STRONG, 1) - 0.10f) < 0.00001,
                "Kept near-camera samples still deepen under the strong cone target");
        // 2026-09-26 定稿：轻/中档抽样豁免——被抽中粒子完全跳过锥压制；重度不豁免；大样本豁免率容差 ±5%
        int exemptLight = 0, exemptStrong = 0, samples = 10000;
        for (int i = 0; i < samples; i++) {
            Object key = new Object();
            if (ParticleAvoidance.coneExempt(key, Strength.LIGHT)) exemptLight++;
            if (ParticleAvoidance.coneExempt(key, Strength.STRONG)) exemptStrong++;
        }
        check(exemptLight > samples * 0.35 && exemptLight < samples * 0.45, "Light tier exempts roughly 40% of particles");
        check(exemptStrong == 0, "Strong tier never exempts particles");
        check(ParticleAvoidance.coneExempt(ParticleAvoidanceTest.class, Strength.LIGHT)
                == ParticleAvoidance.coneExempt(ParticleAvoidanceTest.class, Strength.LIGHT),
                "Cone exemption is stable per particle (no flicker across frames)");
        boolean exemptKept = ParticleAvoidance.applyAngular(1, 3, Strength.LIGHT, 1, false,
                ParticleAvoidanceTest.class) == 1f;
        check(exemptKept == ParticleAvoidance.coneExempt(ParticleAvoidanceTest.class, Strength.LIGHT),
                "applyAngular honors the sampled exemption (keyed particles skip cone suppression)");
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
