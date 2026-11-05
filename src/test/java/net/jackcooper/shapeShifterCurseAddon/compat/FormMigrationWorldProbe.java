package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.CooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.VariableIntPower;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.ability.BeepSheepFormManager;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.command.FormArgumentType;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils;
import net.ssca.neomagic.NeoModel;
import net.ssca.neomagic.NeoRules;
import net.ssca.neomagic.NeoRuntime;
import net.ssca.neomagic.NeoState;

/** Real transformed SSC registry, player components and Origins in a disposable ServerWorld. */
public final class FormMigrationWorldProbe {
    private static int checks;
    private static int preservedPowers;
    private static int preservedCooldowns;
    private static final String[] FIELDS = {"nowFormID", "currentForm", "fallbackFormID",
            "BeforeCursedMoonAppliedForm", "AfterCursedMoonAppliedForm", "transformTargetForm",
            "customPotionFormID"};

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void start(TestContext context) {
        context.waitAndRun(70, () -> {
            try {
                if (io.github.apace100.apoli.Apoli.config == null) {
                    io.github.apace100.apoli.Apoli.config = new io.github.apace100.apoli.util.ApoliConfig();
                }
                check(FormIdMigration.aliases().size() == 10, "all ten renamed form IDs have legacy aliases");
                unchangedInput();
                ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
                player.setNoGravity(true);
                for (var entry : FormIdMigration.aliases().entrySet()) {
                    testForm(context, player, entry.getKey(), entry.getValue());
                }
                IForm vanilla = RegPlayerForms.getPlayerForm(new Identifier("shape-shifter-curse", "allay_sp"));
                check(vanilla != null && vanilla.getFormID().getPath().equals("allay_sp"),
                        "SSC's own similarly named form is unchanged");
                check(preservedPowers > 0 && preservedCooldowns > 0,
                        "retention assertions exercised actual power and cooldown instances");
                System.out.println("Form ID migration GameTest PASS: " + checks + " checks, ten old/new IDs, "
                        + preservedPowers + " actual power instances and " + preservedCooldowns
                        + " cooldowns retained; old components, Origins, command arguments, actions,"
                        + " potion targets and persisted sheep recovery. No live game window.");
                context.complete();
            } catch (Throwable failure) {
                failure.printStackTrace();
                throw new RuntimeException(failure);
            }
        });
    }

    private static void unchangedInput() {
        check(FormIdMigration.canonical((Identifier) null) == null, "nullable Identifier passes through");
        check(FormIdMigration.canonical((String) null) == null, "nullable String passes through");
        for (String value : List.of("", "my_addon:unknown", "other:allay_sp", "allay_sp", "bad ID!")) {
            check(value.equals(FormIdMigration.canonical(value)), "unrelated/malformed complete ID is untouched: " + value);
        }
        NbtCompound untouched = new NbtCompound();
        untouched.putString("nowFormID", "other:allay_sp");
        untouched.putInt("fallbackFormID", 12);
        NbtCompound external = new NbtCompound();
        external.putString("nowFormID", "my_addon:allay_sp");
        untouched.put("external_mod", external);
        check(FormIdMigration.migrateComponentNbt(untouched) == untouched,
                "unchanged components do not allocate or traverse unrelated nested data");
    }

