package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import io.github.apace100.apoli.util.StackPowerUtil;
import net.jackcooper.shapeShifterCurseAddon.spell.NamespaceMigrationSpellSaveChecks;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdMigration;
import net.jackcooper.shapeShifterCurseAddon.util.NamespaceMigration;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Impact;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.Origin;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginLayer;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginLayers;
import net.onixary.shapeShifterCurseFabric.integration.origins.origin.OriginRegistry;
import net.onixary.shapeShifterCurseFabric.integration.origins.registry.ModComponents;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;

public final class NamespaceMigrationWorldChecks {
    private NamespaceMigrationWorldChecks() {}

    public static void verify(ServerPlayerEntity player) throws Exception {
        var forms = PlayerFormComponent.COMPONENT.get(player);
        NbtCompound savedForm = new NbtCompound();
        savedForm.putString("nowFormID", "my_addon:bat_desmodus");
        forms.readFromNbt(savedForm);
        require(forms.nowFormID.toString().equals("ssc_addon:bat_desmodus"), "Namespace-only form change");
        require(FormIdMigration.canonical("my_addon:allay_sp").equals("ssc_addon:allay_crystal_sound")
                && FormIdMigration.canonical("my_addon:allay_crystal_sound").equals("ssc_addon:allay_crystal_sound"),
                "Old and intermediate form IDs");
        require(FormIdMigration.canonical("my_addon:some_custom_form").equals("my_addon:some_custom_form"),
                "Unknown external form remains independent");
        convertedPackSelection(player);
        equipmentPower(player);

        var origin = ModComponents.ORIGIN.get(player);
        NbtCompound savedOrigin = new NbtCompound();
        NbtCompound layer = new NbtCompound();
        layer.putString("Layer", "origins:origin");
        layer.putString("Origin", "my_addon:form_snow_fox_sp");
        NbtList layers = new NbtList();
        layers.add(layer);
        savedOrigin.put("OriginLayers", layers);
        origin.readFromNbt(savedOrigin);
        var actual = origin.getOrigin(OriginLayers.getLayer(new Identifier("origins:origin")));
        require(actual != null && actual.getIdentifier().toString().equals("ssc_addon:form_snow_fox_sp"),
                "Previous SSC Origin layer resolves through the component reader");

        NbtCompound saved = new NbtCompound();
        NbtList rows = new NbtList();
        rows.add(power("my_addon:form_snow_fox_sp_resource", 38, "my_addon:form_snow_fox_sp"));
        saved.put("Powers", rows);
        NbtCompound untouched = saved.copy();
        var holder = PowerHolderComponent.KEY.get(player);
        holder.readFromNbt(saved);
        var type = PowerTypeRegistry.get(new Identifier("ssc_addon:form_snow_fox_sp_resource"));
        require(holder.getPower(type) instanceof VariableIntPower value && value.getValue() == 38,
                "Previous Apoli Type restores the live power and resource value");
        require(holder.hasPower(type, new Identifier("ssc_addon:form_snow_fox_sp")) && saved.equals(untouched),
                "Source migration and immutable input");

        rows.add(0, power("ssc_addon:form_snow_fox_sp_resource", 77, "external:grant"));
        var mixed = NamespaceMigration.powers(saved);
        var migratedRows = mixed.getList("Powers", NbtElement.COMPOUND_TYPE);
        var only = migratedRows.getCompound(0);
        var grants = only.getList("Sources", NbtElement.STRING_TYPE);
        require(migratedRows.size() == 1 && only.getInt("Data") == 77 && grants.size() == 2
                && grants.contains(NbtString.of("ssc_addon:form_snow_fox_sp"))
                && grants.contains(NbtString.of("external:grant")), "Mixed duplicate preserves current data and both grants");
        require(NamespaceMigration.powers(mixed) == mixed, "Current save is idempotent");
        NbtCompound tube = new NbtCompound();
        tube.putString("id", "ssca_neomagic:test_tube_potion");
        tube.putByte("Count", (byte) 3);
        var potion = new NbtCompound();
        potion.putString("Potion", "minecraft:water");
        potion.putString("targetForm", "my_addon:allay_sp");
        tube.put("tag", potion);
        var stack = ItemStack.fromNbt(tube);
        var tubeSave = stack.writeNbt(new NbtCompound());
        require(tubeSave.getString("id").equals("ssc_addon:test_tube_potion")
                && tubeSave.getCompound("tag").getString("targetForm").equals("ssc_addon:allay_crystal_sound")
                && stack.getCount() == 3, "Old filled tube preserves its targeted form and count on disk");
        NamespaceMigrationSpellSaveChecks.verify(player.getServer());
        System.out.println("NAMESPACE_MIGRATION_WORLD_PASS forms origins powers grants spellFileRestart customOriginLayers equippedStackPower");
    }

