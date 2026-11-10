package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** Actual world damage and elapsed server ticks for the two tutorial regressions. */
public final class SpiderTutorialMechanicsProbe {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static ServerPlayerEntity spider(TestContext context) throws Exception {
        var player = NeoFormCastingProbe.player(context, FormIdentifiers.SPIDER_SALTICIDAE, 0);
        player.setNoGravity(false);
        var powers = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(player);
        for (String id : java.util.List.of("form_spider_salticidae_jump_kill", "form_spider_salticidae_venom", "form_spider_salticidae_venom_fang")) {
            powers.addPower(io.github.apace100.apoli.power.PowerTypeRegistry.get(new net.minecraft.util.Identifier("my_addon", id)),
                    new net.minecraft.util.Identifier("ssc_addon", "spider_tutorial_probe"));
        }
        player.getServerWorld().setBlockState(BlockPos.ofFloored(player.getPos()).down(), Blocks.STONE.getDefaultState());
        JumpKillManager.startCharge(player);
        JumpKillManager.release(player);
        check(JumpKillManager.hasActiveSilk(player), "Grounded release creates a real safety anchor");
        return player;
    }

    private static PigEntity target(ServerPlayerEntity owner, double offset) {
        var target = EntityType.PIG.create(owner.getServerWorld());
        target.setAiDisabled(true);
        target.setNoGravity(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        target.refreshPositionAndAngles(owner.getX() + offset, owner.getY(), owner.getZ() + .6, 0, 0);
        owner.getServerWorld().spawnEntity(target);
        return target;
    }

    private static void finish(ServerPlayerEntity player) throws Exception {
        var method = JumpKillManager.class.getDeclaredMethod("finish", ServerPlayerEntity.class, boolean.class);
        method.setAccessible(true);
        method.invoke(null, player, false);
    }

    private static long expiry(ServerPlayerEntity player) throws Exception {
        var anchors = JumpKillManager.class.getDeclaredField("ANCHORS");
        anchors.setAccessible(true);
        var anchor = ((Map<?, ?>) anchors.get(null)).get(player.getUuid());
        var validUntil = anchor.getClass().getDeclaredField("validUntil");
        validUntil.setAccessible(true);
        return validUntil.getLong(anchor);
    }

    public static void run(TestContext context, Runnable complete) throws Exception {
        var owner = spider(context);
        finish(owner);
        var enemy = target(owner, 0);
        var friend = target(owner, .8);
        AllaySPGroupHeal.addToWhitelist(owner, friend);
        VenomSkillManager.onPress(owner);
        check(!JumpKillManager.hasActiveSilk(owner), "Enhanced venom consumes the safety anchor");
        VenomSkillManager.tick(owner);
        check(Math.abs(enemy.getHealth() - 197) < 1e-5, "NPC collision damage is three before the burst");
        int collisionImmunity = enemy.timeUntilRegen;
        check(collisionImmunity > 10, "The actual collision created vanilla hurt immunity");
        VenomSkillManager.tick(owner);
        check(Math.abs(enemy.getHealth() - 188) < 1e-5, "Burst adds its full nine damage after collision, total twelve");
        check(friend.getHealth() == 200 && friend.getStatusEffects().isEmpty(), "Protected neighbor ignores collision and burst");
        check(enemy.timeUntilRegen >= collisionImmunity, "Burst preserves normal subsequent hurt immunity");
        check(!enemy.damage(enemy.getDamageSources().indirectMagic(owner, owner), 5), "A later ordinary weak hit remains blocked");

        var broken = spider(context);
        JumpKillManager.consumeSilk(broken);
        finish(broken);
        check(!JumpKillManager.hasActiveSilk(broken), "Ending a jump cannot recreate an already broken safety silk");

        var delayed = spider(context);
        long releaseTime = delayed.getWorld().getTime();
        context.waitAndRun(6, () -> {
            try {
                long now = delayed.getWorld().getTime();
                check(now > releaseTime, "Server time actually advances during the flight interval");
                check(expiry(delayed) == releaseTime + 160, "Original in-flight anchor deadline is retained until the jump ends");
                finish(delayed);
                check(expiry(delayed) == now + 160, "The complete eight-second recall window begins when the jump ends");
                check(JumpKillManager.hasActiveSilk(delayed), "Safety silk remains available after landing");
                JumpKillManager.startCharge(delayed);
                check(delayed.hasNoGravity(), "Recall can begin despite the jump failure cooldown");
                JumpKillManager.cancel(delayed);
                check(!JumpKillManager.hasActiveSilk(delayed), "Cancelling recall cleans up its anchor");
                System.out.println("Spider tutorial mechanics checks passed: " + checks);
                complete.run();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }
}