    private static void testForm(TestContext context, ServerPlayerEntity player, Identifier oldId,
                                 Identifier newId) throws Exception {
        IForm form = RegPlayerForms.getPlayerForm(newId);
        check(form != null && form.getFormID().equals(newId), "canonical registry entry exists: " + newId);
        check(!RegPlayerForms.playerForms.containsKey(oldId), "legacy ID is not a duplicate registry entry: " + oldId);
        check(RegPlayerForms.getPlayerForm(oldId) == form, "Identifier registry lookup accepts legacy ID");
        check(RegPlayerForms.getPlayerForm(oldId.toString()) == form, "String registry lookup accepts legacy ID");
        check(RegPlayerForms.getPlayerFormOrThrow(oldId) == form, "strict lookup accepts legacy ID");
        var available = FormArgumentType.buildSuggestionsProvider((p, f) -> true).apply(player);
        check(available.stream().filter(newId::equals).count() == 1 && !available.contains(oldId),
                "selection/suggestions contain only one canonical form");
        check(commandForm(context, player, oldId) == form, "actual transformed command argument accepts legacy ID");
        Identifier originId = new Identifier(oldId.getNamespace(), "form_" + oldId.getPath());
        check(form.getFormLayer().getRight().equals(originId), "form retains its previous Origin/power resource identity");
        check(OriginRegistry.get(originId) != null, "existing Origin resource is loaded");

        FormUtils._setForm(player, form);
        PlayerFormComponent component = PlayerFormComponent.COMPONENT.get(player);
        var holder = PowerHolderComponent.KEY.get(player);
        List<Power> powers = new ArrayList<>(holder.getPowers());
        Map<Power, NbtElement> savedValues = new LinkedHashMap<>();
        for (Power power : powers) {
            if (power instanceof VariableIntPower value) value.setValue(7);
            if (power instanceof CooldownPower cooldown) cooldown.use();
            if (power instanceof VariableIntPower || power instanceof CooldownPower) {
                NbtElement tag = power.toTag();
                savedValues.put(power, tag == null ? null : tag.copy());
            }
        }
        NbtCompound legacySave = new NbtCompound();
        component.writeToNbt(legacySave);
        for (String field : FIELDS) legacySave.putString(field, oldId.toString());
        legacySave.put("formHistory", strings(oldId.toString(), newId.toString()));
        legacySave.put("free_perk_forms", strings(oldId.toString(), newId.toString()));
        NbtCompound original = legacySave.copy();
        component.readFromNbt(legacySave);
        check(legacySave.equals(original), "component read does not mutate caller-owned saved NBT");
        check(component.nowForm == form && newId.equals(component.nowFormID), "current and pre-SSC-1.0 currentForm migrate");
        check(newId.equals(component.fallbackFormID) && component.getFallbackForm() == form, "fallback is canonical");
        check(component.BeforeCursedMoonAppliedForm == form && component.AfterCursedMoonAppliedForm == form,
                "both cursed-moon recovery references survive");
        check(component.transformTargetForm == form && newId.equals(component.customPotionFormID),
                "pending transformation and potion references migrate");
        check(component.formHistory.size() == 2 && component.formHistory.stream().allMatch(f -> f == form),
                "legacy history entries resolve to the canonical object");
        check(component.freePerkForms.size() == 1 && component.freePerkForms.contains(newId),
                "old/new free-perk entitlement entries unify without losing entitlement");
        NbtCompound saved = new NbtCompound();
        component.writeToNbt(saved);
        for (String field : FIELDS) {
            if (!field.equals("currentForm")) check(saved.getString(field).equals(newId.toString()),
                    "component resave emits canonical field: " + field);
        }
        check(FormIdMigration.migrateComponentNbt(saved) == saved, "canonical resaves are idempotent without allocation");
        FormUtils._loadForm(player, form, false);
        check(holder.getPowers().size() == powers.size() && holder.getPowers().containsAll(powers),
                "same-Origin reload retains every actual Power instance");
        preservedPowers += powers.size();
        for (var value : savedValues.entrySet()) {
            check(java.util.Objects.equals(value.getValue(), value.getKey().toTag()),
                    "saved resource value/cooldown survives canonical reload: " + value.getKey().getType().getIdentifier());
            if (value.getKey() instanceof CooldownPower) preservedCooldowns++;
        }
        component.setForm(oldId);
        component.setFallbackForm(oldId);
        check(component.nowForm == form && newId.equals(component.nowFormID)
                && newId.equals(component.fallbackFormID), "direct component setters canonicalize their stored IDs");

        NbtCompound potion = new NbtCompound();
        potion.putString("targetForm", oldId.toString());
        check(newId.equals(CTPUtils.getCTPFormIDFromNBT(potion)), "old potion target NBT resolves to new form ID");
        check(potion.getString("targetForm").equals(oldId.toString()), "reading an old potion does not mutate its stack NBT");
        CTPUtils.setCTPFormIDToNBT(potion, oldId);
        check(potion.getString("targetForm").equals(newId.toString()), "new potion target saves canonical ID");
        CTPUtils.setTransformativePotionForm(player, oldId);
        check(newId.equals(component.customPotionFormID) && CTPUtils.getTransformativePotionForm(player) == form,
                "potion assignment stores and resolves the canonical ID");

        FormUtils._setForm(player, RegPlayerForms.ORIGINAL_BEFORE_ENABLE);
        JsonObject action = new JsonObject();
        action.addProperty("type", "shape-shifter-curse:transform_to_form");
        action.addProperty("form_id", oldId.toString());
        action.addProperty("instant", true);
        action.addProperty("force", true);
        ApoliDataTypes.ENTITY_ACTION.read(action).accept(player);
        check(FormUtils.getPlayerForm(player) == form, "old data-pack transform action applies canonical form");

        IForm sheep = RegPlayerForms.getPlayerForm(FormIdentifiers.SHEEP_FORM);
        FormUtils._setForm(player, sheep);
        NbtCompound root = new NbtCompound();
        NbtCompound snapshot = new NbtCompound();
        snapshot.putString("OriginalForm", oldId.toString());
        snapshot.putLong("SheepStart", player.getWorld().getTime());
        snapshot.put("Cooldowns", new NbtCompound());
        root.put("SSCA.SheepForm", snapshot);
        BeepSheepFormManager.readPlayerNbt(player, root);
        check(newId.toString().equals(BeepSheepFormManager.originalFormId(player)),
                "old persisted sheep snapshot exposes canonical original form");
        BeepSheepFormManager.onSheepEnd(player);
        check(FormUtils.getPlayerForm(player) == form && component.fallbackFormID.equals(newId),
                "ending a saved sheep transformation restores the renamed original form");
        neomagicPersistence(player, oldId, newId);
    }

