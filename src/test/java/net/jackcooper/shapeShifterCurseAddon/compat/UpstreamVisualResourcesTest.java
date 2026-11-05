package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyFootstepSoundSpeedPower;
import net.onixary.shapeShifterCurseFabric.render.form_render.sub_controller.FormEyeBlinkController;

/** Real upstream controller, model UVs and SSCA materials, without a running world or renderer. */
public final class UpstreamVisualResourcesTest {
    private static int checks;
    private static final List<String> FORMS=List.of("form_spider_moon_weaver","form_spider_salticidae");
    private static final int[][] OLD_STATIC_EYES={{7,10},{8,10},{15,10},{16,10},{7,11},{8,11},{15,11},{16,11},{6,13},{7,13},{16,13},{17,13}};
    private static final int[][] NEGATIVE_UVS={{28,23},{26,26},{29,26},{26,27}};
    private static final int[][] POSITIVE_UVS={{32,23},{32,26},{33,26},{33,27}};

    public static void main(String[] args)throws Exception {
        var model=json("/assets/shape-shifter-curse/geo/form/form_spider_3.geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var description=model.getAsJsonObject("description");
        Map<String,JsonObject> bones=new HashMap<>();
        for(var element:model.getAsJsonArray("bones")){var bone=element.getAsJsonObject();bones.put(bone.get("name").getAsString(),bone);}
        check(bones.containsKey("eyeRoot")&&bones.containsKey("eyeRoot2"),"SSC baseline provides both eye groups");
        check(bones.get("eyeRoot2").getAsJsonArray("cubes").size()==2,"Both upper/side eye cubes exist");
        var front=bones.get("eyeRoot").getAsJsonArray("cubes");
        check(front.size()==4&&front.get(3).getAsJsonObject().get("mirror").getAsBoolean(),"Material migration matches the upstream mirrored positive-X front eye");

        for(String form:FORMS){
            var config=json("/assets/my_addon/ssc_form_model/origins.origin.my_addon."+form+".json");
            check(config.get("model").getAsString().equals("shape-shifter-curse:geo/form/form_spider_3.geo.json"),"Spider uses the reviewed SSC model");
            var blink=config.getAsJsonObject("animation_system_config").getAsJsonObject("eye_blink");
            var controller=new FormEyeBlinkController(blink);
            // Reflection gives an explicit baseline failure when an old same-version SSC JAR is supplied.
            var groups=FormEyeBlinkController.class.getField("EYE_ROOT_BONES").get(controller);
            check(groups.equals(List.of("eyeRoot","eyeRoot2")),"Actual SSC controller reads both configured groups");
            check(controller.OPEN_SCALE==1f&&controller.CLOSED_SCALE==.01f,"Existing blink scales remain unchanged");
            var base=image(config.get("texture").getAsString());
            var mask=image(config.get("texture_mask").getAsString());
            var emission=image(config.get("fullbright_texture").getAsString());
            var emissionMask=image(config.get("fullbright_texture_mask").getAsString());
            for(var texture:List.of(base,mask,emission,emissionMask))
                check(texture.getWidth()==description.get("texture_width").getAsInt()
                        &&texture.getHeight()==description.get("texture_height").getAsInt(),"All material dimensions match model UVs");
            for(int[][] side:List.of(NEGATIVE_UVS,POSITIVE_UVS)){
                int[] anchor=side[0];
                check((base.getRGB(anchor[0],anchor[1])>>>24)>0,"Front pupil has visible base color");
                check((emission.getRGB(anchor[0],anchor[1])>>>24)>0,"Front pupil retains its emission alpha");
                for(int[] uv:side)for(var layer:List.of(base,mask,emission,emissionMask))
                    check(layer.getRGB(uv[0],uv[1])==layer.getRGB(anchor[0],anchor[1]),"All moved eyes preserve their physical side's RGBA and mask semantics");
            }
            for(int[] uv:OLD_STATIC_EYES){
                check((emission.getRGB(uv[0],uv[1])>>>24)==0,"Old head-skin eyes no longer emit through closed eye bones");
                int alpha=mask.getRGB(uv[0],uv[1])>>>24;
                check(alpha!=1&&alpha!=2,"Old head-skin pixels no longer use custom pupil channels");
            }
            if(form.endsWith("moon_weaver")){
                check((mask.getRGB(28,23)>>>24)==2&&(mask.getRGB(32,23)>>>24)==1,"Moon-weaver eye A/B channels follow the preserved physical sides after UV swap/mirror");
                check((emission.getRGB(28,23)>>>24)==144,"Moon-weaver original emission alpha remains 144");
            }else{
                // The original salticidae pupils use RGBA(0,0,0,0), not opaque black.
                // SSC ProcessPixel returns the texture color unchanged for this zero mask.
                for(var pupilMask:List.of(mask,emissionMask))
                    check(pupilMask.getRGB(28,23)==0&&pupilMask.getRGB(32,23)==0,"Salticidae preserves its original zero-mask pupil color and emission behavior");
                check((emission.getRGB(28,23)>>>24)==127,"Salticidae original emission alpha remains 127");
            }
        }
        var footsteps=json("/data/my_addon/powers/form_sprint_step_sound_1_5_run_0_9.json");
        check(footsteps.get("speed_multiplier").getAsFloat()==1.5f,"Original walking cadence retained");
        check(footsteps.get("adjust_run_individually").getAsBoolean()&&footsteps.get("run_speed_multiplier").getAsFloat()==.9f,"Original individual running cadence retained");
        float offset=footsteps.get("step_sound_offset").getAsFloat();
        check(offset==.35f,"Use SSC's implemented four-legged phase value");
        var power=new ModifyFootstepSoundSpeedPower(null,null,1.5f,false,.9f,offset);
        float shortGap=power.getStepSoundDistance(null,1,false),longGap=power.getStepSoundDistance(null,1,true);
        check(shortGap>0&&shortGap<longGap,"Real SSC power alternates positive short and long steps");
        check(Math.abs(shortGap+longGap-2f/1.5f)<1e-6,"A complete pair keeps the old total walking distance");
        System.out.println("Upstream visual resources: "+checks+" controller/model/material/cadence checks passed. No world or visible client started.");
    }

    private static JsonObject json(String path)throws Exception{
        try(var input=UpstreamVisualResourcesTest.class.getResourceAsStream(path)){
            if(input==null)throw new AssertionError("Missing actual resource: "+path);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static BufferedImage image(String id)throws Exception{
        String[] parts=id.split(":",2);String path="/assets/"+parts[0]+"/"+parts[1];
        try(var input=UpstreamVisualResourcesTest.class.getResourceAsStream(path)){
            if(input==null)throw new AssertionError("Missing material: "+path);
            var result=ImageIO.read(input);if(result==null)throw new AssertionError("Invalid PNG: "+path);return result;
        }
    }
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
