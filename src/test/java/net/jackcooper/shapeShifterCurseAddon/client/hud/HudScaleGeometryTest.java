package net.jackcooper.shapeShifterCurseAddon.client.hud;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import static net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaleGeometry.*;
public final class HudScaleGeometryTest {
    private static int checks;
    public static void main(String[] args) {
        for(int percent:new int[]{-100,0,49,50,75,100,125,150,175,200,201,99999}) {
            for(int[] screen:new int[][]{{320,240},{640,480},{1280,720}}) {
                for(int[] shape:new int[][]{{80,9},{92,9},{33,68},{128,50},{14,68},{52,24},{97,24}}) {
                    for(int[] point:new int[][]{{-500,-500},{0,0},{30,40},{screen[0]-10,screen[1]-10},{9999,9999}}) {
                        for(boolean right:new boolean[]{false,true}) {
                            Box source=new Box(point[0],point[1],shape[0],shape[1]);
                            View v=place(source,percent,screen[0],screen[1],right);Box b=v.bounds();
                            check(b.x()>=0 && b.y()>=0 && b.x()+b.width()<=screen[0] && b.y()+b.height()<=screen[1],"viewport containment");
                            check(Math.abs(b.width()-source.width()*v.scale())<1.01 && Math.abs(b.height()-source.height()*v.scale())<1.01,"one uniform scale with only raster rounding");
                            check(b.contains(b.x()+b.width()/2.0,b.y()+b.height()/2.0,0),"displayed center is clickable");
                            check(!b.contains(b.x()-3,b.y()-3,2),"blank space is not selectable");
                        }
                    }
                }
            }
        }
        View half=place(new Box(100,100,80,10),50,640,480,false);
        check(half.bounds().width()==40 && half.bounds().height()==5 && half.scale()==.5f,"exact half scale");
        View twice=place(new Box(600,100,40,20),200,640,480,true);
        check(twice.bounds().x()==560 && twice.bounds().width()==80 && twice.bounds().height()==40,"right edge stays pinned on zoom");
        var cfg=new SSCAddonClientConfig();
        for(int i=0;i<HudScaling.KEYS.length;i++)check(HudScaling.get(cfg,i)==100,"old configs retain existing size");
        cfg.hudScalePercent.put("future",137);HudScaling.set(cfg,0,150);HudScaling.set(cfg,1,50);
        check(HudScaling.get(cfg,0)==150 && HudScaling.get(cfg,1)==50 && HudScaling.get(cfg,2)==100,"independent HUD values");
        check(cfg.hudScalePercent.get("future")==137,"unknown future settings survive");
        cfg.hudScalePercent=null;check(HudScaling.get(cfg,0)==100,"null legacy map");
        HudScaling.set(cfg,0,999);check(HudScaling.get(cfg,0)==200,"out of range values clamp safely");
        String[] visibilityFields={"showInstinctBar","showManaBar","showCdBar","showSpellbookHud","showChargeBar","showSscMagicHud","showNeoStabilityBar","showSscItemStoreHud"};
        try {
            for(int section=0;section<HudScaling.KEYS.length;section++){
                var field=SSCAddonClientConfig.class.getField(visibilityFields[section]);
                check(HudScaling.isVisible(cfg,section),"legacy visibility remains on by default");
                field.setBoolean(cfg,false);
                for(int other=0;other<HudScaling.KEYS.length;other++)
                    check(HudScaling.isVisible(cfg,other)==(other!=section),"one hidden HUD does not hide another");
                field.setBoolean(cfg,true);
            }
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        System.out.println("HUD scaling: "+checks+" geometry, independent settings and old-config checks passed.");
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}
