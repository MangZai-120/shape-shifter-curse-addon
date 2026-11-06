package net.ssca.neomagic;

import com.google.gson.JsonObject;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.calio.data.SerializableData;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.StableSscScaleWrites;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.additional_power.CrawlingPower;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyStepHeightPower;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;

/** Runs actual transformed SSC tick methods against Pehkui state in a game world. */
public final class NeoScaleWriteProbe {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void settled(ScaleData data, float desired) {
        data.setScale(desired); data.setScale(desired); data.markForSync(false);
    }
    private static void mutated(ScaleData data, String key, float value) {
        NbtCompound nbt = data.writeNbt(new NbtCompound());
        nbt.putFloat(key, value); data.readNbt(nbt); data.markForSync(false);
    }
    private static final class CustomScaleData extends ScaleData {
        CustomScaleData(ScaleType type, LivingEntity entity) { super(type, entity); }
    }

    public static void run(TestContext context) throws Exception {
        checks = 0;
        var player = NeoFormCastingProbe.player(context, FormIdentifiers.AXOLOTL_SP, 100);
        var step = ScaleTypes.STEP_HEIGHT.getScaleData(player);
        var eye = ScaleTypes.EYE_HEIGHT.getScaleData(player);
        var hitbox = ScaleTypes.HITBOX_HEIGHT.getScaleData(player);
        var stepType = new PowerType<ModifyStepHeightPower>(new Identifier("ssca_neomagic", "probe_step_scale"), null);
        var crawlType = new PowerType<CrawlingPower>(new Identifier("ssca_neomagic", "probe_crawl_scale"), null);
        var condition = new ConditionFactory<LivingEntity>(new Identifier("ssca_neomagic", "probe_sneaking"),
                new SerializableData(), (data, entity) -> entity.isSneaking()).read(new JsonObject());
        var stepPower = new ModifyStepHeightPower(stepType, player, 2, condition, false);
        var crawlPower = new CrawlingPower(crawlType, player, 1, 1, .6f, .35f);
        crawlPower.addCondition(entity -> entity.isSneaking());

        player.setSneaking(false);
        settled(step, 1); step.setPersistence(null); step.markForSync(false);
        stepPower.tick();
        check(step.shouldSync() && Boolean.TRUE.equals(step.getPersistence()), "First native tick still initializes persistence");
        step.markForSync(false);
        for (int i = 0; i < 40; i++) stepPower.tick();
        check(!step.shouldSync(), "Forty stable step ticks do not mark scale for synchronization");

        player.setSneaking(true); stepPower.tick();
        check(step.shouldSync() && step.getBaseScale() == 2 && step.getTargetScale() == 2, "Step condition change still applies and syncs immediately");
        stepPower.tick(); step.markForSync(false);
        for (int i = 0; i < 40; i++) stepPower.tick();
        check(!step.shouldSync(), "Stable active step state becomes quiet after previous scale settles");

        for (String field : new String[]{"scale", "previous", "initial", "target"}) {
            settled(step, 2); mutated(step, field, 1.5f); stepPower.tick();
            check(step.shouldSync(), "Native instant assignment preserves transition repair for " + field);
            check(step.getBaseScale() == 2 && step.getTargetScale() == 2, "Transition repairs keep the native desired scale for " + field);
        }
        settled(step, 2); step.setPersistence(false); step.markForSync(false); stepPower.tick();
        check(step.shouldSync() && Boolean.TRUE.equals(step.getPersistence()), "Persistence changes still synchronize independently of scale");
        check(!StableSscScaleWrites.skipScale(step, Float.NaN) && !StableSscScaleWrites.skipScale(step, Float.POSITIVE_INFINITY), "Non-finite desired values always retain the native setter");
        mutated(step, "previous", Float.NaN);
        check(!StableSscScaleWrites.skipScale(step, 2), "Non-finite transition state always retains the native setter");

        player.setSneaking(false); stepPower.tick();
        check(step.shouldSync() && step.getBaseScale() == 1 && step.getTargetScale() == 1, "Step condition rollback still restores the default");
        step.markForSync(false); stepPower.onRemoved();
        check(step.shouldSync() && step.getBaseScale() == 1, "Native onRemoved remains untouched");
        step.markForSync(false); stepPower.onLost();
        check(step.shouldSync(), "Native onLost remains untouched");

        settled(eye, 1); settled(hitbox, 1);
        for (int i = 0; i < 40; i++) crawlPower.tick();
        check(!eye.shouldSync() && !hitbox.shouldSync(), "Forty stable non-crawling ticks send no repeated eye/hitbox state");
        player.setSneaking(true); crawlPower.tick();
        check(eye.shouldSync() && hitbox.shouldSync() && eye.getBaseScale() == .35f && hitbox.getBaseScale() == .6f, "Entering crawl still changes both real scale types");
        crawlPower.tick(); eye.markForSync(false); hitbox.markForSync(false);
        for (int i = 0; i < 40; i++) crawlPower.tick();
        check(!eye.shouldSync() && !hitbox.shouldSync(), "Stable crawling does not mark eye/hitbox for sync");
        eye.setTargetScale(.9f); eye.markForSync(false); crawlPower.tick();
        check(eye.shouldSync() && eye.getTargetScale() == .35f, "Crawl still overrides an externally changed animated target");
        player.setSneaking(false); crawlPower.tick();
        check(eye.shouldSync() && hitbox.shouldSync() && eye.getBaseScale() == 1 && hitbox.getBaseScale() == 1, "Leaving crawl still restores both scales");

        var custom = new CustomScaleData(ScaleTypes.STEP_HEIGHT, player);
        settled(custom, 1); custom.setPersistence(true); custom.markForSync(false);
        check(!StableSscScaleWrites.skipScale(custom, 1), "A ScaleData subclass retains stable scale setter callbacks");
        check(!StableSscScaleWrites.skipPersistence(custom, true), "A ScaleData subclass retains persistence setter callbacks");
        System.out.println("NeoScaleWriteProbe: " + checks + " checks passed");
    }
}
