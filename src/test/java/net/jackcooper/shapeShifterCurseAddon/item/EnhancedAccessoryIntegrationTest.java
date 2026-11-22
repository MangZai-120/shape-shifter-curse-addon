package net.jackcooper.shapeShifterCurseAddon.item;

import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Exercises actual transformed SSC items against CCA form state; no running world. */
public final class EnhancedAccessoryIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", Files.readString(
                Path.of(System.getProperty("accessory.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "enhanced-accessory-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(EnhancedAccessoryIntegrationTest.class.getName() + "$Probe", true, loader)
                .getMethod("run").invoke(null);
    }

    public static final class Probe {
        private static int checks;
        private static final AccessoryItem.SlotData SLOT = new AccessoryItem.SlotData(
                new Identifier("trinket", "chest/necklace"), 0);

        public static void run() throws Exception {
            net.jackcooper.shapeShifterCurseAddon.compat.FountainBeltCompatibilityChecks.run();
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
            player.form = (PlayerFormComponent) unsafe.allocateInstance(PlayerFormComponent.class);
            player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                    .component(RegPlayerFormComponent.PLAYER_FORM, p -> p.form)
                    .build().createContainer(player);
            player.age = 32;

            net.jackcooper.shapeShifterCurseAddon.compat.AccessoriesCompatibilityChecks.initialize(player);

            var accessories = List.of(
                    RegCustomItem.CHARM_OF_NIGHT_CRYSTAL_PLUS, RegCustomItem.CHARM_OF_HOLLOW_FANG_PLUS,
                    RegCustomItem.ATTACH_HOOK_PLUS, RegCustomItem.CHARM_OF_REVERSE_THERMOMETER_PLUS,
                    RegCustomItem.FROST_PAWGLOVE_PLUS, RegCustomItem.VENOM_SPINDLE_PLUS,
                    RegCustomItem.COLLAR_OF_WHISKERS_PLUS, RegCustomItem.DIGESTION_FIBER_BALL_PLUS,
                    RegCustomItem.WITHERED_BANDAGE_PLUS, RegCustomItem.FOUNTAIN_BELT_PLUS,
                    RegCustomItem.AMULET_BRACELET_PLUS);
            var forms = List.of(FormIdentifiers.BAT_PARASITIC_FRUIT, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,
                    FormIdentifiers.SNOW_FOX_SP, FormIdentifiers.BAT_DESMODUS, FormIdentifiers.FAMILIAR_FOX_SP,
                    FormIdentifiers.UPGRADE_FAMILIAR_FOX, FormIdentifiers.FAMILIAR_FOX_RED,
                    FormIdentifiers.SNOW_FOX_FROSTSPINE, FormIdentifiers.AXOLOTL_SP,
                    FormIdentifiers.UPGRADE_AXOLOTL, FormIdentifiers.AXOLOTL_FLUORESCENT,
                    FormIdentifiers.AXOLOTL_ALING, FormIdentifiers.OCELOT_SP, FormIdentifiers.OCELOT_NOVA,
                    FormIdentifiers.ANUBIS_WOLF_SP, FormIdentifiers.GOLDEN_SANDSTORM_SP,
                    FormIdentifiers.SPIDER_MOON_WEAVER, FormIdentifiers.SPIDER_SALTICIDAE,
                    FormIdentifiers.ALLAY_SP, FormIdentifiers.FALLEN_ALLAY_SP,
                    FormIdentifiers.WILD_CAT_SP, FormIdentifiers.WILD_CAT_NIGHTMARE,
                    new Identifier("shape-shifter-curse", "bat_3"),
                    new Identifier("shape-shifter-curse", "familiar_fox_3"),
                    new Identifier("shape-shifter-curse", "snow_fox_3"),
                    new Identifier("shape-shifter-curse", "axolotl_0"), new Identifier("shape-shifter-curse", "axolotl_1"),
                    new Identifier("shape-shifter-curse", "axolotl_2"), new Identifier("shape-shifter-curse", "axolotl_3"),
                    new Identifier("shape-shifter-curse", "ocelot_3"), new Identifier("shape-shifter-curse", "spider_3"),
                    new Identifier("shape-shifter-curse", "anubis_wolf_3"));
            var wearable = new java.util.LinkedHashSet<Item>(accessories);
            wearable.add(RegCustomItem.CHARM_OF_NIGHT_CRYSTAL);
            for (var field : net.jackcooper.shapeShifterCurseAddon.SscAddon.class.getFields()) {
                if (Item.class.isAssignableFrom(field.getType()) && field.get(null) instanceof AccessoryItem accessory) {
                    wearable.add(accessory);
                }
            }
            check(wearable.size() >= accessories.size() + 23, "SSCA form accessories included");
            var dormant = Map.of(RegCustomItem.CHARM_OF_NIGHT_CRYSTAL_PLUS, FormIdentifiers.BAT_PARASITIC_FRUIT,
                    RegCustomItem.AMULET_BRACELET_PLUS, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,
                    RegCustomItem.FROST_PAWGLOVE_PLUS, FormIdentifiers.SNOW_FOX_SP,
                    RegCustomItem.ATTACH_HOOK_PLUS, FormIdentifiers.BAT_DESMODUS,
                    RegCustomItem.COLLAR_OF_WHISKERS_PLUS, FormIdentifiers.OCELOT_NOVA,
                    RegCustomItem.WITHERED_BANDAGE_PLUS, FormIdentifiers.GOLDEN_SANDSTORM_SP);
            for (Identifier form : forms) {
                player.form.nowForm = new NormalForm(form);
                for (Item item : wearable) {
                    var accessory = (AccessoryItem) item;
                    check(accessory.canEquip(new ItemStack(item), player, SLOT),
                            "physical equip: " + form + " / " + item.getClass().getSimpleName());
                    net.jackcooper.shapeShifterCurseAddon.compat.AccessoriesCompatibilityChecks.equip(item, true);
                }
                var current = AccessoryFormEffects.formOf(player);
                for (var pair : dormant.entrySet()) {
                    var itemId = Registries.ITEM.getId(pair.getKey());
                    check(AccessoryFormEffects.allows(current, itemId) == !form.equals(pair.getValue()),
                            "form-specific effect override: " + itemId + " / " + form);
                }
                check(AccessoryFormEffects.allows(current, Registries.ITEM.getId(RegCustomItem.VENOM_SPINDLE_PLUS))
                        == (form.equals(FormIdentifiers.SPIDER_MOON_WEAVER) || form.equals(new Identifier("shape-shifter-curse:spider_3"))),
                        "spindle web projectile effect");
                check(AccessoryFormEffects.allows(current, new Identifier("ssc_addon:venom_gland"))
                        == form.equals(FormIdentifiers.SPIDER_SALTICIDAE), "jumping spider venom gland");
                check(AccessoryFormEffects.allows(current, new Identifier("ssc_addon:erosion_sand_prism"))
                        == form.equals(FormIdentifiers.GOLDEN_SANDSTORM_SP), "sand prism");
                check(AccessoryFormEffects.allows(current, new Identifier("ssc_addon:moon_dust_spellbook")),
                        "universal spellbook");
                check(AccessoryFormEffects.allows(current, Registries.ITEM.getId(RegCustomItem.CHARM_OF_NIGHT_CRYSTAL))
                        == !form.equals(FormIdentifiers.BAT_PARASITIC_FRUIT), "fruit bat night crystal");
                check(AccessoryFormEffects.allows(current, Registries.ITEM.getId(RegCustomItem.FOUNTAIN_BELT_PLUS))
                        == (!form.getNamespace().equals("my_addon") || form.equals(FormIdentifiers.AXOLOTL_SP)),
                        "SSC applies its native belt tier rules and SSCA activates it on SP");
            }
            net.jackcooper.shapeShifterCurseAddon.compat.AccessoriesCompatibilityChecks.report();
            System.out.println("Enhanced accessory integration passed: " + checks
                    + " checks; " + wearable.size() + " wearable accessories x " + forms.size() + " forms.");
        }

        private static void check(boolean success, String message) {
            checks++;
            if (!success) throw new AssertionError(message);
        }
    }

    public static final class TestPlayer extends PlayerEntity implements ComponentProvider {
        private PlayerFormComponent form;
        private ComponentContainer components;
        private TestPlayer(World world, BlockPos pos, float yaw, GameProfile profile) {
            super(world, pos, yaw, profile);
        }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public ComponentContainer getComponentContainer() { return components; }
    }
}
