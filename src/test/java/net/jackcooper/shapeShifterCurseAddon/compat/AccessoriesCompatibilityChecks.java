package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.loader.api.FabricLoader;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityType;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryPriorityUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Checks real resource merges and, when installed, actual transformed Accessories equip calls. */
public final class AccessoriesCompatibilityChecks {
    private static int checks;
    private static Method nativeCanEquip;
    private static Method nativeLookup;
    private static Object nativeSlot;

    public static void initialize(LivingEntity player) throws Exception {
        var slots = new LinkedHashMap<String, String>();
        slots.put("chest/back", "back"); slots.put("chest/cape", "cape");
        slots.put("chest/necklace", "necklace"); slots.put("feet/aglet", "anklet");
        slots.put("feet/shoes", "shoes"); slots.put("hand/glove", "hand");
        slots.put("hand/ring", "ring"); slots.put("head/face", "face");
        slots.put("head/hat", "hat"); slots.put("legs/belt", "belt");
        slots.put("offhand/glove", "hand"); slots.put("offhand/ring", "ring");
        slots.put("hand/extra_hand", "extra_hand");
        slots.put("moonlit/spellbook", "trinket_group_moonlit-spellbook");
        for (var entry : slots.entrySet()) {
            Set<String> oldItems = items("data/trinkets/tags/items/" + entry.getKey() + ".json");
            check(!oldItems.isEmpty(), "legacy slot fixture exists: " + entry.getKey());
            Set<String> nativeItems = items("data/accessories/tags/items/" + entry.getValue() + ".json");
            check(nativeItems.containsAll(oldItems), "native slot retains every existing item: " + entry.getKey());
        }
        JsonObject slot = json("data/ssc_addon/accessories/slot/trinket_group_moonlit-spellbook.json");
        check(slot.get("amount").getAsInt() == 1 && slot.get("operation").getAsString().equals("set"), "one spellbook slot");
        check(!slot.get("replace").getAsBoolean(), "slot data remains mergeable");
        check(slot.getAsJsonArray("validators").toString().contains("accessories:tag"), "native tag validator");
        check(items("data/accessories/tags/items/trinket_group_moonlit-spellbook.json").equals(Set.of("ssc_addon:moon_dust_spellbook")), "spellbook slot does not admit unrelated items");
        JsonObject entity = json("data/ssc_addon/accessories/entity/ssc_addon.json");
        check(entity.getAsJsonArray("entities").toString().equals("[\"minecraft:player\"]"), "slot targets players only");
        check(entity.getAsJsonArray("slots").toString().equals("[\"trinket_group_moonlit-spellbook\"]"), "entity receives the independent slot");
        JsonObject group = json("data/ssc_addon/accessories/group/ssc_addon.json");
        check(group.getAsJsonArray("slots").toString().equals("[\"trinket_group_moonlit-spellbook\"]"), "spellbook has its own visible group");
        check(Arrays.equals(bytes("assets/ssc_addon/textures/slot/spellbook.png"), bytes("assets/ssc_addon/textures/gui/slot/spellbook.png")), "native slot reuses the original icon without resampling");
        for (String locale : List.of("en_us", "zh_cn")) {
            var lang = json("assets/my_addon/lang/" + locale + ".json");
            String label = lang.get("trinkets.slot.moonlit.spellbook").getAsString();
            check(lang.get("accessories.slot.trinket_group_moonlit-spellbook").getAsString().equals(label), "slot translation: " + locale);
            check(lang.get("accessories.slot_group.ssc_addon").getAsString().equals(label), "group translation: " + locale);
        }
        if (FabricLoader.getInstance().isModLoaded("accessories")) {
            check("accessories".equals(AccessoryPriorityUtils.getHighestPriorityPlugin()), "SSC selects native Accessories");
            Class<?> api = Class.forName("io.wispforest.accessories.api.Accessory");
            check(api.isAssignableFrom(AccessoryItem.class), "SSC items implement the actual Accessories API");
            if (FabricLoader.getInstance().isModLoaded("trinkets")) {
                check(!Class.forName("dev.emi.trinkets.api.Trinket").isAssignableFrom(AccessoryItem.class), "Trinkets bridge does not duplicate the native backend");
            }
            Class<?> reference = Class.forName("io.wispforest.accessories.api.slot.SlotReference");
            nativeSlot = reference.getMethod("of", LivingEntity.class, String.class, int.class).invoke(null, player, "necklace", 0);
            nativeCanEquip = api.getMethod("canEquip", ItemStack.class, reference);
            nativeLookup = Class.forName("io.wispforest.accessories.api.AccessoriesAPI").getMethod("getAccessory", Item.class);
            check(nativeLookup.invoke(null, SscAddon.MOON_DUST_SPELLBOOK) == SscAddon.MOON_DUST_SPELLBOOK,
                    "the real addon spellbook registers with Accessories");
            check(Boolean.TRUE.equals(nativeCanEquip.invoke(SscAddon.MOON_DUST_SPELLBOOK,
                    new ItemStack(SscAddon.MOON_DUST_SPELLBOOK), nativeSlot)), "spellbook keeps unrestricted equip behavior");
            nativeSlotResources();
        }
    }

    public static void equip(Item item, boolean allowed) throws Exception {
        if (nativeCanEquip != null) {
            check(nativeLookup.invoke(null, item) == item, "item is registered with the native Accessories API");
            check(Boolean.valueOf(allowed).equals(nativeCanEquip.invoke(item, new ItemStack(item), nativeSlot)),
                    "native Accessories preserves form-specific restrictions: " + item);
        }
    }

