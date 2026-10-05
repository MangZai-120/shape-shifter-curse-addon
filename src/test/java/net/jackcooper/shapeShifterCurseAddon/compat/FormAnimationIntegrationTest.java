package net.jackcooper.shapeShifterCurseAddon.compat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.jackcooper.shapeShifterCurseAddon.forms.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Real Fabric/Mixin and Carry On data; optional SlashBlade class fixture, without rendering. */
public final class FormAnimationIntegrationTest {
    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean("formAnimation.expectSlashBladeFixture")) {
            Path fixture = Path.of(System.getProperty("formAnimation.slashBladeFixtureDir"));
            writeItemFixture(fixture, "mods/flammpfeil/slashblade/item/ItemSlashBlade", "net/minecraft/item/Item");
            writeItemFixture(fixture, "ssca/animation/fixture/CustomBlade", "mods/flammpfeil/slashblade/item/ItemSlashBlade");
        }
        System.setProperty("java.class.path", Files.readString(
                Path.of(System.getProperty("formAnimation.testClasspathFile"))));
        var environment = Boolean.getBoolean("formAnimation.expectServer")
                ? net.fabricmc.api.EnvType.SERVER : net.fabricmc.api.EnvType.CLIENT;
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(environment);
        var loader = knot.init(new String[]{"--version", "form-animation-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(FormAnimationIntegrationTest.class.getName() + "$Probe", true, loader)
                .getMethod("run").invoke(null);
    }

    // Isolated from production/test output: absent-mod runs must not see the optional class.
    private static void writeItemFixture(Path root, String name, String parent) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, parent, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/item/Item$Settings;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>",
                "(Lnet/minecraft/item/Item$Settings;)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(2, 2);
        constructor.visitEnd();
        writer.visitEnd();
        Path output = root.resolve(name + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }

    public static final class Probe {
        private static int checks;
        private record ItemCase(String label, Item item, boolean blade) {}

        @SuppressWarnings("unchecked")
        public static void run() throws Exception {
            boolean carryOn = Boolean.getBoolean("formAnimation.expectCarryOn");
            boolean server = Boolean.getBoolean("formAnimation.expectServer");
            check(FabricLoader.getInstance().isModLoaded("carryon") == carryOn, "requested optional mod loaded");
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var player = (TestPlayer) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TestPlayer.class);
            player.hand = new ItemStack(Items.STICK);
            player.offHand = ItemStack.EMPTY;
            player.tracker = new DataTracker(player);
            if (carryOn) {
                Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
                TrackedData<NbtCompound> key = (TrackedData<NbtCompound>) manager.getField("CARRY_DATA_KEY").get(null);
                player.tracker.startTracking(key, new NbtCompound());
            }
            var forms = List.of(
                    new Form_Axolotl3(new Identifier("my_addon", "form_axolotl_sp")),
                    new Form_Axolotl3(new Identifier("my_addon", "form_upgrade_axolotl")),
                    new Form_AxolotlFluorescent(new Identifier("my_addon", "form_axolotl_fluorescent")),
                    new Form_AxolotlFluorescent(new Identifier("my_addon", "form_axolotl_aling")),
                    new Form_Allay(new Identifier("my_addon", "form_allay_sp")),
                    new Form_FallenAllaySP(new Identifier("my_addon", "form_fallen_allay_sp")));
            List<ItemCase> items = new ArrayList<>();
            items.add(new ItemCase("stick", Items.STICK, false));
            items.add(new ItemCase("vanilla sword", Items.IRON_SWORD, false));
            items.add(new ItemCase("name lookalike", Registry.register(Registries.ITEM,
                    new Identifier("ssca_animation_test", "slashblade"), new Item(new Item.Settings())), false));
            boolean bladeFixture = Boolean.getBoolean("formAnimation.expectSlashBladeFixture");
            if (bladeFixture) {
                items.add(new ItemCase("SlashBlade class fixture", fixtureItem(
                        "mods.flammpfeil.slashblade.item.ItemSlashBlade", "blade"), true));
                items.add(new ItemCase("SlashBlade subclass fixture", fixtureItem(
                        "ssca.animation.fixture.CustomBlade", "custom_blade"), true));
            } else {
                try {
                    Class.forName("mods.flammpfeil.slashblade.item.ItemSlashBlade", false,
                            FormAnimationCompat.class.getClassLoader());
                    throw new AssertionError("Absent-mod run must not contain the SlashBlade fixture");
                } catch (ClassNotFoundException expected) {
                    checks++;
                }
            }
            List<Identifier> states = new ArrayList<>(AnimStateEnum.stateMap.keySet());
            for (NormalForm form : forms) {
                boolean axolotl = !(form instanceof Form_Allay);
                for (boolean sneaking : new boolean[]{false, true}) {
                    player.sneaking = sneaking;
                    for (boolean swimming : new boolean[]{false, true}) {
                        player.swimming = swimming;
                        for (boolean fallFlying : new boolean[]{false, true}) {
                            player.fallFlying = fallFlying;
                            player.hand = new ItemStack(Items.STICK);
                            if (carryOn) setCarrying(player, false);
                            List<AbstractAnimStateController> originals = new ArrayList<>();
                            for (Identifier state : states) originals.add(form.getAnimStateController(player, null, state));
                            check(!FormAnimationCompat.shouldUseVanillaAnimation(player), "normal item keeps form animation");
                            for (ItemCase item : items) {
                                player.hand = new ItemStack(item.item());
                                for (boolean carrying : carryOn ? new boolean[]{false, true, false} : new boolean[]{false}) {
                                    if (carryOn) setCarrying(player, carrying);
                                    boolean active = item.blade() || (carrying && !server && !swimming && !fallFlying);
                                    boolean expected = active && (!axolotl || !sneaking);
                                    check(FormAnimationCompat.shouldUseVanillaAnimation(player) == active,
                                            "allay handoff: " + item.label());
                                    check(FormAnimationCompat.shouldUseAxolotlVanillaAnimation(player) == (active && !sneaking),
                                            "axolotl keeps crawling: " + item.label());
                                    assertControllers(form, player, states, originals, expected, item.label());
                                }
                                player.hand = new ItemStack(Items.STICK);
                                player.offHand = new ItemStack(item.item());
                                check(!FormAnimationCompat.shouldUseVanillaAnimation(player), "offhand alone never hands off");
                                assertControllers(form, player, states, originals, false, "main-hand removal restores animations");
                                player.offHand = ItemStack.EMPTY;
                            }
                        }
                    }
                }
            }
            FormAnimationCompat.VANILLA_CONTROLLER.registerAnim(player, null);
            check(FormAnimationCompat.VANILLA_CONTROLLER.getAnimation(player, null) == null,
                    "handoff plays no SSC animation instead of falling through to a default controller");
            check(FormAnimationCompat.VANILLA_CONTROLLER.getAllAnimations().isEmpty(), "empty handoff has no animation asset");
            if (!server) {
                var feature = Class.forName("net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderFeature");
                for (String method : new String[]{"ssca$bindOverlayPlayer", "ssca$alignEmissiveLayer",
                        "ssca$alignEmissiveGeometry", "ssca$renderFirstPersonEmissive"}) {
                    check(java.util.Arrays.stream(feature.getDeclaredMethods()).anyMatch(m -> m.getName().contains(method)),
                            "existing emissive injection still applies: " + method);
                }
            }
            System.out.println("Form animation integration PASS: " + checks + " checks; Carry On=" + carryOn
                    + "; environment=" + (server ? "SERVER" : "CLIENT")
                    + "; SlashBlade class fixture=" + bladeFixture
                    + "; actual optional mixin, six forms, all states, family-specific sneaking, swimming/elytra,"
                    + " pickup/drop, main/offhand, item subclasses, restoration and emissive hooks."
                    + " Live rendering and actual SlashBlade mod NOT tested.");
        }

        private static Item fixtureItem(String className, String id) throws Exception {
            Item item = (Item) Class.forName(className).getConstructor(Item.Settings.class)
                    .newInstance(new Item.Settings());
            return Registry.register(Registries.ITEM, new Identifier("ssca_animation_test", id), item);
        }

        private static void assertControllers(NormalForm form, TestPlayer player, List<Identifier> states,
                                              List<AbstractAnimStateController> originals, boolean handoff, String label) {
            for (int i = 0; i < states.size(); i++) {
                check(form.getAnimStateController(player, null, states.get(i))
                                == (handoff ? FormAnimationCompat.VANILLA_CONTROLLER : originals.get(i)),
                        form.getFormID() + " / " + states.get(i) + " / " + label);
            }
        }

        @SuppressWarnings("unchecked")
        private static void setCarrying(TestPlayer player, boolean carrying) throws Exception {
            Class<?> dataClass = Class.forName("tschipp.carryon.common.carry.CarryOnData");
            Object data = dataClass.getConstructor(NbtCompound.class).newInstance(new NbtCompound());
            if (carrying) dataClass.getMethod("setCarryingPlayer").invoke(data);
            Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            TrackedData<NbtCompound> key = (TrackedData<NbtCompound>) manager.getField("CARRY_DATA_KEY").get(null);
            player.tracker.set(key, (NbtCompound) dataClass.getMethod("getNbt").invoke(data));
        }

        private static void check(boolean success, String description) {
            checks++;
            if (!success) throw new AssertionError(description);
        }
    }

    /** Allocated without a constructor; only these independent client state getters are used. */
    public static final class TestPlayer extends PlayerEntity {
        private boolean sneaking, swimming, fallFlying;
        private ItemStack hand, offHand;
        private DataTracker tracker;

        private TestPlayer(World world, BlockPos pos, float yaw, GameProfile profile) {
            super(world, pos, yaw, profile);
        }

        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSneaking() { return sneaking; }
        @Override public boolean isSwimming() { return swimming; }
        @Override public boolean isFallFlying() { return fallFlying; }
        @Override public ItemStack getMainHandStack() { return hand; }
        @Override public ItemStack getOffHandStack() { return offHand; }
        @Override public DataTracker getDataTracker() { return tracker; }
    }
}
