package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonObject;
import io.github.apace100.apoli.power.MultiplePowerType;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import java.util.ArrayList;
import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.forms.SscAddonForms;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.CommonConfig;
import net.onixary.shapeShifterCurseFabric.integration.origins.component.PlayerOriginComponent;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Impact;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Origin;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.PerkUtils;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Real transformed SSC revocation, CCA/Apoli ownership and mapped Perks over allocated fixtures only. */
public final class OriginPowerReloadIntegrationProbe {
    private static int checks;
    private static final PowerFactory<Power> DUMMY = new PowerFactory<>(id("factory"), new SerializableData(), data -> Power::new);

    public static void run() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        AxolotlSpPerkIntegrationProbe.unsafe = (sun.misc.Unsafe) field.get(null);
        var world = AxolotlSpPerkIntegrationProbe.allocate(AxolotlSpPerkIntegrationProbe.TestWorld.class);
        world.entities = new java.util.HashMap<>(); world.clock = 100000;
        AxolotlSpPerkIntegrationProbe.world = world;
        ShapeShifterCurseFabric.commonConfig = new CommonConfig();
        ShapeShifterCurseFabric.commonConfig.enableInitialForm = false;
        SscAddonForms.register();
        for (Identifier perkId : RegPerks.getPerkTree(RegPerks.T_Axolotl3PerkTree).getAllPerks()) {
            var perk = (NormalPerk) RegPerks.getPerk(perkId);
            for (Identifier power : perk.powerAdd) AxolotlSpPerkIntegrationProbe.register(power, null);
            for (Identifier power : perk.powerRemove) AxolotlSpPerkIntegrationProbe.register(power, null);
        }
        AxolotlSpPerkIntegrationProbe.register(AxolotlSpPerks.WATER_MAGIC,
                AxolotlSpPerkIntegrationProbe.json("my_addon", AxolotlSpPerks.WATER_MAGIC.getPath()));
        AxolotlSpPerkIntegrationProbe.register(AxolotlSpPerks.TIDAL_PULL,
                AxolotlSpPerkIntegrationProbe.json("my_addon", AxolotlSpPerks.TIDAL_PULL.getPath()));
        var form = RegPlayerForms.getPlayerForm(FormIdentifiers.AXOLOTL_SP);
        var p = AxolotlSpPerkIntegrationProbe.player();
        p.form.setForm(form); p.form.nowPerkTree = form.getPerkTreeID();
        p.form.formPerkMap.put(form.getPerkTreeID(), new ArrayList<>(List.of(RegPerks.P_AxolotlWaterMagicEfficiency)));
        var source = form.getFormLayer().getRight();
        var holder = p.powers;
        var retainedBase = simple("base"); var missingBase = simple("missing_base");
        var origin = new Origin(source, ItemStack.EMPTY, Impact.NONE, 0, 0).add(retainedBase, missingBase);
        OriginRegistry.register(origin);
        holder.addPower(retainedBase, source);
        PerkUtils.loadAllPerk(p, form.getPerkTreeID());
        var mapped = PowerTypeRegistry.get(AxolotlSpPerks.WATER_MAGIC);
        var child = PowerTypeRegistry.get(AxolotlSpPerks.WATER_MAGIC_ATTACK);
        Power mappedInstance = holder.getPower(mapped), childInstance = holder.getPower(child);
        check(mappedInstance != null && childInstance != null, "Real SSC Perk is mapped through the existing SSCA onLoad hook");
        PowerType<AxolotlSpPerkIntegrationProbe.TestCooldown> timer = new PowerType<>(id("accessory_timer"),
                new PowerFactory<AxolotlSpPerkIntegrationProbe.TestCooldown>(id("timer_factory"), new SerializableData(),
                        data -> (type, entity) -> new AxolotlSpPerkIntegrationProbe.TestCooldown(type, entity, 100)).read(new JsonObject()));
        PowerTypeRegistry.register(timer.getIdentifier(), timer);
        holder.addPower(timer, source); var cooldown = holder.getPower(timer); cooldown.use();
        world.clock += 13; int remaining = cooldown.getRemainingTicks();
        var stale = simple("removed_base"); var foreign = id("foreign_source");
        holder.addPower(stale, source); holder.addPower(stale, foreign); Power foreignInstance = holder.getPower(stale);
        var shared = simple("shared_child"); var discardedChild = simple("obsolete_child");
        var obsoleteParent = multiple("obsolete_parent", shared, discardedChild);
        holder.addPower(obsoleteParent, source); Power sharedInstance = holder.getPower(shared);
        var origins = new PlayerOriginComponent(p);
        int syncBefore = p.syncQueries;
        OriginPowerReloadGuard.duringReload(p, form, false, () -> {
            origins.grantPowersFromOrigin(origin, holder);
            origins.revokeRemovedPowers(origin, holder);
            check(holder.getPower(mapped) == mappedInstance, "Revocation defers before mapped Perk declarations");
            PerkUtils.loadAllPerk(p, form.getPerkTreeID());
            check(!holder.addPower(timer, source), "Existing direct accessory declaration is observed before addPower returns false");
            holder.addPower(shared, source);
        });
        check(holder.getPower(mapped) == mappedInstance && holder.getPower(child) == childInstance,
                "Mapped Multiple parent and child retain their actual Power objects");
        check(holder.getPower(timer) == cooldown && cooldown.getRemainingTicks() == remaining,
                "Same-origin accessory cooldown instance and remaining ticks survive reconciliation");
        check(holder.hasPower(missingBase, source), "Upstream missing base-power grant remains effective");
        check(!holder.hasPower(stale, source) && holder.hasPower(stale, foreign) && holder.getPower(stale) == foreignInstance,
                "Deleted base source is removed without touching an unrelated owner or its instance");
        check(!holder.hasPower(obsoleteParent, source) && !holder.hasPower(discardedChild, source),
                "Unrequested Multiple parent and obsolete descendant are removed");
        check(holder.hasPower(shared, source) && holder.getPower(shared) == sharedInstance,
                "Removing an obsolete parent preserves a separately declared shared child's instance");
        check(p.syncQueries == syncBefore + 1, "Only actual stale cleanup requests one final component sync");
        syncBefore = p.syncQueries;
        OriginPowerReloadGuard.duringReload(p, form, false, () -> {
            origins.revokeRemovedPowers(origin, holder);
            PerkUtils.loadAllPerk(p, form.getPerkTreeID());
            holder.addPower(timer, source); holder.addPower(shared, source);
        });
        check(p.syncQueries == syncBefore, "An idempotent reload adds no final cleanup sync");
        check(holder.getPower(timer) == cooldown && holder.getPower(mapped) == mappedInstance,
                "Repeated reload does not reset legitimate resource instances");

