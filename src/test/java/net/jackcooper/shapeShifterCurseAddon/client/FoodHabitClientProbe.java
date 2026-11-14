package net.jackcooper.shapeShifterCurseAddon.client;

import com.google.gson.GsonBuilder;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.ModifyFoodPower;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Real loaded origins, item-use interception, food recovery and client/server consumption. */
public final class FoodHabitClientProbe {
    private static final List<Identifier> FIXED_FORMS = List.of(FormIdentifiers.ALLAY_SP,
            FormIdentifiers.FALLEN_ALLAY_SP, FormIdentifiers.FAMILIAR_FOX_SP, FormIdentifiers.FAMILIAR_FOX_RED);
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final long START = System.nanoTime();
    private static boolean starting, submitted, eating;
    private static volatile boolean ready, finished;
    private static volatile Throwable failure;
    private static int ticks, checks, formCount, clientIndex, waitTicks;
    private static boolean originalConfig;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void config(boolean enabled) {
        ShapeShifterCurseFabric.commonConfig.enableFoodHabitSystem = enabled;
    }

    private static void form(ServerPlayerEntity p, Identifier id) {
        var form = RegPlayerForms.playerForms.get(id);
        check(form != null, "registered form " + id);
        p.stopUsingItem();
        FormUtils._setForm(p, form);
        // The normal custom-food cache refresh is periodic; settle it before the immediate matrix.
        net.onixary.shapeShifterCurseFabric.util.CustomEdibleUtils.ReloadPlayerCustomEdible(p);
        p.changeGameMode(GameMode.SURVIVAL);
        p.getAbilities().invulnerable = true;
        p.setHealth(p.getMaxHealth());
        p.clearStatusEffects();
    }

    private static ItemStack prepare(ServerPlayerEntity p, Item item) {
        p.stopUsingItem();
        p.getInventory().selectedSlot = 4; // Automatic form tools occupy slots 0/1/8.
        p.clearStatusEffects();
        p.getHungerManager().setFoodLevel(2);
        p.getHungerManager().setSaturationLevel(0);
        p.getHungerManager().setExhaustion(0);
        p.getHungerManager().update(p); // Also initializes Apoli's hunger owner cache.
        var stack = new ItemStack(item, 2);
        p.setStackInHand(Hand.MAIN_HAND, stack);
        return stack;
    }

    private static int eat(ServerPlayerEntity p, Item item, boolean allowed, boolean ordinaryRecovery) {
        var stack = prepare(p, item);
        var result = p.interactionManager.interactItem(p, p.getServerWorld(), stack, Hand.MAIN_HAND);
        String label = FormUtils.getPlayerForm(p).getFormID() + "/" + Registries.ITEM.getId(item)
                + "/diet=" + ShapeShifterCurseFabric.commonConfig.enableFoodHabitSystem;
        check(result.isAccepted() == allowed, label + " use result=" + result);
        check(p.isUsingItem() == allowed, label + " eating state");
        if (!allowed) {
            check(stack.getCount() == 2 && p.getHungerManager().getFoodLevel() == 2, label + " denied food untouched");
            return 0;
        }
        p.setStackInHand(Hand.MAIN_HAND, stack.finishUsing(p.getWorld(), p));
        p.stopUsingItem();
        int gained = p.getHungerManager().getFoodLevel() - 2;
        check(p.getMainHandStack().getCount() == 1, label + " exactly one consumed");
        if (ordinaryRecovery) {
            var food = item.getFoodComponent();
            check(gained >= food.getHunger(), label + " hunger must not be penalized: " + gained);
            float minimum = Math.min(2 + food.getHunger(), food.getHunger() * food.getSaturationModifier() * 2);
            check(p.getHungerManager().getSaturationLevel() + 0.0001 >= minimum,
                    label + " saturation must not be penalized: " + p.getHungerManager().getSaturationLevel());
        }
        RESULTS.add(Map.of("case", label, "hunger", gained, "saturation", p.getHungerManager().getSaturationLevel()));
        return gained;
    }

