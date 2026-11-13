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
        require(!MixinSelection.select(bridge, mod -> mod.equals("trinkets") || mod.equals("accessories"), () -> "accessories"),
                "Native Accessories must not also register the Trinkets bridge");
        require(!MixinSelection.select(bridge, mod -> true, () -> "accessories"),
                "Accessories remains authoritative with Curios and a Trinkets compatibility layer present");
        require(!MixinSelection.select(bridge, mod -> mod.equals("accessories"),
                () -> { throw new AssertionError("Accessories alone has no Trinkets API"); }), "Accessories without Trinkets stays optional");
        require(MixinSelection.select(bridge, mod -> mod.equals("trinkets"), () -> "external"),
                "Existing third party fallback behavior is retained");
        require(!MixinSelection.select(bridge, mod -> mod.equals("curios"),
                () -> { throw new AssertionError("Curios alone does not provide Trinkets API"); }), "Curios alone skips bridge");
        require(!MixinSelection.select(iris, mod -> false, () -> null), "Iris absent skips target");
        require(MixinSelection.select(iris, mod -> mod.equals("iris"), () -> null), "Iris present enables target");
        String prefix="net.jackcooper.shapeShifterCurseAddon.mixin.item.";
        String[] mutations={"TrinketsInventoryMutationMixin","CuriosStackMutationMixin","CuriosSizeMutationMixin",
                "CuriosInventoryMutationMixin","AccessoriesContainerMutationMixin","AccessoriesHolderMutationMixin"};
        for(String mutation:mutations)
            require(!MixinSelection.select(prefix+mutation,mod->false,()->null),"Absent backend skips "+mutation);
        require(MixinSelection.select(prefix+mutations[0],mod->mod.equals("trinkets"),()->null),"Native Trinkets mutation hook");
        require(!MixinSelection.select(prefix+mutations[0],mod->mod.equals("trinkets")||mod.equals("tclayer"),()->null),"Wrapped Trinkets uses Accessories mutation hook");
        return 18;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
