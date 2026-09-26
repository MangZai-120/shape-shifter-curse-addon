package net.jackcooper.shapeShifterCurseAddon.client.particle;

import net.minecraft.client.particle.*;
import net.minecraft.client.render.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import java.util.UUID;

public final class ParticleIntegrationProbe {
    public static void run() throws Exception {
        var mods = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (mods.isModLoaded("iris") != Boolean.getBoolean("particle.expectShaders")
                || mods.isModLoaded("sodium") != Boolean.getBoolean("particle.expectShaders")
                || mods.isModLoaded("asyncparticles") != Boolean.getBoolean("particle.expectAsync"))
            throw new AssertionError("Requested compatibility combination was not loaded");
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var caster = (net.minecraft.server.network.ServerPlayerEntity) unsafe.allocateInstance(net.minecraft.server.network.ServerPlayerEntity.class);
        var spectator = (net.minecraft.server.network.ServerPlayerEntity) unsafe.allocateInstance(net.minecraft.server.network.ServerPlayerEntity.class);
        net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.run(caster, () -> {
            if (net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles.trySendScoped(null, spectator, null))
                throw new AssertionError("Other viewers must keep vanilla packets");
            try {
                net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.run(spectator, () -> {
                    throw new IllegalStateException("expected test failure");
                });
            } catch (IllegalStateException expected) {
                if (net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.owner() != caster)
                    throw new AssertionError("Nested failed particle action leaked its owner");
            }
        });
        if (net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.owner() != null)
            throw new AssertionError("Decoration action owner leaked into later skills");
        var manager = (ParticleManager) unsafe.allocateInstance(ParticleManager.class);
        Class.forName("net.minecraft.server.world.ServerWorld"); // Also transform the scoped packet hook.
        if (Arrays.stream(net.minecraft.server.world.ServerWorld.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().contains("ssca$sendDecoration"))) throw new AssertionError("Server packet hook missing");
        AsyncParticleCompatibility.init();
        var emitter = (Particle) unsafe.allocateInstance(ExplosionEmitterParticle.class);
        var owner = UUID.randomUUID();
        // 2026-09-26 用户定稿：火球粒子参与避让——owner 作用域内生成/发包均正常打标，豁免机制仅保留给真正的弹道提示
        var fireballFlame = (Particle) unsafe.allocateInstance(FlameParticle.class);
        FirstPersonParticles.emit(owner, () -> net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.protectedVisual(() -> {
            FirstPersonParticles.tag(fireballFlame);
            net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.protectedVisual(() -> {});
            if (!net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.isProtected())
                throw new AssertionError("Nested protection lost the outer protected scope");
        }));
        if (((ParticleOwnership) fireballFlame).ssca$getDecorationOwner() != null
                || net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.isProtected())
            throw new AssertionError("Protection scope tagged a particle or leaked into later visuals");
        var fireballTrail = (Particle) unsafe.allocateInstance(FlameParticle.class);
        FirstPersonParticles.emit(owner, () -> {
            // 无头环境无 player，认领逻辑（tag 的 player 匹配）只在真机生效；
            // 此处验证 emit 作用域内 source 正确流转（打标链路的上游）
            try {
                var sourceField = FirstPersonParticles.class.getDeclaredField("source");
                sourceField.setAccessible(true);
                if (!owner.equals(((ThreadLocal<?>) sourceField.get(null)).get()))
                    throw new AssertionError("Fireball trail emit lost its owner scope");
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new AssertionError("FirstPersonParticles.source reflection failed", e);
            }
            FirstPersonParticles.tag(fireballTrail);
        });
        if (net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope.isProtected())
            throw new AssertionError("Fireball trail path leaked protection");
        ((ParticleOwnership) emitter).ssca$setDecorationOwner(owner);
        var child = new Sample();
        ((ParticleOwnership) child).ssca$setDecorationOwner(owner);
        if (!owner.equals(((OwnedDecoration) child).ssca$getDecorationOwner()))
            throw new AssertionError("Billboard did not inherit Particle ownership");
        var tick = Arrays.stream(ParticleManager.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("ssca$inheritDecoration") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == Operation.class).findFirst().orElseThrow();
        tick.setAccessible(true);
        var source = FirstPersonParticles.class.getDeclaredField("source");
        source.setAccessible(true);
        Operation<Void> emitChildren = ignored -> {
            try {
                var current = ((ThreadLocal<?>) source.get(null)).get();
                if (!owner.equals(current)) throw new AssertionError("Emitter tick lost child ownership scope");
            } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            return null;
        };
        tick.invoke(manager, emitter, emitChildren);
        if (((ThreadLocal<?>) source.get(null)).get() != null) throw new AssertionError("Emitter ownership leaked beyond its tick");
        var draw = Arrays.stream(ParticleManager.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("ssca$drawWithOpacity") && m.getParameterCount() == 5
                        && m.getParameterTypes()[4] == Operation.class).findFirst().orElseThrow();
        draw.setAccessible(true);
        var buffer = new BufferBuilder(256);
        Operation<Void> geometry = a -> { ((Particle) a[0]).buildGeometry((VertexConsumer) a[1], (Camera) a[2], (float) a[3]); return null; };
        // 视觉覆盖必须分派到 Sample 的重写（绘制路径经 OwnedDecoration 接口取 visibility）。
        // 接口注入已由上方 (ParticleOwnership) 强转验证，下方 draw 循环也会经 mixin 走真实接口分派；
        // 这里改为直接调用以真正消除「重写从未被本地调用」的 IDE 警告——经接口调用时静态目标是接口方法，
        // 编译器不会把这次调用记到 Sample 的重写头上（上一版尝试因此没能消掉警告）
        child.visibility = 0.5f;
        if (child.ssca$cameraVisibility(new Camera(), 0.5f) != 0.5f)
            throw new AssertionError("Visibility override not dispatched through OwnedDecoration");
        for (float v : new float[]{0, 0.025f, 0.1f, 0.5f, 1}) {
            child.visibility = v;
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
            draw.invoke(manager, child, buffer, new Camera(), 0.5f, geometry);
            var built = buffer.end();
            if (v == 0) {
                if (!built.isEmpty()) throw new AssertionError("Hidden particle emitted vertices");
            } else for (int i = 0; i < 4; i++) {
                int alpha = Byte.toUnsignedInt(built.getVertexBuffer().get(i * VertexFormats.POSITION_TEXTURE_COLOR_LIGHT.getVertexSizeByte() + 23));
                if (alpha != (int) (0.8f * v * 255)) throw new AssertionError("Incorrect packed vertex alpha");
            }
            built.release();
            if (((OwnedDecoration) child).ssca$getRenderAlpha() != 0.8f) throw new AssertionError("Persistent alpha changed");
        }
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("asyncparticles")) {
            for (String flag : new String[]{"asyncparticles$isTickSync", "asyncparticles$isRenderSync"})
                if (!(boolean) Particle.class.getMethod(flag).invoke(emitter)) throw new AssertionError("Tagged emitter still uses asynchronous path");
            var flame = (Particle) unsafe.allocateInstance(FlameParticle.class);
            if ((boolean) Particle.class.getMethod("asyncparticles$isTickSync").invoke(flame))
                throw new AssertionError("Untagged flame acceleration changed");
        }
        System.out.println("Particle integration passed: server packet hook, nested action scope, emitter ownership scope, billboard inheritance, real vertex alpha and restoration; iris="
                + mods.isModLoaded("iris") + ", async=" + mods.isModLoaded("asyncparticles") + ". No OpenGL test.");
    }

    private static class Sample extends BillboardParticle {
        float visibility;
        Sample() { super(null, 0, 0, 1); alpha = 0.8f; }
        public float ssca$cameraVisibility(Camera camera, float delta) { return visibility; }
        public float getSize(float delta) { return 0.1f; }
        public int getBrightness(float delta) { return 0xF000F0; }
        protected float getMinU() { return 0; }
        protected float getMaxU() { return 1; }
        protected float getMinV() { return 0; }
        protected float getMaxV() { return 1; }
        public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE; }
    }
}
