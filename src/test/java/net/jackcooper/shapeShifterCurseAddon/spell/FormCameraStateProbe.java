package net.jackcooper.shapeShifterCurseAddon.spell;

import io.github.apace100.apoli.component.PowerHolderComponent;
import java.nio.file.*;
import java.util.*;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.power.MistFormAbilityPower;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import virtuoel.pehkui.api.ScaleTypes;

/** Real power lifecycle and entity dimensions, including overrides replaced by a new form. */
public final class FormCameraStateProbe {
    private static int checks;
    private static void close(double actual,double expected,String why) {
        checks++;if(Math.abs(actual-expected)>0.0001)throw new AssertionError(why+": "+actual+" != "+expected);
    }
    private static void form(ServerPlayerEntity p, net.minecraft.util.Identifier id) {
        p.clearStatusEffects();p.setSneaking(false);p.setPose(EntityPose.STANDING);
        FormUtils._setForm(p,RegPlayerForms.getPlayerForm(id));
    }
    private static MistFormAbilityPower mist(ServerPlayerEntity p) {
        return PowerHolderComponent.getPowers(p,MistFormAbilityPower.class).stream().findFirst().orElseThrow();
    }
    private static void active(ServerPlayerEntity p,MistFormAbilityPower power) {
        p.addStatusEffect(new StatusEffectInstance(SscAddon.MIST_FORM,100));power.tick();p.calculateDimensions();
    }
    private static void audit(ServerPlayerEntity p) {
        if(io.github.apace100.apoli.Apoli.config==null)io.github.apace100.apoli.Apoli.config=new io.github.apace100.apoli.util.ApoliConfig();
        for(float size:new float[]{1,0.5f,0.25f,1.5f}) {
            ScaleTypes.BASE.getScaleData(p).setScale(size);
            form(p,FormIdentifiers.BAT_DESMODUS);
            var power=mist(p);
            for(int tick=0;tick<40;tick++)power.tick();
            close(ScaleTypes.EYE_HEIGHT.getScaleData(p).getBaseScale(),0.7,"idle power preserves bat eyes");
            close(ScaleTypes.HITBOX_HEIGHT.getScaleData(p).getBaseScale(),0.7,"idle power preserves bat hitbox");
            close(p.getStandingEyeHeight(),1.055625*0.6*size,"calibrated bat eyes");
            active(p,power);
            close(p.getWidth(),0.2*size,"mist width");close(p.getHeight(),0.1*size,"mist height");
            close(p.getStandingEyeHeight(),0.08*size,"mist eye follows external scale");
            p.removeStatusEffect(SscAddon.MIST_FORM);power.tick();p.calculateDimensions();
            close(p.getStandingEyeHeight(),1.055625*0.6*size,"mist expiry restores bat eyes");
            close(p.getHeight(),1.8*0.6*0.7*size,"mist expiry restores bat hitbox");
            if(!Boolean.TRUE.equals(ScaleTypes.EYE_HEIGHT.getScaleData(p).getPersistence()))throw new AssertionError("restore persistence");
            active(p,power);p.removeStatusEffect(SscAddon.MIST_FORM);
            p.addStatusEffect(new StatusEffectInstance(SscAddon.PURIFIED,40));power.tick();p.calculateDimensions();
            close(p.getStandingEyeHeight(),1.055625*0.6*size,"purification after effect removal restores eyes");
            p.removeStatusEffect(SscAddon.PURIFIED);
            active(p,power);
            // New form applies its scales before the old power is removed.
            FormUtils._setForm(p,RegPlayerForms.getPlayerForm(FormIdentifiers.AXOLOTL_FLUORESCENT));
            p.calculateDimensions();
            close(ScaleTypes.EYE_HEIGHT.getScaleData(p).getBaseScale(),1,"old mist must not overwrite new form eyes");
            close(p.getWidth(),0.6*0.75*size,"form change removes mist width override");
            close(p.getStandingEyeHeight(),1.524375*0.75*size,"small humanoid after mist");
            form(p,FormIdentifiers.BAT_DESMODUS);power=mist(p);active(p,power);power.onLost();p.calculateDimensions();
            close(p.getStandingEyeHeight(),1.055625*0.6*size,"lost power cleans up active mist");
            form(p,FormIdentifiers.AXOLOTL_ALING);
            var crawls=PowerHolderComponent.KEY.get(p).getPowers().stream()
                    .filter(net.onixary.shapeShifterCurseFabric.additional_power.CrawlingPower.class::isInstance)
                    .map(net.onixary.shapeShifterCurseFabric.additional_power.CrawlingPower.class::cast).toList();
            if(crawls.isEmpty())throw new AssertionError("missing real crawling power");
            p.setSneaking(true);p.setPose(EntityPose.CROUCHING);crawls.forEach(c -> c.tick());p.calculateDimensions();
            close(p.getStandingEyeHeight(),0.419211*0.75*size,"small axolotl crawl");
            p.setSneaking(false);p.setPose(EntityPose.STANDING);crawls.forEach(c -> c.tick());p.calculateDimensions();
            close(p.getStandingEyeHeight(),1.524375*0.75*size,"leaving crawl preserves small humanoid eyes");
            form(p,new net.minecraft.util.Identifier("shape-shifter-curse","original_before_enable"));
            close(p.getStandingEyeHeight(),1.62*size,"human restoration remains vanilla");
        }
    }
    public static void start(TestContext context) {
        context.waitAndRun(50,()->{try {
            audit(context.createMockCreativeServerPlayerInWorld());
            Files.writeString(Path.of("camera-state-result.json"),new com.google.gson.Gson().toJson(Map.of("passed",true,"checks",checks)));
            System.out.println("CAMERA_STATE_PASS checks="+checks);context.complete();
        }catch(Throwable e){e.printStackTrace();context.throwGameTestException(e.toString());}});
    }
}
