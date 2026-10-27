package net.ssca.neomagic;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.ssca.neomagic.client.NeoStabilityHud;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Uses the production HUD painter, with explicit fixture states rather than a player's save. */
public final class NeoHudPreviewScreen extends Screen {
    private final List<NbtCompound> examples=List.of(state(20,80,16.2,-1,0),state(70,80,25,-1,0),state(95,80,40,20,0),state(175,80,25,-1,260));
    private final List<int[]> bounds=new ArrayList<>();
    public NeoHudPreviewScreen(){super(Text.literal("稳定度 HUD · 原生渲染预览"));}
    private static NbtCompound state(double c,double safety,double q,int age,int blocked) {
        var n=new NbtCompound();n.putBoolean("Visible",true);n.putDouble("C",c);n.putDouble("S",safety);n.putDouble("Q",q);
        n.putInt("Age",age);n.putInt("Prep",40);n.putInt("Blocked",blocked);n.putInt("Recovery",40);
        double paid=age<0?0:q*age/40;n.putDouble("Paid",paid);double forecast=c+q-paid;n.putDouble("FailureBasis",forecast);
        var b=Blueprint.starter();var profile=new Profile(safety,1.15,.85,1,1,false);
        n.putDouble("Failure",risk(b,forecast,q,profile).failure());n.putDouble("Offset",risk(b,c,q,profile).offset());return n;
    }
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        c.fillGradient(0,0,width,height,0xFF1B2E3B,0xFF0B1420);
        for(int x=0;x<width;x+=24)c.fill(x,0,x+1,height,0x0A97B4BC);
        for(int y=0;y<height;y+=24)c.fill(0,y,width,y+1,0x0A97B4BC);
        c.drawText(textRenderer,title,24,18,0xFFE8D4A5,false);
        c.drawText(textRenderer,Text.literal("青绿为安全空间 · 金线为阈值 · 虚影为本次预计负荷"),24,35,0xFF9FB4BE,false);
        bounds.clear();boolean wide=width>=520;int cardWidth=Math.min(NeoStabilityHud.WIDTH,width-48);
        int start=(width-(wide?cardWidth*2+28:cardWidth))/2;
        if(wide) {
            String[] names={"01  稳定 · 余量清晰","02  临界 · 预计将越线","03  施法 · 实际负担与进度","04  失稳 · 超额继续增长"};
            for(int i=0;i<examples.size();i++) {
                int x=start+i%2*(cardWidth+28),y=77+i/2*144;
                c.drawText(textRenderer,Text.literal(names[i]),x,y-15,0xFFBCCBD1,false);
                NeoStabilityHud.draw(c,textRenderer,examples.get(i),x,y,cardWidth,120);
                bounds.add(new int[]{x,y,cardWidth,NeoStabilityHud.height(examples.get(i))});
            }
            c.drawText(textRenderer,Text.literal("书旁的小条沿用同一月尘金线样式"),start,369,0xFFBCCBD1,false);
            NeoStabilityHud.drawBookGauge(c,textRenderer,examples.get(1),start,391,76,120);
            c.drawText(textRenderer,Text.literal("预览数据为夹具；主面板与书旁小条均调用游戏实际渲染器。"),24,height-26,0xFF6F8C9C,false);
        } else {
            var sample=examples.get(2);int y=65;
            NeoStabilityHud.draw(c,textRenderer,sample,start,y,cardWidth,120);bounds.add(new int[]{start,y,cardWidth,NeoStabilityHud.height(sample)});
            NeoStabilityHud.drawBookGauge(c,textRenderer,sample,start,y+124,76,120);
        }
    }
    public void verify() {
        for(int[] b:bounds)if(b[0]<0||b[1]<0||b[0]+b[2]>width||b[1]+b[3]>height)throw new AssertionError("Stability HUD leaves viewport");
        if(bounds.isEmpty())throw new AssertionError("Stability HUD preview was not rendered");
    }
    @Override public boolean shouldPause(){return false;}
}
