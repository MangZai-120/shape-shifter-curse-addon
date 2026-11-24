package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.integration.PrePowerLoadCallback;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.ResourcePower;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** The ten pips on the fruit bat's seed bar are its fixed capacity. */
public final class SeedEnergyResourcePower extends ResourcePower {
    public static final int CAPACITY = 10;
    private static final Identifier FACTORY_ID = new Identifier("ssc_addon", "seed_energy_resource");

    private SeedEnergyResourcePower(PowerType<?> type, LivingEntity owner, SerializableData.Instance data) {
        super(type, owner, data.get("hud_render"), data.getInt("start_value"),
                Math.min(data.getInt("min"), CAPACITY), CAPACITY, data.get("min_action"), data.get("max_action"));
        currentValue = MathHelper.clamp(currentValue, min, CAPACITY);
    }

    public static void register() {
        var factory = new PowerFactory<SeedEnergyResourcePower>(FACTORY_ID, ResourcePower.createFactory().getSerializableData(),
                data -> (type, owner) -> new SeedEnergyResourcePower(type, owner, data)).allowCondition();
        Registry.register(ApoliRegistries.POWER_FACTORY, FACTORY_ID, factory);
        // Also pin existing packs which define this same resource with apoli:resource.
        PrePowerLoadCallback.EVENT.register((id, json) -> {
            if (FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY.equals(id)) {
                json.addProperty("type", FACTORY_ID.toString());
                json.addProperty("max", CAPACITY);
            }
        });
    }

    @Override
    public void fromTag(NbtElement tag) {
        super.fromTag(tag);
        // Apoli restores the raw integer directly, including old stock above ten.
        currentValue = MathHelper.clamp(currentValue, min, CAPACITY);
    }
}