        var nestedLeaf = simple("nested_leaf"); var nestedBranch = multiple("nested_branch", nestedLeaf);
        var nestedRoot = multiple("nested_root", nestedBranch); origin.add(nestedRoot); holder.addPower(nestedRoot, source);
        holder.removePower(nestedLeaf, source);
        OriginPowerReloadGuard.duringReload(p, form, false, () -> {
            origins.revokeRemovedPowers(origin, holder);
            PerkUtils.loadAllPerk(p, form.getPerkTreeID());
            holder.addPower(timer, source); holder.addPower(shared, source);
        });
        check(holder.hasPower(nestedLeaf, source), "Existing SSCA repair still restores a missing nested Multiple base descendant");
        check(holder.hasPower(nestedRoot, source) && holder.hasPower(nestedBranch, source),
                "Origin nested Multiple declarations survive the final stale cleanup");

        // Exercise the actual retired identifier used by beforePerks, even if a broken old Origin still declares it.
        var retired = WindSpiritPerks.LONG_POUNCE;
        AxolotlSpPerkIntegrationProbe.register(retired, null); var retiredType = PowerTypeRegistry.get(retired);
        origin.add(retiredType);
        OriginPowerReloadGuard.duringReload(p, form, false, () -> {
            origins.grantPowersFromOrigin(origin, holder); origins.revokeRemovedPowers(origin, holder);
            PerkUtils.loadAllPerk(p, form.getPerkTreeID()); holder.addPower(timer, source); holder.addPower(shared, source);
        });
        check(!holder.hasPower(retiredType, source), "Explicitly retired skills are not restored by the guard");
        var removedExplicitly = simple("explicit_remove"); holder.addPower(removedExplicitly, source);
        OriginPowerReloadGuard.duringReload(p, form, false, () -> {
            origins.revokeRemovedPowers(origin, holder); holder.addPower(removedExplicitly, source);
            holder.removePower(removedExplicitly, source);
            PerkUtils.loadAllPerk(p, form.getPerkTreeID()); holder.addPower(timer, source); holder.addPower(shared, source);
        });
        check(!holder.hasPower(removedExplicitly, source), "Normal Perk removals are never blocked during declaration");

