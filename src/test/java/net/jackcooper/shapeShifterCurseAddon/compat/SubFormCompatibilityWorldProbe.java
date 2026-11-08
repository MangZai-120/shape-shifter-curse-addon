package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.Gson;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.MultiplePowerType;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeReference;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.PerkUtils;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.ISubForm;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.NormalSubForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;

/** Issue #39: exercise SSC's real subform, Origin, Perk and Apoli lifecycle with SSCA mixins. */
public final class SubFormCompatibilityWorldProbe {
    private static int checks;
    private static int nativeForms;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static PowerType<?> type(Identifier id) {
        check(PowerTypeRegistry.contains(id), "Power must be registered: " + id);
        return PowerTypeRegistry.get(id);
    }

    private static void tree(PowerType<?> power, Set<Identifier> ids) {
        if (power instanceof PowerTypeReference<?> reference) power = reference.getReferencedPowerType();
        if (power == null || !ids.add(power.getIdentifier())) return;
        if (power instanceof MultiplePowerType<?> multiple)
            for (var child : multiple.getSubPowers()) tree(type(child), ids);
    }

    private static Set<Identifier> trees(List<Identifier> powers) {
        Set<Identifier> result = new LinkedHashSet<>();
        for (var id : powers) tree(type(id), result);
        return result;
    }

    private static void removed(ServerPlayerEntity player, Identifier source, Set<Identifier> ids, String stage) {
        var holder = PowerHolderComponent.KEY.get(player);
        for (var id : ids) check(!holder.hasPower(type(id), source), stage + " restored an excluded power: " + id);
    }

    private static void present(ServerPlayerEntity player, Identifier source, Set<Identifier> ids, String stage) {
        var holder = PowerHolderComponent.KEY.get(player);
        for (var id : ids) check(holder.hasPower(type(id), source), stage + " lost a declared power: " + id);
    }

    private static void nativeNoop(ServerPlayerEntity player, IForm form) {
        var holder = PowerHolderComponent.KEY.get(player);
        var instances = List.copyOf(holder.getPowers());
        NbtCompound before = new NbtCompound();
        holder.writeToNbt(before);
        check(OriginPowerCompatibility.beforePerks(player, form.getPerkTreeID()) == 0,
                "SSCA repaired a native form: " + form.getFormID());
        OriginPowerCompatibility.restore(player);
        NbtCompound after = new NbtCompound();
        holder.writeToNbt(after);
        check(before.equals(after), "SSCA restore changed native power data: " + form.getFormID());
        for (var power : instances)
            check(holder.getPower(power.getType()) == power, "SSCA restore rebuilt a native power");
        var source = form.getFormLayer().getRight();
        if (OriginRegistry.contains(source)) OriginPowerReloadGuard.duringReload(player, form, false, () ->
                check(!OriginPowerReloadGuard.deferCleanup(player, OriginRegistry.get(source), holder),
                        "SSCA deferred native Origin cleanup: " + form.getFormID()));
    }

    private static void nativeScope(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        var component = PlayerFormComponent.COMPONENT.get(player);
        for (var form : List.copyOf(RegPlayerForms.playerForms.values())) {
            if (!form.getFormID().getNamespace().equals("shape-shifter-curse")) continue;
            component.setForm(form);
            nativeNoop(player, form);
            nativeForms++;
        }
        check(nativeForms > 2, "Native form scope audit did not cover base forms");

        // A native form borrowing an SSCA Origin must still bypass every SSCA repair entry point.
        var addon = RegPlayerForms.getPlayerForm(net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.AXOLOTL_SP);
        var borrowed = addon.getFormLayer();
        var nativeForm = new NormalForm(new Identifier("shape-shifter-curse", "compat_test_borrowed_origin")) {
            @Override public Pair<Identifier, Identifier> getFormLayer() { return borrowed; }
        }.perkTree(addon.getPerkTreeID());
        component.setForm(nativeForm);
        var retired = type(WindSpiritPerks.LONG_POUNCE);
        PowerHolderComponent.KEY.get(player).addPower(retired, borrowed.getRight());
        nativeNoop(player, nativeForm);
        check(PowerHolderComponent.KEY.get(player).hasPower(retired, borrowed.getRight()),
                "SSCA cleaned a retired power from a native form");
    }

