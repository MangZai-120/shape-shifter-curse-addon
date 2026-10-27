package net.ssca.neomagic.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.ssca.neomagic.NeoMagic;
import java.util.Locale;

/** Pixel geometry stays crisp at Minecraft GUI scales; the server remains the source of all values. */
public final class NeoStabilityHud {
    public static final int WIDTH=236, BASE_HEIGHT=88, STRIP_HEIGHT=20;
    private static final int GOLD=0xFFD1B778, PALE=0xFFEDE2C8, MUTED=0xFF9AAEB9;
    private static final int JADE=0xFF8CD6C0, AMBER=0xFFF0BE80, ROSE=0xFFED8D9C;
    private NeoStabilityHud(){}
    public static int height(NbtCompound state){return BASE_HEIGHT+(state.getInt("Age")>=0||state.getInt("Blocked")>0||state.getBoolean("Stored")?STRIP_HEIGHT:0);}
    private static Text t(String key,Object... args){return NeoMagic.text("stability."+key,args);}
    private static double positive(double value){return Double.isFinite(value)?Math.max(0,value):0;}
    private static String number(double value) {
        value=positive(value);
        return value<10000?String.format(Locale.ROOT,"%.1f",value):value<1e6?String.format(Locale.ROOT,"%.1fk",value/1e3):value<1e9?String.format(Locale.ROOT,"%.1fm",value/1e6):String.format(Locale.ROOT,"%.1e",value);
    }
    private static String percent(double value){return NeoClient.pc(Math.max(0,Math.min(1,value)));}
    private static int mix(int a,int b,double part) {
        part=Math.max(0,Math.min(1,part));int r=(int)(((a>>16)&255)*(1-part)+((b>>16)&255)*part);
        int g=(int)(((a>>8)&255)*(1-part)+((b>>8)&255)*part),blue=(int)((a&255)*(1-part)+(b&255)*part);
        return 0xFF000000|r<<16|g<<8|blue;
    }
    private static void right(DrawContext c,TextRenderer font,Text text,int edge,int y,int color){c.drawText(font,text,edge-font.getWidth(text),y,color,false);}
    private static void pixelLine(DrawContext c,int x1,int y1,int x2,int y2,int color) {
        int length=Math.max(Math.abs(x2-x1),Math.abs(y2-y1));
        for(int i=0;i<=length;i++){int x=length==0?x1:x1+(x2-x1)*i/length,y=length==0?y1:y1+(y2-y1)*i/length;c.fill(x,y,x+1,y+1,color);}
    }
    private static void disk(DrawContext c,int x,int y,int radius,int color) {
        for(int row=-radius;row<=radius;row++){int half=(int)Math.sqrt(radius*radius-row*row);c.fill(x-half,y+row,x+half+1,y+row+1,color);}
    }
    private static void frame(DrawContext c,int x,int y,int w,int h) {
        // Clipped corners, a quiet cast shadow, and single-pixel metal catches.
        c.fill(x+3,y+4,x+w+3,y+h+3,0x48000000);
        c.fill(x+3,y,x+w-3,y+3,0xDE101C29);c.fill(x,y+3,x+w,y+h-3,0xDE101C29);c.fill(x+3,y+h-3,x+w-3,y+h,0xDE101C29);
        c.fill(x+3,y,x+w-3,y+1,0xFF786A4C);c.fill(x+3,y+h-1,x+w-3,y+h,0xFF786A4C);
        c.fill(x,y+3,x+1,y+h-3,0xFF5D615C);c.fill(x+w-1,y+3,x+w,y+h-3,0xFF5D615C);
        c.fill(x+1,y+2,x+3,y+3,GOLD);c.fill(x+2,y+1,x+3,y+2,GOLD);
        c.fill(x+w-3,y+1,x+w-2,y+2,GOLD);c.fill(x+w-3,y+2,x+w-1,y+3,GOLD);
        c.fill(x+1,y+h-3,x+3,y+h-2,GOLD);c.fill(x+w-3,y+h-3,x+w-1,y+h-2,GOLD);
        c.fill(x+5,y+2,x+29,y+3,0x55D6C18D);c.fill(x+w-29,y+2,x+w-5,y+3,0x55D6C18D);
        c.fill(x+5,y+h-3,x+15,y+h-2,0x4497C3BE);c.fill(x+w-15,y+h-3,x+w-5,y+h-2,0x4497C3BE);
    }
    private static void crest(DrawContext c,int x,int y,int accent,double time,boolean casting,boolean critical) {
        disk(c,x,y,12,0xFF172936);
        pixelLine(c,x,y-15,x+14,y,GOLD);pixelLine(c,x+14,y,x,y+15,0xFF665D46);
        pixelLine(c,x,y+15,x-14,y,0xFF665D46);pixelLine(c,x-14,y,x,y-15,GOLD);
        disk(c,x-1,y-1,7,accent);disk(c,x+3,y-3,6,0xFF172936);
        c.fill(x+5,y+4,x+6,y+9,GOLD);c.fill(x+3,y+6,x+8,y+7,GOLD);
        c.fill(x-1,y-18,x+2,y-16,GOLD);c.fill(x-1,y+17,x+2,y+19,0xFF627C7B);
        if(casting)for(int i=0;i<3;i++) {
            double a=time*.022+i*Math.PI*2/3;int px=x+(int)(Math.cos(a)*18),py=y+(int)(Math.sin(a)*18);
            c.fill(px,py,px+2,py+2,PALE);
        }
        if(critical){pixelLine(c,x-3,y-6,x+1,y-1,0xFF482B47);pixelLine(c,x+1,y-1,x-2,y+4,0xFF482B47);}
    }
    /** The scale is fixed at S+60. Beyond the rail, the numeric excess keeps increasing. */
    public static void gauge(DrawContext c,int x,int y,int w,int h,double value,double safety,double forecast,boolean preview,double time) {
        value=positive(value);safety=Math.max(1,positive(safety));double extent=safety+60;
        int inner=w-2,threshold=(int)Math.round(inner*safety/extent),filled=(int)Math.round(inner*Math.min(1,value/extent));
        int future=(int)Math.round(inner*Math.min(1,Math.max(value,forecast)/extent));
        c.fill(x+1,y,x+w-1,y+h,0xFF6D674F);c.fill(x,y+1,x+w,y+h-1,0xFF6D674F);
        c.fill(x+1,y+1,x+w-1,y+h-1,0xFF152A32);
        c.fill(x+1+threshold,y+1,x+w-1,y+h-1,0xFF382933);
        if(preview&&future>filled) {
            int ghost=forecast>safety?0x77C59667:0x556DBDAC;
            c.fill(x+1+filled,y+2,x+1+future,y+h-2,ghost);
            for(int i=filled+2;i<future;i+=4)c.fill(x+1+i,y+2,x+2+i,y+h-2,0x559BD0C3);
            c.fill(x+future,y-2,x+future+1,y+1,forecast>safety?AMBER:JADE);
        }
        int safeFill=Math.min(filled,threshold);
        if(safeFill>0)c.fillGradient(x+1,y+1,x+1+safeFill,y+h-1,0xFF9BDCCA,0xFF397D80);
        if(filled>threshold) {
            int top=mix(AMBER,ROSE,(value-safety)/60),bottom=mix(0xFFA37654,0xFF8D3C67,(value-safety)/60);
            c.fillGradient(x+1+threshold,y+1,x+1+filled,y+h-1,top,bottom);
        }
        for(int i=inner/8;i<inner;i+=Math.max(1,inner/8))c.fill(x+1+i,y+h-2,x+2+i,y+h-1,0x5E0B1823);
        if(filled>0)c.fill(x+filled,y+1,x+filled+1,y+h-1,value>safety?PALE:0xFFB6EEE0);
        int marker=x+1+threshold;
        c.fill(marker,y-1,marker+1,y+h+1,0xFFF3D9A1);
        c.fill(marker-2,y-3,marker+3,y-2,GOLD);c.fill(marker-1,y-2,marker+2,y-1,GOLD);
        if(value>safety+60){int alpha=(int)(125+55*Math.sin(time*.1));c.fill(x+w-3,y+1,x+w-1,y+h-1,alpha<<24|0xFFBEBC);}
    }
    public static void draw(DrawContext c,TextRenderer font,NbtCompound state,int x,int y,int width,double time) {
        int w=Math.max(180,Math.min(WIDTH,width)),h=height(state);
        double value=positive(state.getDouble("C")),safety=Math.max(1,positive(state.getDouble("S"))),excess=Math.max(0,value-safety),q=positive(state.getDouble("Q"));
        int age=state.getInt("Age"),blocked=state.getInt("Blocked");boolean stored=state.getBoolean("Stored"),casting=age>=0;
        double forecast=state.contains("FailureBasis")?state.getDouble("FailureBasis"):value+q;
        int accent=blocked>0||excess>60?ROSE:excess>0||!casting&&forecast>safety?AMBER:JADE;
        frame(c,x,y,w,h);crest(c,x+22,y+27,accent,time,casting,excess>60||blocked>0);
        c.drawText(font,t("title"),x+44,y+7,GOLD,false);
        String status=blocked>0?"blocked":stored?"stored":excess>60?"critical":excess>0?"overload":casting?"casting":forecast>safety?"forecast_overload":value>0&&state.getInt("Recovery")==0?"recovering":"stable";
        Text badge=t("status."+status);int badgeW=font.getWidth(badge)+10,badgeX=x+w-10-badgeW;
        c.fill(badgeX,y+6,badgeX+badgeW,y+18,0xFF25303B);c.fill(badgeX,y+6,badgeX+2,y+18,accent);
        right(c,font,badge,x+w-14,y+8,accent);
        Text amount=Text.literal(number(value));String denominator=" / "+number(safety);
        float scale=font.getWidth(amount)*1.5+font.getWidth(denominator)>w-56?1:1.5f;
        c.getMatrices().push();c.getMatrices().translate(x+44,y+22,0);c.getMatrices().scale(scale,scale,1);
        c.drawText(font,amount,0,0,PALE,false);c.getMatrices().pop();
        c.drawText(font,Text.literal(denominator),x+46+(int)(font.getWidth(amount)*scale),y+26,MUTED,false);
        c.drawText(font,t("values"),x+44,y+36,0xFF859CA8,false);
        gauge(c,x+12,y+49,w-24,8,value,safety,forecast,!casting&&!stored&&blocked==0,time);
        c.drawText(font,excess>0?t("excess",number(excess)):t("headroom",number(safety-value)),x+12,y+59,excess>0?accent:JADE,false);
        right(c,font,t("cost",number(q)),x+w-12,y+59,MUTED);
        c.fill(x+12,y+71,x+w-12,y+72,0xFF30414A);
        c.drawText(font,t("failure",percent(state.getDouble("Failure"))),x+12,y+74,state.getDouble("Failure")>=.8?ROSE:state.getDouble("Failure")>0?AMBER:MUTED,false);
        c.drawText(font,t("offset",percent(state.getDouble("Offset"))),x+88,y+74,state.getDouble("Offset")>=.8?ROSE:state.getDouble("Offset")>0?AMBER:MUTED,false);
        Text tail=casting?t("status.casting"):state.getInt("CD")>0?t("cooldown",number(state.getInt("CD")/20.0)):state.getInt("Recovery")>0?t("recovery_wait",number(state.getInt("Recovery")/20.0)):value>0?t("recovering"):t("ready");
        right(c,font,tail,x+w-12,y+74,MUTED);
        if(h>BASE_HEIGHT) {
            int sy=y+BASE_HEIGHT;c.fill(x+8,sy+1,x+w-8,sy+2,0xFF34464D);
            Text info=blocked>0?t("blocked_for",number(blocked/20.0)):stored?t("stored_help"):age>=state.getInt("Prep")?t("prepared"):t("channel",number(age/20.0),number(state.getInt("Prep")/20.0),number(state.getDouble("Paid")));
            c.drawText(font,info,x+12,sy+5,blocked>0?ROSE:PALE,false);
            if(casting&&blocked==0){double part=Math.min(1,age/(double)Math.max(1,state.getInt("Prep")));c.fill(x+8,sy+17,x+8+(int)((w-16)*part),sy+18,GOLD);}
        }
    }
    public static void drawBookGauge(DrawContext c,TextRenderer font,NbtCompound state,int x,int y,int width,double time) {
        double value=state.getDouble("C"),safety=state.getDouble("S");
        gauge(c,x,y,width,8,value,safety,state.getDouble("FailureBasis"),state.getInt("Age")<0&&state.getInt("Blocked")<=0&&!state.getBoolean("Stored"),time);
        c.drawText(font,Text.literal(number(value)+" / "+number(safety)),x+width+5,y,value>safety?AMBER:JADE,true);
        c.drawText(font,t("cost",number(state.getDouble("Q"))),x+width+5,y+10,MUTED,true);
    }
}
