package net.ssca.neomagic;

import java.nio.file.*;
import java.util.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.*;
import virtuoel.pehkui.api.ScaleTypes;

/** 保留真实注册的 Java 兜底形态，避免数据包重载覆盖后漏测注册函数。 */
public final class NeoFormScaleProbe {
    private record Case(IForm form,float size) {}
    private static final List<Case> forms=new ArrayList<>();
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void capture(){
        var values=new LinkedHashMap<Identifier,Float>();
        values.put(FormIdentifiers.FAMILIAR_FOX_SP,.6f);values.put(FormIdentifiers.UPGRADE_FAMILIAR_FOX,.55f);
        values.put(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA,.55f);values.put(FormIdentifiers.FAMILIAR_FOX_RED,.65f);
        values.put(FormIdentifiers.SNOW_FOX_SP,.55f);values.put(FormIdentifiers.SNOW_FOX_FROSTSPINE,.55f);
        values.put(FormIdentifiers.WILD_CAT_SP,.55f);values.put(FormIdentifiers.WILD_CAT_NIGHTMARE,.55f);
        values.put(FormIdentifiers.OCELOT_SP,.75f);values.put(FormIdentifiers.OCELOT_NOVA,.75f);
        values.put(FormIdentifiers.ANUBIS_WOLF_SP,.8f);values.put(FormIdentifiers.GOLDEN_SANDSTORM_SP,.8f);
        values.forEach((id,size)->{
            var form=RegPlayerForms.getPlayerForm(id);
            check(form instanceof NormalForm,"形态在数据包重载前已通过Java注册："+id);
            forms.add(new Case(form,size));
        });
    }
    public static void start(TestContext t){
        t.waitAndRun(65,()->{try{
            var p=t.createMockCreativeServerPlayerInWorld();
            for(var sample:forms){
                var form=sample.form;form.applyScale(p);
                String label=form.getFormID().toString();
                check(form.getBodyType()==PlayerFormBodyType.FERAL,"四足体型保留："+label);
                check(Math.abs(ScaleTypes.WIDTH.getScaleData(p).getBaseScale()-sample.size)<.0001,"宽度缩放保留："+label);
                check(Math.abs(ScaleTypes.HEIGHT.getScaleData(p).getBaseScale()-sample.size)<.0001,"高度缩放保留："+label);
                check(Math.abs(ScaleTypes.EYE_HEIGHT.getScaleData(p).getBaseScale()-.6f)<.0001,"眼高缩放保留："+label);
                check(Math.abs(ScaleTypes.HITBOX_HEIGHT.getScaleData(p).getBaseScale()-.6f)<.0001,"碰撞箱高度保留："+label);
                check(Math.abs(form.getEyeScale()-.6f)<.0001,"新接口基础眼高保留："+label);
            }
            Files.writeString(Path.of("form-scale-result.json"),NeoState.JSON.toJson(Map.of("checks",checks,"forms",forms.size(),"real_server_world",true)));
            System.out.println("NEO_FORM_SCALE_PASS forms="+forms.size()+" checks="+checks);t.complete();
        }catch(Throwable e){e.printStackTrace();t.throwGameTestException(e.toString());}});
    }
}