    private static void neomagicPersistence(ServerPlayerEntity player, Identifier oldId, Identifier newId)
            throws Exception {
        check(NeoRules.profile(oldId.toString()).equals(NeoRules.profile(newId.toString())),
                "legacy and canonical IDs select the same spell profile");
        check(!NeoRules.profile(newId.toString()).reference(), "canonical form uses its configured profile, not fallback");
        var read = NeoState.class.getDeclaredMethod("read", NbtCompound.class);
        read.setAccessible(true);
        for (boolean oldStringEncoding : List.of(false, true)) {
            NeoState original = new NeoState();
            var data = original.player(player.getUuid());
            data.form = oldId.toString();
            data.burden = 42.5;
            data.recovery = 88;
            data.spellbookMasteryExpTen = 123;
            data.spellTutorialSeen = true;
            data.progress.put("migration:test", new double[]{1, 2, 3, 4});
            data.cooldowns.put("migration:spell", 75);
            var experiment = new NeoState.Experiment();
            experiment.form = oldId.toString();
            experiment.dimension = "minecraft:overworld";
            experiment.blueprint = NeoModel.Blueprint.starter();
            experiment.profile = NeoRules.profile(oldId.toString());
            experiment.result = "released";
            data.records.add(experiment);
            data.citations.add(experiment.id);
            NbtCompound tag = original.writeNbt(new NbtCompound());
            if (oldStringEncoding) {
                tag.getCompound("Players").putString(player.getUuidAsString(), NeoState.JSON.toJson(data));
            }
            NbtCompound savedInput = tag.copy();
            NeoState restored = (NeoState) read.invoke(null, tag);
            var actual = restored.player(player.getUuid());
            check(tag.equals(savedInput), "NeoMagic migration preserves caller-owned NBT");
            check(actual.form.equals(newId.toString()) && actual.records.get(0).form.equals(newId.toString()),
                    "both live and recorded NeoMagic form IDs migrate: stringEncoding=" + oldStringEncoding);
            check(actual.burden == 42.5 && actual.recovery == 88 && actual.spellbookMasteryExpTen == 123,
                    "migration preserves accumulated burden, recovery delay and mastery");
            check(actual.spellTutorialSeen && actual.cooldowns.equals(data.cooldowns)
                    && java.util.Arrays.equals(actual.progress.get("migration:test"), data.progress.get("migration:test"))
                    && actual.records.get(0).id.equals(experiment.id) && actual.citations.equals(data.citations),
                    "migration preserves tutorial, cooldowns, adaptations, records and citations");
            check(restored.isDirty(), "migrated NeoMagic state is scheduled for a canonical save");
            NeoState.get(player.getServer()).players.put(player.getUuid(), actual);
            NeoRuntime.refreshForm(player);
            check(actual.burden == 42.5 && actual.recovery == 88 && actual.form.equals(newId.toString()),
                    "first runtime form refresh does not mistake renaming for a form change or clear burden");
            NeoState resaved = (NeoState) read.invoke(null, restored.writeNbt(new NbtCompound()));
            check(!resaved.isDirty(), "canonical NeoMagic resave requires no repeated migration");
        }
    }

    private static IForm commandForm(TestContext context, ServerPlayerEntity player, Identifier id) throws Exception {
        var dispatcher = context.getWorld().getServer().getCommandManager().getDispatcher();
        var builder = new CommandContextBuilder<ServerCommandSource>(dispatcher, player.getCommandSource(),
                dispatcher.getRoot(), 0);
        builder.withArgument("form", new ParsedArgument<>(0, id.toString().length(), id));
        return FormArgumentType.getForm(builder.build(id.toString()), "form");
    }

    private static NbtList strings(String... values) {
        NbtList list = new NbtList();
        for (String value : values) list.add(NbtString.of(value));
        return list;
    }
}
