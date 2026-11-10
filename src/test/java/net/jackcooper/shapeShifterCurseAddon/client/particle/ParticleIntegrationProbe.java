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
                || mods.isModLoaded("asyncparticles") != Boolean.getBoolean("particle.expectAsync")
                || mods.isModLoaded("particle_core") != Boolean.getBoolean("particle.expectCore"))
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
        checkMistOwnership(unsafe, manager, tick, caster, owner);
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
        for (float v : new float[]{0, 0.025f, 0.1f, 0.2f, 0.5f, 0.8f, 1}) {
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
        if (mods.isModLoaded("asyncparticles")) checkAsyncCompatibility(unsafe, owner, emitter);
        System.out.println("Particle integration passed: server packet hook, nested action scope, emitter ownership scope, billboard inheritance, real vertex alpha and restoration; iris="
                + mods.isModLoaded("iris") + ", async=" + mods.isModLoaded("asyncparticles")
                + ", core=" + mods.isModLoaded("particle_core") + ". No OpenGL test.");
    }

    /** Exercise the real skill owner and manager tagging hook, not just manually assigned metadata. */
    private static void checkMistOwnership(sun.misc.Unsafe unsafe, ParticleManager manager,
                                           java.lang.reflect.Method tick,
                                           net.minecraft.server.network.ServerPlayerEntity caster,
                                           UUID owner) throws Exception {
        caster.setUuid(owner);
        var power = unsafe.allocateInstance(net.jackcooper.shapeShifterCurseAddon.power.MistFormAbilityPower.class);
        var entityField = io.github.apace100.apoli.power.Power.class.getDeclaredField("entity");
        entityField.setAccessible(true);
        entityField.set(power, caster);
        var visualOwner = power.getClass().getDeclaredMethod("visualOwner");
        visualOwner.setAccessible(true);
        if (!owner.equals(visualOwner.invoke(power)))
            throw new AssertionError("Mist tick lost its caster outside an Apoli decoration scope");

        // A headless client supplies only the identity needed by tag(); no world/window/GL is created.
        var clientClass = net.minecraft.client.MinecraftClient.class;
        var singleton = Arrays.stream(clientClass.getDeclaredFields())
                .filter(f -> f.getType() == clientClass && java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .findFirst().orElseThrow();
        singleton.setAccessible(true);
        Object previousClient = singleton.get(null);
        var client = (net.minecraft.client.MinecraftClient) unsafe.allocateInstance(clientClass);
        client.player = (net.minecraft.client.network.ClientPlayerEntity) unsafe.allocateInstance(
                net.minecraft.client.network.ClientPlayerEntity.class);
        client.player.setUuid(owner);
        var options = clientClass.getDeclaredField("options");
        options.setAccessible(true);
        options.set(client, unsafe.allocateInstance(net.minecraft.client.option.GameOptions.class));
        client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
        var tagHook = Arrays.stream(ParticleManager.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("ssca$tag") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == Particle.class).findFirst().orElseThrow();
        tagHook.setAccessible(true);
        try {
            singleton.set(null, client);
            for (Class<?> kind : new Class<?>[]{CloudParticle.class, CampfireSmokeParticle.class,
                    RedDustParticle.class, ExplosionLargeParticle.class}) {
                var own = (Particle) unsafe.allocateInstance(kind);
                var foreign = (Particle) unsafe.allocateInstance(kind);
                var ordinary = (Particle) unsafe.allocateInstance(kind);
                FirstPersonParticles.emit(owner, () -> invokeTag(tagHook, manager, own));
                FirstPersonParticles.emit(UUID.randomUUID(), () -> invokeTag(tagHook, manager, foreign));
                invokeTag(tagHook, manager, ordinary);
                var metadata = (ParticleOwnership) own;
                if (!owner.equals(metadata.ssca$getDecorationOwner())
                        || metadata.ssca$isProjectileDecoration())
                    throw new AssertionError("Mist particle was not recognized at manager insertion: " + kind);
                if (((ParticleOwnership) foreign).ssca$getDecorationOwner() != null
                        || ((ParticleOwnership) ordinary).ssca$getDecorationOwner() != null)
                    throw new AssertionError("Mist scope affected another player's or an unowned particle: " + kind);
                var descendant = (Particle) unsafe.allocateInstance(kind);
                Operation<Void> createChild = ignored -> { invokeTag(tagHook, manager, descendant); return null; };
                tick.invoke(manager, own, createChild);
                if (!owner.equals(((ParticleOwnership) descendant).ssca$getDecorationOwner()))
                    throw new AssertionError("Mist child did not inherit its actual parent's metadata: " + kind);
                checkMistVisibility(client, own, foreign);
            }
            try {
                FirstPersonParticles.emit(owner, () -> { throw new IllegalStateException("expected"); });
            } catch (IllegalStateException expected) {
                var afterFailure = (Particle) unsafe.allocateInstance(CloudParticle.class);
                invokeTag(tagHook, manager, afterFailure);
                if (((ParticleOwnership) afterFailure).ssca$getDecorationOwner() != null)
                    throw new AssertionError("Failed mist emission leaked into unrelated particles");
            }
        } finally {
            AsyncParticleCompatibility.end();
            singleton.set(null, previousClient);
        }
        System.out.println("Mist ownership passed: caster outside action scope, four real particle classes, "
                + "manager insertion, child inheritance, all four avoidance strengths, perspective/owner isolation and exception cleanup.");
    }

    private static void checkMistVisibility(net.minecraft.client.MinecraftClient client,
                                            Particle own, Particle foreign) throws Exception {
        var camera = new Camera();
        var focus = Camera.class.getDeclaredField("focusedEntity");
        focus.setAccessible(true);
        focus.set(camera, client.player);
        own.setMaxAge(20);
        // Off the crosshair cone so the existing STANDARD sample lottery cannot mask the distance tiers.
        own.setPos(8, 0, 0);
        var previousX = Particle.class.getDeclaredField("prevPosX");
        previousX.setAccessible(true);
        previousX.setDouble(own, 8);
        var decoration = (OwnedDecoration) own;
        float[] visibility = new float[4];
        for (ParticleAvoidance.Strength strength : ParticleAvoidance.Strength.values()) {
            AsyncParticleCompatibility.snapshotStrength(strength);
            visibility[strength.ordinal()] = decoration.ssca$cameraVisibility(camera, .5f);
            if (((OwnedDecoration) foreign).ssca$cameraVisibility(camera, .5f) != 1)
                throw new AssertionError("Another player's mist was faded by " + strength);
        }
        if (visibility[0] != 1 || visibility[1] != 1 || visibility[2] < 0 || visibility[2] >= 1
                || visibility[3] < 0 || visibility[3] > visibility[2])
            throw new AssertionError("Mist does not follow the normal 0/4/10/16-block settings: "
                    + Arrays.toString(visibility));
        client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
        if (decoration.ssca$cameraVisibility(camera, .5f) != 1)
            throw new AssertionError("Third-person mist must stay fully visible");
        client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
        focus.set(camera, null);
        if (decoration.ssca$cameraVisibility(camera, .5f) != 1)
            throw new AssertionError("A camera focused on someone else must keep the mist visible");
    }

    private static void invokeTag(java.lang.reflect.Method hook, ParticleManager manager, Particle particle) {
        try {
            hook.invoke(manager, particle, null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Actual particle-manager tagging hook failed", failure);
        }
    }

    private static void checkAsyncCompatibility(sun.misc.Unsafe unsafe, UUID owner, Particle emitter) throws Exception {
        checkAsyncQueueRouting();
        // Establish thread identity only; this does not initialize GLFW or an OpenGL context.
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())
            com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        var renderSync = Particle.class.getMethod("asyncparticles$isRenderSync");
        if (!(boolean) renderSync.invoke(emitter)) throw new AssertionError("Tagged emitter lost render sync");
        Class<?> behavior;
        try {
            behavior = Class.forName("fabric.fun.qu_an.minecraft.asyncparticles.client.core.particle.gpu_acceleration.GpuParticleBehavior");
        } catch (ClassNotFoundException legacy) {
            behavior = Class.forName("fabric.fun.qu_an.minecraft.asyncparticles.client.particle.GpuParticleBehavior");
        }
        var singleton = behavior.getDeclaredField("INSTANCE");
        singleton.setAccessible(true);
        var gpu = singleton.get(null);
        var eligible = behavior.getMethod("canRenderFast", SpriteBillboardParticle.class);
        var unowned = (Particle) unsafe.allocateInstance(FlameParticle.class);
        if (!(boolean) eligible.invoke(gpu, unowned)) throw new AssertionError("Unowned flame lost GPU acceleration");
        if ((boolean) renderSync.invoke(unowned)) throw new AssertionError("Unowned flame was pinned to CPU rendering");
        java.lang.reflect.Method tickSync;
        try {
            tickSync = Particle.class.getMethod("asyncparticles$isTickSync");
        } catch (NoSuchMethodException modern) {
            tickSync = null; // The removed legacy API must not disable render compatibility.
        }
        // Real mod classification after its class cache is warm; exceed the former 96-particle quota.
        for (int i = 0; i < 1024; i++) {
            var owned = (Particle) unsafe.allocateInstance(FlameParticle.class);
            ((ParticleOwnership) owned).ssca$setDecorationOwner(owner);
            if (!(boolean) renderSync.invoke(owned)) throw new AssertionError("Burst particle lost render sync at index " + i);
            if ((boolean) eligible.invoke(gpu, owned)) throw new AssertionError("Burst particle remained GPU eligible at index " + i);
            if (tickSync != null && (boolean) tickSync.invoke(owned))
                throw new AssertionError("Decoration forced synchronous ticking at index " + i);
        }
        if (!(boolean) eligible.invoke(gpu, unowned)) throw new AssertionError("Owned particles poisoned the class cache");
        System.out.println("Async compatibility passed: " + behavior.getName()
                + ", 1024 owned flames use CPU rendering, unowned flames retain GPU, tick sync unchanged.");
    }

    private static void checkAsyncQueueRouting() throws Exception {
        var queueClass = Class.forName("fabric.fun.qu_an.minecraft.asyncparticles.client.util.IterationSafeEvictingQueue");
        var constructor = queueClass.getConstructor(int.class, int.class);
        var task = new java.util.concurrent.FutureTask<Void>(() -> {
            for (int count : new int[]{1, 1024}) for (int mode = 0; mode < 4; mode++) {
                @SuppressWarnings("unchecked")
                var opaque = (java.util.Queue<Object>) constructor.newInstance(16, 2048);
                @SuppressWarnings("unchecked")
                var lit = (java.util.Queue<Object>) constructor.newInstance(16, 2048);
                @SuppressWarnings("unchecked")
                var translucent = (java.util.Queue<Object>) constructor.newInstance(16, 2048);
                var owned = new Object();
                var secondOwned = new Object();
                var ordinary = new Object();
                var existing = new Object();
                if (mode == 2) {
                    opaque.add(ordinary);
                    lit.add(owned);
                } else {
                    opaque.add(owned);
                    if (mode == 1) lit.add(ordinary);
                    if (mode == 3) lit.add(secondOwned);
                }
                var burst = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
                burst.add(owned);
                burst.add(secondOwned);
                for (int i = 1; i < count; i++) {
                    var particle = new Object();
                    burst.add(particle);
                    (mode == 2 ? lit : opaque).add(particle);
                }
                translucent.add(existing);
                var original = java.util.Map.of("opaque", opaque, "lit", lit, "translucent", translucent);
                var frame = ParticleRenderRouting.forFrame(original, "opaque", "lit", "translucent", burst::contains);
                if (!frame.get("translucent").contains(owned) || !frame.get("translucent").contains(existing))
                    throw new AssertionError("Async queue lost blended particles in mode " + mode);
                if (frame.get("translucent").size() != count + 1 + (mode == 3 ? 1 : 0))
                    throw new AssertionError("Async queue lost or duplicated burst particles in mode " + mode);
                if (frame.get("opaque").contains(owned) || frame.get("lit").contains(owned))
                    throw new AssertionError("Async queue drew owned particle twice in mode " + mode);
                if ((mode == 1 && frame.get("lit") != lit) || (mode == 2 && frame.get("opaque") != opaque))
                    throw new AssertionError("Unchanged Async queue should be retained in mode " + mode);
                if (translucent.size() != 1 || !translucent.contains(existing)
                        || (mode == 2 ? lit : opaque).size() != count)
                    throw new AssertionError("Routing mutated the simulation queues in mode " + mode);
            }
            return null;
        });
        // A pre-fix stream can spin forever. A daemon plus timeout makes that failure bounded.
        var worker = new Thread(task, "SSCA-async-queue-regression");
        worker.setDaemon(true);
        worker.start();
        try {
            task.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException failure) {
            throw new AssertionError("Particle routing hung on real AsyncParticles queues (empty/unaffected sheet)", failure);
        }
        System.out.println("Async queue routing passed: empty, unaffected and mixed texture sheets terminate without mutating simulation queues.");
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
