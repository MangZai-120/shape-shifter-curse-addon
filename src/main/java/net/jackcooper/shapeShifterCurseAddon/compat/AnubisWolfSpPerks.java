package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.ability.AnubisWolfSpSummonWolves;
import net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.additional_power.WolfMinionPower;
import net.onixary.shapeShifterCurseFabric.minion.IPlayerEntityMinion;
import net.onixary.shapeShifterCurseFabric.minion.MinionRegister;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Original wolf purchases, with SP-only summoning rules and a passive extra slot. */
public final class AnubisWolfSpPerks {
    public static final Identifier SUMMONING_1 = addon("perk_anubis_wolf_sp_summoning_1");
    public static final Identifier SUMMONING_2 = addon("perk_anubis_wolf_sp_summoning_2");
    public static final Identifier SOUL_EXCITATION = addon("perk_anubis_wolf_sp_soul_excitation");
    public static final Identifier SOUL_RECALL = addon("perk_anubis_wolf_sp_soul_recall");

    private AnubisWolfSpPerks() { }

    public static boolean isSupported(IForm form) {
        return form != null && FormIdentifiers.ANUBIS_WOLF_SP.equals(form.getFormID());
    }

    public static Identifier effectivePower(Identifier power) {
        if (original("perks/anubis_wolf_summoning_1").equals(power)) return SUMMONING_1;
        if (original("perks/anubis_wolf_summoning_2").equals(power)) return SUMMONING_2;
        if (original("perks/anubis_wolf_soul_excitation").equals(power)) return SOUL_EXCITATION;
        if (original("perks/anubis_wolf_soul_recall").equals(power)) return SOUL_RECALL;
        return power;
    }

    public static boolean loadMapped(NormalPerk perk, PlayerEntity player, IForm form) {
        if (!isSupported(form) || !RegPerks.getPerkTree(RegPerks.T_AnubisWolf3PerkTree).getAllPerks().contains(perk.getID())) return false;
        var purchased = RegPlayerFormComponent.PLAYER_FORM.get(player).formPerkMap.get(RegPerks.T_AnubisWolf3PerkTree);
        boolean response2 = purchased != null && purchased.contains(RegPerks.P_AnubisWolfPackResponse_2);
        if (RegPerks.P_AnubisWolfWitherTolerance_1.equals(perk.getID()) && purchased != null
                && purchased.contains(RegPerks.P_AnubisWolfWitherTolerance_2)) return true;
        Identifier source = form.getFormLayer().getRight();
        for (Identifier power : perk.powerRemove) FormUtils.removePower(player, effectivePower(power), source);
        for (Identifier power : perk.powerAdd) {
            Identifier mapped = effectivePower(power);
            // Rank I still supplies death healing after rank II replaces only its summon marker.
            if (!SUMMONING_1.equals(mapped) || !response2) FormUtils.applyPower(player, mapped, source);
        }
        return true;
    }

    public static boolean hasExtraCapacity(PlayerEntity player) {
        return player != null && isSupported(FormUtils.getPlayerForm(player)) && has(player, SOUL_EXCITATION);
    }

    public static int capacity(PlayerEntity player, int base) {
        return base + (hasExtraCapacity(player) ? 1 : 0);
    }

    public record PassiveSummon(int count, int maximum, int cooldown) { }

    public static PassiveSummon passiveSummon(int rank, boolean crystal, boolean extraCapacity, boolean whenHit) {
        int accessory = crystal ? 1 : 0;
        int count = (!whenHit && rank >= 2 ? 3 : 2) + accessory;
        int maximum = (rank >= 2 ? 3 : 2) + accessory + (extraCapacity ? 1 : 0);
        return new PassiveSummon(count, maximum, rank > 0 ? 420 : 600);
    }

    public static void summonPassive(Pair<Entity, Entity> pair, boolean reverse) {
        Entity owner = reverse ? pair.getRight() : pair.getLeft();
        Entity nearby = reverse ? pair.getLeft() : pair.getRight();
        if (!(owner instanceof ServerPlayerEntity player) || !(nearby.getWorld() instanceof ServerWorld world)
                || !isSupported(FormUtils.getPlayerForm(player)) || !(player instanceof IPlayerEntityMinion minions)) return;
        int rank = has(player, SUMMONING_2) ? 2 : has(player, SUMMONING_1) ? 1 : 0;
        PassiveSummon spec = passiveSummon(rank, AnubisWolfSpSummonWolves.hasTrinketEquipped(player),
                hasExtraCapacity(player), reverse);
        if (minions.shape_shifter_curse$getMinionsCount(AnubisWolfMinionEntity.MinionID) >= spec.maximum()
                || MinionRegister.IsInCoolDown(AnubisWolfMinionEntity.MinionID, player, spec.cooldown())) return;
        boolean summoned = false;
        for (int i = 0; i < spec.count()
                && minions.shape_shifter_curse$getMinionsCount(AnubisWolfMinionEntity.MinionID) < spec.maximum(); i++) {
            var wolf = MinionRegister.SpawnMinion(MinionRegister.ANUBIS_WOLF_MINION, world,
                    AnubisWolfSpSummonWolves.summonPosition(world, player, nearby), player);
            if (wolf == null) continue;
            wolf.setMinionLevel(3);
            WolfMinionPower.onSpawn(player, wolf);
            summoned = true;
        }
        if (summoned) {
            MinionRegister.SetCoolDown(AnubisWolfMinionEntity.MinionID, player);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WOLF_GROWL, player.getSoundCategory(), 1.0F, 1.5F);
            world.spawnParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true,
                    player.getX(), player.getY() + 0.5, player.getZ(), 8, 0, 0, 0, 0);
        }
    }

    public static void recall(ServerPlayerEntity player) {
        if (!isSupported(FormUtils.getPlayerForm(player)) || !has(player, SOUL_RECALL)
                || AbilityUseGuard.blocked(player)) return;
        AnubisWolfSpSummonWolves.recallAll(player);
    }

    private static boolean has(PlayerEntity player, Identifier id) {
        if (!PowerTypeRegistry.contains(id)) return false;
        var power = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(id));
        return power != null && power.isActive();
    }

    private static Identifier addon(String path) { return new Identifier("my_addon", path); }
    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", path); }
}