    private static void verify(TestContext context, ISubForm form, Identifier perkId, boolean addon) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setNoGravity(true);
        var master = form.getMasterForm();
        var source = form.getFormLayer().getRight();
        var modifier = form.getLayerModifier();
        var excluded = trees(modifier.getRight());
        var additions = trees(modifier.getLeft());
        Set<Identifier> inherited = new LinkedHashSet<>();
        for (var power : OriginRegistry.get(source).getPowerTypes()) tree(power, inherited);
        inherited.removeAll(excluded);
        var holder = PowerHolderComponent.KEY.get(player);

        FormUtils._setForm(player, master);
        present(player, source, excluded, "Master form");
        FormUtils._setForm(player, form);
        removed(player, source, excluded, "Fresh subform switch " + form.getFormID());
        present(player, source, additions, "Fresh subform switch");
        present(player, source, inherited, "Inherited base powers");

        if (addon) {
            var origin = OriginRegistry.get(source);
            OriginPowerReloadGuard.duringReload(player, form, false, () -> {
                OriginPowerReloadGuard.duringReload(player, master, false, () ->
                        check(!OriginPowerReloadGuard.deferCleanup(player, origin, holder),
                                "Nested native reload inherited the SSCA preservation scope"));
                check(OriginPowerReloadGuard.deferCleanup(player, origin, holder),
                        "SSCA preservation scope did not resume after a native reload");
                form.afterApplyLayer(player);
            });
            check(!OriginPowerReloadGuard.deferCleanup(player, origin, holder), "Reload scope leaked outside the form load");
        }

        // Native forms follow SSC's own replacement lifecycle; SSCA retains its own instances.
        Map<Identifier, Power> instances = new HashMap<>();
        for (var id : additions) instances.put(id, holder.getPower(type(id)));
        int count = holder.getPowers().size();
        for (int i = 0; i < 3; i++) {
            FormUtils._loadForm(player, form, false);
            if (addon) OriginPowerCompatibility.restore(player);
            else nativeNoop(player, form);
            removed(player, source, excluded, "Same-form reload / restore");
            for (var entry : instances.entrySet())
                check((holder.getPower(type(entry.getKey())) == entry.getValue()) == addon,
                        "Wrong native/addon reload lifecycle for " + entry.getKey());
            check(holder.getPowers().size() == count, "Repeated repair duplicated powers");
            check(OriginPowerCompatibility.beforePerks(player, form.getPerkTreeID()) == 0, "Repair is not idempotent");
        }

        // Simulate an old contaminated Apoli save, including an independent source of the same power.
        var foreign = new Identifier("compat_test", "accessory");
        var shared = modifier.getRight().get(0);
        for (var id : modifier.getRight()) holder.addPower(type(id), source);
        holder.addPower(type(shared), foreign);
        NbtCompound saved = new NbtCompound();
        holder.writeToNbt(saved);
        holder.readFromNbt(saved);
        if (addon) OriginPowerCompatibility.restore(player);
        else {
            nativeNoop(player, form);
            present(player, source, excluded, "Native restore must leave contaminated data for SSC to reconcile");
            FormUtils._loadForm(player, form, false);
        }
        removed(player, source, excluded, "Old contaminated save");
        check(holder.hasPower(type(shared), foreign), "Repair removed an independent power source");
        holder.removePower(type(shared), foreign);

        // Repair missing subform additions as well as missing inherited powers.
        for (var id : modifier.getLeft()) holder.removePower(type(id), source);
        var missingBase = inherited.iterator().next();
        holder.removePower(type(missingBase), source);
        if (addon) OriginPowerCompatibility.restore(player);
        else {
            nativeNoop(player, form);
            removed(player, source, additions, "Native restore must not repair missing additions");
            check(!holder.hasPower(type(missingBase), source), "Native restore repaired a missing base power");
            FormUtils._loadForm(player, form, false);
        }
        present(player, source, additions, "Missing subform additions");
        present(player, source, inherited, "Missing inherited powers");
        removed(player, source, excluded, "Missing-power repair");

