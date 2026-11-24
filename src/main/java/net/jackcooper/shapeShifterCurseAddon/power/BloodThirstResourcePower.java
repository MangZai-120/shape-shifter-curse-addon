package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.ResourcePower;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.ability.BatDesmodusBloodThirst;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

/** Blood keeps its original Apoli power ID and integer save/sync format. */
public final class BloodThirstResourcePower extends ResourcePower {
    private BloodThirstResourcePower(PowerType<?> type, LivingEntity owner) {
        super(type, owner, HudRender.DONT_RENDER, 0, 0, BatDesmodusBloodThirst.MAX_BLOOD, null, null);
    }

    public static PowerFactory<BloodThirstResourcePower> createFactory() {
        return new PowerFactory<>(new Identifier("ssc_addon", "blood_thirst_resource"),
                new SerializableData(), data -> BloodThirstResourcePower::new);
    }

    @Override
    public int getMax() {
        return BatDesmodusBloodThirst.maxBlood();
    }

    @Override
    public void fromTag(NbtElement tag) {
        super.fromTag(tag);
        // A client may receive the resource before the matching balance snapshot.
        if (!entity.getWorld().isClient) setValue(getValue());
    }

    public static void clampOnlinePlayers(MinecraftServer server) {
        if (!PowerTypeRegistry.contains(FormIdentifiers.BAT_BLOOD_RESOURCE)) return;
        PowerType<?> type = PowerTypeRegistry.get(FormIdentifiers.BAT_BLOOD_RESOURCE);
        for (var player : server.getPlayerManager().getPlayerList()) {
            if (PowerHolderComponent.KEY.get(player).getPower(type) instanceof BloodThirstResourcePower blood
                    && blood.getValue() > blood.getMax()) {
                blood.setValue(blood.getMax());
                PowerHolderComponent.syncPower(player, type);
            }
        }
    }
}
