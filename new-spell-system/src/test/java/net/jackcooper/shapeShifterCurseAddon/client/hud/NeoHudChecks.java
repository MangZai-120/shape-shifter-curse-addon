package net.jackcooper.shapeShifterCurseAddon.client.hud;

import com.google.gson.Gson;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.minecraft.nbt.NbtCompound;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Behavior/resource regressions for independent placement, partial payment and authored art. */
public final class NeoHudChecks {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static NbtCompound state(double c,double s,double q){var n=new NbtCompound();n.putDouble("C",c);n.putDouble("S",s);n.putDouble("Q",q);n.putBoolean("HasSpell",true);n.putInt("Age",-1);return n;}
    public static int run() throws Exception {
        checks=0;
        var n=state(70,80,10);check(NeoStabilityBar.view(n).safe(),"Exact safety equality remains safe");
        n.putDouble("Q",10.00001);check(!NeoStabilityBar.view(n).safe(),"Fractional excess is not rounded away");
        n=state(82,80,25);n.putInt("Age",20);n.putInt("Prep",40);n.putDouble("Paid",12);
        var v=NeoStabilityBar.view(n);check(v.remaining()==13&&v.projected()==95,"Paid burden is not added twice");
        n.putDouble("C",62);check(NeoStabilityBar.view(n).projected()==75,"Relief during casting changes the future burden, not the locked failure draw");
        n.putInt("LockBoundary",18);check(NeoStabilityBar.view(n).phase().equals("locked"),"Actual lock boundary is displayed");
        n=state(83,80,25);n.putBoolean("HasSpell",false);n.putBoolean("RecentFailure",true);v=NeoStabilityBar.view(n);
        check(v.current()==83&&v.projected()==83&&!v.selected()&&v.phase().equals("failed"),"A completed failure retains only the paid current burden");
        n=state(70,200,25);check(NeoStabilityBar.view(n).safe(),"Form safety changes the same spell's fit");
        n=state(95,130,20);n.putDouble("BodyS",80);v=NeoStabilityBar.view(n);check(v.safe()&&v.bodySafety()==80,"Player growth and form base remain distinct");
        int fixed=v.marker();n.putDouble("S",180);check(NeoStabilityBar.view(n).marker()==fixed,"Player levels change the scale rather than the form marker");
        n=state(0,100,0);n.putDouble("BodyS",100);check(NeoStabilityBar.view(n).marker()==54,"Human safety marker is at half of the glass rail");
        n=state(0,200,0);n.putDouble("BodyS",200);check(NeoStabilityBar.view(n).marker()==79,"Familiar safety marker is at eighty percent of the rail");
        n.putDouble("S",350);check(NeoStabilityBar.view(n).marker()==79&&NeoStabilityBar.view(n).edge(700)==96,"Growth keeps the familiar marker fixed and extends danger to 2N");
        n=state(165,80,20);v=NeoStabilityBar.view(n);check(v.overflow()&&v.current()==165&&v.edge(v.current())==96,"Display saturation never caps actual C");
        n=state(0,0,0);check(NeoStabilityBar.view(n).safe()&&NeoStabilityBar.view(n).marker()==12,"Zero safety avoids division by zero");
        n.putDouble("Q",1);check(!NeoStabilityBar.view(n).safe(),"Positive payment at zero safety is unsafe");
        n=state(Double.MAX_VALUE,80,Double.MAX_VALUE);check(Double.isFinite(NeoStabilityBar.view(n).projected()),"Finite extreme inputs keep a saturated visible projection");
        for(int a=1;a<=9;a++)for(int[] size:new int[][]{{320,240},{640,480},{1920,1080}})for(int offset:new int[]{Integer.MIN_VALUE,-500,0,500,Integer.MAX_VALUE}){
            int[] p=NeoStabilityBar.position(a,offset,-offset,size[0],size[1]);check(p[0]>=0&&p[1]>=0&&p[0]+97<=size[0]&&p[1]+24<=size[1],"Clamped independent layout stays on screen");
        }
        Gson gson=new Gson();var cfg=gson.fromJson("{\"showManaBar\":false,\"showSpellbookHud\":false,\"spellbookHudPosOffsetX\":321}",SSCAddonClientConfig.class);
        check(cfg.showNeoStabilityBar&&cfg.showDisintegrationDebugBar,"Old configs receive explicit independent defaults");
        check(cfg.neoStabilityBarPosType==7&&cfg.neoStabilityBarPosOffsetX==16&&cfg.neoStabilityBarPosOffsetY==-88,"Old book layout is not reused by the new bar");
        cfg.showDisintegrationDebugBar=false;cfg.neoStabilityBarPosType=3;cfg.neoStabilityBarPosOffsetX=-130;cfg.neoStabilityBarPosOffsetY=15;
        var restored=gson.fromJson(gson.toJson(cfg),SSCAddonClientConfig.class);
        check(restored.showNeoStabilityBar&&!restored.showDisintegrationDebugBar&&restored.neoStabilityBarPosOffsetX==-130,"Visibility and coordinates survive config roundtrip");
        check(restored.spellbookHudPosOffsetX==321&&!restored.showManaBar&&!restored.showSpellbookHud,"Saving new options leaves old HUD settings untouched");
        BufferedImage author=image("left-safe"),safe=image("frame-safe"),over=image("frame-over"),neutral=image("frame-neutral"),fill=image("fill-full"),mask=image("interior-mask");
        check(author.getWidth()==25&&author.getHeight()==16&&safe.getWidth()==97&&safe.getHeight()==16,"Native sprite dimensions are retained");
        int lamps=0;
        for(int y=0;y<16;y++)for(int x=0;x<97;x++){
            boolean owned=x<25&&(author.getRGB(x,y)>>>24)!=0;
            if(owned)check(author.getRGB(x,y)==safe.getRGB(x,y),"Authored left pixel, including new lamp position, is exact");
            int color=safe.getRGB(x,y),r=(color>>16)&255,g=(color>>8)&255,b=color&255;
            boolean lamp=owned&&b>r+50&&g>r+50;
            if(lamp)lamps++;else check(color==over.getRGB(x,y)&&color==neutral.getRGB(x,y),"Status variants change only the four indicator pixels");
            check(((fill.getRGB(x,y)>>>24)!=0)==((mask.getRGB(x,y)>>>24)!=0),"Fill stays inside authored interior mask");
        }
        check(lamps==4&&(safe.getRGB(4,4)&255)>150&&(safe.getRGB(4,3)&0xFFFFFF)==0,"The user's moved indicator is used");
        System.out.println("Neomagic HUD: "+checks+" geometry, config, payment and authored-resource checks passed.");return checks;
    }
    private static BufferedImage image(String name) throws Exception {try(var in=NeoHudChecks.class.getClassLoader().getResourceAsStream("assets/ssca_neomagic/textures/gui/stability/"+name+".png")){if(in==null)throw new AssertionError("Missing "+name);return ImageIO.read(in);}}
}