        var other = AxolotlSpPerkIntegrationProbe.player();
        other.form.setForm(form); other.form.nowPerkTree = form.getPerkTreeID();
        var otherOrigins = new PlayerOriginComponent(other);
        var isolated = simple("player_isolation"); holder.addPower(isolated, source);
        OriginPowerReloadGuard.duringReload(p, form, false, () -> {
            origins.revokeRemovedPowers(origin, holder);
            check(!OriginPowerReloadGuard.deferCleanup(other, origin, other.powers), "Another player's Origin cannot use the outer scope");
            OriginPowerReloadGuard.duringReload(other, form, false, () -> {
                otherOrigins.revokeRemovedPowers(origin, other.powers); other.powers.addPower(isolated, source);
            });
            OriginPowerReloadGuard.duringReload(p, form, true, () ->
                    check(!OriginPowerReloadGuard.deferCleanup(p, origin, holder), "Nested forced loads suspend the outer preservation scope"));
            try {
                OriginPowerReloadGuard.duringReload(other, form, false, () -> { throw new IllegalStateException("nested"); });
            } catch (IllegalStateException expected) {
                check(expected.getMessage().equals("nested"), "Nested failure propagates without leaking another player's scope");
            }
            PerkUtils.loadAllPerk(p, form.getPerkTreeID()); holder.addPower(timer, source); holder.addPower(shared, source);
        });
        check(!holder.hasPower(isolated, source) && other.powers.hasPower(isolated, source),
                "Another player's declaration cannot retain a stale ability on the original player");
        check(holder.getPower(timer) == cooldown && holder.getPower(mapped) == mappedInstance,
                "Outer declarations resume after nested loads, forced calls and exceptions");
        try {
            OriginPowerReloadGuard.duringReload(p, form, false, () -> { origins.revokeRemovedPowers(origin, holder); throw new IllegalStateException("fixture"); });
            throw new AssertionError("Expected fixture exception");
        } catch (IllegalStateException expected) { check(expected.getMessage().equals("fixture"), "Original load failure propagates"); }
        check(!OriginPowerReloadGuard.deferCleanup(p, origin, holder), "Exception path always removes transaction context");
        OriginPowerReloadGuard.duringReload(p, form, true, () -> origins.revokeRemovedPowers(origin, holder));
        check(!holder.hasPower(mapped, source) && !holder.hasPower(timer, source), "Forced loads retain the upstream full-reset behavior");
        check(!OriginPowerReloadGuard.deferCleanup(p, origin, holder), "Forced/outside calls never leave transaction context");

        check(java.util.Arrays.stream(FormUtils.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("ssca$preserveDeclaredPowerInstances")),
                "Actual transformed FormUtils contains the scoped form-load wrapper");
        var tool = new ItemStack(net.jackcooper.shapeShifterCurseAddon.SscAddon.WATER_SPEAR);
        tool.getOrCreateNbt().putString("CustomNameProof", "retained"); tool.getOrCreateNbt().putBoolean("MorphScaleItem", true);
        check(net.onixary.shapeShifterCurseFabric.additional_power.IsMorphScaleItemCondition.MSI_condition(null, tool),
                "SSC MorphScaleItem marker accepts an SSCA item with its custom NBT intact");
        System.out.println("Origin power reload integration: " + checks + " transformed CCA/Apoli checks passed; no world/server was started.");
    }

    private static Identifier id(String path) { return new Identifier("my_addon", "reload_test_" + path); }
    private static PowerType<Power> simple(String path) {
        var type = new PowerType<Power>(id(path), DUMMY.read(new JsonObject()));
        PowerTypeRegistry.register(type.getIdentifier(), type); return type;
    }
    private static MultiplePowerType<Power> multiple(String path, PowerType<?>... children) {
        var type = new MultiplePowerType<Power>(id(path), DUMMY.read(new JsonObject()));
        type.setSubPowers(java.util.Arrays.stream(children).map(PowerType::getIdentifier).toList());
        PowerTypeRegistry.register(type.getIdentifier(), type); return type;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
