package net.jackcooper.shapeShifterCurseAddon.event;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.effect.SheepFormEffect;
import net.jackcooper.shapeShifterCurseAddon.effect.SheepFormRestoreHandler;
import net.jackcooper.shapeShifterCurseAddon.effect.StunEffect;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Real loaded powers, server mining/attack and recovery ticks in a disposable GameTest world. */
public final class InteractionRecoveryWorldProbe {
    private static int checks;
    private static final UUID OTHER_ATTACK = UUID.fromString("b28f1609-eed2-4d09-af69-70852731f444");

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .00001, message + ": " + actual + " != " + expected);
    }

    private static ServerPlayerEntity player(TestContext test, Identifier form) {
        var player = test.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setNoGravity(true);
        var point = test.getAbsolutePos(new BlockPos(3, 3, 3)).toCenterPos();
        player.refreshPositionAndAngles(point.x, point.y, point.z, 0, 0);
        FormUtils._setForm(player, RegPlayerForms.getPlayerForm(form));
        var online = player.getServer().getPlayerManager().getPlayerList();
        if (!online.contains(player)) online.add(player);
        return player;
    }

    private static double breakingRatio(ServerPlayerEntity player, BlockState state, BlockPos pos) {
        player.getWorld().setBlockState(pos, state); // Apoli 的方块条件读取世界里的实际方块。
        player.setOnGround(true);
        double vanilla = player.getBlockBreakingSpeed(state) / state.getHardness(player.getWorld(), pos)
                / (player.canHarvest(state) ? 30 : 100);
        return state.calcBlockBreakingDelta(player, player.getWorld(), pos) / vanilla;
    }

    private static void mining(TestContext test, Identifier form) {
        var player = player(test, form);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        for (String power : List.of("form_allay_sp_digging_speed_down", "form_allay_sp_prevent_digging_when_fly")) {
            check(PowerHolderComponent.KEY.get(player).hasPower(PowerTypeRegistry.get(new Identifier("my_addon", power))),
                    form + " grants the actual mining power " + power);
        }
        BlockPos pos = player.getBlockPos().add(1, 0, 0);
        player.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = false;
        near(breakingRatio(player, Blocks.STONE.getDefaultState(), pos), .55, form + " grounded hard blocks retain 45% penalty");
        near(breakingRatio(player, Blocks.DIRT.getDefaultState(), pos), 1, form + " grounded soft blocks retain full speed");
        player.getAbilities().flying = true;
        near(breakingRatio(player, Blocks.STONE.getDefaultState(), pos), .11, form + " flying hard blocks use positive 11% speed");
        near(breakingRatio(player, Blocks.DIRT.getDefaultState(), pos), .2, form + " flying soft blocks retain 80% penalty");

        check(AttackBlockCallback.EVENT.invoker().interact(player, player.getWorld(), Hand.MAIN_HAND, pos, Direction.UP)
                == ActionResult.PASS, form + " mining callback allows the attack");
        player.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
        player.interactionManager.processBlockBreakingAction(pos, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                Direction.UP, player.getWorld().getTopY(), 0);
        for (int i = 0; i < 160; i++) {
            player.setOnGround(true);
            player.interactionManager.update();
        }
        player.interactionManager.processBlockBreakingAction(pos, PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                Direction.UP, player.getWorld().getTopY(), 1);
        check(player.getWorld().getBlockState(pos).isAir(), form + " actually finishes mining stone while flying");
        player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(7);
        attack(test, player, form + " still deals melee damage");
    }

    private static void attack(TestContext test, ServerPlayerEntity player, String message) {
        var target = EntityType.PIG.create(test.getWorld());
        target.setAiDisabled(true);
        target.setNoGravity(true);
        target.refreshPositionAndAngles(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
        check(test.getWorld().spawnEntity(target), "Attack target spawns");
        float before = target.getHealth();
        check(AttackEntityCallback.EVENT.invoker().interact(player, player.getWorld(), Hand.MAIN_HAND, target, null)
                == ActionResult.PASS, "Normal server attack callback passes");
        player.attack(target);
        check(target.getHealth() < before, message);
        target.discard();
    }

    private static LivingEntity sheep(TestContext test, int offset) {
        var target = EntityType.ZOMBIE.create(test.getWorld());
        target.setAiDisabled(true);
        target.setNoGravity(true);
        target.setInvulnerable(true); // 日照等环境伤害不参与恢复血量的断言。
        var point = test.getAbsolutePos(new BlockPos(5 + offset, 3, 5)).toCenterPos();
        target.refreshPositionAndAngles(point.x, point.y, point.z, 0, 0);
        test.getWorld().spawnEntity(target);
        target.setHealth(15);
        target.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).addTemporaryModifier(
                new EntityAttributeModifier(OTHER_ATTACK, "Unrelated test modifier", .25,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        check(target.addStatusEffect(new StatusEffectInstance(SscAddon.SHEEP_FORM, 100)), "Sheep effect applies");
        near(target.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 0, "Active sheep cannot deal attack damage");
        near(target.getMaxHealth(), 16, "Sheep health cap still applies");
        near(target.getHealth(), 12, "Sheep conversion retains health percentage");
        return target;
    }

    private static void restored(LivingEntity target, String label) {
        var damage = target.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        check(damage.getModifier(SheepFormEffect.ATTACK_MODIFIER_UUID) == null, label + " removes zero-damage modifier");
        check(damage.getModifier(OTHER_ATTACK) != null, label + " preserves unrelated attack modifier");
        near(damage.getValue(), 3.75, label + " restores actual attack damage");
        near(target.getMaxHealth(), 20, label + " restores max health");
        near(target.getHealth(), 15, label + " preserves current health percentage");
    }

    private static void orphan(ServerPlayerEntity player) {
        player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(7);
        player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).addTemporaryModifier(
                new EntityAttributeModifier(SheepFormEffect.ATTACK_MODIFIER_UUID, "Orphan sheep modifier", -1,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            mining(test, FormIdentifiers.ALLAY_SP);
            mining(test, FormIdentifiers.FALLEN_ALLAY_SP);
            var normal = sheep(test, 0);
            normal.removeStatusEffect(SscAddon.SHEEP_FORM);
            restored(normal, "Normal removal");
            SheepFormRestoreHandler.onSheepEnd(normal);
            restored(normal, "Repeated cleanup");

            var first = sheep(test, 1);
            var second = sheep(test, 2);
            // Fault injection: emulate another mod removing effects without the removal callback.
            first.getActiveStatusEffects().remove(SscAddon.SHEEP_FORM);
            second.getActiveStatusEffects().remove(SscAddon.SHEEP_FORM);
            var active = sheep(test, 3);
            SheepFormRestoreHandler.clearOrphanedAttackModifier(active);
            near(active.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 0, "Orphan guard preserves active sheep restriction");

            var player = player(test, RegPlayerForms.ORIGINAL_BEFORE_ENABLE.getFormID());
            orphan(player); // No health/form snapshot exists, as with a pre-existing orphan on login.
            var stunned = player(test, RegPlayerForms.ORIGINAL_BEFORE_ENABLE.getFormID());
            stunned.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, 100));
            orphan(stunned);

            test.waitAndRun(12, () -> {
                restored(first, "First abnormal removal");
                restored(second, "Second abnormal removal in the same sweep");
                check(active.hasStatusEffect(SscAddon.SHEEP_FORM), "Control case is still sheep");
                near(active.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE), 0, "Sweep preserves active sheep restriction");
                var damage = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                check(damage.getModifier(SheepFormEffect.ATTACK_MODIFIER_UUID) == null, "Online tick removes orphan without snapshot");
                near(damage.getValue(), 7, "Online tick restores player attack damage");
                attack(test, player, "Recovered player actually damages a target");
                var controlled = stunned.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                check(controlled.getModifier(SheepFormEffect.ATTACK_MODIFIER_UUID) == null, "Active STUN does not skip orphan sheep cleanup");
                check(controlled.getModifier(StunEffect.ATTACK_MODIFIER_UUID) != null && stunned.hasStatusEffect(SscAddon.STUN),
                        "Orphan cleanup preserves active STUN");
                near(controlled.getValue(), 0, "Active STUN still blocks attack damage");
                stunned.removeStatusEffect(SscAddon.STUN);
                near(controlled.getValue(), 7, "STUN removal restores damage normally");
                active.removeStatusEffect(SscAddon.SHEEP_FORM);
                restored(active, "Active control later expires normally");
                System.out.println("Interaction recovery GameTest: " + checks + " checks passed in a real ServerWorld.");
                test.complete();
            });
        });
    }
}
