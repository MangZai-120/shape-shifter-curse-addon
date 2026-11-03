package net.ssca.neomagic;

import java.util.List;
import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.FrozenAnimationState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Exercises real client pose interception without spawning or editing a tracked entity. */
public final class FrozenRenderScratchProbe {
    private final PigEntity pig;
    private final RuntimeException expectedFailure = new RuntimeException("Expected scratch probe failure");
    private int checks;

    private FrozenRenderScratchProbe(MinecraftClient client) {
        if (client.world == null) throw new AssertionError("Scratch probe requires a client world");
        pig = new PigEntity(EntityType.PIG, client.world);
        pig.age = 100;
        pig.addStatusEffect(new StatusEffectInstance(SscAddon.FROZEN, 100));
    }

    public static int run(MinecraftClient client) {
        var probe = new FrozenRenderScratchProbe(client);
        Entity previous = FrozenAnimationState.renderingEntity();
        try {
            probe.relocationAndRestore();
            probe.nestedScopes();
            probe.occurrences();
            probe.normalAndExceptions();
            probe.transformCompletion();
            Object[] keys = new Object[70];
            for (int i = 0; i < keys.length; i++) keys[i] = new Object();
            for (int pass = 0; pass < 2; pass++) {
                int root = pass == 0 ? 10 : 40;
                var matrices = translated(root, 0, 0);
                probe.render(() -> FrozenAnimationState.root(matrices,
                        () -> probe.deep(matrices, keys, 0, root)));
            }
            probe.partAndThaw();
            probe.check(FrozenAnimationState.renderingEntity() == previous, "Render entity context restored");
            return probe.checks;
        } finally {
            pigCleanup(probe.pig);
        }
    }

    private static void pigCleanup(PigEntity pig) {
        pig.removeStatusEffect(SscAddon.FROZEN);
        pig.age += 8;
        FrozenAnimationState.render(pig, 0, ignored -> {});
    }

    private void render(Runnable draw) {
        FrozenAnimationState.render(pig, 0, ignored -> draw.run());
    }

    private void relocationAndRestore() {
        Object key = new Object();
        for (int pass = 0; pass < 3; pass++) {
            int offset = pass * 30;
            var matrices = translated(10 + offset, 20 + offset, 30 + offset);
            render(() -> FrozenAnimationState.root(matrices, () -> {
                matrices.translate(2 + offset, 3 + offset, 4 + offset);
                Matrix4f live = new Matrix4f(matrices.peek().getPositionMatrix());
                FrozenAnimationState.matrix(key, matrices,
                        () -> position(matrices, 12 + offset, 23 + offset, 34 + offset, "Root relocation"));
                check(live.equals(matrices.peek().getPositionMatrix(), 0.0001f), "Normal matrix scope restores live position");
            }));
        }
    }

    private void nestedScopes() {
        Object outerKey = new Object(), innerKey = new Object(), tailKey = new Object();
        for (int pass = 0; pass < 2; pass++) {
            int outerRoot = pass == 0 ? 10 : 40;
            int innerRoot = pass == 0 ? 100 : 400;
            int liveOffset = pass == 0 ? 2 : 9;
            var outer = translated(outerRoot, 0, 0);
            render(() -> FrozenAnimationState.root(outer, () -> {
                outer.translate(liveOffset, 0, 0);
                FrozenAnimationState.matrix(outerKey, outer, () -> {
                    position(outer, outerRoot + 2, 0, 0, "Outer frozen scope");
                    var inner = translated(innerRoot, 0, 0);
                    FrozenAnimationState.root(inner, () -> {
                        inner.translate(liveOffset + 2, 0, 0);
                        FrozenAnimationState.matrix(innerKey, inner,
                                () -> position(inner, innerRoot + 4, 0, 0, "Nested root scope"));
                    });
                    position(outer, outerRoot + 2, 0, 0, "Inner scratch never overwrites outer backup");
                    fails(() -> FrozenAnimationState.root(inner, () -> { throw expectedFailure; }));
                    outer.push();
                    try {
                        outer.translate(1, 0, 0);
                        FrozenAnimationState.matrix(tailKey, outer,
                                () -> position(outer, outerRoot + 3, 0, 0, "Previous root restored after inner failure"));
                    } finally { outer.pop(); }
                });
                position(outer, outerRoot + liveOffset, 0, 0, "Nested scope restores original outer live matrix");
            }));
        }
    }

    private void occurrences() {
        Object key = new Object();
        for (int pass = 0; pass < 2; pass++) {
            int root = pass == 0 ? 0 : 100;
            int liveExtra = pass == 0 ? 0 : 50;
            var matrices = translated(root, root, root);
            render(() -> FrozenAnimationState.root(matrices, () -> {
                for (int occurrence = 0; occurrence < 2; occurrence++) {
                    int x = occurrence == 0 ? 2 : 5;
                    matrices.push();
                    try {
                        matrices.translate(x + liveExtra, x + 1 + liveExtra, x + 2 + liveExtra);
                        FrozenAnimationState.matrix(key, matrices, () -> position(matrices,
                                root + x, root + x + 1, root + x + 2, "Same-key occurrence remains distinct"));
                    } finally { matrices.pop(); }
                }
            }));
        }
    }

