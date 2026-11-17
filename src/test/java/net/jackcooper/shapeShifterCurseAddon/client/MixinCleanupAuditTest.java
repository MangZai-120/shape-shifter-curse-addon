package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.compat.MixinSelection;
import net.jackcooper.shapeShifterCurseAddon.util.IrisShadowPassDetector;

/** Nested render, exception and optional dependency regressions for the Mixin cleanup. */
public final class MixinCleanupAuditTest {
    private static int checks;
    public static int run() throws Exception {
        Object outer = new Object(), inner = new Object();
        check(!IrisShadowPassDetector.hasSuppressScope(), "No stale render scope at entry");
        try (var first = IrisShadowPassDetector.suppressFor(outer)) {
            check(IrisShadowPassDetector.shouldSuppressPowerQuery(outer), "Outer entity is scoped");
            check(!IrisShadowPassDetector.shouldSuppressPowerQuery(inner), "Other entities are untouched");
            try (var second = IrisShadowPassDetector.suppressFor(inner)) {
                check(IrisShadowPassDetector.shouldSuppressPowerQuery(inner), "Nested entity is scoped");
                check(!IrisShadowPassDetector.shouldSuppressPowerQuery(outer), "Nested render does not suppress its parent");
            }
            check(IrisShadowPassDetector.shouldSuppressPowerQuery(outer), "Nested return restores the outer scope");
            try (var mainPass = IrisShadowPassDetector.suppressFor(null)) {
                check(!IrisShadowPassDetector.shouldSuppressPowerQuery(outer), "Nested normal pass is untouched");
            }
            check(IrisShadowPassDetector.shouldSuppressPowerQuery(outer), "Normal pass restores the parent");
            boolean[] isolated = {false};
            Thread other = new Thread(() -> isolated[0] = !IrisShadowPassDetector.shouldSuppressPowerQuery(outer));
            other.start(); other.join();
            check(isolated[0], "Render scope cannot leak to another thread");
            try {
                try (var failed = IrisShadowPassDetector.suppressFor(inner)) {
                    throw new IllegalStateException("simulated render failure");
                }
            } catch (IllegalStateException expected) {
                check(IrisShadowPassDetector.shouldSuppressPowerQuery(outer), "Exception restores the parent");
            }
        }
        check(!IrisShadowPassDetector.hasSuppressScope(), "Outermost return removes the thread-local");

        String prefix = "net.jackcooper.shapeShifterCurseAddon.mixin.render.";
        for (String name : new String[]{"LivingEntityRendererShadowFixMixin", "PowerHolderComponentShadowFixMixin"}) {
            check(!MixinSelection.select(prefix + name, mod -> false, () -> null), "No Iris means no shadow hooks");
            check(MixinSelection.select(prefix + name, mod -> mod.equals("iris"), () -> null), "Iris selects the workaround");
        }
        // Force real Mixin transformation without requiring a GL window or constructing renderers.
        for (String target : new String[]{
                "net.minecraft.client.render.item.HeldItemRenderer",
                "net.minecraft.client.gui.DrawContext",
                "net.minecraft.client.render.entity.LivingEntityRenderer",
                "net.minecraft.screen.ScreenHandler",
                "net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem"}) {
            Class.forName(target, false, MixinCleanupAuditTest.class.getClassLoader());
            checks++;
        }
        System.out.println("Mixin cleanup regressions PASS: " + checks + " checks");
        return checks;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
