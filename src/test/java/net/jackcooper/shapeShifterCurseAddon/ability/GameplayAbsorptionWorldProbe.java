package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;

/** Real entity NBT and effect ticks in the isolated Fabric server, including the installed Mixins. */
public final class GameplayAbsorptionWorldProbe {
    private static int checks;
    private GameplayAbsorptionWorldProbe() { }

    public static void run(ServerWorld world) {
        PigEntity original = pig(world);
        original.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2000, 0));
        check(original.getAbsorptionAmount() == 4, "vanilla source granted four absorption HP");
        ParasiticAbsorptionManager.addAbsorption(original, 1);
        check(original.getAbsorptionAmount() == 8 && state(original).remaining() == 4,
                "real non-player target granted and tracked parasitic absorption");
        NbtCompound saved = original.writeNbt(new NbtCompound());
        check(saved.getFloat("SSCA.ParasiticAbsorptionRemaining") == 4
                        && saved.getLong("SSCA.ParasiticAbsorptionEndTick") == world.getTime() + 300,
                "real entity NBT includes owned budget and absolute expiry");
        PigEntity loaded = pig(world);
        loaded.readNbt(saved);
        check(loaded.getAbsorptionAmount() == 8 && state(loaded).remaining() == 4
                        && loaded.hasStatusEffect(SscAddon.BAT_ABSORPTION),
                "real NBT reload restores absorption, owned budget and status effect");

        // Simulate unloaded time by restoring the absolute expiry in the past while the
        // saved effect still has its full duration: the next real tick must expire it.
        NbtCompound elapsed = saved.copy();
        elapsed.putLong("SSCA.ParasiticAbsorptionEndTick", world.getTime() - 1);
        loaded.readNbt(elapsed);
        loaded.tick();
        check(loaded.getAbsorptionAmount() == 4 && state(loaded).remaining() == 0
                        && !loaded.hasStatusEffect(SscAddon.BAT_ABSORPTION)
                        && loaded.hasStatusEffect(StatusEffects.ABSORPTION),
                "elapsed unloaded time expires through the real entity tick and preserves vanilla absorption");

        PigEntity effectExpiry = pig(world);
        effectExpiry.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2000, 0));
        ParasiticAbsorptionManager.addAbsorption(effectExpiry, 1);
        effectExpiry.removeStatusEffect(SscAddon.BAT_ABSORPTION);
        effectExpiry.addStatusEffect(new StatusEffectInstance(SscAddon.BAT_ABSORPTION, 1, 1));
        effectExpiry.tick();
        check(!effectExpiry.hasStatusEffect(SscAddon.BAT_ABSORPTION)
                        && effectExpiry.getAbsorptionAmount() == 4 && state(effectExpiry).remaining() == 0,
                "natural real status-effect expiry removes only the owned parasitic budget");

        PigEntity vanillaExpiry = pig(world);
        vanillaExpiry.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1, 0));
        ParasiticAbsorptionManager.addAbsorption(vanillaExpiry, 1);
        vanillaExpiry.tick();
        check(!vanillaExpiry.hasStatusEffect(StatusEffects.ABSORPTION)
                        && vanillaExpiry.getAbsorptionAmount() == 4 && state(vanillaExpiry).remaining() == 4,
                "real vanilla absorption expiration preserves the parasitic source via its Mixin");
        vanillaExpiry.removeStatusEffect(SscAddon.BAT_ABSORPTION);
        vanillaExpiry.tick();
        check(vanillaExpiry.getAbsorptionAmount() == 0 && state(vanillaExpiry).remaining() == 0,
                "removing the remaining effect leaves no orphan absorption");
        original.discard();
        loaded.discard();
        effectExpiry.discard();
        vanillaExpiry.discard();
        System.out.println("Gameplay absorption world regressions: " + checks + " checks passed.");
    }

    private static PigEntity pig(ServerWorld world) {
        PigEntity pig = java.util.Objects.requireNonNull(EntityType.PIG.create(world));
        pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(64.5, 90, 64.5, 0, 0);
        check(pig instanceof ParasiticAbsorptionAccess, "LivingEntity parasitic Mixin is installed");
        return pig;
    }

    private static ParasiticAbsorptionState state(PigEntity pig) {
        return ((ParasiticAbsorptionAccess) pig).ssca$parasiticAbsorption();
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
