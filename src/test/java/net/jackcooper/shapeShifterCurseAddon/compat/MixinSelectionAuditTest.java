package net.jackcooper.shapeShifterCurseAddon.compat;

/** Exercises the exact plugin decision path with optional APIs absent and competing backends present. */
public final class MixinSelectionAuditTest {
    public static int run() {
        String bridge = "net.jackcooper.shapeShifterCurseAddon.mixin.item.AddonTrinketBridgeMixin";
        String iris = "net.jackcooper.shapeShifterCurseAddon.mixin.client.IrisShaderPackMixin";
        require(!MixinSelection.select(bridge, mod -> false,
                () -> { throw new AssertionError("Absent Trinkets must skip before resolving the accessory backend"); }), "No optional API");
        require(!MixinSelection.select(bridge, mod -> mod.equals("trinkets"), () -> "trinkets"), "Main Trinkets implementation is exclusive");
        require(MixinSelection.select(bridge, mod -> mod.equals("trinkets"), () -> "curios"), "Trinkets API plus Curios backend allows the fallback");
        require(!MixinSelection.select(bridge, mod -> mod.equals("curios"),
                () -> { throw new AssertionError("Curios alone does not provide Trinkets API"); }), "Curios alone skips bridge");
        require(!MixinSelection.select(iris, mod -> false, () -> null), "Iris absent skips target");
        require(MixinSelection.select(iris, mod -> mod.equals("iris"), () -> null), "Iris present enables target");
        return 6;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
