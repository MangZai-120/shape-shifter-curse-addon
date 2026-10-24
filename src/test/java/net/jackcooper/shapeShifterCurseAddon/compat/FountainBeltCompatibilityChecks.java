package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import net.onixary.shapeShifterCurseFabric.util.Accessory.DefaultAccessory;
import net.onixary.shapeShifterCurseFabric.util.TrinketUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Invoked inside the real Fabric/Mixin integration loader. */
public final class FountainBeltCompatibilityChecks {
    private static int checks;

    public static void run() throws Exception {
        backendSelection();
        for (boolean plus : List.of(false, true)) resourceMerge(plus);
        System.out.println("Fountain belt compatibility: " + checks + " checks passed (runtime equip tested separately).");
    }

    private static void backendSelection() throws Exception {
        Class.forName(DefaultAccessory.class.getName());
        var savedSelected = AccessoryUtils.nowAccessoryMod;
        var savedId = AccessoryUtils.nowAccessoryModID;
        var savedActive = new HashMap<>(AccessoryUtils.activeAccessoryModInterfaces);
        var originalCurios = AccessoryUtils.accessoryModInterfaces.get("curios");
        var originalTrinkets = AccessoryUtils.accessoryModInterfaces.get("trinkets");
        check(originalCurios != null && originalTrinkets != null, "SSC registers both default adapters");
        try {
            AccessoryUtils.activeAccessoryModInterfaces.clear();
            AccessoryUtils.activeAccessoryModInterfaces.put("curios", originalCurios);
            AccessoryUtils.activeAccessoryModInterfaces.put("trinkets", originalTrinkets);
            select("curios", originalCurios);
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(true), "real Curios is retained");
            check(AccessoryUtils.nowAccessoryMod == originalCurios && AccessoryUtils.nowAccessoryModID.equals("curios"),
                    "real Curios callbacks retain their matching backend ID");
            select("curios", originalCurios);
            check(AccessoryIoFallback.restoreTrinketsForStubCurios(false), "SSC stub Curios falls back");
            check(AccessoryUtils.nowAccessoryMod == originalTrinkets && AccessoryUtils.nowAccessoryModID.equals("trinkets"),
                    "Trinkets callbacks and slot queries use the same backend");
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(false), "fallback is idempotent");
            select("trinkets", originalTrinkets);
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(true), "native Trinkets is retained");
            AccessoryUtils.activeAccessoryModInterfaces.remove("trinkets");
            select("curios", originalCurios);
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(false), "no fallback to an unavailable adapter");
            check(AccessoryUtils.nowAccessoryMod == originalCurios, "pure Curios is preserved");
            AccessoryUtils.activeAccessoryModInterfaces.put("trinkets", originalTrinkets);
            var external = new ExternalCurios();
            select("curios", external);
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(false), "third party Curios IO survives an SSC stub");
            check(AccessoryUtils.nowAccessoryMod == external, "third party implementation is preserved");
            select("external", external);
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(false), "unrelated backends are preserved");
            select("curios", null);
            check(!AccessoryIoFallback.restoreTrinketsForStubCurios(false), "no selected IO is safe");
        } finally {
            AccessoryUtils.activeAccessoryModInterfaces.clear();
            AccessoryUtils.activeAccessoryModInterfaces.putAll(savedActive);
            select(savedId, savedSelected);
        }
    }

    private static void resourceMerge(boolean plus) throws Exception {
        String suffix = plus ? "_plus" : "";
        String item = "shape-shifter-curse:fountain_belt" + suffix;
        String originalPower = "shape-shifter-curse:form_axolotl_fountain_belt_trinket" + suffix;
        String addonPower = "my_addon:form_axolotl_sp_fountain_belt_trinket" + suffix;
        JsonObject original = json("fountain_belt" + suffix);
        JsonObject addon = json("ssc_addon_fountain_belt" + suffix);
        check(original.getAsJsonObject(item).has("layers"), "SSCA does not shadow original " + item + " resource");
        var saved = new HashMap<>(TrinketUtils.accessoryPowerRegistry);
        try {
            for (boolean reverse : List.of(false, true)) {
                TrinketUtils.clearAccessoryPower();
                TrinketUtils.loadAccessoryPowerData(reverse ? addon : original);
                TrinketUtils.loadAccessoryPowerData(reverse ? original : addon);
                var data = TrinketUtils.getAccessoryPower(new Identifier(item));
                var originalPowers = data.layerPowerAddMap.get(new Identifier("origins:origin"))
                        .get(new Identifier("shape-shifter-curse:form_axolotl_3"));
                check(originalPowers.equals(List.of(new Identifier(originalPower))), "original axolotl keeps its fountain " + suffix);
                check(data.formPowerAdd.get(new Identifier("my_addon:axolotl_sp")).equals(List.of(new Identifier(addonPower))),
                        "SP receives only its replacement fountain " + suffix);
                check(data.formPowerRemove.get(new Identifier("my_addon:axolotl_sp")).contains(new Identifier("my_addon:form_axolotl_sp_water_ball")),
                        "SP belt replaces its burst " + suffix);
                check(data.layerPowerRemoveMap.get(new Identifier("origins:origin"))
                        .get(new Identifier("shape-shifter-curse:form_axolotl_3"))
                        .contains(new Identifier("shape-shifter-curse:form_axolotl_3_sprinting_sneaking_water_explode")),
                        "original belt still removes the original burst " + suffix);
            }
        } finally {
            TrinketUtils.clearAccessoryPower();
            TrinketUtils.accessoryPowerRegistry.putAll(saved);
        }
    }

    private static JsonObject json(String name) throws Exception {
        try (var input = FountainBeltCompatibilityChecks.class.getResourceAsStream(
                "/data/shape-shifter-curse/accessory_power/" + name + ".json")) {
            if (input == null) throw new AssertionError("missing " + name);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void select(String id, AccessoryUtils.AccessoryIO io) {
        AccessoryUtils.nowAccessoryModID = id;
        AccessoryUtils.nowAccessoryMod = io;
    }
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static final class ExternalCurios implements AccessoryUtils.AccessoryIO {
        public Map<Pair<String, String>, List<ItemStack>> getEntitySlots(LivingEntity e) { return Map.of(); }
        public List<ItemStack> getEntitySlot(LivingEntity e, String g, String n) { return List.of(); }
        public ItemStack getEntitySlot(LivingEntity e, String g, String n, int i) { return ItemStack.EMPTY; }
        public void setEntitySlot(LivingEntity e, String g, String n, int i, ItemStack s) { }
    }
}