    private static void audit(ServerPlayerEntity p) {
        var forms = RegPlayerForms.playerForms.keySet().stream()
                .filter(id -> id.getNamespace().equals("my_addon"))
                .filter(id -> RegPlayerForms.playerForms.get(id).getFormID().equals(id)).sorted().toList();
        formCount = forms.size();
        check(formCount >= 23, "audit includes every addon form: " + forms);
        config(false);
        for (var id : forms) {
            form(p, id);
            for (var food : List.of(Items.BREAD, Items.APPLE, Items.BEEF, Items.COOKED_BEEF, Items.COD))
                eat(p, food, true, true);
        }
        // Toggle the existing powers without reapplying the form or reloading resources.
        for (var id : FIXED_FORMS) {
            form(p, id);
            for (boolean enabled : new boolean[]{true, false, true, false}) {
                config(enabled);
                if (id.equals(FormIdentifiers.ALLAY_SP) || id.equals(FormIdentifiers.FALLEN_ALLAY_SP)) {
                    eat(p, Items.BREAD, !enabled, !enabled);
                    eat(p, Items.BEEF, !enabled, !enabled);
                    if (id.equals(FormIdentifiers.FALLEN_ALLAY_SP)) eat(p, Items.COOKED_BEEF, true, true);
                    else check(eat(p, Items.AMETHYST_SHARD, true, false) > 0, "amethyst remains edible");
                } else {
                    int gained = eat(p, Items.COOKED_BEEF, true, !enabled);
                    check(enabled ? gained < 8 : gained == 8, "familiar recovery follows toggle: " + gained);
                }
            }
        }
        form(p, FormIdentifiers.ALLAY_SP);
        config(true);
        var marked = prepare(p, Items.BREAD);
        marked.getOrCreateNbt().putBoolean("MorphScaleFood", true);
        check(!p.interactionManager.interactItem(p, p.getServerWorld(), marked, Hand.MAIN_HAND).isAccepted(),
                "enabled crystal diet still does not grant a morph-scale exemption");
        for (var id : List.of(FormIdentifiers.WILD_CAT_SP, FormIdentifiers.WILD_CAT_NIGHTMARE)) {
            form(p, id);
            for (boolean enabled : new boolean[]{true, false}) {
                config(enabled);
                check(eat(p, Items.BEEF, true, true) == 6, "raw meat bonus survives diet toggle " + id);
            }
        }
        for (var id : List.of(FormIdentifiers.UPGRADE_FAMILIAR_FOX, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
            form(p, id);
            config(true);
            check(eat(p, Items.COOKED_BEEF, true, false) < 8, "inherited familiar penalty remains enabled");
            config(false);
            check(eat(p, Items.COOKED_BEEF, true, true) == 8, "inherited familiar penalty switches off");
        }
        form(p, FormIdentifiers.UPGRADE_AXOLOTL);
        config(true);
        check(eat(p, Items.BREAD, true, false) == 0, "enabled carnivore diet gives no bread nourishment");
        config(false);
        eat(p, Items.BREAD, true, true);
        form(p, new Identifier("shape-shifter-curse", "spider_2"));
        for (boolean enabled : new boolean[]{true, false, true, false}) {
            config(enabled);
            int gained = eat(p, Items.COOKED_BEEF, true, !enabled);
            check(enabled ? gained < 8 : gained == 8, "upstream spider meat penalty follows toggle");
        }
    }

    private static void prepareClient(MinecraftClient mc) {
        ready = false;
        mc.getServer().execute(() -> { try {
            var p = mc.getServer().getPlayerManager().getPlayerList().get(0);
            form(p, FIXED_FORMS.get(clientIndex));
            config(false);
            p.getInventory().clear();
            prepare(p, Items.BREAD);
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(4));
            p.playerScreenHandler.syncState();
            ready = true;
        } catch (Throwable e) { fail(e); } });
    }