    private static void convertedPackSelection(ServerPlayerEntity player) {
        var oldLayer = new Identifier("my_addon", "namespace_probe_layer");
        var layerId = new Identifier("ssc_addon", oldLayer.getPath());
        var oldOrigin = new Identifier("ssca_neomagic", "namespace_probe_origin");
        var originId = new Identifier("ssc_addon", oldOrigin.getPath());
        var current = new Origin(originId, ItemStack.EMPTY, Impact.NONE, 0, 0);
        var legacy = new Origin(oldOrigin, ItemStack.EMPTY, Impact.NONE, 0, 0);
        OriginLayer migratedLayer = layer(layerId, originId);
        OriginLayer independentLayer = layer(oldLayer, oldOrigin);
        OriginRegistry.register(current);
        OriginLayers.add(migratedLayer);
        try {
            NbtCompound saved = new NbtCompound(), row = new NbtCompound();
            row.putString("Layer", oldLayer.toString());
            row.putString("Origin", oldOrigin.toString());
            NbtList rows = new NbtList();
            rows.add(row);
            saved.put("OriginLayers", rows);
            var component = ModComponents.ORIGIN.get(player);
            component.readFromNbt(saved);
            require(component.getOrigin(migratedLayer) == current, "A converted pack retains its custom layer selection");

            var grant = new NbtCompound();
            NbtList grants = new NbtList();
            grants.add(power("ssc_addon:form_snow_fox_sp_resource", 27, oldOrigin.toString()));
            grant.put("Powers", grants);
            var holder = PowerHolderComponent.KEY.get(player);
            var type = PowerTypeRegistry.get(new Identifier("ssc_addon:form_snow_fox_sp_resource"));
            holder.readFromNbt(grant);
            require(holder.hasPower(type, originId), "A converted custom Origin retains ownership of its power");

            NbtCompound mixed = saved.copy();
            var canonicalSelection = new NbtCompound();
            canonicalSelection.putString("Layer", layerId.toString());
            canonicalSelection.putString("Origin", "origins:empty");
            mixed.getList("OriginLayers", NbtElement.COMPOUND_TYPE).add(0, canonicalSelection);
            var normalized = NamespaceMigration.origins(mixed).getList("OriginLayers", NbtElement.COMPOUND_TYPE);
            require(normalized.size() == 1 && normalized.getCompound(0).equals(canonicalSelection),
                    "A previously saved canonical selection wins over the older layer row");

            OriginRegistry.register(legacy);
            OriginLayers.add(independentLayer);
            component.readFromNbt(saved);
            require(component.getOrigin(independentLayer) == legacy && NamespaceMigration.origins(saved) == saved,
                    "Still-registered custom layer and Origin IDs retain their own selection");
            holder.readFromNbt(grant);
            require(holder.hasPower(type, oldOrigin) && !holder.hasPower(type, originId),
                    "An independent Origin keeps its own grant when both namespaces exist");
        } finally {
            OriginLayers.getLayers().remove(migratedLayer);
            OriginLayers.getLayers().remove(independentLayer);
            OriginRegistry.remove(originId);
            OriginRegistry.remove(oldOrigin);
        }
    }

    private static OriginLayer layer(Identifier id, Identifier origin) {
        JsonObject definition = new JsonObject();
        JsonArray origins = new JsonArray();
        origins.add(origin.toString());
        definition.add("origins", origins);
        return OriginLayer.fromJson(id, definition);
    }

    private static void equipmentPower(ServerPlayerEntity player) {
        var oldId = new Identifier("my_addon:form_snow_fox_sp_resource");
        var id = new Identifier("ssc_addon", oldId.getPath());
        var helmet = new ItemStack(Items.IRON_HELMET);
        StackPowerUtil.addPower(helmet, EquipmentSlot.HEAD, oldId, true, false);
        var saved = helmet.writeNbt(new NbtCompound());
        var original = saved.copy();
        var restored = ItemStack.fromNbt(saved);
        var powers = StackPowerUtil.getPowers(restored, EquipmentSlot.HEAD);
        require(powers.size() == 1 && powers.get(0).powerId.equals(id) && powers.get(0).isHidden && saved.equals(original),
                "Apoli reads the canonical power and flags without changing the input stack NBT");
        var resaved = restored.writeNbt(new NbtCompound());
        require(NamespaceMigration.stackPowers(resaved) == resaved, "Resaved equipment uses the canonical key");
        var world = player.getServerWorld();
        var stand = new ArmorStandEntity(world, player.getX(), player.getY(), player.getZ());
        world.spawnEntity(stand);
        try {
            var holder = PowerHolderComponent.KEY.get(stand);
            var type = PowerTypeRegistry.get(id);
            var source = new Identifier("apoli:head");
            stand.equipStack(EquipmentSlot.HEAD, restored);
            stand.tick();
            require(holder.hasPower(type, source), "Wearing the restored helmet grants the registered power");
            stand.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            stand.tick();
            require(!holder.hasPower(type, source), "Removing the restored helmet revokes the same grant");
        } finally {
            stand.discard();
        }
    }

    private static NbtCompound power(String type, int data, String source) {
        NbtCompound tag = new NbtCompound();
        tag.putString("Type", type);
        tag.putInt("Data", data);
        NbtList sources = new NbtList();
        sources.add(NbtString.of(source));
        tag.put("Sources", sources);
        return tag;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