    public static void report() {
        System.out.println("ACCESSORIES_COMPATIBILITY_PASS checks=" + checks + " native_api=" + (nativeCanEquip != null));
    }

    private static void nativeSlotResources() throws Exception {
        // Knot bootstrap skips mod entrypoints; initialize the real default config for the data loaders.
        var configClass = Class.forName("io.wispforest.accessories.compat.AccessoriesConfig").asSubclass(ConfigData.class);
        var holder = AutoConfig.register(configClass, GsonConfigSerializer::new);
        Class.forName("io.wispforest.accessories.Accessories").getField("CONFIG_HOLDER").set(null, holder);
        if (FabricLoader.getInstance().isModLoaded("tclayer")) legacyBookSlot();
        Object loader = apply("SlotTypeLoader", "trinket_group_moonlit-spellbook", "slot/trinket_group_moonlit-spellbook");
        var slots = (Map<?, ?>) loader.getClass().getMethod("getSlotTypes", boolean.class).invoke(loader, false);
        check(slots.keySet().stream().filter(name -> name.toString().contains("spellbook")).count() == 1,
                "legacy conversion and native resources produce exactly one spellbook slot");
        Object book = slots.get("trinket_group_moonlit-spellbook");
        check(book != null, "actual Accessories loader creates the spellbook slot");
        Class<?> type = Class.forName("io.wispforest.accessories.api.slot.SlotType");
        check(Integer.valueOf(1).equals(type.getMethod("amount").invoke(book)), "actual slot has one position");
        check("accessories.slot.trinket_group_moonlit-spellbook".equals(type.getMethod("translation").invoke(book)), "actual slot translation key");
        check(new Identifier("ssc_addon:gui/slot/spellbook").equals(type.getMethod("icon").invoke(book)), "actual slot resolves its icon");
        loader = apply("SlotGroupLoader", "ssc_addon", "group/ssc_addon");
        Object group = loader.getClass().getMethod("getGroup", boolean.class, String.class).invoke(loader, false, "ssc_addon");
        check(group != null, "actual Accessories loader creates the spellbook group");
        Class<?> groupType = Class.forName("io.wispforest.accessories.api.slot.SlotGroup");
        check(Set.of("trinket_group_moonlit-spellbook").equals(groupType.getMethod("slots").invoke(group)), "actual group contains the dedicated slot");
        check("accessories.slot_group.ssc_addon".equals(groupType.getMethod("translation").invoke(group)), "actual group translation key");
        loader = apply("EntitySlotLoader", "ssc_addon", "entity/ssc_addon");
        var playerSlots = (Map<?, ?>) loader.getClass().getMethod("getSlotTypes", boolean.class, EntityType.class).invoke(loader, false, EntityType.PLAYER);
        check(playerSlots.containsKey("trinket_group_moonlit-spellbook"), "actual entity loader assigns the slot to players");
    }

    @SuppressWarnings("unchecked")
    private static void legacyBookSlot() throws Exception {
        Class<?> wrapping = Class.forName("dev.emi.trinkets.compat.WrappingTrinketsUtils");
        check("trinket_group_moonlit-spellbook".equals(wrapping.getMethod("trinketsToAccessories_Slot", Optional.class, String.class)
                .invoke(null, Optional.of("moonlit"), "spellbook")), "native slot retains tclayer's saved inventory identity");
        Class<?> slotType = Class.forName("dev.emi.trinkets.data.SlotLoader$SlotData");
        Object slot = slotType.getConstructor().newInstance();
        Method read = slotType.getDeclaredMethod("read", JsonObject.class);
        read.setAccessible(true);
        read.invoke(slot, json("data/trinkets/slots/moonlit/spellbook.json"));
        Class<?> groupType = Class.forName("dev.emi.trinkets.data.SlotLoader$GroupData");
        Object group = groupType.getConstructor().newInstance();
        ((Map<String, Object>) groupType.getField("slots").get(group)).put("spellbook", slot);
        Class<?> loaderType = Class.forName("dev.emi.trinkets.data.SlotLoader");
        Method apply = loaderType.getDeclaredMethod("apply", Map.class, ResourceManager.class, Profiler.class);
        apply.setAccessible(true);
        apply.invoke(loaderType.getField("INSTANCE").get(null), Map.of("moonlit", group), null, DummyProfiler.INSTANCE);
    }

    private static Object apply(String loaderName, String name, String resource) throws Exception {
        Class<?> type = Class.forName("io.wispforest.accessories.data." + loaderName);
        Object loader = type.getField("INSTANCE").get(null);
        Method apply = type.getDeclaredMethod("apply", Map.class, ResourceManager.class, Profiler.class);
        apply.setAccessible(true);
        apply.invoke(loader, Map.of(new Identifier("ssc_addon", name), json("data/ssc_addon/accessories/" + resource + ".json")), null, DummyProfiler.INSTANCE);
        return loader;
    }

    private static Set<String> items(String path) throws Exception {
        var result = new LinkedHashSet<String>();
        var resources = AccessoriesCompatibilityChecks.class.getClassLoader().getResources(path);
        while (resources.hasMoreElements()) {
            try (var input = resources.nextElement().openStream()) {
                var value = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (value.has("replace") && value.get("replace").getAsBoolean()) result.clear();
                for (var item : value.getAsJsonArray("values")) result.add(item.isJsonPrimitive() ? item.getAsString() : item.getAsJsonObject().get("id").getAsString());
            }
        }
        return result;
    }

    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(new String(bytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] bytes(String path) throws Exception {
        try (var input = AccessoriesCompatibilityChecks.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new AssertionError("missing resource: " + path);
            return input.readAllBytes();
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