    private void normalAndExceptions() {
        Object key = new Object();
        for (int pass = 0; pass < 2; pass++) {
            var matrices = translated(7, 8, 9);
            matrices.scale(2, 3, 4);
            boolean failing = pass != 0;
            render(() -> FrozenAnimationState.root(matrices, () -> {
                matrices.scale(5, 6, 7);
                Matrix4f position = new Matrix4f(matrices.peek().getPositionMatrix());
                Matrix3f normal = new Matrix3f(matrices.peek().getNormalMatrix());
                Runnable draw = () -> {
                    matrices.translate(20, 30, 40);
                    matrices.scale(8, 9, 10);
                    if (failing) throw expectedFailure;
                };
                if (failing) fails(() -> FrozenAnimationState.matrix(key, matrices, draw));
                else FrozenAnimationState.matrix(key, matrices, draw);
                check(position.equals(matrices.peek().getPositionMatrix(), 0.0001f), "Position restored after normal/exception draw");
                check(normal.equals(matrices.peek().getNormalMatrix(), 0.0001f), "Nonuniform normal restored after normal/exception draw");
            }));
        }
    }

    private void transformCompletion() {
        Object key = new Object();
        for (int pass = 0; pass < 3; pass++) {
            int phase = pass;
            var matrices = new MatrixStack();
            render(() -> FrozenAnimationState.root(matrices, () -> {
                matrices.translate(phase == 0 ? 2 : 9, 0, 0);
                Runnable draw = () -> {
                    position(matrices, 2, 0, 0, "Transform sees frozen matrix");
                    if (phase == 2) { matrices.translate(50, 0, 0); throw expectedFailure; }
                };
                if (phase == 2) fails(() -> FrozenAnimationState.transform(key, matrices, draw));
                else FrozenAnimationState.transform(key, matrices, draw);
                position(matrices, phase == 2 ? 9 : 2, 0, 0, "Transform leaves successful output and restores failed output");
            }));
        }
    }

    private void deep(MatrixStack matrices, Object[] keys, int depth, int root) {
        if (depth == keys.length) return;
        matrices.push();
        try {
            matrices.translate(1, 0, 0);
            FrozenAnimationState.matrix(keys[depth], matrices, () -> {
                position(matrices, root + depth + 1, 0, 0, "Deep scope before nested lease");
                deep(matrices, keys, depth + 1, root);
                position(matrices, root + depth + 1, 0, 0, "Deep scope after nested lease");
            });
        } finally { matrices.pop(); }
    }

    private void partAndThaw() {
        var part = new ModelPart(List.of(), Map.of());
        part.pitch = 0.4f;
        render(() -> FrozenAnimationState.part(part, () -> near(part.pitch, 0.4f, "Capture frozen part")));
        part.pitch = 1.2f;
        render(() -> FrozenAnimationState.part(part, () -> near(part.pitch, 0.4f, "Frozen part ignores live pitch")));
        near(part.pitch, 1.2f, "Frozen part restores live pitch");
        render(() -> fails(() -> FrozenAnimationState.part(part, () -> { throw expectedFailure; })));
        near(part.pitch, 1.2f, "Part exception restores live pitch");
        pig.removeStatusEffect(SscAddon.FROZEN);
        int thawAge = pig.age;
        for (int tick : new int[]{0, 2, 4}) {
            pig.age = thawAge + tick;
            float expected = tick == 0 ? 0.4f : tick == 2 ? 0.8f : 1.2f;
            render(() -> FrozenAnimationState.part(part, () -> near(part.pitch, expected, "Four-tick thaw at " + tick)));
            near(part.pitch, 1.2f, "Thaw restores live pitch at " + tick);
        }
    }

    private static MatrixStack translated(double x, double y, double z) {
        var matrices = new MatrixStack();
        matrices.translate(x, y, z);
        return matrices;
    }

    private void position(MatrixStack matrices, float x, float y, float z, String message) {
        var position = matrices.peek().getPositionMatrix();
        near(position.m30(), x, message + " x");
        near(position.m31(), y, message + " y");
        near(position.m32(), z, message + " z");
    }

    private void fails(Runnable draw) {
        try { draw.run(); } catch (RuntimeException failure) {
            check(failure == expectedFailure, "Original draw exception propagates");
            return;
        }
        throw new AssertionError("Expected draw exception was swallowed");
    }

    private void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.0001f, message + ": " + actual + " != " + expected);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
