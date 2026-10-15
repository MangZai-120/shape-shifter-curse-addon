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
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
            player.form = (PlayerFormComponent) unsafe.allocateInstance(PlayerFormComponent.class);
            player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                    .component(RegPlayerFormComponent.PLAYER_FORM, p -> p.form)
                    .build().createContainer(player);
            player.age = 32;

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
                    new Identifier("shape-shifter-curse", "snow_fox_3"));
            int blocked = 0;
            for (Identifier form : forms) {
                player.form.nowForm = new NormalForm(form);
                for (Item item : accessories) {
                    check(Registries.ITEM.getRawId(item) >= 0, "actual SSC item is registered");
                    boolean expected = !((form.equals(FormIdentifiers.BAT_PARASITIC_FRUIT)
                            && item == RegCustomItem.CHARM_OF_NIGHT_CRYSTAL_PLUS)
                            || (form.equals(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)
                            && item == RegCustomItem.AMULET_BRACELET_PLUS)
                            || (form.equals(FormIdentifiers.SNOW_FOX_SP)
                            && item == RegCustomItem.FROST_PAWGLOVE_PLUS)
                            || (form.equals(FormIdentifiers.BAT_DESMODUS)
                            && item == RegCustomItem.ATTACH_HOOK_PLUS)
                            || (item == RegCustomItem.FOUNTAIN_BELT_PLUS
                            && !form.equals(FormIdentifiers.AXOLOTL_SP))
                            || (form.equals(FormIdentifiers.OCELOT_NOVA)
                            && item == RegCustomItem.COLLAR_OF_WHISKERS_PLUS)
                            || (form.equals(FormIdentifiers.GOLDEN_SANDSTORM_SP)
                            && item == RegCustomItem.WITHERED_BANDAGE_PLUS)
                            || (form.equals(FormIdentifiers.SPIDER_SALTICIDAE)
                            && item == RegCustomItem.VENOM_SPINDLE_PLUS));
                    var accessory = (AccessoryItem) item;
                    check(accessory.canEquip(new ItemStack(item), player, SLOT) == expected,
                            "real canEquip: " + form + " / " + Registries.ITEM.getId(item));
                    check(!Boolean.FALSE.equals(AddonExclusiveFormCheck.check(player, accessory)) == expected,
                            "server cleanup agrees with equip decision");
                    if (!expected) blocked++;
                }
            }
            check(blocked == 31, "only the requested item/form combinations are denied (7 specific + 24 non-SP forms for fountain belt plus)");

            Item[] limited = {RegCustomItem.CHARM_OF_NIGHT_CRYSTAL_PLUS,
                    RegCustomItem.AMULET_BRACELET_PLUS, RegCustomItem.FROST_PAWGLOVE_PLUS,
                    RegCustomItem.ATTACH_HOOK_PLUS};
            Identifier[] deniedForms = {FormIdentifiers.BAT_PARASITIC_FRUIT,
                    FormIdentifiers.FAMILIAR_FOX_MANCIANIMA, FormIdentifiers.SNOW_FOX_SP,
                    FormIdentifiers.BAT_DESMODUS};
            // 喷泉腰带＋：SP 允许，其它形态拒绝（含进化/荧光/阿澪），登录宽限后恢复拒绝
            player.form.nowForm = new NormalForm(FormIdentifiers.UPGRADE_AXOLOTL);
            check(!((AccessoryItem) RegCustomItem.FOUNTAIN_BELT_PLUS).canEquip(
                    new ItemStack(RegCustomItem.FOUNTAIN_BELT_PLUS), player, SLOT), "fountain belt plus denied for upgrade axolotl");
            player.form.nowForm = new NormalForm(FormIdentifiers.AXOLOTL_FLUORESCENT);
            check(!((AccessoryItem) RegCustomItem.FOUNTAIN_BELT_PLUS).canEquip(
                    new ItemStack(RegCustomItem.FOUNTAIN_BELT_PLUS), player, SLOT), "fountain belt plus denied for fluorescent axolotl");
            player.form.nowForm = new NormalForm(FormIdentifiers.AXOLOTL_ALING);
            check(!((AccessoryItem) RegCustomItem.FOUNTAIN_BELT_PLUS).canEquip(
                    new ItemStack(RegCustomItem.FOUNTAIN_BELT_PLUS), player, SLOT), "fountain belt plus denied for aling");
            player.form.nowForm = new NormalForm(FormIdentifiers.AXOLOTL_SP);
            check(((AccessoryItem) RegCustomItem.FOUNTAIN_BELT_PLUS).canEquip(
                    new ItemStack(RegCustomItem.FOUNTAIN_BELT_PLUS), player, SLOT), "fountain belt plus allowed only for axolotl SP");
            player.age = 0;
            check(((AccessoryItem) RegCustomItem.FOUNTAIN_BELT_PLUS).canEquip(
                    new ItemStack(RegCustomItem.FOUNTAIN_BELT_PLUS), player, SLOT), "login grace lets belt load");
            player.age = 32;
            for (int i = 0; i < limited.length; i++) {
                var accessory = (AccessoryItem) limited[i];
                var stack = new ItemStack(limited[i]);
                player.form.nowForm = new NormalForm(deniedForms[i]);
                player.age = 0;
                check(accessory.canEquip(stack, player, SLOT), "login restoration grace");
                player.age = 32;
                check(!accessory.canEquip(stack, player, SLOT), "restored restricted form is rejected");
                player.form.nowForm = new NormalForm(FormIdentifiers.SHEEP_FORM);
                check(accessory.canEquip(stack, player, SLOT), "allowed form accepts existing item");
                player.form.nowForm = new NormalForm(deniedForms[i]);
                check(Boolean.FALSE.equals(AddonExclusiveFormCheck.check(player, accessory)),
                        "wear before transforming becomes eligible for server removal");
            }

            player.form.nowForm = new NormalForm(FormIdentifiers.UPGRADE_AXOLOTL);
            check(((AccessoryItem) RegCustomItem.FOUNTAIN_BELT).canEquip(
                    new ItemStack(RegCustomItem.FOUNTAIN_BELT), player, SLOT), "ordinary fountain belt stays equipable (no restriction on ordinary version)");
            player.form.nowForm = new NormalForm(FormIdentifiers.BAT_DESMODUS);
            check(((AccessoryItem) RegCustomItem.ATTACH_HOOK).canEquip(
                    new ItemStack(RegCustomItem.ATTACH_HOOK), player, SLOT), "ordinary attach hook still equipable");
            player.form.nowForm = new NormalForm(FormIdentifiers.BAT_PARASITIC_FRUIT);
            check(((AccessoryItem) RegCustomItem.ATTACH_HOOK).canEquip(
                    new ItemStack(RegCustomItem.ATTACH_HOOK), player, SLOT), "ordinary attach hook still equipable for fruit bat");
            player.form.nowForm = new NormalForm(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
            check(((AccessoryItem) RegCustomItem.AMULET_BRACELET).canEquip(
                    new ItemStack(RegCustomItem.AMULET_BRACELET), player, SLOT), "ordinary bracelet still equipable");
            player.form.nowForm = new NormalForm(FormIdentifiers.SNOW_FOX_SP);
            check(((AccessoryItem) RegCustomItem.FROST_PAWGLOVE).canEquip(
                    new ItemStack(RegCustomItem.FROST_PAWGLOVE), player, SLOT), "ordinary pawglove still equipable");
            player.form.nowForm = new NormalForm(FormIdentifiers.BAT_PARASITIC_FRUIT);
            check(!((AccessoryItem) RegCustomItem.CHARM_OF_NIGHT_CRYSTAL).canEquip(
                    new ItemStack(RegCustomItem.CHARM_OF_NIGHT_CRYSTAL), player, SLOT), "ordinary night charm remains denied");
            // 朔望禁戴颈环＋；风灵放行；普通版颈环不限戴
            player.form.nowForm = new NormalForm(FormIdentifiers.OCELOT_NOVA);
            check(!((AccessoryItem) RegCustomItem.COLLAR_OF_WHISKERS_PLUS).canEquip(
                    new ItemStack(RegCustomItem.COLLAR_OF_WHISKERS_PLUS), player, SLOT), "whisker collar plus denied for nova");
            check(Boolean.FALSE.equals(AddonExclusiveFormCheck.check(player,
                    (AccessoryItem) RegCustomItem.COLLAR_OF_WHISKERS_PLUS)), "nova wearing collar becomes eligible for removal");
            check(((AccessoryItem) RegCustomItem.COLLAR_OF_WHISKERS).canEquip(
                    new ItemStack(RegCustomItem.COLLAR_OF_WHISKERS), player, SLOT), "ordinary whisker collar stays equipable");
            player.form.nowForm = new NormalForm(FormIdentifiers.OCELOT_SP);
            check(((AccessoryItem) RegCustomItem.COLLAR_OF_WHISKERS_PLUS).canEquip(
                    new ItemStack(RegCustomItem.COLLAR_OF_WHISKERS_PLUS), player, SLOT), "whisker collar plus allowed for wind spirit");
            // 金沙岚禁戴裹布＋；冥裁者放行
            player.form.nowForm = new NormalForm(FormIdentifiers.GOLDEN_SANDSTORM_SP);
            check(!((AccessoryItem) RegCustomItem.WITHERED_BANDAGE_PLUS).canEquip(
                    new ItemStack(RegCustomItem.WITHERED_BANDAGE_PLUS), player, SLOT), "withered bandage plus denied for golden sandstorm");
            check(Boolean.FALSE.equals(AddonExclusiveFormCheck.check(player,
                    (AccessoryItem) RegCustomItem.WITHERED_BANDAGE_PLUS)), "sandstorm wearing bandage becomes eligible for removal");
            player.form.nowForm = new NormalForm(FormIdentifiers.ANUBIS_WOLF_SP);
            check(((AccessoryItem) RegCustomItem.WITHERED_BANDAGE_PLUS).canEquip(
                    new ItemStack(RegCustomItem.WITHERED_BANDAGE_PLUS), player, SLOT), "withered bandage plus allowed for anubis wolf SP");
            // 跳蛛禁戴纺锤＋；月织蛛放行
            player.form.nowForm = new NormalForm(FormIdentifiers.SPIDER_SALTICIDAE);
            check(!((AccessoryItem) RegCustomItem.VENOM_SPINDLE_PLUS).canEquip(
                    new ItemStack(RegCustomItem.VENOM_SPINDLE_PLUS), player, SLOT), "venom spindle plus denied for salticidae");
            check(Boolean.FALSE.equals(AddonExclusiveFormCheck.check(player,
                    (AccessoryItem) RegCustomItem.VENOM_SPINDLE_PLUS)), "salticidae wearing spindle becomes eligible for removal");
            player.form.nowForm = new NormalForm(FormIdentifiers.SPIDER_MOON_WEAVER);
            check(((AccessoryItem) RegCustomItem.VENOM_SPINDLE_PLUS).canEquip(
                    new ItemStack(RegCustomItem.VENOM_SPINDLE_PLUS), player, SLOT), "venom spindle plus allowed for moon weaver");
            System.out.println("Enhanced accessory integration passed: " + checks
                    + " checks; 11 enhanced accessories x 25 forms, actual Mixin equip calls, login grace,"
                    + " transformation cleanup eligibility and ordinary accessory behavior.");
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