    public static void install() {
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle());
            originalConfig = ShapeShifterCurseFabric.commonConfig.enableFoodHabitSystem;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { try {
            if (finished) return;
            if (System.nanoTime() - START > 240_000_000_000L) throw new AssertionError("food habit client timed out");
            if (mc.getOverlay() != null) return;
            if (!starting && mc.currentScreen instanceof TitleScreen) {
                starting = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.getViewDistance().setValue(4);
                mc.options.getMaxFps().setValue(60);
                var data = new DataConfiguration(new DataPackSettings(List.of("vanilla", "fabric"), List.of()), DataConfiguration.SAFE_MODE.enabledFeatures());
                var rules = new GameRules();
                rules.get(GameRules.NATURAL_REGENERATION).set(false, null);
                mc.createIntegratedServerLoader().createAndStart("FoodHabit-" + System.currentTimeMillis(),
                        new LevelInfo("Food habit regression", GameMode.SURVIVAL, false, Difficulty.NORMAL, true, rules, data),
                        new GeneratorOptions(20261114L, false, false),
                        r -> r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if (mc.world == null || mc.player == null || mc.getServer() == null) return;
            if (mc.currentScreen != null) mc.setScreen(null);
            if (++ticks < 40) return;
            if (!submitted) {
                submitted = true;
                mc.getServer().execute(() -> { try {
                    audit(mc.getServer().getPlayerManager().getPlayerList().get(0));
                    mc.execute(() -> prepareClient(mc));
                } catch (Throwable e) { fail(e); } });
            }
            if (!ready) return;
            if (!eating) {
                if (++waitTicks < 25) return;
                check(FormUtils.getPlayerForm(mc.player).getFormID().equals(FIXED_FORMS.get(clientIndex)), "client form synced");
                check(mc.player.getMainHandStack().isOf(Items.BREAD) && mc.player.getMainHandStack().getCount() == 2,
                        "client food synced: " + mc.player.getMainHandStack() + " slot=" + mc.player.getInventory().selectedSlot);
                check(mc.player.getHungerManager().getFoodLevel() == 2, "client hunger synced");
                for (boolean enabled : new boolean[]{true, false, true, false}) {
                    config(enabled);
                    if (clientIndex < 2) {
                        var result = mc.player.getMainHandStack().use(mc.world, mc.player, Hand.MAIN_HAND);
                        check(result.getResult().isAccepted() != enabled, "client restriction follows toggle");
                        mc.player.clearActiveItem();
                    } else {
                        var penalties = PowerHolderComponent.getPowers(mc.player, ModifyFoodPower.class);
                        check(penalties.stream().anyMatch(p -> p.getType().getIdentifier().getPath().equals("form_familiar_fox_sp_food_down")) == enabled,
                                "client food penalty follows toggle");
                    }
                }
                check(mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted(), "network food use accepted");
                mc.options.useKey.setPressed(true);
                eating = true;
                waitTicks = 0;
            } else if (++waitTicks >= 36) {
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(mc.player);
                ready = false;
                mc.getServer().execute(() -> { try {
                    var p = mc.getServer().getPlayerManager().getPlayerList().get(0);
                    check(p.getMainHandStack().getCount() == 1, "network consumption removes exactly one bread");
                    check(p.getHungerManager().getFoodLevel() == 7, "network consumption restores five hunger");
                    check(Math.abs(p.getHungerManager().getSaturationLevel() - 6) < .001, "network consumption restores six saturation");
                    mc.execute(() -> { try {
                        eating = false; waitTicks = 0;
                        if (++clientIndex < FIXED_FORMS.size()) prepareClient(mc);
                        else {
                            config(originalConfig);
                            Files.writeString(Path.of("food-habit-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                                    "passed", true, "checks", checks, "addon_forms", formCount, "network_consumption_forms", 4,
                                    "environment", "isolated real client and integrated server", "food_results", RESULTS)));
                            finished = true;
                            System.out.println("FOOD_HABIT_PASS checks=" + checks + " forms=" + formCount);
                            mc.scheduleStop();
                        }
                    } catch (Throwable e) { fail(e); } });
                } catch (Throwable e) { fail(e); } });
            }
        } catch (Throwable e) { fail(e); } });
    }

    private static void fail(Throwable e) {
        if (failure != null) return;
        failure = e; finished = true; e.printStackTrace();
        config(originalConfig);
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient.getInstance().options.useKey.setPressed(false);
            MinecraftClient.getInstance().scheduleStop();
        });
    }

    public static void verifyResult() {
        if (failure != null) throw new AssertionError("food habit check failed", failure);
        if (!finished) throw new AssertionError("client exited before food checks completed");
    }
}
