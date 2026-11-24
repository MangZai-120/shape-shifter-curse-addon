package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.ResourcePower;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** The beacon keeps its integer resource and save format. Only a raised flag can need repair. */
public final class BeaconActivePower extends ResourcePower {
    public static final Identifier FACTORY_ID = new Identifier("ssc_addon", "beacon_active");

    private BeaconActivePower(PowerType<?> type, LivingEntity owner) {
        super(type, owner, HudRender.DONT_RENDER, 0, 0, 1, null, null);
    }

    public static PowerFactory<BeaconActivePower> createFactory() {
        return new PowerFactory<>(FACTORY_ID, new SerializableData(), data -> BeaconActivePower::new);
    }

    @Override
    public int setValue(int value) {
        int result = super.setValue(value);
        notifyIfRaised(result);
        return result;
    }

    @Override
    public void fromTag(NbtElement tag) {
        super.fromTag(tag);
        notifyIfRaised(getValue());
    }

    private void notifyIfRaised(int value) {
        if (value != 0 && entity instanceof ServerPlayerEntity player) AllaySPPortableBeacon.flagRaised(player);
    }
}