        var perk = (NormalPerk) RegPerks.getPerk(perkId);
        PlayerFormComponent.COMPONENT.get(player).formPerkMap.put(form.getPerkTreeID(), new ArrayList<>(List.of(perkId)));
        player.totalExperience = 1234;
        PerkUtils.loadAllPerk(player, form.getPerkTreeID());
        var perkRemoved = trees(perk.powerRemove);
        var perkAdded = trees(perk.powerAdd);
        var expectedAdditions = new LinkedHashSet<>(additions);
        expectedAdditions.removeAll(perkRemoved);
        var expectedInherited = new LinkedHashSet<>(inherited);
        expectedInherited.removeAll(perkRemoved);
        if (!addon) {
            for (var id : perk.powerAdd) holder.removePower(type(id), source);
            nativeNoop(player, form);
            removed(player, source, perkAdded, "Native restore must not reapply purchased Perks");
            PerkUtils.loadAllPerk(player, form.getPerkTreeID());
        }
        for (int i = 0; i < 2; i++) {
            FormUtils._loadForm(player, form, false);
            removed(player, source, excluded, "Purchased Perk reload");
            removed(player, source, perkRemoved, "Purchased Perk exclusions");
            present(player, source, perkAdded, "Purchased Perk powers");
            present(player, source, expectedAdditions, "Subform powers with Perk");
            present(player, source, expectedInherited, "Inherited powers with Perk");
            check(player.totalExperience == 1234, "Reload charged experience for an existing Perk");
        }
        FormUtils.reApplyPower(player);
        removed(player, source, excluded, "Forced reapply");
        present(player, source, expectedAdditions, "Forced reapply");
        present(player, source, perkAdded, "Forced reapply Perk");
        FormUtils._setForm(player, master);
        present(player, source, excluded, "Switch back to master");
        removed(player, source, additions, "Switch back to master");
        FormUtils._setForm(player, form);
        removed(player, source, excluded, "Switch to subform again");
        present(player, source, expectedAdditions, "Switch to subform again");
        System.out.println("SUBFORM_COMPATIBILITY_PASS " + form.getFormID());
    }

    public static void start(TestContext context) {
        context.waitAndRun(70, () -> {
            try {
                if (io.github.apace100.apoli.Apoli.config == null)
                    io.github.apace100.apoli.Apoli.config = new io.github.apace100.apoli.util.ApoliConfig();
                nativeScope(context);
                var avali = (ISubForm) RegPlayerForms.BAT_3_SUB_AVALI;
                verify(context, avali, RegPerks.P_AvaliEnvironmentalProtection_1, false);
                verify(context, (ISubForm) RegPlayerForms.SNOW_FOX_3_SUB_MARBLED_POLECAT, RegPerks.P_MarbledPolecatPreciseMomentum, false);
                // Reverse boundary: an SSCA form borrowing SSC's Origin keeps SSCA compatibility.
                var addon = new NormalSubForm(new Identifier("my_addon", "compat_test_shared_origin"), avali.getMasterForm());
                addon.addPower(avali.getLayerModifier().getLeft().toArray(Identifier[]::new));
                addon.removePower(avali.getLayerModifier().getRight().toArray(Identifier[]::new));
                addon.perkTree(avali.getPerkTreeID());
                RegPlayerForms.registerPlayerForm(addon);
                verify(context, addon, RegPerks.P_AvaliEnvironmentalProtection_1, true);
                Files.writeString(Path.of("subform-compatibility-result.json"), new Gson().toJson(Map.of(
                        "checks", checks, "real_server_world", true, "native_forms_bypassed", nativeForms,
                        "native_subforms", 2, "addon_shared_origin_repaired", true)));
                System.out.println("SUBFORM_COMPATIBILITY_WORLD_PASS checks=" + checks);
                context.complete();
            } catch (Exception | AssertionError error) {
                throw new IllegalStateException(error);
            }
        });
    }
}
